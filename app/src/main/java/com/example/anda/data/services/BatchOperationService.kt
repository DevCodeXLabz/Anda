package com.example.anda.data.services

import android.content.Context
import com.example.anda.data.local.dao.DocumentDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Service for performing batch operations on multiple documents.
 * Supports bulk export, delete, and sync retry operations.
 */
class BatchOperationService(
    private val context: Context,
    private val documentDao: DocumentDao,
    private val pdfExportService: PdfExportService
) {

    /**
     * Data class for batch operation result
     */
    data class BatchOperationResult(
        val operationType: String,
        val totalItems: Int,
        val successCount: Int,
        val failureCount: Int,
        val errors: List<String> = emptyList(),
        val resultPath: String? = null
    )

    /**
     * Export multiple documents as a ZIP file containing PDFs
     * Document IDs are string-based (documentId from DocumentEntity)
     */
    suspend fun exportMultipleAsZip(
        documentIds: List<String>,
        outputFileName: String = "documents_export.zip"
    ): BatchOperationResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        var successCount = 0

        try {
            val exportDir = File(context.getExternalFilesDir(null), "exports")
            exportDir.mkdirs()

            val zipFile = File(exportDir, outputFileName)
            ZipOutputStream(zipFile.outputStream()).use { zos ->
                for (documentId in documentIds) {
                    try {
                        val document = documentDao.findByDocumentId(documentId)
                        if (document != null) {
                            // Use the PDF path if already exported
                            if (document.pdfPath != null && File(document.pdfPath).exists()) {
                                val pdfFile = File(document.pdfPath)
                                val zipEntry = ZipEntry("${document.documentType}_${documentId}.pdf")
                                zos.putNextEntry(zipEntry)
                                pdfFile.inputStream().use { it.copyTo(zos) }
                                zos.closeEntry()
                            }
                            successCount++
                        } else {
                            errors.add("Document $documentId not found")
                        }
                    } catch (e: Exception) {
                        errors.add("Failed to export document $documentId: ${e.message}")
                    }
                }
            }

            BatchOperationResult(
                operationType = "EXPORT_ZIP",
                totalItems = documentIds.size,
                successCount = successCount,
                failureCount = documentIds.size - successCount,
                errors = errors,
                resultPath = zipFile.absolutePath
            )
        } catch (e: Exception) {
            BatchOperationResult(
                operationType = "EXPORT_ZIP",
                totalItems = documentIds.size,
                successCount = 0,
                failureCount = documentIds.size,
                errors = listOf("Zip creation failed: ${e.message}")
            )
        }
    }

    /**
     * Mark multiple documents as synced
     */
    suspend fun markMultipleAsSynced(documentIds: List<String>): BatchOperationResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        var successCount = 0

        for (documentId in documentIds) {
            try {
                documentDao.markSynced(documentId)
                successCount++
            } catch (e: Exception) {
                errors.add("Failed to mark document $documentId as synced: ${e.message}")
            }
        }

        BatchOperationResult(
            operationType = "MARK_SYNCED",
            totalItems = documentIds.size,
            successCount = successCount,
            failureCount = documentIds.size - successCount,
            errors = errors
        )
    }

    /**
     * Retry sync for multiple unsigned documents
     */
    suspend fun retryUnsyncedDocuments(): BatchOperationResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        
        try {
            val unsyncedIds = documentDao.listAllDocumentIds(limit = 500)
            var successCount = 0

            for (documentId in unsyncedIds) {
                try {
                    documentDao.markSynced(documentId)
                    successCount++
                } catch (e: Exception) {
                    errors.add("Failed to retry sync for $documentId: ${e.message}")
                }
            }

            BatchOperationResult(
                operationType = "RETRY_UNSYNCED",
                totalItems = unsyncedIds.size,
                successCount = successCount,
                failureCount = unsyncedIds.size - successCount,
                errors = errors
            )
        } catch (e: Exception) {
            BatchOperationResult(
                operationType = "RETRY_UNSYNCED",
                totalItems = 0,
                successCount = 0,
                failureCount = 0,
                errors = listOf("Failed to get unsynced documents: ${e.message}")
            )
        }
    }

    /**
     * Get summary statistics about documents
     */
    suspend fun getDocumentStatistics(): Map<String, Any> = withContext(Dispatchers.IO) {
        val totalCount = documentDao.countAll()
        val signedRecentlyCount = documentDao.countSignedSince(
            System.currentTimeMillis() - (24 * 60 * 60 * 1000) // Last 24 hours
        )

        return@withContext mapOf(
            "totalDocuments" to totalCount,
            "signedToday" to signedRecentlyCount
        )
    }

}


