package com.fsck.k9.notification

import android.app.Application
import android.app.Notification
import android.app.PendingIntent
import android.graphics.Bitmap
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import app.k9mail.legacy.message.controller.MessageReference
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.fsck.k9.mail.Address
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.thunderbird.core.android.account.LegacyAccountDto
import net.thunderbird.core.android.testing.MockHelper.mockBuilder
import net.thunderbird.core.android.testing.RobolectricTest
import net.thunderbird.core.preference.avatar.SenderAvatarSettings
import net.thunderbird.core.preference.avatar.SenderAvatarSettingsPreferenceManager
import net.thunderbird.components.ui.testing.coroutines.MainDispatcherHelper
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class SingleMessageNotificationCreatorTest : RobolectricTest() {
    private val mainDispatcher = MainDispatcherHelper(UnconfinedTestDispatcher())
    private val senderAvatarSettingsPreferenceManager = FakeSenderAvatarSettingsPreferenceManager()
    private val resourceProvider = TestAvatarNotificationResourceProvider()
    private val notification = mock<Notification>()
    private val builder = mockBuilder<NotificationCompat.Builder> {
        on { build() } doReturn notification
    }

    private lateinit var testSubject: SingleMessageNotificationCreator

    @Before
    fun setUp() {
        mainDispatcher.setUp()
        testSubject = SingleMessageNotificationCreator(
            notificationHelper = createNotificationHelper(),
            actionCreator = createNotificationActionCreator(),
            resourceProvider = resourceProvider,
            lockScreenNotificationCreator = mock(),
            senderAvatarSettingsPreferenceManager = senderAvatarSettingsPreferenceManager,
            application = ApplicationProvider.getApplicationContext<Application>(),
        )
    }

    @After
    fun tearDown() {
        mainDispatcher.tearDown()
    }

    @Test
    fun `create notification looks up avatar when notification contact pictures are enabled`() = runTest {
        senderAvatarSettingsPreferenceManager.setShowInNotifications(true)

        testSubject.createSingleNotification(
            baseNotificationData = createBaseNotificationData(),
            singleNotificationData = createSingleNotificationData(),
        ).join()

        assertThat(resourceProvider.avatarCalls).isEqualTo(1)
        assertThat(resourceProvider.lastMessageReference).isEqualTo(MessageReference("account", 1, "uid"))
        assertThat(resourceProvider.lastSettings?.isShowInNotifications).isEqualTo(true)
    }

    @Test
    fun `create notification skips avatar lookup when notification contact pictures are disabled`() = runTest {
        senderAvatarSettingsPreferenceManager.setShowInNotifications(false)

        testSubject.createSingleNotification(
            baseNotificationData = createBaseNotificationData(),
            singleNotificationData = createSingleNotificationData(),
        ).join()

        assertThat(resourceProvider.avatarCalls).isEqualTo(0)
    }

    private fun createNotificationHelper(): NotificationHelper {
        return mock {
            on { createNotificationBuilder(any(), any()) } doReturn builder
        }
    }

    private fun createNotificationActionCreator(): NotificationActionCreator {
        val pendingIntent = mock<PendingIntent>()
        return mock {
            on { createViewMessagePendingIntent(any()) } doReturn pendingIntent
            on { createDismissMessagePendingIntent(any()) } doReturn pendingIntent
        }
    }

    private fun createBaseNotificationData(): BaseNotificationData {
        return BaseNotificationData(
            account = LegacyAccountDto("00000000-0000-0000-0000-000000000000"),
            accountName = "Account name",
            groupKey = "group",
            color = 0,
            newMessagesCount = 1,
            lockScreenNotificationData = LockScreenNotificationData.None,
            appearance = NotificationAppearance(
                ringtone = null,
                vibrationPattern = null,
                ledColor = null,
            ),
        )
    }

    private fun createSingleNotificationData(): SingleNotificationData {
        return SingleNotificationData(
            notificationId = 23,
            isSilent = true,
            timestamp = 9000,
            content = NotificationContent(
                messageReference = MessageReference("account", 1, "uid"),
                sender = Address("alice@example.com", "Alice"),
                subject = "Subject",
                preview = "Preview",
                summary = "Summary",
            ),
            actions = emptyList(),
            wearActions = emptyList(),
            addLockScreenNotification = false,
        )
    }

    private class TestAvatarNotificationResourceProvider :
        NotificationResourceProvider by TestNotificationResourceProvider() {
        var avatarCalls = 0
        var lastMessageReference: MessageReference? = null
        var lastSettings: SenderAvatarSettings? = null

        override suspend fun avatar(
            address: Address,
            messageReference: MessageReference,
            settings: SenderAvatarSettings,
        ): Bitmap? {
            avatarCalls += 1
            lastMessageReference = messageReference
            lastSettings = settings
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
    }

    private class FakeSenderAvatarSettingsPreferenceManager : SenderAvatarSettingsPreferenceManager {
        private val prefs = MutableStateFlow(SenderAvatarSettings())

        override fun save(config: SenderAvatarSettings) {
            prefs.value = config
        }

        override fun getConfig(): SenderAvatarSettings = prefs.value

        override fun getConfigFlow(): Flow<SenderAvatarSettings> = prefs

        fun setShowInNotifications(isEnabled: Boolean) {
            prefs.update { it.copy(isShowInNotifications = isEnabled) }
        }
    }
}
