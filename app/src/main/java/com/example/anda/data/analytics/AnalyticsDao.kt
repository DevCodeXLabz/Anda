package com.example.anda.data.analytics

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Insert
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Daily production metrics for analytics dashboard.
 * Tracks how many documents were created per day per technician.
 */
@Entity(tableName = "production_metrics")
data class ProductionMetricEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,                    // YYYY-MM-DD
    val technicianCpf: String,           // Which technician
    val companyId: String,               // Which company/clinic
    val documentsCreated: Int = 0,       // Count of docs created
    val documentsSignedCount: Int = 0,   // Count actually signed
    val totalTimeMinutes: Int = 0,       // Time spent on documents (minutes)
    val avgTimePerDocMinutes: Int = 0    // Time per document
)

/**
 * Revenue tracking for company/clinic (when billing enabled).
 */
@Entity(tableName = "revenue_metrics")
data class RevenueMetricEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val companyId: String,
    val documentsCount: Int = 0,
    val totalRevenue: Double = 0.0,
    val avgRevenuePerDoc: Double = 0.0,
    val techniciansActive: Int = 0
)

/**
 * Track document generation errors for troubleshooting.
 */
@Entity(tableName = "error_logs")
data class ErrorLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val documentType: String,
    val errorMessage: String,
    val technicianCpf: String,
    val stackTrace: String = ""
)

@Dao
interface ProductionMetricsDao {
    
    @Insert
    suspend fun insert(metric: ProductionMetricEntity)
    
    @Update
    suspend fun update(metric: ProductionMetricEntity)
    
    @Query("SELECT * FROM production_metrics WHERE date >= :startDate AND date <= :endDate AND companyId = :companyId ORDER BY date DESC")
    fun getMetricsByDateRange(startDate: String, endDate: String, companyId: String): Flow<List<ProductionMetricEntity>>
    
    @Query("SELECT * FROM production_metrics WHERE technicianCpf = :cpf AND date >= :startDate ORDER BY date DESC")
    fun getTechnicianMetrics(cpf: String, startDate: String): Flow<List<ProductionMetricEntity>>
    
    @Query("SELECT SUM(documentsCreated) FROM production_metrics WHERE companyId = :companyId AND date >= :startDate")
    suspend fun getTotalDocumentsCreated(companyId: String, startDate: String): Int?
    
    @Query("SELECT AVG(avgTimePerDocMinutes) FROM production_metrics WHERE companyId = :companyId AND date >= :startDate")
    suspend fun getAvgTimePerDoc(companyId: String, startDate: String): Double?
}

@Dao
interface RevenueMetricsDao {
    
    @Insert
    suspend fun insert(metric: RevenueMetricEntity)
    
    @Query("SELECT * FROM revenue_metrics WHERE date >= :startDate AND date <= :endDate AND companyId = :companyId ORDER BY date DESC")
    fun getRevenueByDateRange(startDate: String, endDate: String, companyId: String): Flow<List<RevenueMetricEntity>>
    
    @Query("SELECT SUM(totalRevenue) FROM revenue_metrics WHERE companyId = :companyId AND date >= :startDate")
    suspend fun getTotalRevenue(companyId: String, startDate: String): Double?
}

@Dao
interface ErrorLogDao {
    
    @Insert
    suspend fun logError(error: ErrorLogEntity)
    
    @Query("SELECT * FROM error_logs WHERE documentType = :docType ORDER BY timestamp DESC LIMIT 20")
    suspend fun getRecentErrors(docType: String): List<ErrorLogEntity>
}

