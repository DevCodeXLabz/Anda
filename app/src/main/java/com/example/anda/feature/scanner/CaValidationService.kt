package com.example.anda.feature.scanner

import com.example.anda.data.network.OfficialCaApiClient
import com.example.anda.data.network.OfficialCaResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

object CaValidationService {

    private const val CACHE_TTL_MS = 5L * 60L * 1000L
    private const val MAX_CACHE_ENTRIES = 300
    private val cache = ConcurrentHashMap<String, CachedValidation>()
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<CaValidationResult>>()

    private val knownItems = mapOf(
        "12345" to CaValidationResult(
            caNumber = "12345",
            isValid = true,
            itemName = "Protetor auricular tipo plug",
            riskCoverage = "Ruido ocupacional",
            validUntil = "2028-12-31",
            source = "local",
            statusReason = null
        ),
        "98765" to CaValidationResult(
            caNumber = "98765",
            isValid = false,
            itemName = "Luva de raspa",
            riskCoverage = "Risco mecanico",
            validUntil = "2023-10-31",
            source = "local",
            statusReason = "expired"
        )
    )

    suspend fun validate(
        caNumber: String,
        officialLookup: suspend (String) -> OfficialCaResponse? = { number ->
            withContext(Dispatchers.IO) { OfficialCaApiClient.fetchByCaNumber(number) }
        }
    ): CaValidationResult {
        val normalizedCa = caNumber.trim()
        if (normalizedCa.isBlank()) {
            return CaValidationResult(
                caNumber = "-",
                isValid = false,
                itemName = "Numero de C.A. invalido",
                riskCoverage = "-",
                validUntil = "-",
                source = "fallback",
                statusReason = "not_found"
            )
        }

        val now = System.currentTimeMillis()
        cache[normalizedCa]
            ?.takeIf { (now - it.cachedAt) <= CACHE_TTL_MS }
            ?.let { return it.result }

        inFlight[normalizedCa]?.let { return it.await() }

        val deferred = CompletableDeferred<CaValidationResult>()
        val existing = inFlight.putIfAbsent(normalizedCa, deferred)
        if (existing != null) {
            return existing.await()
        }

        return try {
            val official = officialLookup(normalizedCa)?.let {
                CaValidationResult(
                    caNumber = it.caNumber,
                    isValid = it.isValid,
                    itemName = it.itemName,
                    riskCoverage = it.riskCoverage,
                    validUntil = it.validUntil,
                    source = it.source,
                    statusReason = it.statusReason
                )
            }

            val result = official ?: knownItems[normalizedCa] ?: CaValidationResult(
                caNumber = normalizedCa,
                isValid = false,
                itemName = "Nao encontrado na base local",
                riskCoverage = "-",
                validUntil = "-",
                source = "fallback",
                statusReason = "not_found"
            )

            cache[normalizedCa] = CachedValidation(result = result, cachedAt = System.currentTimeMillis())
            trimCacheIfNeeded()
            deferred.complete(result)
            result
        } catch (throwable: Throwable) {
            deferred.completeExceptionally(throwable)
            throw throwable
        } finally {
            inFlight.remove(normalizedCa, deferred)
        }
    }

    private fun trimCacheIfNeeded() {
        val now = System.currentTimeMillis()
        cache.entries.removeIf { (_, value) -> (now - value.cachedAt) > CACHE_TTL_MS }

        val overflow = cache.size - MAX_CACHE_ENTRIES
        if (overflow <= 0) return

        cache.entries
            .sortedBy { it.value.cachedAt }
            .take(overflow)
            .forEach { cache.remove(it.key) }
    }
}

private data class CachedValidation(
    val result: CaValidationResult,
    val cachedAt: Long
)

data class CaValidationResult(
    val caNumber: String,
    val isValid: Boolean,
    val itemName: String,
    val riskCoverage: String,
    val validUntil: String,
    val source: String,
    val statusReason: String? = null
)
