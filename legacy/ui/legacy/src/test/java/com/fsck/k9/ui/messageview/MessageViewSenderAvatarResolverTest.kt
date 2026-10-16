package com.fsck.k9.ui.messageview

import android.net.Uri
import app.k9mail.core.android.common.contact.Contact
import app.k9mail.core.android.common.contact.ContactRepository
import app.k9mail.legacy.message.controller.MessageReference
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import com.fsck.k9.mail.Address
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import net.thunderbird.core.android.testing.RobolectricTest
import net.thunderbird.core.common.mail.EmailAddress
import net.thunderbird.core.preference.avatar.DefaultSenderAvatarResolver
import net.thunderbird.core.preference.avatar.SenderAvatarSettings
import net.thunderbird.core.preference.avatar.SenderAvatarSettingsPreferenceManager
import net.thunderbird.core.preference.avatar.SenderAvatarImage
import net.thunderbird.core.preference.avatar.SenderMessageIdentity
import net.thunderbird.core.preference.avatar.SenderVerificationMethod
import net.thunderbird.core.preference.avatar.SenderVerificationResult
import org.junit.Test

class MessageViewSenderAvatarResolverTest : RobolectricTest() {
    private val settingsManager = FakeSettingsManager()
    private val contactRepository = FakeContactRepository()
    private val testSubject = MessageViewSenderAvatarResolver(
        contactRepository = contactRepository,
        senderAvatarResolver = DefaultSenderAvatarResolver(),
        settingsManager = settingsManager,
    )

    @Test
    fun `contact photo is selected for exact message reference`() {
        contactRepository.photoUri = Uri.parse("content://contacts/alice")

        val result = testSubject.resolve(MESSAGE_REFERENCE, ADDRESS)

        assertThat(result).isInstanceOf<MessageViewSenderAvatar.Visible>()
        val visible = result as MessageViewSenderAvatar.Visible
        assertThat(visible.imageUri).isEqualTo(contactRepository.photoUri)
        assertThat(visible.isVerified).isFalse()
    }

    @Test
    fun `trusted brand and badge are selected for exact message only`() {
        val result = testSubject.resolve(
            messageReference = MESSAGE_REFERENCE,
            address = ADDRESS,
            verifiedBrandImage = BRAND_URI,
            verification = verified(MESSAGE_IDENTITY),
        )

        assertThat(result).isInstanceOf<MessageViewSenderAvatar.Visible>()
        val visible = result as MessageViewSenderAvatar.Visible
        assertThat(visible.imageUri).isEqualTo(BRAND_URI)
        assertThat(visible.isVerified).isTrue()
    }

    @Test
    fun `brand and badge are rejected when verification belongs to another message`() {
        val result = testSubject.resolve(
            messageReference = MESSAGE_REFERENCE,
            address = ADDRESS,
            verifiedBrandImage = BRAND_URI,
            verification = verified(MESSAGE_IDENTITY.copy(messageId = "other-message")),
        )

        assertThat(result).isInstanceOf<MessageViewSenderAvatar.Visible>()
        val visible = result as MessageViewSenderAvatar.Visible
        assertThat(visible.imageUri).isEqualTo(null)
        assertThat(visible.isVerified).isFalse()
    }

    @Test
    fun `avatar is hidden when message view setting is disabled`() {
        settingsManager.currentConfig = SenderAvatarSettings(isShowInMessageList = false)

        val result = testSubject.resolve(MESSAGE_REFERENCE, ADDRESS)

        assertThat(result).isEqualTo(MessageViewSenderAvatar.Hidden)
    }

    private fun verified(identity: SenderMessageIdentity) = SenderVerificationResult.Verified(
        message = identity,
        methods = setOf(SenderVerificationMethod.DMARC_ALIGNMENT),
    )

    private class FakeContactRepository : ContactRepository {
        var photoUri: Uri? = null

        override fun getContactFor(emailAddress: EmailAddress): Contact? = null
        override fun hasContactFor(emailAddress: EmailAddress): Boolean = false
        override fun hasAnyContactFor(emailAddresses: List<EmailAddress>): Boolean = false
        override fun getPhotoUri(emailAddress: String): Uri? = photoUri
    }

    private class FakeSettingsManager : SenderAvatarSettingsPreferenceManager {
        var currentConfig = SenderAvatarSettings()

        override fun save(config: SenderAvatarSettings) {
            currentConfig = config
        }

        override fun getConfig(): SenderAvatarSettings = currentConfig
        override fun getConfigFlow(): Flow<SenderAvatarSettings> = flowOf(currentConfig)
    }

    private companion object {
        val MESSAGE_REFERENCE = MessageReference("account", 42, "message")
        val MESSAGE_IDENTITY = SenderMessageIdentity("account", "42", "message")
        val ADDRESS = Address("alice@example.org", "Alice")
        val BRAND_URI: Uri = Uri.parse("file:///verified-brand.png")
    }
}
