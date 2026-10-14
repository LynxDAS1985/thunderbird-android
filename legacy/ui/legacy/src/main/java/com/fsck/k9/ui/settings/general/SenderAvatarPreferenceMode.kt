package com.fsck.k9.ui.settings.general

import net.thunderbird.core.featureflag.FeatureFlagResult

internal enum class SenderAvatarPreferenceMode {
    Unified,
    Legacy,
}

internal fun FeatureFlagResult.toSenderAvatarPreferenceMode(): SenderAvatarPreferenceMode = whenEnabledOrNot(
    onEnabled = { SenderAvatarPreferenceMode.Unified },
    onDisabledOrUnavailable = { SenderAvatarPreferenceMode.Legacy },
)
