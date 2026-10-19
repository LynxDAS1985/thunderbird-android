package net.thunderbird.core.preference.avatar

/**
 * Pure resolver shared by list, message view, and notification adapters.
 *
 * A brand image and green badge are returned only when trusted evidence belongs to the exact message in the request.
 */
class DefaultSenderAvatarResolver : SenderAvatarResolver {
    override fun resolve(request: SenderAvatarRequest): SenderAvatarResolution {
        if (!request.settings.isEnabledFor(request.surface)) return SenderAvatarResolution.Hidden

        request.contactImage?.let { image ->
            return SenderAvatarResolution.Visible(SenderAvatarSource.Contact(image))
        }

        val verification = request.verification.validFor(request.message)
        val verifiedBrandImage = request.verifiedBrandImage
        if (verifiedBrandImage != null && verification != null) {
            return SenderAvatarResolution.Visible(
                source = SenderAvatarSource.VerifiedBrand(verifiedBrandImage),
                badge = SenderVerificationBadge.Verified(verification.methods),
            )
        }

        val monogram = request.monogram?.trim()?.takeIf(String::isNotEmpty)
        return if (monogram != null) {
            SenderAvatarResolution.Visible(
                SenderAvatarSource.Monogram(
                    value = monogram,
                    useContactColor = request.settings.isColorizeMissing,
                ),
            )
        } else {
            SenderAvatarResolution.Visible(SenderAvatarSource.Generic)
        }
    }

    private fun SenderAvatarSettings.isEnabledFor(surface: SenderAvatarSurface): Boolean = when (surface) {
        SenderAvatarSurface.MESSAGE_LIST -> isShowInMessageList
        SenderAvatarSurface.MESSAGE_VIEW -> isShowInMessageView
        SenderAvatarSurface.NOTIFICATION -> isShowInNotifications
    }

    private fun SenderVerificationResult.validFor(
        message: SenderMessageIdentity,
    ): SenderVerificationResult.Verified? {
        return (this as? SenderVerificationResult.Verified)?.takeIf { it.message == message }
    }
}
