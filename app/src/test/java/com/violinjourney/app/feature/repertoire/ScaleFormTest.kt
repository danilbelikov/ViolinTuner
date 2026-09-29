package com.violinjourney.app.feature.repertoire

import androidx.lifecycle.SavedStateHandle
import com.violinjourney.app.core.domain.repertoire.Accidental
import com.violinjourney.app.core.domain.repertoire.FakeRepertoireRepository
import com.violinjourney.app.core.domain.repertoire.PieceDraft
import com.violinjourney.app.core.domain.repertoire.PieceSection
import com.violinjourney.app.core.domain.repertoire.PieceStatus
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.domain.repertoire.Tonic
import com.violinjourney.app.core.domain.repertoire.scale.ScaleKind
import com.violinjourney.app.core.domain.repertoire.scale.ScaleSpec
import com.violinjourney.app.core.domain.session.FakeSessionRepository
import com.violinjourney.app.core.domain.session.SessionSummary
import com.violinjourney.app.core.time.FixedWallClock
import com.violinjourney.app.core.time.WallClock
import com.violinjourney.app.feature.repertoire.scale.ScaleFormDialog
import com.violinjourney.app.feature.repertoire.scale.ScaleFormEffect
import com.violinjourney.app.feature.repertoire.scale.ScaleFormIntent
import com.violinjourney.app.feature.repertoire.scale.ScaleFormViewModel
import com.violinjourney.app.feature.repertoire.scale.ScaleTexts
import com.violinjourney.app.feature.repertoire.scale.ScaleTwin
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The form of a scale keeps what the player did (spec 3.15, 3.22): its draft outlives the process as the notes field
 * does, and «Открыть» on the scale that is there already asks before it drops a tempo, a status or notes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScaleFormTest {
    private val config = RepertoireConfig()
    private val repertoire = FakeRepertoireRepository()
    private val sessions = FakeSessionRepository()
    private val clock: WallClock = FixedWallClock(Instant.fromEpochMilliseconds(7_000), TimeZone.UTC)
    private val texts = object : ScaleTexts {
        override fun titleOf(spec: ScaleSpec) = "${spec.key.germanName} ${spec.kind} ${spec.octaves}"
    }
    private val gMajor = ScaleSpec(Tonic.G, Accidental.NATURAL, ScaleKind.MAJOR, 3)

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.scaleForm(pieceId: Long? = null): Pair<ScaleFormViewModel, MutableList<ScaleFormEffect>> =
        scaleForm(SavedStateHandle(mapOf(ScaleFormViewModel.ARG_PIECE_ID to (pieceId ?: ScaleFormViewModel.NEW_SCALE))))

    private fun TestScope.scaleForm(handle: SavedStateHandle): Pair<ScaleFormViewModel, MutableList<ScaleFormEffect>> {
        val viewModel = ScaleFormViewModel(handle, repertoire, sessions, config, clock, texts)
        val effects = mutableListOf<ScaleFormEffect>()
        backgroundScope.launch { viewModel.effects.collect { effects += it } }
        runCurrent()
        return viewModel to effects
    }

    @Test
    fun `opening the scale there is asks first when the tempo or the notes would be lost`() = runTest {
        val threeOctaves = repertoire.add(PieceDraft(title = "G-dur 3", section = PieceSection.SCALES, scale = gMajor, key = gMajor.key), 1)
        val twoOctaves = repertoire.add(PieceDraft(title = "G-dur 2", section = PieceSection.SCALES, scale = gMajor.copy(octaves = 2), key = gMajor.key), 2)
        val (form, effects) = scaleForm(threeOctaves)
        form.onIntent(ScaleFormIntent.NotesChanged("медленно"))
        form.onIntent(ScaleFormIntent.OctavesSelected(2))
        runCurrent()
        assertEquals(twoOctaves, form.state.value.twin?.id)

        form.onIntent(ScaleFormIntent.OpenExistingClicked)
        runCurrent()
        assertEquals(ScaleFormDialog.DISCARD_AND_OPEN, form.state.value.dialog)
        assertTrue("nothing leaves before the answer", effects.isEmpty())
        form.onIntent(ScaleFormIntent.DialogDismissed)
        assertNull(form.state.value.dialog)

        form.onIntent(ScaleFormIntent.OpenExistingClicked)
        form.onIntent(ScaleFormIntent.DialogConfirmed)
        runCurrent()
        assertEquals(listOf<ScaleFormEffect>(ScaleFormEffect.OpenScale(twoOctaves)), effects)
        assertEquals("the scale left keeps its notes", "", repertoire.piece(threeOctaves)!!.notes)
    }

    /** The saved state as Android gives it back to a new process: its values, and nothing else of the old one. */
    private fun rebornFrom(handle: SavedStateHandle) = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })

    @Test
    fun `a new scale form comes back as it was left after the process was gone`() = runTest {
        val handle = SavedStateHandle(mapOf(ScaleFormViewModel.ARG_PIECE_ID to ScaleFormViewModel.NEW_SCALE))
        val (form, _) = scaleForm(handle)
        form.onIntent(ScaleFormIntent.TonicClicked(Tonic.D))
        form.onIntent(ScaleFormIntent.KindSelected(ScaleKind.HARMONIC_MINOR))
        form.onIntent(ScaleFormIntent.OctavesSelected(2))
        form.onIntent(ScaleFormIntent.TempoPicked(60))
        form.onIntent(ScaleFormIntent.NotesChanged("x"))
        runCurrent()
        val left = form.state.value.draft

        val (reborn, _) = scaleForm(rebornFrom(handle))
        assertEquals(left, reborn.state.value.draft)
        reborn.onIntent(ScaleFormIntent.SaveClicked)
        runCurrent()
        val saved = repertoire.pieces.value.single()
        assertEquals(ScaleSpec(Tonic.D, Accidental.NATURAL, ScaleKind.HARMONIC_MINOR, 2), saved.scale)
        assertEquals(60 to "x", saved.tempoBpm to saved.notes)
    }

    @Test
    fun `an edited scale comes back with its edits and still asks before leaving them`() = runTest {
        val id = repertoire.add(PieceDraft(title = "G-dur", section = PieceSection.SCALES, scale = gMajor, key = gMajor.key), 1)
        val handle = SavedStateHandle(mapOf(ScaleFormViewModel.ARG_PIECE_ID to id))
        val (form, _) = scaleForm(handle)
        form.onIntent(ScaleFormIntent.OctavesSelected(2))
        form.onIntent(ScaleFormIntent.NotesChanged("x"))
        runCurrent()

        val (reborn, effects) = scaleForm(rebornFrom(handle))
        assertEquals(2 to "x", reborn.state.value.draft.octaves to reborn.state.value.draft.notes)
        assertEquals(Tonic.G to ScaleKind.MAJOR, reborn.state.value.draft.tonic to reborn.state.value.draft.kind)
        reborn.onIntent(ScaleFormIntent.CloseClicked)
        runCurrent()
        assertEquals(ScaleFormDialog.DISCARD, reborn.state.value.dialog)
        assertTrue(effects.isEmpty())

        // a form never edited reads the scale as stored
        val (fresh, _) = scaleForm(id)
        assertEquals(3 to "", fresh.state.value.draft.octaves to fresh.state.value.draft.notes)
    }

    /**
     * «Не сохранять?» and «Удалить …?» call a scale by the name it has in the list (spec 3.36.1): the title as stored, in the
     * language it was made in (spec 3.26) — not the one the form would give it now, and not the octaves just picked.
     */
    @Test
    fun `an edited scale is called by its stored title while its octaves change`() = runTest {
        val stored = "G-dur · 3 октавы"
        val id = repertoire.add(PieceDraft(title = stored, section = PieceSection.SCALES, scale = gMajor, key = gMajor.key), 1)
        val (form, _) = scaleForm(id)
        assertEquals(stored, form.state.value.savedTitle)
        assertNotEquals("the stored title, not the one said anew", texts.titleOf(gMajor), form.state.value.savedTitle)

        form.onIntent(ScaleFormIntent.OctavesSelected(2))
        runCurrent()
        assertEquals(2, form.state.value.scale?.spec?.octaves)
        assertEquals("the name before the edit", stored, form.state.value.savedTitle)
    }

    @Test
    fun `a new scale has no saved title to be called by`() = runTest {
        val (form, _) = scaleForm()
        form.onIntent(ScaleFormIntent.TonicClicked(Tonic.D))
        runCurrent()
        assertNull(form.state.value.savedTitle)
    }

    private fun take(id: Long, pieceId: Long?) = SessionSummary(
        id = id, title = null, startedAtEpochMs = id * 1_000, durationMs = 60_000, a4Hz = 440.0, toleranceCents = 8.0, nearCents = 20.0,
        scorePercent = 80, nearPercent = 0, offPercent = 0, maeCents = 0.0, biasCents = 0.0, previewZones = emptyList(), audioPath = null,
        pieceId = pieceId,
    )

    // spec 3.36.4: «Такая гамма уже есть — в «Гаммах», статус «Учу», 2 дубля.» over «Открыть её»
    @Test
    fun `the scale that is there already comes with its status and the number of its takes`() = runTest {
        val learning = repertoire.add(PieceDraft(title = "G-dur 3", section = PieceSection.SCALES, scale = gMajor, key = gMajor.key, status = PieceStatus.LEARNING), 1)
        val bare = repertoire.add(PieceDraft(title = "G-dur 2", section = PieceSection.SCALES, scale = gMajor.copy(octaves = 2), key = gMajor.key), 2)
        sessions.sessions.value = listOf(take(1, learning), take(2, learning), take(3, bare + 100), take(4, null))
        val (form, _) = scaleForm()
        form.onIntent(ScaleFormIntent.TonicClicked(Tonic.G))
        form.onIntent(ScaleFormIntent.OctavesSelected(3))
        runCurrent()
        assertEquals(ScaleTwin(learning, PieceStatus.LEARNING, takes = 2), form.state.value.twin)
        assertFalse("the twin is opened, not saved", form.state.value.canSave)

        form.onIntent(ScaleFormIntent.OctavesSelected(2))
        assertEquals("without takes — «нет дублей»", ScaleTwin(bare, PieceStatus.READING, takes = 0), form.state.value.twin)

        // a take recorded meanwhile is counted
        sessions.sessions.value = sessions.sessions.value + take(5, bare)
        runCurrent()
        assertEquals(1, form.state.value.twin?.takes)
    }

    // spec 3.36.4: in an edit the key and the kind sleep under their reason — a tap that still comes says nothing and changes nothing
    @Test
    fun `an edit hears no tap on the tonic, the sign or the kind, and shows no toast`() = runTest {
        val id = repertoire.add(PieceDraft(title = "G-dur", section = PieceSection.SCALES, scale = gMajor, key = gMajor.key), 1)
        val (form, effects) = scaleForm(id)
        val before = form.state.value.draft
        form.onIntent(ScaleFormIntent.TonicClicked(Tonic.A))
        form.onIntent(ScaleFormIntent.AccidentalSelected(Accidental.FLAT))
        form.onIntent(ScaleFormIntent.KindSelected(ScaleKind.HARMONIC_MINOR))
        runCurrent()
        assertEquals(before, form.state.value.draft)
        assertTrue("no message, no dialog", effects.isEmpty() && form.state.value.dialog == null)
    }

    // spec 3.36.4: «Три октавы от F4 не помещаются на скрипке» — the reason names the lowest tonic the violin has for the key
    @Test
    fun `the start note is the lowest tonic of the key on the violin`() = runTest {
        val (form, _) = scaleForm()
        assertNull("no tonic, no start", form.state.value.startNote)
        form.onIntent(ScaleFormIntent.TonicClicked(Tonic.F))
        assertEquals("F4", form.state.value.startNote)
        assertEquals(setOf(1, 2), form.state.value.octavesAllowed)
        form.onIntent(ScaleFormIntent.AccidentalSelected(Accidental.SHARP))
        assertEquals("F#4", form.state.value.startNote)
        form.onIntent(ScaleFormIntent.TonicClicked(Tonic.G))
        form.onIntent(ScaleFormIntent.AccidentalSelected(Accidental.NATURAL))
        form.onIntent(ScaleFormIntent.TonicClicked(Tonic.G))
        assertEquals("G3", form.state.value.startNote)
        assertEquals(setOf(1, 2, 3), form.state.value.octavesAllowed)
    }

    // spec 3.36.4: the quiet line «Добавить заметку» of a scale opens its form at that field (3.15), as a piece's does
    @Test
    fun `a form opened by the quiet line of the notes puts the focus in them, and only that one`() = runTest {
        val id = repertoire.add(PieceDraft(title = "G-dur", section = PieceSection.SCALES, scale = gMajor, key = gMajor.key), 1)
        val (atNotes, _) = scaleForm(SavedStateHandle(mapOf(ScaleFormViewModel.ARG_PIECE_ID to id, ScaleFormViewModel.ARG_FOCUS_NOTES to true)))
        assertTrue(atNotes.state.value.focusNotes)
        runCurrent()
        assertTrue("still once the scale is read", atNotes.state.value.focusNotes && !atNotes.state.value.loading)

        val (edited, _) = scaleForm(id)
        assertFalse("«Изменить» opens it at its top", edited.state.value.focusNotes)
        val (fresh, _) = scaleForm()
        assertFalse(fresh.state.value.focusNotes)
    }
}
