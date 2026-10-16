package com.fsck.k9.ui.messagelist.item

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.fsck.k9.ui.messagelist.MessageListAppearance
import com.fsck.k9.ui.messagelist.MessageListItem
import kotlin.time.ExperimentalTime
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList
import net.thunderbird.core.preference.avatar.SenderAvatarResolution
import net.thunderbird.core.preference.avatar.SenderAvatarSource
import net.thunderbird.feature.mail.message.list.preferences.MessageListPreferences
import net.thunderbird.feature.mail.message.list.ui.component.config.MessageItemAccountIndicator
import net.thunderbird.feature.mail.message.list.ui.component.organism.ReadMessageItem
import net.thunderbird.feature.mail.message.list.ui.component.organism.UnreadMessageItem
import net.thunderbird.feature.mail.message.list.ui.state.Account
import net.thunderbird.feature.mail.message.list.ui.state.Avatar
import net.thunderbird.feature.mail.message.list.ui.state.ComposedAddressStyle
import net.thunderbird.feature.mail.message.list.ui.state.ComposedAddressUi
import net.thunderbird.feature.mail.message.list.ui.state.MessageItemUi

@Suppress("LongParameterList", "LongMethod")
@OptIn(ExperimentalTime::class)
@Composable
@Deprecated("Don't use. Will be removed soon.")
internal fun MessageItemContent(
    item: MessageListItem,
    isActive: Boolean,
    isSelected: Boolean,
    avatarResolution: SenderAvatarResolution,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onAvatarClick: () -> Unit,
    onFavouriteClick: (Boolean) -> Unit,
    appearance: MessageListAppearance,
) {
    val messageItemUi = rememberMessageItemUi(
        item = item,
        showContactPicture = appearance.showContactPicture,
        isSelected = isSelected,
        isActive = isActive,
        avatarResolution = avatarResolution,
    )

    val preferences = remember(appearance) {
        MessageListPreferences(
            density = appearance.density,
            groupConversations = appearance.showingThreadedList,
            showCorrespondentNames = false,
            showMessageAvatar = appearance.showContactPicture,
            showFavouriteButton = appearance.stars,
            senderAboveSubject = appearance.senderAboveSubject,
            excerptLines = appearance.previewLines,
            dateTimeFormat = appearance.dateTimeFormat,
            colorizeBackgroundWhenRead = appearance.backGroundAsReadIndicator,
        )
    }
    val accountIndicator = remember(appearance) {
        if (appearance.showAccountIndicator) {
            MessageItemAccountIndicator(color = Color(item.account.profile.color))
        } else {
            null
        }
    }
    when {
        item.isRead -> ReadMessageItem(
            state = messageItemUi,
            preferences = preferences,
            accountIndicator = accountIndicator,
            onClick = onClick,
            onLongClick = onLongClick,
            onAvatarClick = onAvatarClick,
            onFavouriteChange = onFavouriteClick,
        )

        else -> UnreadMessageItem(
            state = messageItemUi,
            preferences = preferences,
            accountIndicator = accountIndicator,
            onClick = onClick,
            onLongClick = onLongClick,
            onAvatarClick = onAvatarClick,
            onFavouriteChange = onFavouriteClick,
        )
    }
}

@Composable
private fun rememberMessageItemUi(
    item: MessageListItem,
    showContactPicture: Boolean,
    isSelected: Boolean,
    isActive: Boolean,
    avatarResolution: SenderAvatarResolution,
): MessageItemUi = remember(item, showContactPicture, isSelected, isActive, avatarResolution) {
    item.toMessageItemUi(showContactPicture, isSelected, isActive, avatarResolution)
}

internal fun MessageListItem.toMessageItemUi(
    showContactPicture: Boolean,
    isSelected: Boolean,
    isActive: Boolean,
    avatarResolution: SenderAvatarResolution,
): MessageItemUi = MessageItemUi(
    state = if (isRead) MessageItemUi.State.Read else MessageItemUi.State.Unread,
    id = messageUid,
    messageReference = messageReference.toIdentityString(),
    account = Account(
        id = account.id,
        color = Color(account.profile.color),
    ),
    senders = ComposedAddressUi(
        displayName = displayAddress?.address ?: "",
        displayNameStyles = buildSenderStyles(),
        avatar = avatarResolution.toComposeAvatar().takeIf { showContactPicture },
        color = Color(contactColor),
    ),
    subject = subject ?: "n/a",
    excerpt = previewText,
    formattedReceivedAt = displayMessageDateTime,
    hasAttachments = hasAttachments,
    starred = isStarred,
    encrypted = isMessageEncrypted,
    answered = isAnswered,
    forwarded = isForwarded,
    selected = isSelected,
    threadCount = threadCount,
    active = isActive,
)

private fun SenderAvatarResolution.toComposeAvatar(): Avatar? = when (this) {
    SenderAvatarResolution.Hidden -> null
    is SenderAvatarResolution.Visible -> when (val source = source) {
        is SenderAvatarSource.Contact -> Avatar.Image(source.image.key)
        is SenderAvatarSource.VerifiedBrand -> Avatar.Image(source.image.key)
        is SenderAvatarSource.Monogram -> Avatar.Monogram(source.value)
        SenderAvatarSource.Generic -> null
    }
}

internal fun MessageListItem.buildSenderStyles(): ImmutableList<ComposedAddressStyle> = buildList {
    when (val separatorIndex = displayName.indexOf(',')) {
        -1 if !isRead -> add(ComposedAddressStyle.Bold(start = 0))
        in 0..Int.MAX_VALUE if !isRead -> {
            add(ComposedAddressStyle.Bold(start = 0, end = separatorIndex))
            add(ComposedAddressStyle.Regular(start = separatorIndex))
        }

        else -> add(ComposedAddressStyle.Regular(start = 0))
    }
}.toPersistentList()
