package net.thunderbird.feature.mail.sender.avatar.internal

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import java.net.InetAddress
import net.thunderbird.core.preference.avatar.SenderMessageIdentity
import net.thunderbird.core.preference.avatar.SenderVerificationMethod
import net.thunderbird.core.preference.avatar.SenderVerificationResult
import net.thunderbird.feature.mail.sender.avatar.BimiLogoRequest
import org.junit.Test

class BimiSecurityTest {
    private val message = SenderMessageIdentity("account", "folder", "message")

    @Test
    fun `gate accepts matching identity with DMARC evidence`() {
        val request =
            request(SenderVerificationResult.Verified(message, setOf(SenderVerificationMethod.DMARC_ALIGNMENT)))

        assertThat(BimiSecurity.acceptsVerifiedMessage(request)).isTrue()
    }

    @Test
    fun `gate rejects another message even with DMARC evidence`() {
        val other = message.copy(messageId = "other")
        val request = request(SenderVerificationResult.Verified(other, setOf(SenderVerificationMethod.DMARC_ALIGNMENT)))

        assertThat(BimiSecurity.acceptsVerifiedMessage(request)).isFalse()
    }

    @Test
    fun `gate rejects matching identity without DMARC evidence`() {
        val request =
            request(SenderVerificationResult.Verified(message, setOf(SenderVerificationMethod.BIMI_ASSERTION)))

        assertThat(BimiSecurity.acceptsVerifiedMessage(request)).isFalse()
    }

    @Test
    fun `URL accepts only credential-free HTTPS on port 443`() {
        assertThat(BimiSecurity.validateUrl("https://brand.example/logo.svg")).isNotNull()
        assertThat(BimiSecurity.validateUrl("https://brand.example:443/logo.svg")).isNotNull()
        assertThat(BimiSecurity.validateUrl("http://brand.example/logo.svg")).isNull()
        assertThat(BimiSecurity.validateUrl("https://user:pass@brand.example/logo.svg")).isNull()
        assertThat(BimiSecurity.validateUrl("https://brand.example:8443/logo.svg")).isNull()
    }

    @Test
    fun `DNS rejects private reserved and mixed answers`() {
        val public = InetAddress.getByName("93.184.216.34")
        val private = InetAddress.getByName("192.168.1.1")

        assertThat(BimiSecurity.addressesArePublic(listOf(public))).isTrue()
        assertThat(BimiSecurity.addressesArePublic(listOf(private))).isFalse()
        assertThat(BimiSecurity.addressesArePublic(listOf(public, private))).isFalse()
        assertThat(BimiSecurity.addressesArePublic(listOf(InetAddress.getByName("127.0.0.1")))).isFalse()
        assertThat(BimiSecurity.addressesArePublic(listOf(InetAddress.getByName("100.64.0.1")))).isFalse()
        assertThat(BimiSecurity.addressesArePublic(listOf(InetAddress.getByName("fc00::1")))).isFalse()
        assertThat(BimiSecurity.addressesArePublic(listOf(InetAddress.getByName("2001:db8::1")))).isFalse()
    }

    private fun request(verification: SenderVerificationResult) = BimiLogoRequest(
        message = message,
        alignedDomain = "example.com",
        verification = verification,
    )
}
