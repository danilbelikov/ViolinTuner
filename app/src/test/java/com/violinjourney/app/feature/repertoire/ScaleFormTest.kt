package com.violinjourney.app.feature.repertoire

import androidx.lifecycle.SavedStateHandle
import com.violinjourney.app.core.domain.repertoire.Accidental
import com.violinjourney.app.core.domain.repertoire.FakeRepertoireRepository
import com.violinjourney.app.core.domain.repertoire.PieceDraft
import com.violinjourney.app.core.domain.repertoire.PieceSection
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.domain.repertoire.Tonic
import com.violinjourney.app.core.domain.repertoire.scale.ScaleKind
import com.violinjourney.app.core.domain.repertoire.scale.ScaleSpec
import com.violinjourney.app.core.time.FixedWallClock
import com.violinjourney.app.core.time.WallClock
import com.violinjourney.app.feature.repertoire.scale.ScaleFormDialog
import com.violinjourney.app.feature.repertoire.scale.ScaleFormEffect
import com.violinjourney.app.feature.repertoire.scale.ScaleFormIntent
import com.violinjourney.app.feature.repertoire.scale.ScaleFormViewModel
import com.violinjourney.app.feature.repertoire.scale.ScaleTexts
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The form of a scale keeps what the player did (spec 3.15, 3.22): «Открыть» on the scale that is there already asks
 * before it drops a tempo, a status or notes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScaleFormTest {
    private val config = RepertoireConfig()
    private val repertoire = FakeRepertoireRepository()
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
        val viewModel = ScaleFormViewModel(handle, repertoire, config, clock, texts)
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
        assertEquals(twoOctaves, form.state.value.existingId)

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
}
