package com.violinjourney.app.feature.practice.components

import com.violinjourney.app.core.domain.practice.ForgottenEndings
import com.violinjourney.app.core.domain.practice.RunningPractice
import com.violinjourney.app.feature.practice.PracticePrompt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The numbers of «Занятие не закончено» on its face (spec 3.36.3): a sheet sliding away after an answer — the prompt and its numbers
 * already gone — keeps the last it showed; a new question starts without numbers.
 */
class KeptEndingsTest {
    private val asked = PracticePrompt.Forgotten(RunningPractice(1_000L, lastSoundEpochMs = 2_000L))
    private val first = ForgottenEndings(runningMs = 192L, endNowMs = 192L, endAtMarkMs = 64L)
    private val later = first.copy(runningMs = 193L, endNowMs = 193L)

    @Test
    fun `a face whose numbers are gone keeps the last it showed`() {
        val kept = KeptEndings()
        assertNull(kept.of(asked, null), "before the first tick: nothing to show")
        assertEquals(first, kept.of(asked, first))
        assertEquals(later, kept.of(asked, later))
        assertEquals(later, kept.of(asked, null), "the answer took the numbers: the face sliding away still says them")
    }

    @Test
    fun `another question starts without the numbers of the one before`() {
        val kept = KeptEndings()
        kept.of(asked, first)
        val next = PracticePrompt.Forgotten(RunningPractice(5_000L, lastSoundEpochMs = null))
        assertNull(kept.of(next, null))
        assertEquals(later, kept.of(next, later))
    }
}
