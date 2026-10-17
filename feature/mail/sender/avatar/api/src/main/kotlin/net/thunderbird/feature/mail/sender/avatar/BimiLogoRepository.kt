package net.thunderbird.feature.mail.sender.avatar

import net.thunderbird.core.preference.avatar.SenderAvatarImage
import net.thunderbird.core.preference.avatar.SenderMessageIdentity
import net.thunderbird.core.preference.avatar.SenderVerificationResult

interface BimiLogoRepository {
    fun load(request: BimiLogoRequest): BimiLogoResult
}

data class BimiLogoRequest(
    val message: SenderMessageIdentity,
    val alignedDomain: String,
    val verification: SenderVerificationResult,
    val selector: String = "default",
)

sealed interface BimiLogoResult {
    data class Available(
        val image: SenderAvatarImage,
        val mayShowVerifiedShield: Boolean,
        val source: Source,
    ) : BimiLogoResult

    data class Rejected(val reason: Reason) : BimiLogoResult

    enum class Source { NETWORK, FRESH_CACHE, STALE_CACHE }

    enum class Reason {
        MESSAGE_NOT_DMARC_VERIFIED,
        INVALID_DOMAIN,
        INVALID_SELECTOR,
        DNS_ERROR,
        BIMI_RECORD_NOT_FOUND,
        INVALID_BIMI_RECORD,
        INVALID_URL,
        BLOCKED_ADDRESS,
        NETWORK_ERROR,
        INVALID_RESPONSE,
        INVALID_SVG,
        RASTERIZATION_FAILED,
        CACHE_ERROR,
    }
}
