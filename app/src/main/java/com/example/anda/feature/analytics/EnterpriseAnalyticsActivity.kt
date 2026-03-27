package com.example.anda.feature.analytics

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import com.example.anda.R
import com.example.anda.databinding.ActivityEnterpriseAnalyticsBinding
import com.example.anda.core.profile.ProfileManager
import com.example.anda.core.profile.UserProfile
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Enterprise Analytics Dashboard
 *
 * Displays real-time metrics for clinic/company managers:
 * - Team productivity (documents created per day/tech)
 * - Revenue tracking (if billing enabled)
 * - Error monitoring
 * - Trend analysis
 *
 * Access: Only available to company/clinic profiles
 */
class EnterpriseAnalyticsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEnterpriseAnalyticsBinding
    private val viewModel: EnterpriseAnalyticsViewModel by viewModels()
    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check if user is authorized (must be clinic/company profile)
        val profile = ProfileManager.getProfile(this)
        if (profile == null || !isAuthorizedProfile()) {
            Toast.makeText(this, "Acesso restrito a clínicas e empresas", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding = ActivityEnterpriseAnalyticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        observeMetrics()
    }

    private fun setupUI() {
        binding.apply {
            toolbarTitle.text = "Dashboard de Produtividade"
            backButton.setOnClickListener { finish() }

            // Date range selector
            dateRangeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                    val daysBack = when (pos) {
                        0 -> 7
                        1 -> 30
                        2 -> 90
                        else -> 7
                    }
                    viewModel.setDateRange(daysBack)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }

            // Refresh button
            refreshButton.setOnClickListener {
                viewModel.refreshMetrics()
                Toast.makeText(this@EnterpriseAnalyticsActivity, "Atualizando...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeMetrics() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe productivity summary
                viewModel.productivitySummary.collect { summary ->
                    if (summary != null) {
                        renderProductivityCard(summary)
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe revenue summary
                viewModel.revenueSummary.collect { summary ->
                    if (summary != null) {
                        renderRevenueCard(summary)
                    }
                }
            }
        }
    }

    private fun renderProductivityCard(summary: com.example.anda.data.analytics.ProductivitySummary) {
        binding.apply {
            docsCreatedValue.text = summary.totalDocumentsCreated.toString()
            avgTimeValue.text = "${summary.avgTimePerDocMinutes} min"
            periodValue.text = "Últimos ${summary.daysAnalyzed} dias"
            
            productivityContainer.visibility = android.view.View.VISIBLE
        }
    }

    private fun renderRevenueCard(summary: com.example.anda.data.analytics.RevenueSummary) {
        binding.apply {
            revenueValue.text = "R$ ${String.format("%.2f", summary.totalRevenue)}"
            revenueContainer.visibility = android.view.View.VISIBLE
        }
    }

    private fun isAuthorizedProfile(): Boolean {
        val profile = ProfileManager.getProfile(this)
        return profile == UserProfile.CLINIC ||
            profile == UserProfile.COMPANY ||
            profile == UserProfile.PRESTADORA
    }
}

