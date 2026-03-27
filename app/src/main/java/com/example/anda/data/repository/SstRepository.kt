package com.example.anda.data.repository

import android.content.Context
import com.example.anda.data.history.LookupHistoryItem
import com.example.anda.data.history.LookupHistoryStore
import com.example.anda.data.local.AppDatabase
import com.example.anda.data.local.entity.CompanyEntity
import com.example.anda.data.network.BrasilApiClient
import com.example.anda.data.network.CompanyProfile
import com.example.anda.data.sync.SyncQueueRepository
import com.example.anda.domain.CnaeRiskMapper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object SstRepository {

    private val seeded = AtomicBoolean(false)
    @Volatile
    private var historyStore: LookupHistoryStore? = null
    @Volatile
    private var appDatabase: AppDatabase? = null

    private val _counts = MutableStateFlow(DashboardCounts())
    val counts: StateFlow<DashboardCounts> = _counts.asStateFlow()

    private val _companyLookupState = MutableStateFlow(CompanyLookupState())
    val companyLookupState: StateFlow<CompanyLookupState> = _companyLookupState.asStateFlow()

    private val _lookupHistory = MutableStateFlow<List<LookupHistoryItem>>(emptyList())
    val lookupHistory: StateFlow<List<LookupHistoryItem>> = _lookupHistory.asStateFlow()

    private const val COMPANY_LOOKUP_CACHE_TTL_MS = 5L * 60L * 1000L
    private const val COMPANY_LOCAL_FRESHNESS_MS = 24L * 60L * 60L * 1000L
    private val companyLookupCache = ConcurrentHashMap<String, CachedCompanyProfile>()
    private val lookupMutex = Mutex()
    @Volatile
    private var activeLookupCnpj: String? = null

    fun initialize(context: Context) {
        if (historyStore != null) return
        historyStore = LookupHistoryStore(context.applicationContext)
        appDatabase = AppDatabase.getInstance(context.applicationContext)
        _lookupHistory.value = historyStore?.readAll().orEmpty()
    }

    suspend fun seedDemoDataIfNeeded() {
        if (seeded.compareAndSet(false, true)) {
            _counts.value = DashboardCounts(
                clinicCount = 1,
                companyCount = 1,
                employeeCount = 1,
                employeeRiskLinks = 1,
                pendingSyncItems = SyncQueueRepository.pendingCount()
            )
        }
    }

    suspend fun syncPendingRecords(): Int {
        delay(300)
        val synced = SyncQueueRepository.processBatch { true }
        val pending = SyncQueueRepository.pendingCount()
        _counts.update { it.copy(pendingSyncItems = pending) }
        return synced
    }

    suspend fun lookupCompanyByCnpj(cnpj: String) {
        val normalizedCnpj = cnpj.filter(Char::isDigit)
        if (normalizedCnpj.length != 14) {
            _companyLookupState.value = CompanyLookupState(
                isLoading = false,
                company = null,
                riskGrade = null,
                errorMessage = "CNPJ deve ter 14 digitos"
            )
            return
        }

        val now = System.currentTimeMillis()
        companyLookupCache[normalizedCnpj]
            ?.takeIf { (now - it.cachedAt) <= COMPANY_LOOKUP_CACHE_TTL_MS }
            ?.let { cached ->
                publishCompanyLookupResult(cached.profile)
                return
            }

        val localCompany = appDatabase?.companyDao()?.findByCnpj(normalizedCnpj)
        val localProfile = localCompany?.toProfile()
        val localIsFresh = localCompany != null && (now - localCompany.updatedAt) <= COMPANY_LOCAL_FRESHNESS_MS
        if (localProfile != null) {
            companyLookupCache[normalizedCnpj] = CachedCompanyProfile(
                profile = localProfile,
                cachedAt = now
            )
            publishCompanyLookupResult(localProfile)
            if (localIsFresh) {
                return
            }
        }

        var shouldProceed = true
        lookupMutex.withLock {
            if (activeLookupCnpj == normalizedCnpj) {
                shouldProceed = false
            } else {
                activeLookupCnpj = normalizedCnpj
            }
        }

        if (!shouldProceed) {
            return
        }

        _companyLookupState.value = CompanyLookupState(isLoading = true)

        try {
            runCatching {
                BrasilApiClient.fetchCompanyByCnpj(normalizedCnpj)
            }.onSuccess { company ->
                companyLookupCache[normalizedCnpj] = CachedCompanyProfile(
                    profile = company,
                    cachedAt = System.currentTimeMillis()
                )
                publishCompanyLookupResult(company)
                saveCompanyOffline(company)
                enqueueCompanySync(company)
            }.onFailure { throwable ->
                val cached = companyLookupCache[normalizedCnpj]
                if (cached != null) {
                    publishCompanyLookupResult(cached.profile)
                } else {
                    _companyLookupState.value = CompanyLookupState(
                        isLoading = false,
                        company = null,
                        riskGrade = null,
                        errorMessage = throwable.message ?: "Falha ao consultar CNPJ"
                    )
                }
            }
        } finally {
            lookupMutex.withLock {
                if (activeLookupCnpj == normalizedCnpj) {
                    activeLookupCnpj = null
                }
            }
        }
    }

    private suspend fun publishCompanyLookupResult(company: CompanyProfile) {
        val riskGrade = CnaeRiskMapper.map(company.cnae)
        _companyLookupState.value = CompanyLookupState(
            isLoading = false,
            company = company,
            riskGrade = riskGrade,
            errorMessage = null
        )

        saveLookup(
            LookupHistoryItem(
                cnpj = company.cnpj,
                legalName = company.legalName,
                cnae = company.cnae,
                riskGrade = riskGrade,
                timestamp = System.currentTimeMillis()
            )
        )

        _counts.update { current ->
            val companies = if (current.companyCount == 0) 1 else current.companyCount
            current.copy(
                companyCount = companies,
                pendingSyncItems = SyncQueueRepository.pendingCount()
            )
        }
    }

    private suspend fun saveCompanyOffline(company: CompanyProfile) {
        appDatabase?.companyDao()?.upsert(
            CompanyEntity(
                cnpj = company.cnpj,
                legalName = company.legalName,
                tradeName = company.tradeName,
                cnae = company.cnae,
                city = company.city,
                state = company.state,
                postalCode = company.postalCode
            )
        )
    }

    private suspend fun enqueueCompanySync(company: CompanyProfile) {
        val payload = """
            {"type":"company","cnpj":"${company.cnpj}","updatedAt":${System.currentTimeMillis()}}
        """.trimIndent()
        SyncQueueRepository.enqueueCompanySync(companyRef = "company-${company.cnpj}", payloadJson = payload)
    }

    private fun saveLookup(item: LookupHistoryItem) {
        historyStore?.append(item)
        _lookupHistory.value = historyStore?.readAll().orEmpty()
    }
}

data class DashboardCounts(
    val clinicCount: Int = 0,
    val companyCount: Int = 0,
    val employeeCount: Int = 0,
    val employeeRiskLinks: Int = 0,
    val pendingSyncItems: Int = 0
)

data class CompanyLookupState(
    val isLoading: Boolean = false,
    val company: CompanyProfile? = null,
    val riskGrade: Int? = null,
    val errorMessage: String? = null
)

private data class CachedCompanyProfile(
    val profile: CompanyProfile,
    val cachedAt: Long
)

private fun CompanyEntity.toProfile(): CompanyProfile {
    return CompanyProfile(
        cnpj = cnpj,
        legalName = legalName,
        tradeName = tradeName,
        cnae = cnae,
        city = city,
        state = state,
        postalCode = postalCode
    )
}

