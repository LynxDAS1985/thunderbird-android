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

class DefaultSenderAvatarSettingsPreferenceManager(
    private val messageListPreferencesManager: MessageListPreferencesManager,
    private val notificationPreferenceManager: NotificationPreferenceManager,
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
        pendingConfig.value = config
        configState.value = config
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
        return SenderAvatarSettings(
            isShowInMessageList = messageListSettings.isShowContactPicture,
            isColorizeMissing = messageListSettings.isColorizeMissingContactPictures,
            isShowInNotifications = notificationSettings.isShowContactPictureInNotification,
        )
    }
}
