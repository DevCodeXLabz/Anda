package com.example.anda

import com.example.anda.data.network.OfficialCaResponse
import com.example.anda.feature.scanner.CaValidationService
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class CaValidationServiceTest {

    @Test
    fun validate_withOfficialResponse_prioritizesBackendData() {
        runBlocking {
            val result = CaValidationService.validate("55555") {
                OfficialCaResponse(
                    caNumber = "55555",
                    isValid = true,
                    itemName = "Capacete classe B",
                    riskCoverage = "Impacto",
                    validUntil = "2030-01-01",
                    source = "oficial",
                    statusReason = null
                )
            }

            assertTrue(result.isValid)
            assertEquals("oficial", result.source)
            assertEquals("Capacete classe B", result.itemName)
        }
    }

    @Test
    fun validate_withoutOfficialResponse_fallsBackToLocalOrFallback() {
        runBlocking {
            val localResult = CaValidationService.validate("12345") { null }
            val unknownResult = CaValidationService.validate("00000") { null }
            val expiredLocalResult = CaValidationService.validate("98765") { null }

            assertEquals("local", localResult.source)
            assertFalse(unknownResult.isValid)
            assertEquals("fallback", unknownResult.source)
            assertEquals("not_found", unknownResult.statusReason)
            assertEquals("expired", expiredLocalResult.statusReason)
        }
    }

    @Test
    fun validate_concurrentSameCa_executesSingleOfficialLookup() {
        runBlocking {
            val lookupCalls = AtomicInteger(0)

            val first = async {
                CaValidationService.validate("24680") {
                    lookupCalls.incrementAndGet()
                    delay(120)
                    OfficialCaResponse(
                        caNumber = "24680",
                        isValid = true,
                        itemName = "Respirador PFF2",
                        riskCoverage = "Aerodispersoides",
                        validUntil = "2032-06-30",
                        source = "oficial",
                        statusReason = null
                    )
                }
            }

            val second = async {
                CaValidationService.validate("24680") {
                    lookupCalls.incrementAndGet()
                    delay(120)
                    OfficialCaResponse(
                        caNumber = "24680",
                        isValid = true,
                        itemName = "Respirador PFF2",
                        riskCoverage = "Aerodispersoides",
                        validUntil = "2032-06-30",
                        source = "oficial",
                        statusReason = null
                    )
                }
            }

            val r1 = first.await()
            val r2 = second.await()

            assertTrue(r1.isValid)
            assertTrue(r2.isValid)
            assertEquals("24680", r1.caNumber)
            assertEquals("24680", r2.caNumber)
            assertEquals(1, lookupCalls.get())
        }
    }
}


