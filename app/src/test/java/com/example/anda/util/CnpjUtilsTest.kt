package com.example.anda.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CnpjUtilsTest {

    @Test
    fun normalizeCnpj_removesPunctuationAndSpaces() {
        val raw = "12.345.678/0001-91"
        val normalized = CnpjUtils.normalizeCnpj(raw)
        assertEquals("12345678000191", normalized)
    }

    @Test
    fun normalizeCnpj_handlesAlreadyDigits() {
        val raw = "12345678000191"
        val normalized = CnpjUtils.normalizeCnpj(raw)
        assertEquals("12345678000191", normalized)
    }

    @Test
    fun normalizeCnpj_nullOrBlank_returnsEmpty() {
        assertEquals("", CnpjUtils.normalizeCnpj(null))
        assertEquals("", CnpjUtils.normalizeCnpj(""))
    }
}

