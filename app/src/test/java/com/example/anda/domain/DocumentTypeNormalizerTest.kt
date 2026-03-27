package com.example.anda.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentTypeNormalizerTest {

    @Test
    fun normalize_shouldHandleNrAliasesWithSeparatorsAndPrefix() {
        assertEquals("LAUDO_NR12", DocumentTypeNormalizer.normalize("NR12"))
        assertEquals("LAUDO_NR12", DocumentTypeNormalizer.normalize("nr-12"))
        assertEquals("LAUDO_NR20", DocumentTypeNormalizer.normalize("NR 20"))
        assertEquals("LAUDO_NR10", DocumentTypeNormalizer.normalize("laudo_nr_10"))
    }

    @Test
    fun normalize_shouldHandleKnownCompositeAliases() {
        assertEquals("MAPA_RISCO", DocumentTypeNormalizer.normalize("mapa riscos"))
        assertEquals("PLANO_ACAO", DocumentTypeNormalizer.normalize("plano acao"))
        assertEquals("CERTIFICADO_EPI", DocumentTypeNormalizer.normalize("certificado epi"))
    }

    @Test
    fun normalize_shouldHandleExtendedAliasVariants() {
        assertEquals("LAUDO_NR12", DocumentTypeNormalizer.normalize(" Laudo NR-12 "))
        assertEquals("LAUDO_NR20", DocumentTypeNormalizer.normalize("laudo nr 20"))
        assertEquals("LAUDO_NR10", DocumentTypeNormalizer.normalize("laudo-nr_10"))

        assertEquals("MAPA_RISCO", DocumentTypeNormalizer.normalize("Mapa-Riscos"))
        assertEquals("PLANO_ACAO", DocumentTypeNormalizer.normalize("PLANO-ACAO"))
        assertEquals("CERTIFICADO_EPI", DocumentTypeNormalizer.normalize("certificado-EPI"))
    }

    @Test
    fun normalize_shouldBeIdempotentForCanonicalTypes() {
        assertEquals("LAUDO_NR10", DocumentTypeNormalizer.normalize("LAUDO_NR10"))
        assertEquals("LAUDO_NR12", DocumentTypeNormalizer.normalize("LAUDO_NR12"))
        assertEquals("LAUDO_NR20", DocumentTypeNormalizer.normalize("LAUDO_NR20"))
        assertEquals("MAPA_RISCO", DocumentTypeNormalizer.normalize("MAPA_RISCO"))
        assertEquals("PLANO_ACAO", DocumentTypeNormalizer.normalize("PLANO_ACAO"))
        assertEquals("CERTIFICADO_EPI", DocumentTypeNormalizer.normalize("CERTIFICADO_EPI"))
    }

    @Test
    fun normalize_shouldFallbackToUppercaseForUnknownTypes() {
        assertEquals("CUSTOM_DOC", DocumentTypeNormalizer.normalize(" custom_doc "))
    }

    @Test
    fun normalize_shouldReturnEmptyStringForBlankInput() {
        assertEquals("", DocumentTypeNormalizer.normalize(""))
        assertEquals("", DocumentTypeNormalizer.normalize("   "))
    }

    @Test
    fun normalize_shouldBeIdempotentForAllDocumentTypeEnumValues() {
        // Every canonical name in the DocumentType enum must survive a round-trip unchanged.
        DocumentType.entries.forEach { type ->
            assertEquals(
                "normalize() changed canonical value $type",
                type.name,
                DocumentTypeNormalizer.normalize(type.name)
            )
        }
    }
}

