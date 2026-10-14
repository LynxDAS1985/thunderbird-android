package net.thunderbird.core.preference.avatar

/** Result produced for one exact message. It is not a sender-wide or domain-wide trust decision. */
sealed interface SenderVerificationResult {
    data object NotChecked : SenderVerificationResult

    data class Verified(
        val message: SenderMessageIdentity,
        val methods: Set<SenderVerificationMethod>,
    ) : SenderVerificationResult {
        init {
            require(methods.isNotEmpty()) { "A verified result must contain evidence" }
        }
    }

    data class Failed(
        val message: SenderMessageIdentity,
        val reason: SenderVerificationFailure,
    ) : SenderVerificationResult
}

/** Evidence already established by trusted mail-processing code. This module performs no network access. */
enum class SenderVerificationMethod {
    DMARC_ALIGNMENT,
    BIMI_ASSERTION,
    VERIFIED_MARK_CERTIFICATE,
}

enum class SenderVerificationFailure {
    IDENTITY_MISMATCH,
    AUTHENTICATION_FAILED,
    BRAND_NOT_VERIFIED,
    UNSUPPORTED,
    TEMPORARILY_UNAVAILABLE,
}
