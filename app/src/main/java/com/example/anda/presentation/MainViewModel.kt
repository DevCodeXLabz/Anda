package com.example.anda.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.anda.data.network.OfficialCaApiClient
import com.example.anda.data.network.BackendHealthSnapshot
import com.example.anda.data.repository.CompanyLookupState
import com.example.anda.data.repository.DashboardCounts
import com.example.anda.data.repository.SstRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class MainViewModel(
    private val hasConfiguredBackend: () -> Boolean = { OfficialCaApiClient.hasConfiguredBackend() },
    private val readBackendHealth: suspend () -> BackendHealthSnapshot? = {
        withContext(Dispatchers.IO) { OfficialCaApiClient.fetchBackendHealthSnapshot() }
    }
) : ViewModel() {

    // App-only offline-first: evita polling de rede continuo e economiza requests.
    private val backendMonitoringEnabled = false

    private val _statusMessage = MutableStateFlow("Pronto para iniciar M0/M1")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _backendHealth = MutableStateFlow("Backend: verificando...")
    val backendHealth: StateFlow<String> = _backendHealth.asStateFlow()

    private val _backendFallbackBadge = MutableStateFlow("Fallback: sem dados")
    val backendFallbackBadge: StateFlow<String> = _backendFallbackBadge.asStateFlow()

    private val _backendFallbackLevel = MutableStateFlow(BackendFallbackLevel.UNKNOWN)
    val backendFallbackLevel: StateFlow<BackendFallbackLevel> = _backendFallbackLevel.asStateFlow()

    private var backendHealthMonitorJob: Job? = null

    val dashboardCounts: StateFlow<DashboardCounts> = SstRepository.counts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardCounts())

    val companyLookupState: StateFlow<CompanyLookupState> = SstRepository.companyLookupState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CompanyLookupState())

    val lookupHistory = SstRepository.lookupHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun seedAndPropagateDemo() {
        viewModelScope.launch {
            runCatching {
                SstRepository.seedDemoDataIfNeeded()
            }.onSuccess {
                _statusMessage.value = "Dados demo inseridos e risco propagado com sucesso"
            }.onFailure {
                _statusMessage.value = "Falha ao inserir dados demo: ${it.message}"
            }
        }
    }

    fun runManualSync() {
        viewModelScope.launch {
            runCatching {
                SstRepository.syncPendingRecords()
            }.onSuccess { syncedCount ->
                _statusMessage.value = "Sincronizacao concluida. Itens enviados: $syncedCount"
            }.onFailure {
                _statusMessage.value = "Sincronizacao falhou: ${it.message}"
            }
        }
    }

    fun lookupCompany(cnpj: String) {
        viewModelScope.launch {
            SstRepository.lookupCompanyByCnpj(cnpj)
        }
    }

    fun refreshBackendHealth() {
        viewModelScope.launch {
            updateBackendHealthOnce()
        }
    }

    fun startBackendHealthMonitoring(intervalMs: Long = 30_000L) {
        if (!backendMonitoringEnabled) {
            _backendHealth.value = "Backend: monitoramento automatico desativado"
            return
        }
        if (backendHealthMonitorJob != null) return

        backendHealthMonitorJob = viewModelScope.launch {
            while (isActive) {
                updateBackendHealthOnce()
                delay(intervalMs)
            }
        }
    }

    fun stopBackendHealthMonitoring() {
        backendHealthMonitorJob?.cancel()
        backendHealthMonitorJob = null
    }

    private suspend fun updateBackendHealthOnce() {
        if (!hasConfiguredBackend()) {
            _backendHealth.value = "Backend: CA_API_BASE_URL nao configurada"
            _backendFallbackBadge.value = "Fallback: sem dados"
            _backendFallbackLevel.value = BackendFallbackLevel.UNKNOWN
            return
        }

        _backendHealth.value = "Backend: verificando..."
        val snapshot = readBackendHealth()
        if (snapshot == null) {
            _backendHealth.value = "Backend: OFFLINE (fallback ativo)"
            _backendFallbackBadge.value = "Fallback: sem dados"
            _backendFallbackLevel.value = BackendFallbackLevel.UNKNOWN
            return
        }

        _backendHealth.value = "Backend: ONLINE"
        val assessment = buildFallbackAssessment(snapshot)
        _backendFallbackBadge.value = assessment.label
        _backendFallbackLevel.value = assessment.level
    }

    private fun buildFallbackAssessment(snapshot: BackendHealthSnapshot): FallbackAssessment {
        val providedLevel = snapshot.fallbackLevel?.uppercase()
        if (!providedLevel.isNullOrBlank()) {
            val level = when (providedLevel) {
                "CRITICAL" -> BackendFallbackLevel.CRITICAL
                "HIGH" -> BackendFallbackLevel.HIGH
                "MODERATE" -> BackendFallbackLevel.MODERATE
                "NORMAL" -> BackendFallbackLevel.NORMAL
                "LOW_SAMPLE" -> BackendFallbackLevel.LOW_SAMPLE
                else -> BackendFallbackLevel.UNKNOWN
            }

            val label = if (snapshot.fallbackRateWindow != null && snapshot.sampleSizeWindow != null) {
                val pct = (snapshot.fallbackRateWindow * 100.0).toInt()
                "Fallback: ${providedLevel} (${pct}%, n=${snapshot.sampleSizeWindow})"
            } else {
                "Fallback: $providedLevel"
            }

            return FallbackAssessment(label, level)
        }

        val totalLookups = snapshot.fallbackServed + snapshot.upstreamSuccess
        if (totalLookups < 5) {
            return FallbackAssessment(
                label = "Fallback: amostra baixa",
                level = BackendFallbackLevel.LOW_SAMPLE
            )
        }

        val ratio = snapshot.fallbackServed.toDouble() / totalLookups.toDouble()
        return when {
            ratio >= 0.50 -> FallbackAssessment("Fallback: CRITICO", BackendFallbackLevel.CRITICAL)
            ratio >= 0.35 -> FallbackAssessment("Fallback: ALTO", BackendFallbackLevel.HIGH)
            ratio >= 0.20 -> FallbackAssessment("Fallback: MODERADO", BackendFallbackLevel.MODERATE)
            else -> FallbackAssessment("Fallback: NORMAL", BackendFallbackLevel.NORMAL)
        }
    }

    override fun onCleared() {
        stopBackendHealthMonitoring()
        super.onCleared()
    }
}

private data class FallbackAssessment(
    val label: String,
    val level: BackendFallbackLevel
)

enum class BackendFallbackLevel {
    UNKNOWN,
    LOW_SAMPLE,
    NORMAL,
    MODERATE,
    HIGH,
    CRITICAL
}

