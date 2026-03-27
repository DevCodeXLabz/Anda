package com.example.anda.data.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfExportTextFormatterTest {

    @Test
    fun normalizeForPdf_shouldPreserveMeaningfulHtmlStructure() {
        val html = """
            <html>
              <body>
                <h2>Título</h2>
                <p><strong>Empresa:</strong> ACME &amp; Filhos</p>
                <p>Linha 1<br>Linha 2</p>
                <ul>
                  <li>Primeiro item</li>
                  <li>Segundo item</li>
                </ul>
                <table>
                  <tr><th>Campo</th><th>Valor</th></tr>
                  <tr><td>Responsável</td><td>Maria</td></tr>
                </table>
              </body>
            </html>
        """.trimIndent()

        val normalized = PdfExportTextFormatter.normalizeForPdf(html)

        assertTrue(normalized.contains("Título"))
        assertTrue(normalized.contains("Empresa: ACME & Filhos"))
        assertTrue(normalized.contains("Linha 1\nLinha 2"))
        assertTrue(normalized.contains("• Primeiro item"))
        assertTrue(normalized.contains("• Segundo item"))
        assertTrue(normalized.contains("Campo | Valor"))
        assertTrue(normalized.contains("Responsável | Maria"))
        assertFalse(normalized.contains("<table>"))
        assertFalse(normalized.contains("&amp;"))
    }

    @Test
    fun normalizeForPdf_shouldCollapseExcessBlankLinesAndIgnoreScripts() {
        val html = """
            <div>Bloco 1</div>
            <script>window.alert('x')</script>
            <div>Bloco 2</div>
            <p>&#67;onclusão</p>
        """.trimIndent()

        val normalized = PdfExportTextFormatter.normalizeForPdf(html)

        assertEquals("Bloco 1\n\nBloco 2\n\nConclusão", normalized)
    }

    @Test
    fun wrapForPdf_shouldWrapBulletsWithIndentedContinuation() {
        val lines = PdfExportTextFormatter.wrapForPdf(
            text = "• medida de controle coletiva obrigatória para atividade crítica",
            maxChars = 24
        )

        assertTrue(lines.size > 1)
        assertEquals("• medida de controle", lines[0])
        assertTrue(lines[1].startsWith("  "))
        assertFalse(lines[1].startsWith("• "))
        assertTrue(lines.all { it.length <= 24 })
    }

    @Test
    fun wrapForPdf_shouldSplitLongWordsWhenNeeded() {
        val lines = PdfExportTextFormatter.wrapForPdf(
            text = "ABCDEFGHIJKLMNOPQRSTUVWXYZ",
            maxChars = 10
        )

        assertEquals(listOf("ABCDEFGHIJ", "KLMNOPQRST", "UVWXYZ"), lines)
    }

    @Test
    fun wrapForPdf_shouldWrapNumberedItemsWithAlignedContinuation() {
        val lines = PdfExportTextFormatter.wrapForPdf(
            text = "12. procedimento obrigatório para inspeção detalhada",
            maxChars = 24
        )

        assertTrue(lines.size > 1)
        assertEquals("12. procedimento", lines[0])
        assertTrue(lines[1].startsWith("    "))
        assertTrue(lines[1].trimStart().startsWith("obrigatório"))
        assertTrue(lines.all { it.length <= 24 })
    }

    @Test
    fun normalizeForPdf_shouldDecodeAposEntity() {
        val normalized = PdfExportTextFormatter.normalizeForPdf("<p>Treinamento &apos;NR&apos; concluído</p>")

        assertEquals("Treinamento 'NR' concluído", normalized)
    }
}

