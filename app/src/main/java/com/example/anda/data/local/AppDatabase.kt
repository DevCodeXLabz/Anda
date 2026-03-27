package com.example.anda.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.anda.data.analytics.ErrorLogDao
import com.example.anda.data.analytics.ErrorLogEntity
import com.example.anda.data.analytics.ProductionMetricEntity
import com.example.anda.data.analytics.ProductionMetricsDao
import com.example.anda.data.analytics.RevenueMetricEntity
import com.example.anda.data.analytics.RevenueMetricsDao
import com.example.anda.data.local.dao.CompanyDao
import com.example.anda.data.local.migrations.Migrations
import com.example.anda.data.local.dao.DocumentDao
import com.example.anda.data.local.dao.DocumentVersionDao
import com.example.anda.data.local.dao.EmployeeDao
import com.example.anda.data.local.dao.ServiceRequestDao
import com.example.anda.data.local.dao.SyncAttemptLogDao
import com.example.anda.data.local.dao.SyncQueueDao
import com.example.anda.data.local.dao.TemplateDao
import com.example.anda.data.local.entity.CompanyEntity
import com.example.anda.data.local.entity.DocumentEntity
import com.example.anda.data.local.entity.DocumentVersionEntity
import com.example.anda.data.local.entity.EmployeeEntity
import com.example.anda.data.local.entity.ServiceRequestEntity
import com.example.anda.data.local.entity.SyncAttemptLogEntity
import com.example.anda.data.local.entity.SyncQueueEntity
import com.example.anda.data.local.entity.TemplateEntity

@Database(
    entities = [
        CompanyEntity::class,
        DocumentEntity::class,
        SyncQueueEntity::class,
        SyncAttemptLogEntity::class,
        EmployeeEntity::class,
        ServiceRequestEntity::class,
        TemplateEntity::class,
        DocumentVersionEntity::class,
        ProductionMetricEntity::class,
        RevenueMetricEntity::class,
        ErrorLogEntity::class
    ],
    version = 14,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun companyDao(): CompanyDao
    abstract fun documentDao(): DocumentDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun syncAttemptLogDao(): SyncAttemptLogDao
    abstract fun employeeDao(): EmployeeDao
    abstract fun serviceRequestDao(): ServiceRequestDao
    abstract fun templateDao(): TemplateDao
    abstract fun documentVersionDao(): DocumentVersionDao
    abstract fun productionMetricsDao(): ProductionMetricsDao
    abstract fun revenueMetricsDao(): RevenueMetricsDao
    abstract fun errorLogDao(): ErrorLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "anda_app.db"
                ).addMigrations(
                    Migrations.MIGRATION_11_12,
                    Migrations.MIGRATION_12_13,
                    Migrations.MIGRATION_13_14
                )
                    .fallbackToDestructiveMigration(dropAllTables = true) // safety net for pre-v11 installs
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
