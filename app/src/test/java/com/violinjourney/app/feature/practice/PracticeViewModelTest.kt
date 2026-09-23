package com.violinjourney.app.feature.practice

import com.violinjourney.app.core.data.profile.FakeAvatarFiles
import com.violinjourney.app.core.domain.repertoire.FakeRepertoireRepository
import com.violinjourney.app.core.domain.practice.FakePracticeRepository
import com.violinjourney.app.core.domain.practice.FakeRunningPracticeStore
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_HOUR
import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_MINUTE
import com.violinjourney.app.core.domain.practice.PracticeEntry
import com.violinjourney.app.core.domain.practice.FinishPracticeAsk
import com.violinjourney.app.core.domain.practice.PracticeFinisher
import com.violinjourney.app.core.domain.practice.RunningPractice
import com.violinjourney.app.core.domain.progress.FakeProfileRepository
import com.violinjourney.app.core.domain.progress.FakeTrophyRepository
import com.violinjourney.app.core.domain.progress.ProgressConfig
import com.violinjourney.app.core.domain.session.FakeSessionRepository
import com.violinjourney.app.core.domain.journey.FakeJourneyRepository
import com.violinjourney.app.core.domain.journey.TaktEarning
import com.violinjourney.app.feature.journey.JourneyMotion
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PracticeViewModelTest {
    private val repository = FakePracticeRepository()
    private val store = FakeRunningPracticeStore()
    private val sessions = FakeSessionRepository()
    private val config = PracticeConfig()
    private val trophies = FakeTrophyRepository()
    private val profiles = FakeProfileRepository()
    private val avatarFiles = FakeAvatarFiles()
    private val zone: ZoneId = ZoneId.of("Europe/Moscow")

    /** A clock the test moves by hand; the ticker's delays run on the test scheduler. */
    private class TestClock(var nowMs: Long, private val zone: ZoneId) : Clock() {
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = TestClock(nowMs, zone)
        override fun instant(): Instant = Instant.ofEpochMilli(nowMs)
    }

    // 2026-09-17 18:00 Moscow
    private val journey = FakeJourneyRepository()
    private val clock = TestClock(Instant.parse("2026-09-17T15:00:00Z").toEpochMilli(), zone)
    private val today = LocalDate.of(2026, 9, 17)

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(finishAsk: FinishPracticeAsk = FinishPracticeAsk()): Pair<PracticeViewModel, MutableList<PracticeEffect>> {
        val viewModel = PracticeViewModel(
            repository, store, PracticeFinisher(repository, store, clock, journey = journey), sessions, config, FakeRepertoireRepository(), clock,
            trophies, profiles, avatarFiles, ProgressConfig(), journey, finishAsk = finishAsk,
        )
        val effects = mutableListOf<PracticeEffect>()
        backgroundScope.launch { viewModel.state.collect {} }
        backgroundScope.launch { viewModel.effects.collect { effects += it } }
        runCurrent()
        return viewModel to effects
    }

    /** Moves the wall clock and the virtual time together, a second at a time, like real time does. */
    private fun TestScope.pass(ms: Long) {
        runCurrent()
        var left = ms
        while (left > 0) {
            val slice = minOf(left, 1_000L)
            clock.nowMs += slice
            advanceTimeBy(slice)
            runCurrent() // a tick due exactly now runs against the clock of this moment
            left -= slice
        }
        runCurrent()
    }

    @Test
    fun `starts empty and idle`() = runTest {
        val (viewModel, _) = viewModel()
        val state = viewModel.state.value
        assertFalse(state.loading)
        assertFalse(state.hasHistory)
        assertNull(state.runningMs)
        assertEquals(YearMonth.of(2026, 9), state.month)
        assertEquals(today, state.selected.date)
    }

    @Test
    fun `start stores the moment, opens live and the timer follows the clock`() = runTest {
        val (viewModel, effects) = viewModel()
        viewModel.onIntent(PracticeIntent.StartClicked)
        runCurrent()
        assertEquals(RunningPractice(clock.nowMs, null), store.running.value)
        assertEquals(listOf(PracticeEffect.OpenLive), effects)
        assertEquals(0L, viewModel.state.value.runningMs)
        pass(2_500)
        assertEquals(2_000L, viewModel.state.value.runningMs)
        pass(500)
        assertEquals(3_000L, viewModel.state.value.runningMs)
    }

    @Test
    fun `a second start does not restart a running practice`() = runTest {
        val (viewModel, effects) = viewModel()
        store.start(clock.nowMs - 10 * MS_PER_MINUTE)
        runCurrent()
        viewModel.onIntent(PracticeIntent.StartClicked)
        runCurrent()
        assertEquals(clock.nowMs - 10 * MS_PER_MINUTE, store.running.value!!.startedAtEpochMs)
        assertEquals(listOf(PracticeEffect.OpenLive), effects)
    }

    @Test
    fun `stopping under a minute drops the practice with a toast`() = runTest {
        val (viewModel, effects) = viewModel()
        viewModel.onIntent(PracticeIntent.StartClicked)
        pass(59_000)
        viewModel.onIntent(PracticeIntent.StopClicked)
        runCurrent()
        assertNull(store.running.value)
        assertNull(viewModel.state.value.sheet)
        assertEquals(PracticeEffect.ShowTooShort, effects.last())
        assertTrue(repository.entries.value.isEmpty())
    }

    @Test
    fun `stopping opens the summary and saving stores the practice on its start day`() = runTest {
        val (viewModel, _) = viewModel()
        viewModel.onIntent(PracticeIntent.StartClicked)
        val start = clock.nowMs
        pass(47 * MS_PER_MINUTE + 20_000)
        viewModel.onIntent(PracticeIntent.StopClicked)
        runCurrent()
        val sheet = viewModel.state.value.sheet as PracticeSheet.Summary
        assertEquals(47, sheet.minutes)
        assertEquals(47 * MS_PER_MINUTE + 20_000, sheet.actualMs)
        // the timer keeps going behind the sheet: the practice is not over until "Сохранить"
        assertEquals(47 * MS_PER_MINUTE + 20_000, viewModel.state.value.runningMs)

        viewModel.onIntent(PracticeIntent.SummarySaved)
        runCurrent()
        assertEquals(
            listOf(PracticeEntry(today, start, 47 * MS_PER_MINUTE + 20_000, manual = false, id = 1)),
            repository.entries.value,
        )
        assertNull(store.running.value)
        // «Занятие сохранено» takes the summary's place (spec 3.31)
        assertTrue(viewModel.state.value.sheet is PracticeSheet.Recap)
        assertNull(viewModel.state.value.runningMs)
        assertTrue(viewModel.state.value.hasHistory)
        assertEquals(47 * MS_PER_MINUTE + 20_000, viewModel.state.value.todayMs)
    }

    @Test
    fun `a trimmed summary saves whole minutes`() = runTest {
        val (viewModel, _) = viewModel()
        viewModel.onIntent(PracticeIntent.StartClicked)
        pass(47 * MS_PER_MINUTE + 20_000)
        viewModel.onIntent(PracticeIntent.StopClicked)
        viewModel.onIntent(PracticeIntent.SummaryStepped(-1))
        viewModel.onIntent(PracticeIntent.SummaryStepped(-1))
        runCurrent()
        assertEquals(37, (viewModel.state.value.sheet as PracticeSheet.Summary).minutes)
        viewModel.onIntent(PracticeIntent.SummarySaved)
        runCurrent()
        assertEquals(37 * MS_PER_MINUTE, repository.entries.value.single().durationMs)
    }

    @Test
    fun `a practice started before midnight belongs to that day`() = runTest {
        val (viewModel, _) = viewModel()
        clock.nowMs = Instant.parse("2026-09-17T20:50:00Z").toEpochMilli() // 23:50 Moscow
        viewModel.onIntent(PracticeIntent.StartClicked)
        pass(30 * MS_PER_MINUTE)
        viewModel.onIntent(PracticeIntent.StopClicked)
        viewModel.onIntent(PracticeIntent.SummarySaved)
        runCurrent()
        assertEquals(LocalDate.of(2026, 9, 17), repository.entries.value.single().date)
    }

    @Test
    fun `discarding the summary saves nothing`() = runTest {
        val (viewModel, _) = viewModel()
        viewModel.onIntent(PracticeIntent.StartClicked)
        pass(10 * MS_PER_MINUTE)
        viewModel.onIntent(PracticeIntent.StopClicked)
        viewModel.onIntent(PracticeIntent.SummaryDiscarded)
        runCurrent()
        assertNull(store.running.value)
        assertNull(viewModel.state.value.sheet)
        assertTrue(repository.entries.value.isEmpty())
    }

    @Test
    fun `hiding the summary is not an answer - the practice runs on and nothing is saved`() = runTest {
        val (viewModel, _) = viewModel()
        viewModel.onIntent(PracticeIntent.StartClicked)
        pass(10 * MS_PER_MINUTE)
        viewModel.onIntent(PracticeIntent.StopClicked)
        runCurrent()
        assertTrue(viewModel.state.value.sheet is PracticeSheet.Summary)

        viewModel.onIntent(PracticeIntent.SummaryHidden)
        runCurrent()
        assertNull(viewModel.state.value.sheet)
        assertNotNull("still running", store.running.value)
        assertTrue(repository.entries.value.isEmpty())
    }

    @Test
    fun `finishing asked for from Live opens the summary once, even in a view model made for the ask`() = runTest {
        store.start(clock.millis() - 20 * MS_PER_MINUTE)
        val ask = FinishPracticeAsk().apply { ask() }
        val (viewModel, _) = viewModel(ask)
        runCurrent()
        val sheet = viewModel.state.value.sheet as PracticeSheet.Summary
        assertEquals(20, sheet.minutes)
        assertEquals(false, ask.asked.value)

        viewModel.onIntent(PracticeIntent.SummaryHidden)
        runCurrent()
        assertNull("the ask is forgotten, not repeated", viewModel.state.value.sheet)
    }

    @Test
    fun `the settings row of the profile stores the name and opens the settings`() = runTest {
        val (viewModel, effects) = viewModel()
        viewModel.onIntent(PracticeIntent.ProfileClicked)
        runCurrent()
        viewModel.onIntent(PracticeIntent.ProfileNameChanged("Аня"))
        viewModel.onIntent(PracticeIntent.ProfileSettingsClicked)
        runCurrent()
        assertNull(viewModel.state.value.sheet)
        assertEquals("Аня", profiles.profile.value.name)
        assertTrue(PracticeEffect.OpenSettings in effects)
    }

    @Test
    fun `months move back and never past the current one`() = runTest {
        val (viewModel, _) = viewModel()
        viewModel.onIntent(PracticeIntent.MonthForward)
        runCurrent()
        assertEquals(YearMonth.of(2026, 9), viewModel.state.value.month)
        viewModel.onIntent(PracticeIntent.MonthBack)
        viewModel.onIntent(PracticeIntent.MonthBack)
        runCurrent()
        assertEquals(YearMonth.of(2026, 7), viewModel.state.value.month)
        assertTrue(viewModel.state.value.canGoForward)
        viewModel.onIntent(PracticeIntent.MonthForward)
        runCurrent()
        assertEquals(YearMonth.of(2026, 8), viewModel.state.value.month)
    }

    @Test
    fun `days are selectable up to today`() = runTest {
        val (viewModel, _) = viewModel()
        viewModel.onIntent(PracticeIntent.DaySelected(LocalDate.of(2026, 9, 3)))
        runCurrent()
        assertEquals(LocalDate.of(2026, 9, 3), viewModel.state.value.selected.date)
        viewModel.onIntent(PracticeIntent.DaySelected(LocalDate.of(2026, 9, 18)))
        runCurrent()
        assertEquals(LocalDate.of(2026, 9, 3), viewModel.state.value.selected.date)
    }

    @Test
    fun `editing a day replaces its time with a manual entry`() = runTest {
        repository.add(PracticeEntry(LocalDate.of(2026, 9, 16), 1_000, 50 * MS_PER_MINUTE, manual = false))
        val (viewModel, _) = viewModel()
        viewModel.onIntent(PracticeIntent.DaySelected(LocalDate.of(2026, 9, 16)))
        viewModel.onIntent(PracticeIntent.EditTimeClicked)
        runCurrent()
        assertEquals(50, (viewModel.state.value.sheet as PracticeSheet.EditTime).minutes)
        viewModel.onIntent(PracticeIntent.EditTimeAdded(30))
        viewModel.onIntent(PracticeIntent.EditTimeStepped(+1))
        viewModel.onIntent(PracticeIntent.EditTimeSaved)
        runCurrent()
        val (date, duration, start) = repository.replacedDays.single()
        assertEquals(LocalDate.of(2026, 9, 16), date)
        assertEquals(85 * MS_PER_MINUTE, duration)
        assertEquals(LocalDate.of(2026, 9, 16).atTime(12, 0).atZone(zone).toInstant().toEpochMilli(), start)
        assertNull(viewModel.state.value.sheet)
        assertEquals(85 * MS_PER_MINUTE, viewModel.state.value.selected.totalMs)
    }

    @Test
    fun `clearing a day to zero and cancelling the sheet`() = runTest {
        repository.add(PracticeEntry(today, 1_000, 50 * MS_PER_MINUTE, manual = false))
        val (viewModel, _) = viewModel()
        viewModel.onIntent(PracticeIntent.EditTimeClicked)
        viewModel.onIntent(PracticeIntent.EditTimeCleared)
        viewModel.onIntent(PracticeIntent.EditTimeCancelled)
        runCurrent()
        assertEquals(50 * MS_PER_MINUTE, viewModel.state.value.todayMs)
        viewModel.onIntent(PracticeIntent.EditTimeClicked)
        viewModel.onIntent(PracticeIntent.EditTimeCleared)
        viewModel.onIntent(PracticeIntent.EditTimeSaved)
        runCurrent()
        assertEquals(0L, viewModel.state.value.todayMs)
        assertFalse(viewModel.state.value.hasHistory)
    }

    @Test
    fun `a session card opens the session`() = runTest {
        val (viewModel, effects) = viewModel()
        viewModel.onIntent(PracticeIntent.SessionClicked(7))
        runCurrent()
        assertEquals(listOf(PracticeEffect.OpenSession(7)), effects)
    }

    @Test
    fun `the header follows the entries, the trophies and the profile`() = runTest {
        val (viewModel, _) = viewModel()
        assertEquals(1, viewModel.state.value.header.level)
        assertEquals(listOf(TrophyBadge(1, given = false)), viewModel.state.value.header.trophyRow)

        repository.replaceDay(today, 12 * MS_PER_HOUR, startedAtEpochMs = 0)
        trophies.award(1, today)
        trophies.award(10, today)
        // Seen already: a trophy stands in the row only once its gift sheet is answered.
        trophies.markShown(1)
        trophies.markShown(10)
        profiles.setName("Даня")
        runCurrent()

        val header = viewModel.state.value.header
        assertEquals("Даня", header.name)
        assertEquals(12 * MS_PER_HOUR, header.totalMs)
        assertEquals(4, header.level)
        assertEquals(listOf(TrophyBadge(1, true), TrophyBadge(10, true), TrophyBadge(50, false)), header.trophyRow)
    }

    @Test
    fun `the name is stored when the profile sheet closes, cleaned and cut`() = runTest {
        val (viewModel, _) = viewModel()
        viewModel.onIntent(PracticeIntent.ProfileClicked)
        runCurrent()
        assertEquals(PracticeSheet.Profile(nameDraft = "", importingPhoto = false), viewModel.state.value.sheet)

        viewModel.onIntent(PracticeIntent.ProfileNameChanged("  Даня "))
        runCurrent()
        assertEquals("  Даня ", (viewModel.state.value.sheet as PracticeSheet.Profile).nameDraft)
        assertEquals("nothing is stored while typing", "", profiles.profile.value.name)

        viewModel.onIntent(PracticeIntent.ProfileNameChanged("я".repeat(30)))
        runCurrent()
        assertEquals(24, (viewModel.state.value.sheet as PracticeSheet.Profile).nameDraft.length)

        viewModel.onIntent(PracticeIntent.ProfileNameChanged(" Даня "))
        viewModel.onIntent(PracticeIntent.ProfileClosed)
        runCurrent()
        assertNull(viewModel.state.value.sheet)
        assertEquals("Даня", profiles.profile.value.name)
        assertEquals("Даня", viewModel.state.value.header.name)
    }

    @Test
    fun `the profile sheet opens with the stored name and an emptied field removes it`() = runTest {
        profiles.setName("Даня")
        val (viewModel, _) = viewModel()
        viewModel.onIntent(PracticeIntent.ProfileClicked)
        runCurrent()
        assertEquals("Даня", (viewModel.state.value.sheet as PracticeSheet.Profile).nameDraft)

        viewModel.onIntent(PracticeIntent.ProfileNameChanged(""))
        viewModel.onIntent(PracticeIntent.ProfileClosed)
        runCurrent()
        assertEquals("", profiles.profile.value.name)
    }

    @Test
    fun `a picked photo replaces the old one at once and the old file goes`() = runTest {
        val (viewModel, effects) = viewModel()
        viewModel.onIntent(PracticeIntent.ProfileClicked)
        viewModel.onIntent(PracticeIntent.ProfilePhotoPicked("content://first"))
        runCurrent()
        val first = profiles.profile.value.avatarFile
        assertEquals(setOf(first), avatarFiles.names)
        assertEquals(first, viewModel.state.value.header.avatarPath)
        assertFalse((viewModel.state.value.sheet as PracticeSheet.Profile).importingPhoto)

        viewModel.onIntent(PracticeIntent.ProfilePhotoPicked("content://second"))
        runCurrent()
        val second = profiles.profile.value.avatarFile
        assertTrue(second != null && second != first)
        assertEquals(setOf(second), avatarFiles.names)

        viewModel.onIntent(PracticeIntent.ProfilePhotoRemoved)
        runCurrent()
        assertNull(profiles.profile.value.avatarFile)
        assertNull(viewModel.state.value.header.avatarPath)
        assertTrue(avatarFiles.names.isEmpty())
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `a photo that cannot be read says so and keeps the old one`() = runTest {
        val (viewModel, effects) = viewModel()
        viewModel.onIntent(PracticeIntent.ProfileClicked)
        viewModel.onIntent(PracticeIntent.ProfilePhotoPicked("content://first"))
        runCurrent()
        val first = profiles.profile.value.avatarFile

        viewModel.onIntent(PracticeIntent.ProfilePhotoPicked("content://broken"))
        runCurrent()
        assertEquals(listOf<PracticeEffect>(PracticeEffect.ShowPhotoFailed), effects)
        assertEquals(first, profiles.profile.value.avatarFile)
        assertFalse((viewModel.state.value.sheet as PracticeSheet.Profile).importingPhoto)
    }

    @Test
    fun `a photo whose file is gone reads as no photo`() = runTest {
        profiles.setAvatarFile("avatar-lost.jpg")
        val (viewModel, _) = viewModel()
        assertNull(viewModel.state.value.header.avatarPath)
    }

    @Test
    fun `one sheet at a time - trophies do not open over another sheet`() = runTest {
        val (viewModel, _) = viewModel()
        viewModel.onIntent(PracticeIntent.TrophiesClicked)
        runCurrent()
        assertEquals(PracticeSheet.Trophies, viewModel.state.value.sheet)
        viewModel.onIntent(PracticeIntent.ProfileClicked)
        runCurrent()
        assertEquals(PracticeSheet.Trophies, viewModel.state.value.sheet)
        viewModel.onIntent(PracticeIntent.TrophiesClosed)
        runCurrent()
        assertNull(viewModel.state.value.sheet)
    }

    @Test
    fun `gifts come one after another, lowest first, and each thanks marks its trophy seen`() = runTest {
        trophies.award(10, today)
        trophies.award(1, today)
        val (viewModel, _) = viewModel()
        assertEquals(1, viewModel.state.value.gift?.hours)
        assertTrue(viewModel.state.value.header.trophyRow.none { it.given })

        viewModel.onIntent(PracticeIntent.GiftAccepted(1))
        runCurrent()
        assertEquals(10, viewModel.state.value.gift?.hours)
        assertEquals(listOf(TrophyBadge(1, true), TrophyBadge(10, false)), viewModel.state.value.header.trophyRow)

        viewModel.onIntent(PracticeIntent.GiftAccepted(10))
        runCurrent()
        assertNull(viewModel.state.value.gift)
        assertTrue(trophies.trophies.value.all { it.shown })
        assertEquals(listOf(TrophyBadge(1, true), TrophyBadge(10, true), TrophyBadge(50, false)), viewModel.state.value.header.trophyRow)
    }

    @Test
    fun `a gift waits while another sheet is open`() = runTest {
        val (viewModel, _) = viewModel()
        viewModel.onIntent(PracticeIntent.EditTimeClicked)
        runCurrent()
        trophies.award(1, today)
        runCurrent()
        assertNull("the edit sheet is still open", viewModel.state.value.gift)

        viewModel.onIntent(PracticeIntent.EditTimeCancelled)
        runCurrent()
        assertEquals(1, viewModel.state.value.gift?.hours)
    }

    @Test
    fun `a gift not answered comes back after the process dies`() = runTest {
        trophies.award(1, today)
        val (first, _) = viewModel()
        assertEquals(1, first.state.value.gift?.hours)

        val (second, _) = viewModel()
        assertEquals(Gift(hours = 1, index = 0, awardedDate = today), second.state.value.gift)
    }

    @Test
    fun `the journey window shows takts earned on the spot after their recap, not those earned before`() = runTest {
        journey.start(clock.millis())
        journey.earn(TaktEarning(clock.millis(), 100, 80, 600_000, 100))
        val (viewModel, effects) = viewModel()
        backgroundScope.launch { viewModel.journeyWindow.collect {} }
        runCurrent()

        val before = viewModel.journeyWindow.value!!
        assertEquals(100L, before.balance)
        assertEquals(200L, before.missing)
        assertNull(before.justEarned)
        assertNull(viewModel.state.value.sheet) // history is not recapped

        // saved elsewhere — the forgotten-practice prompt over this screen: the recap opens here all the same
        repository.add(PracticeEntry(today, clock.millis() - 2_280_000, 2_280_000, manual = false))
        journey.earn(TaktEarning(clock.millis(), 300, 264, 2_280_000, 340))
        runCurrent()
        val recap = (viewModel.state.value.sheet as PracticeSheet.Recap).recap
        assertEquals(340, recap.takts)
        assertEquals(2_280_000L, recap.durationMs)
        assertNull(viewModel.journeyWindow.value!!.justEarned) // the pill waits for the recap

        viewModel.onIntent(PracticeIntent.RecapClosed)
        runCurrent()
        assertNull(viewModel.state.value.sheet)
        val fresh = viewModel.journeyWindow.value!!
        assertEquals(340, fresh.justEarned)
        assertTrue(fresh.canDepart)

        advanceTimeBy(JourneyMotion.EARNED_PILL_MS + 1)
        runCurrent()
        assertNull(viewModel.journeyWindow.value!!.justEarned)
        assertEquals(440L, viewModel.journeyWindow.value!!.balance)

        viewModel.onIntent(PracticeIntent.JourneyClicked)
        runCurrent()
        assertEquals(PracticeEffect.OpenJourney, effects.last())
    }

    @Test
    fun `saving recaps the practice once, and the recap comes before the gift and the pill`() = runTest {
        journey.start(clock.millis())
        val (viewModel, _) = viewModel()
        backgroundScope.launch { viewModel.journeyWindow.collect {} }
        viewModel.onIntent(PracticeIntent.StartClicked)
        pass(62 * MS_PER_MINUTE)
        viewModel.onIntent(PracticeIntent.StopClicked)
        viewModel.onIntent(PracticeIntent.SummarySaved)
        trophies.award(1, today) // the hour the practice has just crossed
        runCurrent()

        val recap = (viewModel.state.value.sheet as PracticeSheet.Recap).recap
        assertEquals(124, recap.takts) // 62 minutes, no notes, no elements
        assertEquals(124, recap.sources.timeTakts)
        assertEquals(0, recap.sources.notesTakts)
        assertEquals(1, recap.streakDays)
        assertTrue(recap.streakExtended)
        assertNull(recap.dayTotalMs)
        assertEquals(1, journey.earnings.size)
        assertNull(viewModel.state.value.gift) // it waits for the recap

        viewModel.onIntent(PracticeIntent.RecapClosed)
        runCurrent()
        assertEquals(1, viewModel.state.value.gift?.hours)
        assertNull(viewModel.journeyWindow.value!!.justEarned) // and the pill waits for the gift

        viewModel.onIntent(PracticeIntent.GiftAccepted(1))
        runCurrent()
        assertEquals(124, viewModel.journeyWindow.value!!.justEarned)
        advanceTimeBy(JourneyMotion.EARNED_PILL_MS + 1)
        runCurrent()
        assertNull(viewModel.journeyWindow.value!!.justEarned)
        // the earning seen by the journey flow afterwards is the same practice: no second recap
        assertNull(viewModel.state.value.sheet)
    }

    @Test
    fun `«В дорогу» of the recap closes it and goes home`() = runTest {
        val (viewModel, effects) = viewModel()
        viewModel.onIntent(PracticeIntent.StartClicked)
        pass(10 * MS_PER_MINUTE)
        viewModel.onIntent(PracticeIntent.StopClicked)
        viewModel.onIntent(PracticeIntent.SummarySaved)
        runCurrent()
        assertTrue(viewModel.state.value.sheet is PracticeSheet.Recap)

        viewModel.onIntent(PracticeIntent.RecapTravelClicked)
        runCurrent()
        assertNull(viewModel.state.value.sheet)
        assertEquals(PracticeEffect.OpenHome, effects.last())
    }

    @Test
    fun `a discarded practice and one too short are not recapped`() = runTest {
        val (viewModel, _) = viewModel()
        viewModel.onIntent(PracticeIntent.StartClicked)
        pass(10 * MS_PER_MINUTE)
        viewModel.onIntent(PracticeIntent.StopClicked)
        viewModel.onIntent(PracticeIntent.SummaryDiscarded)
        runCurrent()
        assertNull(viewModel.state.value.sheet)

        viewModel.onIntent(PracticeIntent.StartClicked)
        pass(30_000)
        viewModel.onIntent(PracticeIntent.StopClicked)
        runCurrent()
        assertNull(viewModel.state.value.sheet)
        assertTrue(journey.earnings.isEmpty())
    }
}
