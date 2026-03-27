package com.example.anda

import com.example.anda.feature.scanner.CaNumberExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaNumberExtractorTest {

    @Test
    fun extract_withStandardFormat_returnsNumber() {
        assertEquals("12345", CaNumberExtractor.extract("Luva de raspa C.A. 12345"))
    }

    @Test
    fun extract_withCompactFormat_returnsNumber() {
        assertEquals("98765", CaNumberExtractor.extract("CA98765 protetor"))
    }

    @Test
    fun extract_withoutCa_returnsNull() {
        assertNull(CaNumberExtractor.extract("Nenhum codigo no texto"))
    }
}

