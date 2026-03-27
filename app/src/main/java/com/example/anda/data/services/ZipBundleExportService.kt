package com.example.anda.data.services

import android.content.Context
import com.example.anda.data.local.entity.DocumentEntity
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipBundleExportService {

    data class BundleDocument(
        val documentId: String,
        val documentType: String,
        val companyCnpj: String,
        val title: String,
        val pdfPath: String? = null,
        val auditTxtPath: String? = null,
        val signedAt: Long? = null,
        val signedBy: String? = null,
        val isSynced: Boolean = false,
        val updatedAt: Long = System.currentTimeMillis()
    ) {
        companion object {
            fun fromEntity(document: DocumentEntity): BundleDocument {
                return BundleDocument(
                    documentId = document.documentId,
                    documentType = document.documentType,
                    companyCnpj = document.companyCnpj,
                    title = document.title,
                    pdfPath = document.pdfPath,
                    auditTxtPath = document.auditTxtPath,
                    signedAt = document.signedAt,
                    signedBy = document.signedBy,
                    isSynced = document.isSynced,
                    updatedAt = document.updatedAt
                )
            }
        }
    }

    data class BundleExportResult(
        val zipFile: File,
        val documentsCount: Int,
        val filesAdded: Int,
        val filesSkipped: Int
    )

    private data class PreparedArtifact(
        val document: BundleDocument,
        val kindLabel: String,
        val zipEntryName: String,
        val sourceFile: File
    )

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.forLanguageTag("pt-BR"))

    fun exportBundleZip(
        context: Context,
        baseName: String,
        filePaths: List<String>
    ): File {
        return exportBundleZip(
            outputDir = File(context.filesDir, EXPORTS_DIR_NAME),
            baseName = baseName,
            filePaths = filePaths
        ).zipFile
    }

    fun exportBundleZip(
        outputDir: File,
        baseName: String,
        filePaths: List<String>,
        generatedAtMillis: Long = System.currentTimeMillis()
    ): BundleExportResult {
        val documents = filePaths.mapIndexed { index, path ->
            BundleDocument(
                documentId = "arquivo-${index + 1}",
                documentType = "ARQUIVO",
                companyCnpj = "N/A",
                title = File(path).name,
                pdfPath = path
            )
        }
        return exportDocumentBundle(
            outputDir = outputDir,
            baseName = baseName,
            documents = documents,
            generatedAtMillis = generatedAtMillis
        )
    }

    fun exportDocumentBundle(
        context: Context,
        baseName: String,
        documents: List<BundleDocument>
    ): BundleExportResult {
        return exportDocumentBundle(
            outputDir = File(context.filesDir, EXPORTS_DIR_NAME),
            baseName = baseName,
            documents = documents
        )
    }

    fun exportDocumentBundle(
        outputDir: File,
        baseName: String,
        documents: List<BundleDocument>,
        generatedAtMillis: Long = System.currentTimeMillis()
    ): BundleExportResult {
        if (!outputDir.exists()) outputDir.mkdirs()

        val usedEntryNames = linkedSetOf<String>()
        val preparedArtifacts = mutableListOf<PreparedArtifact>()
        var skippedCount = 0

        documents.forEachIndexed { index, document ->
            val folderName = uniqueEntryName(
                usedEntryNames = usedEntryNames,
                desiredName = buildFolderName(index, document)
            )
            listOf(
                "PDF" to document.pdfPath,
                "AUDITORIA_TXT" to document.auditTxtPath
            ).forEach { (kindLabel, sourcePath) ->
                val normalizedPath = sourcePath?.trim().orEmpty()
                if (normalizedPath.isBlank()) return@forEach

                val sourceFile = File(normalizedPath)
                if (!sourceFile.exists() || !sourceFile.isFile) {
                    skippedCount++
                    return@forEach
                }

                val desiredEntryName = "$folderName/${sanitizeSegment(sourceFile.nameWithoutExtension)}${if (sourceFile.extension.isNotBlank()) ".${sanitizeSegment(sourceFile.extension)}" else ""}"
                val zipEntryName = uniqueEntryName(usedEntryNames, desiredEntryName)
                preparedArtifacts += PreparedArtifact(
                    document = document,
                    kindLabel = kindLabel,
                    zipEntryName = zipEntryName,
                    sourceFile = sourceFile
                )
            }
        }

        val safeBaseName = sanitizeSegment(baseName).ifBlank { "anda-docs" }
        val outFile = File(outputDir, "$safeBaseName-$generatedAtMillis.zip")
        val manifestContent = buildManifest(
            baseName = safeBaseName,
            generatedAtMillis = generatedAtMillis,
            documents = documents,
            preparedArtifacts = preparedArtifacts,
            skippedCount = skippedCount
        )

        ZipOutputStream(FileOutputStream(outFile)).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY_NAME))
            zip.write(manifestContent.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            preparedArtifacts.forEach { artifact ->
                FileInputStream(artifact.sourceFile).use { input ->
                    zip.putNextEntry(ZipEntry(artifact.zipEntryName))
                    input.copyTo(zip)
                    zip.closeEntry()
                }
            }
        }

        return BundleExportResult(
            zipFile = outFile,
            documentsCount = documents.size,
            filesAdded = preparedArtifacts.size,
            filesSkipped = skippedCount
        )
    }

    private fun buildManifest(
        baseName: String,
        generatedAtMillis: Long,
        documents: List<BundleDocument>,
        preparedArtifacts: List<PreparedArtifact>,
        skippedCount: Int
    ): String {
        val artifactsByDocument = preparedArtifacts.groupBy { it.document.documentId }
        return buildString {
            appendLine("ANDA - MANIFESTO DE EXPORTACAO ZIP")
            appendLine("Pacote: $baseName")
            appendLine("Gerado em: ${dateFormat.format(Date(generatedAtMillis))}")
            appendLine("Documentos considerados: ${documents.size}")
            appendLine("Artefatos incluidos: ${preparedArtifacts.size}")
            appendLine("Artefatos ignorados/ausentes: $skippedCount")
            appendLine()

            if (documents.isEmpty()) {
                appendLine("Sem documentos vinculados ao pacote.")
            } else {
                documents.forEachIndexed { index, document ->
                    appendLine("[${index + 1}] ${document.documentType} | ID ${document.documentId}")
                    appendLine("Titulo: ${document.title}")
                    appendLine("Empresa: ${document.companyCnpj}")
                    appendLine("Assinado: ${document.signedAt?.let { "SIM em ${dateFormat.format(Date(it))} por ${document.signedBy.orEmpty()}" } ?: "NAO"}")
                    appendLine("Sincronizado: ${if (document.isSynced) "SIM" else "NAO"}")
                    appendLine("Atualizado em: ${dateFormat.format(Date(document.updatedAt))}")

                    val documentArtifacts = artifactsByDocument[document.documentId].orEmpty()
                    val pdfArtifact = documentArtifacts.firstOrNull { it.kindLabel == "PDF" }
                    val auditArtifact = documentArtifacts.firstOrNull { it.kindLabel == "AUDITORIA_TXT" }
                    appendLine("PDF: ${pdfArtifact?.zipEntryName ?: artifactStatus(document.pdfPath)}")
                    appendLine("Auditoria TXT: ${auditArtifact?.zipEntryName ?: artifactStatus(document.auditTxtPath)}")
                    appendLine()
                }
            }
        }
    }

    private fun artifactStatus(path: String?): String {
        val normalized = path?.trim().orEmpty()
        return when {
            normalized.isBlank() -> "nao disponivel"
            File(normalized).exists() -> "disponivel fora do pacote"
            else -> "arquivo ausente no dispositivo"
        }
    }

    private fun buildFolderName(index: Int, document: BundleDocument): String {
        val prefix = (index + 1).toString().padStart(3, '0')
        val type = sanitizeSegment(document.documentType).ifBlank { "documento" }
        val id = sanitizeSegment(document.documentId).ifBlank { "sem-id" }
        return "$prefix-$type-$id"
    }

    private fun uniqueEntryName(usedEntryNames: MutableSet<String>, desiredName: String): String {
        val normalized = desiredName.replace('\\', '/').trim('/').ifBlank { "arquivo" }
        if (usedEntryNames.add(normalized)) return normalized

        val slashIndex = normalized.lastIndexOf('/')
        val folder = if (slashIndex >= 0) normalized.substring(0, slashIndex + 1) else ""
        val fileName = normalized.substring(slashIndex + 1)
        val dotIndex = fileName.lastIndexOf('.')
        val name = if (dotIndex > 0) fileName.substring(0, dotIndex) else fileName
        val extension = if (dotIndex > 0) fileName.substring(dotIndex) else ""

        var attempt = 2
        while (true) {
            val candidate = "$folder$name-$attempt$extension"
            if (usedEntryNames.add(candidate)) return candidate
            attempt++
        }
    }

    private fun sanitizeSegment(value: String): String {
        return value
            .trim()
            .replace("[^A-Za-z0-9._-]+".toRegex(), "-")
            .replace("-+".toRegex(), "-")
            .trim('-', '.')
            .take(80)
    }

    companion object {
        const val MANIFEST_ENTRY_NAME = "manifesto-exportacao.txt"
        private const val EXPORTS_DIR_NAME = "exports"
    }
}

