package com.example.anda.feature.requests

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class ServiceRequestOperationalHistoryFormatterTest {

    @Test
    fun parse_splitsHistoryAndFreeNotes() {
        val notes = """
            Observacao livre
            [ANDA-LINK][26/03/2026 10:30] req=REQ-1 type=ASO doc=aso-123;DRAFT status=IN_PROGRESS
            Outra nota
        """.trimIndent()

        val result = ServiceRequestOperationalHistoryFormatter.parse(notes)

        assertEquals(1, result.historyLines.size)
        assertTrue(result.historyLines.first().contains("Rascunho salvo"))
        assertEquals("Observacao livre\nOutra nota", result.freeNotes)
    }

    @Test
    fun latestActionSummary_returnsMostRecentEvent() {
        val notes = """
            [ANDA-LINK][26/03/2026 10:30] req=REQ-1 type=ASO doc=aso-123;DRAFT status=IN_PROGRESS
            [ANDA-LINK][26/03/2026 10:45] req=REQ-1 type=ASO doc=aso-123;SIGNED status=COMPLETED
        """.trimIndent()

        val summary = ServiceRequestOperationalHistoryFormatter.latestActionSummary(notes)

        assertEquals("Assinatura local • ASO • aso-123", summary)
    }

    @Test
    fun latestActionSummary_returnsNull_whenNoOperationalEntries() {
        val notes = "Apenas observacoes livres"
        val summary = ServiceRequestOperationalHistoryFormatter.latestActionSummary(notes)
        assertNull(summary)
    }

    @Test
    fun parse_usesFallbackLabel_forUnknownEvent() {
        val notes = "[ANDA-LINK][26/03/2026 11:00] req=REQ-1 type=ASO doc=aso-123;CUSTOM status=IN_PROGRESS"

        val result = ServiceRequestOperationalHistoryFormatter.parse(notes)

        assertEquals(1, result.historyLines.size)
        assertTrue(result.historyLines.first().contains("Evento operacional"))
    }

    @Test
    fun latestActionEpochMs_returnsMostRecentTimestamp() {
        val notes = """
            [ANDA-LINK][26/03/2026 10:30] req=REQ-1 type=ASO doc=aso-123;DRAFT status=IN_PROGRESS
            [ANDA-LINK][26/03/2026 10:45] req=REQ-1 type=ASO doc=aso-123;SIGNED status=COMPLETED
        """.trimIndent()

        val epoch = ServiceRequestOperationalHistoryFormatter.latestActionEpochMs(notes)
        val expected = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))
            .parse("26/03/2026 10:45")
            ?.time

        assertEquals(expected, epoch)
    }

    @Test
    fun latestActionEpochMs_returnsNull_whenTimestampIsInvalid() {
        val notes = "[ANDA-LINK][stamp-invalido] req=REQ-1 type=ASO doc=aso-123;DRAFT status=IN_PROGRESS"

        val epoch = ServiceRequestOperationalHistoryFormatter.latestActionEpochMs(notes)

        assertNull(epoch)
    }
}

