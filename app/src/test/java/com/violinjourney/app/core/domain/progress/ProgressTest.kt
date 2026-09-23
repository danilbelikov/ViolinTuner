package com.violinjourney.app.core.domain.progress

import com.violinjourney.app.core.domain.practice.PracticeEntry
import com.violinjourney.app.core.domain.progress.ProgressConfig.Companion.MS_PER_HOUR
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressTest {
    private val config = ProgressConfig()
    private val minute = 60_000L

    @Test
    fun `progress is the sum of every entry, manual or timed`() {
        val day = LocalDate.parse("2026-09-17")
        val entries = listOf(
            PracticeEntry(day, 0, 40 * minute, manual = false),
            PracticeEntry(day, 0, 20 * minute, manual = true),
            PracticeEntry(day.minusDays(30), 0, 2 * MS_PER_HOUR, manual = true),
        )
        assertEquals(3 * MS_PER_HOUR, Progress.totalMs(entries))
        assertEquals(0L, Progress.totalMs(emptyList()))
    }

    @Test
    fun `nothing practised is level one with an empty bar and two hours to go`() {
        assertEquals(LevelProgress(level = 1, nextLevel = 2, fraction = 0f, toNextMs = 2 * MS_PER_HOUR), Progress.levelOf(0, config))
    }

    @Test
    fun `a level begins exactly at its threshold`() {
        config.levelThresholdHours.forEachIndexed { index, hours ->
            val threshold = hours * MS_PER_HOUR
            assertEquals("at $hours h", index + 1, Progress.levelOf(threshold, config).level)
            if (index > 0) assertEquals("just under $hours h", index, Progress.levelOf(threshold - 1, config).level)
        }
    }

    @Test
    fun `the bar is the share of the way between two thresholds`() {
        // The main frame of the handoff: 16 h 40 min is level 4 (10 h), 44 % of the way to 25 h.
        val level = Progress.levelOf(16 * MS_PER_HOUR + 40 * minute, config)
        assertEquals(4, level.level)
        assertEquals(5, level.nextLevel)
        assertEquals(0.444f, level.fraction, 0.001f)
        assertEquals(8 * MS_PER_HOUR + 20 * minute, level.toNextMs)
    }

    @Test
    fun `the last level has a full bar and nothing after it`() {
        val expected = LevelProgress(level = 15, nextLevel = null, fraction = 1f, toNextMs = null)
        assertEquals(expected, Progress.levelOf(10_000 * MS_PER_HOUR, config))
        assertEquals(expected, Progress.levelOf(25_000 * MS_PER_HOUR, config))
    }

    @Test
    fun `a negative total reads as nothing`() {
        assertEquals(Progress.levelOf(0, config), Progress.levelOf(-5, config).copy(toNextMs = 2 * MS_PER_HOUR))
        assertEquals(1, Progress.levelOf(-5, config).level)
    }

    @Test
    fun `the next trophy is the lowest mark not taken`() {
        assertEquals(1, Progress.nextTrophyHours(emptySet(), config))
        assertEquals(50, Progress.nextTrophyHours(setOf(1, 10), config))
        // A gap can only come from an edited history; the gap is still the next one.
        assertEquals(10, Progress.nextTrophyHours(setOf(1, 50), config))
        assertNull(Progress.nextTrophyHours(config.trophyHours.toSet(), config))
    }

    @Test
    fun `what is left to a mark never goes below zero`() {
        assertEquals(33 * MS_PER_HOUR + 20 * minute, Progress.remainingMs(16 * MS_PER_HOUR + 40 * minute, 50))
        assertEquals(0L, Progress.remainingMs(60 * MS_PER_HOUR, 50))
    }

    @Test
    fun `marks from 2500 hours are far ahead unless taken or next`() {
        val early = setOf(1, 10)
        assertFalse(Progress.isFar(1000, early, config))
        assertTrue(Progress.isFar(2500, early, config))
        assertTrue(Progress.isFar(10_000, early, config))

        val late = setOf(1, 10, 50, 100, 250, 500, 1000)
        assertFalse("the next mark always shows what is left", Progress.isFar(2500, late, config))
        assertTrue(Progress.isFar(5000, late, config))
        assertFalse("a taken mark is not ahead at all", Progress.isFar(2500, late + 2500, config))
    }
}
