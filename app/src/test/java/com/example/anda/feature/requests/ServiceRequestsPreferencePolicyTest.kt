package com.example.anda.feature.requests

import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceRequestsPreferencePolicyTest {

    @Test
    fun sanitizeStatusFilter_fallsBackToAllWhenUnknown() {
        assertEquals("all", ServiceRequestsPreferencePolicy.sanitizeStatusFilter("unknown"))
    }

    @Test
    fun sanitizeStatusFilter_keepsKnownValues() {
        assertEquals("open", ServiceRequestsPreferencePolicy.sanitizeStatusFilter("open"))
        assertEquals("active", ServiceRequestsPreferencePolicy.sanitizeStatusFilter("active"))
        assertEquals("all", ServiceRequestsPreferencePolicy.sanitizeStatusFilter("all"))
    }

    @Test
    fun normalizeCnpjFilter_keepsOnlyDigits() {
        assertEquals("20074884000136", ServiceRequestsPreferencePolicy.normalizeCnpjFilter("20.074.884/0001-36"))
    }

    @Test
    fun keys_areModeAware() {
        assertEquals("status_filter_technician", ServiceRequestsPreferencePolicy.statusPreferenceKey(technicianMode = true))
        assertEquals("status_filter_company", ServiceRequestsPreferencePolicy.statusPreferenceKey(technicianMode = false))
        assertEquals("contractor_cnpj_filter_technician", ServiceRequestsPreferencePolicy.cnpjFilterPreferenceKey(technicianMode = true))
        assertEquals("contractor_cnpj_filter_company", ServiceRequestsPreferencePolicy.cnpjFilterPreferenceKey(technicianMode = false))
        assertEquals("sort_latest_action_technician", ServiceRequestsPreferencePolicy.sortPreferenceKey(technicianMode = true))
        assertEquals("sort_latest_action_company", ServiceRequestsPreferencePolicy.sortPreferenceKey(technicianMode = false))
    }
}

