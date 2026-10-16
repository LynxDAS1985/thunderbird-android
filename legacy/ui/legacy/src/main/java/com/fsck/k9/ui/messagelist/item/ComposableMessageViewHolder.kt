package com.fsck.k9.ui.messagelist.item

import android.content.Context
import androidx.compose.ui.platform.ComposeView
import com.fsck.k9.ui.messagelist.MessageListAppearance
import com.fsck.k9.ui.messagelist.MessageListItem
import com.fsck.k9.ui.messagelist.MessageListSenderAvatarResolver
import net.thunderbird.core.ui.theme.api.FeatureThemeProvider

/**
 * A composable view holder for message list items.
 */
@Suppress("LongParameterList")
class ComposableMessageViewHolder(
    private val composeView: ComposeView,
    private val themeProvider: FeatureThemeProvider,
    private val onClick: (MessageListItem) -> Unit,
    private val onLongClick: (MessageListItem) -> Unit,
    private val onAvatarClick: (MessageListItem) -> Unit,
    private val onFavouriteClick: (MessageListItem) -> Unit,
    private val appearance: () -> MessageListAppearance,
    private val senderAvatarResolver: MessageListSenderAvatarResolver,
) : MessageListViewHolder(composeView) {

    var uniqueId: Long = -1L

    fun bind(item: MessageListItem, isActive: Boolean, isSelected: Boolean) {
        uniqueId = item.uniqueId
        val appearance = appearance()
        val avatar = senderAvatarResolver.resolve(item, appearance.showContactPicture)

        composeView.setContent {
            themeProvider.WithTheme {
                MessageItemContent(
                    item = item,
                    isActive = isActive,
                    isSelected = isSelected,
                    avatarResolution = avatar,
                    onClick = { onClick(item) },
                    onLongClick = { onLongClick(item) },
                    onAvatarClick = { onAvatarClick(item) },
                    onFavouriteClick = { onFavouriteClick(item) },
                    appearance = appearance,
                )
            }
        }
    }

    companion object {
        @Suppress("LongParameterList")
        fun create(
            context: Context,
            themeProvider: FeatureThemeProvider,
            senderAvatarResolver: MessageListSenderAvatarResolver,
            onClick: (MessageListItem) -> Unit,
            onLongClick: (MessageListItem) -> Unit,
            onFavouriteClick: (MessageListItem) -> Unit,
            onAvatarClick: (MessageListItem) -> Unit,
            appearance: () -> MessageListAppearance,
        ): ComposableMessageViewHolder {
            val composeView = ComposeView(context)

            val holder = ComposableMessageViewHolder(
                composeView = composeView,
                themeProvider = themeProvider,
                senderAvatarResolver = senderAvatarResolver,
                onClick = onClick,
                onLongClick = onLongClick,
                onAvatarClick = onAvatarClick,
                onFavouriteClick = onFavouriteClick,
                appearance = appearance,
            )

            composeView.tag = holder

            return holder
        }
    }
}
