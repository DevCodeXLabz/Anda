package com.example.anda.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database migration definitions for the ANDA app.
 *
 * Version history:
 *   1–10  : initial schema iterations — no explicit migrations; fallbackToDestructiveMigration
 *           was used as a safety net.
 *   11    : added DocumentVersionEntity, SyncAttemptLogEntity; added signatureB64,
 *           publicKeyFingerprint, and signatureAlgorithm nullable columns to the
 *           `documents` table.
 *   12    : encryption-activation checkpoint (LGPD hardening, Sprint 2).
 *           No schema changes — encryption is handled at the application layer
 *           (DocumentLocalRepository.encryptSensitiveFields). This hop formalises the
 *           explicit migration chain and replaces fallbackToDestructiveMigration for
 *           users upgrading from v11.
 *   13    : adds enterprise analytics tables.
 *   14    : adds explicit service-request linkage on documents via sourceRequestCode.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * ADDING NEW COLUMNS OR TABLES?
 *   1. Bump `version` in AppDatabase.
 *   2. Add a new Migration(N, N+1) object below.
 *   3. Register it in AppDatabase.getInstance() via .addMigrations(…).
 *   4. Write a matching test in MigrationTest (androidTest).
 * ─────────────────────────────────────────────────────────────────────────────
 */
object Migrations {

    /**
     * Migration 11 → 12.
     *
     * Schema-identical hop: no DDL changes required.
     *
     * This migration exists to:
     *   • Replace [androidx.room.RoomDatabase.Builder.fallbackToDestructiveMigration]
     *     for the specific 11→12 upgrade path, preserving existing user data.
     *   • Document the encryption-activation checkpoint (LGPD / Lei 13.709/2018).
     *   • Establish the baseline for all future explicit migrations.
     */
    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Schema-identical hop — no DDL required.
            // Encryption operates at the application layer; no column changes needed.
        }
    }

    /**
     * Migration 12 → 13.
     *
     * Adds three new tables for enterprise analytics & reporting:
     *   • production_metrics: Track document creation per technician per day
     *   • revenue_metrics: Track revenue per company per day (when billing enabled)
     *   • error_logs: Track errors for troubleshooting and support
     */
    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Create production_metrics table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS production_metrics (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    date TEXT NOT NULL,
                    technicianCpf TEXT NOT NULL,
                    companyId TEXT NOT NULL,
                    documentsCreated INTEGER NOT NULL,
                    documentsSignedCount INTEGER NOT NULL,
                    totalTimeMinutes INTEGER NOT NULL,
                    avgTimePerDocMinutes INTEGER NOT NULL
                )
            """.trimIndent())

            // Create revenue_metrics table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS revenue_metrics (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    date TEXT NOT NULL,
                    companyId TEXT NOT NULL,
                    documentsCount INTEGER NOT NULL,
                    totalRevenue REAL NOT NULL,
                    avgRevenuePerDoc REAL NOT NULL,
                    techniciansActive INTEGER NOT NULL
                )
            """.trimIndent())

            // Create error_logs table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS error_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    documentType TEXT NOT NULL,
                    errorMessage TEXT NOT NULL,
                    technicianCpf TEXT NOT NULL,
                    stackTrace TEXT NOT NULL
                )
            """.trimIndent())
        }
    }

    /**
     * Migration 13 → 14.
     *
     * Adds explicit linkage between generated documents and originating service requests,
     * avoiding heuristic-only association by contractor CNPJ + document type.
     */
    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE documents ADD COLUMN sourceRequestCode TEXT")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_documents_sourceRequestCode ON documents(sourceRequestCode)"
            )
        }
    }
}

