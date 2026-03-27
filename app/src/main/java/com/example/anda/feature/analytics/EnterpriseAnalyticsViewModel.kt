package com.example.anda.feature.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.anda.data.analytics.EnterpriseAnalyticsService
import com.example.anda.data.analytics.ProductivitySummary
import com.example.anda.data.analytics.RevenueSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Enterprise Analytics Dashboard
 * Manages data fetching and UI state
 */
class EnterpriseAnalyticsViewModel(
    private val analyticsService: EnterpriseAnalyticsService? = null
) : ViewModel() {

    private val _productivitySummary = MutableStateFlow<ProductivitySummary?>(null)
    val productivitySummary: StateFlow<ProductivitySummary?> = _productivitySummary

    private val _revenueSummary = MutableStateFlow<RevenueSummary?>(null)
    val revenueSummary: StateFlow<RevenueSummary?> = _revenueSummary

    private var currentDaysBack = 30
    private var currentCompanyId = "" // Set from profile

    init {
        loadInitialMetrics()
    }

    private fun loadInitialMetrics() {
        viewModelScope.launch {
            refreshMetrics()
        }
    }

    fun setDateRange(daysBack: Int) {
        currentDaysBack = daysBack
        refreshMetrics()
    }

    fun refreshMetrics() {
        viewModelScope.launch {
            try {
                // Fetch productivity summary
                val productivity = analyticsService?.getProductivitySummary(
                    companyId = currentCompanyId,
                    daysBack = currentDaysBack
                ) ?: ProductivitySummary(
                    totalDocumentsCreated = 0,
                    avgTimePerDocMinutes = 0,
                    daysAnalyzed = currentDaysBack
                )
                _productivitySummary.emit(productivity)

                // Fetch revenue summary  
                val revenue = analyticsService?.getRevenueSummary(
                    companyId = currentCompanyId,
                    daysBack = currentDaysBack
                ) ?: RevenueSummary(
                    totalRevenue = 0.0,
                    daysAnalyzed = currentDaysBack
                )
                _revenueSummary.emit(revenue)
            } catch (e: Exception) {
                // Handle error
                android.util.Log.e("Analytics", "Error loading metrics", e)
            }
        }
    }
}

