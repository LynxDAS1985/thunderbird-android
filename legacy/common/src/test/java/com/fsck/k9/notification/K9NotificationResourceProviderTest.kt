package com.fsck.k9.notification

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import app.k9mail.core.android.common.contact.ContactRepository
import app.k9mail.legacy.message.controller.MessageReference
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.fsck.k9.contacts.ContactPictureLoader
import com.fsck.k9.mail.Address
import com.fsck.k9.view.RecipientSelectView.Recipient
import kotlinx.coroutines.test.runTest
import net.thunderbird.core.preference.avatar.SenderAvatarImage
import net.thunderbird.core.preference.avatar.SenderAvatarRequest
import net.thunderbird.core.preference.avatar.SenderAvatarResolution
import net.thunderbird.core.preference.avatar.SenderAvatarResolver
import net.thunderbird.core.preference.avatar.SenderAvatarSettings
import net.thunderbird.core.preference.avatar.SenderAvatarSource
import net.thunderbird.core.preference.avatar.SenderAvatarSurface
import net.thunderbird.core.preference.avatar.SenderMessageIdentity
import net.thunderbird.core.preference.avatar.SenderVerificationResult
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class K9NotificationResourceProviderTest {
    private val contactRepository = mock<ContactRepository>()
    private val contactPictureLoader = mock<ContactPictureLoader>()
    private val senderAvatarResolver = mock<SenderAvatarResolver>()
    private val bitmap = mock<Bitmap>()
    private val settings = SenderAvatarSettings(isShowInNotifications = true)
    private val address = Address("alice@example.com", "Alice")
    private val messageReference = MessageReference("account", 42, "uid-123")
    private val testSubject = K9NotificationResourceProvider(
        context = mock<Context>(),
        contactRepository = contactRepository,
        contactPictureLoader = contactPictureLoader,
        senderAvatarResolver = senderAvatarResolver,
    )

    @Test
    fun `avatar resolves contact photo for exact message without trusted brand data`() = runTest {
        val photoUri = mock<Uri>()
        whenever(photoUri.toString()) doReturn "content://contacts/alice"
        val photoUriKey = photoUri.toString()
        whenever(contactRepository.getPhotoUri("alice@example.com")) doReturn photoUri
        whenever(senderAvatarResolver.resolve(any())) doReturn SenderAvatarResolution.Visible(
            SenderAvatarSource.Contact(SenderAvatarImage(photoUriKey)),
        )
        whenever(contactPictureLoader.getContactPicture(any<Recipient>())) doReturn bitmap

        val result = testSubject.avatar(address, messageReference, settings)

        assertThat(result).isEqualTo(bitmap)
        verify(contactRepository).getPhotoUri("alice@example.com")
        val request = argumentCaptor<SenderAvatarRequest>().apply {
            verify(senderAvatarResolver).resolve(capture())
        }.firstValue
        assertThat(request.message).isEqualTo(SenderMessageIdentity("account", "42", "uid-123"))
        assertThat(request.surface).isEqualTo(SenderAvatarSurface.NOTIFICATION)
        assertThat(request.settings).isEqualTo(settings)
        assertThat(request.contactImage).isEqualTo(SenderAvatarImage(photoUri.toString()))
        assertThat(request.verifiedBrandImage).isNull()
        assertThat(request.verification).isEqualTo(SenderVerificationResult.NotChecked)

        val recipient = argumentCaptor<Recipient>().apply {
            verify(contactPictureLoader).getContactPicture(capture())
        }.firstValue
        assertThat(recipient.photoThumbnailUri).isEqualTo(photoUri)
    }

    @Test
    fun `avatar uses monogram fallback when contact has no photo`() = runTest {
        whenever(contactRepository.getPhotoUri("alice@example.com")) doReturn null
        whenever(senderAvatarResolver.resolve(any())) doReturn SenderAvatarResolution.Visible(
            SenderAvatarSource.Monogram(value = "Alice", useContactColor = true),
        )
        whenever(contactPictureLoader.getContactPicture(any<Recipient>())) doReturn bitmap

        val result = testSubject.avatar(address, messageReference, settings)

        assertThat(result).isEqualTo(bitmap)
        val recipient = argumentCaptor<Recipient>().apply {
            verify(contactPictureLoader).getContactPicture(capture())
        }.firstValue
        assertThat(recipient.photoThumbnailUri).isNull()
        assertThat(recipient.address).isEqualTo(address)
    }

    @Test
    fun `avatar returns null when resolver hides avatar`() = runTest {
        whenever(senderAvatarResolver.resolve(any())) doReturn SenderAvatarResolution.Hidden

        val result = testSubject.avatar(
            address = address,
            messageReference = messageReference,
            settings = settings.copy(isShowInNotifications = false),
        )

        assertThat(result).isNull()
    }

    @Test
    fun `avatar refuses verified brand source without Android trusted-result adapter`() = runTest {
        whenever(senderAvatarResolver.resolve(any())) doReturn SenderAvatarResolution.Visible(
            SenderAvatarSource.VerifiedBrand(SenderAvatarImage("https://example.com/logo.svg")),
        )

        val result = testSubject.avatar(address, messageReference, settings)

        assertThat(result).isNull()
    }
}
