package com.violinjourney.app.feature.history

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/**
 * A section of «Записи» asked for from elsewhere — «Открыть репертуар» on Live (spec 3.28): one ask for the whole app,
 * taken once by the tab. Not the saved state of the tab's entry: the handle the navigation gives out is not the one the
 * tab's view model is made with, and the ask never reached it.
 */
class HistorySectionAsk {
    private val section = MutableStateFlow<HistorySection?>(null)
    val asked: StateFlow<HistorySection?> = section.asStateFlow()

    fun ask(wanted: HistorySection) {
        section.value = wanted
    }

    /** The section asked for, once: whoever takes it answers it. */
    fun take(): HistorySection? = section.getAndUpdate { null }
}
