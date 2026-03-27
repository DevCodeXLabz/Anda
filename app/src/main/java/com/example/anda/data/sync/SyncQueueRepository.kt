package com.example.anda.data.sync

import android.content.Context
import com.example.anda.data.local.AppDatabase
import com.example.anda.data.local.entity.SyncAttemptLogEntity
import com.example.anda.data.local.entity.SyncQueueEntity

object SyncQueueRepository {

    @Volatile
    private var initialized = false
    private lateinit var db: AppDatabase

    fun initialize(context: Context) {
        if (initialized) return
        db = AppDatabase.getInstance(context)
        initialized = true
    }

    suspend fun enqueueDocumentSync(documentId: String, payloadJson: String) {
        enqueueItemSync(itemType = "DOCUMENT", itemRef = documentId, payloadJson = payloadJson)
    }

    suspend fun enqueueCompanySync(companyRef: String, payloadJson: String) {
        enqueueItemSync(itemType = "COMPANY", itemRef = companyRef, payloadJson = payloadJson)
    }

    private suspend fun enqueueItemSync(itemType: String, itemRef: String, payloadJson: String) {
        ensureInitialized()
        val existing = db.syncQueueDao().findByTypeAndRef(itemType = itemType, itemRef = itemRef)
        if (existing != null) {
            val samePayload = existing.payloadJson == payloadJson
            val stillQueued = existing.status == SyncQueueEntity.STATUS_PENDING ||
                existing.status == SyncQueueEntity.STATUS_FAILED

            if (samePayload && stillQueued) {
                return
            }

            db.syncQueueDao().refreshAsPending(id = existing.id, payloadJson = payloadJson)
            db.syncAttemptLogDao().insert(
                SyncAttemptLogEntity(
                    itemRef = itemRef,
                    status = SyncQueueEntity.STATUS_PENDING,
                    attemptNumber = existing.attempts,
                    message = "Item atualizado e reenfileirado para sincronizacao"
                )
            )
            return
        }

        db.syncQueueDao().enqueue(
            SyncQueueEntity(
                itemType = itemType,
                itemRef = itemRef,
                payloadJson = payloadJson,
                status = SyncQueueEntity.STATUS_PENDING
            )
        )
        db.syncAttemptLogDao().insert(
            SyncAttemptLogEntity(
                itemRef = itemRef,
                status = SyncQueueEntity.STATUS_PENDING,
                attemptNumber = 0,
                message = "Item enfileirado para sincronizacao"
            )
        )
    }

    suspend fun listAttemptHistory(itemRef: String): List<SyncAttemptLogEntity> {
        ensureInitialized()
        return db.syncAttemptLogDao().listByItemRef(itemRef)
    }

    suspend fun retryAllFailedNow(): Int {
        ensureInitialized()
        val updated = db.syncQueueDao().retryAllFailedNow()
        db.syncAttemptLogDao().insert(
            SyncAttemptLogEntity(
                itemRef = "GLOBAL",
                status = SyncQueueEntity.STATUS_FAILED,
                attemptNumber = 0,
                message = "Reprocessamento manual solicitado para itens com falha"
            )
        )
        return updated
    }

    suspend fun pendingCount(): Int {
        ensureInitialized()
        return db.syncQueueDao().pendingCount()
    }

    suspend fun processBatch(simulateRemoteCall: suspend (SyncQueueEntity) -> Boolean): Int {
        ensureInitialized()
        val items = db.syncQueueDao().nextBatch(limit = 20)
        var successCount = 0

        for (item in items) {
            db.syncQueueDao().updateAttempt(
                id = item.id,
                status = SyncQueueEntity.STATUS_PROCESSING,
                attempts = item.attempts,
                nextAttemptAt = System.currentTimeMillis(),
                lastError = null
            )
            db.syncAttemptLogDao().insert(
                SyncAttemptLogEntity(
                    itemRef = item.itemRef,
                    status = SyncQueueEntity.STATUS_PROCESSING,
                    attemptNumber = item.attempts + 1,
                    message = "Iniciando tentativa ${item.attempts + 1}"
                )
            )

            val succeeded = runCatching { simulateRemoteCall(item) }.getOrDefault(false)
            if (succeeded) {
                if (item.itemType == "DOCUMENT") {
                    db.documentDao().markSynced(item.itemRef)
                }
                db.syncQueueDao().deleteById(item.id)
                db.syncAttemptLogDao().insert(
                    SyncAttemptLogEntity(
                        itemRef = item.itemRef,
                        status = SyncQueueEntity.STATUS_DONE,
                        attemptNumber = item.attempts + 1,
                        message = "Sincronizacao concluida com sucesso"
                    )
                )
                successCount++
            } else {
                val nextAttempts = item.attempts + 1
                val retryDelayMs = computeRetryDelayMs(nextAttempts)
                db.syncQueueDao().updateAttempt(
                    id = item.id,
                    status = SyncQueueEntity.STATUS_FAILED,
                    attempts = nextAttempts,
                    nextAttemptAt = System.currentTimeMillis() + retryDelayMs,
                    lastError = "Falha ao sincronizar item ${item.itemRef}"
                )
                db.syncAttemptLogDao().insert(
                    SyncAttemptLogEntity(
                        itemRef = item.itemRef,
                        status = SyncQueueEntity.STATUS_FAILED,
                        attemptNumber = nextAttempts,
                        message = "Falha na tentativa $nextAttempts. Novo retry em ${retryDelayMs / 1000}s"
                    )
                )
            }
        }

        return successCount
    }

    private fun ensureInitialized() {
        check(initialized) { "SyncQueueRepository nao inicializado" }
    }

    /** Maximum number of items processed in a single sync batch. */
    val MAX_BATCH_SIZE = 20

    /**
     * Exponential-backoff retry delay for a failed sync item.
     *
     * Formula: `BASE_DELAY_MS × 2^coerceIn(attempts, 1, 6)`
     *
     * | attempts | delay  |
     * |----------|--------|
     * | ≤ 1      |  10 s  |
     * | 2        |  20 s  |
     * | 3        |  40 s  |
     * | 4        |  80 s  |
     * | 5        | 160 s  |
     * | ≥ 6      | 320 s  | ← cap (≈ 5 min 20 s)
     *
     * @param attempts The *new* attempt count after a failure (i.e. `item.attempts + 1`).
     */
    fun computeRetryDelayMs(attempts: Int): Long =
        BASE_DELAY_MS * (1L shl attempts.coerceIn(1, MAX_BACKOFF_SHIFT))

    private const val BASE_DELAY_MS      = 5_000L  // 5 seconds base
    private const val MAX_BACKOFF_SHIFT  = 6       // cap: 2^6 = 64 → 320 s
}

