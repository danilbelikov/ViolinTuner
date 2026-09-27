package com.violinjourney.app.feature.repertoire.stand

import com.violinjourney.app.feature.repertoire.piece.TakeProblem
import com.violinjourney.app.feature.repertoire.piece.TakeState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** The stand hears of a take only what it shows (spec 3.15): its loudness and the backing's bar do not reach it. */
class StandTakeTest {
    private val take = TakeState(recording = true, elapsedSeconds = 12, levels = listOf(0.1f, 0.4f), problem = null, micPermission = true)

    @Test
    fun `the loudness and the backing's progress change nothing on the stand`() {
        val louder = take.copy(levels = listOf(0.9f, 0.8f), backingPlayedMs = 12_300, backingDurationMs = 180_000)
        assertEquals(StandTake.of(take), StandTake.of(louder))
    }

    @Test
    fun `its seconds its running and its problem reach the stand`() {
        assertNotEquals(StandTake.of(take), StandTake.of(take.copy(elapsedSeconds = 13)))
        assertNotEquals(StandTake.of(take), StandTake.of(take.copy(recording = false)))
        assertNotEquals(StandTake.of(take), StandTake.of(take.copy(problem = TakeProblem.TOO_NOISY)))
    }
}
