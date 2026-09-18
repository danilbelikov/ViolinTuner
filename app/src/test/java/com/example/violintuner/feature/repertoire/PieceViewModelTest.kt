package com.example.violintuner.feature.repertoire

import androidx.lifecycle.SavedStateHandle
import com.example.violintuner.core.data.repertoire.FakeSheetFiles
import com.example.violintuner.core.domain.repertoire.FakeRepertoireRepository
import com.example.violintuner.core.domain.repertoire.PieceDraft
import com.example.violintuner.core.domain.repertoire.PieceStatus
import com.example.violintuner.core.domain.repertoire.RepertoireConfig
import com.example.violintuner.feature.repertoire.piece.PieceEffect
import com.example.violintuner.feature.repertoire.piece.PieceIntent
import com.example.violintuner.feature.repertoire.piece.PieceViewModel
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PieceViewModelTest {
    private val repertoire = FakeRepertoireRepository()
    private val files = FakeSheetFiles()
    private val clock: Clock = Clock.fixed(Instant.ofEpochMilli(9_000), ZoneOffset.UTC)

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.screen(pieceId: Long, saved: SavedStateHandle = SavedStateHandle(mapOf(PieceViewModel.ARG_PIECE_ID to pieceId))):
        Pair<PieceViewModel, MutableList<PieceEffect>> {
        val viewModel = PieceViewModel(saved, repertoire, files, RepertoireConfig(), clock)
        val effects = mutableListOf<PieceEffect>()
        backgroundScope.launch { viewModel.state.collect {} }
        backgroundScope.launch { viewModel.effects.collect { effects += it } }
        runCurrent()
        return viewModel to effects
    }

    @Test
    fun `the screen shows the piece and follows its edits`() = runTest {
        val id = repertoire.add(PieceDraft(title = "Менуэт", composer = "Бах", notes = "Такты 9–12"), nowEpochMs = 1)
        val (viewModel, _) = screen(id)
        assertEquals("Менуэт", viewModel.state.value.header!!.title)
        assertEquals("Такты 9–12", viewModel.state.value.notes)

        repertoire.update(id, PieceDraft(title = "Гавот"), nowEpochMs = 2)
        runCurrent()
        assertEquals("Гавот", viewModel.state.value.header!!.title)
        assertEquals("", viewModel.state.value.header!!.composer)
    }

    @Test
    fun `picked photos become pages in the order they were picked, numbered from one`() = runTest {
        val id = repertoire.add(PieceDraft(title = "Менуэт"), nowEpochMs = 1)
        val (viewModel, effects) = screen(id)
        val copying = CompletableDeferred<Unit>()
        files.hold = copying
        viewModel.onIntent(PieceIntent.PhotosPicked(listOf("content://1", "content://2")))
        runCurrent()
        assertEquals("placeholders stand in the strip while the photos are copied", 2, viewModel.state.value.importing)
        assertTrue(viewModel.state.value.pages.isEmpty())
        copying.complete(Unit)
        runCurrent()

        val state = viewModel.state.value
        assertEquals(0, state.importing)
        assertEquals(listOf(1, 2), state.pages.map { it.number })
        assertEquals(listOf("/sheets/page-1-thumb.jpg", "/sheets/page-2-thumb.jpg"), state.pages.map { it.thumbPath })
        assertEquals("adding a page is activity", 9_000L, repertoire.piece(id)!!.updatedAtEpochMs)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `a photo that does not open is said once, and the others still come in`() = runTest {
        val id = repertoire.add(PieceDraft(title = "Менуэт"), nowEpochMs = 1)
        val (viewModel, effects) = screen(id)
        viewModel.onIntent(PieceIntent.PhotosPicked(listOf("content://1", "content://broken", "content://broken-too", "content://4")))
        runCurrent()
        assertEquals(2, viewModel.state.value.pages.size)
        assertEquals(listOf<PieceEffect>(PieceEffect.ShowPhotoFailed), effects)
    }

    @Test
    fun `a camera shot goes in like a picked photo and its temporary file goes away`() = runTest {
        val id = repertoire.add(PieceDraft(title = "Менуэт"), nowEpochMs = 1)
        val (viewModel, effects) = screen(id)
        viewModel.onIntent(PieceIntent.CameraClicked)
        runCurrent()
        val shot = files.cameraFiles.single()
        assertEquals(listOf<PieceEffect>(PieceEffect.LaunchCamera(shot.path)), effects)

        viewModel.onIntent(PieceIntent.CameraFinished(saved = true))
        runCurrent()
        assertEquals(1, viewModel.state.value.pages.size)
        assertFalse(shot.exists())
    }

    @Test
    fun `backing out of the camera adds nothing and leaves no file, and the pending shot survives a process death`() = runTest {
        val id = repertoire.add(PieceDraft(title = "Менуэт"), nowEpochMs = 1)
        val saved = SavedStateHandle(mapOf(PieceViewModel.ARG_PIECE_ID to id))
        val (before, _) = screen(id, saved)
        before.onIntent(PieceIntent.CameraClicked)
        val shot: File = files.cameraFiles.single()

        // The camera app pushed us out of memory; a new view model gets the same saved state.
        val (after, _) = screen(id, saved)
        after.onIntent(PieceIntent.CameraFinished(saved = false))
        runCurrent()
        assertTrue(after.state.value.pages.isEmpty())
        assertFalse(shot.exists())

        after.onIntent(PieceIntent.CameraFinished(saved = true))
        runCurrent()
        assertTrue("a result nobody asked for is ignored", after.state.value.pages.isEmpty())
    }

    @Test
    fun `the status changes from the chip menu`() = runTest {
        val id = repertoire.add(PieceDraft(title = "Менуэт"), nowEpochMs = 1)
        val (viewModel, _) = screen(id)
        viewModel.onIntent(PieceIntent.StatusChipClicked)
        runCurrent()
        assertTrue(viewModel.state.value.statusMenuOpen)
        viewModel.onIntent(PieceIntent.StatusSelected(PieceStatus.IN_REPERTOIRE))
        runCurrent()
        assertFalse(viewModel.state.value.statusMenuOpen)
        assertEquals(PieceStatus.IN_REPERTOIRE, viewModel.state.value.header!!.status)
    }

    @Test
    fun `buttons lead where they say`() = runTest {
        val id = repertoire.add(PieceDraft(title = "Менуэт"), nowEpochMs = 1)
        val (viewModel, effects) = screen(id)
        viewModel.onIntent(PieceIntent.EditClicked)
        viewModel.onIntent(PieceIntent.AddNotesClicked)
        viewModel.onIntent(PieceIntent.PageClicked(3))
        viewModel.onIntent(PieceIntent.BackClicked)
        runCurrent()
        assertEquals(
            listOf(PieceEffect.OpenForm(id, false), PieceEffect.OpenForm(id, true), PieceEffect.OpenStand(id, 3), PieceEffect.Close),
            effects,
        )
    }

    @Test
    fun `a piece deleted under the screen closes it once`() = runTest {
        val id = repertoire.add(PieceDraft(title = "Менуэт"), nowEpochMs = 1)
        val (viewModel, effects) = screen(id)
        repertoire.delete(id)
        runCurrent()
        repertoire.add(PieceDraft(title = "Другое"), nowEpochMs = 2)
        runCurrent()
        assertEquals(listOf<PieceEffect>(PieceEffect.Close), effects)
        assertTrue(viewModel.state.value.loading)
    }
}
