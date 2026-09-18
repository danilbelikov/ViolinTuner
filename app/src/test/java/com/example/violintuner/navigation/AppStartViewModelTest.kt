package com.example.violintuner.navigation

import com.example.violintuner.core.domain.practice.FakePracticeRepository
import com.example.violintuner.core.domain.practice.FakeRunningPracticeStore
import com.example.violintuner.core.domain.practice.PracticeConfig
import com.example.violintuner.core.domain.practice.PracticeConfig.Companion.MS_PER_HOUR
import com.example.violintuner.core.domain.practice.PracticeConfig.Companion.MS_PER_MINUTE
import com.example.violintuner.core.domain.practice.PracticeFinisher
import com.example.violintuner.core.domain.session.FakeSessionRepository
import com.example.violintuner.core.settings.FakeSettingsRepository
import com.example.violintuner.feature.practice.PracticePrompt
import com.example.violintuner.feature.practice.PracticePromptIntent
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppStartViewModelTest {
    private val zone: ZoneId = ZoneId.of("Europe/Moscow")
    // 2026-09-17 21:00 Moscow
    private val now = Instant.parse("2026-09-17T18:00:00Z").toEpochMilli()
    private val clock = Clock.fixed(Instant.ofEpochMilli(now), zone)
    private val store = FakeRunningPracticeStore()
    private val repository = FakePracticeRepository()
    private val config = PracticeConfig()

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = AppStartViewModel(
        FakeSettingsRepository(), FakeSessionRepository(), store, PracticeFinisher(repository, store, clock), config, clock,
    )

    private suspend fun running(elapsedMs: Long, lastSoundAgoMs: Long?) {
        store.start(now - elapsedMs)
        if (lastSoundAgoMs != null) store.markSound(now - lastSoundAgoMs)
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
        assertEquals(LocalDate.of(2026, 9, 17), entry.date)
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
}
