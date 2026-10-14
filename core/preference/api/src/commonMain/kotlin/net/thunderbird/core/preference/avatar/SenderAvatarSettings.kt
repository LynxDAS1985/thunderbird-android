package net.thunderbird.core.preference.avatar

import net.thunderbird.core.preference.display.visualSettings.message.list.MESSAGE_LIST_SETTINGS_DEFAULT_IS_COLORIZE_MISSING_CONTACT_PICTURE
import net.thunderbird.core.preference.display.visualSettings.message.list.MESSAGE_LIST_SETTINGS_DEFAULT_IS_SHOW_CONTACT_PICTURE
import net.thunderbird.core.preference.notification.NOTIFICATION_PREFERENCE_DEFAULT_IS_SHOW_CONTACT_PICTURE_IN_NOTIFICATION

const val SENDER_AVATAR_SETTINGS_DEFAULT_IS_SHOW_IN_MESSAGE_LIST =
    MESSAGE_LIST_SETTINGS_DEFAULT_IS_SHOW_CONTACT_PICTURE
const val SENDER_AVATAR_SETTINGS_DEFAULT_IS_COLORIZE_MISSING =
    MESSAGE_LIST_SETTINGS_DEFAULT_IS_COLORIZE_MISSING_CONTACT_PICTURE
const val SENDER_AVATAR_SETTINGS_DEFAULT_IS_SHOW_IN_NOTIFICATIONS =
    NOTIFICATION_PREFERENCE_DEFAULT_IS_SHOW_CONTACT_PICTURE_IN_NOTIFICATION

/**
 * Settings controlling sender avatars across the application.
 *
 * These values intentionally mirror the existing message list and notification preferences. This keeps the
 * established storage keys and defaults as the source of truth while allowing a single settings screen to manage
 * them together.
 */
data class SenderAvatarSettings(
    val isShowInMessageList: Boolean = SENDER_AVATAR_SETTINGS_DEFAULT_IS_SHOW_IN_MESSAGE_LIST,
    val isColorizeMissing: Boolean = SENDER_AVATAR_SETTINGS_DEFAULT_IS_COLORIZE_MISSING,
    val isShowInNotifications: Boolean = SENDER_AVATAR_SETTINGS_DEFAULT_IS_SHOW_IN_NOTIFICATIONS,
)
