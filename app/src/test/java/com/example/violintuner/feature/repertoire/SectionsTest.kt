package com.example.violintuner.feature.repertoire

import androidx.lifecycle.SavedStateHandle
import com.example.violintuner.core.data.repertoire.FakeSheetFiles
import com.example.violintuner.core.domain.repertoire.Accidental
import com.example.violintuner.core.domain.repertoire.FakeRepertoireRepository
import com.example.violintuner.core.domain.repertoire.PieceDraft
import com.example.violintuner.core.domain.repertoire.PieceSection
import com.example.violintuner.core.domain.repertoire.PieceStatus
import com.example.violintuner.core.domain.repertoire.RepertoireConfig
import com.example.violintuner.core.domain.repertoire.SectionCount
import com.example.violintuner.core.domain.repertoire.SectionRef
import com.example.violintuner.core.domain.repertoire.Tonic
import com.example.violintuner.core.domain.repertoire.scale.ScaleKind
import com.example.violintuner.core.domain.repertoire.scale.ScaleSpec
import com.example.violintuner.core.domain.session.FakeSessionRepository
import com.example.violintuner.feature.repertoire.form.PieceFormIntent
import com.example.violintuner.feature.repertoire.form.PieceFormViewModel
import com.example.violintuner.feature.repertoire.scale.ScaleFormEffect
import com.example.violintuner.feature.repertoire.scale.ScaleFormIntent
import com.example.violintuner.feature.repertoire.scale.ScaleFormViewModel
import com.example.violintuner.feature.repertoire.scale.ScaleTexts
import com.example.violintuner.feature.repertoire.sections.SectionsEffect
import com.example.violintuner.feature.repertoire.sections.SectionsIntent
import com.example.violintuner.feature.repertoire.sections.SectionsViewModel
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

/** Sections of the repertoire on their screens (spec 3.22): the landing, the list of a section, the forms. */
@OptIn(ExperimentalCoroutinesApi::class)
class SectionsTest {
    private val config = RepertoireConfig()
    private val repertoire = FakeRepertoireRepository()
    private val clock: Clock = Clock.fixed(Instant.ofEpochMilli(7_000), ZoneOffset.UTC)
    private val texts = object : ScaleTexts {
        override fun titleOf(spec: ScaleSpec) = "${spec.key.germanName} ${spec.kind} ${spec.octaves}"
    }
    private val gMajor = ScaleSpec(Tonic.G, Accidental.NATURAL, ScaleKind.MAJOR, 3)

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.sections(): Pair<SectionsViewModel, MutableList<SectionsEffect>> {
        val viewModel = SectionsViewModel(repertoire, config, clock)
        val effects = mutableListOf<SectionsEffect>()
        backgroundScope.launch { viewModel.state.collect {} }
        backgroundScope.launch { viewModel.effects.collect { effects += it } }
        runCurrent()
        return viewModel to effects
    }

    private fun TestScope.list(section: SectionRef): Pair<RepertoireViewModel, MutableList<RepertoireEffect>> {
        val viewModel = RepertoireViewModel(
            SavedStateHandle(mapOf(RepertoireViewModel.ARG_SECTION to SectionKeys.keyOf(section))),
            repertoire, FakeSessionRepository(), FakeSheetFiles(), config, clock,
        )
        val effects = mutableListOf<RepertoireEffect>()
        backgroundScope.launch { viewModel.state.collect {} }
        backgroundScope.launch { viewModel.effects.collect { effects += it } }
        runCurrent()
        return viewModel to effects
    }

    private fun TestScope.scaleForm(pieceId: Long? = null): Pair<ScaleFormViewModel, MutableList<ScaleFormEffect>> {
        val viewModel = ScaleFormViewModel(
            SavedStateHandle(mapOf(ScaleFormViewModel.ARG_PIECE_ID to (pieceId ?: ScaleFormViewModel.NEW_SCALE))), repertoire, config, clock, texts,
        )
        val effects = mutableListOf<ScaleFormEffect>()
        backgroundScope.launch { viewModel.effects.collect { effects += it } }
        runCurrent()
        return viewModel to effects
    }

    @Test
    fun `the landing shows the four sections with their counts and the total`() = runTest {
        repertoire.add(PieceDraft(title = "Менуэт", status = PieceStatus.IN_REPERTOIRE), 1)
        repertoire.add(PieceDraft(title = "Концерт"), 2)
        repertoire.add(PieceDraft(title = "Кайзер № 3", section = PieceSection.ETUDES, status = PieceStatus.LEARNING), 3)
        val (viewModel, _) = sections()
        val state = viewModel.state.value
        assertEquals(PieceSection.entries.map { SectionRef.BuiltIn(it) }, state.cards.map { it.ref })
        assertEquals(SectionCount(1, 0, 1), state.cards[0].count)
        assertEquals(SectionCount(0, 1, 0), state.cards[2].count)
        assertEquals(SectionCount(1, 1, 1), state.total)
    }

    @Test
    fun `a new section is named, made and opened at once - and an empty name makes nothing`() = runTest {
        val (viewModel, effects) = sections()
        viewModel.onIntent(SectionsIntent.AddClicked)
        viewModel.onIntent(SectionsIntent.NameChanged("   "))
        runCurrent()
        assertFalse(viewModel.state.value.canCreate)
        viewModel.onIntent(SectionsIntent.CreateConfirmed)
        runCurrent()
        assertTrue(repertoire.groups.value.isEmpty())

        viewModel.onIntent(SectionsIntent.NameChanged("  Двойные ноты, терции и сексты — всё подряд "))
        viewModel.onIntent(SectionsIntent.CreateConfirmed)
        runCurrent()
        val group = repertoire.groups.value.single()
        assertTrue(group.name.startsWith("Двойные ноты") && group.name.length <= config.maxGroupNameLength)
        assertEquals(listOf<SectionsEffect>(SectionsEffect.OpenSection(SectionRef.Custom(group.id))), effects)
        assertNull(viewModel.state.value.newName)
        assertEquals(5, viewModel.state.value.cards.size)
    }

    @Test
    fun `the list of a section holds its elements only and asks for the right form`() = runTest {
        repertoire.add(PieceDraft(title = "Менуэт"), 1)
        repertoire.add(PieceDraft(title = "G-dur", section = PieceSection.SCALES, scale = gMajor, key = gMajor.key), 2)
        val (scales, effects) = list(SectionRef.BuiltIn(PieceSection.SCALES))
        val card = scales.state.value.cards.single()
        assertEquals(gMajor, card.scale)
        assertTrue(card.exercise && !card.stroke)
        assertEquals(SectionCount(1, 0, 0), scales.state.value.count)
        scales.onIntent(RepertoireIntent.AddClicked)
        runCurrent()
        assertEquals(listOf<RepertoireEffect>(RepertoireEffect.OpenNew(SectionRef.BuiltIn(PieceSection.SCALES))), effects)

        val (pieces, _) = list(SectionRef.BuiltIn(PieceSection.PIECES))
        assertEquals(listOf("Менуэт"), pieces.state.value.cards.map { it.title })
        assertFalse(pieces.state.value.cards.single().exercise)
    }

    @Test
    fun `a section of one's own is renamed, and deleting it moves its elements home and closes the list`() = runTest {
        val groupId = repertoire.addGroup("Терции", 1)
        val pieceId = repertoire.add(PieceDraft(title = "Шрадик", groupId = groupId), 2)
        val (viewModel, effects) = list(SectionRef.Custom(groupId))
        assertEquals("Терции", viewModel.state.value.sectionName)
        assertTrue(viewModel.state.value.custom)

        viewModel.onIntent(RepertoireIntent.DialogRequested(SectionDialog.RENAME))
        runCurrent()
        assertEquals("Терции", viewModel.state.value.nameDraft)
        viewModel.onIntent(RepertoireIntent.NameChanged("Двойные ноты"))
        viewModel.onIntent(RepertoireIntent.DialogConfirmed)
        runCurrent()
        assertEquals("Двойные ноты", viewModel.state.value.sectionName)

        viewModel.onIntent(RepertoireIntent.DialogRequested(SectionDialog.DELETE))
        viewModel.onIntent(RepertoireIntent.DialogConfirmed)
        runCurrent()
        assertTrue(repertoire.groups.value.isEmpty())
        val moved = repertoire.piece(pieceId)!!
        assertEquals(PieceSection.PIECES to null, moved.section to moved.groupId)
        assertEquals(listOf<RepertoireEffect>(RepertoireEffect.Close), effects)
    }

    @Test
    fun `a built-in section has no menu to answer`() = runTest {
        val (viewModel, _) = list(SectionRef.BuiltIn(PieceSection.ETUDES))
        viewModel.onIntent(RepertoireIntent.DialogRequested(SectionDialog.DELETE))
        runCurrent()
        assertNull(viewModel.state.value.dialog)
    }

    @Test
    fun `a new element is born in the section its form was opened for`() = runTest {
        val args = mapOf(PieceFormViewModel.ARG_PIECE_ID to PieceFormViewModel.NEW_PIECE, PieceFormViewModel.ARG_SECTION to PieceSection.STROKES.name)
        val form = PieceFormViewModel(SavedStateHandle(args), repertoire, config, clock)
        runCurrent()
        assertTrue(form.state.value.stroke)
        form.onIntent(PieceFormIntent.TitleChanged("Спиккато"))
        form.onIntent(PieceFormIntent.SaveClicked)
        runCurrent()
        assertEquals(PieceSection.STROKES, repertoire.pieces.value.single().section)
    }

    @Test
    fun `an element moves between sections, never into the scales, and a stroke keeps neither author nor key`() = runTest {
        val groupId = repertoire.addGroup("Оркестр", 1)
        val id = repertoire.add(PieceDraft(title = "Легато", composer = "Шевчик", key = gMajor.key), 2)
        val form = PieceFormViewModel(SavedStateHandle(mapOf(PieceFormViewModel.ARG_PIECE_ID to id)), repertoire, config, clock)
        runCurrent()
        assertEquals(5, form.state.value.sections.size)
        assertFalse(form.state.value.sections.single { it.ref == SectionRef.BuiltIn(PieceSection.SCALES) }.enabled)

        form.onIntent(PieceFormIntent.SectionSelected(SectionRef.BuiltIn(PieceSection.SCALES)))
        assertEquals(SectionRef.BuiltIn(PieceSection.PIECES), form.state.value.section)
        form.onIntent(PieceFormIntent.SectionSelected(SectionRef.Custom(groupId)))
        assertEquals(SectionRef.Custom(groupId), form.state.value.section)
        form.onIntent(PieceFormIntent.SectionSelected(SectionRef.BuiltIn(PieceSection.STROKES)))
        form.onIntent(PieceFormIntent.SaveClicked)
        runCurrent()
        val saved = repertoire.piece(id)!!
        assertEquals(PieceSection.STROKES to null, saved.section to saved.groupId)
        assertEquals("" to null, saved.composer to saved.key)
    }

    @Test
    fun `three choices make a scale - its title, its key and its section follow`() = runTest {
        val (form, effects) = scaleForm()
        assertNull(form.state.value.scale)
        assertFalse(form.state.value.canSave)
        form.onIntent(ScaleFormIntent.TonicClicked(Tonic.G))
        form.onIntent(ScaleFormIntent.OctavesSelected(3))
        form.onIntent(ScaleFormIntent.TempoPicked(60))
        assertEquals(43, form.state.value.scale!!.notes.size)
        form.onIntent(ScaleFormIntent.SaveClicked)
        runCurrent()
        val saved = repertoire.pieces.value.single()
        assertEquals(gMajor, saved.scale)
        assertEquals(PieceSection.SCALES, saved.section)
        assertEquals(gMajor.key, saved.key)
        assertEquals("G-dur MAJOR 3", saved.title)
        assertEquals(60, saved.tempoBpm)
        assertEquals(listOf<ScaleFormEffect>(ScaleFormEffect.OpenScale(saved.id)), effects)
    }

    @Test
    fun `what cannot be drawn cannot be picked - eight signs, octaves off the instrument`() = runTest {
        val (form, _) = scaleForm()
        form.onIntent(ScaleFormIntent.AccidentalSelected(Accidental.SHARP))
        // G sharp major would have eight sharps; F sharp and C sharp are the only sharp majors
        assertEquals(setOf(Tonic.F, Tonic.C), form.state.value.tonicsAllowed)
        form.onIntent(ScaleFormIntent.TonicClicked(Tonic.G))
        assertNull(form.state.value.draft.tonic)

        form.onIntent(ScaleFormIntent.AccidentalSelected(Accidental.NATURAL))
        form.onIntent(ScaleFormIntent.TonicClicked(Tonic.G))
        form.onIntent(ScaleFormIntent.OctavesSelected(3))
        // F has no third octave: the choice comes down by itself rather than hold a scale the app refuses to draw
        form.onIntent(ScaleFormIntent.TonicClicked(Tonic.F))
        assertEquals(setOf(1, 2), form.state.value.octavesAllowed)
        assertEquals(2, form.state.value.draft.octaves)
        form.onIntent(ScaleFormIntent.OctavesSelected(3))
        assertEquals(2, form.state.value.draft.octaves)

        // a key that the new kind does not allow is dropped: As-dur is fine, as-moll has seven flats and stays, gis-dur never was
        form.onIntent(ScaleFormIntent.TonicClicked(Tonic.D))
        form.onIntent(ScaleFormIntent.AccidentalSelected(Accidental.FLAT))
        form.onIntent(ScaleFormIntent.KindSelected(ScaleKind.HARMONIC_MINOR))
        assertNull("des-moll would have eight flats", form.state.value.draft.tonic)
    }

    @Test
    fun `the same scale is not added twice - the form offers to open the one there is`() = runTest {
        val existing = repertoire.add(PieceDraft(title = "G-dur", section = PieceSection.SCALES, scale = gMajor, key = gMajor.key), 1)
        val (form, effects) = scaleForm()
        form.onIntent(ScaleFormIntent.TonicClicked(Tonic.G))
        form.onIntent(ScaleFormIntent.OctavesSelected(3))
        runCurrent()
        assertEquals(existing, form.state.value.existingId)
        assertFalse(form.state.value.canSave)
        form.onIntent(ScaleFormIntent.SaveClicked)
        form.onIntent(ScaleFormIntent.OpenExistingClicked)
        runCurrent()
        assertEquals(1, repertoire.pieces.value.size)
        assertEquals(listOf<ScaleFormEffect>(ScaleFormEffect.OpenScale(existing)), effects)

        form.onIntent(ScaleFormIntent.OctavesSelected(2))
        assertNull("two octaves are another scale", form.state.value.existingId)
    }

    @Test
    fun `a scale that exists keeps its key and kind - its octaves, tempo and status change`() = runTest {
        val id = repertoire.add(PieceDraft(title = "G-dur", section = PieceSection.SCALES, scale = gMajor, key = gMajor.key), 1)
        val (form, effects) = scaleForm(id)
        assertFalse(form.state.value.isNew)
        form.onIntent(ScaleFormIntent.TonicClicked(Tonic.A))
        form.onIntent(ScaleFormIntent.KindSelected(ScaleKind.MELODIC_MINOR))
        runCurrent()
        assertEquals(Tonic.G to ScaleKind.MAJOR, form.state.value.draft.tonic to form.state.value.draft.kind)
        assertEquals(listOf<ScaleFormEffect>(ScaleFormEffect.ShowLocked, ScaleFormEffect.ShowLocked), effects)

        form.onIntent(ScaleFormIntent.OctavesSelected(2))
        form.onIntent(ScaleFormIntent.StatusSelected(PieceStatus.IN_REPERTOIRE))
        form.onIntent(ScaleFormIntent.SaveClicked)
        runCurrent()
        val saved = repertoire.piece(id)!!
        assertEquals(gMajor.copy(octaves = 2), saved.scale)
        assertEquals("G-dur MAJOR 2", saved.title)
        assertEquals(7_000L, saved.learnedAtEpochMs)
        assertEquals(ScaleFormEffect.Close, effects.last())
    }
}
