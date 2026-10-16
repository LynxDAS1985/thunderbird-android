package com.fsck.k9.ui.messageview

import android.net.Uri
import app.k9mail.core.android.common.contact.ContactRepository
import app.k9mail.legacy.message.controller.MessageReference
import com.fsck.k9.mail.Address
import net.thunderbird.core.preference.avatar.SenderAvatarImage
import net.thunderbird.core.preference.avatar.SenderAvatarRequest
import net.thunderbird.core.preference.avatar.SenderAvatarResolution
import net.thunderbird.core.preference.avatar.SenderAvatarResolver
import net.thunderbird.core.preference.avatar.SenderAvatarSettingsPreferenceManager
import net.thunderbird.core.preference.avatar.SenderAvatarSource
import net.thunderbird.core.preference.avatar.SenderAvatarSurface
import net.thunderbird.core.preference.avatar.SenderMessageIdentity
import net.thunderbird.core.preference.avatar.SenderVerificationBadge
import net.thunderbird.core.preference.avatar.SenderVerificationResult

internal class MessageViewSenderAvatarResolver(
    private val contactRepository: ContactRepository,
    private val senderAvatarResolver: SenderAvatarResolver,
    private val settingsManager: SenderAvatarSettingsPreferenceManager,
) {
    fun resolve(
        messageReference: MessageReference,
        address: Address,
        verifiedBrandImage: Uri? = null,
        verification: SenderVerificationResult = SenderVerificationResult.NotChecked,
    ): MessageViewSenderAvatar {
        val contactImage = contactRepository.getPhotoUri(address.address)?.toSenderAvatarImage()
        val resolution = senderAvatarResolver.resolve(
            SenderAvatarRequest(
                message = messageReference.toSenderMessageIdentity(),
                surface = SenderAvatarSurface.MESSAGE_VIEW,
                settings = settingsManager.getConfig(),
                contactImage = contactImage,
                verifiedBrandImage = verifiedBrandImage?.toSenderAvatarImage(),
                monogram = address.personal ?: address.address,
                verification = verification,
            ),
        )

        return resolution.toMessageViewSenderAvatar(address)
    }

    private fun SenderAvatarResolution.toMessageViewSenderAvatar(address: Address): MessageViewSenderAvatar {
        return when (this) {
            SenderAvatarResolution.Hidden -> MessageViewSenderAvatar.Hidden
            is SenderAvatarResolution.Visible -> MessageViewSenderAvatar.Visible(
                imageUri = when (val source = source) {
                    is SenderAvatarSource.Contact -> Uri.parse(source.image.key)
                    is SenderAvatarSource.VerifiedBrand -> Uri.parse(source.image.key)
                    SenderAvatarSource.Generic,
                    is SenderAvatarSource.Monogram,
                    -> null
                },
                fallbackAddress = address,
                isVerified = badge is SenderVerificationBadge.Verified,
            )
        }
    }

    private fun Uri.toSenderAvatarImage() = SenderAvatarImage(toString())

    private fun MessageReference.toSenderMessageIdentity() = SenderMessageIdentity(
        accountId = accountUuid,
        folderId = folderId.toString(),
        messageId = uid,
    )
}

sealed interface MessageViewSenderAvatar {
    data object Hidden : MessageViewSenderAvatar

    data class Visible(
        val imageUri: Uri?,
        val fallbackAddress: Address,
        val isVerified: Boolean,
    ) : MessageViewSenderAvatar
}
