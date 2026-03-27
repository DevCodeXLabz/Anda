package com.example.anda.data.analytics

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Enterprise Analytics Service
 * 
 * Provides real-time insights for clinic/company dashboards:
 * - Team productivity (docs per tech)
 * - Revenue tracking (if billing enabled)
 * - Error monitoring
 * - Trend analysis
 */
class EnterpriseAnalyticsService(
    private val productionDao: ProductionMetricsDao,
    private val revenueDao: RevenueMetricsDao,
    private val errorDao: ErrorLogDao
) {
    
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    
    /**
     * Record when a technician creates a document.
     */
    suspend fun recordDocumentCreation(
        technicianCpf: String,
        companyId: String,
        documentType: String,
        timeSpentMinutes: Int
    ) = withContext(Dispatchers.IO) {
        val today = LocalDate.now().format(dateFormatter)
        
        // Fetch or create today's metric
        val metric = ProductionMetricEntity(
            date = today,
            technicianCpf = technicianCpf,
            companyId = companyId,
            documentsCreated = 1,
            totalTimeMinutes = timeSpentMinutes,
            avgTimePerDocMinutes = timeSpentMinutes
        )
        
        productionDao.insert(metric)
    }
    
    /**
     * Get productivity summary for a date range.
     * Returns: total docs, avg time per doc, top performers
     */
    suspend fun getProductivitySummary(
        companyId: String,
        daysBack: Int = 30
    ): ProductivitySummary = withContext(Dispatchers.IO) {
        val startDate = LocalDate.now().minusDays(daysBack.toLong()).format(dateFormatter)
        
        val totalDocs = productionDao.getTotalDocumentsCreated(companyId, startDate) ?: 0
        val avgTime = productionDao.getAvgTimePerDoc(companyId, startDate) ?: 0.0
        
        ProductivitySummary(
            totalDocumentsCreated = totalDocs,
            avgTimePerDocMinutes = avgTime.roundToInt(),
            daysAnalyzed = daysBack
        )
    }
    
    /**
     * Get revenue summary (if billing enabled).
     */
    suspend fun getRevenueSummary(
        companyId: String,
        daysBack: Int = 30
    ): RevenueSummary = withContext(Dispatchers.IO) {
        val startDate = LocalDate.now().minusDays(daysBack.toLong()).format(dateFormatter)
        
        val totalRevenue = revenueDao.getTotalRevenue(companyId, startDate) ?: 0.0
        
        RevenueSummary(
            totalRevenue = totalRevenue,
            daysAnalyzed = daysBack
        )
    }
    
    /**
     * Log an error for troubleshooting.
     */
    suspend fun logError(
        documentType: String,
        errorMessage: String,
        technicianCpf: String,
        stackTrace: String = ""
    ) = withContext(Dispatchers.IO) {
        val error = ErrorLogEntity(
            timestamp = System.currentTimeMillis(),
            documentType = documentType,
            errorMessage = errorMessage,
            technicianCpf = technicianCpf,
            stackTrace = stackTrace
        )
        errorDao.logError(error)
    }
    
    /**
     * Get recent errors for a document type.
     * Used in admin/support dashboard to see what's breaking.
     */
    suspend fun getRecentErrors(documentType: String): List<ErrorLogEntity> = withContext(Dispatchers.IO) {
        errorDao.getRecentErrors(documentType)
    }
}

data class ProductivitySummary(
    val totalDocumentsCreated: Int,
    val avgTimePerDocMinutes: Int,
    val daysAnalyzed: Int
)

data class RevenueSummary(
    val totalRevenue: Double,
    val daysAnalyzed: Int
)

