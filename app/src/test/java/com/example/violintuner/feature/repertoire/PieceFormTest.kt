package com.example.violintuner.feature.repertoire

import androidx.lifecycle.SavedStateHandle
import com.example.violintuner.core.domain.repertoire.Accidental
import com.example.violintuner.core.domain.repertoire.FakeRepertoireRepository
import com.example.violintuner.core.domain.repertoire.KeyMode
import com.example.violintuner.core.domain.repertoire.MusicalKey
import com.example.violintuner.core.domain.repertoire.PieceDraft
import com.example.violintuner.core.domain.repertoire.PieceStatus
import com.example.violintuner.core.domain.repertoire.RepertoireConfig
import com.example.violintuner.core.domain.repertoire.Tonic
import com.example.violintuner.feature.repertoire.form.PieceFormDialog
import com.example.violintuner.feature.repertoire.form.PieceFormEffect
import com.example.violintuner.feature.repertoire.form.PieceFormIntent
import com.example.violintuner.feature.repertoire.form.PieceFormReducer
import com.example.violintuner.feature.repertoire.form.PieceFormViewModel
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PieceFormTest {
    private val config = RepertoireConfig()
    private val repertoire = FakeRepertoireRepository()
    private val clock: Clock = Clock.fixed(Instant.ofEpochMilli(7_000), ZoneOffset.UTC)

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.form(pieceId: Long? = null): Pair<PieceFormViewModel, MutableList<PieceFormEffect>> {
        val args = mapOf(PieceFormViewModel.ARG_PIECE_ID to (pieceId ?: PieceFormViewModel.NEW_PIECE))
        val viewModel = PieceFormViewModel(SavedStateHandle(args), repertoire, config, clock)
        val effects = mutableListOf<PieceFormEffect>()
        backgroundScope.launch { viewModel.effects.collect { effects += it } }
        runCurrent()
        return viewModel to effects
    }

    @Test
    fun `a tap on a tonic picks a natural major key, another tonic keeps the rest, the same tonic clears`() {
        val g = PieceFormReducer.clickTonic(null, Tonic.G)
        assertEquals(MusicalKey(Tonic.G, Accidental.NATURAL, KeyMode.MAJOR), g)
        val fisMoll = MusicalKey(Tonic.F, Accidental.SHARP, KeyMode.MINOR)
        assertEquals(fisMoll.copy(tonic = Tonic.C), PieceFormReducer.clickTonic(fisMoll, Tonic.C))
        assertNull(PieceFormReducer.clickTonic(fisMoll, Tonic.F))
    }

    @Test
    fun `a stray space is not an edit, a changed field is`() {
        val saved = PieceDraft(title = "Менуэт", notes = "ноты")
        assertFalse(PieceFormReducer.isDirty(saved, saved.copy(title = " Менуэт  "), config))
        assertTrue(PieceFormReducer.isDirty(saved, saved.copy(tempoBpm = 96), config))
        assertFalse("an untouched new form can just close", PieceFormReducer.isDirty(PieceDraft(), PieceDraft(title = "  "), config))
        assertTrue(PieceFormReducer.isDirty(PieceDraft(), PieceDraft(notes = "что-то"), config))
    }

    @Test
    fun `a new piece cannot be saved without a title, and says so only once asked`() = runTest {
        val (form, effects) = form()
        assertFalse(form.state.value.canSave)
        assertFalse("not an error before the user has been there", form.state.value.titleError)

        form.onIntent(PieceFormIntent.SaveClicked)
        runCurrent()
        assertTrue(form.state.value.titleError)
        assertTrue(effects.isEmpty() && repertoire.pieces.value.isEmpty())

        form.onIntent(PieceFormIntent.TitleChanged("Менуэт"))
        assertTrue(form.state.value.canSave)
        assertFalse(form.state.value.titleError)
    }

    @Test
    fun `saving a new piece stores it cleaned and opens its screen`() = runTest {
        val (form, effects) = form()
        form.onIntent(PieceFormIntent.TitleChanged("  Менуэт соль мажор "))
        form.onIntent(PieceFormIntent.TonicClicked(Tonic.G))
        form.onIntent(PieceFormIntent.TempoPicked(100))
        form.onIntent(PieceFormIntent.TempoStepped(-5))
        form.onIntent(PieceFormIntent.StatusSelected(PieceStatus.LEARNING))
        form.onIntent(PieceFormIntent.SaveClicked)
        form.onIntent(PieceFormIntent.SaveClicked)
        runCurrent()

        val piece = repertoire.pieces.value.single()
        assertEquals("Менуэт соль мажор", piece.title)
        assertEquals("G-dur", piece.key!!.germanName)
        assertEquals(95, piece.tempoBpm)
        assertEquals(PieceStatus.LEARNING, piece.status)
        assertEquals(7_000L, piece.createdAtEpochMs)
        assertEquals("a second tap does not save twice", listOf<PieceFormEffect>(PieceFormEffect.OpenCreated(piece.id)), effects)
    }

    @Test
    fun `the sign and the mode need a tonic, and typed text is capped as it is typed`() = runTest {
        val (form, _) = form()
        form.onIntent(PieceFormIntent.AccidentalSelected(Accidental.FLAT))
        form.onIntent(PieceFormIntent.ModeSelected(KeyMode.MINOR))
        assertNull(form.state.value.draft.key)

        form.onIntent(PieceFormIntent.TonicClicked(Tonic.B))
        form.onIntent(PieceFormIntent.AccidentalSelected(Accidental.FLAT))
        assertEquals("B-dur", form.state.value.draft.key!!.germanName)

        form.onIntent(PieceFormIntent.TitleChanged("я".repeat(200)))
        assertEquals(80, form.state.value.draft.title.length)
    }

    @Test
    fun `an edit opens with the piece, saves over it and closes`() = runTest {
        val id = repertoire.add(PieceDraft(title = "Менуэт", composer = "Бах", tempoBpm = 96), nowEpochMs = 1)
        val (form, effects) = form(id)
        assertFalse(form.state.value.loading || form.state.value.isNew)
        assertEquals("Бах", form.state.value.draft.composer)

        form.onIntent(PieceFormIntent.TempoPicked(null))
        form.onIntent(PieceFormIntent.SaveClicked)
        runCurrent()
        assertNull(repertoire.piece(id)!!.tempoBpm)
        assertEquals(7_000L, repertoire.piece(id)!!.updatedAtEpochMs)
        assertEquals(listOf<PieceFormEffect>(PieceFormEffect.Close), effects)
    }

    @Test
    fun `closing with edits asks first, closing without them does not`() = runTest {
        val id = repertoire.add(PieceDraft(title = "Менуэт"), nowEpochMs = 1)
        val (clean, cleanEffects) = form(id)
        clean.onIntent(PieceFormIntent.CloseClicked)
        runCurrent()
        assertEquals(listOf<PieceFormEffect>(PieceFormEffect.Close), cleanEffects)

        val (dirty, dirtyEffects) = form(id)
        dirty.onIntent(PieceFormIntent.NotesChanged("Такты 9–12"))
        dirty.onIntent(PieceFormIntent.CloseClicked)
        runCurrent()
        assertEquals(PieceFormDialog.DISCARD, dirty.state.value.dialog)
        assertTrue(dirtyEffects.isEmpty())

        dirty.onIntent(PieceFormIntent.DialogDismissed)
        assertNull(dirty.state.value.dialog)
        dirty.onIntent(PieceFormIntent.CloseClicked)
        dirty.onIntent(PieceFormIntent.DialogConfirmed)
        runCurrent()
        assertEquals(listOf<PieceFormEffect>(PieceFormEffect.Close), dirtyEffects)
        assertEquals("nothing was saved", "", repertoire.piece(id)!!.notes)
    }

    @Test
    fun `deleting asks, removes the piece and closes both screens, and a new piece has nothing to delete`() = runTest {
        val id = repertoire.add(PieceDraft(title = "Менуэт"), nowEpochMs = 1)
        val (form, effects) = form(id)
        form.onIntent(PieceFormIntent.DeleteClicked)
        assertEquals(PieceFormDialog.DELETE, form.state.value.dialog)
        form.onIntent(PieceFormIntent.DialogConfirmed)
        runCurrent()
        assertTrue(repertoire.pieces.value.isEmpty())
        assertEquals(listOf<PieceFormEffect>(PieceFormEffect.CloseDeleted), effects)

        val (fresh, _) = form()
        fresh.onIntent(PieceFormIntent.DeleteClicked)
        assertNull(fresh.state.value.dialog)
    }

    @Test
    fun `the form of a piece that is gone closes by itself`() = runTest {
        val (_, effects) = form(pieceId = 404)
        assertEquals(listOf<PieceFormEffect>(PieceFormEffect.CloseDeleted), effects)
    }
}
