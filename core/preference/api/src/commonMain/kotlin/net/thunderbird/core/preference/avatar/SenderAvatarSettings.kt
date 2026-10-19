package net.thunderbird.core.preference.avatar

import net.thunderbird.core.preference.display.visualSettings.message.list.MESSAGE_LIST_SETTINGS_DEFAULT_IS_COLORIZE_MISSING_CONTACT_PICTURE
import net.thunderbird.core.preference.display.visualSettings.message.list.MESSAGE_LIST_SETTINGS_DEFAULT_IS_SHOW_CONTACT_PICTURE
import net.thunderbird.core.preference.notification.NOTIFICATION_PREFERENCE_DEFAULT_IS_SHOW_CONTACT_PICTURE_IN_NOTIFICATION

const val SENDER_AVATAR_SETTINGS_DEFAULT_IS_ENABLED = true
const val SENDER_AVATAR_SETTINGS_DEFAULT_IS_SOURCE_CONTACTS_ENABLED = true
const val SENDER_AVATAR_SETTINGS_DEFAULT_IS_SOURCE_BIMI_ENABLED = true
const val SENDER_AVATAR_SETTINGS_DEFAULT_IS_SHOW_IN_MESSAGE_LIST =
    MESSAGE_LIST_SETTINGS_DEFAULT_IS_SHOW_CONTACT_PICTURE
const val SENDER_AVATAR_SETTINGS_DEFAULT_IS_SHOW_IN_MESSAGE_VIEW = true
const val SENDER_AVATAR_SETTINGS_DEFAULT_IS_COLORIZE_MISSING =
    MESSAGE_LIST_SETTINGS_DEFAULT_IS_COLORIZE_MISSING_CONTACT_PICTURE
const val SENDER_AVATAR_SETTINGS_DEFAULT_IS_SHOW_IN_NOTIFICATIONS =
    NOTIFICATION_PREFERENCE_DEFAULT_IS_SHOW_CONTACT_PICTURE_IN_NOTIFICATION
const val SENDER_AVATAR_SETTINGS_DEFAULT_IS_SHOW_VERIFIED_SHIELD = true
const val SENDER_AVATAR_SETTINGS_DEFAULT_IS_BIMI_WIFI_ONLY = false

enum class SenderAvatarPriority {
    CONTACTS_FIRST,
    BIMI_FIRST,
}

enum class SenderAvatarFallbackStyle {
    COLORED_MONOGRAM,
    DEFAULT_ICON,
}

/**
 * Settings controlling sender avatars across the application.
 *
 * These values mirror the established storage keys and defaults as the source of truth while allowing a single
 * settings screen to manage all avatar display, sources, priority, and fallback mechanisms together.
 */
data class SenderAvatarSettings(
    val isEnabled: Boolean = SENDER_AVATAR_SETTINGS_DEFAULT_IS_ENABLED,
    val isSourceContactsEnabled: Boolean = SENDER_AVATAR_SETTINGS_DEFAULT_IS_SOURCE_CONTACTS_ENABLED,
    val isSourceBimiEnabled: Boolean = SENDER_AVATAR_SETTINGS_DEFAULT_IS_SOURCE_BIMI_ENABLED,
    val priority: SenderAvatarPriority = SenderAvatarPriority.CONTACTS_FIRST,
    val isShowInMessageList: Boolean = SENDER_AVATAR_SETTINGS_DEFAULT_IS_SHOW_IN_MESSAGE_LIST,
    val isShowInMessageView: Boolean = SENDER_AVATAR_SETTINGS_DEFAULT_IS_SHOW_IN_MESSAGE_VIEW,
    val isColorizeMissing: Boolean = SENDER_AVATAR_SETTINGS_DEFAULT_IS_COLORIZE_MISSING,
    val fallbackStyle: SenderAvatarFallbackStyle = SenderAvatarFallbackStyle.COLORED_MONOGRAM,
    val isShowInNotifications: Boolean = SENDER_AVATAR_SETTINGS_DEFAULT_IS_SHOW_IN_NOTIFICATIONS,
    val isShowVerifiedShield: Boolean = SENDER_AVATAR_SETTINGS_DEFAULT_IS_SHOW_VERIFIED_SHIELD,
    val isBimiWifiOnly: Boolean = SENDER_AVATAR_SETTINGS_DEFAULT_IS_BIMI_WIFI_ONLY,
)
