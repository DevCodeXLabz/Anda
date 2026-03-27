package com.example.anda.feature.epi

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.anda.databinding.ActivityEpiManagementBinding
import com.example.anda.databinding.ItemEpiCardBinding
import com.example.anda.feature.scanner.CaScannerActivity
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EpiManagementActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEpiManagementBinding

    private companion object {
        const val PREFS_EPI = "anda_epi_tracker"
        const val KEY_EPI_LIST = "epi_list"
        val DATE_FORMAT = SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEpiManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.scanCaButton.setOnClickListener {
            startActivity(Intent(this, CaScannerActivity::class.java))
        }

        binding.addEpiManualButton.setOnClickListener {
            binding.addEpiFormCard.visibility = View.VISIBLE
            binding.addEpiFormCard.requestFocus()
        }

        binding.cancelAddEpiButton.setOnClickListener {
            binding.addEpiFormCard.visibility = View.GONE
            clearForm()
        }

        binding.saveEpiButton.setOnClickListener {
            saveEpiFromForm()
        }

        // Pre-fill from scanner result if passed via intent
        intent.getStringExtra("ca_number")?.let { ca ->
            binding.addEpiFormCard.visibility = View.VISIBLE
            binding.epiCaNumberInput.setText(ca)
        }

        loadAndRenderEpis()
    }

    override fun onResume() {
        super.onResume()
        loadAndRenderEpis()
    }

    private fun saveEpiFromForm() {
        val type = binding.epiTypeInput.text.toString().trim()
        val caNumber = binding.epiCaNumberInput.text.toString().trim()
        val manufacturer = binding.epiManufacturerInput.text.toString().trim()
        val expiry = binding.epiExpiryInput.text.toString().trim()

        if (type.isBlank() || caNumber.isBlank()) {
            Toast.makeText(this, "Informe o tipo de EPI e o número do C.A.", Toast.LENGTH_SHORT).show()
            return
        }

        val epiObj = JSONObject().apply {
            put("id", System.currentTimeMillis().toString())
            put("type", type)
            put("ca", caNumber)
            put("manufacturer", manufacturer)
            put("expiry", expiry)
            put("addedAt", DATE_FORMAT.format(Date()))
        }

        val list = loadEpiList()
        list.put(epiObj)
        saveEpiList(list)

        binding.addEpiFormCard.visibility = View.GONE
        clearForm()
        loadAndRenderEpis()
        Toast.makeText(this, "EPI cadastrado com sucesso!", Toast.LENGTH_SHORT).show()
    }

    private fun clearForm() {
        binding.epiTypeInput.setText("")
        binding.epiCaNumberInput.setText("")
        binding.epiManufacturerInput.setText("")
        binding.epiExpiryInput.setText("")
    }

    private fun loadAndRenderEpis() {
        val list = loadEpiList()
        binding.epiListContainer.removeAllViews()

        var valid = 0
        var expiring = 0
        var expired = 0

        if (list.length() == 0) {
            binding.epiEmptyCard.visibility = View.VISIBLE
        } else {
            binding.epiEmptyCard.visibility = View.GONE
            for (i in 0 until list.length()) {
                runCatching {
                    val epi = list.getJSONObject(i)
                    val status = evaluateStatus(epi.optString("expiry"))
                    when (status) {
                        EpiStatus.VALID -> valid++
                        EpiStatus.EXPIRING -> expiring++
                        EpiStatus.EXPIRED -> expired++
                        EpiStatus.UNKNOWN -> valid++
                    }
                    addEpiCard(epi, status)
                }
            }
        }

        binding.epiCountValid.text = valid.toString()
        binding.epiCountExpiring.text = expiring.toString()
        binding.epiCountExpired.text = expired.toString()
    }

    private fun addEpiCard(epi: JSONObject, status: EpiStatus) {
        val itemBinding = ItemEpiCardBinding.inflate(layoutInflater, binding.epiListContainer, false)
        val type = epi.optString("type", "EPI")
        val ca = epi.optString("ca", "—")
        val manufacturer = epi.optString("manufacturer", "")
        val expiry = epi.optString("expiry", "")
        val addedAt = epi.optString("addedAt", "")
        val id = epi.optString("id", "")

        itemBinding.epiTypeName.text = type
        itemBinding.epiCaNumber.text = "C.A. $ca"
        itemBinding.epiManufacturerText.text = if (manufacturer.isNotBlank()) manufacturer else "Fabricante não informado"
        itemBinding.epiExpiryText.text = if (expiry.isNotBlank()) "Validade: $expiry" else "Validade não informada"
        itemBinding.epiAddedAt.text = if (addedAt.isNotBlank()) "Cadastrado em $addedAt" else ""

        val (statusLabel, statusColor) = when (status) {
            EpiStatus.VALID -> "✅ VÁLIDO" to getColor(com.example.anda.R.color.status_normal)
            EpiStatus.EXPIRING -> "⚠️ VENCENDO" to getColor(com.example.anda.R.color.status_moderate)
            EpiStatus.EXPIRED -> "❌ VENCIDO" to getColor(com.example.anda.R.color.status_high)
            EpiStatus.UNKNOWN -> "ℹ️ SEM VALIDADE" to getColor(com.example.anda.R.color.anda_text_secondary)
        }
        itemBinding.epiStatusChip.text = statusLabel
        itemBinding.epiStatusChip.setTextColor(statusColor)

        itemBinding.removeEpiButton.setOnClickListener {
            removeEpiById(id)
        }

        binding.epiListContainer.addView(itemBinding.root)
    }

    private fun evaluateStatus(expiryStr: String): EpiStatus {
        if (expiryStr.isBlank()) return EpiStatus.UNKNOWN
        return runCatching {
            val expiry = DATE_FORMAT.parse(expiryStr) ?: return@runCatching EpiStatus.UNKNOWN
            val now = Date()
            val thirtyDaysMs = 30L * 24L * 60L * 60L * 1000L
            when {
                expiry.before(now) -> EpiStatus.EXPIRED
                (expiry.time - now.time) <= thirtyDaysMs -> EpiStatus.EXPIRING
                else -> EpiStatus.VALID
            }
        }.getOrElse { EpiStatus.UNKNOWN }
    }

    private fun removeEpiById(id: String) {
        val list = loadEpiList()
        val newList = JSONArray()
        for (i in 0 until list.length()) {
            runCatching {
                val item = list.getJSONObject(i)
                if (item.optString("id") != id) newList.put(item)
            }
        }
        saveEpiList(newList)
        loadAndRenderEpis()
        Toast.makeText(this, "EPI removido.", Toast.LENGTH_SHORT).show()
    }

    private fun loadEpiList(): JSONArray {
        val prefs = getSharedPreferences(PREFS_EPI, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_EPI_LIST, "[]") ?: "[]"
        return runCatching { JSONArray(json) }.getOrElse { JSONArray() }
    }

    private fun saveEpiList(list: JSONArray) {
        getSharedPreferences(PREFS_EPI, Context.MODE_PRIVATE)
            .edit().putString(KEY_EPI_LIST, list.toString()).apply()
    }

    enum class EpiStatus { VALID, EXPIRING, EXPIRED, UNKNOWN }
}

