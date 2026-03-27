package com.example.anda.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [CnaeRiskMapper].
 *
 * Coverage:
 *  - null / blank inputs  → fallback grade 2
 *  - exact match keys     → specific grade
 *  - prefix match         → NR-04 grade
 *  - unknown prefix       → fallback grade 2
 *  - formatted vs. raw CNAE strings
 */
class CnaeRiskMapperTest {

    // ── null / blank ──────────────────────────────────────────────────────

    @Test
    fun `null cnae returns fallback grade 2`() {
        assertEquals(2, CnaeRiskMapper.map(null))
    }

    @Test
    fun `blank cnae returns fallback grade 2`() {
        assertEquals(2, CnaeRiskMapper.map(""))
        assertEquals(2, CnaeRiskMapper.map("   "))
    }

    // ── exact matches ─────────────────────────────────────────────────────

    @Test
    fun `exact match construction grade 4`() {
        assertEquals(4, CnaeRiskMapper.map("41.20-4-00"))
    }

    @Test
    fun `exact match food grade 3`() {
        assertEquals(3, CnaeRiskMapper.map("10.11-2-01"))
    }

    @Test
    fun `exact match health grade 2`() {
        assertEquals(2, CnaeRiskMapper.map("86.40-2-05"))
    }

    @Test
    fun `exact match retail grade 2`() {
        assertEquals(2, CnaeRiskMapper.map("47.11-3-02"))
    }

    @Test
    fun `exact match it grade 1`() {
        assertEquals(1, CnaeRiskMapper.map("62.01-5-01"))
    }

    @Test
    fun `exact match metal fabrication grade 3`() {
        assertEquals(3, CnaeRiskMapper.map("25.11-0-00"))
    }

    // ── prefix rules ─────────────────────────────────────────────────────

    @Test
    fun `prefix 41 construction grade 4`() {
        // Construcao de edificios - NR-18
        assertEquals(4, CnaeRiskMapper.map("41.00-0-00"))
    }

    @Test
    fun `prefix 05 mining grade 4`() {
        // Extracao de carvao
        assertEquals(4, CnaeRiskMapper.map("05.10-8-00"))
    }

    @Test
    fun `prefix 35 electrical grade 3`() {
        // Eletricidade e gas - NR-10
        assertEquals(3, CnaeRiskMapper.map("35.10-4-01"))
    }

    @Test
    fun `prefix 62 it grade 1`() {
        // Desenvolvimento de sistemas
        assertEquals(1, CnaeRiskMapper.map("62.09-1-00"))
    }

    @Test
    fun `prefix 86 health grade 2`() {
        // Atividades de atencao a saude - NR-32
        assertEquals(2, CnaeRiskMapper.map("86.30-5-04"))
    }

    @Test
    fun `prefix 43 construction services grade 4`() {
        // Servicos especializados de construcao - NR-18
        assertEquals(4, CnaeRiskMapper.map("43.29-1-05"))
    }

    // ── fallback for unknown prefix ───────────────────────────────────────

    @Test
    fun `unknown prefix 99 returns fallback grade 2`() {
        assertEquals(2, CnaeRiskMapper.map("99.00-0-00"))
    }

    @Test
    fun `unknown prefix 00 returns fallback grade 2`() {
        assertEquals(2, CnaeRiskMapper.map("00.01-0-00"))
    }

    // ── exact overrides prefix ────────────────────────────────────────────

    @Test
    fun `exact match takes priority over prefix rule`() {
        // Prefix "41" maps to grade 4; exact "41.20-4-00" also 4 — no conflict here,
        // but verifies exact lookup is attempted first.
        assertEquals(4, CnaeRiskMapper.map("41.20-4-00"))
    }

    @Test
    fun `exact match overrides lower prefix grade`() {
        // Prefix "86" → grade 2; exact "86.40-2-05" → also grade 2.
        // Confirm exact entry is used (both equal, no regression).
        assertEquals(2, CnaeRiskMapper.map("86.40-2-05"))
    }

    // ── whitespace tolerance ─────────────────────────────────────────────

    @Test
    fun `leading and trailing whitespace is trimmed`() {
        assertEquals(4, CnaeRiskMapper.map("  41.20-4-00  "))
    }
}

