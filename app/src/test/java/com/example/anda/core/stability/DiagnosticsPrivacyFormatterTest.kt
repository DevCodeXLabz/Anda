package com.example.anda.core.stability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsPrivacyFormatterTest {

    @Test
    fun anonymizeOrigin_shouldReturnStableMaskedValue() {
        val a = DiagnosticsPrivacyFormatter.anonymizeOrigin("MainActivity/renderSafeModeStatus")
        val b = DiagnosticsPrivacyFormatter.anonymizeOrigin("MainActivity/renderSafeModeStatus")

        assertEquals(a, b)
        assertTrue(a.startsWith("origin-"))
        assertFalse(a.contains("MainActivity"))
    }

    @Test
    fun compactFatalForShareable_shouldHideDetailedStack() {
        val fatal = """
            time=123
            thread=main
            java.lang.IllegalStateException: boom
            at com.example.anda.MainActivity.onCreate(MainActivity.kt:10)
            at com.example.anda.App.start(App.kt:5)
        """.trimIndent()

        val compacted = DiagnosticsPrivacyFormatter.compactFatalForShareable(fatal)
        assertTrue(compacted.contains("stack=(ocultado_privacidade)"))
        assertFalse(compacted.contains("at com.example.anda.App.start"))
    }
}

