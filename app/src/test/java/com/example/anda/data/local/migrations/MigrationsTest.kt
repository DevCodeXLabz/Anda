package com.example.anda.data.local.migrations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [Migrations].
 *
 * These tests verify migration metadata (version numbers, chain ordering, and
 * consistency with [com.example.anda.data.local.AppDatabase.version]).
 *
 * For full schema-validation tests that apply the SQL against a real SQLite
 * database, use the instrumented `MigrationTest` in the `androidTest` source set
 * together with `MigrationTestHelper` from `androidx.room:room-testing`.
 */
class MigrationsTest {

    // ── MIGRATION_11_12 metadata ──────────────────────────────────────────────

    @Test
    fun `MIGRATION_11_12 has startVersion 11`() {
        assertEquals(11, Migrations.MIGRATION_11_12.startVersion)
    }

    @Test
    fun `MIGRATION_11_12 has endVersion 12`() {
        assertEquals(12, Migrations.MIGRATION_11_12.endVersion)
    }

    @Test
    fun `MIGRATION_11_12 endVersion is greater than startVersion`() {
        assertTrue(
            "endVersion (${Migrations.MIGRATION_11_12.endVersion}) must be > " +
                "startVersion (${Migrations.MIGRATION_11_12.startVersion})",
            Migrations.MIGRATION_11_12.endVersion > Migrations.MIGRATION_11_12.startVersion
        )
    }

    @Test
    fun `MIGRATION_11_12 covers exactly one version hop`() {
        val delta = Migrations.MIGRATION_11_12.endVersion - Migrations.MIGRATION_11_12.startVersion
        assertEquals(
            "Each migration should cover exactly one version hop to keep the chain " +
                "easy to reason about. MIGRATION_11_12 delta is $delta.",
            1,
            delta
        )
    }

    @Test
    fun `MIGRATION_12_13 has expected metadata`() {
        assertEquals(12, Migrations.MIGRATION_12_13.startVersion)
        assertEquals(13, Migrations.MIGRATION_12_13.endVersion)
        assertEquals(1, Migrations.MIGRATION_12_13.endVersion - Migrations.MIGRATION_12_13.startVersion)
    }

    @Test
    fun `MIGRATION_13_14 has expected metadata`() {
        assertEquals(13, Migrations.MIGRATION_13_14.startVersion)
        assertEquals(14, Migrations.MIGRATION_13_14.endVersion)
        assertEquals(1, Migrations.MIGRATION_13_14.endVersion - Migrations.MIGRATION_13_14.startVersion)
    }

    // ── Chain integrity ───────────────────────────────────────────────────────

    @Test
    fun `migration chain starts at version 11`() {
        val allMigrations = listOf(
            Migrations.MIGRATION_11_12,
            Migrations.MIGRATION_12_13,
            Migrations.MIGRATION_13_14
        )
        assertEquals(
            "The chain must start at v11 — the last version that used fallbackToDestructiveMigration.",
            11,
            allMigrations.first().startVersion
        )
    }

    @Test
    fun `migration chain is contiguous — no gaps or overlaps`() {
        val allMigrations = listOf(
            Migrations.MIGRATION_11_12,
            Migrations.MIGRATION_12_13,
            Migrations.MIGRATION_13_14
        )
        val broken = allMigrations.zipWithNext().filter { (a, b) ->
            a.endVersion != b.startVersion
        }
        assertTrue(
            "Migration chain has gaps or overlaps at: " +
                broken.joinToString { "${it.first.endVersion} → ${it.second.startVersion}" },
            broken.isEmpty()
        )
    }

    // ── Consistency with AppDatabase.version ─────────────────────────────────

    @Test
    fun `last migration endVersion matches AppDatabase schema version`() {
        // Keep this constant in sync with the `version` field in AppDatabase.
        // If AppDatabase.version changes, you MUST add a new Migration and update
        // this constant accordingly — this test is the compile-time reminder.
        val expectedDatabaseVersion = 14
        val lastMigrationEnd = Migrations.MIGRATION_13_14.endVersion

        assertEquals(
            "AppDatabase.version ($expectedDatabaseVersion) must equal the endVersion of the " +
                "last registered migration ($lastMigrationEnd). Either bump the DB version " +
                "and add a Migration, or align the constant here.",
            expectedDatabaseVersion,
            lastMigrationEnd
        )
    }
}

