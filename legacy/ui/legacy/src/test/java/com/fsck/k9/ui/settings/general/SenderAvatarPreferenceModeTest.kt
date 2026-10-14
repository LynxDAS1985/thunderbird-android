package com.fsck.k9.ui.settings.general

import assertk.assertThat
import assertk.assertions.isEqualTo
import net.thunderbird.core.featureflag.FeatureFlagResult
import org.junit.Test

class SenderAvatarPreferenceModeTest {
    @Test
    fun `enabled flag uses unified preferences`() {
        val result = FeatureFlagResult.Enabled.toSenderAvatarPreferenceMode()

        assertThat(result).isEqualTo(SenderAvatarPreferenceMode.Unified)
    }

    @Test
    fun `disabled flag keeps legacy preferences`() {
        val result = FeatureFlagResult.Disabled.toSenderAvatarPreferenceMode()

        assertThat(result).isEqualTo(SenderAvatarPreferenceMode.Legacy)
    }

    @Test
    fun `unavailable flag keeps legacy preferences`() {
        val result = FeatureFlagResult.Unavailable.toSenderAvatarPreferenceMode()

        assertThat(result).isEqualTo(SenderAvatarPreferenceMode.Legacy)
    }
}
