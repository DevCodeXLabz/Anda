package com.example.anda.core.profile

import android.content.Context
import java.security.MessageDigest
import java.util.Locale

object ProfileManager {

    private const val PREFS_NAME = "anda_profile"
    private const val KEY_PROFILE = "selected_profile"
    private const val KEY_DISPLAY_NAME = "display_name"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
    private const val KEY_LOCKOUT_UNTIL = "lockout_until"
    private const val KEY_LAST_ACTIVE = "last_active_ms"
    private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
    private const val PIN_HASH_PREFIX = "anda_pin_v1:"

    const val PIN_LENGTH = 6

    fun saveProfile(context: Context, profile: UserProfile, displayName: String = "") {
        prefs(context).edit()
            .putString(KEY_PROFILE, profile.storageValue)
            .putString(KEY_DISPLAY_NAME, displayName.trim())
            .apply()
    }

    fun getProfile(context: Context): UserProfile? {
        val stored = prefs(context).getString(KEY_PROFILE, null)
        return UserProfile.fromStorage(stored)
    }

    fun getDisplayName(context: Context): String {
        return prefs(context).getString(KEY_DISPLAY_NAME, "") ?: ""
    }

    fun hasProfile(context: Context): Boolean = getProfile(context) != null

    fun hasPin(context: Context): Boolean {
        return !prefs(context).getString(KEY_PIN_HASH, null).isNullOrBlank()
    }

    fun isFullyConfigured(context: Context): Boolean = hasProfile(context) && hasPin(context)

    fun setPin(context: Context, pin: String): Boolean {
        if (!isValidPin(pin)) return false

        prefs(context).edit()
            .putString(KEY_PIN_HASH, hashPin(pin))
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0L)
            .apply()
        return true
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val storedHash = prefs(context).getString(KEY_PIN_HASH, null) ?: return false
        return storedHash == hashPin(pin)
    }

    fun failedAttempts(context: Context): Int {
        return prefs(context).getInt(KEY_FAILED_ATTEMPTS, 0).coerceAtLeast(0)
    }

    fun remainingLockoutMs(context: Context, nowMs: Long = System.currentTimeMillis()): Long {
        val until = prefs(context).getLong(KEY_LOCKOUT_UNTIL, 0L)
        return (until - nowMs).coerceAtLeast(0L)
    }

    fun isLockedOut(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        return remainingLockoutMs(context, nowMs) > 0L
    }

    fun recordFailedUnlockAttempt(context: Context, nowMs: Long = System.currentTimeMillis()): Long {
        val nextAttempts = failedAttempts(context) + 1
        val lockoutMs = lockoutDurationMsForAttemptCount(nextAttempts)
        val nextLockoutUntil = if (lockoutMs > 0L) nowMs + lockoutMs else 0L

        prefs(context).edit()
            .putInt(KEY_FAILED_ATTEMPTS, nextAttempts)
            .putLong(KEY_LOCKOUT_UNTIL, nextLockoutUntil)
            .apply()

        return (nextLockoutUntil - nowMs).coerceAtLeast(0L)
    }

    fun lockoutDurationMsForAttemptCount(attemptCount: Int): Long {
        return when {
            attemptCount >= 7 -> 10 * 60_000L
            attemptCount >= 5 -> 2 * 60_000L
            attemptCount >= 3 -> 30_000L
            else -> 0L
        }
    }

    fun resetUnlockFailures(context: Context, nowMs: Long = System.currentTimeMillis()) {
        prefs(context).edit()
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0L)
            .putLong(KEY_LAST_ACTIVE, nowMs)
            .apply()
    }

    // ── Session ───────────────────────────────────────────────────

    fun touchSession(context: Context, nowMs: Long = System.currentTimeMillis()) {
        prefs(context).edit()
            .putLong(KEY_LAST_ACTIVE, nowMs)
            .apply()
    }

    fun lastActiveMs(context: Context): Long {
        return prefs(context).getLong(KEY_LAST_ACTIVE, 0L)
    }

    fun isSessionExpired(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        return SessionPolicy.isSessionExpired(lastActiveMs(context), nowMs)
    }

    // ── Biometric preference ─────────────────────────────────────

    fun isBiometricEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_BIOMETRIC_ENABLED, false)
    }

    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    fun saveDisplayName(context: Context, displayName: String) {
        prefs(context).edit().putString(KEY_DISPLAY_NAME, displayName.trim()).apply()
    }

    fun clearAll(context: Context) {
        prefs(context).edit().clear().apply()
    }

    fun clearPin(context: Context) {
        prefs(context).edit()
            .remove(KEY_PIN_HASH)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0L)
            .apply()
    }

    fun isValidPin(pin: String): Boolean {
        return pin.length == PIN_LENGTH && pin.all { it.isDigit() }
    }

    private fun hashPin(pin: String): String {
        val payload = PIN_HASH_PREFIX + pin
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte ->
            String.format(Locale.US, "%02x", byte)
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

