package com.example.violintuner.feature.history

import androidx.lifecycle.SavedStateHandle
import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.repertoire.FakeRepertoireRepository
import com.example.violintuner.core.domain.repertoire.PieceDraft
import com.example.violintuner.core.domain.session.FakeSessionRepository
import com.example.violintuner.core.domain.session.NewSession
import com.example.violintuner.core.domain.session.SessionAnalyzer
import com.example.violintuner.core.domain.session.SessionSample
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private val repository = FakeSessionRepository()
    private val repertoire = FakeRepertoireRepository()
    private val config = IntonationConfig()
    private val now = Instant.parse("2026-09-17T09:00:00Z")
    private val clock = Clock.fixed(now, ZoneId.of("Europe/Moscow"))

    private object NoFiles : com.example.violintuner.core.audio.recording.SessionAudioFiles {
        override fun newFile(): java.io.File = error("not used")
        override fun existing(name: String): java.io.File? = null
        override fun delete(name: String) = Unit
        override fun deleteOrphans(referenced: Set<String>, nowEpochMs: Long, minAgeMs: Long) = Unit
    }

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun save(daysAgo: Long, cents: Double = 1.0, pieceId: Long? = null): Long {
        val samples = List(60) { SessionSample(69, cents) }
        val analysis = SessionAnalyzer.analyze(samples, config)
        return repository.save(
            NewSession(
                startedAtEpochMs = now.minusSeconds(daysAgo * 86_400).toEpochMilli(), durationMs = 3_000, config = config,
                samples = samples, metrics = analysis.metrics!!,
                previewZones = SessionAnalyzer.previewZones(analysis.segments, config), audioPath = null, pieceId = pieceId,
            ),
        )
    }

    private val savedState = SavedStateHandle()

    private fun TestScope.viewModel(): HistoryViewModel {
        val viewModel = HistoryViewModel(savedState, repository, repertoire, config, clock, NoFiles)
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
        assertEquals(listOf(java.time.LocalDate.of(2026, 9, 17), java.time.LocalDate.of(2026, 9, 7)), state.cards.map { it.date })
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
        savedState[HistoryViewModel.OPEN_SECTION] = HistorySection.REPERTOIRE.name
        runCurrent()
        assertEquals(HistorySection.REPERTOIRE, viewModel.state.value.section)
        assertEquals(null, savedState.get<String>(HistoryViewModel.OPEN_SECTION))
        viewModel.onIntent(HistoryIntent.SectionSelected(HistorySection.SESSIONS))
        runCurrent()
        assertEquals(HistorySection.SESSIONS, viewModel.state.value.section)
    }
}
