package com.example.anda.data.repository

import android.content.Context
import com.example.anda.core.security.LocalDataProtection
import com.example.anda.data.local.AppDatabase
import com.example.anda.data.local.entity.DocumentEntity
import com.example.anda.data.local.entity.DocumentVersionEntity
import com.example.anda.data.local.entity.SyncAttemptLogEntity
import com.example.anda.data.requests.ServiceRequestNotificationService
import com.example.anda.data.requests.ServiceRequestLifecyclePolicy
import com.example.anda.data.requests.ServiceRequestStatus
import com.example.anda.data.sync.SyncQueueRepository
import android.util.Log
import com.example.anda.BuildConfig
import com.example.anda.util.CnpjUtils
import com.example.anda.core.stability.CrashShield
import com.example.anda.domain.DocumentTypeNormalizer
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DocumentLocalRepository {

    @Volatile
    private var db: AppDatabase? = null
    @Volatile
    private var appContext: Context? = null
    // Test-only hook: when set, this provider will be used to obtain a ServiceRequestDao
    // allowing unit tests to inject a mock DAO without needing an AppDatabase instance.
    @Volatile
    private var testServiceRequestDaoProvider: (() -> com.example.anda.data.local.dao.ServiceRequestDao)? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (db != null) return
        LocalDataProtection.initialize(context.applicationContext)
        db = AppDatabase.getInstance(context.applicationContext)
    }

    /**
     * Test helper: temporarily use the provided AppDatabase to execute the internal
     * linkRequestForDocument logic. This avoids the need to initialize the full
     * repository during unit tests and is intended for test use only.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    suspend fun linkRequestForDocumentForTesting(
        testDatabase: AppDatabase,
        document: com.example.anda.data.local.entity.DocumentEntity,
        event: com.example.anda.data.requests.ServiceRequestLifecyclePolicy.DocumentEvent
    ) {
        val previous = db
        db = testDatabase
        // Use a provider that returns the real DAO from this AppDatabase for the duration of the test
        val previousProvider = testServiceRequestDaoProvider
        if (testServiceRequestDaoProvider == null) {
            testServiceRequestDaoProvider = { testDatabase.serviceRequestDao() }
        }
        try {
            linkRequestForDocument(document, event)
        } finally {
            db = previous
            testServiceRequestDaoProvider = previousProvider
        }
    }

    /**
     * Test helper: set a ServiceRequestDao provider (used by unit tests to inject mocks)
     */
    @Suppress("unused")
    fun setTestServiceRequestDaoProvider(provider: (() -> com.example.anda.data.local.dao.ServiceRequestDao)?) {
        testServiceRequestDaoProvider = provider
    }

    suspend fun saveDraft(document: DocumentEntity) {
        dao().upsert(document.encryptSensitiveFields())
        recordDocumentVersion(document.documentId, document.payloadJson, document.contentHash, "CREATE")
        linkRequestForDocument(document, ServiceRequestLifecyclePolicy.DocumentEvent.DRAFT_SAVED)
        SyncQueueRepository.enqueueDocumentSync(
            documentId = document.documentId,
            payloadJson = buildPayload(document.documentId)
        )
    }

    suspend fun markSigned(
        documentId: String,
        signedAt: Long,
        signedBy: String,
        contentHash: String,
        signatureB64: String = "",
        publicKeyFingerprint: String = "",
        signatureAlgorithm: String = "SHA256withRSA"
    ) {
        val encryptedSignedBy = LocalDataProtection.encryptString(signedBy)
        dao().markSigned(
            documentId = documentId,
            signedAt = signedAt,
            signedBy = encryptedSignedBy,
            contentHash = contentHash,
            signatureB64 = signatureB64,
            publicKeyFingerprint = publicKeyFingerprint,
            signatureAlgorithm = signatureAlgorithm
        )
        recordDocumentVersion(documentId, "", contentHash, "SIGNED", editedBy = signedBy)
        
        val document = findByDocumentId(documentId)
        if (document != null) {
            linkRequestForDocument(document, ServiceRequestLifecyclePolicy.DocumentEvent.SIGNED)
            
            // Auto-complete the request if it's the only one or meet criteria
            if (document.sourceRequestCode != null) {
                updateRequestStatusFromEvent(document.sourceRequestCode, ServiceRequestLifecyclePolicy.DocumentEvent.SIGNED, document)
            }
        }

        SyncQueueRepository.enqueueDocumentSync(
            documentId = documentId,
            payloadJson = buildPayload(documentId)
        )
    }

    suspend fun markPdfExported(documentId: String, pdfPath: String) {
        val encryptedPdfPath = LocalDataProtection.encryptString(pdfPath)
        dao().markPdfExported(documentId, encryptedPdfPath)
        
        val document = findByDocumentId(documentId)
        if (document != null && document.sourceRequestCode != null) {
            updateRequestStatusFromEvent(document.sourceRequestCode, ServiceRequestLifecyclePolicy.DocumentEvent.PDF_EXPORTED, document)
        }
    }

    suspend fun markAuditExported(documentId: String, auditTxtPath: String) {
        val encryptedAuditPath = LocalDataProtection.encryptString(auditTxtPath)
        dao().markAuditExported(documentId, encryptedAuditPath)
    }

    /**
     * Records a version history entry for the given document.
     * Safe to call from any coroutine; silently ignores if db is null or row not found.
     */
    private suspend fun recordDocumentVersion(
        documentId: String,
        contentSnapshot: String,
        contentHash: String,
        changeType: String,
        editedBy: String = "user"
    ) {
        val database = db ?: return
        val dbId = dao().getIdByDocumentId(documentId) ?: return
        val versionDao = database.documentVersionDao()
        val currentMax = versionDao.getCurrentVersion(dbId)?.versionNumber ?: 0
        versionDao.markAllAsNonCurrent(dbId)
        versionDao.insert(
            DocumentVersionEntity(
                documentId = dbId,
                versionNumber = currentMax + 1,
                contentSnapshot = contentSnapshot.take(4000),
                changeType = changeType,
                editedBy = editedBy,
                contentHash = contentHash,
                isCurrent = true,
                isRestorable = changeType != "SIGNED",
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun findByDocumentId(documentId: String): DocumentEntity? {
        return dao().findByDocumentId(documentId)?.decryptSensitiveFields()
    }

    suspend fun findLatestByCompanyCnpj(companyCnpj: String): DocumentEntity? {
        return dao().findLatestByCompanyCnpj(companyCnpj)?.decryptSensitiveFields()
    }

    suspend fun findLatestByCompanyAndType(companyCnpj: String, documentType: String): DocumentEntity? {
        return dao().findLatestByCompanyAndType(companyCnpj = companyCnpj, documentType = documentType)
            ?.decryptSensitiveFields()
    }

    suspend fun findLatestByRequestCode(requestCode: String): DocumentEntity? {
        return dao().findLatestByRequestCode(requestCode)?.decryptSensitiveFields()
    }

    suspend fun findLatestDocumentId(): String? = dao().findLatest()?.decryptSensitiveFields()?.documentId

    suspend fun loadOperationalSummary(): OperationalSummary {
        val count = dao().countAll()
        val signedToday = dao().countSignedSince(startOfToday())
        val now = System.currentTimeMillis()
        val expiring30d = dao().countExpiringBetween(now, now + DAYS_30_MS)
        val latest = dao().findLatest()?.documentId
        val pending = SyncQueueRepository.pendingCount()
        val failed = checkNotNull(db) { "DocumentLocalRepository nao inicializado" }.syncQueueDao().failedCount()
        return OperationalSummary(
            totalDocuments = count,
            pendingSync = pending,
            latestDocumentId = latest,
            signedToday = signedToday,
            failedSync = failed,
            expiringIn30Days = expiring30d
        )
    }

    suspend fun listDocuments(typeFilter: String = "", companyFilter: String = "", titleFilter: String = ""): List<DocumentEntity> {
        return dao().listDocuments(typeFilter = typeFilter, companyFilter = companyFilter, titleFilter = titleFilter, limit = 100)
            .map { it.decryptSensitiveFields() }
    }

    suspend fun pendingSyncCount(): Int = SyncQueueRepository.pendingCount()

    /**
     * Public helper to count documents expired before provided epoch milliseconds.
     */
    suspend fun countExpiredBefore(nowEpochMs: Long): Int = dao().countExpiredBefore(nowEpochMs)

    /**
     * Public helper to count documents expiring between two epoch millisecond instants.
     */
    suspend fun countExpiringBetween(nowEpochMs: Long, untilEpochMs: Long): Int = dao().countExpiringBetween(nowEpochMs, untilEpochMs)

    suspend fun forceSyncDocument(documentId: String) {
        SyncQueueRepository.enqueueDocumentSync(
            documentId = documentId,
            payloadJson = buildPayload(documentId)
        )
    }

    suspend fun listSyncAttempts(documentId: String): List<SyncAttemptLogEntity> {
        return SyncQueueRepository.listAttemptHistory(documentId)
    }

    /**
     * Returns the version history for a document ordered newest-first.
     * Returns empty list if document not found or database not initialized.
     */
    suspend fun listVersionHistory(documentId: String): List<DocumentVersionEntity> {
        val database = db ?: return emptyList()
        val dbId = dao().getIdByDocumentId(documentId) ?: return emptyList()
        return database.documentVersionDao().getVersionsForDocument(dbId).first()
    }

    /**
     * Returns the number of saved versions for the given document string id.
     * Returns 0 if the document is not found or the database is uninitialized.
     */
    suspend fun getVersionCount(documentId: String): Int {
        val database = db ?: return 0
        val dbId = dao().getIdByDocumentId(documentId) ?: return 0
        return database.documentVersionDao().getVersionCountDirect(dbId)
    }

    suspend fun retryAllFailedNow(): Int = SyncQueueRepository.retryAllFailedNow()

    suspend fun forceSyncSignedUnsyncedBatch(limit: Int = 100): Int {
        val ids = dao().listSignedUnsyncedDocumentIds(limit)
        ids.forEach { id ->
            SyncQueueRepository.enqueueDocumentSync(
                documentId = id,
                payloadJson = buildPayload(id)
            )
        }
        return ids.size
    }

    suspend fun forceSyncAllBatch(limit: Int = 500): Int {
        val ids = dao().listAllDocumentIds(limit)
        ids.forEach { id ->
            SyncQueueRepository.enqueueDocumentSync(
                documentId = id,
                payloadJson = buildPayload(id)
            )
        }
        return ids.size
    }

    private fun dao() = checkNotNull(db) { "DocumentLocalRepository nao inicializado" }.documentDao()

    private suspend fun updateRequestStatusFromEvent(
        requestCode: String,
        event: ServiceRequestLifecyclePolicy.DocumentEvent,
        document: DocumentEntity
    ) {
        val database = db ?: return
        val dao = testServiceRequestDaoProvider?.invoke() ?: database.serviceRequestDao()
        val request = dao.findByRequestCode(requestCode) ?: return
        
        val nextStatus = ServiceRequestLifecyclePolicy.nextStatus(request.status, event)
        if (nextStatus != request.status) {
            val stamp = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR")).format(Date())
            val eventCode = when(event) {
                ServiceRequestLifecyclePolicy.DocumentEvent.DRAFT_SAVED -> "DRAFT"
                ServiceRequestLifecyclePolicy.DocumentEvent.SIGNED -> "SIGNED"
                ServiceRequestLifecyclePolicy.DocumentEvent.PDF_EXPORTED -> "PDF"
            }
            
            val logLine = "[ANDA-LINK][$stamp] req=$requestCode type=${document.documentType} doc=${document.documentId};$eventCode status=$nextStatus"
            val newNotes = if (request.notes.isBlank()) logLine else "${request.notes}\n$logLine"
            
            dao.updateNotesAndStatus(
                id = request.id,
                notes = newNotes,
                status = nextStatus
            )
            
            // Notify if completed
            if (ServiceRequestStatus.isCompleted(nextStatus)) {
                appContext?.let { ctx ->
                    ServiceRequestNotificationService(ctx).notifyCompanyRequestCompleted(
                        companyEmail = "", // Would be in CompanyEntity
                        companyName = request.contractorName,
                        requestType = request.requestedDocumentType,
                        requestId = request.requestCode,
                        documentCount = 1
                    )
                }
            }
        }
    }

    private suspend fun linkRequestForDocument(document: DocumentEntity, event: ServiceRequestLifecyclePolicy.DocumentEvent) {
        val database = db // may be null during unit tests when provider is used
        val explicitRequestCode = document.sourceRequestCode
        if (BuildConfig.DEBUG) Log.d("DocumentLocalRepo", "linkRequestForDocument: doc=${document.documentId} explicitRequestCode=$explicitRequestCode")
        if (!explicitRequestCode.isNullOrBlank()) {
            // Try to link to the explicit request code first
            try {
                val explicitDao = testServiceRequestDaoProvider?.invoke() ?: database?.serviceRequestDao()
                val explicit = explicitDao?.findByRequestCode(explicitRequestCode)
                if (explicit != null) {
                    if (BuildConfig.DEBUG) Log.d("DocumentLocalRepo", "Found explicit request for code=$explicitRequestCode -> linking")
                    updateRequestStatusFromEvent(explicitRequestCode, event, document)
                    return
                } else {
                    if (BuildConfig.DEBUG) Log.d("DocumentLocalRepo", "Explicit request code provided but not found: $explicitRequestCode - will try fallback by CNPJ")
                }
            } catch (t: Throwable) {
                Log.e("DocumentLocalRepo", "Error while looking up explicit request code=$explicitRequestCode", t)
                CrashShield.recordRecoverableError("DocumentLocalRepository/linkRequestForDocument/explicitLookup", t)
                // continue to fallback
            }
        }

        // Fallback: try candidate CNPJ values (original/formatted and normalized digits-only)
        try {
            val companyCnpjPlain = document.companyCnpj
            val candidates = com.example.anda.util.DocumentLookupUtils.candidateCnpjLookupValues(companyCnpjPlain)
            if (candidates.isEmpty()) {
                if (BuildConfig.DEBUG) Log.d("DocumentLocalRepo", "No companyCnpj provided on document ${document.documentId}; skipping fallback")
                return
            }

            if (BuildConfig.DEBUG) Log.d("DocumentLocalRepo", "Fallback lookup candidates=$candidates for doc=${document.documentId}")

            var found: com.example.anda.data.local.entity.ServiceRequestEntity? = null
            for (candidate in candidates) {
                try {
                    val dao = testServiceRequestDaoProvider?.invoke() ?: database!!.serviceRequestDao()
                    val active = dao.listActiveByContractorCnpj(candidate)
                    if (active.isNotEmpty()) {
                        found = active.first()
                        if (BuildConfig.DEBUG) Log.d("DocumentLocalRepo", "Found active request for candidate=$candidate -> code=${found.requestCode}")
                        break
                    }
                } catch (inner: Throwable) {
                    // Record recoverable errors for each candidate lookup but continue trying others
                    Log.e("DocumentLocalRepo", "Error while looking up candidate CNPJ=$candidate", inner)
                    CrashShield.recordRecoverableError("DocumentLocalRepository/linkRequestForDocument/candidateLookup", inner)
                }
            }

            if (found != null) {
                updateRequestStatusFromEvent(found.requestCode, event, document)
            }
        } catch (t: Throwable) {
            // Do not let fallback failures crash the repository - record and continue
            Log.e("DocumentLocalRepo", "Fallback lookup failed", t)
            CrashShield.recordRecoverableError("DocumentLocalRepository/linkRequestForDocument/fallback", t)
        }
    }

    private fun notifyRequestLifecycleProgress(
        requestCode: String,
        contractorName: String,
        requestedDocumentType: String,
        assignedEmployeeName: String,
        previousStatus: String,
        nextStatus: String
    ) {
        if (previousStatus == nextStatus) return
        val context = appContext ?: return
        val notifier = ServiceRequestNotificationService(context)

        when {
            nextStatus == ServiceRequestStatus.IN_PROGRESS -> {
                notifier.notifyCompanyRequestStarted(
                    companyEmail = "",
                    companyName = contractorName,
                    technicianName = assignedEmployeeName.ifBlank { "Equipe ANDA" },
                    requestId = requestCode
                )
            }
            nextStatus == ServiceRequestStatus.COMPLETED -> {
                notifier.notifyCompanyRequestCompleted(
                    companyEmail = "",
                    companyName = contractorName,
                    requestType = requestedDocumentType,
                    requestId = requestCode,
                    documentCount = 1
                )
            }
        }
    }

    private fun appendRequestLinkNote(
        existingNotes: String,
        requestCode: String,
        documentId: String,
        documentType: String,
        event: ServiceRequestLifecyclePolicy.DocumentEvent,
        status: String
    ): String {
        val eventTag = when (event) {
            ServiceRequestLifecyclePolicy.DocumentEvent.DRAFT_SAVED -> "DRAFT"
            ServiceRequestLifecyclePolicy.DocumentEvent.SIGNED -> "SIGNED"
            ServiceRequestLifecyclePolicy.DocumentEvent.PDF_EXPORTED -> "PDF"
        }
        val signature = "doc=$documentId;$eventTag"
        if (existingNotes.contains(signature)) {
            return existingNotes
        }
        val stamp = REQUEST_LINK_DATE_FMT.format(Date())
        val line = "[ANDA-LINK][$stamp] req=$requestCode type=$documentType $signature status=${ServiceRequestStatus.normalize(status)}"
        return if (existingNotes.isBlank()) line else "$existingNotes\n$line"
    }

    private fun buildPayload(documentId: String): String {
        return "{" +
            "\"type\":\"document\"," +
            "\"documentId\":\"$documentId\"," +
            "\"updatedAt\":${System.currentTimeMillis()}" +
            "}"
    }

    private fun startOfToday(): Long {
        val now = java.util.Calendar.getInstance()
        now.set(java.util.Calendar.HOUR_OF_DAY, 0)
        now.set(java.util.Calendar.MINUTE, 0)
        now.set(java.util.Calendar.SECOND, 0)
        now.set(java.util.Calendar.MILLISECOND, 0)
        return now.timeInMillis
    }

    private const val DAYS_30_MS = 30L * 24L * 60L * 60L * 1000L
    private val REQUEST_LINK_DATE_FMT = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))
}

private fun DocumentEntity.encryptSensitiveFields(): DocumentEntity {
    return copy(
        companyCnpj = LocalDataProtection.encryptString(companyCnpj),
        title = LocalDataProtection.encryptString(title),
        payloadJson = LocalDataProtection.encryptString(payloadJson),
        pdfPath = LocalDataProtection.encryptNullable(pdfPath),
        auditTxtPath = LocalDataProtection.encryptNullable(auditTxtPath),
        signedBy = LocalDataProtection.encryptNullable(signedBy)
    )
}

private fun DocumentEntity.decryptSensitiveFields(): DocumentEntity {
    return copy(
        companyCnpj = LocalDataProtection.decryptString(companyCnpj),
        title = LocalDataProtection.decryptString(title),
        payloadJson = LocalDataProtection.decryptString(payloadJson),
        pdfPath = LocalDataProtection.decryptNullable(pdfPath),
        auditTxtPath = LocalDataProtection.decryptNullable(auditTxtPath),
        signedBy = LocalDataProtection.decryptNullable(signedBy)
    )
}

data class OperationalSummary(
    val totalDocuments: Int,
    val pendingSync: Int,
    val latestDocumentId: String?,
    val signedToday: Int,
    val failedSync: Int,
    val expiringIn30Days: Int
)


