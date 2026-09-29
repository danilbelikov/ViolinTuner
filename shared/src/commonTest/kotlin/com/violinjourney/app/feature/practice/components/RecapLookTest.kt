package com.violinjourney.app.feature.practice.components

import com.violinjourney.app.core.domain.journey.TaktSources
import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_MINUTE
import com.violinjourney.app.core.domain.practice.PracticeRecap
import com.violinjourney.app.core.domain.practice.RecapRoad
import com.violinjourney.app.core.domain.progress.Progress
import com.violinjourney.app.core.domain.progress.ProgressConfig
import com.violinjourney.app.core.domain.progress.ProgressConfig.Companion.MS_PER_HOUR
import kotlin.test.Test
import kotlin.test.assertEquals

/** How «Занятие сохранено» lays out the streak and the level (spec 3.36.3): a pair, a new level framed with the streak as a row, no zero. */
class RecapLookTest {
    private val config = ProgressConfig()

    /** A practice of [minutes] after [before] of practice in all, with a streak of [streak] days. */
    private fun recap(streak: Int, before: Long, minutes: Int = 47): PracticeRecap {
        val duration = minutes * MS_PER_MINUTE
        return PracticeRecap(
            durationMs = duration,
            dayTotalMs = null,
            takts = minutes * 2,
            sources = TaktSources(notesInTune = 0, notesTakts = 0, minutes = minutes, timeTakts = minutes * 2, pieces = 0, piecesTakts = 0),
            road = RecapRoad.NotStarted,
            streakDays = streak,
            streakExtended = streak > 0,
            levelBefore = Progress.levelOf(before, config),
            levelAfter = Progress.levelOf(before + duration, config),
        )
    }

    /** Level 1 ends at 2 h (5.7): 1 h 50 min and 20 minutes more cross it. */
    private val crossing = 110 * MS_PER_MINUTE

    @Test
    fun `the streak and the level stand as a pair`() {
        assertEquals(RecapLook(RecapStreak.Pair, levelUp = false), RecapLook.of(recap(streak = 8, before = 40 * MS_PER_HOUR)))
        // one day is a streak too: it is not zero
        assertEquals(RecapLook(RecapStreak.Pair, levelUp = false), RecapLook.of(recap(streak = 1, before = 40 * MS_PER_HOUR)))
    }

    @Test
    fun `a new level takes a framed card and the streak shrinks to a row`() {
        val look = RecapLook.of(recap(streak = 8, before = crossing, minutes = 20))
        assertEquals(RecapLook(RecapStreak.Row, levelUp = true), look)
    }

    @Test
    fun `a streak of zero is not shown and the level stands the whole width`() {
        assertEquals(RecapLook(RecapStreak.None, levelUp = false), RecapLook.of(recap(streak = 0, before = 40 * MS_PER_HOUR)))
        // a new level with a streak of zero: the framed card, and no row under it either
        assertEquals(RecapLook(RecapStreak.None, levelUp = true), RecapLook.of(recap(streak = 0, before = crossing, minutes = 20)))
    }
}
