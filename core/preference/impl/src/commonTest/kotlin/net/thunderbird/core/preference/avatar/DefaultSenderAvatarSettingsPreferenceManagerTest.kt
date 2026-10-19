package net.thunderbird.core.preference.avatar

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.thunderbird.core.preference.PreferenceManager
import net.thunderbird.core.preference.display.visualSettings.message.list.DisplayMessageListSettings
import net.thunderbird.core.preference.display.visualSettings.message.list.MessageListPreferencesManager
import net.thunderbird.core.preference.notification.NotificationPreference
import net.thunderbird.core.preference.notification.NotificationPreferenceManager

class DefaultSenderAvatarSettingsPreferenceManagerTest {

    @Test
    fun `getConfig should preserve every combination from an existing installation`() = runTest {
        listOf(false, true).forEach { showInMessageList ->
            listOf(false, true).forEach { colorizeMissing ->
                listOf(false, true).forEach { showInNotifications ->
                    val manager = createManager(
                        messageListSettings = DisplayMessageListSettings(
                            isShowContactPicture = showInMessageList,
                            isColorizeMissingContactPictures = colorizeMissing,
                        ),
                        notificationSettings = NotificationPreference(
                            isShowContactPictureInNotification = showInNotifications,
                        ),
                        scope = backgroundScope,
                    )

                    assertThat(manager.getConfig()).isEqualTo(
                        SenderAvatarSettings(
                            isShowInMessageList = showInMessageList,
                            isColorizeMissing = colorizeMissing,
                            isShowInNotifications = showInNotifications,
                        ),
                    )
                }
            }
        }
    }

    @Test
    fun `getConfig should map existing preferences`() = runTest {
        val manager = createManager(
            messageListSettings = DisplayMessageListSettings(
                isShowContactPicture = false,
                isColorizeMissingContactPictures = true,
            ),
            notificationSettings = NotificationPreference(isShowContactPictureInNotification = false),
            scope = backgroundScope,
        )

        assertThat(manager.getConfig()).isEqualTo(
            SenderAvatarSettings(
                isShowInMessageList = false,
                isColorizeMissing = true,
                isShowInNotifications = false,
            ),
        )
    }

    @Test
    fun `save should update avatar preferences and preserve unrelated settings`() = runTest {
        val messageListManager = FakeMessageListPreferencesManager(
            DisplayMessageListSettings(previewLines = 7),
        )
        val notificationManager = FakeNotificationPreferenceManager(
            NotificationPreference(quietTimeStarts = "23:15"),
        )
        val manager = createManager(messageListManager, notificationManager, backgroundScope)

        manager.save(
            SenderAvatarSettings(
                isShowInMessageList = false,
                isColorizeMissing = false,
                isShowInNotifications = false,
            ),
        )

        assertThat(messageListManager.getConfig()).isEqualTo(
            DisplayMessageListSettings(
                isShowContactPicture = false,
                isColorizeMissingContactPictures = false,
                previewLines = 7,
            ),
        )
        assertThat(notificationManager.getConfig()).isEqualTo(
            NotificationPreference(
                isShowContactPictureInNotification = false,
                quietTimeStarts = "23:15",
            ),
        )
    }

    @Test
    fun `save should not expose mixed state while notification save is delayed`() = runTest {
        val messageListManager = FakeMessageListPreferencesManager(DisplayMessageListSettings())
        val notificationManager = DelayedNotificationPreferenceManager(NotificationPreference())
        val manager = createManager(messageListManager, notificationManager, backgroundScope)
        val expected = SenderAvatarSettings(
            isShowInMessageList = false,
            isColorizeMissing = false,
            isShowInNotifications = false,
        )

        manager.save(expected)
        advanceUntilIdle()

        assertThat(messageListManager.getConfig().isShowContactPicture).isEqualTo(false)
        assertThat(notificationManager.getConfig().isShowContactPictureInNotification).isEqualTo(true)
        assertThat(manager.getConfig()).isEqualTo(expected)

        notificationManager.completeSave()
        advanceUntilIdle()

        assertThat(manager.getConfig()).isEqualTo(expected)
    }

    @Test
    fun `flow should reflect changes from existing preference managers`() = runTest {
        val messageListManager = FakeMessageListPreferencesManager(DisplayMessageListSettings())
        val notificationManager = FakeNotificationPreferenceManager(NotificationPreference())
        val manager = createManager(messageListManager, notificationManager, backgroundScope)

        manager.getConfigFlow().test {
            assertThat(awaitItem()).isEqualTo(SenderAvatarSettings())

            messageListManager.save(
                messageListManager.getConfig().copy(
                    isShowContactPicture = false,
                    isColorizeMissingContactPictures = false,
                ),
            )
            assertThat(awaitItem()).isEqualTo(
                SenderAvatarSettings(
                    isShowInMessageList = false,
                    isColorizeMissing = false,
                ),
            )

            notificationManager.save(
                notificationManager.getConfig().copy(isShowContactPictureInNotification = false),
            )
            assertThat(awaitItem()).isEqualTo(
                SenderAvatarSettings(
                    isShowInMessageList = false,
                    isColorizeMissing = false,
                    isShowInNotifications = false,
                ),
            )
        }
    }

    @Test
    fun `save failure should reset pending state and rollback configState`() = runTest {
        val failingManager = object :
            FakePreferenceManager<DisplayMessageListSettings>(DisplayMessageListSettings()),
            MessageListPreferencesManager {
            override fun save(config: DisplayMessageListSettings) {
                throw IllegalStateException("Disk full")
            }
        }
        val notificationManager = FakeNotificationPreferenceManager(NotificationPreference())
        val manager = createManager(failingManager, notificationManager, backgroundScope)
        val initialConfig = manager.getConfig()

        kotlin.test.assertFailsWith<IllegalStateException> {
            manager.save(
                SenderAvatarSettings(
                    isShowInMessageList = false,
                    isColorizeMissing = false,
                    isShowInNotifications = false,
                ),
            )
        }

        assertThat(manager.getConfig()).isEqualTo(initialConfig)
    }

    private fun createManager(
        messageListSettings: DisplayMessageListSettings,
        notificationSettings: NotificationPreference,
        scope: CoroutineScope,
    ): DefaultSenderAvatarSettingsPreferenceManager = createManager(
        FakeMessageListPreferencesManager(messageListSettings),
        FakeNotificationPreferenceManager(notificationSettings),
        scope,
    )

    private fun createManager(
        messageListManager: MessageListPreferencesManager,
        notificationManager: NotificationPreferenceManager,
        scope: CoroutineScope,
    ): DefaultSenderAvatarSettingsPreferenceManager = DefaultSenderAvatarSettingsPreferenceManager(
        messageListPreferencesManager = messageListManager,
        notificationPreferenceManager = notificationManager,
        scope = scope,
    )
}

private class FakeMessageListPreferencesManager(
    initialConfig: DisplayMessageListSettings,
) : FakePreferenceManager<DisplayMessageListSettings>(initialConfig), MessageListPreferencesManager

private class FakeNotificationPreferenceManager(
    initialConfig: NotificationPreference,
) : FakePreferenceManager<NotificationPreference>(initialConfig), NotificationPreferenceManager

private class DelayedNotificationPreferenceManager(
    initialConfig: NotificationPreference,
) : NotificationPreferenceManager {
    private val config = MutableStateFlow(initialConfig)
    private var pendingConfig: NotificationPreference? = null

    override fun save(config: NotificationPreference) {
        pendingConfig = config
    }

    fun completeSave() {
        config.value = checkNotNull(pendingConfig)
        pendingConfig = null
    }

    override fun getConfig(): NotificationPreference = config.value

    override fun getConfigFlow(): Flow<NotificationPreference> = config
}

private open class FakePreferenceManager<T>(initialConfig: T) : PreferenceManager<T> {
    private val config = MutableStateFlow(initialConfig)

    override fun save(config: T) {
        this.config.value = config
    }

    override fun getConfig(): T = config.value

    override fun getConfigFlow(): Flow<T> = config
}
