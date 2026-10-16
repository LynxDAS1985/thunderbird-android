package net.thunderbird.feature.mail.sender.avatar.internal

import java.net.IDN
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import net.thunderbird.core.preference.avatar.SenderVerificationMethod
import net.thunderbird.core.preference.avatar.SenderVerificationResult
import net.thunderbird.feature.mail.sender.avatar.BimiLogoRequest

internal object BimiSecurity {
    fun acceptsVerifiedMessage(request: BimiLogoRequest): Boolean {
        val verification = request.verification as? SenderVerificationResult.Verified ?: return false
        return verification.message == request.message &&
            SenderVerificationMethod.DMARC_ALIGNMENT in verification.methods
    }

    fun normalizeDomain(domain: String): String? = runCatching {
        IDN.toASCII(domain.trim().trimEnd('.'), IDN.USE_STD3_ASCII_RULES).lowercase()
            .takeIf { it.isNotEmpty() && it.length <= 253 && it.split('.').all(::validLabel) }
    }.getOrNull()

    fun normalizeSelector(selector: String): String? = selector.trim().lowercase()
        .takeIf { it.length in 1..63 && validLabel(it) }

    fun validateUrl(value: String): URI? = runCatching { URI(value) }.getOrNull()?.takeIf { uri ->
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.isAbsolute && uri.host != null && uri.userInfo == null && uri.fragment == null &&
            uri.rawPath?.startsWith('/') != false &&
            (uri.port == -1 || uri.port == 443)
    }

    fun addressesArePublic(addresses: List<InetAddress>): Boolean =
        addresses.isNotEmpty() && addresses.all(::isPublic)

    private fun validLabel(label: String): Boolean =
        label.isNotEmpty() && label.length <= 63 && label.first() != '-' && label.last() != '-' &&
            label.all { it.isLetterOrDigit() || it == '-' }

    private fun isPublic(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress
        ) {
            return false
        }

        val bytes = address.address
        return when (address) {
            is Inet4Address -> isPublicV4(bytes)
            is Inet6Address -> !isBlockedV6(bytes) && embeddedV4(bytes)?.let(::isPublicV4) != false
            else -> false
        }
    }

    private fun isPublicV4(bytes: ByteArray): Boolean {
        val a = bytes[0].toInt() and 0xff
        val b = bytes[1].toInt() and 0xff
        return !(
            a == 0 || a == 10 || a == 127 || a >= 224 ||
                (a == 100 && b in 64..127) || (a == 169 && b == 254) ||
                (a == 172 && b in 16..31) || (a == 192 && b == 168) ||
                (a == 192 && b == 0) || (a == 192 && b == 2) ||
                (a == 198 && b in 18..19) || (a == 198 && b == 51) ||
                (a == 203 && b == 0) || a >= 240
            )
    }

    private fun isBlockedV6(bytes: ByteArray): Boolean {
        val first = bytes[0].toInt() and 0xff
        val second = bytes[1].toInt() and 0xff
        val third = bytes[2].toInt() and 0xff
        val fourth = bytes[3].toInt() and 0xff
        val isGlobalUnicast = first and 0xe0 == 0x20
        val isDocumentation = first == 0x20 && second == 0x01 && third == 0x0d && fourth == 0xb8
        return !isGlobalUnicast || isDocumentation
    }

    private fun embeddedV4(bytes: ByteArray): ByteArray? {
        val mapped = bytes.take(10).all { it == 0.toByte() } && bytes[10] == 0xff.toByte() && bytes[11] == 0xff.toByte()
        return if (mapped) bytes.copyOfRange(12, 16) else null
    }
}
