package net.thunderbird.core.preference.avatar

class DefaultDmarcVerifier : DmarcVerifier {
    override fun verify(request: DmarcVerificationRequest): SenderVerificationResult {
        val trustedIds = request.trustedAuthservIds.mapNotNullTo(mutableSetOf(), ::normalizeDomain)
        if (trustedIds.isEmpty()) return SenderVerificationResult.NotChecked

        val header = request.authenticationResults.firstOrNull() ?: return SenderVerificationResult.NotChecked
        val parsed = AuthenticationResultsParser.parse(header) ?: return SenderVerificationResult.NotChecked
        if (parsed.authservId !in trustedIds) return SenderVerificationResult.NotChecked

        val fromDomain = request.fromAddress.substringAfterLast('@', missingDelimiterValue = "")
            .substringBefore('>')
            .let(::normalizeDomain)
            ?: return failed(request, SenderVerificationFailure.IDENTITY_MISMATCH)

        val dmarc = parsed.dmarc ?: return SenderVerificationResult.NotChecked
        if (dmarc.result != "pass") return failed(request, SenderVerificationFailure.AUTHENTICATION_FAILED)
        if (dmarc.headerFrom != fromDomain) return failed(request, SenderVerificationFailure.IDENTITY_MISMATCH)

        return SenderVerificationResult.Verified(
            message = request.message,
            methods = setOf(SenderVerificationMethod.DMARC_ALIGNMENT),
        )
    }

    private fun failed(request: DmarcVerificationRequest, reason: SenderVerificationFailure) =
        SenderVerificationResult.Failed(message = request.message, reason = reason)
}

private object AuthenticationResultsParser {
    private const val MAX_HEADER_LENGTH = 16_384

    fun parse(value: String): ParsedAuthenticationResults? {
        if (value.length > MAX_HEADER_LENGTH || value.any { it == '\u0000' || it == '\r' || it == '\n' }) return null

        val sections = splitSections(value) ?: return null
        val authservId = sections.firstOrNull()
            ?.trim()
            ?.substringBefore(' ')
            ?.let(::normalizeDomain)
            ?: return null

        val dmarcSections = sections.drop(1).mapNotNull(::parseDmarc)
        if (dmarcSections.size != 1) return null

        return ParsedAuthenticationResults(authservId, dmarcSections.single())
    }

    private fun parseDmarc(section: String): ParsedDmarc? {
        val uncommented = removeComments(section) ?: return null
        val tokens = uncommented.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
        val method = tokens.firstOrNull()?.split('=', limit = 2) ?: return null
        if (!method[0].equals("dmarc", ignoreCase = true) || method.size != 2) return null

        val result = method[1].lowercase()
        val headerFromValues = tokens.drop(1).mapNotNull { token ->
            val pair = token.split('=', limit = 2)
            pair.takeIf { it.size == 2 && it[0].equals("header.from", ignoreCase = true) }?.get(1)
        }
        if (headerFromValues.size != 1) return null
        val headerFrom = normalizeDomain(headerFromValues.single()) ?: return null

        return ParsedDmarc(result, headerFrom)
    }

    private fun splitSections(value: String): List<String>? {
        val sections = mutableListOf<String>()
        val current = StringBuilder()
        var commentDepth = 0
        var escaped = false

        value.forEach { character ->
            when {
                escaped -> {
                    current.append(character)
                    escaped = false
                }
                character == '\\' && commentDepth > 0 -> {
                    current.append(character)
                    escaped = true
                }
                character == '(' -> {
                    commentDepth++
                    current.append(character)
                }
                character == ')' -> {
                    if (commentDepth == 0) return null
                    commentDepth--
                    current.append(character)
                }
                character == ';' && commentDepth == 0 -> {
                    sections += current.toString()
                    current.clear()
                }
                else -> current.append(character)
            }
        }
        if (commentDepth != 0 || escaped) return null
        sections += current.toString()
        return sections
    }

    private fun removeComments(value: String): String? {
        val result = StringBuilder()
        var depth = 0
        var escaped = false
        value.forEach { character ->
            when {
                escaped -> escaped = false
                character == '\\' && depth > 0 -> escaped = true
                character == '(' -> depth++
                character == ')' -> if (depth == 0) return null else depth--
                depth == 0 -> result.append(character)
            }
        }
        return result.toString().takeIf { depth == 0 && !escaped }
    }
}

private data class ParsedAuthenticationResults(
    val authservId: String,
    val dmarc: ParsedDmarc?,
)

private data class ParsedDmarc(
    val result: String,
    val headerFrom: String,
)

private fun normalizeDomain(value: String): String? {
    val normalized = value.trim().trimEnd('.').lowercase()
    if (normalized.isEmpty() || normalized.length > 253 || normalized.any { it.code !in 0x21..0x7e }) return null

    val labels = normalized.split('.')
    if (labels.any { label ->
            label.isEmpty() || label.length > 63 || label.first() == '-' || label.last() == '-' ||
                label.any { !it.isLetterOrDigit() && it != '-' }
        }
    ) {
        return null
    }
    return normalized
}
