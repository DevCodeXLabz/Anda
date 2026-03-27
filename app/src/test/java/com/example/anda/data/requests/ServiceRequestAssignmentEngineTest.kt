package com.example.anda.data.requests

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class ServiceRequestAssignmentEngineTest {

    private lateinit var engine: ServiceRequestAssignmentEngine

    @Before
    fun setup() {
        engine = ServiceRequestAssignmentEngine()
    }

    @Test
    fun findBestTechnician_selectsCertifiedTech() {
        val techs = listOf(
            TechnicianProfile("111.111.111-11", "João", listOf("ASO", "PCMSO"), activeRequestCount = 2),
            TechnicianProfile("222.222.222-22", "Maria", listOf("ASO"), activeRequestCount = 0)
        )

        val best = engine.findBestTechnician("ASO", techs)

        // Maria should be selected (least busy, both certified for ASO)
        assertEquals("222.222.222-22", best)
    }

    @Test
    fun findBestTechnician_returnsNullWhenNoMatch() {
        val techs = listOf(
            TechnicianProfile("111.111.111-11", "João", listOf("NR10"), activeRequestCount = 0)
        )

        val best = engine.findBestTechnician("PCMSO", techs)

        assertEquals(null, best)
    }

    @Test
    fun findBestTechnician_prefersGeneralist() {
        val techs = listOf(
            TechnicianProfile("111.111.111-11", "João", listOf("ASO"), activeRequestCount = 0),
            TechnicianProfile("222.222.222-22", "Maria", listOf("ALL"), activeRequestCount = 5)
        )

        val best = engine.findBestTechnician("PCMSO", techs)

        // Maria is generalist but busy; João is only ASO; should pick Maria (generalist match)
        assertEquals("222.222.222-22", best)
    }

    @Test
    fun calculateAssignmentScore_rewardsCertificationAndAvailability() {
        val tech = TechnicianProfile(
            "111.111.111-11",
            "João",
            listOf("ASO", "PCMSO"),
            activeRequestCount = 0,
            yearsExperience = 5
        )

        val score = engine.calculateAssignmentScore(tech, "ASO")

        // Base 50 + Certification 30 + No active requests 20 + Experience 10 = 110 (capped at 100)
        assertEquals(100, score)
    }

    @Test
    fun calculateAssignmentScore_penalizesOverbooked() {
        val tech = TechnicianProfile(
            "111.111.111-11",
            "João",
            listOf("ASO"),
            activeRequestCount = 10,
            yearsExperience = 2
        )

        val score = engine.calculateAssignmentScore(tech, "ASO")

        // Base 50 + Certification 30 + no availability bonus = ~84
        assertEquals(84, score)
    }
}

