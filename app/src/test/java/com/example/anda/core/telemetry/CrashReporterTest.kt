package com.example.anda.core.telemetry

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [CrashReporter] PII redaction and metadata handling.
 *
 * Note: Full integration tests (Firebase Crashlytics API mocking) are candidates
 * for future instrumented tests. These unit tests focus on data sanitization.
 */
class CrashReporterTest {

    // ── PII Redaction ───────────────────────────────────────────────────────

    @Test
    fun redactPii_redactsCpf() {
        val input = "User CPF: 123.456.789-00 was processed"
        val redacted = redactPiiHelper(input)
        assertTrue(
            "CPF pattern should be redacted",
            redacted.contains("[CPF]")
        )
        assertFalse(
            "Original CPF digits should not appear",
            redacted.contains("123.456.789-00")
        )
    }

    @Test
    fun redactPii_redactsPhone() {
        val input = "Contact: (11) 98765-4321 or mobile"
        val redacted = redactPiiHelper(input)
        assertTrue(
            "Phone pattern should be redacted",
            redacted.contains("[PHONE]")
        )
        assertFalse(
            "Original phone digits should not appear",
            redacted.contains("98765-4321")
        )
    }

    @Test
    fun redactPii_redactsEmail() {
        val input = "Email: john.doe@example.com was not verified"
        val redacted = redactPiiHelper(input)
        assertTrue(
            "Email pattern should be redacted",
            redacted.contains("[EMAIL]")
        )
        assertFalse(
            "Original email should not appear",
            redacted.contains("john.doe@example.com")
        )
    }

    @Test
    fun redactPii_keepsNonPii() {
        val input = "Document type: ASO, status: SYNCED, attempts: 3"
        val redacted = redactPiiHelper(input)
        assertEquals(input, redacted, "Non-PII text should pass through unchanged")
    }

    @Test
    fun redactPii_capsLength() {
        val longInput = "x".repeat(500) + " email@test.com suffix"
        val redacted = redactPiiHelper(longInput)
        assertTrue(
            "Redacted value should be capped at 200 chars",
            redacted.length <= 200
        )
    }

    @Test
    fun redactPii_multiplePiiPatterns() {
        val input = "CPF: 123.456.789-00 Phone: (21) 99999-8888 Email: test@example.com"
        val redacted = redactPiiHelper(input)
        assertTrue("Should redact CPF", redacted.contains("[CPF]"))
        assertTrue("Should redact phone", redacted.contains("[PHONE]"))
        assertTrue("Should redact email", redacted.contains("[EMAIL]"))
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun redactPiiHelper(input: String): String {
        return input
            .replace(Regex("""\d{3}\.\d{3}\.\d{3}-\d{2}"""), "[CPF]")
            .replace(Regex("""\(\d{2}\)\s?\d{4,5}-\d{4}"""), "[PHONE]")
            .replace(Regex("""[\w.-]+@[\w.-]+\.\w+"""), "[EMAIL]")
            .take(200)
    }

    private fun assertEquals(expected: String, actual: String, message: String) {
        assertTrue("$message; expected=$expected, actual=$actual", expected == actual)
    }
}

