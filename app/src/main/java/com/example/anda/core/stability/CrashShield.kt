package com.example.anda.core.stability

import android.content.Context
import android.util.Log
import com.example.anda.core.security.LocalDataProtection
import java.io.PrintWriter
import java.io.StringWriter

object CrashShield {

    private const val PREFS = "anda_crash_shield"
    private const val KEY_FATAL = "last_fatal"
    private const val KEY_CRASH_WINDOW_START = "crash_window_start"
    private const val KEY_CRASH_COUNT = "crash_count"
    private const val KEY_SAFE_MODE_UNTIL = "safe_mode_until"
    private const val KEY_RECOVERABLE = "recoverable_errors"
    private const val MAX_RECOVERABLE_ERRORS = 20
    private const val DEFAULT_MANUAL_SAFE_MODE_MS = 30L * 60L * 1000L
    private const val ENTRY_SEPARATOR = '\u001e'
    private const val FIELD_SEPARATOR = '\u001f'

    @Volatile
    private var appContextRef: Context? = null

    fun install(context: Context) {
        val appContext = context.applicationContext
        appContextRef = appContext
        LocalDataProtection.initialize(appContext)
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val now = System.currentTimeMillis()
                val payload = buildCrashPayload(thread.name, throwable)
                val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val previousWindowStart = prefs.getLong(KEY_CRASH_WINDOW_START, 0L)
                val previousCount = prefs.getInt(KEY_CRASH_COUNT, 0)

                val inWindow = previousWindowStart > 0L && (now - previousWindowStart) <= 60L * 60L * 1000L
                val windowStart = if (inWindow) previousWindowStart else now
                val crashCount = if (inWindow) previousCount + 1 else 1

                val decision = SafeModePolicy.evaluate(
                    windowStartAt = windowStart,
                    crashCountInWindow = crashCount,
                    now = now
                )

                prefs.edit()
                    .putString(KEY_FATAL, LocalDataProtection.encryptString(payload))
                    .putLong(KEY_CRASH_WINDOW_START, windowStart)
                    .putInt(KEY_CRASH_COUNT, crashCount)
                    .apply()

                if (decision.shouldEnable && decision.safeModeUntil != null) {
                    prefs.edit().putLong(KEY_SAFE_MODE_UNTIL, decision.safeModeUntil).apply()
                }
            }
            Log.e("CrashShield", "Fatal crash captured", throwable)
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun peekLastFatalReport(context: Context): String? {
        return context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_FATAL, null)
            ?.let(LocalDataProtection::decryptString)
    }

    fun consumeLastFatalReport(context: Context): String? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val report = prefs.getString(KEY_FATAL, null)?.let(LocalDataProtection::decryptString)
        if (!report.isNullOrBlank()) {
            prefs.edit().remove(KEY_FATAL).apply()
        }
        return report
    }

    fun isSafeModeActive(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        val safeModeUntil = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_SAFE_MODE_UNTIL, 0L)

        if (safeModeUntil <= 0L) return false
        return SafeModePolicy.isSafeModeStillActive(safeModeUntil, now)
    }

    fun safeModeUntil(context: Context): Long? {
        val value = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_SAFE_MODE_UNTIL, 0L)
        return value.takeIf { it > 0L }
    }

    fun disableSafeMode(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_SAFE_MODE_UNTIL)
            .apply()
    }

    fun enableSafeModeFor(context: Context, durationMs: Long = DEFAULT_MANUAL_SAFE_MODE_MS) {
        val until = System.currentTimeMillis() + durationMs.coerceAtLeast(60_000L)
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_SAFE_MODE_UNTIL, until)
            .apply()
    }

    fun remainingSafeModeMs(context: Context, now: Long = System.currentTimeMillis()): Long {
        val until = safeModeUntil(context) ?: return 0L
        return (until - now).coerceAtLeast(0L)
    }

    fun clearDiagnostics(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_FATAL)
            .remove(KEY_CRASH_WINDOW_START)
            .remove(KEY_CRASH_COUNT)
            .remove(KEY_SAFE_MODE_UNTIL)
            .remove(KEY_RECOVERABLE)
            .apply()
    }

    fun crashCountInCurrentWindow(context: Context): Int {
        return context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_CRASH_COUNT, 0)
    }

    fun recordRecoverableError(origin: String, throwable: Throwable) {
        Log.e("CrashShield", "Recoverable error in $origin", throwable)
        val appContext = appContextRef ?: return
        runCatching {
            val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val current = decodeEntries(prefs.getString(KEY_RECOVERABLE, null))
            val next = current + RecoverableErrorEntry(
                timestamp = System.currentTimeMillis(),
                origin = origin,
                errorType = throwable.javaClass.simpleName,
                message = throwable.message.orEmpty(),
                stack = stackTrace(throwable)
            )
            val trimmed = if (next.size > MAX_RECOVERABLE_ERRORS) {
                next.takeLast(MAX_RECOVERABLE_ERRORS)
            } else {
                next
            }
            prefs.edit()
                .putString(KEY_RECOVERABLE, LocalDataProtection.encryptString(encodeEntries(trimmed)))
                .apply()
        }
    }

    fun peekRecoverableErrors(context: Context): List<RecoverableErrorEntry> {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_RECOVERABLE, null)
            ?.let(LocalDataProtection::decryptString)
        return decodeEntries(raw)
    }

    fun buildDiagnosticsReport(
        context: Context,
        mode: DiagnosticsReportMode = DiagnosticsReportMode.FULL
    ): String {
        val now = System.currentTimeMillis()
        val safeMode = isSafeModeActive(context, now)
        val safeModeUntil = safeModeUntil(context)
        val fatal = peekLastFatalReport(context)
        val recoverable = peekRecoverableErrors(context)
        val crashCount = crashCountInCurrentWindow(context)

        val safeModeLine = if (safeMode) {
            "ATIVO ate=${safeModeUntil ?: "-"}"
        } else {
            "INATIVO"
        }

        val recoverableBlock = if (recoverable.isEmpty()) {
            "(nenhum erro recuperavel)"
        } else {
            recoverable.joinToString("\n\n") { entry ->
                if (mode == DiagnosticsReportMode.FULL) {
                    "time=${entry.timestamp}\norigin=${entry.origin}\ntype=${entry.errorType}\nmessage=${entry.message}\nstack=${entry.stack.take(1200)}"
                } else {
                    "time=${entry.timestamp}\norigin=${DiagnosticsPrivacyFormatter.anonymizeOrigin(entry.origin)}\ntype=${entry.errorType}\nmessage=${entry.message}\nstack=(ocultado_privacidade)"
                }
            }
        }

        val fatalBlock = if (mode == DiagnosticsReportMode.FULL) {
            fatal ?: "(sem fatal)"
        } else {
            DiagnosticsPrivacyFormatter.compactFatalForShareable(fatal)
        }

        return buildString {
            append("ANDA DIAGNOSTIC REPORT\n")
            append("mode=").append(mode.name).append('\n')
            append("generated_at=").append(now).append('\n')
            append("safe_mode=").append(safeModeLine).append('\n')
            append("crash_count_window=").append(crashCount).append("\n\n")
            append("=== LAST_FATAL ===\n")
            append(fatalBlock)
            append("\n\n=== RECOVERABLE_ERRORS ===\n")
            append(recoverableBlock)
            append('\n')
        }
    }


    private fun buildCrashPayload(threadName: String, throwable: Throwable): String {
        return "time=${System.currentTimeMillis()}\nthread=$threadName\n${stackTrace(throwable)}"
    }

    private fun stackTrace(throwable: Throwable): String {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }

    private fun encodeEntries(entries: List<RecoverableErrorEntry>): String {
        return entries.joinToString(ENTRY_SEPARATOR.toString()) { entry ->
            listOf(
                entry.timestamp.toString(),
                sanitize(entry.origin),
                sanitize(entry.errorType),
                sanitize(entry.message),
                sanitize(entry.stack)
            ).joinToString(FIELD_SEPARATOR.toString())
        }
    }

    private fun decodeEntries(raw: String?): List<RecoverableErrorEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(ENTRY_SEPARATOR).mapNotNull { line ->
            val parts = line.split(FIELD_SEPARATOR)
            if (parts.size < 5) return@mapNotNull null
            val timestamp = parts[0].toLongOrNull() ?: return@mapNotNull null
            RecoverableErrorEntry(
                timestamp = timestamp,
                origin = restore(parts[1]),
                errorType = restore(parts[2]),
                message = restore(parts[3]),
                stack = restore(parts[4])
            )
        }
    }

    private fun sanitize(value: String): String {
        return value
            .replace(ENTRY_SEPARATOR.toString(), " ")
            .replace(FIELD_SEPARATOR.toString(), " ")
            .replace("\n", "\\n")
    }

    private fun restore(value: String): String = value.replace("\\n", "\n")
}

data class RecoverableErrorEntry(
    val timestamp: Long,
    val origin: String,
    val errorType: String,
    val message: String,
    val stack: String
)

enum class DiagnosticsReportMode {
    FULL,
    SHAREABLE
}

