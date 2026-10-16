package com.fsck.k9.ui.messagedetails

import android.net.Uri
import com.fsck.k9.mail.Address
import com.fsck.k9.view.MessageCryptoDisplayStatus
import com.fsck.k9.ui.messageview.MessageViewSenderAvatar
import net.thunderbird.feature.mail.folder.api.FolderType

data class MessageDetailsUi(
    val date: String?,
    val cryptoDetails: CryptoDetails?,
    val from: List<Participant>,
    val sender: List<Participant>,
    val replyTo: List<Participant>,
    val to: List<Participant>,
    val cc: List<Participant>,
    val bcc: List<Participant>,
    val folder: FolderInfoUi?,
)

data class CryptoDetails(
    val cryptoStatus: MessageCryptoDisplayStatus,
    val isClickable: Boolean,
)

data class Participant(
    val displayName: CharSequence?,
    val emailAddress: String,
    val contactLookupUri: Uri?,
    val senderAvatar: MessageViewSenderAvatar? = null,
) {
    val isInContacts: Boolean
        get() = contactLookupUri != null

    val address: Address
        get() = Address(emailAddress, displayName?.toString())
}

internal enum class ParticipantRole(val supportsSenderBrand: Boolean) {
    FROM(supportsSenderBrand = true),
    SENDER(supportsSenderBrand = true),
    REPLY_TO(supportsSenderBrand = false),
    TO(supportsSenderBrand = false),
    CC(supportsSenderBrand = false),
    BCC(supportsSenderBrand = false),
}

data class FolderInfoUi(
    val displayName: String,
    val type: FolderType,
)
