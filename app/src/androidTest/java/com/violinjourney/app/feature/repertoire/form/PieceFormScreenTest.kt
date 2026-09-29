package com.violinjourney.app.feature.repertoire.form

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.domain.repertoire.Accidental
import com.violinjourney.app.core.domain.repertoire.KeyMode
import com.violinjourney.app.core.domain.repertoire.MusicalKey
import com.violinjourney.app.core.domain.repertoire.PieceDraft
import com.violinjourney.app.core.domain.repertoire.PieceSection
import com.violinjourney.app.core.domain.repertoire.SectionRef
import com.violinjourney.app.core.domain.repertoire.Tonic
import com.violinjourney.app.core.ui.components.AppSheetCard
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.repertoire.sections.sectionName
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.dot_separator
import com.violinjourney.app.shared.resources.etude_delete
import com.violinjourney.app.shared.resources.form_optional
import com.violinjourney.app.shared.resources.form_section
import com.violinjourney.app.shared.resources.key_none
import com.violinjourney.app.shared.resources.key_pick_tonic_first
import com.violinjourney.app.shared.resources.piece_delete
import com.violinjourney.app.shared.resources.piece_field_composer
import com.violinjourney.app.shared.resources.piece_field_key
import com.violinjourney.app.shared.resources.piece_field_title
import com.violinjourney.app.shared.resources.piece_key_major
import com.violinjourney.app.shared.resources.piece_tempo_clear
import com.violinjourney.app.shared.resources.piece_tempo_description
import com.violinjourney.app.shared.resources.piece_tempo_faster
import com.violinjourney.app.shared.resources.piece_tempo_slower
import com.violinjourney.app.shared.resources.piece_title_error
import com.violinjourney.app.shared.resources.practice_save
import com.violinjourney.app.shared.resources.profile_done
import com.violinjourney.app.shared.resources.section_scales_only
import kotlinx.coroutines.awaitCancellation
import org.jetbrains.compose.resources.stringResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The form of a piece of R4 (spec 3.36.4) by its phrases, its answers and its layout: without a title «Сохранить» sleeps under «Без
 * названия не сохранить» from the first frame — over it, or beside it in one line where less than 200 dp are left between the bar and
 * the keyboard, that line under the fields where the keyboard leaves less than it and a field — the field in focus whole over the
 * keyboard — and over it again once the keyboard goes; the rows «Раздел» and «Тональность» are buttons that say their value and
 * open their sheets, and a swipe or «назад» on a sheet only hides it; a caption of parts is said with a comma; the tempo — «—» first
 * and chosen without a tempo, «−» and «+» pressed over 48; the delete line says the kind of the element; an edit being read shows
 * nothing to type and no reason; the sheet «Тональность» sleeps its sign and mode without a tonic, lays them side by side lying, lays
 * the tonics in one row wherever each has 48 — on a phone of 411 too — and breaks no word of its buttons on 360 at the font 1.3;
 * «Гаммы» in «Раздел» do not answer. The author field follows the draft where the view model rewrites it (a move into «Штрихи»,
 * spec 3.22).
 *
 * Every screen is laid out in a window of its own size, whatever the device's ([LocalWindowInfo] of that size — the bottom zone and
 * the sheet read it — and a font of 1.0 unless one is named); what is pressed stands inside the window of a phone. The keyboard is the
 * insets a real one hands the window, given to the view of the composition. The words are read in the composition, in the language
 * the screen speaks (the lesson of stage 107).
 */
@RunWith(AndroidJUnit4::class)
class PieceFormScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val intents = mutableListOf<PieceFormIntent>()
    private val words = mutableMapOf<String, String>()

    private fun word(key: String): String = words.getValue(key)

    private class Window(private val size: IntSize) : WindowInfo {
        override val isWindowFocused: Boolean = true
        override val containerSize: IntSize get() = size
    }

    @Composable
    private fun InWindow(width: Dp, height: Dp, fontScale: Float = 1f, content: @Composable () -> Unit) {
        val density = LocalDensity.current
        val window = with(density) { Window(IntSize(width.roundToPx(), height.roundToPx())) }
        CompositionLocalProvider(LocalWindowInfo provides window, LocalDensity provides Density(density.density, fontScale)) {
            Box(Modifier.requiredSize(width, height).testTag(WINDOW)) { content() }
        }
    }

    /** The view of the composition: the window hands its insets — a keyboard among them — to it. */
    private lateinit var view: View

    private fun stateIn(section: PieceSection, composer: String, draft: PieceDraft = PieceDraft(title = "Étude No. 2", composer = composer, section = section)) =
        PieceFormState(
            loading = false, isNew = false, draft = draft, canSave = draft.title.isNotBlank(), dialog = null, maxTitleLength = 80,
            maxComposerLength = 60, maxNotesLength = 2000, focusNotes = false, savedTitle = draft.title, section = SectionRef.BuiltIn(section),
            sections = SECTIONS,
        )

    private fun newState(draft: PieceDraft) = stateIn(draft.section, draft.composer, draft).copy(isNew = true, savedTitle = "")

    /**
     * Portrait 412 × 800 by default: every press lands inside the window of a phone (the lesson of stage 109). [typing] — a field is to
     * take the focus: no real keyboard comes up for it — the input method is intercepted, as the sample of Compose that disables the
     * soft keyboard does — and the keyboard is only what [keyboard] hands the window. [statusBar] — the room the screen's host keeps at
     * the top of the window, the form under it.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun show(state: PieceFormState, width: Dp = 412.dp, height: Dp = 800.dp, typing: Boolean = false, statusBar: Dp = 0.dp) {
        compose.setContent {
            view = LocalView.current
            ReadWords()
            ViolinTheme {
                InWindow(width, height) {
                    val form = Modifier.padding(top = statusBar)
                    if (typing) {
                        InterceptPlatformTextInput(interceptor = { _, _ -> awaitCancellation() }) {
                            PieceFormScreen(state = state, onIntent = { intents += it }, modifier = form)
                        }
                    } else {
                        PieceFormScreen(state = state, onIntent = { intents += it }, modifier = form)
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** What the window hands its views: the keyboard alone, [bottom] px from the bottom; none at 0. */
    private fun keyboardInsets(bottom: Int): WindowInsetsCompat = WindowInsetsCompat.Builder()
        .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, bottom))
        .setVisible(WindowInsetsCompat.Type.ime(), bottom > 0)
        .build()

    private fun keyboard(height: Dp) {
        val bottom = with(compose.density) { height.roundToPx() }
        compose.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(view, keyboardInsets(bottom)) }
        compose.waitForIdle()
    }

    /** Past the 150 ms the keyboard stands still for, and the scroll that brings the field in focus up. */
    private fun settle() {
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.waitForIdle()
    }

    @Composable
    private fun ReadWords() {
        words[REASON] = stringResource(Res.string.piece_title_error)
        words[SAVE] = stringResource(Res.string.practice_save)
        words[TITLE] = stringResource(Res.string.piece_field_title)
        words[KEY_ROW] = stringResource(Res.string.piece_field_key)
        words[SECTION_ROW] = stringResource(Res.string.form_section)
        words[PIECES] = sectionName(SectionRef.BuiltIn(PieceSection.PIECES), null)
        words[SCALES] = sectionName(SectionRef.BuiltIn(PieceSection.SCALES), null)
        words[ETUDES] = sectionName(SectionRef.BuiltIn(PieceSection.ETUDES), null)
        words[COMPOSER_SHOWN] = stringResource(Res.string.piece_field_composer) + stringResource(Res.string.dot_separator) + stringResource(Res.string.form_optional)
        words[COMPOSER_SAID] = stringResource(Res.string.piece_field_composer) + ", " + stringResource(Res.string.form_optional)
        words[DELETE_PIECE] = stringResource(Res.string.piece_delete)
        words[DELETE_ETUDE] = stringResource(Res.string.etude_delete)
        words[NO_TONIC] = stringResource(Res.string.key_pick_tonic_first)
        words[NO_KEY] = stringResource(Res.string.key_none)
        words[DONE] = stringResource(Res.string.profile_done)
        words[MAJOR] = stringResource(Res.string.piece_key_major)
        words[SCALES_ONLY] = stringResource(Res.string.section_scales_only)
        words[SLOWER] = stringResource(Res.string.piece_tempo_slower)
        words[FASTER] = stringResource(Res.string.piece_tempo_faster)
        words[NO_TEMPO] = stringResource(Res.string.piece_tempo_clear)
        words[TEMPO_80] = stringResource(Res.string.piece_tempo_description, QUICK_TEMPO)
    }

    /** The words of a button are merged into it: the node that says them is the button. */
    private fun saveButton() = compose.onNodeWithText(word(SAVE))

    @Test
    fun aMoveIntoStrokesAndBackShowsTheAuthorThatWillBeSaved() {
        var state by mutableStateOf(stateIn(PieceSection.ETUDES, composer = "Kreutzer"))
        compose.setContent { ViolinTheme { PieceFormScreen(state = state, onIntent = {}) } }
        compose.onNodeWithText("Kreutzer").assertExists()

        // what the view model does on a move into «Штрихи» and back to «Этюды»
        compose.runOnIdle { state = stateIn(PieceSection.STROKES, composer = "") }
        compose.waitForIdle()
        compose.runOnIdle { state = stateIn(PieceSection.ETUDES, composer = "") }
        compose.waitForIdle()
        compose.onNodeWithText("Kreutzer").assertDoesNotExist()
    }

    @Test
    fun typedAuthorStaysWhenTheSectionChangesOutsideStrokes() {
        var state by mutableStateOf(stateIn(PieceSection.ETUDES, composer = "Kreutzer"))
        compose.setContent {
            ViolinTheme {
                PieceFormScreen(
                    state = state,
                    onIntent = { intent -> if (intent is PieceFormIntent.ComposerChanged) state = state.copy(draft = state.draft.copy(composer = intent.text)) },
                )
            }
        }
        compose.onNodeWithText("Kreutzer").performTextReplacement("Kayser")
        compose.runOnIdle { state = stateIn(PieceSection.PIECES, composer = state.draft.composer) }
        compose.waitForIdle()
        compose.onNodeWithText("Kayser").assertExists()
    }

    @Test
    fun aNewPieceWithoutATitleSaysWhyOverItsSleepingButtonFromTheFirstFrame() {
        show(newState(PieceDraft()))
        compose.onNodeWithText(word(REASON)).assertExists()
        saveButton().assertIsNotEnabled()
        val reason = compose.onNodeWithText(word(REASON)).getUnclippedBoundsInRoot()
        val button = saveButton().getUnclippedBoundsInRoot()
        assertTrue("the reason stands over the button", reason.bottom <= button.top)
        saveButton().performClick()
        compose.runOnIdle { assertTrue("a sleeping button sends nothing", intents.none { it == PieceFormIntent.SaveClicked }) }
    }

    @Test
    fun aTitleWakesTheButtonAndTakesTheReasonAway() {
        show(newState(PieceDraft(title = "Менуэт соль мажор")))
        compose.onNodeWithText(word(REASON)).assertDoesNotExist()
        saveButton().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(PieceFormIntent.SaveClicked, intents.last()) }
    }

    /** Lying over a keyboard: less than 200 dp between the bar (48 lying) and the bottom — the reason and the button stand in one line. */
    @Test
    fun withLessThan200LeftTheReasonAndTheButtonStandInOneLine() {
        show(newState(PieceDraft()), width = 892.dp, height = 240.dp)
        val reason = compose.onNodeWithText(word(REASON)).getUnclippedBoundsInRoot()
        val button = saveButton().getUnclippedBoundsInRoot()
        assertTrue("the reason on the left", reason.right <= button.left)
        val middle = (reason.top + reason.bottom) / 2
        assertTrue("in the line of the button", middle > button.top && middle < button.bottom)
        assertEquals("a button of 48", 48f, (button.bottom - button.top).value, 1f)
    }

    @Test
    fun with200AndMoreTheReasonStandsOverTheButton() {
        show(newState(PieceDraft()), width = 892.dp, height = 412.dp)
        val reason = compose.onNodeWithText(word(REASON)).getUnclippedBoundsInRoot()
        val button = saveButton().getUnclippedBoundsInRoot()
        assertTrue(reason.bottom <= button.top)
    }

    private fun assertTheReasonOverTheButtonOf56() {
        val reason = compose.onNodeWithText(word(REASON)).getUnclippedBoundsInRoot()
        val button = saveButton().getUnclippedBoundsInRoot()
        assertTrue("the reason over the button: ${reason.bottom}, the button at ${button.top}", reason.bottom <= button.top)
        assertEquals("a button of 56", 56f, (button.bottom - button.top).value, 1f)
    }

    /**
     * Lying over a keyboard (the review of stage 110): what counts is the room the keyboard leaves between the bar and it — 412 − 48
     * − 200 = 164, under 200: one line; the keyboard gone, 364 — the reason over the button of 56 again. The keyboard of the frame is
     * the insets of the window less what the host of the screen has taken: here nothing.
     */
    @Test
    fun lyingOverAKeyboardTheZoneIsOneLineAndAColumnAgainOnceItGoes() {
        show(newState(PieceDraft()), width = 892.dp, height = 412.dp)
        assertTheReasonOverTheButtonOf56()

        keyboard(LYING_KEYBOARD)
        val reason = compose.onNodeWithText(word(REASON)).getUnclippedBoundsInRoot()
        val button = saveButton().getUnclippedBoundsInRoot()
        assertTrue("the reason on the left: ${reason.right}, the button at ${button.left}", reason.right <= button.left)
        val middle = (reason.top + reason.bottom) / 2
        assertTrue("in the line of the button", middle > button.top && middle < button.bottom)
        assertEquals("a button of 48", 48f, (button.bottom - button.top).value, 1f)

        keyboard(0.dp)
        assertTheReasonOverTheButtonOf56()
    }

    /**
     * Lying over a keyboard too high for the zone in one line and a field together (the lead's finding on the emulator, stage 110:
     * Pixel 7 lying, Gboard ≈ 262 dp — the line stayed pinned and left the title in focus a strip of 16 dp, and the words were typed
     * unseen). 412 − 48 − 262 = 102 dp between the bar and the keyboard, less than the line (8 + 48 + 10) and the field at the top of
     * the column (8 + 82): the line goes under the fields. The title in focus stands whole over the keyboard and keeps its focus while
     * the zone moves; the keyboard's «Далее» brings the author up, whole too; «Сохранить» — asleep, its reason beside it, the only one
     * — is reached by scrolling to the end of the fields; the keyboard gone, the zone is pinned again, the reason over the button of 56.
     *
     * Can fail on the code before the fix: there the line stays pinned and leaves the fields 102 − 66 = 36 dp over it — the title, at
     * the top of the column, is cut to 28 of its 82, and the first check fails (and «Сохранить», pinned, has no scroll to be reached by).
     */
    @Test
    fun lyingOverAKeyboardTooHighForTheLineAndAFieldTheZoneGoesUnderTheFields() {
        show(newState(PieceDraft()), width = 892.dp, height = 412.dp, typing = true)
        val title = compose.onNode(hasSetTextAction() and hasText(word(TITLE)))
        title.performClick()
        title.assertIsFocused()

        keyboard(HIGH_KEYBOARD)
        settle()
        val window = compose.onNodeWithTag(WINDOW).getUnclippedBoundsInRoot()
        val keyboardTop = window.bottom - HIGH_KEYBOARD
        title.assertIsFocused()
        assertWholeOverTheKeyboard(title, keyboardTop, "the title in focus")

        // the keyboard's «Далее»: the next field takes the focus and comes up to the top of the column, whole in the room
        title.performImeAction()
        val author = compose.onNode(hasSetTextAction() and hasText(word(COMPOSER_SAID)))
        author.assertIsFocused()
        settle()
        assertWholeOverTheKeyboard(author, keyboardTop, "the author in focus")

        compose.onAllNodesWithText(word(SAVE)).assertCountEquals(1)
        saveButton().performScrollTo().assertIsNotEnabled()
        val button = saveButton().getBoundsInRoot()
        assertEquals("the button of 48 whole: ${button.bottom - button.top}", 48f, (button.bottom - button.top).value, 1f)
        assertTrue("over the keyboard: ${button.bottom}, the keyboard at $keyboardTop", button.bottom <= keyboardTop + 1.dp)
        assertTrue("under the bar: ${button.top}, the window at ${window.top}", button.top >= window.top + LYING_BAR - 1.dp)
        val reason = compose.onNodeWithText(word(REASON)).getUnclippedBoundsInRoot()
        assertTrue("the reason on the left: ${reason.right}, the button at ${button.left}", reason.right <= button.left)
        val middle = (reason.top + reason.bottom) / 2
        assertTrue("in the line of the button", middle > button.top && middle < button.bottom)

        keyboard(0.dp)
        assertTheReasonOverTheButtonOf56()
    }

    /**
     * The smallest window lying (the lead's finding on the emulator, stage 110: 640 × 360, the bar ending ≈ 70 dp from the top under the
     * status bar, the top of Gboard at 125 — 55 dp between them): that room is lower than the title with its caption, and the line being
     * typed wins — the frame's bottom, the line typed over its padding, stands on the keyboard, and the caption goes up under the bar.
     * The status bar is the room the host keeps at the top, 25: 360 − 25 − 48 − 230 = 57 dp between the bar and the keyboard. The line
     * typed is the text's own line, as the layout of the text says it, in the middle of the frame — see [typedLineOf].
     *
     * Can fail on the code before the fix: there the column, shrunk under the title in focus, brings the whole field in by its nearest
     * edge — the top: the caption stands whole, the frame runs 25 dp under the keyboard, and the line typed is cut, 15 of its 24 dp over
     * the keyboard — the first check fails.
     */
    @Test
    fun onTheSmallestWindowLyingTheLineBeingTypedWinsOverTheCaption() {
        show(stateIn(PieceSection.PIECES, composer = "", PieceDraft(title = TYPED)), width = 640.dp, height = 360.dp, typing = true, statusBar = STATUS_BAR)
        val title = compose.onNode(hasSetTextAction() and hasText(word(TITLE)))
        title.performClick()
        title.assertIsFocused()

        keyboard(LOW_WINDOW_KEYBOARD)
        settle()
        title.assertIsFocused()
        val window = compose.onNodeWithTag(WINDOW).getUnclippedBoundsInRoot()
        val barBottom = window.top + STATUS_BAR + LYING_BAR
        val keyboardTop = window.bottom - LOW_WINDOW_KEYBOARD
        val line = typedLineOf(title)
        assertTrue("the line typed over the keyboard: ${line.bottom}, the keyboard at $keyboardTop", line.bottom <= keyboardTop + 1.dp)
        assertTrue("the line typed under the bar: ${line.top}, the bar at $barBottom", line.top >= barBottom - 1.dp)
        val whole = title.getUnclippedBoundsInRoot()
        assertEquals("the bottom of the frame on the keyboard: ${whole.bottom}", keyboardTop.value, whole.bottom.value, 1f)
        assertTrue("the caption gone up under the bar: ${whole.top}, the bar at $barBottom", whole.top < barBottom)
    }

    /**
     * The line typed in a field of one line with nothing under its frame, as the title is: the line of its text — the layout of the
     * text, as the field's semantics hands it — in the middle of the frame (OutlinedTextField stands one line so), the frame at the
     * bottom of the field and as high as OutlinedTextField makes it for that line.
     */
    private fun typedLineOf(field: SemanticsNodeInteraction): DpRect {
        val layouts = mutableListOf<TextLayoutResult>()
        field.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val text = layouts.first()
        val line = with(compose.density) { (text.getLineBottom(0) - text.getLineTop(0)).toDp() }
        val padding = OutlinedTextFieldDefaults.contentPadding()
        val frame = maxOf(OutlinedTextFieldDefaults.MinHeight, padding.calculateTopPadding() + line + padding.calculateBottomPadding())
        val whole = field.getUnclippedBoundsInRoot()
        val bottom = whole.bottom - (frame - line) / 2
        return DpRect(whole.left, bottom - line, whole.right, bottom)
    }

    /** Nothing of the field cut by the edges of the column it scrolls in, and all of it over the keyboard. */
    private fun assertWholeOverTheKeyboard(field: SemanticsNodeInteraction, keyboardTop: Dp, what: String) {
        val whole = field.getUnclippedBoundsInRoot()
        val shown = field.getBoundsInRoot()
        assertEquals(
            "$what whole: ${shown.bottom - shown.top} shown of ${whole.bottom - whole.top}",
            (whole.bottom - whole.top).value,
            (shown.bottom - shown.top).value,
            1f,
        )
        assertTrue("$what over the keyboard: ${shown.bottom}, the keyboard at $keyboardTop", shown.bottom <= keyboardTop + 1.dp)
    }

    @Test
    fun theRowsAreButtonsThatSayTheirValueAndOpenTheirSheets() {
        show(newState(PieceDraft(title = "Менуэт", key = MusicalKey(Tonic.G, Accidental.NATURAL, KeyMode.MAJOR))))
        compose.onNodeWithContentDescription("${word(KEY_ROW)}, G-dur").assert(hasClickAction()).performClick()
        compose.onNodeWithContentDescription("${word(SECTION_ROW)}, ${word(PIECES)}").performClick()
        compose.runOnIdle {
            assertEquals(listOf(PieceFormIntent.KeyRowClicked, PieceFormIntent.SectionRowClicked), intents.filter { it is PieceFormIntent.KeyRowClicked || it is PieceFormIntent.SectionRowClicked })
        }
    }

    @Test
    fun withoutAKeyTheRowSaysOnlyItsName() {
        show(newState(PieceDraft(title = "Менуэт")))
        compose.onNodeWithContentDescription(word(KEY_ROW)).assertExists()
    }

    @Test
    fun anOptionalFieldIsSaidWithACommaAndShownWithADot() {
        show(newState(PieceDraft(title = "Менуэт")))
        compose.onNode(hasSetTextAction() and hasText(word(COMPOSER_SAID))).assertExists()
        // the caption drawn with its dot is said in its own words only: nobody reads «·» aloud
        compose.onAllNodesWithText(word(COMPOSER_SHOWN), useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun theDeleteLineSaysTheKindOfTheElement() {
        show(stateIn(PieceSection.ETUDES, composer = "Kreutzer"), height = 1400.dp)
        compose.onNodeWithText(word(DELETE_ETUDE)).performClick()
        compose.onNodeWithText(word(DELETE_PIECE)).assertDoesNotExist()
        compose.runOnIdle { assertEquals(PieceFormIntent.DeleteClicked, intents.last()) }
    }

    @Test
    fun anEditBeingReadShowsNothingToTypeAndNoReason() {
        show(stateIn(PieceSection.PIECES, composer = "", PieceDraft()).copy(loading = true))
        compose.onNodeWithText(word(REASON)).assertDoesNotExist()
        compose.onNodeWithText(word(SAVE)).assertDoesNotExist()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
    }

    /** The content of the sheet in a column [width] wide — the sheet's own less its fields — in a window [window] wide. */
    private fun showKeySheet(key: MusicalKey?, width: Dp = 412.dp, window: Dp = width, height: Dp = 800.dp, fontScale: Float = 1f) {
        compose.setContent {
            ReadWords()
            ViolinTheme {
                InWindow(window, height, fontScale) {
                    Column(Modifier.width(width)) {
                        KeySheetContent(key, onIntent = { intents += it })
                        KeySheetButtons(onIntent = { intents += it })
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun layoutOf(text: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single()
    }

    /** [text] goes on to a next line only at a space, and all of it is shown: no word breaks by the letter, none is cut away. */
    private fun assertWordsWhole(text: String) {
        val layout = layoutOf(text)
        for (line in 0 until layout.lineCount - 1) {
            val end = layout.getLineEnd(line)
            assertTrue("«$text» breaks inside a word after «${text.substring(0, end)}»", text[end - 1].isWhitespace())
        }
        assertEquals("«$text» is cut after line ${layout.lineCount}", text.length, layout.getLineEnd(layout.lineCount - 1))
    }

    @Test
    fun withoutATonicTheSignAndTheModeSleepUnderTheirReason() {
        showKeySheet(key = null)
        compose.onNodeWithText("♭").assertIsNotEnabled()
        compose.onNodeWithText(word(MAJOR)).assertIsNotEnabled()
        compose.onNodeWithText(word(NO_TONIC)).assertExists()
        compose.onNodeWithText("D").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(PieceFormIntent.TonicClicked(Tonic.D), intents.last()) }
    }

    @Test
    fun withATonicTheKeyStandsLargeAndTheButtonsSayTheirWords() {
        showKeySheet(key = MusicalKey(Tonic.A, Accidental.NATURAL, KeyMode.MINOR))
        compose.onNodeWithText("a-moll").assertExists()
        compose.onNodeWithText("A").assertIsSelected()
        compose.onNodeWithText(word(NO_TONIC)).assertDoesNotExist()
        compose.onNodeWithText("♯").assertIsEnabled()
        compose.onNodeWithText(word(NO_KEY)).performClick()
        compose.onNodeWithText(word(DONE)).performClick()
        compose.runOnIdle { assertEquals(listOf(PieceFormIntent.KeyCleared, PieceFormIntent.SheetHidden), intents.takeLast(2)) }
    }

    private fun assertSevenInOneRowOf48() {
        val tonics = TONICS.map { compose.onNodeWithText(it).getUnclippedBoundsInRoot() }
        assertTrue("one row: ${tonics.map { it.top }}", tonics.all { it.top == tonics.first().top })
        // laid out in whole pixels: a button of 48 may come out a part of a pixel narrower
        tonics.forEach { assertTrue("a button of ${it.right - it.left}", (it.right - it.left).value >= 47.5f) }
    }

    @Test
    fun sevenTonicsStandInOneRowFrom360() {
        showKeySheet(key = null, width = 360.dp)
        assertSevenInOneRowOf48()
    }

    @Test
    fun narrowerTheTonicsStandInTwoRowsOfTheSameButtons() {
        showKeySheet(key = null, width = 359.dp)
        val c = compose.onNodeWithText("C").getUnclippedBoundsInRoot()
        val g = compose.onNodeWithText("G").getUnclippedBoundsInRoot()
        assertTrue("G under C", g.top >= c.bottom)
        assertEquals("the same width", (c.right - c.left).value, (g.right - g.left).value, 0.5f)
        assertTrue("none under 48", (c.right - c.left).value >= 48f)
        assertEquals("G under C, the first of its row", c.left.value, g.left.value, 0.5f)
    }

    /**
     * The sheet as the phone lays it — its card, its fields of 20 — on a phone of 411.43 dp (1080 px at 420 dpi, the Pixel 7 of the
     * emulator): 371 inside the fields, and the seven still stand in one row of 48 (the review of stage 110; the rule was a row of 372).
     */
    @Test
    fun onAPhoneOf411TheSheetKeepsTheSevenInOneRow() {
        compose.setContent {
            ReadWords()
            ViolinTheme {
                InWindow(PHONE_411, 800.dp) {
                    AppSheetCard(bottom = { KeySheetButtons(onIntent = {}) }) { KeySheetContent(key = null, onIntent = {}) }
                }
            }
        }
        compose.waitForIdle()
        assertSevenInOneRowOf48()
    }

    /** Lying the sign and the mode stand side by side, so that the reason under them is in sight in 412 without scrolling. */
    @Test
    fun lyingTheSignAndTheModeStandSideBySideWithTheReasonUnderThem() {
        showKeySheet(key = null, width = 600.dp, window = 892.dp, height = 412.dp)
        val sign = compose.onNodeWithText("♭").getUnclippedBoundsInRoot()
        val mode = compose.onNodeWithText(word(MAJOR)).getUnclippedBoundsInRoot()
        val reason = compose.onNodeWithText(word(NO_TONIC)).getUnclippedBoundsInRoot()
        assertTrue("the mode beside the sign: ${mode.left}, the sign ends at ${sign.right}", mode.left >= sign.right)
        assertEquals("in one row", (sign.top + sign.bottom).value / 2, (mode.top + mode.bottom).value / 2, 1f)
        assertTrue("the reason under both", reason.top >= sign.bottom && reason.top >= mode.bottom)
    }

    @Test
    fun uprightTheModeStandsUnderTheSign() {
        showKeySheet(key = null)
        val sign = compose.onNodeWithText("♭").getUnclippedBoundsInRoot()
        val mode = compose.onNodeWithText(word(MAJOR)).getUnclippedBoundsInRoot()
        assertTrue("the mode under the sign", mode.top >= sign.bottom)
    }

    /**
     * On 360 the halves of the buttons have 155 − 2 · 16 = 123 for their words; «тональности» at the font 1.3 — 127 at 15 sp — would
     * break by the letter and lose its last one (the review of stage 110): both buttons step down together, and no word breaks.
     */
    @Test
    fun on360WithALargeFontTheButtonsOfTheSheetBreakNoWord() {
        showKeySheet(key = null, width = 320.dp, window = 360.dp, height = 640.dp, fontScale = LARGE_FONT)
        assertWordsWhole(word(NO_KEY))
        assertWordsWhole(word(DONE))
        val none = compose.onNodeWithText(word(NO_KEY)).getUnclippedBoundsInRoot()
        val done = compose.onNodeWithText(word(DONE)).getUnclippedBoundsInRoot()
        assertEquals("in one row", none.top.value, done.top.value, 1f)
        assertEquals("of one height", (none.bottom - none.top).value, (done.bottom - done.top).value, 1f)
        compose.onNodeWithText(word(NO_KEY)).performClick()
        compose.runOnIdle { assertEquals(PieceFormIntent.KeyCleared, intents.last()) }
    }

    /** The form with its sheet up, as the view model says; every hide only takes the sheet down, as the view model does. */
    private fun showWithSheet(sheet: PieceFormSheet) {
        var state by mutableStateOf(newState(PieceDraft(title = "Менуэт", key = MusicalKey(Tonic.G, Accidental.NATURAL, KeyMode.MAJOR))).copy(sheet = sheet))
        compose.setContent {
            ReadWords()
            ViolinTheme {
                InWindow(412.dp, 800.dp) {
                    PieceFormScreen(state = state, onIntent = { intent ->
                        intents += intent
                        if (intent == PieceFormIntent.SheetHidden) state = state.copy(sheet = null)
                    })
                }
            }
        }
        compose.waitForIdle()
    }

    /** Spec 3.36.4 and the rule of the owner: «назад» on a sheet only closes it — the key picked stays, nothing else is heard. */
    @Test
    fun backOnTheSheetOfTheKeyOnlyHidesIt() {
        showWithSheet(PieceFormSheet.KEY)
        compose.onNodeWithText("G-dur").assertExists()
        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithText(word(DONE)).assertDoesNotExist()
        compose.runOnIdle { assertEquals("only hidden: no «Без тональности», no «✕» of the form", listOf<PieceFormIntent>(PieceFormIntent.SheetHidden), intents) }
    }

    @Test
    fun aSwipeOnTheSheetOfTheKeyOnlyHidesIt() {
        showWithSheet(PieceFormSheet.KEY)
        compose.onNodeWithText("G-dur").performTouchInput {
            swipe(start = center, end = Offset(center.x, center.y + SWIPE.dp.toPx()), durationMillis = SWIPE_MS)
        }
        compose.waitForIdle()
        compose.onNodeWithText(word(DONE)).assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf<PieceFormIntent>(PieceFormIntent.SheetHidden), intents) }
    }

    @Test
    fun theSheetOfTheSectionHasNoButtonsOfTheKey() {
        showWithSheet(PieceFormSheet.SECTION)
        compose.onNodeWithText(word(ETUDES)).assertExists()
        compose.onNodeWithText(word(DONE)).assertDoesNotExist()
        compose.onNodeWithText(word(NO_KEY)).assertDoesNotExist()
    }

    /** Spec 3.36.4, 5.29 R4: «—» first among the quick tempos, chosen while there is no tempo, and it takes the tempo away. */
    @Test
    fun withoutATempoTheDashIsChosenFirstAndTakesTheTempoAway() {
        show(newState(PieceDraft(title = "Менуэт")))
        val dash = compose.onNode(hasContentDescription(word(NO_TEMPO)) and isSelectable())
        val eighty = compose.onNode(hasContentDescription(word(TEMPO_80)) and isSelectable())
        dash.assertIsSelected()
        eighty.assertIsNotSelected()
        val dashBounds = dash.getUnclippedBoundsInRoot()
        val eightyBounds = eighty.getUnclippedBoundsInRoot()
        assertTrue("«—» first: ${dashBounds.left}, «80» at ${eightyBounds.left}", dashBounds.right <= eightyBounds.left)
        assertEquals("in one row", dashBounds.top.value, eightyBounds.top.value, 1f)
        dash.performClick()
        compose.runOnIdle { assertEquals(PieceFormIntent.TempoPicked(null), intents.last()) }
        eighty.performClick()
        compose.runOnIdle { assertEquals(PieceFormIntent.TempoPicked(QUICK_TEMPO), intents.last()) }
    }

    /**
     * «−» and «+» are 44 wide to the eye and pressed over 48 × 48 (5.29 R4): a touch 1 dp outside a sign, in the 2 dp Compose widens
     * a target under 48 by, steps. Not the touch bounds of the node — Compose reports 48 there whatever the stepper lays out (the
     * lesson of stage 101) — but a real touch at the root: a clip of the stepper would take it away.
     */
    @Test
    fun theSignsOfTheTempoAnswerATouchJustOutsideThem() {
        show(newState(PieceDraft(title = "Менуэт", tempoBpm = 100)))
        val down = compose.onNodeWithContentDescription(word(SLOWER)).getUnclippedBoundsInRoot()
        val up = compose.onNodeWithContentDescription(word(FASTER)).getUnclippedBoundsInRoot()
        assertEquals("«−» 44 wide", 44f, (down.right - down.left).value, 0.5f)
        compose.onRoot().performTouchInput { click(Offset((down.left - OUTSIDE).toPx(), ((down.top + down.bottom) / 2).toPx())) }
        compose.runOnIdle { assertEquals(PieceFormIntent.TempoStepped(-1), intents.last()) }
        compose.onRoot().performTouchInput { click(Offset((up.right + OUTSIDE).toPx(), ((up.top + up.bottom) / 2).toPx())) }
        compose.runOnIdle { assertEquals(PieceFormIntent.TempoStepped(1), intents.last()) }
    }

    @Test
    fun theScalesDoNotAnswerInTheSheetOfTheSectionAndTheCurrentOneIsChecked() {
        compose.setContent {
            ReadWords()
            ViolinTheme {
                InWindow(412.dp, 800.dp) {
                    Column { SectionSheetContent(newState(PieceDraft(title = "Менуэт")), onIntent = { intents += it }) }
                }
            }
        }
        compose.waitForIdle()
        compose.onNode(hasText(word(SCALES)) and hasText(word(SCALES_ONLY))).assertIsNotEnabled()
        compose.onNodeWithText(word(PIECES)).assertIsSelected()
        compose.onNodeWithText(word(ETUDES)).performClick()
        compose.runOnIdle { assertEquals(PieceFormIntent.SectionSelected(SectionRef.BuiltIn(PieceSection.ETUDES)), intents.last()) }
    }

    private companion object {
        const val WINDOW = "window"
        const val REASON = "reason"
        const val SAVE = "save"
        const val TITLE = "title"
        const val KEY_ROW = "keyRow"
        const val SECTION_ROW = "sectionRow"
        const val PIECES = "pieces"
        const val SCALES = "scales"
        const val ETUDES = "etudes"
        const val COMPOSER_SHOWN = "composerShown"
        const val COMPOSER_SAID = "composerSaid"
        const val DELETE_PIECE = "deletePiece"
        const val DELETE_ETUDE = "deleteEtude"
        const val NO_TONIC = "noTonic"
        const val NO_KEY = "noKey"
        const val DONE = "done"
        const val MAJOR = "major"
        const val SCALES_ONLY = "scalesOnly"
        const val SLOWER = "slower"
        const val FASTER = "faster"
        const val NO_TEMPO = "noTempo"
        const val TEMPO_80 = "tempo80"
        const val QUICK_TEMPO = 80

        val TONICS = listOf("C", "D", "E", "F", "G", "A", "B")

        /** A keyboard lying on 892 × 412: 412 − 48 − 200 = 164 between the bar and it. */
        val LYING_KEYBOARD = 200.dp

        /** Gboard lying on the Pixel 7 of the emulator: 412 − 48 − 262 = 102 between the bar and it. */
        val HIGH_KEYBOARD = 262.dp
        val LYING_BAR = 48.dp
        const val SETTLE_MS = 600L

        /** 640 × 360 lying: the status bar the host keeps at the top, and the keyboard — 360 − 25 − 48 − 230 = 57 between the bar and it. */
        val STATUS_BAR = 25.dp
        val LOW_WINDOW_KEYBOARD = 230.dp
        const val TYPED = "Concerto No. 2, 3rd mvt"

        /** 1080 px at 420 dpi. */
        val PHONE_411 = 411.43.dp
        const val LARGE_FONT = 1.3f

        /** A touch this far outside a sign of 44: inside the 2 dp Compose widens it by to 48. */
        val OUTSIDE = 1.dp

        /** A swipe down the sheet, as in AppSheetTest. */
        const val SWIPE = 700
        const val SWIPE_MS = 150L

        val SECTIONS = listOf(
            SectionOption(SectionRef.BuiltIn(PieceSection.PIECES), null, enabled = true),
            SectionOption(SectionRef.BuiltIn(PieceSection.SCALES), null, enabled = false),
            SectionOption(SectionRef.BuiltIn(PieceSection.ETUDES), null, enabled = true),
            SectionOption(SectionRef.BuiltIn(PieceSection.STROKES), null, enabled = true),
        )
    }
}
