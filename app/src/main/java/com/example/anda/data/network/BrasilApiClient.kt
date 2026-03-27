package com.example.anda.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object BrasilApiClient {

    suspend fun fetchCompanyByCnpj(rawCnpj: String): CompanyProfile = withContext(Dispatchers.IO) {
        val cnpj = normalizeCnpj(rawCnpj)
        require(cnpj.length == 14) { "CNPJ deve ter 14 digitos." }

        val endpoint = URL("https://brasilapi.com.br/api/cnpj/v1/$cnpj")
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
        }

        try {
            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                throw IllegalStateException("BrasilAPI retornou HTTP $statusCode")
            }

            val body = BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                reader.readText()
            }

            val json = JSONObject(body)
            CompanyProfile(
                cnpj = cnpj,
                legalName = json.optString("razao_social"),
                tradeName = json.optString("nome_fantasia"),
                cnae = json.optString("cnae_fiscal"),
                city = json.optString("municipio"),
                state = json.optString("uf"),
                postalCode = json.optString("cep")
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun normalizeCnpj(cnpj: String): String = cnpj.filter(Char::isDigit)
}

data class CompanyProfile(
    val cnpj: String,
    val legalName: String,
    val tradeName: String,
    val cnae: String,
    val city: String,
    val state: String,
    val postalCode: String
)

