package com.violinjourney.app.core.domain.practice

import com.violinjourney.app.core.domain.journey.Arrival
import com.violinjourney.app.core.domain.journey.JourneyConfig
import com.violinjourney.app.core.domain.journey.JourneyProgress
import com.violinjourney.app.core.domain.journey.JourneyRoute
import com.violinjourney.app.core.domain.journey.JourneyRules
import com.violinjourney.app.core.domain.journey.TaktEarning
import com.violinjourney.app.core.domain.progress.ProgressConfig
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** «Занятие сохранено» (spec 3.31, 5.24): takts by source, the road, the streak and the level around one practice. */
class RecapRulesTest {
    private val journeyConfig = JourneyConfig()
    private val progressConfig = ProgressConfig()
    private val today = LocalDate.of(2026, 9, 23)
    private val min = 60_000L
    private val hour = 60 * min

    private fun entry(date: LocalDate, start: Long, ms: Long, manual: Boolean = false) = PracticeEntry(date, start, ms, manual)

    private fun earning(inTune: Int = 212, ms: Long = 47 * min, pieces: Int = 2) =
        TaktEarning(atEpochMs = 0, notesPlayed = inTune + 100, notesInTune = inTune, durationMs = ms, takts = JourneyRules.taktsFor(inTune, ms, journeyConfig, pieces), piecesPaid = pieces)

    /** Home, then [stops] more of the route reached. */
    private fun journey(earned: Long, spent: Long, stops: Int) = JourneyProgress(
        earned = earned,
        spent = spent,
        arrivals = JourneyRoute.stops.take(stops + 1).map { Arrival(it.id, 0) },
        extras = emptySet(),
    )

    @Test
    fun `the sources add up to the takts, and each is paid by the rule that paid it`() {
        val sources = JourneyRules.taktsBySource(earning(), journeyConfig)
        assertEquals(71, sources.notesTakts) // 212 / 3, up
        assertEquals(94, sources.timeTakts)
        assertEquals(60, sources.piecesTakts)
        assertEquals(earning().takts, sources.total)
        assertEquals(225, sources.total)
    }

    @Test
    fun `a practice without Live has no clean notes and no elements, only time`() {
        val sources = JourneyRules.taktsBySource(earning(inTune = 0, ms = 12 * min, pieces = 0), journeyConfig)
        assertEquals(0, sources.notesTakts)
        assertEquals(0, sources.piecesTakts)
        assertEquals(24, sources.total)
    }

    @Test
    fun `the day's total is shown only when the day had other practice, and the streak grows only with the day's first`() {
        val first = RecapRules.of(earning(), listOf(entry(today.minusDays(1), 0, hour), entry(today, 10, 47 * min)), JourneyProgress.EMPTY, today, journeyConfig, progressConfig)
        assertNull(first.dayTotalMs)
        assertTrue(first.streakExtended)
        assertEquals(2, first.streakDays)

        val second = RecapRules.of(earning(), listOf(entry(today, 5, 38 * min), entry(today, 10, 47 * min)), JourneyProgress.EMPTY, today, journeyConfig, progressConfig)
        assertEquals(85 * min, second.dayTotalMs)
        assertFalse(second.streakExtended)
    }

    @Test
    fun `the practice's day is the latest timed entry, not a day typed in by hand`() {
        val yesterday = today.minusDays(1)
        // started before midnight: its day is yesterday, where there was nothing else
        val recap = RecapRules.of(
            earning(),
            listOf(entry(yesterday, 10, 47 * min), entry(today, 5, 30 * min, manual = true)),
            JourneyProgress.EMPTY, today, journeyConfig, progressConfig,
        )
        assertNull(recap.dayTotalMs)
        assertTrue(recap.streakExtended)
    }

    @Test
    fun `the level is taken before and after the practice, and a threshold crossed is a new level`() {
        // 1 h 50 min before, 2 h 37 min after: level 2 starts at 2 h
        val recap = RecapRules.of(earning(), listOf(entry(today.minusDays(3), 0, 110 * min), entry(today, 10, 47 * min)), JourneyProgress.EMPTY, today, journeyConfig, progressConfig)
        assertEquals(1, recap.levelBefore.level)
        assertEquals(2, recap.levelAfter.level)
        assertTrue(recap.levelUp)

        val same = RecapRules.of(earning(), listOf(entry(today.minusDays(3), 0, 10 * min), entry(today, 10, 47 * min)), JourneyProgress.EMPTY, today, journeyConfig, progressConfig)
        assertFalse(same.levelUp)
        assertTrue(same.levelAfter.fraction > same.levelBefore.fraction)
    }

    @Test
    fun `the road before the journey begins, on a leg, with enough, and at the end of the route`() {
        assertEquals(RecapRoad.NotStarted, RecapRules.roadOf(JourneyProgress.EMPTY, 225))

        // at Vienna (index 4), 697 in the purse after 225 earned; Prague costs 1 600
        val vienna = journey(earned = 697 + 300 + 500 + 800 + 1200, spent = 300 + 500 + 800 + 1200, stops = 4)
        val leg = RecapRules.roadOf(vienna, 225) as RecapRoad.Leg
        assertEquals(JourneyRoute.indexOf("prague"), leg.nextIndex)
        assertEquals(1600, leg.price)
        assertEquals(472L, leg.balanceBefore)
        assertEquals(697L, leg.balanceAfter)
        assertEquals(903L, leg.missing)
        assertFalse(leg.enough)

        val rich = journey(earned = 1650 + 2800, spent = 2800, stops = 4)
        assertTrue((RecapRules.roadOf(rich, 305) as RecapRoad.Leg).enough)

        val all = journey(earned = 90_000, spent = 81_900, stops = JourneyRoute.stops.lastIndex)
        assertEquals(RecapRoad.RouteDone(8_100), RecapRules.roadOf(all, 176))
    }

    @Test
    fun `the purse before never goes below zero`() {
        val fresh = journey(earned = 100, spent = 0, stops = 0)
        assertEquals(0L, (RecapRules.roadOf(fresh, 225) as RecapRoad.Leg).balanceBefore)
    }
}
