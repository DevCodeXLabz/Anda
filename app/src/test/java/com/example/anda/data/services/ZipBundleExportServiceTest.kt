package com.example.anda.data.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile
import kotlin.io.path.createTempDirectory

class ZipBundleExportServiceTest {

    private val service = ZipBundleExportService()

    @Test
    fun exportDocumentBundle_shouldIncludeManifestAndResolveDuplicateFileNames() {
            val tempDir = createTempDirectory("zip-bundle-test-").toFile()
        try {
            val sourceA = File(tempDir, "source-a").apply { mkdirs() }
            val sourceB = File(tempDir, "source-b").apply { mkdirs() }
            val pdf1 = File(sourceA, "report.pdf").apply { writeText("pdf-one") }
            val pdf2 = File(sourceB, "report.pdf").apply { writeText("pdf-two") }
            val audit = File(sourceB, "audit.txt").apply { writeText("audit-two") }

            val result = service.exportDocumentBundle(
                outputDir = File(tempDir, "out"),
                baseName = "ANDA Docs",
                documents = listOf(
                    ZipBundleExportService.BundleDocument(
                        documentId = "doc-1",
                        documentType = "ASO",
                        companyCnpj = "12345678000190",
                        title = "ASO 1",
                        pdfPath = pdf1.absolutePath
                    ),
                    ZipBundleExportService.BundleDocument(
                        documentId = "doc-2",
                        documentType = "ASO",
                        companyCnpj = "99887766000111",
                        title = "ASO 2",
                        pdfPath = pdf2.absolutePath,
                        auditTxtPath = audit.absolutePath
                    )
                ),
                generatedAtMillis = 123456L
            )

            assertTrue(result.zipFile.exists())
            assertEquals(2, result.documentsCount)
            assertEquals(3, result.filesAdded)
            assertEquals(0, result.filesSkipped)

            ZipFile(result.zipFile).use { zipFile ->
                val entryNames = zipFile.entries().asSequence().map { it.name }.toList()
                assertTrue(entryNames.contains(ZipBundleExportService.MANIFEST_ENTRY_NAME))
                assertEquals(entryNames.size, entryNames.toSet().size)
                assertTrue(entryNames.count { it.endsWith("report.pdf") } == 2)

                val manifest = zipFile.getInputStream(zipFile.getEntry(ZipBundleExportService.MANIFEST_ENTRY_NAME))
                    .bufferedReader()
                    .use { it.readText() }
                assertTrue(manifest.contains("ANDA - MANIFESTO DE EXPORTACAO ZIP"))
                assertTrue(manifest.contains("doc-1"))
                assertTrue(manifest.contains("doc-2"))
                assertTrue(manifest.contains("Artefatos incluidos: 3"))
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun exportDocumentBundle_shouldSkipMissingFilesAndRecordStatusInManifest() {
            val tempDir = createTempDirectory("zip-bundle-missing-").toFile()
        try {
            val existingAudit = File(tempDir, "audit.txt").apply { writeText("audit") }
            val missingPdf = File(tempDir, "missing.pdf")

            val result = service.exportDocumentBundle(
                outputDir = File(tempDir, "out"),
                baseName = "bundle",
                documents = listOf(
                    ZipBundleExportService.BundleDocument(
                        documentId = "doc-missing",
                        documentType = "PGR",
                        companyCnpj = "123",
                        title = "PGR",
                        pdfPath = missingPdf.absolutePath,
                        auditTxtPath = existingAudit.absolutePath
                    )
                ),
                generatedAtMillis = 999L
            )

            assertEquals(1, result.filesAdded)
            assertEquals(1, result.filesSkipped)

            ZipFile(result.zipFile).use { zipFile ->
                val manifest = zipFile.getInputStream(zipFile.getEntry(ZipBundleExportService.MANIFEST_ENTRY_NAME))
                    .bufferedReader()
                    .use { it.readText() }
                assertTrue(manifest.contains("arquivo ausente no dispositivo"))
                assertTrue(manifest.contains("Auditoria TXT:"))
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun exportBundleZip_legacyWrapperShouldStillCreateZipWithManifest() {
            val tempDir = createTempDirectory("zip-bundle-legacy-").toFile()
        try {
            val input = File(tempDir, "legacy.pdf").apply { writeText("legacy") }

            val result = service.exportBundleZip(
                outputDir = File(tempDir, "out"),
                baseName = "legacy-bundle",
                filePaths = listOf(input.absolutePath),
                generatedAtMillis = 321L
            )

            assertTrue(result.zipFile.exists())
            assertEquals(1, result.documentsCount)
            assertEquals(1, result.filesAdded)

            ZipFile(result.zipFile).use { zipFile ->
                val entryNames = zipFile.entries().asSequence().map { it.name }.toList()
                assertTrue(entryNames.contains(ZipBundleExportService.MANIFEST_ENTRY_NAME))
                assertTrue(entryNames.any { it.endsWith("legacy.pdf") })
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }
}


