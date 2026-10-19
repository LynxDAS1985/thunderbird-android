package net.thunderbird.core.preference.avatar

/**
 * Pure resolver shared by list, message view, and notification adapters.
 *
 * A brand image and green badge are returned only when trusted evidence belongs to the exact message in the request.
 */
class DefaultSenderAvatarResolver : SenderAvatarResolver {
    override fun resolve(request: SenderAvatarRequest): SenderAvatarResolution {
        if (!request.settings.isEnabled) return SenderAvatarResolution.Hidden
        if (!request.settings.isEnabledFor(request.surface)) return SenderAvatarResolution.Hidden

        val hasContacts = request.settings.isSourceContactsEnabled
        val hasBimi = request.settings.isSourceBimiEnabled

        val contactImage = if (hasContacts) request.contactImage else null
        val verifiedBrandImage = if (hasBimi) request.verifiedBrandImage else null
        val verification = if (hasBimi) request.verification.validFor(request.message) else null

        val badge = if (request.settings.isShowVerifiedShield && verification != null) {
            SenderVerificationBadge.Verified(verification.methods)
        } else {
            SenderVerificationBadge.None
        }

        if (request.settings.priority == SenderAvatarPriority.BIMI_FIRST) {
            if (verifiedBrandImage != null && verification != null) {
                return SenderAvatarResolution.Visible(
                    source = SenderAvatarSource.VerifiedBrand(verifiedBrandImage),
                    badge = badge,
                )
            }
            if (contactImage != null) {
                return SenderAvatarResolution.Visible(SenderAvatarSource.Contact(contactImage))
            }
        } else {
            if (contactImage != null) {
                return SenderAvatarResolution.Visible(SenderAvatarSource.Contact(contactImage))
            }
            if (verifiedBrandImage != null && verification != null) {
                return SenderAvatarResolution.Visible(
                    source = SenderAvatarSource.VerifiedBrand(verifiedBrandImage),
                    badge = badge,
                )
            }
        }

        if (request.settings.fallbackStyle == SenderAvatarFallbackStyle.DEFAULT_ICON) {
            return SenderAvatarResolution.Visible(SenderAvatarSource.Generic)
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
