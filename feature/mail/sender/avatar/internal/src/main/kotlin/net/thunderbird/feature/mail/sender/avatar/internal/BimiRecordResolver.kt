package net.thunderbird.feature.mail.sender.avatar.internal

import java.net.URI
import org.minidns.dnsmessage.DnsMessage
import org.minidns.hla.DnssecResolverApi
import org.minidns.record.TXT

internal data class BimiRecord(
    val logoUri: URI,
    val ttlMillis: Long,
    val dnssecAuthenticated: Boolean,
)

internal sealed interface BimiRecordLookupResult {
    data class Found(val record: BimiRecord) : BimiRecordLookupResult
    data object NotFound : BimiRecordLookupResult
    data object Invalid : BimiRecordLookupResult
    data object Error : BimiRecordLookupResult
}

internal fun interface BimiRecordResolver {
    fun lookup(domain: String, selector: String): BimiRecordLookupResult
}

internal class MiniDnsBimiRecordResolver : BimiRecordResolver {
    override fun lookup(domain: String, selector: String): BimiRecordLookupResult = runCatching {
        val queryName = "$selector._bimi.$domain"
        val result = DnssecResolverApi.INSTANCE.resolve(queryName, TXT::class.java)
        if (!result.wasSuccessful()) {
            if (result.responseCode == DnsMessage.RESPONSE_CODE.NX_DOMAIN) {
                return@runCatching BimiRecordLookupResult.NotFound
            }
            return@runCatching BimiRecordLookupResult.Error
        }

        val answers = result.answersOrEmptySet.toList()
        if (answers.isEmpty()) return@runCatching BimiRecordLookupResult.NotFound
        if (answers.size != 1) return@runCatching BimiRecordLookupResult.Invalid

        val txtRecord = answers.single()
        val text = txtRecord.characterStrings?.joinToString("") ?: txtRecord.text
        val logoUri = BimiRecordParser.parseLogoUri(text) ?: return@runCatching BimiRecordLookupResult.Invalid
        val ttlSeconds = result.rawAnswer.answerSection
            .filter { it.type.name == "TXT" }
            .minOfOrNull { it.ttl }
            ?: DEFAULT_TTL_SECONDS

        BimiRecordLookupResult.Found(
            BimiRecord(
                logoUri = logoUri,
                ttlMillis = ttlSeconds.coerceAtLeast(0).coerceAtMost(MAX_TTL_SECONDS) * 1_000,
                dnssecAuthenticated = result.isAuthenticData,
            ),
        )
    }.getOrDefault(BimiRecordLookupResult.Error)

    private companion object {
        const val DEFAULT_TTL_SECONDS = 3_600L
        const val MAX_TTL_SECONDS = 86_400L
    }
}

internal object BimiRecordParser {
    private const val MAX_RECORD_BYTES = 8 * 1024

    fun parseLogoUri(record: String): URI? {
        if (record.toByteArray(Charsets.UTF_8).size > MAX_RECORD_BYTES) return null

        val tags = linkedMapOf<String, String>()
        for (part in record.split(';')) {
            val trimmed = part.trim()
            if (trimmed.isEmpty()) continue
            val separator = trimmed.indexOf('=')
            if (separator <= 0) return null
            val name = trimmed.substring(0, separator).trim().lowercase()
            val value = trimmed.substring(separator + 1).trim()
            if (name !in setOf("v", "l", "a", "s")) return null
            if (name != "a" && value.isEmpty()) return null
            if (tags.put(name, value) != null) return null
        }

        if (tags["v"] != "BIMI1") return null
        return tags["l"]?.let(BimiSecurity::validateUrl)
    }
}
