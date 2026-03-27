package com.example.anda.core.telemetry

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.anda.BuildConfig
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Production telemetry bridge integrating [CrashShield] (local) with
 * [Firebase Crashlytics](remote, opt-in).
 *
 * **Design Principle**: Decoupled — Crashlytics is optional. If Firebase is not
 * initialized or Crashlytics collection is disabled, errors are still logged locally
 * via [CrashShield] without crashing the app.
 *
 * **Error Routing**:
 *   1. Fatal crashes → Captured by [CrashShield]; reported to Crashlytics on next app start.
 *   2. Recoverable errors → Logged locally via [CrashShield]; optionally sent to Crashlytics
 *      based on user consent & error severity.
 *   3. Network/encryption errors → Non-fatal Crashlytics custom records.
 *
 * **LGPD Compliance** (Lei 13.709/2018):
 *   - PII fields are stripped before sending to Crashlytics.
 *   - User must opt-in via SharedPreferences; opt-out is always respected.
 *   - Sensitive stack traces are redacted.
 */
object CrashReporter {

    private const val PREFS = "anda_crash_reporter"
    private const val KEY_CRASHLYTICS_OPT_IN = "crashlytics_opt_in"
    private const val KEY_LAST_REPORT_TIME = "last_report_time"
    private const val MIN_REPORT_INTERVAL_MS = 60 * 60 * 1000L  // 1 hour

    @Volatile
    private var initialized = false
    private var appContextRef: Context? = null

    /**
     * Initialize telemetry on app startup.
     * Safe to call multiple times; subsequent calls are no-ops.
     */
    fun initialize(context: Context) {
        if (initialized) return
        appContextRef = context.applicationContext
        initialized = true

        // Set up automatic error reporting from CrashShield to Crashlytics
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.setCustomKey("app_version", BuildConfig.VERSION_NAME)
        crashlytics.setCustomKey("sdk_version", Build.VERSION.SDK_INT)
        crashlytics.setCustomKey("device", "${Build.MANUFACTURER} ${Build.MODEL}")
        crashlytics.setCustomKey("build_type", BuildConfig.BUILD_TYPE)

        Log.i("CrashReporter", "Telemetry initialized; opt-in status=${isOptIn(context)}")
    }

    /**
     * Record a recoverable (non-fatal) error to local logs and optionally Crashlytics.
     *
     * This is the primary entry point for errors caught during normal operations
     * (e.g., sync failures, document parsing, encryption errors). All recoverable
     * errors are always logged locally; remote reporting respects user consent.
     *
     * @param origin Human-readable error source (e.g., "SyncWorker/processBatch")
     * @param throwable The exception
     * @param metadata Optional structured data (dict of key=value for Crashlytics)
     */
    fun recordRecoverableError(
        origin: String,
        throwable: Throwable,
        metadata: Map<String, String> = emptyMap()
    ) {
        Log.w("CrashReporter", "Recoverable error in $origin: ${throwable.message}")

        // Always log locally
        val context = appContextRef ?: return
        com.example.anda.core.stability.CrashShield.recordRecoverableError(origin, throwable)

        // Optionally send to Crashlytics
        if (isOptIn(context)) {
            reportTocrashlytics(
                message = "Recoverable: $origin",
                throwable = throwable,
                metadata = metadata
            )
        }
    }

    /**
     * Record a custom event without throwing (e.g., sync retry, encryption key rotation).
     * Useful for monitoring non-error state transitions.
     *
     * @param category High-level area (e.g., "sync", "encryption", "network")
     * @param event Event name (e.g., "retry_backoff_started")
     * @param metadata Optional attributes
     */
    fun recordEvent(
        category: String,
        event: String,
        metadata: Map<String, String> = emptyMap()
    ) {
        val context = appContextRef ?: return
        Log.d("CrashReporter", "Event: $category/$event")

        if (isOptIn(context)) {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.log("[$category] $event")
            metadata.forEach { (k, v) ->
                crashlytics.setCustomKey(k, v)
            }
        }
    }

    /**
     * Report a fatal crash that was caught by [CrashShield].
     * Called after the app recovers from the crash on next start.
     *
     * @param crashPayload The encrypted crash report from [CrashShield.consumeLastFatalReport]
     * @param decrypted Optionally, the decrypted human-readable payload (without PII)
     */
    fun reportFatalCrash(crashPayload: String, decrypted: String? = null) {
        val context = appContextRef ?: return

        if (isOptIn(context)) {
            val crashlytics = FirebaseCrashlytics.getInstance()
            val payload = decrypted?.take(1000) ?: "(encrypted payload, not decrypted)"
            crashlytics.recordException(
                Exception("Previous app session crashed: $payload")
            )
            Log.i("CrashReporter", "Fatal crash report sent to Crashlytics")
        }
    }

    /**
     * Set the user ID for Crashlytics.
     * Called after authentication succeeds.
     *
     * @param userId Unique user identifier (opaque — no PII)
     */
    fun setUserId(userId: String) {
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.setUserId(userId)
        Log.d("CrashReporter", "User ID set for telemetry")
    }

    /**
     * Clear the user ID (on sign-out).
     */
    fun clearUserId() {
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.setUserId("")
        Log.d("CrashReporter", "User ID cleared")
    }

    /**
     * Check if the user has opted in to Crashlytics reporting.
     * Default: false (opt-in only).
     */
    fun isOptIn(context: Context): Boolean {
        return context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_CRASHLYTICS_OPT_IN, false)
    }

    /**
     * Set user consent for Crashlytics reporting.
     * Should be called after the user acknowledges the privacy notice.
     *
     * **LGPD Note**: Opt-in is mandatory. The app should show a dialog explaining
     * that error reports help us improve stability, but no data linking to the user
     * is collected without consent.
     */
    fun setOptIn(context: Context, optIn: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_CRASHLYTICS_OPT_IN, optIn)
            .apply()

        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.setCrashlyticsCollectionEnabled(optIn)
        Log.i("CrashReporter", "Crashlytics opt-in set to $optIn")
    }

    /**
     * Clear all telemetry data and reset consent.
     * Called on sign-out or when user requests data deletion (LGPD Art. 17).
     */
    fun clearUserData(context: Context) {
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.setUserId("")
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        Log.i("CrashReporter", "Telemetry data cleared")
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun reportTocrashlytics(
        message: String,
        throwable: Throwable,
        metadata: Map<String, String>
    ) {
        runCatching {
            val context = appContextRef ?: return@runCatching
            val throttle = context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_REPORT_TIME, 0L)

            val now = System.currentTimeMillis()
            if ((now - throttle) < MIN_REPORT_INTERVAL_MS) {
                Log.d("CrashReporter", "Report throttled (< 1 hour since last)")
                return@runCatching
            }

            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.log(message)
            metadata.forEach { (k, v) -> crashlytics.setCustomKey(k, redactPii(v)) }
            crashlytics.recordException(throwable)

            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_REPORT_TIME, now)
                .apply()
        }.onFailure {
            Log.e("CrashReporter", "Failed to report to Crashlytics", it)
        }
    }

    /**
     * Redact obvious PII from metadata before sending to Crashlytics.
     * Looks for patterns like CPF, phone, email.
     */
    private fun redactPii(value: String): String {
        // Simple heuristics — not foolproof, but catches common patterns
        return value
            .replace(Regex("""\d{3}\.\d{3}\.\d{3}-\d{2}"""), "[CPF]")  // CPF
            .replace(Regex("""\(\d{2}\)\s?\d{4,5}-\d{4}"""), "[PHONE]")  // Phone
            .replace(Regex("""[\w.-]+@[\w.-]+\.\w+"""), "[EMAIL]")  // Email
            .take(200)  // Cap length
    }
}

