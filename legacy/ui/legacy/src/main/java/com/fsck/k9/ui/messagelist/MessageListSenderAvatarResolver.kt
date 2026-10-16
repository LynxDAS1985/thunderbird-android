package com.fsck.k9.ui.messagelist

import app.k9mail.core.android.common.contact.ContactRepository
import net.thunderbird.core.preference.avatar.SenderAvatarImage
import net.thunderbird.core.preference.avatar.SenderAvatarRequest
import net.thunderbird.core.preference.avatar.SenderAvatarResolution
import net.thunderbird.core.preference.avatar.SenderAvatarResolver
import net.thunderbird.core.preference.avatar.SenderAvatarSettingsPreferenceManager
import net.thunderbird.core.preference.avatar.SenderAvatarSurface
import net.thunderbird.core.preference.avatar.SenderMessageIdentity
import net.thunderbird.feature.account.avatar.AvatarMonogramCreator

/** Android boundary that supplies message-list data to the platform-independent avatar resolver. */
class MessageListSenderAvatarResolver(
    private val contactRepository: ContactRepository,
    private val avatarMonogramCreator: AvatarMonogramCreator,
    private val settingsManager: SenderAvatarSettingsPreferenceManager,
    private val resolver: SenderAvatarResolver,
) {
    fun resolve(item: MessageListItem, showAvatar: Boolean): SenderAvatarResolution {
        val address = item.displayAddress?.address
        val contactImage = address
            ?.let(contactRepository::getPhotoUri)
            ?.let { uri -> SenderAvatarImage(uri.toString()) }
        val monogram = avatarMonogramCreator.create(item.displayName.toString(), address)
        val settings = settingsManager.getConfig().copy(isShowInMessageList = showAvatar)

        return resolver.resolve(
            SenderAvatarRequest(
                message = SenderMessageIdentity(
                    accountId = item.account.uuid,
                    folderId = item.folderId.toString(),
                    messageId = item.messageUid,
                ),
                surface = SenderAvatarSurface.MESSAGE_LIST,
                settings = settings,
                contactImage = contactImage,
                verifiedBrandImage = null,
                monogram = monogram,
            ),
        )
    }
}
