package com.example.anda.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentLookupUtilsTest {

    @Test
    fun candidates_for_formatted_and_digits() {
        val formatted = "12.345.678/0001-91"
        val candidates = DocumentLookupUtils.candidateCnpjLookupValues(formatted)
        assertEquals(2, candidates.size)
        assertEquals("12.345.678/0001-91", candidates[0])
        assertEquals("12345678000191", candidates[1])
    }

    @Test
    fun candidates_for_digits_only() {
        val digits = "12345678000191"
        val candidates = DocumentLookupUtils.candidateCnpjLookupValues(digits)
        assertEquals(1, candidates.size)
        assertEquals(digits, candidates[0])
    }

    @Test
    fun candidates_for_null_blank() {
        assertEquals(0, DocumentLookupUtils.candidateCnpjLookupValues(null).size)
        assertEquals(0, DocumentLookupUtils.candidateCnpjLookupValues("").size)
        assertEquals(0, DocumentLookupUtils.candidateCnpjLookupValues("   ").size)
    }
}

