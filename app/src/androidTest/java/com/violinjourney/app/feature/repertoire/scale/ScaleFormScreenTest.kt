package com.violinjourney.app.feature.repertoire.scale

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.domain.repertoire.Accidental
import com.violinjourney.app.core.domain.repertoire.PieceStatus
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.domain.repertoire.Tonic
import com.violinjourney.app.core.domain.repertoire.scale.ScaleSpec
import com.violinjourney.app.core.domain.repertoire.scale.Scales
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.repertoire.components.LocalExerciseWords
import com.violinjourney.app.feature.repertoire.components.statusLabel
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.key_pick_tonic_first
import com.violinjourney.app.shared.resources.repertoire_no_takes
import com.violinjourney.app.shared.resources.scale_kind_major
import com.violinjourney.app.shared.resources.scale_locked_reason
import com.violinjourney.app.shared.resources.scale_octaves_unfit
import com.violinjourney.app.shared.resources.scale_open_twin
import com.violinjourney.app.shared.resources.scale_tonics_gray
import com.violinjourney.app.shared.resources.scale_twin
import com.violinjourney.app.shared.resources.section_add_scale
import com.violinjourney.app.shared.resources.takes_few
import com.violinjourney.app.shared.resources.takes_many
import com.violinjourney.app.shared.resources.takes_one
import org.jetbrains.compose.resources.stringResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The form of a scale of R4 (spec 3.36.4) by its phrases and its answers: the scale that is there already turns the main button into
 * «Открыть её» under the plate that says where it is, its status — «Выучено», a scale being an exercise — and its takes, or «нет
 * дублей»; without a tonic the button sleeps under «Сначала выберите тонику»; grey tonics do not answer and are said once; the third
 * octave off the violin sleeps and names its start; in an edit the tonic, the sign and the kind sleep under the one reason of their
 * own — no line of grey tonics even where the key has them — and the octaves still answer.
 *
 * Laid out in a window of its own size ([LocalWindowInfo] of that size, a font of 1.0) — portrait 412 × 800, so that what is pressed
 * stands inside the window of a phone: the bottom zone, and in an edit the octaves of a scale of one octave (its notes take one
 * system; three octaves take three and push the octaves under the zone). What is only looked at may lie outside it. The words are
 * read in the composition, in the language the screen speaks.
 */
@RunWith(AndroidJUnit4::class)
class ScaleFormScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val intents = mutableListOf<ScaleFormIntent>()
    private val words = mutableMapOf<String, String>()
    private val config = RepertoireConfig()

    private fun word(key: String): String = words.getValue(key)

    private class Window(private val size: IntSize) : WindowInfo {
        override val isWindowFocused: Boolean = true
        override val containerSize: IntSize get() = size
    }

    @Composable
    private fun InWindow(width: Dp, height: Dp, content: @Composable () -> Unit) {
        val density = LocalDensity.current
        val window = with(density) { Window(IntSize(width.roundToPx(), height.roundToPx())) }
        CompositionLocalProvider(LocalWindowInfo provides window, LocalDensity provides Density(density.density, 1f)) {
            Box(Modifier.requiredSize(width, height).testTag(WINDOW)) { content() }
        }
    }

    private fun stateOf(draft: ScaleDraft, isNew: Boolean = true, twin: ScaleTwin? = null): ScaleFormState {
        val tonic = draft.tonic
        val scale = tonic?.let { Scales.build(ScaleSpec(it, draft.accidental, draft.kind, draft.octaves), config.scaleLowestMidi, config.scaleHighestMidi) }
        return ScaleFormState(
            loading = false,
            isNew = isNew,
            draft = draft,
            scale = scale,
            tonicsAllowed = Tonic.entries.filter { Scales.isKeyAllowed(it, draft.accidental, draft.kind) }.toSet(),
            octavesAllowed = if (tonic == null) {
                (1..Scales.MAX_OCTAVES).toSet()
            } else {
                (1..Scales.MAX_OCTAVES).filter { Scales.octavesFit(tonic, draft.accidental, it, config.scaleLowestMidi, config.scaleHighestMidi) }.toSet()
            },
            twin = twin,
            canSave = scale != null && twin == null,
            dialog = null,
            maxNotesLength = config.maxNotesLength,
            savedTitle = if (isNew) null else "G-dur · 1 октава",
            startNote = tonic?.let { ScaleWords.nameOf(Scales.lowestTonic(it, draft.accidental, config.scaleLowestMidi)) },
        )
    }

    /** The view of the composition: the window hands its insets — a keyboard among them — to it. */
    private lateinit var view: View

    private fun show(state: ScaleFormState, width: Dp = 412.dp, height: Dp = 800.dp) {
        compose.setContent {
            view = LocalView.current
            ReadWords(state)
            ViolinTheme { InWindow(width, height) { ScaleFormScreen(state = state, onIntent = { intents += it }) } }
        }
        compose.waitForIdle()
    }

    /** What the window hands its views: the keyboard alone, [height] from the bottom; none at 0. */
    private fun keyboard(height: Dp) {
        val bottom = with(compose.density) { height.roundToPx() }
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, bottom))
            .setVisible(WindowInsetsCompat.Type.ime(), bottom > 0)
            .build()
        compose.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(view, insets) }
        compose.waitForIdle()
    }

    @Composable
    private fun ReadWords(state: ScaleFormState) {
        words[ADD] = stringResource(Res.string.section_add_scale)
        words[NO_TONIC] = stringResource(Res.string.key_pick_tonic_first)
        words[GREY] = stringResource(Res.string.scale_tonics_gray)
        words[LOCKED] = stringResource(Res.string.scale_locked_reason)
        words[OPEN] = stringResource(Res.string.scale_open_twin)
        words[MAJOR] = stringResource(Res.string.scale_kind_major)
        state.startNote?.let { words[UNFIT] = stringResource(Res.string.scale_octaves_unfit, it) }
        state.twin?.let { twin ->
            val takes = if (twin.takes == 0) {
                stringResource(Res.string.repertoire_no_takes)
            } else {
                stringResource(Formats.plural(twin.takes, Res.string.takes_one, Res.string.takes_few, Res.string.takes_many), twin.takes)
            }
            // a scale is an exercise: its third step is «Выучено»
            CompositionLocalProvider(LocalExerciseWords provides true) { words[TWIN] = stringResource(Res.string.scale_twin, statusLabel(twin.status), takes) }
            // what a piece would say: «В репертуаре»
            CompositionLocalProvider(LocalExerciseWords provides false) { words[TWIN_OF_A_PIECE] = stringResource(Res.string.scale_twin, statusLabel(twin.status), takes) }
        }
    }

    @Test
    fun theScaleThatIsThereAlreadyIsOpenedUnderThePlateThatSaysWhereItIs() {
        show(stateOf(ScaleDraft(tonic = Tonic.G, octaves = 3), twin = ScaleTwin(id = 3, status = PieceStatus.LEARNING, takes = 2)))
        compose.onNodeWithText(word(TWIN)).assertExists()
        compose.onNodeWithText(word(ADD)).assertDoesNotExist()
        val plate = compose.onNodeWithText(word(TWIN)).getUnclippedBoundsInRoot()
        val button = compose.onNodeWithText(word(OPEN)).assertIsEnabled().getUnclippedBoundsInRoot()
        assertTrue("the plate over the button", plate.bottom <= button.top)
        compose.onNodeWithText(word(OPEN)).performClick()
        compose.runOnIdle { assertEquals(ScaleFormIntent.OpenExistingClicked, intents.last()) }
    }

    /** «Такая гамма уже есть — в «Гаммах», статус «Выучено», нет дублей.» — the words of an exercise, and none of a number of takes. */
    @Test
    fun theTwinLearnedWithoutTakesSaysSoInTheWordsOfAnExercise() {
        show(stateOf(ScaleDraft(tonic = Tonic.G, octaves = 3), twin = ScaleTwin(id = 3, status = PieceStatus.IN_REPERTOIRE, takes = 0)))
        assertNotEquals("the words of a scale and of a piece differ", word(TWIN), word(TWIN_OF_A_PIECE))
        compose.onNodeWithText(word(TWIN)).assertExists()
        compose.onNodeWithText(word(TWIN_OF_A_PIECE)).assertDoesNotExist()
    }

    @Test
    fun withoutATonicTheButtonSleepsUnderItsReason() {
        show(stateOf(ScaleDraft()))
        compose.onNodeWithText(word(NO_TONIC)).assertExists()
        compose.onNodeWithText(word(ADD)).assertIsNotEnabled()
    }

    @Test
    fun greyTonicsDoNotAnswerAndAreSaidOnce() {
        show(stateOf(ScaleDraft(tonic = Tonic.F, accidental = Accidental.SHARP, octaves = 2)))
        compose.onNodeWithText("G").assertIsNotEnabled()
        compose.onNodeWithText("C").assertIsEnabled()
        compose.onNodeWithText(word(GREY)).assertExists()
        compose.onNodeWithText(word(LOCKED)).assertDoesNotExist()
    }

    @Test
    fun theThirdOctaveOffTheViolinSleepsAndItsReasonNamesTheStart() {
        show(stateOf(ScaleDraft(tonic = Tonic.F, octaves = 2)))
        compose.onNodeWithText("3").assertIsNotEnabled()
        compose.onNodeWithText("2").assertIsEnabled()
        assertTrue(word(UNFIT).contains("F4"))
        compose.onNodeWithText(word(UNFIT)).assertExists()
    }

    /**
     * Es-dur — its key has a grey tonic: F would be Fes-dur, eight flats — so that the line of grey tonics would be there were the edit
     * not the reason it is (the review of stage 110: a G-dur has none, and the rule went unchecked). One octave: its notes are one
     * system, and «2» stands over the bottom zone of 412 × 800.
     */
    @Test
    fun anEditSleepsTheKeyAndTheKindUnderTheirOneReasonAndTheOctavesStillAnswer() {
        val state = stateOf(ScaleDraft(tonic = Tonic.E, accidental = Accidental.FLAT, octaves = 1, tempoBpm = 80, status = PieceStatus.LEARNING), isNew = false)
        assertTrue("Es-dur has grey tonics: ${state.tonicsAllowed}", state.tonicsAllowed.size < Tonic.entries.size)
        show(state)
        compose.onNodeWithText("G").assertIsNotEnabled()
        compose.onNodeWithText("A").assertIsNotEnabled()
        compose.onNodeWithText("♭").assertIsNotEnabled()
        compose.onNodeWithText(word(MAJOR)).assertIsNotEnabled()
        compose.onNodeWithText(word(LOCKED)).assertExists()
        compose.onNodeWithText(word(GREY)).assertDoesNotExist()
        compose.onNodeWithText("2").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(ScaleFormIntent.OctavesSelected(2), intents.last()) }
    }

    /**
     * Lying over a keyboard too high for the zone in one line and a field together (5.29 R4; the lead's finding on the emulator in
     * stage 110): the plate of the twin goes under the fields with «Открыть её» in its line — reached by scrolling, the only one, and
     * still opening the scale; the keyboard gone, the plate stands over the button, pinned again. The button, at the right of a column
     * of 892, lies outside the window of a phone: it is pressed by its action, not by a touch. On the code before the fix the line stays
     * pinned: «Открыть её» has no scroll to be reached by, and performScrollTo fails.
     */
    @Test
    fun lyingOverAHighKeyboardThePlateOfTheTwinGoesUnderTheFieldsWithItsButton() {
        show(stateOf(ScaleDraft(tonic = Tonic.G, octaves = 3), twin = ScaleTwin(id = 3, status = PieceStatus.LEARNING, takes = 2)), width = 892.dp, height = 412.dp)
        keyboard(HIGH_KEYBOARD)

        compose.onAllNodesWithText(word(OPEN)).assertCountEquals(1)
        val open = compose.onNodeWithText(word(OPEN)).performScrollTo()
        val window = compose.onNodeWithTag(WINDOW).getUnclippedBoundsInRoot()
        val button = open.getBoundsInRoot()
        assertEquals("the button of 48 whole: ${button.bottom - button.top}", 48f, (button.bottom - button.top).value, 1f)
        assertTrue("over the keyboard: ${button.bottom}", button.bottom <= window.bottom - HIGH_KEYBOARD + 1.dp)
        val plate = compose.onNodeWithText(word(TWIN)).getUnclippedBoundsInRoot()
        assertTrue("the plate on the left: ${plate.right}, the button at ${button.left}", plate.right <= button.left)
        val middle = (plate.top + plate.bottom) / 2
        assertTrue("in the line of the button", middle > button.top && middle < button.bottom)
        open.performSemanticsAction(SemanticsActions.OnClick)
        compose.runOnIdle { assertEquals(ScaleFormIntent.OpenExistingClicked, intents.last()) }

        keyboard(0.dp)
        val pinnedPlate = compose.onNodeWithText(word(TWIN)).getUnclippedBoundsInRoot()
        val pinnedButton = compose.onNodeWithText(word(OPEN)).getUnclippedBoundsInRoot()
        assertTrue("pinned again, the plate over the button", pinnedPlate.bottom <= pinnedButton.top)
    }

    private companion object {
        const val WINDOW = "window"

        /** Gboard lying on the Pixel 7 of the emulator: 412 − 48 − 262 = 102 between the bar and it. */
        val HIGH_KEYBOARD = 262.dp
        const val ADD = "add"
        const val NO_TONIC = "noTonic"
        const val GREY = "grey"
        const val LOCKED = "locked"
        const val OPEN = "open"
        const val MAJOR = "major"
        const val UNFIT = "unfit"
        const val TWIN = "twin"
        const val TWIN_OF_A_PIECE = "twinOfAPiece"
    }
}
