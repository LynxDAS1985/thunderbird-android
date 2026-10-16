package net.thunderbird.core.preference.avatar

/**
 * Converts a trusted server's DMARC result into evidence bound to one stored message.
 *
 * [authenticationResults] must preserve the order in which the headers occur in the message. Only the first
 * Authentication-Results header is considered. Callers must explicitly configure [trustedAuthservIds]; an empty set
 * is the safe default for arbitrary IMAP servers.
 */
interface DmarcVerifier {
    fun verify(request: DmarcVerificationRequest): SenderVerificationResult
}

data class DmarcVerificationRequest(
    val message: SenderMessageIdentity,
    val fromAddress: String,
    val authenticationResults: List<String>,
    val trustedAuthservIds: Set<String> = emptySet(),
)
