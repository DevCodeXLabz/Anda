package com.example.anda.ui.design

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import com.example.anda.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

/**
 * DesignSystem Utility
 *
 * Centralized UI component styling for consistent ANDA look & feel.
 *
 * Goals:
 * - Enforce color palette (Navy, Green, Gold)
 * - Consistent padding/spacing (8dp, 16dp, 24dp grid)
 * - Smooth transitions and animations
 * - Accessible contrast ratios
 */
object DesignSystem {

    // ─────────────────────────────────────────────────────────────
    // BUTTON STYLES
    // ─────────────────────────────────────────────────────────────

    /**
     * Primary action button (Navy background, white text).
     * Use for main workflows: Create, Save, Submit.
     */
    fun stylePrimaryButton(button: MaterialButton, context: Context) {
        button.apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.anda_navy))
            setTextColor(ContextCompat.getColor(context, R.color.white))
            cornerRadius = 32
            elevation = 4f
            isAllCaps = false
            setRippleColorResource(R.color.anda_gold)
        }
    }

    /**
     * Secondary action button (Green background, white text).
     * Use for positive confirmations: Approve, Accept, Continue.
     */
    fun styleSecondaryButton(button: MaterialButton, context: Context) {
        button.apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.anda_green))
            setTextColor(ContextCompat.getColor(context, R.color.white))
            cornerRadius = 32
            elevation = 2f
            isAllCaps = false
        }
    }

    /**
     * Tertiary action button (Gold background, navy text).
     * Use for premium/advanced features: Analytics, Settings.
     */
    fun styleTertiaryButton(button: MaterialButton, context: Context) {
        button.apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.anda_gold))
            setTextColor(ContextCompat.getColor(context, R.color.anda_navy))
            cornerRadius = 32
            isAllCaps = false
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
    }

    /**
     * Danger/Destructive button (Red background).
     * Use for Delete, Cancel, Reject.
     */
    fun styleDangerButton(button: MaterialButton, context: Context) {
        button.apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.status_high))
            setTextColor(ContextCompat.getColor(context, R.color.white))
            cornerRadius = 32
            isAllCaps = false
        }
    }

    /**
     * Text-only button (no background).
     * Use for secondary actions, close, cancel.
     */
    fun styleTextButton(button: MaterialButton, context: Context) {
        button.apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setTextColor(ContextCompat.getColor(context, R.color.anda_navy))
            cornerRadius = 24
            isAllCaps = false
        }
    }

    // ─────────────────────────────────────────────────────────────
    // CARD STYLES
    // ─────────────────────────────────────────────────────────────

    /**
     * Standard card for content sections.
     */
    fun styleCard(card: MaterialCardView, context: Context) {
        card.apply {
            cardElevation = 4f
            radius = 16f
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.anda_surface))
            strokeColor = ContextCompat.getColor(context, R.color.anda_bg)
            strokeWidth = 1
        }
    }

    /**
     * Elevated card (more prominent).
     */
    fun styleElevatedCard(card: MaterialCardView, context: Context) {
        card.apply {
            cardElevation = 8f
            radius = 20f
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.anda_surface))
        }
    }

    /**
     * Alert/Status card with colored border.
     */
    fun styleAlertCard(card: MaterialCardView, context: Context, severity: AlertSeverity) {
        card.apply {
            cardElevation = 2f
            radius = 16f
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.anda_surface))
            val color = when (severity) {
                AlertSeverity.INFO -> ContextCompat.getColor(context, R.color.anda_navy)
                AlertSeverity.WARNING -> ContextCompat.getColor(context, R.color.status_moderate)
                AlertSeverity.ERROR -> ContextCompat.getColor(context, R.color.status_high)
                AlertSeverity.SUCCESS -> ContextCompat.getColor(context, R.color.anda_green)
            }
            setStrokeColor(color)
            strokeWidth = 2
        }
    }

    enum class AlertSeverity {
        INFO, WARNING, ERROR, SUCCESS
    }

    // ─────────────────────────────────────────────────────────────
    // TEXT STYLES
    // ─────────────────────────────────────────────────────────────

    /**
     * Large title (page headings).
     */
    fun styleTitleLarge(text: TextView, context: Context) {
        text.apply {
            textSize = 24f
            setTextColor(ContextCompat.getColor(context, R.color.anda_text_primary))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
    }

    /**
     * Medium title (section headings).
     */
    fun styleTitleMedium(text: TextView, context: Context) {
        text.apply {
            textSize = 18f
            setTextColor(ContextCompat.getColor(context, R.color.anda_navy))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
    }

    /**
     * Body text (primary content).
     */
    fun styleBodyText(text: TextView, context: Context) {
        text.apply {
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.anda_text_primary))
            TextViewCompat.setLineHeight(this, 20)
        }
    }

    /**
     * Secondary text (captions, hints).
     */
    fun styleCaptionText(text: TextView, context: Context) {
        text.apply {
            textSize = 12f
            setTextColor(ContextCompat.getColor(context, R.color.anda_text_secondary))
            letterSpacing = 0.02f
        }
    }

    // ─────────────────────────────────────────────────────────────
    // LOADING & STATES
    // ─────────────────────────────────────────────────────────────

    /**
     * Create indeterminate progress (loading spinner).
     */
    fun createLoadingSpinner(context: Context): ProgressBar {
        return ProgressBar(context, null, android.R.attr.progressBarStyle).apply {
            isIndeterminate = true
            indeterminateTintList = ContextCompat.getColorStateList(context, R.color.anda_green)
        }
    }

    /**
     * Show "Loading..." state on a view.
     */
    fun showLoading(view: View) {
        view.alpha = 0.6f
        view.isEnabled = false
    }

    /**
     * Hide loading state.
     */
    fun hideLoading(view: View) {
        view.alpha = 1f
        view.isEnabled = true
    }

    /**
     * Show empty state message.
     */
    fun showEmptyState(text: TextView, context: Context, message: String) {
        text.apply {
            visibility = View.VISIBLE
            setText(message)
            styleCaptionText(this, context)
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setTextColor(ContextCompat.getColor(context, R.color.anda_text_secondary))
        }
    }

    /**
     * Show error state with message and icon.
     */
    fun showErrorState(card: MaterialCardView, context: Context, errorMessage: String) {
        styleAlertCard(card, context, AlertSeverity.ERROR)
        card.visibility = View.VISIBLE
    }

    // ─────────────────────────────────────────────────────────────
    // SPACING GRID
    // ─────────────────────────────────────────────────────────────

    object Spacing {
        const val EXTRA_SMALL = 4    // 4dp - tiny gaps
        const val SMALL = 8          // 8dp - compact spacing
        const val MEDIUM = 16        // 16dp - standard spacing
        const val LARGE = 24         // 24dp - generous spacing
        const val EXTRA_LARGE = 32   // 32dp - section breaks
    }

    // ─────────────────────────────────────────────────────────────
    // ANIMATIONS
    // ─────────────────────────────────────────────────────────────

    /**
     * Fade in animation (smooth entrance).
     */
    fun animateFadeIn(view: View, durationMs: Long = 300) {
        view.alpha = 0f
        view.animate()
            .alpha(1f)
            .setDuration(durationMs)
            .start()
    }

    /**
     * Fade out animation.
     */
    fun animateFadeOut(view: View, durationMs: Long = 300, onComplete: () -> Unit = {}) {
        view.animate()
            .alpha(0f)
            .setDuration(durationMs)
            .withEndAction(onComplete)
            .start()
    }

    /**
     * Scale pulse (draws attention - use sparingly).
     */
    fun animatePulse(view: View, durationMs: Long = 600) {
        view.animate()
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(durationMs / 2)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(durationMs / 2)
                    .start()
            }
            .start()
    }
}

