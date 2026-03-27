package com.example.anda.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.anda.data.local.migrations.Migrations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented Room database tests.
 *
 * These tests verify that:
 *  - The database can be created at the target schema version.
 *  - The migration metadata is correct.
 *  - DAOs are accessible and functional.
 *
 * Full schema migration validation (applying v11 → v12 migrations) requires
 * `androidx.room:room-testing` and `MigrationTestHelper`, which is a future
 * addition to the androidTest suite.
 *
 * Run with:
 *   ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunner="androidx.test.runner.AndroidJUnitRunner"
 *
 * or target this test:
 *   ./gradlew :app:connectedAndroidTest --tests "*MigrationAndroidTest*"
 */
@RunWith(AndroidJUnit4::class)
class MigrationAndroidTest {

    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Create an in-memory database (not persisted) for testing
        db = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries()
            .addMigrations(
                Migrations.MIGRATION_11_12,
                Migrations.MIGRATION_12_13,
                Migrations.MIGRATION_13_14
            )
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    // ── Schema version ───────────────────────────────────────────────────

    @Test
    fun database_version_matches_expected() {
        val expectedVersion = AppDatabase::class.java
            .getAnnotation(Database::class.java)
            ?.version
            ?: 14
        val actualVersion = db.openHelper.readableDatabase.version
        assertEquals(
            "AppDatabase version should match schema version",
            expectedVersion,
            actualVersion
        )
    }

    // ── DAO accessibility ────────────────────────────────────────────────

    @Test
    fun documentDao_isAccessible() {
        val dao = db.documentDao()
        assertNotNull("DocumentDao must be accessible after database creation", dao)
    }

    @Test
    fun syncQueueDao_isAccessible() {
        val dao = db.syncQueueDao()
        assertNotNull("SyncQueueDao must be accessible", dao)
    }

    @Test
    fun syncAttemptLogDao_isAccessible() {
        val dao = db.syncAttemptLogDao()
        assertNotNull("SyncAttemptLogDao must be accessible", dao)
    }

    @Test
    fun documentVersionDao_isAccessible() {
        val dao = db.documentVersionDao()
        assertNotNull("DocumentVersionDao must be accessible", dao)
    }

    @Test
    fun companyDao_isAccessible() {
        val dao = db.companyDao()
        assertNotNull("CompanyDao must be accessible", dao)
    }

    @Test
    fun employeeDao_isAccessible() {
        val dao = db.employeeDao()
        assertNotNull("EmployeeDao must be accessible", dao)
    }

    @Test
    fun serviceRequestDao_isAccessible() {
        val dao = db.serviceRequestDao()
        assertNotNull("ServiceRequestDao must be accessible", dao)
    }

    @Test
    fun templateDao_isAccessible() {
        val dao = db.templateDao()
        assertNotNull("TemplateDao must be accessible", dao)
    }

    // ── Migration metadata ───────────────────────────────────────────────

    @Test
    fun migration_11_to_12_hasCorrectVersions() {
        val migration = Migrations.MIGRATION_11_12
        assertEquals(
            "Migration startVersion should be 11",
            11,
            migration.startVersion
        )
        assertEquals(
            "Migration endVersion should be 12",
            12,
            migration.endVersion
        )
    }

    @Test
    fun migration_11_to_12_versionProgression_isForward() {
        val migration = Migrations.MIGRATION_11_12
        val isForward = migration.endVersion > migration.startVersion
        assertEquals("Migration must be forward (endVersion > startVersion)", true, isForward)
    }

    @Test
    fun migration_12_to_13_hasCorrectVersions() {
        val migration = Migrations.MIGRATION_12_13
        assertEquals("Migration startVersion should be 12", 12, migration.startVersion)
        assertEquals("Migration endVersion should be 13", 13, migration.endVersion)
    }

    @Test
    fun migration_13_to_14_hasCorrectVersions() {
        val migration = Migrations.MIGRATION_13_14
        assertEquals("Migration startVersion should be 13", 13, migration.startVersion)
        assertEquals("Migration endVersion should be 14", 14, migration.endVersion)
    }
}

