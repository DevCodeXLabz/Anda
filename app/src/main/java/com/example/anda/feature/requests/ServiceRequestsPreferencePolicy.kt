package com.example.anda.feature.requests

object ServiceRequestsPreferencePolicy {

    private const val FILTER_ALL = "all"
    private const val FILTER_OPEN = "open"
    private const val FILTER_ACTIVE = "active"

    private const val STATUS_FILTER_TECHNICIAN = "status_filter_technician"
    private const val STATUS_FILTER_COMPANY = "status_filter_company"

    private const val CNPJ_FILTER_TECHNICIAN = "contractor_cnpj_filter_technician"
    private const val CNPJ_FILTER_COMPANY = "contractor_cnpj_filter_company"

    private const val SORT_LATEST_ACTION_TECHNICIAN = "sort_latest_action_technician"
    private const val SORT_LATEST_ACTION_COMPANY = "sort_latest_action_company"

    fun sanitizeStatusFilter(raw: String?): String {
        return when (raw) {
            FILTER_ALL, FILTER_OPEN, FILTER_ACTIVE -> raw
            else -> FILTER_ALL
        }
    }

    fun normalizeCnpjFilter(raw: String?): String {
        return raw.orEmpty().filter(Char::isDigit)
    }

    fun statusPreferenceKey(technicianMode: Boolean): String {
        return if (technicianMode) STATUS_FILTER_TECHNICIAN else STATUS_FILTER_COMPANY
    }

    fun cnpjFilterPreferenceKey(technicianMode: Boolean): String {
        return if (technicianMode) CNPJ_FILTER_TECHNICIAN else CNPJ_FILTER_COMPANY
    }

    fun sortPreferenceKey(technicianMode: Boolean): String {
        return if (technicianMode) SORT_LATEST_ACTION_TECHNICIAN else SORT_LATEST_ACTION_COMPANY
    }
}

