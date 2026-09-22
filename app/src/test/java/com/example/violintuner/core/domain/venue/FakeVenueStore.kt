package com.example.violintuner.core.domain.venue

import kotlinx.coroutines.flow.MutableStateFlow

/** «Where we are» kept in memory. */
class FakeVenueStore(initial: String? = null) : VenueStore {
    override val stored = MutableStateFlow(initial)

    override suspend fun store(value: String?) {
        stored.value = value
    }
}
