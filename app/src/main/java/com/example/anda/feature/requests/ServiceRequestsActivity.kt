package com.example.anda.feature.requests

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.Context
import android.net.Uri
import androidx.core.widget.addTextChangedListener
import android.os.Build
import android.os.Bundle
import android.content.res.ColorStateList
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.anda.R
import com.example.anda.core.profile.ProfileManager
import com.example.anda.data.local.AppDatabase
import com.example.anda.data.local.entity.DocumentEntity
import com.example.anda.data.local.entity.EmployeeEntity
import com.example.anda.data.local.entity.ServiceRequestEntity
import com.example.anda.data.repository.DocumentLocalRepository
import com.example.anda.data.requests.ServiceRequestAssignmentEngine
import com.example.anda.data.requests.ServiceRequestNotificationService
import com.example.anda.data.requests.ServiceRequestStatus
import com.example.anda.data.requests.TechnicianProfile
import com.example.anda.databinding.ActivityServiceRequestsBinding
import com.example.anda.databinding.ItemServiceRequestCardBinding
import com.example.anda.feature.documents.DocumentDetailActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class ServiceRequestsActivity : AppCompatActivity() {

    private data class RequestUiSummary(
        val request: ServiceRequestEntity,
        val linkedDocument: DocumentEntity?,
        val latestActionEpochMs: Long?
    )

    private lateinit var binding: ActivityServiceRequestsBinding
    private lateinit var db: AppDatabase
    private val assignmentEngine = ServiceRequestAssignmentEngine()
    private lateinit var notificationService: ServiceRequestNotificationService
    private var statusFilter: String = FILTER_ALL
    private var technicianMode: Boolean = false
    private var technicianName: String = ""
    private var technicianCpf: String = ""
    private var focusRequestCode: String = ""
    private var sortByLatestAction: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityServiceRequestsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getInstance(this)
        notificationService = ServiceRequestNotificationService(this)
        DocumentLocalRepository.initialize(this)
        technicianMode = intent.getBooleanExtra(EXTRA_TECHNICIAN_MODE, false)
        technicianName = ProfileManager.getDisplayName(this).trim()
        sortByLatestAction = readSortPreference()
        statusFilter = readStatusPreference()
        consumeIntent(intent)
        notificationService.createNotificationChannel()

        lifecycleScope.launch(Dispatchers.IO) {
            db.serviceRequestDao().normalizeLegacyCompletedStatuses()
        }

        if (technicianMode) {
            binding.formCard.visibility = View.GONE
            if (technicianName.isBlank()) {
                binding.emptyText.text = getString(R.string.service_request_technician_empty_name)
            }
        }

        binding.saveRequestButton.setOnClickListener { createRequest() }
        binding.refreshButton.setOnClickListener { loadRequests() }
        binding.filterAllButton.setOnClickListener {
            statusFilter = FILTER_ALL
            persistStatusPreference(statusFilter)
            updateFilterButtonVisuals()
            loadRequests()
        }
        binding.filterOpenButton.setOnClickListener {
            statusFilter = FILTER_OPEN
            persistStatusPreference(statusFilter)
            updateFilterButtonVisuals()
            loadRequests()
        }
        binding.filterActiveButton.setOnClickListener {
            statusFilter = FILTER_ACTIVE
            persistStatusPreference(statusFilter)
            updateFilterButtonVisuals()
            loadRequests()
        }
        binding.sortLatestActionButton.setOnClickListener {
            sortByLatestAction = !sortByLatestAction
            persistSortPreference(sortByLatestAction)
            renderSortModeLabel()
            loadRequests()
        }
        renderSortModeLabel()
        updateFilterButtonVisuals()

        // restore contractor CNPJ filter
        val persistedCnpj = readContractorCnpjFilter()
        if (persistedCnpj.isNotBlank()) {
            binding.contractorCnpjFilterInput.setText(persistedCnpj)
        }

        // persist CNPJ filter when changed
        binding.contractorCnpjFilterInput.addTextChangedListener {
            val text = it?.toString().orEmpty()
            persistContractorCnpjFilter(text)
        }

        loadRequests()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeIntent(intent)
        loadRequests()
    }

    override fun onResume() {
        super.onResume()
        loadRequests()
    }

    private fun createRequest() {
        val contractorName = binding.contractorNameInput.text?.toString().orEmpty().trim()
        val contractorCnpj = binding.contractorCnpjInput.text?.toString().orEmpty().filter(Char::isDigit)
        val documentType = binding.documentTypeInput.text?.toString().orEmpty().trim()
        val whatsapp = binding.contactWhatsappInput.text?.toString().orEmpty().trim()
        val notes = binding.notesInput.text?.toString().orEmpty().trim()
        val preferWhatsapp = binding.preferWhatsappCheckbox.isChecked

        if (contractorName.isBlank()) {
            Toast.makeText(this, getString(R.string.service_request_error_name_required), Toast.LENGTH_SHORT).show()
            return
        }
        if (contractorCnpj.length != 14) {
            Toast.makeText(this, getString(R.string.service_request_error_cnpj_invalid), Toast.LENGTH_SHORT).show()
            return
        }
        if (documentType.isBlank()) {
            Toast.makeText(this, getString(R.string.service_request_error_doc_required), Toast.LENGTH_SHORT).show()
            return
        }

        val now = System.currentTimeMillis()
        val request = ServiceRequestEntity(
            requestCode = buildRequestCode(contractorName),
            contractorName = contractorName,
            contractorCnpj = contractorCnpj,
            requestedDocumentType = documentType,
            preferredContactChannel = if (preferWhatsapp) "whatsapp" else "app",
            contactWhatsapp = whatsapp,
            notes = notes,
            status = ServiceRequestStatus.OPEN,
            createdAt = now,
            updatedAt = now
        )

        lifecycleScope.launch {
            val savedRequest = withContext(Dispatchers.IO) {
                val requestId = db.serviceRequestDao().upsert(request)
                db.serviceRequestDao().findById(requestId) ?: request.copy(id = requestId)
            }
            val autoAssignedName = autoAssignBestMatch(savedRequest)
            clearForm()
            val message = if (autoAssignedName.isBlank()) {
                getString(R.string.service_request_saved)
            } else {
                getString(R.string.service_request_assign_success, autoAssignedName)
            }
            Toast.makeText(this@ServiceRequestsActivity, message, Toast.LENGTH_SHORT).show()
            focusRequestCode = savedRequest.requestCode
            loadRequests()
        }
    }

    private fun loadRequests() {
        lifecycleScope.launch {
            if (technicianMode && technicianCpf.isBlank() && technicianName.isNotBlank()) {
                technicianCpf = withContext(Dispatchers.IO) {
                    db.employeeDao().findByExactName(technicianName)?.cpf.orEmpty()
                }
            }

            val cnpjFilter = binding.contractorCnpjFilterInput.text?.toString().orEmpty().filter(Char::isDigit)
            val filteredRequests = withContext(Dispatchers.IO) { db.serviceRequestDao().listAll() }
                .filter { request ->
                    val normalizedStatus = ServiceRequestStatus.normalize(request.status)
                    val statusAllowed = when (statusFilter) {
                        FILTER_OPEN -> ServiceRequestStatus.isOpen(normalizedStatus)
                        FILTER_ACTIVE -> ServiceRequestStatus.isActive(normalizedStatus)
                        else -> true
                    }
                    val cnpjAllowed = cnpjFilter.isBlank() || request.contractorCnpj.contains(cnpjFilter)
                    val technicianAllowed = if (!technicianMode) {
                        true
                    } else {
                        (technicianCpf.isNotBlank() && request.assignedEmployeeCpf == technicianCpf) ||
                            (technicianCpf.isBlank() && technicianName.isNotBlank() &&
                                request.assignedEmployeeName.equals(technicianName, ignoreCase = true))
                    }
                    statusAllowed && cnpjAllowed && technicianAllowed
                }
            val summaries = withContext(Dispatchers.IO) {
                filteredRequests.map { request ->
                    RequestUiSummary(
                        request = request,
                        linkedDocument = DocumentLocalRepository.findLatestByRequestCode(request.requestCode),
                        latestActionEpochMs = ServiceRequestOperationalHistoryFormatter.latestActionEpochMs(request.notes)
                    )
                }
            }
            val sorted = ServiceRequestSortPolicy.sort(
                items = summaries,
                focusRequestCode = focusRequestCode,
                sortByLatestAction = sortByLatestAction,
                requestCodeOf = { it.request.requestCode },
                updatedAtOf = { it.request.updatedAt },
                latestActionEpochMsOf = { it.latestActionEpochMs }
            )
            renderRequests(sorted)
        }
    }

    private fun readSortPreference(): Boolean {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(ServiceRequestsPreferencePolicy.sortPreferenceKey(technicianMode), false)
    }

    private fun persistSortPreference(enabled: Boolean) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(ServiceRequestsPreferencePolicy.sortPreferenceKey(technicianMode), enabled)
            .apply()
    }

    private fun renderRequests(items: List<RequestUiSummary>) {
        binding.requestsCountText.text = getString(R.string.service_request_count_template, items.size)
        binding.requestsContainer.removeAllViews()

        if (items.isEmpty()) {
            binding.emptyText.visibility = View.VISIBLE
            binding.emptyText.text = if (technicianMode && technicianName.isBlank()) {
                getString(R.string.service_request_technician_empty_name)
            } else {
                getString(R.string.service_request_empty)
            }
            return
        }
        binding.emptyText.visibility = View.GONE

        val inflater = layoutInflater
        items.forEach { item ->
            val request = item.request
            val normalizedStatus = ServiceRequestStatus.normalize(request.status)
            val card = ItemServiceRequestCardBinding.inflate(inflater, binding.requestsContainer, false)
            card.requestCodeText.text = request.requestCode
            card.requestContractorText.text = request.contractorName
            card.requestDocText.text = getString(R.string.service_request_doc_template, request.requestedDocumentType)
            card.requestStatusText.text = statusLabel(normalizedStatus)
            card.requestAssignedText.text = assignedLabel(request)
            card.requestContactText.text = contactLabel(request)
            card.requestUpdatedText.text = getString(R.string.service_request_updated_template, formatDateTime(request.updatedAt))
            card.requestLinkText.text = linkedDocumentLabel(item.linkedDocument)
            card.requestOpsText.text = linkedOperationalStatus(request, item.linkedDocument)
            applyStatusVisualState(card, normalizedStatus, request.requestCode == focusRequestCode)
            applyLinkBadgeVisualState(
                card = card,
                hasLinkedDocument = item.linkedDocument != null,
                normalizedStatus = normalizedStatus,
                isFocused = request.requestCode == focusRequestCode
            )
            card.root.setOnClickListener {
                startActivity(
                    Intent(this, ServiceRequestDetailActivity::class.java)
                        .putExtra(ServiceRequestDetailActivity.EXTRA_REQUEST_CODE, request.requestCode)
                )
            }
            card.root.isClickable = true
            card.root.isFocusable = true

            card.assignButton.setOnClickListener { assignRequest(request) }
            card.progressButton.setOnClickListener { moveToInProgress(request) }
            card.doneButton.setOnClickListener { markDone(request) }
            card.openDocumentButton.setOnClickListener { openDocumentForRequest(request) }
            card.openLinkedButton.setOnClickListener { openLinkedDocumentFromCard(item.linkedDocument) }
            card.requestLinkText.setOnClickListener { openLinkedDocumentFromCard(item.linkedDocument) }
            card.requestOpsText.setOnClickListener { openLinkedDocumentFromCard(item.linkedDocument) }
            card.whatsappButton.setOnClickListener { openWhatsapp(request) }

            if (technicianMode) {
                card.assignButton.visibility = View.GONE
                card.progressButton.visibility = if (ServiceRequestStatus.isAssigned(normalizedStatus)) View.VISIBLE else View.GONE
                card.doneButton.visibility = if (ServiceRequestStatus.isInProgress(normalizedStatus)) View.VISIBLE else View.GONE
            } else {
                card.assignButton.visibility = if (ServiceRequestStatus.isOpen(normalizedStatus)) View.VISIBLE else View.GONE
                card.progressButton.visibility = if (ServiceRequestStatus.isAssigned(normalizedStatus)) View.VISIBLE else View.GONE
                card.doneButton.visibility = if (ServiceRequestStatus.isInProgress(normalizedStatus)) View.VISIBLE else View.GONE
            }
            card.openDocumentButton.visibility = if (ServiceRequestStatus.isCompleted(normalizedStatus)) View.GONE else View.VISIBLE
            card.openLinkedButton.visibility = if (item.linkedDocument == null) View.GONE else View.VISIBLE

            binding.requestsContainer.addView(card.root)
        }
    }

    private fun renderSortModeLabel() {
        binding.sortLatestActionButton.text = if (sortByLatestAction) {
            getString(R.string.service_request_sort_latest_action)
        } else {
            getString(R.string.service_request_sort_updated)
        }
    }

    private fun updateFilterButtonVisuals() {
        binding.filterAllButton.isSelected = statusFilter == FILTER_ALL
        binding.filterOpenButton.isSelected = statusFilter == FILTER_OPEN
        binding.filterActiveButton.isSelected = statusFilter == FILTER_ACTIVE
    }

    private fun readStatusPreference(): String {
        val raw = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(ServiceRequestsPreferencePolicy.statusPreferenceKey(technicianMode), FILTER_ALL)
        return ServiceRequestsPreferencePolicy.sanitizeStatusFilter(raw)
    }

    private fun persistStatusPreference(value: String) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(
                ServiceRequestsPreferencePolicy.statusPreferenceKey(technicianMode),
                ServiceRequestsPreferencePolicy.sanitizeStatusFilter(value)
            )
            .apply()
    }

    private fun readContractorCnpjFilter(): String {
        val raw = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(ServiceRequestsPreferencePolicy.cnpjFilterPreferenceKey(technicianMode), "")
        return ServiceRequestsPreferencePolicy.normalizeCnpjFilter(raw)
    }

    private fun persistContractorCnpjFilter(value: String) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(
                ServiceRequestsPreferencePolicy.cnpjFilterPreferenceKey(technicianMode),
                ServiceRequestsPreferencePolicy.normalizeCnpjFilter(value)
            )
            .apply()
    }

    private fun openDocumentForRequest(request: ServiceRequestEntity) {
        startActivity(ServiceRequestDocumentLauncher.buildIntent(this, request))
    }

    private fun linkedDocumentLabel(document: DocumentEntity?): String {
        if (document == null) return getString(R.string.service_request_link_none)
        return getString(
            R.string.service_request_link_template,
            document.documentType,
            document.documentId
        )
    }

    private fun linkedOperationalStatus(request: ServiceRequestEntity, document: DocumentEntity?): String {
        val latestAction = ServiceRequestOperationalHistoryFormatter.latestActionSummary(request.notes)
        if (!latestAction.isNullOrBlank()) {
            return getString(R.string.service_request_ops_latest_template, latestAction)
        }
        if (document == null) return getString(R.string.service_request_ops_draft)
        return when {
            document.isSynced -> getString(R.string.service_request_ops_synced)
            document.signedAt != null && !document.pdfPath.isNullOrBlank() -> getString(R.string.service_request_ops_signed_pdf)
            document.signedAt != null -> getString(R.string.service_request_ops_signed)
            !document.pdfPath.isNullOrBlank() -> getString(R.string.service_request_ops_pdf)
            else -> getString(R.string.service_request_ops_draft)
        }
    }

    private fun openLinkedDocumentFromCard(document: DocumentEntity?) {
        if (document == null) {
            Toast.makeText(this, getString(R.string.service_request_open_linked_unavailable), Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(
            Intent(this, DocumentDetailActivity::class.java)
                .putExtra(DocumentDetailActivity.EXTRA_DOCUMENT_ID, document.documentId)
        )
    }

    private fun applyStatusVisualState(
        card: ItemServiceRequestCardBinding,
        normalizedStatus: String,
        isFocused: Boolean
    ) {
        val strokeColorRes = when {
            isFocused -> R.color.anda_gold
            ServiceRequestStatus.isCompleted(normalizedStatus) -> R.color.status_normal
            ServiceRequestStatus.isInProgress(normalizedStatus) -> R.color.anda_navy_medium
            ServiceRequestStatus.isAssigned(normalizedStatus) -> R.color.status_moderate
            else -> R.color.status_neutral
        }
        val strokeWidth = if (isFocused) 4 else 2
        val backgroundRes = when {
            ServiceRequestStatus.isCompleted(normalizedStatus) -> R.color.anda_green_light
            ServiceRequestStatus.isInProgress(normalizedStatus) -> R.color.anda_surface
            else -> R.color.anda_surface
        }

        card.root.strokeColor = ContextCompat.getColor(this, strokeColorRes)
        card.root.strokeWidth = strokeWidth
        card.root.setCardBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(this, backgroundRes)))
        card.requestStatusText.setTextColor(ContextCompat.getColor(this, strokeColorRes))
    }

    private fun applyLinkBadgeVisualState(
        card: ItemServiceRequestCardBinding,
        hasLinkedDocument: Boolean,
        normalizedStatus: String,
        isFocused: Boolean
    ) {
        val (textRes, textColorRes, backgroundColorRes) = when {
            isFocused -> Triple(
                if (hasLinkedDocument) R.string.service_request_link_badge_linked else R.string.service_request_link_badge_unlinked,
                R.color.anda_navy_dark,
                R.color.status_focus_bg
            )
            hasLinkedDocument && ServiceRequestStatus.isCompleted(normalizedStatus) -> Triple(
                R.string.service_request_link_badge_linked,
                R.color.status_normal,
                R.color.status_normal_bg
            )
            hasLinkedDocument -> Triple(
                R.string.service_request_link_badge_linked,
                R.color.anda_navy_medium,
                R.color.status_active_bg
            )
            else -> Triple(
                R.string.service_request_link_badge_unlinked,
                R.color.status_neutral,
                R.color.status_neutral_bg
            )
        }

        card.requestLinkBadge.text = getString(textRes)
        card.requestLinkBadge.setTextColor(ContextCompat.getColor(this, textColorRes))
        card.requestLinkBadge.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, backgroundColorRes))
    }

    private fun assignRequest(request: ServiceRequestEntity) {
        lifecycleScope.launch {
            val assignees = withContext(Dispatchers.IO) {
                db.employeeDao().listAssignableByCompany(request.contractorCnpj)
            }
            if (assignees.isEmpty()) {
                Toast.makeText(this@ServiceRequestsActivity, getString(R.string.service_request_assign_no_eligible), Toast.LENGTH_SHORT).show()
                return@launch
            }
            showAssigneePicker(request, assignees)
        }
    }

    private fun showAssigneePicker(request: ServiceRequestEntity, assignees: List<EmployeeEntity>) {
        val labels = assignees.map { assignee ->
            val type = if (assignee.collaboratorType == "freelancer") "freelancer" else "interno"
            "${assignee.name} ($type)"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.service_request_assign_picker_title))
            .setItems(labels) { _, index ->
                val selected = assignees[index]
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        db.serviceRequestDao().updateAssignment(
                            id = request.id,
                            status = ServiceRequestStatus.ASSIGNED,
                            assignedEmployeeCpf = selected.cpf,
                            assignedEmployeeName = selected.name
                        )
                    }
                    notifyAssignment(request, selected.name)
                    Toast.makeText(this@ServiceRequestsActivity, getString(R.string.service_request_assign_success, selected.name), Toast.LENGTH_SHORT).show()
                    loadRequests()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun moveToInProgress(request: ServiceRequestEntity) {
        lifecycleScope.launch {
            if (request.assignedEmployeeCpf.isBlank()) {
                Toast.makeText(this@ServiceRequestsActivity, getString(R.string.service_request_assign_first), Toast.LENGTH_SHORT).show()
                return@launch
            }
            withContext(Dispatchers.IO) { db.serviceRequestDao().updateStatus(request.id, ServiceRequestStatus.IN_PROGRESS) }
            focusRequestCode = request.requestCode
            loadRequests()
        }
    }

    private fun markDone(request: ServiceRequestEntity) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { db.serviceRequestDao().updateStatus(request.id, ServiceRequestStatus.COMPLETED) }
            focusRequestCode = request.requestCode
            loadRequests()
        }
    }

    private suspend fun autoAssignBestMatch(request: ServiceRequestEntity): String = withContext(Dispatchers.IO) {
        val assignees = db.employeeDao().listAssignableByCompany(request.contractorCnpj)
        if (assignees.isEmpty()) return@withContext ""

        val profiles = assignees.map { employee ->
            TechnicianProfile(
                cpf = employee.cpf,
                name = employee.name,
                certifications = inferCertifications(employee, request.requestedDocumentType),
                activeRequestCount = db.serviceRequestDao().countActiveByAssignee(employee.cpf),
                yearsExperience = inferExperience(employee)
            )
        }
        val bestCpf = assignmentEngine.findBestTechnician(request.requestedDocumentType, profiles) ?: return@withContext ""
        val selected = assignees.firstOrNull { it.cpf == bestCpf } ?: return@withContext ""

        db.serviceRequestDao().updateAssignment(
            id = request.id,
            status = ServiceRequestStatus.ASSIGNED,
            assignedEmployeeCpf = selected.cpf,
            assignedEmployeeName = selected.name
        )

        withContext(Dispatchers.Main) {
            notifyAssignment(request, selected.name)
        }
        selected.name
    }

    private fun inferCertifications(employee: EmployeeEntity, requestType: String): List<String> {
        val requestNormalized = requestType.trim().uppercase(Locale.ROOT)
        val roleText = (employee.role + " " + employee.notes).uppercase(Locale.ROOT)
        val certs = mutableSetOf<String>()

        val medicalDocs = setOf("ASO", "PCMSO", "PPP")
        val safetyDocs = setOf(
            "PGR", "APR", "CAT", "LTCAT", "AET", "LAUDO_NR10", "LAUDO_NR12", "LAUDO_NR20",
            "PCA", "PPR", "PT", "INSALUBRIDADE", "PERICULOSIDADE", "PLANO_ACAO", "PGRTR", "RESGATE", "INVENTARIO"
        )

        if (requestNormalized in medicalDocs && roleText.contains("MED")) {
            certs += requestNormalized
        }
        if (requestNormalized in safetyDocs && (
                roleText.contains("SEGUR") || roleText.contains("ENGENHE") || roleText.contains("TECN")
            )
        ) {
            certs += requestNormalized
        }
        if (roleText.contains(requestNormalized.replace("_", " ")) || roleText.contains(requestNormalized.replace("_", ""))) {
            certs += requestNormalized
        }
        if (certs.isEmpty()) {
            certs += "ALL"
        }
        return certs.toList()
    }

    private fun inferExperience(employee: EmployeeEntity): Int {
        val notes = employee.notes.lowercase(Locale.ROOT)
        return Regex("(\\d{1,2})\\s*ano")
            .find(notes)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: 0
    }

    private fun openWhatsapp(request: ServiceRequestEntity) {
        val number = request.contactWhatsapp.filter(Char::isDigit)
        if (number.isBlank()) {
            Toast.makeText(this, getString(R.string.service_request_whatsapp_missing), Toast.LENGTH_SHORT).show()
            return
        }

        val message = Uri.encode(getString(R.string.service_request_whatsapp_message_template, request.requestCode))
        val uri = Uri.parse("https://wa.me/$number?text=$message")

        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.service_request_whatsapp_not_available), Toast.LENGTH_SHORT).show()
        }
    }

    private fun notifyAssignment(request: ServiceRequestEntity, assigneeName: String) {
        val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return
        notificationService.notifyTechnicianAssignment(
            technicianCpf = "",
            technicianName = assigneeName,
            companyName = request.contractorName,
            requestType = request.requestedDocumentType,
            requestId = request.requestCode
        )
    }

    private fun consumeIntent(intent: Intent?) {
        focusRequestCode = intent?.getStringExtra(EXTRA_FOCUS_REQUEST_CODE).orEmpty().trim()
    }

    private fun clearForm() {
        binding.contractorNameInput.text?.clear()
        binding.contractorCnpjInput.text?.clear()
        binding.documentTypeInput.text?.clear()
        binding.contactWhatsappInput.text?.clear()
        binding.notesInput.text?.clear()
        binding.preferWhatsappCheckbox.isChecked = false
    }

    private fun statusLabel(status: String): String {
        return when (ServiceRequestStatus.normalize(status)) {
            ServiceRequestStatus.OPEN -> getString(R.string.service_request_status_open)
            ServiceRequestStatus.ASSIGNED -> getString(R.string.service_request_status_assigned)
            ServiceRequestStatus.IN_PROGRESS -> getString(R.string.service_request_status_in_progress)
            ServiceRequestStatus.COMPLETED -> getString(R.string.service_request_status_done)
            else -> status
        }
    }

    private fun assignedLabel(request: ServiceRequestEntity): String {
        return if (request.assignedEmployeeName.isBlank()) {
            getString(R.string.service_request_assignee_unassigned)
        } else {
            getString(R.string.service_request_assignee_template, request.assignedEmployeeName)
        }
    }

    private fun contactLabel(request: ServiceRequestEntity): String {
        return if (request.preferredContactChannel == "whatsapp") {
            if (request.contactWhatsapp.isBlank()) {
                getString(R.string.service_request_contact_whatsapp_missing)
            } else {
                getString(R.string.service_request_contact_whatsapp_template, request.contactWhatsapp)
            }
        } else {
            getString(R.string.service_request_contact_app)
        }
    }

    private fun formatDateTime(epochMs: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))
        return sdf.format(Date(epochMs))
    }

    private fun buildRequestCode(contractorName: String): String {
        val prefix = contractorName.trim().uppercase(Locale.ROOT).take(3).padEnd(3, 'X')
        val serial = (1000 + Random.nextInt(9000)).toString()
        return "$prefix-${System.currentTimeMillis().toString().takeLast(6)}-$serial"
    }

    companion object {
        const val EXTRA_TECHNICIAN_MODE = "extra_technician_mode"
        const val EXTRA_FOCUS_REQUEST_CODE = "extra_focus_request_code"
        private const val FILTER_ALL = "all"
        private const val FILTER_OPEN = "open"
        private const val FILTER_ACTIVE = "active"
        private const val PREFS_NAME = "service_requests_prefs"
    }
}
