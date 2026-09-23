package com.violinjourney.app.core.domain.practice

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * «Закончить занятие», asked for on Live and answered on «Занятия» (spec 3.12): the tag by the record key leads to the
 * tab, whose sheet does the finishing. One flag for the whole app, taken once. Not the saved state of the tab's entry:
 * the handle the navigation gives out is not the one the tab's view model is made with.
 */
@Singleton
class FinishPracticeAsk @Inject constructor() {
    private val flag = MutableStateFlow(false)
    val asked: StateFlow<Boolean> = flag.asStateFlow()

    fun ask() {
        flag.value = true
    }

    /** True once per ask: whoever takes it answers it. */
    fun take(): Boolean = flag.compareAndSet(expect = true, update = false)
}
