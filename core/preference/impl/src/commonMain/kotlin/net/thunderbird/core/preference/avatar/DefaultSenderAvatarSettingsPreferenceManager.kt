package net.thunderbird.core.preference.avatar

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import net.thunderbird.core.preference.display.visualSettings.message.list.DisplayMessageListSettings
import net.thunderbird.core.preference.display.visualSettings.message.list.MessageListPreferencesManager
import net.thunderbird.core.preference.notification.NotificationPreference
import net.thunderbird.core.preference.notification.NotificationPreferenceManager
import net.thunderbird.core.preference.storage.Storage
import net.thunderbird.core.preference.storage.StorageEditor
import net.thunderbird.core.preference.storage.getEnumOrDefault

const val SENDER_AVATAR_KEY_ENABLED = "sender_avatars_enabled"
const val SENDER_AVATAR_KEY_SOURCE_CONTACTS = "sender_avatar_source_contacts"
const val SENDER_AVATAR_KEY_SOURCE_BIMI = "sender_avatar_source_bimi"
const val SENDER_AVATAR_KEY_PRIORITY = "sender_avatar_priority"
const val SENDER_AVATAR_KEY_FALLBACK_STYLE = "sender_avatar_fallback_style"
const val SENDER_AVATAR_KEY_SHOW_IN_MESSAGE_VIEW = "sender_avatar_show_in_message_view"
const val SENDER_AVATAR_KEY_SHOW_VERIFIED_SHIELD = "sender_avatar_show_verified_shield"
const val SENDER_AVATAR_KEY_WIFI_ONLY = "sender_avatar_wifi_only"

class DefaultSenderAvatarSettingsPreferenceManager(
    private val messageListPreferencesManager: MessageListPreferencesManager,
    private val notificationPreferenceManager: NotificationPreferenceManager,
    private val storage: Storage? = null,
    private val storageEditor: StorageEditor? = null,
    scope: CoroutineScope,
) : SenderAvatarSettingsPreferenceManager {

    private val configState = MutableStateFlow(loadConfig())
    private val pendingConfig = MutableStateFlow<SenderAvatarSettings?>(null)

    init {
        scope.launch {
            combine(
                messageListPreferencesManager.getConfigFlow(),
                notificationPreferenceManager.getConfigFlow(),
                pendingConfig,
            ) { messageListSettings, notificationSettings, pending ->
                val persistedConfig = createConfig(messageListSettings, notificationSettings)
                if (pending == null || pending == persistedConfig) persistedConfig else pending
            }.collect { config ->
                configState.value = config
                if (pendingConfig.value == config && loadConfig() == config) {
                    pendingConfig.value = null
                }
            }
        }
    }

    override fun save(config: SenderAvatarSettings) {
        val previousConfig = configState.value
        pendingConfig.value = config
        configState.value = config
        try {
            storageEditor?.let { editor ->
                editor.putBoolean(SENDER_AVATAR_KEY_ENABLED, config.isEnabled)
                editor.putBoolean(SENDER_AVATAR_KEY_SOURCE_CONTACTS, config.isSourceContactsEnabled)
                editor.putBoolean(SENDER_AVATAR_KEY_SOURCE_BIMI, config.isSourceBimiEnabled)
                editor.putString(SENDER_AVATAR_KEY_PRIORITY, config.priority.name)
                editor.putString(SENDER_AVATAR_KEY_FALLBACK_STYLE, config.fallbackStyle.name)
                editor.putBoolean(SENDER_AVATAR_KEY_SHOW_IN_MESSAGE_VIEW, config.isShowInMessageView)
                editor.putBoolean(SENDER_AVATAR_KEY_SHOW_VERIFIED_SHIELD, config.isShowVerifiedShield)
                editor.putBoolean(SENDER_AVATAR_KEY_WIFI_ONLY, config.isBimiWifiOnly)
                editor.commit()
            }
            messageListPreferencesManager.save(
                messageListPreferencesManager.getConfig().copy(
                    isShowContactPicture = config.isShowInMessageList,
                    isColorizeMissingContactPictures = config.isColorizeMissing,
                ),
            )
            notificationPreferenceManager.save(
                notificationPreferenceManager.getConfig().copy(
                    isShowContactPictureInNotification = config.isShowInNotifications,
                ),
            )
        } catch (e: Throwable) {
            pendingConfig.value = null
            configState.value = previousConfig
            throw e
        }
    }

    override fun getConfig(): SenderAvatarSettings = configState.value

    override fun getConfigFlow(): Flow<SenderAvatarSettings> = configState

    private fun loadConfig(): SenderAvatarSettings {
        val messageListSettings = messageListPreferencesManager.getConfig()
        val notificationSettings = notificationPreferenceManager.getConfig()

        return createConfig(messageListSettings, notificationSettings)
    }

    private fun createConfig(
        messageListSettings: DisplayMessageListSettings,
        notificationSettings: NotificationPreference,
    ): SenderAvatarSettings {
        val isEnabled = storage?.getBoolean(
            SENDER_AVATAR_KEY_ENABLED,
            SENDER_AVATAR_SETTINGS_DEFAULT_IS_ENABLED,
        ) ?: SENDER_AVATAR_SETTINGS_DEFAULT_IS_ENABLED

        val isSourceContacts = storage?.getBoolean(
            SENDER_AVATAR_KEY_SOURCE_CONTACTS,
            SENDER_AVATAR_SETTINGS_DEFAULT_IS_SOURCE_CONTACTS_ENABLED,
        ) ?: SENDER_AVATAR_SETTINGS_DEFAULT_IS_SOURCE_CONTACTS_ENABLED

        val isSourceBimi = storage?.getBoolean(
            SENDER_AVATAR_KEY_SOURCE_BIMI,
            SENDER_AVATAR_SETTINGS_DEFAULT_IS_SOURCE_BIMI_ENABLED,
        ) ?: SENDER_AVATAR_SETTINGS_DEFAULT_IS_SOURCE_BIMI_ENABLED

        val priority = storage?.getEnumOrDefault(
            SENDER_AVATAR_KEY_PRIORITY,
            SenderAvatarPriority.CONTACTS_FIRST,
        ) ?: SenderAvatarPriority.CONTACTS_FIRST

        val fallbackStyle = storage?.getEnumOrDefault(
            SENDER_AVATAR_KEY_FALLBACK_STYLE,
            SenderAvatarFallbackStyle.COLORED_MONOGRAM,
        ) ?: SenderAvatarFallbackStyle.COLORED_MONOGRAM

        val isShowInMessageView = storage?.getBoolean(
            SENDER_AVATAR_KEY_SHOW_IN_MESSAGE_VIEW,
            SENDER_AVATAR_SETTINGS_DEFAULT_IS_SHOW_IN_MESSAGE_VIEW,
        ) ?: SENDER_AVATAR_SETTINGS_DEFAULT_IS_SHOW_IN_MESSAGE_VIEW

        val isShowVerifiedShield = storage?.getBoolean(
            SENDER_AVATAR_KEY_SHOW_VERIFIED_SHIELD,
            SENDER_AVATAR_SETTINGS_DEFAULT_IS_SHOW_VERIFIED_SHIELD,
        ) ?: SENDER_AVATAR_SETTINGS_DEFAULT_IS_SHOW_VERIFIED_SHIELD

        val isBimiWifiOnly = storage?.getBoolean(
            SENDER_AVATAR_KEY_WIFI_ONLY,
            SENDER_AVATAR_SETTINGS_DEFAULT_IS_BIMI_WIFI_ONLY,
        ) ?: SENDER_AVATAR_SETTINGS_DEFAULT_IS_BIMI_WIFI_ONLY

        return SenderAvatarSettings(
            isEnabled = isEnabled,
            isSourceContactsEnabled = isSourceContacts,
            isSourceBimiEnabled = isSourceBimi,
            priority = priority,
            isShowInMessageList = messageListSettings.isShowContactPicture,
            isShowInMessageView = isShowInMessageView,
            isColorizeMissing = messageListSettings.isColorizeMissingContactPictures,
            fallbackStyle = fallbackStyle,
            isShowInNotifications = notificationSettings.isShowContactPictureInNotification,
            isShowVerifiedShield = isShowVerifiedShield,
            isBimiWifiOnly = isBimiWifiOnly,
        )
    }
}
