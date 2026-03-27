package com.example.anda.feature.requests

import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceRequestSortPolicyTest {

    private data class SortFixture(
        val requestCode: String,
        val updatedAt: Long,
        val latestActionEpochMs: Long?
    )

    @Test
    fun sort_prioritizesFocusedRequest_beforeAnyOtherRule() {
        val items = listOf(
            SortFixture(requestCode = "REQ-1", updatedAt = 1000L, latestActionEpochMs = 1000L),
            SortFixture(requestCode = "REQ-2", updatedAt = 5000L, latestActionEpochMs = 5000L)
        )

        val sorted = ServiceRequestSortPolicy.sort(
            items = items,
            focusRequestCode = "REQ-1",
            sortByLatestAction = true,
            requestCodeOf = { it.requestCode },
            updatedAtOf = { it.updatedAt },
            latestActionEpochMsOf = { it.latestActionEpochMs }
        )

        assertEquals("REQ-1", sorted.first().requestCode)
    }

    @Test
    fun sort_byLatestAction_usesLatestActionThenUpdatedAt() {
        val items = listOf(
            SortFixture(requestCode = "REQ-1", updatedAt = 2000L, latestActionEpochMs = 1000L),
            SortFixture(requestCode = "REQ-2", updatedAt = 1000L, latestActionEpochMs = 2000L),
            SortFixture(requestCode = "REQ-3", updatedAt = 3000L, latestActionEpochMs = 2000L)
        )

        val sorted = ServiceRequestSortPolicy.sort(
            items = items,
            focusRequestCode = "",
            sortByLatestAction = true,
            requestCodeOf = { it.requestCode },
            updatedAtOf = { it.updatedAt },
            latestActionEpochMsOf = { it.latestActionEpochMs }
        )

        assertEquals(listOf("REQ-3", "REQ-2", "REQ-1"), sorted.map { it.requestCode })
    }

    @Test
    fun sort_byUpdatedAt_whenLatestActionModeDisabled() {
        val items = listOf(
            SortFixture(requestCode = "REQ-1", updatedAt = 2000L, latestActionEpochMs = 5000L),
            SortFixture(requestCode = "REQ-2", updatedAt = 3000L, latestActionEpochMs = 1000L),
            SortFixture(requestCode = "REQ-3", updatedAt = 1000L, latestActionEpochMs = 9000L)
        )

        val sorted = ServiceRequestSortPolicy.sort(
            items = items,
            focusRequestCode = "",
            sortByLatestAction = false,
            requestCodeOf = { it.requestCode },
            updatedAtOf = { it.updatedAt },
            latestActionEpochMsOf = { it.latestActionEpochMs }
        )

        assertEquals(listOf("REQ-2", "REQ-1", "REQ-3"), sorted.map { it.requestCode })
    }

    @Test
    fun sort_byLatestAction_placesNullLatestActionAfterValidTimestamp() {
        val items = listOf(
            SortFixture(requestCode = "REQ-1", updatedAt = 3000L, latestActionEpochMs = null),
            SortFixture(requestCode = "REQ-2", updatedAt = 1000L, latestActionEpochMs = 2000L)
        )

        val sorted = ServiceRequestSortPolicy.sort(
            items = items,
            focusRequestCode = "",
            sortByLatestAction = true,
            requestCodeOf = { it.requestCode },
            updatedAtOf = { it.updatedAt },
            latestActionEpochMsOf = { it.latestActionEpochMs }
        )

        assertEquals(listOf("REQ-2", "REQ-1"), sorted.map { it.requestCode })
    }

    @Test
    fun sort_usesRequestCodeAsFinalTieBreaker() {
        val items = listOf(
            SortFixture(requestCode = "REQ-1", updatedAt = 2000L, latestActionEpochMs = 1000L),
            SortFixture(requestCode = "REQ-3", updatedAt = 2000L, latestActionEpochMs = 1000L),
            SortFixture(requestCode = "REQ-2", updatedAt = 2000L, latestActionEpochMs = 1000L)
        )

        val sorted = ServiceRequestSortPolicy.sort(
            items = items,
            focusRequestCode = "",
            sortByLatestAction = true,
            requestCodeOf = { it.requestCode },
            updatedAtOf = { it.updatedAt },
            latestActionEpochMsOf = { it.latestActionEpochMs }
        )

        assertEquals(listOf("REQ-3", "REQ-2", "REQ-1"), sorted.map { it.requestCode })
    }
}

