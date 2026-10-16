package com.fsck.k9.ui.messagelist

import android.net.Uri
import app.k9mail.core.android.common.contact.ContactRepository
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.fsck.k9.mail.Address
import net.thunderbird.core.android.account.LegacyAccount
import net.thunderbird.core.android.testing.RobolectricTest
import net.thunderbird.core.preference.avatar.SenderAvatarImage
import net.thunderbird.core.preference.avatar.SenderAvatarRequest
import net.thunderbird.core.preference.avatar.SenderAvatarResolution
import net.thunderbird.core.preference.avatar.SenderAvatarResolver
import net.thunderbird.core.preference.avatar.SenderAvatarSettings
import net.thunderbird.core.preference.avatar.SenderAvatarSettingsPreferenceManager
import net.thunderbird.core.preference.avatar.SenderAvatarSource
import net.thunderbird.core.preference.avatar.SenderAvatarSurface
import net.thunderbird.core.preference.avatar.SenderMessageIdentity
import net.thunderbird.feature.account.avatar.AvatarMonogramCreator
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MessageListSenderAvatarResolverTest : RobolectricTest() {
    private val contactRepository = mock<ContactRepository>()
    private val settingsManager = mock<SenderAvatarSettingsPreferenceManager>()
    private val resolver = mock<SenderAvatarResolver>()
    private val monogramCreator = AvatarMonogramCreator { _, _ -> "JD" }
    private val testSubject = MessageListSenderAvatarResolver(
        contactRepository = contactRepository,
        avatarMonogramCreator = monogramCreator,
        settingsManager = settingsManager,
        resolver = resolver,
    )

    @Test
    fun `resolve passes contact and exact message identity to resolver`() {
        val item = createItem()
        val uri = Uri.parse("content://contacts/42")
        val expected = SenderAvatarResolution.Visible(SenderAvatarSource.Contact(SenderAvatarImage(uri.toString())))
        whenever(contactRepository.getPhotoUri("john@example.com")).thenReturn(uri)
        whenever(settingsManager.getConfig()).thenReturn(SenderAvatarSettings())
        whenever(resolver.resolve(org.mockito.kotlin.any())).thenReturn(expected)

        val result = testSubject.resolve(item, showAvatar = true)

        val request = argumentCaptor<SenderAvatarRequest>()
        verify(resolver).resolve(request.capture())
        assertThat(result).isEqualTo(expected)
        assertThat(request.firstValue.message).isEqualTo(
            SenderMessageIdentity(accountId = "account-1", folderId = "23", messageId = "message-99"),
        )
        assertThat(request.firstValue.surface).isEqualTo(SenderAvatarSurface.MESSAGE_LIST)
        assertThat(request.firstValue.contactImage).isEqualTo(SenderAvatarImage(uri.toString()))
        assertThat(request.firstValue.verifiedBrandImage).isEqualTo(null)
        assertThat(request.firstValue.monogram).isEqualTo("JD")
    }

    @Test
    fun `resolve lets resolver hide avatar when current list appearance disables it`() {
        val item = createItem()
        whenever(settingsManager.getConfig()).thenReturn(SenderAvatarSettings(isShowInMessageList = true))
        whenever(resolver.resolve(org.mockito.kotlin.any())).thenReturn(SenderAvatarResolution.Hidden)

        testSubject.resolve(item, showAvatar = false)

        val request = argumentCaptor<SenderAvatarRequest>()
        verify(resolver).resolve(request.capture())
        assertThat(request.firstValue.settings.isShowInMessageList).isEqualTo(false)
    }

    private fun createItem(): MessageListItem {
        val account = mock<LegacyAccount>()
        whenever(account.uuid).thenReturn("account-1")

        return MessageListItem(
            account = account,
            subject = "Subject",
            threadCount = 1,
            messageDate = 0,
            internalDate = 0,
            displayName = "John Doe",
            displayAddress = Address("john@example.com"),
            displayMessageDateTime = "12:00",
            previewText = "Preview",
            isMessageEncrypted = false,
            isRead = false,
            isStarred = false,
            isAnswered = false,
            isForwarded = false,
            hasAttachments = false,
            uniqueId = 1,
            folderId = 23,
            messageUid = "message-99",
            databaseId = 2,
            threadRoot = 3,
            contactColor = 0,
        )
    }
}
