package net.thunderbird.core.preference.avatar

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import net.thunderbird.core.preference.display.visualSettings.message.list.MESSAGE_LIST_SETTINGS_DEFAULT_IS_COLORIZE_MISSING_CONTACT_PICTURE
import net.thunderbird.core.preference.display.visualSettings.message.list.MESSAGE_LIST_SETTINGS_DEFAULT_IS_SHOW_CONTACT_PICTURE
import net.thunderbird.core.preference.notification.NOTIFICATION_PREFERENCE_DEFAULT_IS_SHOW_CONTACT_PICTURE_IN_NOTIFICATION

class SenderAvatarSettingsTest {

    @Test
    fun `defaults should match existing avatar preferences`() {
        val settings = SenderAvatarSettings()

        assertThat(settings.isShowInMessageList)
            .isEqualTo(MESSAGE_LIST_SETTINGS_DEFAULT_IS_SHOW_CONTACT_PICTURE)
        assertThat(settings.isColorizeMissing)
            .isEqualTo(MESSAGE_LIST_SETTINGS_DEFAULT_IS_COLORIZE_MISSING_CONTACT_PICTURE)
        assertThat(settings.isShowInNotifications)
            .isEqualTo(NOTIFICATION_PREFERENCE_DEFAULT_IS_SHOW_CONTACT_PICTURE_IN_NOTIFICATION)
    }
}
