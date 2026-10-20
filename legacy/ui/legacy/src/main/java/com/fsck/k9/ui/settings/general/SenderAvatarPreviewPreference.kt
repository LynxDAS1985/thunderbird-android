package com.fsck.k9.ui.settings.general

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import app.k9mail.core.ui.legacy.designsystem.atom.icon.Icons
import com.fsck.k9.ui.R

class SenderAvatarPreviewPreference
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle,
    defStyleRes: Int = 0,
) : Preference(context, attrs, defStyleAttr, defStyleRes) {

    private var isMasterEnabled: Boolean = true
    private var isContactsEnabled: Boolean = true
    private var isBimiEnabled: Boolean = true
    private var priority: String = "CONTACTS_FIRST"
    private var fallbackStyle: String = "COLORED_MONOGRAM"
    private var isShowShield: Boolean = true

    init {
        layoutResource = R.layout.preference_sender_avatar_preview
        isSelectable = false
    }

    fun updateState(
        isMasterEnabled: Boolean,
        isContactsEnabled: Boolean,
        isBimiEnabled: Boolean,
        priority: String,
        fallbackStyle: String,
        isShowShield: Boolean,
    ) {
        if (
            this.isMasterEnabled != isMasterEnabled ||
            this.isContactsEnabled != isContactsEnabled ||
            this.isBimiEnabled != isBimiEnabled ||
            this.priority != priority ||
            this.fallbackStyle != fallbackStyle ||
            this.isShowShield != isShowShield
        ) {
            this.isMasterEnabled = isMasterEnabled
            this.isContactsEnabled = isContactsEnabled
            this.isBimiEnabled = isBimiEnabled
            this.priority = priority
            this.fallbackStyle = fallbackStyle
            this.isShowShield = isShowShield
            notifyChanged()
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val avatarContainer = holder.findViewById(R.id.preview_avatar_container)
        val monogramView = holder.findViewById(R.id.preview_avatar_monogram) as? TextView
        val iconView = holder.findViewById(R.id.preview_avatar_icon) as? ImageView
        val badgeView = holder.findViewById(R.id.preview_avatar_badge) as? ImageView
        val explanationView = holder.findViewById(R.id.preview_explanation) as? TextView

        if (!isMasterEnabled) {
            avatarContainer?.visibility = View.GONE
            explanationView?.setText(R.string.sender_avatar_preview_explanation_disabled)
            return
        }

        avatarContainer?.visibility = View.VISIBLE

        val badgeBg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(resolveThemeColor(context, android.R.attr.colorBackground, Color.WHITE))
        }
        badgeView?.background = badgeBg

        val isBimiActive = isBimiEnabled && (priority == "BIMI_FIRST" || !isContactsEnabled)
        val isContactsActive = isContactsEnabled && (priority == "CONTACTS_FIRST" || !isBimiEnabled)

        when {
            isBimiActive -> {
                monogramView?.visibility = View.GONE
                iconView?.visibility = View.VISIBLE
                iconView?.setImageResource(Icons.Outlined.AccountCircle)
                iconView?.setColorFilter(
                    resolveThemeColor(context, androidx.appcompat.R.attr.colorPrimary, 0xFF1976D2.toInt()),
                )
                badgeView?.visibility = if (isShowShield) View.VISIBLE else View.GONE
                explanationView?.setText(R.string.sender_avatar_preview_explanation_brand)
            }
            isContactsActive -> {
                monogramView?.visibility = View.GONE
                iconView?.visibility = View.VISIBLE
                iconView?.setImageResource(Icons.Outlined.AccountCircle)
                iconView?.clearColorFilter()
                badgeView?.visibility = View.GONE
                explanationView?.setText(R.string.sender_avatar_preview_explanation_contact)
            }
            else -> {
                badgeView?.visibility = View.GONE
                if (fallbackStyle == "COLORED_MONOGRAM") {
                    iconView?.visibility = View.GONE
                    monogramView?.visibility = View.VISIBLE
                    monogramView?.text = "G"
                    val circle = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(0xFF2E7D32.toInt())
                    }
                    monogramView?.background = circle
                    explanationView?.setText(R.string.sender_avatar_preview_explanation_monogram)
                } else {
                    monogramView?.visibility = View.GONE
                    iconView?.visibility = View.VISIBLE
                    iconView?.setImageResource(Icons.Outlined.AccountCircle)
                    iconView?.clearColorFilter()
                    explanationView?.setText(R.string.sender_avatar_preview_explanation_generic)
                }
            }
        }
    }

    @ColorInt
    private fun resolveThemeColor(context: Context, @AttrRes attr: Int, @ColorInt defaultColor: Int): Int {
        val typedValue = TypedValue()
        return if (context.theme.resolveAttribute(attr, typedValue, true)) {
            typedValue.data
        } else {
            defaultColor
        }
    }
}

