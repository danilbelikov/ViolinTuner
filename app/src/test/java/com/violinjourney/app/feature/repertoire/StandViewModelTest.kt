package com.violinjourney.app.feature.repertoire

import androidx.lifecycle.SavedStateHandle
import com.violinjourney.app.core.data.repertoire.FakeSheetFiles
import com.violinjourney.app.core.domain.repertoire.FakeRepertoireRepository
import com.violinjourney.app.core.domain.repertoire.FakeStandHintStore
import com.violinjourney.app.core.domain.repertoire.PieceDraft
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.domain.repertoire.StandHintStore
import com.violinjourney.app.core.time.FixedWallClock
import com.violinjourney.app.core.time.WallClock
import com.violinjourney.app.feature.repertoire.stand.StandEffect
import com.violinjourney.app.feature.repertoire.stand.StandIntent
import com.violinjourney.app.feature.repertoire.stand.StandViewModel
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StandViewModelTest {
    private val repertoire = FakeRepertoireRepository()
    private val files = FakeSheetFiles()
    // the stand seen before: the hint of the first visit is its own tests' business
    private val hints = FakeStandHintStore(seen = true)
    private val clock: WallClock = FixedWallClock(Instant.fromEpochMilliseconds(50_000), TimeZone.UTC)

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun pieceWithPages(count: Int): Long {
        val id = repertoire.add(PieceDraft(title = "Менуэт"), nowEpochMs = 1)
        repeat(count) {
            val stored = checkNotNull(files.import("content://photo/$it"))
            repertoire.addPage(id, stored.fileName, stored.thumbFileName, nowEpochMs = 2)
        }
        return id
    }

    private fun TestScope.stand(pieceId: Long, page: Int = 0, hints: StandHintStore = this@StandViewModelTest.hints): Pair<StandViewModel, MutableList<StandEffect>> {
        val viewModel = StandViewModel(
            SavedStateHandle(mapOf(StandViewModel.ARG_PIECE_ID to pieceId, StandViewModel.ARG_PAGE to page)),
            repertoire, files, hints, RepertoireConfig(), clock,
        )
        val effects = mutableListOf<StandEffect>()
        backgroundScope.launch { viewModel.effects.collect { effects += it } }
        runCurrent()
        return viewModel to effects
    }

    @Test
    fun `the pages stand in the order they were added and open where the strip was tapped`() = runTest {
        val (viewModel, _) = stand(pieceWithPages(3), page = 1)
        val state = viewModel.state.value
        assertFalse(state.loading)
        assertEquals(listOf("/sheets/page-1.jpg", "/sheets/page-2.jpg", "/sheets/page-3.jpg"), state.pages.map { it.path })
        assertEquals(1, state.initialPage)
    }

    @Test
    fun `a page asked for beyond the last one opens the last one, and a lost file is blank paper`() = runTest {
        val id = pieceWithPages(2)
        files.names -= "page-2.jpg"
        val (viewModel, _) = stand(id, page = 7)
        assertEquals(1, viewModel.state.value.initialPage)
        assertNull(viewModel.state.value.pages[1].path)
    }

    @Test
    fun `the panel leaves after three seconds without a touch and comes back on a tap in the middle`() = runTest {
        val (viewModel, _) = stand(pieceWithPages(2))
        assertTrue(viewModel.state.value.panelVisible)
        advanceTimeBy(2_999)
        assertTrue(viewModel.state.value.panelVisible)
        advanceTimeBy(2)
        assertFalse(viewModel.state.value.panelVisible)

        viewModel.onIntent(StandIntent.PanelToggled)
        assertTrue(viewModel.state.value.panelVisible)
        advanceTimeBy(3_001)
        assertFalse(viewModel.state.value.panelVisible)
    }

    @Test
    fun `a touch gives the panel three more seconds, and a tap in the middle sends it away at once`() = runTest {
        val (viewModel, _) = stand(pieceWithPages(2))
        advanceTimeBy(2_500)
        viewModel.onIntent(StandIntent.Touched)
        advanceTimeBy(2_500)
        assertTrue(viewModel.state.value.panelVisible)
        viewModel.onIntent(StandIntent.PanelToggled)
        assertFalse(viewModel.state.value.panelVisible)
        viewModel.onIntent(StandIntent.Touched)
        advanceTimeBy(10_000)
        assertFalse("a touch does not bring a hidden panel back", viewModel.state.value.panelVisible)
    }

    @Test
    fun `the panel does not leave from under the delete dialog`() = runTest {
        val (viewModel, _) = stand(pieceWithPages(2))
        viewModel.onIntent(StandIntent.DeleteClicked)
        advanceTimeBy(10_000)
        assertTrue(viewModel.state.value.deleteDialog && viewModel.state.value.panelVisible)
        viewModel.onIntent(StandIntent.DeleteDismissed)
        assertEquals(2, viewModel.state.value.pages.size)
        advanceTimeBy(3_001)
        assertFalse(viewModel.state.value.panelVisible)
    }

    @Test
    fun `removing a page takes the one on the stand, with its files, and counts as activity`() = runTest {
        val id = pieceWithPages(3)
        val (viewModel, effects) = stand(id)
        viewModel.onIntent(StandIntent.PageSettled(1))
        viewModel.onIntent(StandIntent.DeleteClicked)
        viewModel.onIntent(StandIntent.DeleteConfirmed)
        runCurrent()
        assertEquals(listOf("/sheets/page-1.jpg", "/sheets/page-3.jpg"), viewModel.state.value.pages.map { it.path })
        assertFalse(viewModel.state.value.deleteDialog)
        assertEquals(50_000L, repertoire.piece(id)!!.updatedAtEpochMs)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `removing the last page closes the stand, once`() = runTest {
        val (viewModel, effects) = stand(pieceWithPages(1))
        viewModel.onIntent(StandIntent.DeleteClicked)
        viewModel.onIntent(StandIntent.DeleteConfirmed)
        runCurrent()
        viewModel.onIntent(StandIntent.BackClicked)
        runCurrent()
        assertEquals(listOf<StandEffect>(StandEffect.Close), effects)
    }

    @Test
    fun `the zones are outlined on the very first visit and never again`() = runTest {
        val id = pieceWithPages(2)
        val unseen = FakeStandHintStore()
        val (first, _) = stand(id, hints = unseen)
        assertTrue(first.state.value.showHint)
        first.onIntent(StandIntent.HintShown)
        runCurrent()
        assertFalse(first.state.value.showHint)

        val (second, _) = stand(id, hints = unseen)
        assertFalse(second.state.value.showHint)
        assertTrue("a visit after the first opens with the panel, as ever", second.state.value.panelVisible)
    }

    // spec 3.36.4: nothing shows before both the pages and the mark of the first visit are read — the panel would blink before the hint
    @Test
    fun `the stand stays dark until the mark of the first visit is read, and a first visit has no panel`() = runTest {
        val id = pieceWithPages(2)
        val mark = MutableSharedFlow<Boolean>(replay = 1)
        val slow = object : StandHintStore {
            override val seen: Flow<Boolean> = mark
            override suspend fun markSeen() = Unit
        }
        val (viewModel, _) = stand(id, hints = slow)
        assertTrue("the pages are read, the mark is not", viewModel.state.value.loading)

        mark.emit(false)
        runCurrent()
        val state = viewModel.state.value
        assertFalse(state.loading)
        assertTrue(state.showHint)
        assertFalse("no panel under the hint", state.panelVisible)
    }

    // spec 3.36.4: the hint stays 4 s, then goes by itself; the panel stays away after it — the middle of the sheet calls it
    @Test
    fun `the hint goes by itself after four seconds and the panel stays hidden`() = runTest {
        val unseen = FakeStandHintStore()
        val (viewModel, _) = stand(pieceWithPages(2), hints = unseen)
        advanceTimeBy(3_999)
        assertTrue(viewModel.state.value.showHint)
        advanceTimeBy(2)
        runCurrent()
        assertFalse(viewModel.state.value.showHint)
        assertTrue("never again", unseen.seen.value)
        advanceTimeBy(10_000)
        assertFalse(viewModel.state.value.panelVisible)
    }

    // spec 3.36.4: the first touch takes the hint away at once — an edge turns the page (Touched), the panel stays hidden
    @Test
    fun `a touch takes the hint away at once and the panel stays hidden`() = runTest {
        val unseen = FakeStandHintStore()
        val (viewModel, _) = stand(pieceWithPages(2), hints = unseen)
        advanceTimeBy(1_000)
        viewModel.onIntent(StandIntent.Touched)
        runCurrent()
        assertFalse(viewModel.state.value.showHint)
        assertTrue(unseen.seen.value)
        assertFalse(viewModel.state.value.panelVisible)
        advanceTimeBy(5_000)
        assertFalse("the hint's own time does not bring anything back", viewModel.state.value.showHint)
    }

    // spec 3.36.4: a tap in the middle under the hint takes it away and calls the panel, which then hides as ever
    @Test
    fun `a tap in the middle under the hint calls the panel`() = runTest {
        val unseen = FakeStandHintStore()
        val (viewModel, _) = stand(pieceWithPages(2), hints = unseen)
        viewModel.onIntent(StandIntent.PanelToggled)
        runCurrent()
        assertFalse(viewModel.state.value.showHint)
        assertTrue(viewModel.state.value.panelVisible)
        advanceTimeBy(3_001)
        assertFalse(viewModel.state.value.panelVisible)
    }

    @Test
    fun `a scale opens with its notes drawn, the photos follow, and the drawn page cannot be deleted`() = runTest {
        val spec = com.violinjourney.app.core.domain.repertoire.scale.ScaleSpec(
            com.violinjourney.app.core.domain.repertoire.Tonic.G, com.violinjourney.app.core.domain.repertoire.Accidental.NATURAL,
            com.violinjourney.app.core.domain.repertoire.scale.ScaleKind.MAJOR, 3,
        )
        val id = repertoire.add(
            PieceDraft(title = "G-dur", key = spec.key, section = com.violinjourney.app.core.domain.repertoire.PieceSection.SCALES, scale = spec), nowEpochMs = 1,
        )
        val (bare, bareEffects) = stand(id)
        assertEquals(listOf(true), bare.state.value.pages.map { it.drawn })
        assertTrue("a scale without photos still has something on the stand", bareEffects.isEmpty())

        val stored = checkNotNull(files.import("content://photo/fingering"))
        repertoire.addPage(id, stored.fileName, stored.thumbFileName, nowEpochMs = 2)
        val (viewModel, _) = stand(id)
        assertEquals(listOf(true, false), viewModel.state.value.pages.map { it.drawn })
        assertEquals(43, viewModel.state.value.pages.first().scale!!.notes.size)

        viewModel.onIntent(StandIntent.DeleteClicked)
        assertFalse(viewModel.state.value.deleteDialog)
        viewModel.onIntent(StandIntent.PageSettled(1))
        viewModel.onIntent(StandIntent.DeleteClicked)
        assertTrue(viewModel.state.value.deleteDialog)
    }
}
