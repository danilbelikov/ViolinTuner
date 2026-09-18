package com.example.violintuner.feature.history

import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.repertoire.FakeRepertoireRepository
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

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun save(daysAgo: Long, cents: Double = 1.0): Long {
        val samples = List(60) { SessionSample(69, cents) }
        val analysis = SessionAnalyzer.analyze(samples, config)
        return repository.save(
            NewSession(
                startedAtEpochMs = now.minusSeconds(daysAgo * 86_400).toEpochMilli(), durationMs = 3_000, config = config,
                samples = samples, metrics = analysis.metrics!!,
                previewZones = SessionAnalyzer.previewZones(analysis.segments, config), audioPath = null,
            ),
        )
    }

    private fun TestScope.viewModel(): HistoryViewModel {
        val viewModel = HistoryViewModel(repository, repertoire, config, clock)
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
        assertEquals(listOf(DayLabel.Today, DayLabel.On(java.time.LocalDate.of(2026, 9, 7))), state.cards.map { it.day })
        assertEquals(listOf(100, 0), state.cards.map { it.scorePercent })
        assertEquals(100, state.weeks.last().averageScore)
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
}
