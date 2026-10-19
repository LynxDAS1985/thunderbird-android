package net.thunderbird.core.preference.avatar

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class DefaultDmarcVerifierTest {
    private val testSubject: DmarcVerifier = DefaultDmarcVerifier()

    @Test
    fun `accepts exact message when trusted server reports aligned DMARC pass`() {
        val result = testSubject.verify(request())

        assertThat(result).isEqualTo(
            SenderVerificationResult.Verified(MESSAGE, setOf(SenderVerificationMethod.DMARC_ALIGNMENT)),
        )
    }

    @Test
    fun `accepts case insensitive fields and comments`() {
        val result = testSubject.verify(
            request(headers = listOf("AUTH.EXAMPLE.; DMARC=PASS (policy accepted) HEADER.FROM=Sender.Example")),
        )

        assertThat(result).isEqualTo(
            SenderVerificationResult.Verified(MESSAGE, setOf(SenderVerificationMethod.DMARC_ALIGNMENT)),
        )
    }

    @Test
    fun `does not trust any header when trusted server list is empty`() {
        val result = testSubject.verify(request(trustedIds = emptySet()))

        assertThat(result).isEqualTo(SenderVerificationResult.NotChecked)
    }

    @Test
    fun `does not skip forged topmost authentication results`() {
        val result = testSubject.verify(
            request(
                headers = listOf(
                    "attacker.example; dmarc=pass header.from=sender.example",
                    VALID_HEADER,
                ),
            ),
        )

        assertThat(result).isEqualTo(SenderVerificationResult.NotChecked)
    }

    @Test
    fun `rejects DMARC failure reported by trusted server`() {
        val result = testSubject.verify(
            request(headers = listOf("auth.example; dmarc=fail header.from=sender.example")),
        )

        assertThat(result).isEqualTo(
            SenderVerificationResult.Failed(MESSAGE, SenderVerificationFailure.AUTHENTICATION_FAILED),
        )
    }

    @Test
    fun `rejects header from domain that differs from actual sender`() {
        val result = testSubject.verify(
            request(headers = listOf("auth.example; dmarc=pass header.from=other.example")),
        )

        assertThat(result).isEqualTo(
            SenderVerificationResult.Failed(MESSAGE, SenderVerificationFailure.IDENTITY_MISMATCH),
        )
    }

    @Test
    fun `rejects duplicate DMARC results`() {
        val result = testSubject.verify(
            request(
                headers = listOf(
                    "auth.example; dmarc=pass header.from=sender.example; dmarc=pass header.from=x.example",
                ),
            ),
        )

        assertThat(result).isEqualTo(SenderVerificationResult.NotChecked)
    }

    @Test
    fun `accepts RFC 5322 folded header value`() {
        val result = testSubject.verify(
            request(headers = listOf("auth.example;\r\n dmarc=pass header.from=sender.example")),
        )

        assertThat(result).isEqualTo(
            SenderVerificationResult.Verified(MESSAGE, setOf(SenderVerificationMethod.DMARC_ALIGNMENT)),
        )
    }

    @Test
    fun `accepts bimi pass tag and awards BIMI assertion method`() {
        val result = testSubject.verify(
            request(headers = listOf("auth.example; dmarc=pass header.from=sender.example; bimi=pass")),
        )

        assertThat(result).isEqualTo(
            SenderVerificationResult.Verified(
                MESSAGE,
                setOf(SenderVerificationMethod.DMARC_ALIGNMENT, SenderVerificationMethod.BIMI_ASSERTION),
            ),
        )
    }

    @Test
    fun `rejects DMARC failure even without header from`() {
        val result = testSubject.verify(
            request(headers = listOf("auth.example; dmarc=fail")),
        )

        assertThat(result).isEqualTo(
            SenderVerificationResult.Failed(MESSAGE, SenderVerificationFailure.AUTHENTICATION_FAILED),
        )
    }

    @Test
    fun `binds failed result to the requested message`() {
        val otherMessage = SenderMessageIdentity("account", "archive", "42")
        val result = testSubject.verify(
            request(
                message = otherMessage,
                headers = listOf("auth.example; dmarc=fail header.from=sender.example"),
            ),
        )

        assertThat(result).isEqualTo(
            SenderVerificationResult.Failed(otherMessage, SenderVerificationFailure.AUTHENTICATION_FAILED),
        )
    }

    private fun request(
        message: SenderMessageIdentity = MESSAGE,
        headers: List<String> = listOf(VALID_HEADER),
        trustedIds: Set<String> = setOf("auth.example"),
    ) = DmarcVerificationRequest(
        message = message,
        fromAddress = "Sender <mail@sender.example>",
        authenticationResults = headers,
        trustedAuthservIds = trustedIds,
    )

    private companion object {
        val MESSAGE = SenderMessageIdentity("account", "inbox", "42")
        const val VALID_HEADER = "auth.example; dmarc=pass header.from=sender.example"
    }
}
