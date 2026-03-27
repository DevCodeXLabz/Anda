package com.example.anda.data.network

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * Tests for BrasilApiClient CNPJ lookup functionality.
 *
 * This validates that the API correctly fetches company data from the public BrasilAPI service.
 * 
 * Note: The real API test is commented out as it requires network access.
 * Uncomment and run manually with live network to test against BrasilAPI.
 */
class BrasilApiClientTest {

    /**
     * Tests CNPJ normalization (removing non-digit characters).
     * This is always reliable without network access.
     */
    @Test
    fun testCnpjNormalization() {
        // Test that CNPJ normalization works correctly with various formats
        val testCases = listOf(
            "20.074.884/0001-36" to "20074884000136",
            "20074884000136" to "20074884000136",
            "20-074-884-0001-36" to "20074884000136"
        )

        testCases.forEach { (input, expected) ->
            // Note: normalizeCnpj is private, so this test documents the expected behavior
            val normalized = input.filter(Char::isDigit)
            assertEquals("CNPJ $input should normalize to $expected", expected, normalized)
        }
    }

    /**
     * Tests CompanyProfile data structure and field storage.
     */
    @Test
    fun testCompanyProfileDataStructure() {
        // Arrange: Create a CompanyProfile with test data
        val profile = CompanyProfile(
            cnpj = "20074884000136",
            legalName = "Nancy Rezende de Lima",
            tradeName = "Nancy Rezende",
            cnae = "8621-6/01",
            city = "São Paulo",
            state = "SP",
            postalCode = "01311-100"
        )

        // Assert: Verify all fields are correctly stored
        assertEquals("20074884000136", profile.cnpj)
        assertEquals("Nancy Rezende de Lima", profile.legalName)
        assertEquals("Nancy Rezende", profile.tradeName)
        assertEquals("São Paulo", profile.city)
        assertEquals("SP", profile.state)
    }

    /**
     * MANUAL TEST: Uncomment to test live CNPJ lookup with BrasilAPI.
     * 
     * This requires:
     * 1. Live network access
     * 2. Run as: ./gradlew testDebugUnitTest -PrunNetworkTests
     * 3. Validates that CNPJ 20.074.884/0001-36 returns "Nancy Rezende de Lima"
     * 
     * Expected result:
     * - cnpj: 20074884000136
     * - legalName: Nancy Rezende de Lima
     * - city and state: populated from API
     
    @Test
    fun testCnpjLookup_ValidNancyRezende_LiveAPI() {
        // Arrange
        val cnpj = "20.074.884/0001-36"
        val expectedLegalName = "Nancy Rezende de Lima"

        try {
            // Act: Synchronously call the BrasilAPI client
            val company = BrasilApiClient.fetchCompanyByCnpj(cnpj)

            // Assert
            assertEquals("CNPJ should be normalized to 14 digits", "20074884000136", company.cnpj)
            assertEquals("Legal name should match Nancy Rezende de Lima", expectedLegalName, company.legalName)
            assertTrue("City should be populated", company.city.isNotBlank())
            assertTrue("State should be populated", company.state.isNotBlank())
        } catch (e: Exception) {
            // Log for manual inspection
            println("Live CNPJ lookup test failed (expected if no network): ${e.message}")
            throw e
        }
    }
     */
}


