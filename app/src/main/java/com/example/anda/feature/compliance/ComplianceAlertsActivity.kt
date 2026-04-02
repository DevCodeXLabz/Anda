package com.example.anda.feature.compliance

import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.example.anda.R
import com.example.anda.data.sync.ComplianceAlertScheduler
import com.example.anda.data.repository.ComplianceAlertRepository
import com.example.anda.data.repository.ComplianceAlert
import com.example.anda.feature.documents.DocumentDetailActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ComplianceAlertsActivity : AppCompatActivity() {

    private lateinit var summary: TextView
    private lateinit var alertsAdapter: ComplianceAlertsAdapter
    private lateinit var alertsRecyclerView: RecyclerView
    private lateinit var emptyState: TextView
    private val currentAlerts = mutableListOf<ComplianceAlert>()
    private var activeFilter = FILTER_ALL

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                runComplianceCheckNow()
            } else {
                // If user permanently denied (Don't ask again), guide to app settings
                val permanentlyDenied = !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

                if (permanentlyDenied) {
                    AlertDialog.Builder(this)
                        .setTitle(R.string.compliance_alerts_title)
                        .setMessage(R.string.compliance_alerts_notification_permission_denied)
                        .setPositiveButton(R.string.compliance_alerts_open_settings) { _, _ ->
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", packageName, null)
                            }
                            startActivity(intent)
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                    // show persistent open settings button in UI
                    runOnUiThread {
                        findViewById<Button>(R.id.open_settings_button).visibility = android.view.View.VISIBLE
                    }
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.compliance_alerts_notification_permission_denied),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_compliance_alerts)
        title = getString(R.string.compliance_alerts_title)

        summary = findViewById(R.id.compliance_summary)
        summary.text = getString(R.string.compliance_alerts_empty)
        emptyState = findViewById(R.id.alerts_empty_state)

        val openSettingsBtn = findViewById<Button>(R.id.open_settings_button)
        openSettingsBtn.visibility = android.view.View.GONE
        openSettingsBtn.setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
        }

        findViewById<Button>(R.id.refresh_button).setOnClickListener { requestNotificationPermissionOrRunCheck() }

        // RecyclerView setup
        alertsRecyclerView = findViewById(R.id.alerts_recycler_view)
        alertsAdapter = ComplianceAlertsAdapter { alert ->
            val docId = alert.documentId
            if (!docId.isNullOrBlank()) {
                startActivity(
                    Intent(this, DocumentDetailActivity::class.java)
                        .putExtra(DocumentDetailActivity.EXTRA_DOCUMENT_ID, docId)
                )
            }
        }
        alertsRecyclerView.layoutManager = LinearLayoutManager(this)
        alertsRecyclerView.adapter = alertsAdapter

        // Filter chips
        val chipGroup = findViewById<ChipGroup>(R.id.filter_chip_group)
        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            activeFilter = when (checkedIds.firstOrNull()) {
                R.id.chip_filter_expired -> FILTER_EXPIRED
                R.id.chip_filter_critical -> FILTER_CRITICAL
                R.id.chip_filter_warning -> FILTER_WARNING
                else -> FILTER_ALL
            }
            applyFilter()
        }

        // initial load
        loadAlerts()
    }

    private fun loadAlerts() {
        // show progress
        findViewById<android.view.View>(R.id.alerts_progress).visibility = android.view.View.VISIBLE
        summary.text = getString(R.string.compliance_alerts_loading)
        lifecycleScope.launch {
            val alerts = withContext(Dispatchers.IO) { ComplianceAlertRepository.loadActiveAlerts() }
            currentAlerts.clear()
            currentAlerts.addAll(alerts)
            updateChipCounts(alerts)
            applyFilter()
            if (alerts.isEmpty()) {
                summary.text = getString(R.string.compliance_alerts_all_good)
            } else {
                summary.text = getString(
                    R.string.compliance_alerts_summary_template,
                    alerts.size,
                    alerts.count { it.severity == "EXPIRED" },
                    alerts.count { it.severity == "WARNING" || it.severity == "CRITICAL" },
                    0,
                    0
                )
            }
            findViewById<android.view.View>(R.id.alerts_progress).visibility = android.view.View.GONE
        }
    }

    private fun applyFilter() {
        val filtered = when (activeFilter) {
            FILTER_EXPIRED -> currentAlerts.filter { it.severity == "EXPIRED" }
            FILTER_CRITICAL -> currentAlerts.filter { it.severity == "CRITICAL" }
            FILTER_WARNING -> currentAlerts.filter { it.severity == "WARNING" }
            else -> currentAlerts.toList()
        }
        alertsAdapter.submitList(filtered)
        val hasItems = filtered.isNotEmpty()
        alertsRecyclerView.visibility = if (hasItems) android.view.View.VISIBLE else android.view.View.GONE
        emptyState.visibility = if (!hasItems && currentAlerts.isNotEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun updateChipCounts(alerts: List<ComplianceAlert>) {
        val countAll = alerts.size
        val countExpired = alerts.count { it.severity == "EXPIRED" }
        val countCritical = alerts.count { it.severity == "CRITICAL" }
        val countWarning = alerts.count { it.severity == "WARNING" }

        fun chipLabel(baseRes: Int, count: Int): String {
            val base = getString(baseRes)
            return if (count > 0) getString(R.string.compliance_filter_count_template, base, count) else base
        }

        findViewById<Chip>(R.id.chip_filter_all).text = chipLabel(R.string.compliance_filter_all, countAll)
        findViewById<Chip>(R.id.chip_filter_expired).text = chipLabel(R.string.compliance_filter_expired, countExpired)
        findViewById<Chip>(R.id.chip_filter_critical).text = chipLabel(R.string.compliance_filter_critical, countCritical)
        findViewById<Chip>(R.id.chip_filter_warning).text = chipLabel(R.string.compliance_filter_warning, countWarning)
    }

    private fun requestNotificationPermissionOrRunCheck() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            runComplianceCheckNow()
            return
        }

        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            runComplianceCheckNow()
            return
        }

        if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.compliance_alerts_title)
                .setMessage(R.string.compliance_alerts_notification_permission_rationale)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } else {
            Toast.makeText(
                this,
                getString(R.string.compliance_alerts_notification_permission_required),
                Toast.LENGTH_SHORT
            ).show()
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun runComplianceCheckNow() {
        summary.text = getString(R.string.compliance_alerts_loading)
        ComplianceAlertScheduler.scheduleOneTimeCheck(this)
        Toast.makeText(this, getString(R.string.compliance_alerts_check_requested), Toast.LENGTH_SHORT).show()
        alertsRecyclerView.postDelayed({
            loadAlerts()
        }, 800)
    }

    companion object {
        private const val FILTER_ALL = "ALL"
        private const val FILTER_EXPIRED = "EXPIRED"
        private const val FILTER_CRITICAL = "CRITICAL"
        private const val FILTER_WARNING = "WARNING"
    }
}
