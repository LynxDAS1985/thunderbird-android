package net.thunderbird.core.preference.avatar

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlin.test.assertFailsWith

class DefaultSenderAvatarResolverTest {
    private val testSubject: SenderAvatarResolver = DefaultSenderAvatarResolver()

    @Test
    fun `uses contact image before other sources`() {
        val request = request(
            contactImage = CONTACT_IMAGE,
            verifiedBrandImage = BRAND_IMAGE,
            verification = verified(MESSAGE),
        )

        val result = testSubject.resolve(request)

        assertThat(result).isEqualTo(
            SenderAvatarResolution.Visible(SenderAvatarSource.Contact(CONTACT_IMAGE)),
        )
    }

    @Test
    fun `uses verified brand and badge for exact message`() {
        val request = request(
            verifiedBrandImage = BRAND_IMAGE,
            verification = verified(MESSAGE),
        )

        val result = testSubject.resolve(request)

        assertThat(result).isEqualTo(
            SenderAvatarResolution.Visible(
                source = SenderAvatarSource.VerifiedBrand(BRAND_IMAGE),
                badge = SenderVerificationBadge.Verified(VERIFICATION_METHODS),
            ),
        )
    }

    @Test
    fun `never uses badge when verification belongs to another message`() {
        val request = request(
            verifiedBrandImage = BRAND_IMAGE,
            monogram = "S",
            verification = verified(OTHER_MESSAGE),
        )

        val result = testSubject.resolve(request)

        assertThat(result).isEqualTo(
            SenderAvatarResolution.Visible(SenderAvatarSource.Monogram("S", useContactColor = true)),
        )
    }

    @Test
    fun `never uses badge when verification failed`() {
        val request = request(
            verifiedBrandImage = BRAND_IMAGE,
            verification = SenderVerificationResult.Failed(
                message = MESSAGE,
                reason = SenderVerificationFailure.AUTHENTICATION_FAILED,
            ),
        )

        val result = testSubject.resolve(request)

        assertThat(result).isEqualTo(SenderAvatarResolution.Visible(SenderAvatarSource.Generic))
    }

    @Test
    fun `never uses brand or badge in message view when message was not checked`() {
        val request = request(
            surface = SenderAvatarSurface.MESSAGE_VIEW,
            verifiedBrandImage = BRAND_IMAGE,
        )

        val result = testSubject.resolve(request)

        assertThat(result).isEqualTo(SenderAvatarResolution.Visible(SenderAvatarSource.Generic))
    }

    @Test
    fun `uses cached brand without badge in message list when message was not checked`() {
        val request = request(
            surface = SenderAvatarSurface.MESSAGE_LIST,
            verifiedBrandImage = BRAND_IMAGE,
        )

        val result = testSubject.resolve(request)

        assertThat(result).isEqualTo(
            SenderAvatarResolution.Visible(
                source = SenderAvatarSource.VerifiedBrand(BRAND_IMAGE),
                badge = SenderVerificationBadge.None,
            ),
        )
    }

    @Test
    fun `uses trimmed monogram and configured color behavior`() {
        val request = request(
            settings = SenderAvatarSettings(isColorizeMissing = false),
            monogram = "  AB  ",
        )

        val result = testSubject.resolve(request)

        assertThat(result).isEqualTo(
            SenderAvatarResolution.Visible(SenderAvatarSource.Monogram("AB", useContactColor = false)),
        )
    }

    @Test
    fun `uses generic image when no usable source exists`() {
        val result = testSubject.resolve(request(monogram = "   "))

        assertThat(result).isEqualTo(SenderAvatarResolution.Visible(SenderAvatarSource.Generic))
    }

    @Test
    fun `hides list and message view avatar when respective setting is disabled`() {
        val listDisabled = SenderAvatarSettings(isShowInMessageList = false)
        val viewDisabled = SenderAvatarSettings(isShowInMessageView = false)

        assertThat(testSubject.resolve(request(surface = SenderAvatarSurface.MESSAGE_LIST, settings = listDisabled)))
            .isEqualTo(SenderAvatarResolution.Hidden)
        assertThat(testSubject.resolve(request(surface = SenderAvatarSurface.MESSAGE_VIEW, settings = viewDisabled)))
            .isEqualTo(SenderAvatarResolution.Hidden)
    }

    @Test
    fun `hides notification avatar when notification setting is disabled`() {
        val settings = SenderAvatarSettings(isShowInNotifications = false)

        val result = testSubject.resolve(request(surface = SenderAvatarSurface.NOTIFICATION, settings = settings))

        assertThat(result).isEqualTo(SenderAvatarResolution.Hidden)
    }

    @Test
    fun `hides avatar when master switch is disabled`() {
        val settings = SenderAvatarSettings(isEnabled = false)
        val result = testSubject.resolve(request(settings = settings, contactImage = CONTACT_IMAGE))

        assertThat(result).isEqualTo(SenderAvatarResolution.Hidden)
    }

    @Test
    fun `ignores contact image when contact source is disabled`() {
        val settings = SenderAvatarSettings(isSourceContactsEnabled = false)
        val result = testSubject.resolve(request(settings = settings, contactImage = CONTACT_IMAGE, monogram = "AC"))

        assertThat(result).isEqualTo(
            SenderAvatarResolution.Visible(SenderAvatarSource.Monogram("AC", useContactColor = true)),
        )
    }

    @Test
    fun `ignores brand image when bimi source is disabled`() {
        val settings = SenderAvatarSettings(isSourceBimiEnabled = false)
        val result = testSubject.resolve(
            request(
                settings = settings,
                verifiedBrandImage = BRAND_IMAGE,
                verification = verified(MESSAGE),
                monogram = "AC",
            ),
        )

        assertThat(result).isEqualTo(
            SenderAvatarResolution.Visible(SenderAvatarSource.Monogram("AC", useContactColor = true)),
        )
    }

    @Test
    fun `prioritizes bimi over contacts when priority is BIMI_FIRST`() {
        val settings = SenderAvatarSettings(priority = SenderAvatarPriority.BIMI_FIRST)
        val verification = verified(MESSAGE)
        val result = testSubject.resolve(
            request(
                settings = settings,
                contactImage = CONTACT_IMAGE,
                verifiedBrandImage = BRAND_IMAGE,
                verification = verification,
            ),
        )

        assertThat(result).isEqualTo(
            SenderAvatarResolution.Visible(
                source = SenderAvatarSource.VerifiedBrand(BRAND_IMAGE),
                badge = SenderVerificationBadge.Verified(verification.methods),
            ),
        )
    }

    @Test
    fun `prioritizes contacts over bimi when priority is CONTACTS_FIRST`() {
        val settings = SenderAvatarSettings(priority = SenderAvatarPriority.CONTACTS_FIRST)
        val verification = verified(MESSAGE)
        val result = testSubject.resolve(
            request(
                settings = settings,
                contactImage = CONTACT_IMAGE,
                verifiedBrandImage = BRAND_IMAGE,
                verification = verification,
            ),
        )

        assertThat(result).isEqualTo(
            SenderAvatarResolution.Visible(SenderAvatarSource.Contact(CONTACT_IMAGE)),
        )
    }

    @Test
    fun `omits badge when isShowVerifiedShield is false`() {
        val settings = SenderAvatarSettings(isShowVerifiedShield = false)
        val verification = verified(MESSAGE)
        val result = testSubject.resolve(
            request(
                settings = settings,
                verifiedBrandImage = BRAND_IMAGE,
                verification = verification,
            ),
        )

        assertThat(result).isEqualTo(
            SenderAvatarResolution.Visible(
                source = SenderAvatarSource.VerifiedBrand(BRAND_IMAGE),
                badge = SenderVerificationBadge.None,
            ),
        )
    }

    @Test
    fun `uses generic silhouette when fallbackStyle is DEFAULT_ICON`() {
        val settings = SenderAvatarSettings(fallbackStyle = SenderAvatarFallbackStyle.DEFAULT_ICON)
        val result = testSubject.resolve(request(settings = settings, monogram = "AB"))

        assertThat(result).isEqualTo(SenderAvatarResolution.Visible(SenderAvatarSource.Generic))
    }

    @Test
    fun `rejects verified result without evidence`() {
        assertFailsWith<IllegalArgumentException> {
            SenderVerificationResult.Verified(message = MESSAGE, methods = emptySet())
        }
    }

    @Test
    fun `rejects incomplete message identity`() {
        assertFailsWith<IllegalArgumentException> {
            SenderMessageIdentity(accountId = "account", folderId = "folder", messageId = " ")
        }
    }

    private fun request(
        surface: SenderAvatarSurface = SenderAvatarSurface.MESSAGE_LIST,
        settings: SenderAvatarSettings = SenderAvatarSettings(),
        contactImage: SenderAvatarImage? = null,
        verifiedBrandImage: SenderAvatarImage? = null,
        monogram: String? = null,
        verification: SenderVerificationResult = SenderVerificationResult.NotChecked,
    ) = SenderAvatarRequest(
        message = MESSAGE,
        surface = surface,
        settings = settings,
        contactImage = contactImage,
        verifiedBrandImage = verifiedBrandImage,
        monogram = monogram,
        verification = verification,
    )

    private fun verified(message: SenderMessageIdentity) = SenderVerificationResult.Verified(
        message = message,
        methods = VERIFICATION_METHODS,
    )

    private companion object {
        val MESSAGE = SenderMessageIdentity("account", "folder", "message")
        val OTHER_MESSAGE = SenderMessageIdentity("account", "folder", "other-message")
        val CONTACT_IMAGE = SenderAvatarImage("content://contact/1")
        val BRAND_IMAGE = SenderAvatarImage("cache://verified-brand/example")
        val VERIFICATION_METHODS = setOf(SenderVerificationMethod.DMARC_ALIGNMENT)
    }
}
