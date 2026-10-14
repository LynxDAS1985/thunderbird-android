package com.fsck.k9.preferences

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.fsck.k9.K9RobolectricTest
import net.thunderbird.core.preference.notification.NOTIFICATION_PREFERENCE_DEFAULT_IS_SHOW_CONTACT_PICTURE_IN_NOTIFICATION
import org.junit.Test

class GeneralSettingsDescriptionsTest : K9RobolectricTest() {
    @Test
    fun `notification contact picture setting accepts enabled value`() {
        val settings = GeneralSettingsDescriptions.validate(
            Settings.VERSION,
            mapOf(KEY to "true"),
        )

        assertThat(settings[KEY]).isEqualTo(true)
    }

    @Test
    fun `notification contact picture setting accepts disabled value`() {
        val settings = GeneralSettingsDescriptions.validate(
            Settings.VERSION,
            mapOf(KEY to "false"),
        )

        assertThat(settings[KEY]).isEqualTo(false)
    }

    @Test
    fun `backup without notification contact picture setting uses existing default`() {
        val oldVersion = Settings.VERSION - 1
        val validatedSettings = GeneralSettingsDescriptions.validate(oldVersion, emptyMap())
        val settings = GeneralSettingsDescriptions.upgrade(oldVersion, validatedSettings)

        assertThat(settings[KEY])
            .isEqualTo(NOTIFICATION_PREFERENCE_DEFAULT_IS_SHOW_CONTACT_PICTURE_IN_NOTIFICATION)
    }

    private companion object {
        const val KEY = "showContactPictureInNotification"
    }
}
