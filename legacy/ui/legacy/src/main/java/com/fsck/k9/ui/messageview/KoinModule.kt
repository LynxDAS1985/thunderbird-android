package com.fsck.k9.ui.messageview

import org.koin.dsl.module

val messageViewUiModule = module {
    factory {
        MessageViewSenderAvatarResolver(
            contactRepository = get(),
            senderAvatarResolver = get(),
            settingsManager = get(),
        )
    }
    factory {
        createMessageViewRecipientFormatter(
            contactNameProvider = get(),
            resources = get(),
            messageListPreferencesManager = get(),
        )
    }
}
