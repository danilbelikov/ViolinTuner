package com.example.violintuner.feature.live.block

import com.example.violintuner.core.domain.practice.FakeBlockStore
import com.example.violintuner.core.domain.practice.FakePieceBlockRepository
import com.example.violintuner.core.domain.practice.FakeRunningPracticeStore
import com.example.violintuner.core.domain.practice.PieceBlock
import com.example.violintuner.core.domain.practice.PracticeConfig
import com.example.violintuner.core.domain.repertoire.FakeRepertoireRepository
import com.example.violintuner.core.domain.repertoire.PieceDraft
import com.example.violintuner.core.domain.repertoire.PieceStatus
import com.example.violintuner.core.domain.session.FakeSessionRepository
import java.time.Clock
import java.time.Instant
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Blocks on Live (spec 3.28): the offer, the choice, the bookmark following the clock, stop, change, a deleted element. */
@OptIn(ExperimentalCoroutinesApi::class)
class BlockViewModelTest {
    private val zone: ZoneId = ZoneId.of("Europe/Moscow")
    private val min = 60_000L

    /** A clock the test moves by hand; the ticker's delays run on the test scheduler. */
    private class TestClock(var nowMs: Long, private val zone: ZoneId) : Clock() {
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = TestClock(nowMs, zone)
        override fun instant(): Instant = Instant.ofEpochMilli(nowMs)
    }

    private val clock = TestClock(Instant.parse("2026-09-22T15:00:00Z").toEpochMilli(), zone)
    private val practice = FakeRunningPracticeStore()
    private val blocks = FakeBlockStore()
    private val history = FakePieceBlockRepository()
    private val repertoire = FakeRepertoireRepository()

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): Pair<BlockViewModel, MutableList<BlockEffect>> {
        val viewModel = BlockViewModel(practice, blocks, history, repertoire, FakeSessionRepository(), PracticeConfig(), clock)
        val effects = mutableListOf<BlockEffect>()
        backgroundScope.launch { viewModel.state.collect {} }
        backgroundScope.launch { viewModel.effects.collect { effects += it } }
        runCurrent()
        return viewModel to effects
    }

    /** Moves the wall clock and the virtual time together, a second at a time. */
    private fun TestScope.pass(ms: Long) {
        runCurrent()
        var left = ms
        while (left > 0) {
            val slice = minOf(left, 1_000L)
            clock.nowMs += slice
            advanceTimeBy(slice)
            runCurrent()
            left -= slice
        }
        runCurrent()
    }

    private suspend fun add(title: String, composer: String = ""): Long =
        repertoire.add(PieceDraft(title = title, composer = composer, key = null, tempoBpm = null, status = PieceStatus.LEARNING, notes = ""), 0)

    @Test
    fun `without a practice the bookmark offers one, and the practice starts right here`() = runTest {
        add("Кайзер № 3")
        val (viewModel, _) = viewModel()
        assertEquals(Bookmark.Entry, viewModel.state.value.bookmark)
        viewModel.onIntent(BlockIntent.BookmarkClicked)
        runCurrent()
        assertEquals(BlockSheet.Offer, viewModel.state.value.sheet)

        viewModel.onIntent(BlockIntent.StartPracticeClicked)
        runCurrent()
        assertEquals(clock.nowMs, practice.running.value?.startedAtEpochMs)
        // the same sheet turns into the choice
        assertTrue(viewModel.state.value.sheet is BlockSheet.Picker)
    }

    @Test
    fun `a pick and a goal start a block, the bookmark counts down and is done at the goal`() = runTest {
        val kaiser = add("Кайзер № 3", composer = "Г. Кайзер")
        practice.start(clock.nowMs)
        val (viewModel, _) = viewModel()
        viewModel.onIntent(BlockIntent.BookmarkClicked)
        viewModel.onIntent(BlockIntent.PieceClicked(kaiser))
        runCurrent()
        assertEquals(10, (viewModel.state.value.sheet as BlockSheet.Picker).goalMinutes)
        viewModel.onIntent(BlockIntent.GoalStepped(+1))
        viewModel.onIntent(BlockIntent.StartClicked)
        runCurrent()

        assertNull(viewModel.state.value.sheet)
        assertEquals(PieceBlock(kaiser, clock.nowMs, 15 * min), blocks.blocks.value?.current)
        assertEquals(Bookmark.Running("Кайзер № 3", minutesLeft = 15, progress = 0f), viewModel.state.value.bookmark)
        pass(8 * min)
        assertEquals(7, (viewModel.state.value.bookmark as Bookmark.Running).minutesLeft)
        pass(7 * min)
        assertEquals(Bookmark.Done("Кайзер № 3"), viewModel.state.value.bookmark)
    }

    @Test
    fun `«Остановить» ends the block, a new pick ends the running one without a question`() = runTest {
        val scale = add("G-dur · 3 октавы")
        val minuet = add("Менуэт соль мажор")
        practice.start(clock.nowMs)
        val (viewModel, _) = viewModel()
        fun start(id: Long) {
            viewModel.onIntent(BlockIntent.BookmarkClicked)
            viewModel.onIntent(BlockIntent.PieceClicked(id))
            viewModel.onIntent(BlockIntent.StartClicked)
        }
        start(scale)
        runCurrent()
        pass(3 * min)
        start(minuet)
        runCurrent()
        assertEquals(listOf(PieceBlock(scale, clock.nowMs - 3 * min, 10 * min, endedAtEpochMs = clock.nowMs)), blocks.blocks.value?.finished)

        viewModel.onIntent(BlockIntent.BookmarkClicked)
        runCurrent()
        assertEquals("Менуэт соль мажор", (viewModel.state.value.sheet as BlockSheet.Picker).now?.title)
        viewModel.onIntent(BlockIntent.StopClicked)
        runCurrent()
        assertNull(blocks.blocks.value?.current)
        assertEquals(Bookmark.Entry, viewModel.state.value.bookmark)
        // the sheet stays: the next element is picked here
        assertNull((viewModel.state.value.sheet as BlockSheet.Picker).now)
    }

    @Test
    fun `the element whose block runs cannot be picked again`() = runTest {
        val scale = add("G-dur · 3 октавы")
        practice.start(clock.nowMs)
        val (viewModel, _) = viewModel()
        viewModel.onIntent(BlockIntent.BookmarkClicked)
        viewModel.onIntent(BlockIntent.PieceClicked(scale))
        viewModel.onIntent(BlockIntent.StartClicked)
        runCurrent()
        val first = blocks.blocks.value
        viewModel.onIntent(BlockIntent.BookmarkClicked)
        viewModel.onIntent(BlockIntent.PieceClicked(scale))
        viewModel.onIntent(BlockIntent.StartClicked)
        runCurrent()
        assertEquals(first, blocks.blocks.value)
    }

    @Test
    fun `a deleted element leaves the bookmark, and an empty repertoire leads to it`() = runTest {
        val scale = add("G-dur · 3 октавы")
        practice.start(clock.nowMs)
        val (viewModel, effects) = viewModel()
        viewModel.onIntent(BlockIntent.BookmarkClicked)
        viewModel.onIntent(BlockIntent.PieceClicked(scale))
        viewModel.onIntent(BlockIntent.StartClicked)
        runCurrent()
        repertoire.delete(scale)
        runCurrent()
        assertEquals(Bookmark.Entry, viewModel.state.value.bookmark)

        viewModel.onIntent(BlockIntent.BookmarkClicked)
        runCurrent()
        assertEquals(emptyList<PickerSection>(), (viewModel.state.value.sheet as BlockSheet.Picker).sections)
        viewModel.onIntent(BlockIntent.OpenRepertoireClicked)
        runCurrent()
        assertEquals(listOf(BlockEffect.OpenRepertoire), effects)
        assertNull(viewModel.state.value.sheet)
    }
}
