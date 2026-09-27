package com.violinjourney.app.feature.history

import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.backing.BackingOutput
import com.violinjourney.app.core.domain.backing.FakeBackingRepository
import com.violinjourney.app.core.domain.backing.TakeBacking
import com.violinjourney.app.core.domain.repertoire.FakeRepertoireRepository
import com.violinjourney.app.core.domain.repertoire.PieceDraft
import com.violinjourney.app.core.domain.session.FakeSessionRepository
import com.violinjourney.app.core.domain.session.NewSession
import com.violinjourney.app.core.domain.session.SessionAnalyzer
import com.violinjourney.app.core.domain.session.SessionSample
import com.violinjourney.app.core.time.FixedWallClock
import com.violinjourney.app.core.time.WallClock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private val repository = FakeSessionRepository()
    private val repertoire = FakeRepertoireRepository()
    private val config = IntonationConfig()
    private val now = Instant.parse("2026-09-17T09:00:00Z")
    /**
     * Counts the reads of the time: the list is built with one read of "today" (HistoryViewModel.listed) and nothing else
     * of the view model asks the clock, so the reads count the builds. A StateFlow drops a state equal to the one it has:
     * comparing the cards could not tell a list built again from one left alone.
     */
    private class CountingClock(private val base: WallClock) : WallClock by base {
        var reads = 0
        override fun instant(): Instant = base.instant().also { reads++ }
    }

    private val clock = CountingClock(FixedWallClock(now, TimeZone.of("Europe/Moscow")))

    /** Answers for the files it is given, and counts how often it is asked. */
    private class Files(private val present: Map<String, Long> = emptyMap()) : com.violinjourney.app.core.audio.recording.SessionAudioFiles {
        var asked = 0
        override fun newFile(): java.io.File = error("not used")
        override fun existing(name: String): java.io.File? {
            asked++
            return present[name]?.let { size ->
                java.io.File.createTempFile("history", ".mp4").apply {
                    deleteOnExit()
                    writeBytes(ByteArray(size.toInt()))
                }
            }
        }
        override fun delete(name: String) = Unit
        override fun deleteOrphans(referenced: Set<String>, nowEpochMs: Long, minAgeMs: Long) = Unit
    }

    private var files = Files()
    private val backings = FakeBackingRepository()

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun save(daysAgo: Long, cents: Double = 1.0, pieceId: Long? = null): Long {
        val samples = List(60) { SessionSample(69, cents) }
        val analysis = SessionAnalyzer.analyze(samples, config)
        return repository.save(
            NewSession(
                startedAtEpochMs = (now - (daysAgo * 86_400).seconds).toEpochMilliseconds(), durationMs = 3_000, config = config,
                samples = samples, metrics = analysis.metrics!!,
                previewZones = SessionAnalyzer.previewZones(analysis.segments, config), audioPath = null, pieceId = pieceId,
            ),
        )
    }

    private val sectionAsk = HistorySectionAsk()

    private fun TestScope.viewModel(): HistoryViewModel {
        val viewModel = HistoryViewModel(repository, repertoire, config, clock, files, sectionAsk, backings, background = StandardTestDispatcher(testScheduler))
        backgroundScope.launch { viewModel.state.collect {} }
        return viewModel
    }

    @Test
    fun `starts loading, then shows what is stored`() = runTest {
        save(daysAgo = 0)
        save(daysAgo = 10, cents = 30.0)
        val viewModel = viewModel()
        assertTrue(viewModel.state.value.loading)
        runCurrent()
        val state = viewModel.state.value
        assertEquals(2, state.totalCount)
        assertEquals(listOf(LocalDate(2026, 9, 17), LocalDate(2026, 9, 7)), state.cards.map { it.date })
        assertEquals(1, state.days.last().count)
        assertEquals(2, state.days.sumOf { it.count })
    }

    @Test
    fun `filter narrows the cards only`() = runTest {
        save(daysAgo = 0)
        save(daysAgo = 10)
        val viewModel = viewModel()
        viewModel.onIntent(HistoryIntent.FilterSelected(HistoryFilter.THIS_WEEK))
        runCurrent()
        assertEquals(HistoryFilter.THIS_WEEK, viewModel.state.value.filter)
        assertEquals(1, viewModel.state.value.cards.size)
        assertEquals(2, viewModel.state.value.totalCount)
    }

    @Test
    fun `new and deleted sessions show up by themselves`() = runTest {
        val viewModel = viewModel()
        runCurrent()
        assertEquals(0, viewModel.state.value.totalCount)

        val id = save(daysAgo = 0)
        runCurrent()
        assertEquals(1, viewModel.state.value.totalCount)

        repository.delete(id)
        runCurrent()
        assertEquals(0, viewModel.state.value.totalCount)
    }

    @Test
    fun `tapping a card opens the session`() = runTest {
        val viewModel = viewModel()
        viewModel.onIntent(HistoryIntent.SessionClicked(42))
        assertEquals(HistoryEffect.OpenSession(42), viewModel.effects.first())
    }

    private fun HistoryViewModel.select(intent: SelectionIntent) = onIntent(HistoryIntent.Select(intent))

    @Test
    fun `picking cards lays the marks over the list instead of building it again`() = runTest {
        val first = save(daysAgo = 0)
        val video = save(daysAgo = 1)
        repository.sessions.value = repository.sessions.value.map { if (it.id == video) it.copy(videoPath = "take.mp4") else it }
        files = Files(present = mapOf("take.mp4" to 1_234))
        val viewModel = viewModel()
        runCurrent()
        val cards = viewModel.state.value.cards
        assertEquals(1_234L, cards.single { it.id == video }.videoBytes)
        val asked = files.asked
        val builds = clock.reads

        viewModel.select(SelectionIntent.CardLongPressed(first))
        viewModel.onIntent(HistoryIntent.SessionClicked(video))
        viewModel.onIntent(HistoryIntent.SessionClicked(first))
        viewModel.select(SelectionIntent.SelectAllClicked)
        runCurrent()

        assertEquals(setOf(first, video), viewModel.state.value.selection.ids)
        assertSame("the cards are the ones built before the picking", cards, viewModel.state.value.cards)
        assertEquals("the list was not built again", builds, clock.reads)
        assertEquals("no file was asked about again", asked, files.asked)
    }

    @Test
    fun `a take made under a backing says so on its card`() = runTest {
        val pieceId = repertoire.add(PieceDraft(title = "Концерт"), nowEpochMs = 1)
        val under = save(daysAgo = 0, pieceId = pieceId)
        val plain = save(daysAgo = 1, pieceId = pieceId)
        val viewModel = viewModel()
        runCurrent()
        assertTrue(viewModel.state.value.cards.none { it.underBacking })

        backings.saveTake(
            TakeBacking(
                sessionId = under, backingId = 1, offsetMs = 0, recordedOffsetMs = 0, gainDb = 0f, playedMs = 1_000,
                output = BackingOutput.WIRED, deviceName = null,
            ),
        )
        runCurrent()
        assertEquals(mapOf(under to true, plain to false), viewModel.state.value.cards.associate { it.id to it.underBacking })

        // the shift dragged on «Звук» changes nothing the list shows: it is not built again
        val builds = clock.reads
        backings.setTakeMix(under, offsetMs = 120, gainDb = -3f)
        runCurrent()
        assertEquals("a new shift of a take builds no list", builds, clock.reads)
    }

    @Test
    fun `a long press opens the selection and taps pick instead of opening`() = runTest {
        val first = save(daysAgo = 0)
        val second = save(daysAgo = 1)
        val viewModel = viewModel()
        runCurrent()

        viewModel.select(SelectionIntent.CardLongPressed(first))
        viewModel.onIntent(HistoryIntent.SessionClicked(second))
        runCurrent()

        assertEquals(Selection(active = true, ids = setOf(first, second)), viewModel.state.value.selection)
        assertTrue(viewModel.state.value.allSelected)
        // the tap inside the mode opened nothing: the first session to open is the one tapped after it
        viewModel.select(SelectionIntent.Closed)
        viewModel.onIntent(HistoryIntent.SessionClicked(first))
        assertEquals(HistoryEffect.OpenSession(first), viewModel.effects.first())
    }

    @Test
    fun `select all takes only what the filter shows`() = runTest {
        val recent = save(daysAgo = 0)
        save(daysAgo = 10)
        val viewModel = viewModel()
        viewModel.onIntent(HistoryIntent.FilterSelected(HistoryFilter.THIS_WEEK))
        runCurrent()

        viewModel.select(SelectionIntent.SelectClicked)
        viewModel.select(SelectionIntent.SelectAllClicked)
        runCurrent()

        assertEquals(setOf(recent), viewModel.state.value.selection.ids)
    }

    @Test
    fun `the filter and the section stay put while picking`() = runTest {
        save(daysAgo = 0)
        val viewModel = viewModel()
        runCurrent()
        viewModel.select(SelectionIntent.SelectClicked)

        viewModel.onIntent(HistoryIntent.FilterSelected(HistoryFilter.MONTH))
        viewModel.onIntent(HistoryIntent.SectionSelected(HistorySection.REPERTOIRE))
        runCurrent()

        assertEquals(HistoryFilter.ALL, viewModel.state.value.filter)
        assertEquals(HistorySection.SESSIONS, viewModel.state.value.section)
    }

    @Test
    fun `confirming deletes the picked ones and closes the mode`() = runTest {
        val first = save(daysAgo = 0)
        val kept = save(daysAgo = 1)
        val third = save(daysAgo = 2)
        val viewModel = viewModel()
        runCurrent()

        viewModel.select(SelectionIntent.CardLongPressed(first))
        viewModel.onIntent(HistoryIntent.SessionClicked(third))
        viewModel.select(SelectionIntent.DeleteClicked)
        runCurrent()
        assertTrue(viewModel.state.value.selection.confirming)

        viewModel.select(SelectionIntent.DeleteConfirmed)
        runCurrent()

        assertEquals(listOf(kept), viewModel.state.value.cards.map { it.id })
        assertEquals(Selection(), viewModel.state.value.selection)
    }

    @Test
    fun `dismissing the dialog and closing the mode delete nothing`() = runTest {
        val id = save(daysAgo = 0)
        val viewModel = viewModel()
        runCurrent()

        viewModel.select(SelectionIntent.CardLongPressed(id))
        viewModel.select(SelectionIntent.DeleteClicked)
        viewModel.select(SelectionIntent.DeleteDismissed)
        viewModel.select(SelectionIntent.Closed)
        runCurrent()

        assertEquals(1, viewModel.state.value.totalCount)
        assertEquals(Selection(), viewModel.state.value.selection)
    }

    @Test
    fun `a picked session deleted elsewhere drops out of the selection`() = runTest {
        val first = save(daysAgo = 0)
        val second = save(daysAgo = 1)
        val viewModel = viewModel()
        runCurrent()
        viewModel.select(SelectionIntent.CardLongPressed(first))
        viewModel.onIntent(HistoryIntent.SessionClicked(second))

        repository.delete(first)
        runCurrent()

        assertEquals(setOf(second), viewModel.state.value.selection.ids)
    }

    @Test
    fun `the best mark is set on a take, moves to another and is cleared by a second tap`() = runTest {
        val pieceId = repertoire.add(PieceDraft(title = "Менуэт"), nowEpochMs = 0)
        val first = save(daysAgo = 1, pieceId = pieceId)
        val second = save(daysAgo = 0, pieceId = pieceId)
        val free = save(daysAgo = 2)
        val viewModel = viewModel()
        runCurrent()
        fun best() = viewModel.state.value.cards.filter { it.best }.map { it.id }

        viewModel.onIntent(HistoryIntent.BestToggled(first))
        runCurrent()
        assertEquals(listOf(first), best())

        viewModel.onIntent(HistoryIntent.BestToggled(second))
        runCurrent()
        assertEquals(listOf(second), best())
        // the list of «Записи» stays in the order of time: the mark pins a take on the screen of its piece only
        assertEquals(listOf(second, first, free), viewModel.state.value.cards.map { it.id })

        viewModel.onIntent(HistoryIntent.BestToggled(second))
        viewModel.onIntent(HistoryIntent.BestToggled(free)) // not a take: nothing to mark
        runCurrent()
        assertEquals(emptyList<Long>(), best())
    }

    @Test
    fun `a section asked for from Live opens once, and the tab stays free afterwards`() = runTest {
        val viewModel = viewModel()
        runCurrent()
        assertEquals(HistorySection.SESSIONS, viewModel.state.value.section)
        sectionAsk.ask(HistorySection.REPERTOIRE)
        runCurrent()
        assertEquals(HistorySection.REPERTOIRE, viewModel.state.value.section)
        assertEquals(null, sectionAsk.asked.value)
        viewModel.onIntent(HistoryIntent.SectionSelected(HistorySection.SESSIONS))
        runCurrent()
        assertEquals(HistorySection.SESSIONS, viewModel.state.value.section)
    }
}
