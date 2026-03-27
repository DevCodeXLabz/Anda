package com.example.anda.data.network

import com.example.anda.BuildConfig
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object OfficialCaApiClient {

    fun hasConfiguredBackend(): Boolean = BuildConfig.CA_API_BASE_URL.trim().isNotBlank()

    fun pingBackend(timeoutMs: Int = 4_000): Boolean {
        val baseUrl = BuildConfig.CA_API_BASE_URL.trim()
        if (baseUrl.isBlank()) return false

        val normalizedBase = if (baseUrl.endsWith('/')) baseUrl.dropLast(1) else baseUrl
        val endpoint = URL("$normalizedBase/health")
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
        }

        return try {
            connection.responseCode in 200..299
        } catch (_: Throwable) {
            false
        } finally {
            connection.disconnect()
        }
    }

    fun fetchBackendHealthSnapshot(timeoutMs: Int = 4_000): BackendHealthSnapshot? {
        val baseUrl = BuildConfig.CA_API_BASE_URL.trim()
        if (baseUrl.isBlank()) return null

        val normalizedBase = if (baseUrl.endsWith('/')) baseUrl.dropLast(1) else baseUrl
        val endpoint = URL("$normalizedBase/home/metrics")
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("Accept", "application/json")
        }

        return try {
            if (connection.responseCode !in 200..299) return null
            val body = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
            val json = JSONObject(body)
            if (json.has("fallback_level")) {
                return BackendHealthSnapshot(
                    fallbackServed = json.optInt("fallback_count_window", 0),
                    upstreamSuccess = 0,
                    cacheHits = 0,
                    cacheMisses = 0,
                    upstreamFailures = 0,
                    fallbackRateWindow = json.optDouble("fallback_rate_window", 0.0),
                    fallbackLevel = json.optString("fallback_level", "UNKNOWN").uppercase(),
                    sampleSizeWindow = json.optInt("sample_size_window", 0)
                )
            }

            val metrics = json.optJSONObject("metrics") ?: JSONObject()
            BackendHealthSnapshot(
                fallbackServed = metrics.optInt("fallbackServed", 0),
                upstreamSuccess = metrics.optInt("upstreamSuccess", 0),
                cacheHits = metrics.optInt("cacheHits", 0),
                cacheMisses = metrics.optInt("cacheMisses", 0),
                upstreamFailures = metrics.optInt("upstreamFailures", 0)
            )
        } catch (_: Throwable) {
            null
        } finally {
            connection.disconnect()
        }
    }

    // The endpoint is configurable via gradle property CA_API_BASE_URL.
    // Expected pattern: {baseUrl}/ca/{numero}
    fun fetchByCaNumber(caNumber: String): OfficialCaResponse? {
        val normalizedCa = caNumber.trim()
        if (normalizedCa.isBlank()) return null

        val baseUrl = BuildConfig.CA_API_BASE_URL.trim()
        if (baseUrl.isBlank()) return null

        val normalizedBase = if (baseUrl.endsWith('/')) baseUrl.dropLast(1) else baseUrl
        val endpoint = URL("$normalizedBase/ca/$normalizedCa")

        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Accept", "application/json")
        }

        return try {
            if (connection.responseCode !in 200..299) return null

            val body = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
            val json = JSONObject(body)

            OfficialCaResponse(
                caNumber = json.optString("ca", normalizedCa),
                isValid = readBoolean(json, listOf("valido", "is_valid", "isValid", "status")),
                itemName = readString(json, listOf("item_name", "equipamento", "item", "nome", "descricao")),
                riskCoverage = readString(json, listOf("protecao", "risk_coverage", "cobertura")),
                validUntil = readString(json, listOf("validade", "valid_until", "data_validade")),
                source = readOptionalString(json, listOf("source")) ?: "oficial",
                statusReason = readOptionalString(json, listOf("status_reason"))
            )
        } catch (_: Throwable) {
            null
        } finally {
            connection.disconnect()
        }
    }

    // Supports either a plain URL or a template with {ca} placeholder.
    fun buildOfficialConsultUrl(caNumber: String): String? {
        val normalizedCa = caNumber.trim()
        if (normalizedCa.isBlank()) return null

        val configured = BuildConfig.CA_OFFICIAL_CONSULT_URL.trim()
        if (configured.isBlank()) return null

        return if (configured.contains("{ca}")) {
            configured.replace("{ca}", normalizedCa)
        } else {
            configured
        }
    }

    private fun readBoolean(json: JSONObject, keys: List<String>): Boolean {
        for (key in keys) {
            if (!json.has(key)) continue
            val value = json.opt(key)
            when (value) {
                is Boolean -> return value
                is String -> {
                    val normalized = value.trim().lowercase()
                    if (normalized in setOf("true", "valido", "válido", "ok", "ativo", "active")) {
                        return true
                    }
                    if (normalized in setOf("false", "invalido", "inválido", "vencido", "expired", "inactive")) {
                        return false
                    }
                }
                is Number -> return value.toInt() != 0
            }
        }
        return false
    }

    private fun readString(json: JSONObject, keys: List<String>): String {
        return readOptionalString(json, keys) ?: "-"
    }

    private fun readOptionalString(json: JSONObject, keys: List<String>): String? {
        for (key in keys) {
            val value = json.optString(key)
            if (value.isNotBlank()) return value
        }
        return null
    }
}

data class OfficialCaResponse(
    val caNumber: String,
    val isValid: Boolean,
    val itemName: String,
    val riskCoverage: String,
    val validUntil: String,
    val source: String,
    val statusReason: String? = null
)

data class BackendHealthSnapshot(
    val fallbackServed: Int,
    val upstreamSuccess: Int,
    val cacheHits: Int,
    val cacheMisses: Int,
    val upstreamFailures: Int,
    val fallbackRateWindow: Double? = null,
    val fallbackLevel: String? = null,
    val sampleSizeWindow: Int? = null
)

