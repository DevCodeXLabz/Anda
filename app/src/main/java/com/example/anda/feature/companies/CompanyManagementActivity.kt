package com.example.anda.feature.companies

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.anda.R
import com.example.anda.data.local.AppDatabase
import com.example.anda.data.local.entity.CompanyEntity
import com.example.anda.data.network.BrasilApiClient
import com.example.anda.databinding.ActivityCompanyManagementBinding
import com.example.anda.databinding.ItemCompanyCardBinding
import com.example.anda.domain.CnaeRiskMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages the local list of companies (CRUD + CNPJ lookup via BrasilAPI).
 *
 * Can also be launched in **pick mode** ([EXTRA_PICK_MODE] = true):
 * tapping a card returns company data via result extras.
 */
class CompanyManagementActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PICK_MODE = "pick_mode"

        const val RESULT_COMPANY_CNPJ      = "company_cnpj"
        const val RESULT_COMPANY_LEGAL_NAME = "company_legal_name"
        const val RESULT_COMPANY_TRADE_NAME = "company_trade_name"
        const val RESULT_COMPANY_CNAE      = "company_cnae"
        const val RESULT_COMPANY_CITY      = "company_city"
        const val RESULT_COMPANY_STATE     = "company_state"

        fun buildPickIntent(context: Context): Intent =
            Intent(context, CompanyManagementActivity::class.java).apply {
                putExtra(EXTRA_PICK_MODE, true)
            }
    }

    private lateinit var binding: ActivityCompanyManagementBinding
    private lateinit var db: AppDatabase

    private val pickMode get() = intent.getBooleanExtra(EXTRA_PICK_MODE, false)
    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCompanyManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getInstance(this)

        if (pickMode) {
            title = getString(R.string.company_mgmt_pick_mode_title)
            binding.addCompanyButton.visibility = View.GONE
            binding.addCompanyFormCard.visibility = View.GONE
            binding.companyListHeader.text = getString(R.string.company_mgmt_pick_mode_hint)
        }

        binding.addCompanyButton.setOnClickListener {
            binding.addCompanyFormCard.visibility = View.VISIBLE
            binding.addCompanyFormCard.requestFocus()
        }

        binding.cancelAddCompanyButton.setOnClickListener {
            binding.addCompanyFormCard.visibility = View.GONE
            clearForm()
        }

        binding.lookupCnpjButton.setOnClickListener {
            lookupCnpj()
        }

        binding.saveCompanyButton.setOnClickListener {
            saveCompanyFromForm()
        }

        binding.companySearchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchJob?.cancel()
                val query = s?.toString().orEmpty().trim()
                searchJob = lifecycleScope.launch {
                    delay(300)
                    loadCompanies(query)
                }
            }
        })

        loadCompanies("")
    }

    override fun onResume() {
        super.onResume()
        loadCompanies(binding.companySearchInput.text?.toString().orEmpty().trim())
    }

    // ── Data operations ───────────────────────────────────────────────────

    private fun loadCompanies(query: String) {
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) {
                if (query.isBlank()) db.companyDao().listAll()
                else db.companyDao().search(query)
            }
            renderList(list)
        }
    }

    private fun lookupCnpj() {
        val cnpj = binding.companyCnpjInput.text?.toString().orEmpty().filter(Char::isDigit)
        if (cnpj.length != 14) {
            Toast.makeText(this, getString(R.string.company_mgmt_cnpj_invalid), Toast.LENGTH_SHORT).show()
            return
        }

        binding.lookupProgressBar.visibility = View.VISIBLE
        binding.lookupCnpjButton.isEnabled = false

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { BrasilApiClient.fetchCompanyByCnpj(cnpj) }
            }

            binding.lookupProgressBar.visibility = View.GONE
            binding.lookupCnpjButton.isEnabled = true

            result.onSuccess { company ->
                binding.companyLegalNameInput.setText(company.legalName)
                binding.companyTradeNameInput.setText(company.tradeName)
                binding.companyCnaeInput.setText(company.cnae)
                binding.companyCityInput.setText(company.city)
                binding.companyStateInput.setText(company.state)
                val riskGrade = CnaeRiskMapper.map(company.cnae)
                val msg = getString(R.string.company_mgmt_lookup_success, company.legalName, riskGrade)
                Toast.makeText(this@CompanyManagementActivity, msg, Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                Toast.makeText(
                    this@CompanyManagementActivity,
                    getString(R.string.company_mgmt_lookup_error, err.message ?: ""),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun saveCompanyFromForm() {
        val cnpj = binding.companyCnpjInput.text?.toString().orEmpty().filter(Char::isDigit)
        val legalName = binding.companyLegalNameInput.text?.toString().orEmpty().trim()
        val tradeName = binding.companyTradeNameInput.text?.toString().orEmpty().trim()
        val cnae = binding.companyCnaeInput.text?.toString().orEmpty().trim()
        val city = binding.companyCityInput.text?.toString().orEmpty().trim()
        val state = binding.companyStateInput.text?.toString().orEmpty().trim().uppercase()
        val contactWhatsapp = binding.companyContactWhatsappInput.text?.toString().orEmpty().trim()
        val preferredChannel = if (binding.companyPreferWhatsappCheckbox.isChecked) "whatsapp" else "app"

        if (cnpj.length != 14) {
            Toast.makeText(this, getString(R.string.company_mgmt_cnpj_invalid), Toast.LENGTH_SHORT).show()
            return
        }
        if (legalName.isBlank()) {
            Toast.makeText(this, getString(R.string.company_mgmt_legal_name_required), Toast.LENGTH_SHORT).show()
            return
        }

        val entity = CompanyEntity(
            cnpj = cnpj,
            legalName = legalName,
            tradeName = tradeName,
            cnae = cnae,
            city = city,
            state = state,
            postalCode = "",
            contactWhatsapp = contactWhatsapp,
            preferredContactChannel = preferredChannel
        )

        lifecycleScope.launch {
            withContext(Dispatchers.IO) { db.companyDao().upsert(entity) }
            Toast.makeText(this@CompanyManagementActivity,
                getString(R.string.company_mgmt_saved_toast), Toast.LENGTH_SHORT).show()
            clearForm()
            binding.addCompanyFormCard.visibility = View.GONE
            loadCompanies(binding.companySearchInput.text?.toString().orEmpty().trim())
        }
    }

    private fun deleteCompany(entity: CompanyEntity) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { db.companyDao().deleteById(entity.id) }
            Toast.makeText(this@CompanyManagementActivity,
                getString(R.string.company_mgmt_deleted_toast), Toast.LENGTH_SHORT).show()
            loadCompanies(binding.companySearchInput.text?.toString().orEmpty().trim())
        }
    }

    private fun pickCompany(entity: CompanyEntity) {
        val data = Intent().apply {
            putExtra(RESULT_COMPANY_CNPJ,       entity.cnpj)
            putExtra(RESULT_COMPANY_LEGAL_NAME, entity.legalName)
            putExtra(RESULT_COMPANY_TRADE_NAME, entity.tradeName)
            putExtra(RESULT_COMPANY_CNAE,       entity.cnae)
            putExtra(RESULT_COMPANY_CITY,       entity.city)
            putExtra(RESULT_COMPANY_STATE,      entity.state)
        }
        setResult(Activity.RESULT_OK, data)
        finish()
    }

    // ── UI rendering ──────────────────────────────────────────────────────

    private fun renderList(list: List<CompanyEntity>) {
        val count = list.size
        binding.companyCountText.text =
            if (count == 0) getString(R.string.company_mgmt_count_placeholder)
            else getString(R.string.company_mgmt_count_template, count)

        binding.companyListContainer.removeAllViews()

        if (list.isEmpty()) {
            binding.companyEmptyText.visibility = View.VISIBLE
            return
        }

        binding.companyEmptyText.visibility = View.GONE
        val inflater = LayoutInflater.from(this)

        for (entity in list) {
            val card = ItemCompanyCardBinding.inflate(inflater, binding.companyListContainer, false)

            card.companyLegalNameText.text = entity.legalName
            card.companyTradeNameText.text = entity.tradeName.ifBlank { "—" }
            card.companyCnpjText.text = formatCnpj(entity.cnpj)
            card.companyCnaeText.text = if (entity.cnae.isNotBlank()) "CNAE: ${entity.cnae}" else ""
            card.companyCityStateText.text = buildCityState(entity)
            card.companyContactText.text = buildContactLabel(entity)

            if (pickMode) {
                card.deleteCompanyButton.visibility = View.GONE
                card.root.setOnClickListener { pickCompany(entity) }
            } else {
                card.deleteCompanyButton.setOnClickListener { deleteCompany(entity) }
                card.root.setOnClickListener {
                    // fill form with existing data for editing
                    binding.companyCnpjInput.setText(entity.cnpj)
                    binding.companyLegalNameInput.setText(entity.legalName)
                    binding.companyTradeNameInput.setText(entity.tradeName)
                    binding.companyCnaeInput.setText(entity.cnae)
                    binding.companyCityInput.setText(entity.city)
                    binding.companyStateInput.setText(entity.state)
                    binding.companyContactWhatsappInput.setText(entity.contactWhatsapp)
                    binding.companyPreferWhatsappCheckbox.isChecked = entity.preferredContactChannel == "whatsapp"
                    binding.addCompanyFormCard.visibility = View.VISIBLE
                    binding.addCompanyFormCard.requestFocus()
                }
            }

            binding.companyListContainer.addView(card.root)
        }
    }

    private fun clearForm() {
        binding.companyCnpjInput.setText("")
        binding.companyLegalNameInput.setText("")
        binding.companyTradeNameInput.setText("")
        binding.companyCnaeInput.setText("")
        binding.companyCityInput.setText("")
        binding.companyStateInput.setText("")
        binding.companyContactWhatsappInput.setText("")
        binding.companyPreferWhatsappCheckbox.isChecked = false
    }

    // ── Formatting helpers ────────────────────────────────────────────────

    private fun formatCnpj(cnpj: String): String {
        val d = cnpj.filter(Char::isDigit)
        return if (d.length == 14)
            "${d.substring(0,2)}.${d.substring(2,5)}.${d.substring(5,8)}/${d.substring(8,12)}-${d.substring(12)}"
        else cnpj
    }

    private fun buildCityState(entity: CompanyEntity): String {
        val parts = listOf(entity.city, entity.state).filter(String::isNotBlank)
        return parts.joinToString(" — ")
    }

    private fun buildContactLabel(entity: CompanyEntity): String {
        return if (entity.preferredContactChannel == "whatsapp") {
            if (entity.contactWhatsapp.isNotBlank()) {
                getString(R.string.company_mgmt_contact_whatsapp_template, entity.contactWhatsapp)
            } else {
                getString(R.string.company_mgmt_contact_whatsapp_missing)
            }
        } else {
            getString(R.string.company_mgmt_contact_app_only)
        }
    }
}

