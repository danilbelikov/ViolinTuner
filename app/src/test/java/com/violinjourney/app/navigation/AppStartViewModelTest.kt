package com.violinjourney.app.navigation

import com.violinjourney.app.core.audio.playback.FakeSessionWaveforms
import com.violinjourney.app.core.audio.share.FakeShareFiles
import com.violinjourney.app.core.data.Housekeeping
import com.violinjourney.app.core.data.profile.FakeAvatarFiles
import com.violinjourney.app.core.domain.backing.NoBackings
import com.violinjourney.app.core.domain.practice.BlockRules
import com.violinjourney.app.core.domain.practice.FakeBlockStore
import com.violinjourney.app.core.domain.practice.FakePracticeRepository
import com.violinjourney.app.core.domain.practice.FakeRunningPracticeStore
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_HOUR
import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_MINUTE
import com.violinjourney.app.core.domain.practice.PracticeFinisher
import com.violinjourney.app.core.domain.practice.PracticeStats
import com.violinjourney.app.core.domain.practice.testPracticeFinisher
import com.violinjourney.app.core.domain.progress.FakeProfileRepository
import com.violinjourney.app.core.domain.progress.FakeTrophyRepository
import com.violinjourney.app.core.domain.progress.ProgressConfig
import com.violinjourney.app.core.domain.progress.Trophy
import com.violinjourney.app.core.domain.progress.TrophyAwarder
import com.violinjourney.app.core.domain.repertoire.FakeRepertoireRepository
import com.violinjourney.app.core.domain.repertoire.PieceDraft
import com.violinjourney.app.core.domain.session.FakeSessionRepository
import com.violinjourney.app.core.settings.FakeSettingsRepository
import com.violinjourney.app.core.time.WallClock
import com.violinjourney.app.feature.practice.PlayedLine
import com.violinjourney.app.feature.practice.PracticePrompt
import com.violinjourney.app.feature.practice.PracticePromptEffect
import com.violinjourney.app.feature.practice.PracticePromptIntent
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppStartViewModelTest {
    private val zone: TimeZone = TimeZone.of("Europe/Moscow")
    // 2026-09-17 21:00 Moscow
    private val now = Instant.parse("2026-09-17T18:00:00Z").toEpochMilliseconds()

    /** Stands at [now] until a test moves it: a dialog left on screen for hours. */
    private class MovingClock(var nowMs: Long, override val zone: TimeZone) : WallClock {
        override fun instant(): Instant = Instant.fromEpochMilliseconds(nowMs)
    }

    private val clock = MovingClock(now, zone)
    private val store = FakeRunningPracticeStore()
    private val repository = FakePracticeRepository()
    private val config = PracticeConfig()

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val trophies = FakeTrophyRepository()
    private val profile = FakeProfileRepository()
    private val avatarFiles = FakeAvatarFiles()
    private val repertoire = FakeRepertoireRepository()
    private val blocks = FakeBlockStore()
    private val shareFiles = FakeShareFiles()
    private val keptPcm = mutableListOf<Set<String>>()
    private val backingPcm = object : com.violinjourney.app.core.audio.backing.BackingPcm {
        override fun cached(backing: com.violinjourney.app.core.domain.backing.Backing, sampleRate: Int): java.io.File? = null
        override fun prepare(backing: com.violinjourney.app.core.domain.backing.Backing, sampleRate: Int): java.io.File? = null
        override fun deleteOrphans(keptFiles: Set<String>) {
            keptPcm += keptFiles
        }
    }

    private fun viewModel(
        finisher: PracticeFinisher = testPracticeFinisher(repository, store, clock),
        config: PracticeConfig = this.config,
    ) = AppStartViewModel(
        FakeSettingsRepository(), store, finisher, config, clock, repository, TrophyAwarder(trophies, ProgressConfig(), clock), repertoire,
        Housekeeping(
            FakeSessionRepository(), FakeSessionWaveforms(), avatarFiles, profile, shareFiles, repertoire, NoBackings, backingPcm, clock,
            io = Dispatchers.Main,
        ),
        blocks,
    )

    private suspend fun running(elapsedMs: Long, lastSoundAgoMs: Long?) {
        store.startIfIdle(now - elapsedMs)
        if (lastSoundAgoMs != null) store.markSound(now - lastSoundAgoMs)
    }

    @Test
    fun `the forgotten-practice prompt steps by the injected config`() = runTest {
        assertEquals(10, viewModel(config = PracticeConfig(editStepMinutes = 10)).promptStepMinutes)
        assertEquals(PracticeConfig().editStepMinutes, viewModel().promptStepMinutes)
    }

    @Test
    fun `temporary files are swept at the start and every time the app goes away`() = runTest {
        val viewModel = viewModel()
        runCurrent()
        assertEquals(1, shareFiles.sweeps)

        viewModel.onAppStopped()
        runCurrent()
        assertEquals(2, shareFiles.sweeps)
        assertEquals(now, shareFiles.sweptAtMs)
    }

    @Test
    fun `the prepared sound of backings is kept while the backing is and swept with it, at the start and when the app goes`() = runTest {
        val viewModel = viewModel()
        runCurrent()
        assertEquals(1, keptPcm.size)
        viewModel.onAppStopped()
        runCurrent()
        assertEquals(2, keptPcm.size)
    }

    @Test
    fun `no practice or a live one asks nothing`() = runTest {
        val viewModel = viewModel()
        viewModel.onAppOpened()
        runCurrent()
        assertNull(viewModel.practicePrompt.value)

        running(elapsedMs = 3 * MS_PER_HOUR, lastSoundAgoMs = 10 * MS_PER_MINUTE)
        viewModel.onAppOpened()
        runCurrent()
        assertNull(viewModel.practicePrompt.value)
    }

    @Test
    fun `a quiet hour brings the dialog, ending at the last sound saves up to it`() = runTest {
        running(elapsedMs = 3 * MS_PER_HOUR + 12 * MS_PER_MINUTE, lastSoundAgoMs = 2 * MS_PER_HOUR)
        val viewModel = viewModel()
        viewModel.onAppOpened()
        runCurrent()
        assertEquals(
            PracticePrompt.Forgotten(3 * MS_PER_HOUR + 12 * MS_PER_MINUTE, lastSoundEpochMs = now - 2 * MS_PER_HOUR),
            viewModel.practicePrompt.value,
        )

        // Live keeps listening under the dialog: a note heard meanwhile must not move the end
        store.markSound(now)
        viewModel.onPromptIntent(PracticePromptIntent.EndAtLastSound)
        runCurrent()
        val entry = repository.entries.value.single()
        assertEquals(MS_PER_HOUR + 12 * MS_PER_MINUTE, entry.durationMs)
        assertEquals(LocalDate(2026, 9, 17), entry.date)
        assertNull(store.running.value)
        assertNull(viewModel.practicePrompt.value)
    }

    @Test
    fun `ending now saves the whole time`() = runTest {
        running(elapsedMs = 2 * MS_PER_HOUR, lastSoundAgoMs = 90 * MS_PER_MINUTE)
        val viewModel = viewModel()
        viewModel.onAppOpened()
        viewModel.onPromptIntent(PracticePromptIntent.EndNow)
        runCurrent()
        assertEquals(2 * MS_PER_HOUR, repository.entries.value.single().durationMs)
        assertNull(store.running.value)
    }

    @Test
    fun `continuing keeps the practice and counts as a sign of life`() = runTest {
        running(elapsedMs = 2 * MS_PER_HOUR, lastSoundAgoMs = 90 * MS_PER_MINUTE)
        val viewModel = viewModel()
        viewModel.onAppOpened()
        viewModel.onPromptIntent(PracticePromptIntent.Continue)
        runCurrent()
        assertNull(viewModel.practicePrompt.value)
        assertEquals(now, store.running.value!!.lastSoundEpochMs)
        assertTrue(repository.entries.value.isEmpty())
        viewModel.onAppOpened()
        runCurrent()
        assertNull("no second question right away", viewModel.practicePrompt.value)
    }

    @Test
    fun `without any sound the dialog offers to set the time through the summary sheet`() = runTest {
        running(elapsedMs = 2 * MS_PER_HOUR, lastSoundAgoMs = null)
        val viewModel = viewModel()
        viewModel.onAppOpened()
        runCurrent()
        assertEquals(PracticePrompt.Forgotten(2 * MS_PER_HOUR, null), viewModel.practicePrompt.value)

        viewModel.onPromptIntent(PracticePromptIntent.EditTime)
        runCurrent()
        val summary = viewModel.practicePrompt.value as PracticePrompt.Summary
        assertEquals(120, summary.sheet.minutes)
        // a rotation while the sheet is up must not bring the dialog back
        viewModel.onAppOpened()
        runCurrent()
        assertTrue(viewModel.practicePrompt.value is PracticePrompt.Summary)

        repeat(3) { viewModel.onPromptIntent(PracticePromptIntent.SummaryStepped(-1)) }
        viewModel.onPromptIntent(PracticePromptIntent.SummarySaved)
        runCurrent()
        assertEquals(105 * MS_PER_MINUTE, repository.entries.value.single().durationMs)
        assertNull(store.running.value)
        assertNull(viewModel.practicePrompt.value)
    }

    @Test
    fun `past the limit the practice has ended by itself and the sheet reports it`() = runTest {
        running(elapsedMs = 14 * MS_PER_HOUR, lastSoundAgoMs = 9 * MS_PER_HOUR)
        val viewModel = viewModel()
        viewModel.onAppOpened()
        runCurrent()
        val summary = viewModel.practicePrompt.value as PracticePrompt.Summary
        assertEquals(5 * 60, summary.sheet.minutes)
        assertEquals(now - 14 * MS_PER_HOUR, summary.sheet.startedAtEpochMs)

        viewModel.onPromptIntent(PracticePromptIntent.SummaryDiscarded)
        runCurrent()
        assertTrue(repository.entries.value.isEmpty())
        assertNull(store.running.value)
        assertNull(viewModel.practicePrompt.value)
    }

    @Test
    fun `swiping the summary away only hides it - the practice stays and the question comes back`() = runTest {
        running(elapsedMs = 14 * MS_PER_HOUR, lastSoundAgoMs = 9 * MS_PER_HOUR)
        val viewModel = viewModel()
        viewModel.onAppOpened()
        runCurrent()
        assertTrue(viewModel.practicePrompt.value is PracticePrompt.Summary)

        viewModel.onPromptIntent(PracticePromptIntent.SummaryHidden)
        runCurrent()
        assertNull(viewModel.practicePrompt.value)
        assertNotNull(store.running.value)
        assertTrue(repository.entries.value.isEmpty())

        viewModel.onAppOpened()
        runCurrent()
        assertTrue(viewModel.practicePrompt.value is PracticePrompt.Summary)
    }

    @Test
    fun `two quick answers to the forgotten practice store it once`() = runTest {
        running(elapsedMs = 2 * MS_PER_HOUR, lastSoundAgoMs = 90 * MS_PER_MINUTE)
        // a row takes a moment to write, as in the database: the second tap lands while the first one writes
        val slow = FakePracticeRepository(addDelayMs = 1)
        val viewModel = viewModel(testPracticeFinisher(slow, store, clock))
        viewModel.onAppOpened()
        runCurrent()

        viewModel.onPromptIntent(PracticePromptIntent.EndNow)
        viewModel.onPromptIntent(PracticePromptIntent.EndNow)
        advanceTimeBy(10)
        runCurrent()

        assertEquals(1, slow.entries.value.size)
        assertNull(store.running.value)
        assertNull(viewModel.practicePrompt.value)
    }

    @Test
    fun `an answer to a practice that was already ended elsewhere stores nothing`() = runTest {
        running(elapsedMs = 2 * MS_PER_HOUR, lastSoundAgoMs = 90 * MS_PER_MINUTE)
        val viewModel = viewModel()
        viewModel.onAppOpened()
        runCurrent()
        store.clear() // the practice screen saved or discarded it meanwhile
        viewModel.onPromptIntent(PracticePromptIntent.EndNow)
        runCurrent()
        assertTrue(repository.entries.value.isEmpty())
        assertNull(viewModel.practicePrompt.value)
    }

    private val today: LocalDate = LocalDate.parse("2026-09-17")

    private suspend fun setDay(date: LocalDate, hours: Int) =
        repository.replaceDay(date, hours * MS_PER_HOUR, PracticeStats.manualStartOf(date, zone))

    @Test
    fun `hours already practised before the update bring their trophies at once`() = runTest {
        setDay(today.minus(3, DateTimeUnit.DAY), hours = 12)
        viewModel()
        runCurrent()
        assertEquals(listOf(Trophy(1, today, shown = false), Trophy(10, today, shown = false)), trophies.trophies.value)
    }

    @Test
    fun `a practice saved from the forgotten prompt gives its trophy`() = runTest {
        running(elapsedMs = 90 * MS_PER_MINUTE, lastSoundAgoMs = 70 * MS_PER_MINUTE)
        val viewModel = viewModel()
        viewModel.onAppOpened()
        runCurrent()
        assertTrue(trophies.trophies.value.isEmpty())

        viewModel.onPromptIntent(PracticePromptIntent.EndNow)
        runCurrent()
        assertEquals(listOf(1), trophies.trophies.value.map { it.hours })
    }

    @Test
    fun `editing a day up gives every mark passed, editing it down takes nothing away`() = runTest {
        viewModel()
        runCurrent()
        setDay(today, hours = 12)
        setDay(today.minus(1, DateTimeUnit.DAY), hours = 12)
        setDay(today.minus(2, DateTimeUnit.DAY), hours = 12)
        setDay(today.minus(3, DateTimeUnit.DAY), hours = 12)
        setDay(today.minus(4, DateTimeUnit.DAY), hours = 12)
        runCurrent()
        assertEquals(listOf(1, 10, 50), trophies.trophies.value.map { it.hours })

        trophies.markShown(1)
        setDay(today, hours = 0)
        setDay(today.minus(1, DateTimeUnit.DAY), hours = 0)
        runCurrent()
        assertEquals(listOf(1, 10, 50), trophies.trophies.value.map { it.hours })
        assertEquals(listOf(true, false, false), trophies.trophies.value.map { it.shown })
    }

    @Test
    fun `photos nobody points at are removed on start, the current one stays`() = runTest {
        val old = avatarFiles.import("content://old")!!
        val current = avatarFiles.import("content://current")!!
        profile.setAvatarFile(current)
        viewModel()
        runCurrent()
        assertNull(avatarFiles.existing(old))
        assertEquals(setOf(current), avatarFiles.names)
    }

    @Test
    fun `sheet photos nobody points at are cleaned up on start`() = runTest {
        viewModel()
        runCurrent()
        assertEquals(1, repertoire.orphanCleanups)
    }

    @Test
    fun `the sheet of a practice that ended by itself lists what was played, cut where the practice ended`() = runTest {
        val scale = repertoire.add(PieceDraft(title = "G-dur · 3 октавы"), 0)
        val minuet = repertoire.add(PieceDraft(title = "Менуэт соль мажор"), 0)
        running(elapsedMs = 14 * MS_PER_HOUR, lastSoundAgoMs = null)
        val start = now - 14 * MS_PER_HOUR
        // the scale for its ten minutes; the minuet left running for hours, its goal long passed by the end
        blocks.update { BlockRules.started(it, start, scale, 10 * MS_PER_MINUTE, start) }
        blocks.update { BlockRules.started(it, start, minuet, 30 * MS_PER_MINUTE, start + 10 * MS_PER_MINUTE) }
        val viewModel = viewModel()
        viewModel.onAppOpened()
        runCurrent()
        // no sound at all: the practice ends an hour after its start, and so do its blocks
        val summary = viewModel.practicePrompt.value as PracticePrompt.Summary
        assertEquals(
            listOf(PlayedLine("G-dur · 3 октавы", 10, 10, done = true), PlayedLine("Менуэт соль мажор", 30, 30, done = true)),
            summary.sheet.played,
        )
        // five minutes on the stepper: the minuet begun at ten no longer fits
        repeat(11) { viewModel.onPromptIntent(PracticePromptIntent.SummaryStepped(-1)) }
        runCurrent()
        assertEquals(listOf(PlayedLine("G-dur · 3 октавы", 5, 10, done = false)), (viewModel.practicePrompt.value as PracticePrompt.Summary).sheet.played)
    }

    // A dialog left on screen (spec 3.12): it follows the clock, and past the limit the practice has ended by itself.

    private fun AppStartViewModel.effects(scope: kotlinx.coroutines.test.TestScope): MutableList<PracticePromptEffect> {
        val seen = mutableListOf<PracticePromptEffect>()
        scope.backgroundScope.launch { promptEffects.collect { seen += it } }
        return seen
    }

    @Test
    fun `a dialog left open follows the clock`() = runTest {
        running(elapsedMs = 2 * MS_PER_HOUR, lastSoundAgoMs = 90 * MS_PER_MINUTE)
        val viewModel = viewModel()
        viewModel.onAppOpened()
        runCurrent()
        clock.nowMs += 30 * MS_PER_MINUTE
        viewModel.onAppOpened()
        runCurrent()
        assertEquals(
            PracticePrompt.Forgotten(2 * MS_PER_HOUR + 30 * MS_PER_MINUTE, lastSoundEpochMs = now - 90 * MS_PER_MINUTE),
            viewModel.practicePrompt.value,
        )
    }

    @Test
    fun `a dialog found again past twelve hours becomes the sheet of the practice that ended by itself`() = runTest {
        running(elapsedMs = 2 * MS_PER_HOUR, lastSoundAgoMs = 90 * MS_PER_MINUTE) // the violin sounded at its 30th minute
        val viewModel = viewModel()
        viewModel.onAppOpened()
        runCurrent()
        assertTrue(viewModel.practicePrompt.value is PracticePrompt.Forgotten)

        clock.nowMs += 11 * MS_PER_HOUR
        viewModel.onAppOpened()
        runCurrent()
        assertEquals(30, (viewModel.practicePrompt.value as PracticePrompt.Summary).sheet.minutes)
    }

    @Test
    fun `«Закончить сейчас» past twelve hours ends at the last sound`() = runTest {
        running(elapsedMs = 2 * MS_PER_HOUR, lastSoundAgoMs = 90 * MS_PER_MINUTE)
        val viewModel = viewModel()
        viewModel.onAppOpened()
        runCurrent()
        clock.nowMs += 11 * MS_PER_HOUR
        viewModel.onPromptIntent(PracticePromptIntent.EndNow)
        runCurrent()
        assertEquals(30 * MS_PER_MINUTE, repository.entries.value.single().durationMs)
    }

    @Test
    fun `«Изменить время» of a dialog left past twelve hours opens at an hour`() = runTest {
        running(elapsedMs = 2 * MS_PER_HOUR, lastSoundAgoMs = null)
        val viewModel = viewModel()
        viewModel.onAppOpened()
        runCurrent()
        clock.nowMs += 11 * MS_PER_HOUR
        viewModel.onPromptIntent(PracticePromptIntent.EditTime)
        runCurrent()
        assertEquals(60, (viewModel.practicePrompt.value as PracticePrompt.Summary).sheet.minutes)
    }

    @Test
    fun `«Продолжаю заниматься» past twelve hours brings the sheet and no sign of life`() = runTest {
        running(elapsedMs = 2 * MS_PER_HOUR, lastSoundAgoMs = 90 * MS_PER_MINUTE)
        val viewModel = viewModel()
        viewModel.onAppOpened()
        runCurrent()
        clock.nowMs += 11 * MS_PER_HOUR
        viewModel.onPromptIntent(PracticePromptIntent.Continue)
        runCurrent()
        assertEquals(30, (viewModel.practicePrompt.value as PracticePrompt.Summary).sheet.minutes)
        assertEquals(now - 90 * MS_PER_MINUTE, store.running.value!!.lastSoundEpochMs)
    }

    @Test
    fun `an expired practice under a minute is not kept and says so`() = runTest {
        store.startIfIdle(now - 14 * MS_PER_HOUR)
        store.markSound(now - 14 * MS_PER_HOUR + 30_000)
        val viewModel = viewModel()
        val effects = viewModel.effects(this)
        viewModel.onAppOpened()
        runCurrent()
        assertNull(viewModel.practicePrompt.value)
        assertNull(store.running.value)
        assertTrue(repository.entries.value.isEmpty())
        assertEquals(listOf<PracticePromptEffect>(PracticePromptEffect.ShowTooShort), effects)
    }

    @Test
    fun `ending at a last sound under a minute says so`() = runTest {
        store.startIfIdle(now - 2 * MS_PER_HOUR)
        store.markSound(now - 2 * MS_PER_HOUR + 40_000)
        val viewModel = viewModel()
        val effects = viewModel.effects(this)
        viewModel.onAppOpened()
        runCurrent()
        viewModel.onPromptIntent(PracticePromptIntent.EndAtLastSound)
        runCurrent()
        assertTrue(repository.entries.value.isEmpty())
        assertNull(store.running.value)
        assertNull(viewModel.practicePrompt.value)
        assertEquals(listOf<PracticePromptEffect>(PracticePromptEffect.ShowTooShort), effects)
    }

    @Test
    fun `the sheet of a practice that ended by itself lists only the blocks of its own practice`() = runTest {
        val scale = repertoire.add(PieceDraft(title = "G-dur · 3 октавы"), 0)
        running(elapsedMs = 14 * MS_PER_HOUR, lastSoundAgoMs = null)
        // an earlier practice whose blocks were left behind
        val other = now - 20 * MS_PER_HOUR
        blocks.update { BlockRules.started(it, other, scale, 10 * MS_PER_MINUTE, other) }
        val viewModel = viewModel()
        viewModel.onAppOpened()
        runCurrent()
        assertTrue((viewModel.practicePrompt.value as PracticePrompt.Summary).sheet.played.isEmpty())
    }
}
