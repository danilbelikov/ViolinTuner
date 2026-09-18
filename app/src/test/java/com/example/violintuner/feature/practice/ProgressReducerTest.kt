package com.example.violintuner.feature.practice

import com.example.violintuner.core.domain.progress.ProgressConfig
import com.example.violintuner.core.domain.progress.ProgressConfig.Companion.MS_PER_HOUR
import com.example.violintuner.core.domain.progress.Trophy
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressReducerTest {
    private val config = ProgressConfig()
    private val day = LocalDate.parse("2026-09-13")
    private val minute = 60_000L

    private fun given(vararg hours: Int) = hours.map { Trophy(it, day, shown = true) }

    private fun header(totalMs: Long, trophies: List<Trophy> = emptyList(), name: String = "") =
        ProgressReducer.headerOf(totalMs, trophies, name, avatarPath = null, config)

    @Test
    fun `the main frame of the handoff - level 4, two trophies and the next one`() {
        val header = header(16 * MS_PER_HOUR + 40 * minute, given(1, 10), name = "Даня")
        assertEquals(4, header.level)
        assertEquals(5, header.nextLevel)
        assertEquals(8 * MS_PER_HOUR + 20 * minute, header.toNextLevelMs)
        assertEquals(listOf(TrophyBadge(1, true), TrophyBadge(10, true), TrophyBadge(50, false)), header.trophyRow)
        assertEquals(2, header.givenTrophies)
        assertEquals(50, header.nextTrophyHours)
    }

    @Test
    fun `nothing practised - level one, an empty bar and the first mark as an outline`() {
        val header = header(0)
        assertEquals(1, header.level)
        assertEquals(0f, header.levelFraction, 0f)
        assertEquals(2 * MS_PER_HOUR, header.toNextLevelMs)
        assertEquals(listOf(TrophyBadge(1, false)), header.trophyRow)
        assertEquals(0, header.givenTrophies)
    }

    @Test
    fun `only the two latest trophies stand in the row, the count knows them all`() {
        val header = header(1250 * MS_PER_HOUR, given(1, 10, 50, 100, 250, 500, 1000))
        assertEquals(listOf(TrophyBadge(500, true), TrophyBadge(1000, true), TrophyBadge(2500, false)), header.trophyRow)
        assertEquals(7, header.givenTrophies)
        assertEquals(12, header.level)
    }

    @Test
    fun `with every trophy taken the row has no outline and the last level has nothing after it`() {
        val header = header(10_000 * MS_PER_HOUR, given(*config.trophyHours.toIntArray()))
        assertEquals(listOf(TrophyBadge(5000, true), TrophyBadge(10_000, true)), header.trophyRow)
        assertNull(header.nextTrophyHours)
        assertEquals(15, header.level)
        assertNull(header.nextLevel)
        assertNull(header.toNextLevelMs)
        assertEquals(1f, header.levelFraction, 0f)
    }

    @Test
    fun `a total edited down keeps the trophies and lowers the level`() {
        val header = header(30 * minute, given(1, 10))
        assertEquals(1, header.level)
        assertEquals(listOf(TrophyBadge(1, true), TrophyBadge(10, true), TrophyBadge(50, false)), header.trophyRow)
    }

    @Test
    fun `the list - dates for the given, what is left for the rest, nothing for the far ones`() {
        val lines = ProgressReducer.trophyLines(16 * MS_PER_HOUR + 40 * minute, given(1, 10), config)
        assertEquals(config.trophyHours, lines.map { it.hours })
        assertEquals(List(10) { it }, lines.map { it.index })
        assertEquals(listOf(day, day), lines.take(2).map { it.awardedDate })
        assertTrue(lines.drop(2).all { it.awardedDate == null })

        assertEquals(33 * MS_PER_HOUR + 20 * minute, lines[2].remainingMs)
        assertEquals(listOf(50), lines.filter { it.isNext }.map { it.hours })
        assertEquals(listOf(2500, 5000, 10_000), lines.filter { it.isFar }.map { it.hours })
        assertTrue(lines.filter { it.isFar }.all { it.remainingMs == null })
        assertTrue(lines.filter { !it.isFar && it.awardedDate == null }.all { it.remainingMs != null })
    }

    @Test
    fun `the next mark is never far, so the divider moves up as the marks are taken`() {
        val lines = ProgressReducer.trophyLines(1250 * MS_PER_HOUR, given(1, 10, 50, 100, 250, 500, 1000), config)
        assertEquals(listOf(5000, 10_000), lines.filter { it.isFar }.map { it.hours })
        assertEquals(1250 * MS_PER_HOUR, lines.single { it.hours == 2500 }.remainingMs)

        val last = ProgressReducer.trophyLines(6000 * MS_PER_HOUR, given(1, 10, 50, 100, 250, 500, 1000, 2500, 5000), config)
        assertTrue(last.none { it.isFar })
    }
}
