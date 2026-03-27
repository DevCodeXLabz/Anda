package com.example.anda.feature.employees

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.anda.R
import com.example.anda.data.local.AppDatabase
import com.example.anda.data.local.entity.EmployeeEntity
import com.example.anda.databinding.ActivityEmployeeManagementBinding
import com.example.anda.databinding.ItemEmployeeCardBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Manages the local list of employees (CRUD).
 *
 * Can also be launched in **pick mode** (extra [EXTRA_PICK_MODE] = true):
 * the user taps a card and the activity returns that employee's data via
 * [RESULT_EMPLOYEE_CPF], [RESULT_EMPLOYEE_NAME], [RESULT_EMPLOYEE_BIRTH_DATE],
 * [RESULT_EMPLOYEE_ROLE], [RESULT_EMPLOYEE_COMPANY_CNPJ], [RESULT_EMPLOYEE_COMPANY_NAME].
 */
class EmployeeManagementActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PICK_MODE = "pick_mode"
        const val EXTRA_PREFILL_CNPJ = "prefill_cnpj"

        const val RESULT_EMPLOYEE_CPF           = "emp_cpf"
        const val RESULT_EMPLOYEE_NAME          = "emp_name"
        const val RESULT_EMPLOYEE_BIRTH_DATE    = "emp_birth_date"
        const val RESULT_EMPLOYEE_ROLE          = "emp_role"
        const val RESULT_EMPLOYEE_COMPANY_CNPJ  = "emp_company_cnpj"
        const val RESULT_EMPLOYEE_COMPANY_NAME  = "emp_company_name"

        fun buildPickIntent(context: Context, prefillCnpj: String = ""): Intent =
            Intent(context, EmployeeManagementActivity::class.java).apply {
                putExtra(EXTRA_PICK_MODE, true)
                putExtra(EXTRA_PREFILL_CNPJ, prefillCnpj)
            }
    }

    private lateinit var binding: ActivityEmployeeManagementBinding
    private lateinit var db: AppDatabase

    private val pickMode get() = intent.getBooleanExtra(EXTRA_PICK_MODE, false)
    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEmployeeManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getInstance(this)

        if (pickMode) {
            title = getString(R.string.emp_pick_mode_title)
            binding.addEmployeeButton.visibility = View.GONE
            binding.addEmployeeForm.visibility = View.GONE
            binding.empListHeader.text = getString(R.string.emp_pick_mode_hint)
        }

        // Pre-fill CNPJ filter if launched from ASO
        val prefillCnpj = intent.getStringExtra(EXTRA_PREFILL_CNPJ).orEmpty()
        if (prefillCnpj.isNotBlank()) {
            binding.empCnpjFilterInput.setText(prefillCnpj)
        }

        binding.addEmployeeButton.setOnClickListener { toggleForm() }
        binding.saveEmployeeButton.setOnClickListener { saveEmployee() }
        binding.cancelEmployeeButton.setOnClickListener { collapseForm() }

        setupSearchListeners()
        loadEmployees()
    }

    override fun onResume() {
        super.onResume()
        loadEmployees()
    }

    // ── Form toggle ────────────────────────────────────────────────────────────

    private fun toggleForm() {
        val visible = binding.addEmployeeForm.visibility == View.VISIBLE
        binding.addEmployeeForm.visibility = if (visible) View.GONE else View.VISIBLE
        binding.addEmployeeButton.text = getString(
            if (visible) R.string.emp_add_button else R.string.emp_cancel_button
        )
    }

    private fun collapseForm() {
        binding.addEmployeeForm.visibility = View.GONE
        binding.addEmployeeButton.text = getString(R.string.emp_add_button)
        clearFormFields()
    }

    private fun clearFormFields() {
        binding.empCpfInput.text?.clear()
        binding.empNameInput.text?.clear()
        binding.empBirthDateInput.text?.clear()
        binding.empRoleInput.text?.clear()
        binding.empFreelancerCheckbox.isChecked = false
        binding.empApprovedCheckbox.isChecked = true
        binding.empCompanyCnpjInput.text?.clear()
        binding.empCompanyNameInput.text?.clear()
        binding.empNotesInput.text?.clear()
    }

    // ── Save employee ───────────────────────────────────────────────────────────

    private fun saveEmployee() {
        val cpf  = binding.empCpfInput.text.toString().filter { it.isDigit() }
        val name = binding.empNameInput.text.toString().trim()

        if (cpf.length != 11) {
            Toast.makeText(this, getString(R.string.emp_error_cpf), Toast.LENGTH_SHORT).show()
            return
        }
        if (name.isBlank()) {
            Toast.makeText(this, getString(R.string.emp_error_name), Toast.LENGTH_SHORT).show()
            return
        }

        val entity = EmployeeEntity(
            cpf          = cpf,
            name         = name,
            birthDate    = binding.empBirthDateInput.text.toString().trim(),
            role         = binding.empRoleInput.text.toString().trim(),
            companyCnpj  = binding.empCompanyCnpjInput.text.toString().filter { it.isDigit() },
            companyName  = binding.empCompanyNameInput.text.toString().trim(),
            collaboratorType = if (binding.empFreelancerCheckbox.isChecked) "freelancer" else "internal",
            approvalStatus = when {
                binding.empFreelancerCheckbox.isChecked && !binding.empApprovedCheckbox.isChecked -> "pending"
                else -> "approved"
            },
            approvedBy = if (binding.empApprovedCheckbox.isChecked) {
                getString(R.string.emp_approval_source_prestadora)
            } else {
                ""
            },
            approvedAt = if (binding.empApprovedCheckbox.isChecked) System.currentTimeMillis() else null,
            notes        = binding.empNotesInput.text.toString().trim()
        )

        lifecycleScope.launch {
            withContext(Dispatchers.IO) { db.employeeDao().upsert(entity) }
            Toast.makeText(this@EmployeeManagementActivity, getString(R.string.emp_saved_toast), Toast.LENGTH_SHORT).show()
            collapseForm()
            loadEmployees()
        }
    }

    // ── Search / filter ─────────────────────────────────────────────────────────

    private fun setupSearchListeners() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(300)
                    loadEmployees()
                }
            }
            override fun afterTextChanged(s: Editable?) = Unit
        }
        binding.empSearchInput.addTextChangedListener(watcher)
        binding.empCnpjFilterInput.addTextChangedListener(watcher)
    }

    // ── Load & render list ───────────────────────────────────────────────────────

    private fun loadEmployees() {
        lifecycleScope.launch {
            val query  = binding.empSearchInput.text.toString().trim()
            val cnpj   = binding.empCnpjFilterInput.text.toString().filter { it.isDigit() }

            val list = withContext(Dispatchers.IO) {
                when {
                    query.isNotBlank() -> db.employeeDao().search(query)
                    cnpj.isNotBlank()  -> db.employeeDao().listByCompany(cnpj)
                    else               -> db.employeeDao().listAll()
                }
            }

            val total = withContext(Dispatchers.IO) { db.employeeDao().count() }
            binding.empCountText.text = getString(R.string.emp_count_template, total)

            renderList(list)
        }
    }

    private fun renderList(list: List<EmployeeEntity>) {
        binding.empListContainer.removeAllViews()

        if (list.isEmpty()) {
            binding.empEmptyText.visibility = View.VISIBLE
            return
        }
        binding.empEmptyText.visibility = View.GONE

        val inflater = LayoutInflater.from(this)
        for (emp in list) {
            val cardBinding = ItemEmployeeCardBinding.inflate(inflater, binding.empListContainer, false)

            cardBinding.empNameText.text = emp.name
            cardBinding.empCpfText.text = formatCpf(emp.cpf)
            cardBinding.empRoleText.text = emp.role.ifBlank { getString(R.string.emp_role_unspecified) }
            cardBinding.empCompanyText.text = if (emp.companyName.isNotBlank()) emp.companyName
                                              else emp.companyCnpj.ifBlank { "—" }
            cardBinding.empAsoStatusText.text = asoStatusLabel(emp)
            applyAsoStatusColor(cardBinding.empAsoStatusText, emp)
            cardBinding.empAccessStatusText.text = collaboratorStatusLabel(emp)
            applyCollaboratorStatusColor(cardBinding.empAccessStatusText, emp)

            if (pickMode) {
                // Tap whole card to pick
                cardBinding.root.isClickable = true
                cardBinding.root.isFocusable = true
                cardBinding.removeEmployeeButton.visibility = View.GONE
                cardBinding.root.setOnClickListener {
                    if (emp.collaboratorType == "freelancer" && emp.approvalStatus != "approved") {
                        Toast.makeText(
                            this,
                            getString(R.string.emp_pick_requires_approval),
                            Toast.LENGTH_SHORT
                        ).show()
                        return@setOnClickListener
                    }
                    returnPickedEmployee(emp)
                }
            } else {
                cardBinding.removeEmployeeButton.setOnClickListener { deleteEmployee(emp) }
            }

            binding.empListContainer.addView(cardBinding.root)
        }
    }

    // ── Pick mode result ─────────────────────────────────────────────────────────

    private fun returnPickedEmployee(emp: EmployeeEntity) {
        val result = Intent().apply {
            putExtra(RESULT_EMPLOYEE_CPF,          emp.cpf)
            putExtra(RESULT_EMPLOYEE_NAME,         emp.name)
            putExtra(RESULT_EMPLOYEE_BIRTH_DATE,   emp.birthDate)
            putExtra(RESULT_EMPLOYEE_ROLE,         emp.role)
            putExtra(RESULT_EMPLOYEE_COMPANY_CNPJ, emp.companyCnpj)
            putExtra(RESULT_EMPLOYEE_COMPANY_NAME, emp.companyName)
        }
        setResult(Activity.RESULT_OK, result)
        finish()
    }

    // ── Delete ───────────────────────────────────────────────────────────────────

    private fun deleteEmployee(emp: EmployeeEntity) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { db.employeeDao().delete(emp) }
            Toast.makeText(this@EmployeeManagementActivity, getString(R.string.emp_deleted_toast), Toast.LENGTH_SHORT).show()
            loadEmployees()
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private fun formatCpf(cpf: String): String {
        val d = cpf.filter { it.isDigit() }.padEnd(11, '0')
        return if (d.length >= 11)
            "CPF: ${d.substring(0,3)}.${d.substring(3,6)}.${d.substring(6,9)}-${d.substring(9,11)}"
        else "CPF: $cpf"
    }

    private fun asoStatusLabel(emp: EmployeeEntity): String {
        val next = emp.nextAsoDate ?: return ""
        val now  = System.currentTimeMillis()
        val days = TimeUnit.MILLISECONDS.toDays(next - now)
        return when {
            days < 0    -> getString(R.string.emp_aso_expired)
            days <= 30  -> getString(R.string.emp_aso_expiring_template, days)
            else        -> {
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"))
                getString(R.string.emp_aso_valid_template, sdf.format(Date(next)))
            }
        }
    }

    private fun applyAsoStatusColor(tv: TextView, emp: EmployeeEntity) {
        val next = emp.nextAsoDate ?: return
        val now  = System.currentTimeMillis()
        val days = TimeUnit.MILLISECONDS.toDays(next - now)
        val colorRes = when {
            days < 0   -> R.color.status_critical
            days <= 30 -> R.color.status_moderate
            else       -> R.color.status_normal
        }
        tv.setTextColor(getColor(colorRes))
    }

    private fun collaboratorStatusLabel(emp: EmployeeEntity): String {
        return if (emp.collaboratorType == "freelancer") {
            if (emp.approvalStatus == "approved") {
                getString(R.string.emp_collab_freelancer_approved)
            } else {
                getString(R.string.emp_collab_freelancer_pending)
            }
        } else {
            getString(R.string.emp_collab_internal)
        }
    }

    private fun applyCollaboratorStatusColor(tv: TextView, emp: EmployeeEntity) {
        val colorRes = when {
            emp.collaboratorType == "freelancer" && emp.approvalStatus != "approved" -> R.color.status_moderate
            else -> R.color.status_normal
        }
        tv.setTextColor(getColor(colorRes))
    }
}



