package com.example.anda.feature.requests

object ServiceRequestSortPolicy {

    fun <T> sort(
        items: List<T>,
        focusRequestCode: String,
        sortByLatestAction: Boolean,
        requestCodeOf: (T) -> String,
        updatedAtOf: (T) -> Long,
        latestActionEpochMsOf: (T) -> Long?
    ): List<T> {
        return if (sortByLatestAction) {
            items.sortedWith(
                compareByDescending<T> { requestCodeOf(it) == focusRequestCode }
                    .thenByDescending { latestActionEpochMsOf(it) ?: Long.MIN_VALUE }
                    .thenByDescending { updatedAtOf(it) }
                    .thenByDescending { requestCodeOf(it) }
            )
        } else {
            items.sortedWith(
                compareByDescending<T> { requestCodeOf(it) == focusRequestCode }
                    .thenByDescending { updatedAtOf(it) }
                    .thenByDescending { requestCodeOf(it) }
            )
        }
    }
}

