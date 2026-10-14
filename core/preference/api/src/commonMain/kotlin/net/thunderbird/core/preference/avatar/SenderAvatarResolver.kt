package net.thunderbird.core.preference.avatar

/** Selects one safe avatar representation for every place where a sender is shown. */
interface SenderAvatarResolver {
    fun resolve(request: SenderAvatarRequest): SenderAvatarResolution
}

enum class SenderAvatarSurface {
    MESSAGE_LIST,
    MESSAGE_VIEW,
    NOTIFICATION,
}

/**
 * Stable identity of one stored message.
 *
 * Verification results are accepted only when all three fields match the message being rendered.
 */
data class SenderMessageIdentity(
    val accountId: String,
    val folderId: String,
    val messageId: String,
) {
    init {
        require(accountId.isNotBlank()) { "accountId must not be blank" }
        require(folderId.isNotBlank()) { "folderId must not be blank" }
        require(messageId.isNotBlank()) { "messageId must not be blank" }
    }
}

/** Opaque reference interpreted by the Android image loader at the integration boundary. */
data class SenderAvatarImage(
    val key: String,
) {
    init {
        require(key.isNotBlank()) { "key must not be blank" }
    }
}

data class SenderAvatarRequest(
    val message: SenderMessageIdentity,
    val surface: SenderAvatarSurface,
    val settings: SenderAvatarSettings,
    val contactImage: SenderAvatarImage? = null,
    val verifiedBrandImage: SenderAvatarImage? = null,
    val monogram: String? = null,
    val verification: SenderVerificationResult = SenderVerificationResult.NotChecked,
)

sealed interface SenderAvatarResolution {
    data object Hidden : SenderAvatarResolution

    data class Visible(
        val source: SenderAvatarSource,
        val badge: SenderVerificationBadge = SenderVerificationBadge.None,
    ) : SenderAvatarResolution
}

sealed interface SenderAvatarSource {
    data class Contact(val image: SenderAvatarImage) : SenderAvatarSource

    data class VerifiedBrand(val image: SenderAvatarImage) : SenderAvatarSource

    data class Monogram(val value: String, val useContactColor: Boolean) : SenderAvatarSource

    data object Generic : SenderAvatarSource
}

sealed interface SenderVerificationBadge {
    data object None : SenderVerificationBadge

    data class Verified(val methods: Set<SenderVerificationMethod>) : SenderVerificationBadge
}
