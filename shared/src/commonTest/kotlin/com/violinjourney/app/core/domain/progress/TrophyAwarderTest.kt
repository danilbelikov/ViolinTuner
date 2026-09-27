package com.violinjourney.app.core.domain.progress

import com.violinjourney.app.core.domain.practice.FakePracticeRepository
import com.violinjourney.app.core.domain.practice.PracticeEntry
import com.violinjourney.app.core.domain.progress.ProgressConfig.Companion.MS_PER_HOUR
import com.violinjourney.app.core.time.FixedWallClock
import com.violinjourney.app.core.time.WallClock
import kotlin.time.Instant
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.assertEquals
import kotlin.test.Test

class TrophyAwarderTest {
    private val config = ProgressConfig()
    private val zone = TimeZone.of("Europe/Moscow")
    private val today = LocalDate.parse("2026-09-18")
    private val clock = FixedWallClock(Instant.parse("2026-09-18T20:30:00Z"), zone)

    @Test
    fun `a mark is due from the very millisecond it is reached`() {
        assertEquals(emptyList<Int>(), TrophyAwarder.due(MS_PER_HOUR - 1, emptySet(), config))
        assertEquals(listOf(1), TrophyAwarder.due(MS_PER_HOUR, emptySet(), config))
    }

    @Test
    fun `a jump over several marks gives them all — lowest first`() {
        assertEquals(listOf(1, 10, 50), TrophyAwarder.due(60 * MS_PER_HOUR, emptySet(), config))
        assertEquals(listOf(50), TrophyAwarder.due(60 * MS_PER_HOUR, setOf(1, 10), config))
    }

    @Test
    fun `nothing is due when every reached mark has its trophy`() {
        assertEquals(emptyList<Int>(), TrophyAwarder.due(16 * MS_PER_HOUR, setOf(1, 10), config))
    }

    @Test
    fun `trophies get the local date of the award`() = runTest {
        val repository = FakeTrophyRepository()
        TrophyAwarder(repository, config, clock).award(12 * MS_PER_HOUR, awardedHours = emptySet())
        // 20:30 UTC is already the 18th, 23:30, in Moscow.
        assertEquals(listOf(Trophy(1, today, shown = false), Trophy(10, today, shown = false)), repository.trophies.value)
    }

    @Test
    fun `a total that went down takes nothing away and a repeat changes nothing`() = runTest {
        val repository = FakeTrophyRepository()
        val earlier = LocalDate.parse("2026-09-02")
        repository.award(1, earlier)
        repository.award(10, earlier)
        val awarder = TrophyAwarder(repository, config, clock)

        awarder.award(30 * 60_000L, awardedHours = setOf(1, 10))
        awarder.award(12 * MS_PER_HOUR, awardedHours = emptySet())

        assertEquals(listOf(Trophy(1, earlier, shown = false), Trophy(10, earlier, shown = false)), repository.trophies.value)
    }

    @Test
    fun `following the practice gives each trophy once as the total grows`() = runTest {
        val repository = FakeTrophyRepository()
        val practice = FakePracticeRepository()
        backgroundScope.launch { TrophyAwarder(repository, config, clock).follow(practice.entries) }
        runCurrent()
        assertEquals(emptyList(), repository.trophies.value)

        practice.add(PracticeEntry(today, startedAtEpochMs = 0, durationMs = 5 * MS_PER_HOUR, manual = true))
        runCurrent()
        assertEquals(listOf(Trophy(1, today, shown = false)), repository.trophies.value)

        practice.add(PracticeEntry(today, startedAtEpochMs = 1, durationMs = 6 * MS_PER_HOUR, manual = true))
        runCurrent()
        assertEquals(listOf(1, 10), repository.trophies.value.map { it.hours }, "the ten hours are given, the first hour is not given again")

        // a day changed by hand to less: what was given stays (spec 5.7)
        practice.replaceDay(today, durationMs = MS_PER_HOUR / 2, startedAtEpochMs = 0)
        runCurrent()
        assertEquals(listOf(1, 10), repository.trophies.value.map { it.hours })
    }
}
