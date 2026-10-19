package com.fsck.k9.preferences.upgrader;

import java.util.Map;
import com.fsck.k9.preferences.SettingsUpgrader;
import net.thunderbird.core.preference.notification.NotificationPreferenceKt;

/**
 * Ensure <em>showContactPictureInNotification</em> has default value for backups upgraded from version 111.
 */
public class GeneralSettingsUpgraderTo112 implements SettingsUpgrader {

    @Override
    public void upgrade(Map<String, Object> settings) {
        if (!settings.containsKey("showContactPictureInNotification")) {
            settings.put(
                "showContactPictureInNotification",
                NotificationPreferenceKt.NOTIFICATION_PREFERENCE_DEFAULT_IS_SHOW_CONTACT_PICTURE_IN_NOTIFICATION
            );
        }
    }
}

