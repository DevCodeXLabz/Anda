package com.example.anda.feature.consent

import android.app.Dialog
import android.os.Bundle
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.anda.R
import com.example.anda.core.telemetry.CrashReporter

/**
 * Dialog fragment for LGPD privacy notice and Crashlytics opt-in.
 *
 * **LGPD (Lei 13.709/2018) Article 9(IV)** — Consent Management:
 *   User must explicitly acknowledge that error reports help improve app stability,
 *   and that no personal data is collected without consent.
 *
 * **Data Collected When Opted In**:
 *   - Error messages and stack traces (with PII redacted)
 *   - Device model, OS version, app version
 *   - Timestamp of crash
 *   - No user name, email, CPF, CNPJ, or document contents
 *
 * **Usage**:
 * ```
 * val dialog = CrashReporterConsentDialog()
 * dialog.show(supportFragmentManager, "crashlytics_consent")
 * ```
 */
class CrashReporterConsentDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val currentConsent = CrashReporter.isOptIn(context)
        val content = buildDialogContent(context, currentConsent)

        return AlertDialog.Builder(context)
            .setTitle(R.string.consent_crashlytics_title)
            .setView(content.root)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                CrashReporter.setOptIn(context, content.checkBox.isChecked)
            }
            .setNegativeButton(R.string.consent_button_later) { _, _ ->
                CrashReporter.setOptIn(context, false)
            }
            .setCancelable(false)
            .create()
    }

    private data class DialogContent(
        val root: LinearLayout,
        val checkBox: CheckBox
    )

    private fun buildDialogContent(context: android.content.Context, currentConsent: Boolean): DialogContent {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, 8, padding, 0)
        }

        val description = TextView(context).apply {
            text = context.getString(R.string.consent_crashlytics_description)
        }

        val checkBox = CheckBox(context).apply {
            text = context.getString(R.string.consent_crashlytics_checkbox_label)
            isChecked = currentConsent
        }

        val legalNotice = TextView(context).apply {
            text = context.getString(R.string.consent_crashlytics_legal_notice)
        }

        container.addView(description)
        container.addView(checkBox)
        container.addView(legalNotice)

        return DialogContent(container, checkBox)
    }

    companion object {
        /**
         * Show the consent dialog (fragment-based).
         */
        fun show(fragmentManager: androidx.fragment.app.FragmentManager) {
            CrashReporterConsentDialog().show(fragmentManager, "crashlytics_consent")
        }
    }
}


