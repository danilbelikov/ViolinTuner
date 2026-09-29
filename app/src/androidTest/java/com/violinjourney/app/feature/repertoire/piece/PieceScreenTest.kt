package com.violinjourney.app.feature.repertoire.piece

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.domain.backing.AudioRoute
import com.violinjourney.app.core.domain.backing.BackingOutput
import com.violinjourney.app.core.domain.repertoire.PieceStatus
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.history.HistoryCard
import com.violinjourney.app.feature.repertoire.components.statusLabel
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.backing_add
import com.violinjourney.app.shared.resources.backing_chip
import com.violinjourney.app.shared.resources.backing_headphones_none
import com.violinjourney.app.shared.resources.backing_headphones_wired
import com.violinjourney.app.shared.resources.backing_needs_headphones
import com.violinjourney.app.shared.resources.backing_or_turn_off
import com.violinjourney.app.shared.resources.piece_edit
import com.violinjourney.app.shared.resources.piece_notes_add
import com.violinjourney.app.shared.resources.piece_sheets_camera
import com.violinjourney.app.shared.resources.piece_sheets_gallery
import com.violinjourney.app.shared.resources.piece_tempo_description
import com.violinjourney.app.shared.resources.record_stop
import com.violinjourney.app.shared.resources.selection_select
import com.violinjourney.app.shared.resources.stand_recording_description
import com.violinjourney.app.shared.resources.take_chart_description
import com.violinjourney.app.shared.resources.take_grant_permission
import com.violinjourney.app.shared.resources.take_no_permission
import com.violinjourney.app.shared.resources.take_progress_description
import com.violinjourney.app.shared.resources.take_record
import com.violinjourney.app.shared.resources.takes_empty_title
import com.violinjourney.app.shared.resources.takes_title
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The screen of an element of R4 (spec 3.36.4) by its phrases, its answers and its layout: the status is a switch whose step is
 * sent at once, during a take too; «Записать дубль» sleeps under the line «нет разрешения» (which asks) or under a plate with its
 * way out, and the capsule «С минусовкой» is a switch pressed over 48; a running take is the bar that says its time and stops with
 * «стоп», while «Изменить» sleeps; until the backing is read there is no key and no switch; without takes — «Дублей пока нет» and no
 * title; the summary of the takes is one phrase; lying, the zone is the left column of 300, and the line of what is set goes in a
 * window lower than 380.
 *
 * Every screen is laid out in a window of its own size, whatever the device's ([LocalWindowInfo] of that size — the bottom zone reads
 * it — and a font of 1.0); the words are read in the composition, in the language the screen speaks (the lesson of stage 107).
 */
@RunWith(AndroidJUnit4::class)
class PieceScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val intents = mutableListOf<PieceIntent>()
    private val photos = mutableListOf<String>()
    private val words = mutableMapOf<String, String>()

    private fun word(key: String): String = words.getValue(key)

    private class Window(private val size: IntSize) : WindowInfo {
        override val isWindowFocused: Boolean = true
        override val containerSize: IntSize get() = size
    }

    @Composable
    private fun InWindow(width: Dp, height: Dp, fontScale: Float, content: @Composable () -> Unit) {
        val density = LocalDensity.current
        val window = with(density) { Window(IntSize(width.roundToPx(), height.roundToPx())) }
        CompositionLocalProvider(LocalWindowInfo provides window, LocalDensity provides Density(density.density, fontScale)) {
            Box(Modifier.requiredSize(width, height).testTag(WINDOW)) { content() }
        }
    }

    /** The take the screen shows; a test may start one under the screen it has shown. */
    private val takeState = mutableStateOf(IDLE)

    private fun show(
        state: PieceState,
        backing: BackingUi? = BUDS,
        take: TakeState = IDLE,
        width: Dp = 412.dp,
        height: Dp = 800.dp,
        fontScale: Float = 1f,
    ) {
        takeState.value = take
        compose.setContent {
            ReadWords()
            ViolinTheme {
                InWindow(width, height, fontScale) {
                    PieceScreen(
                        state = state,
                        take = takeState,
                        onIntent = { intents += it },
                        addPhoto = AddPhotoActions(onCamera = { photos += CAMERA }, onGallery = { photos += GALLERY }),
                        zone = TimeZone.UTC,
                        backing = backing,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Composable
    private fun ReadWords() {
        words[IN_REPERTOIRE] = statusLabel(PieceStatus.IN_REPERTOIRE)
        words[LEARNING] = statusLabel(PieceStatus.LEARNING)
        words[RECORD] = stringResource(Res.string.take_record)
        words[NO_MIC] = stringResource(Res.string.take_no_permission)
        words[GRANT] = stringResource(Res.string.take_grant_permission)
        words[NEEDS] = stringResource(Res.string.backing_needs_headphones)
        words[OR_OFF] = stringResource(Res.string.backing_or_turn_off)
        words[NONE] = stringResource(Res.string.backing_headphones_none)
        words[BUDS_SAID] = stringResource(Res.string.backing_headphones_wired, BUDS_NAME)
        words[CHIP] = stringResource(Res.string.backing_chip)
        words[EDIT] = stringResource(Res.string.piece_edit)
        words[STOP] = stringResource(Res.string.record_stop)
        words[RUNNING] = stringResource(Res.string.stand_recording_description, Formats.timer(RECORDED_SECONDS * 1_000L))
        words[TAKES] = stringResource(Res.string.takes_title)
        words[EMPTY] = stringResource(Res.string.takes_empty_title)
        words[PHOTO] = stringResource(Res.string.piece_sheets_camera)
        words[GALLERY_WORD] = stringResource(Res.string.piece_sheets_gallery)
        words[META] = "$COMPOSER, $KEY, " + stringResource(Res.string.piece_tempo_description, TEMPO)
        words[SUMMARY] = stringResource(Res.string.take_progress_description, 82, 88) + ", " + stringResource(Res.string.take_chart_description, "76, 88, 82")
        words[SELECT] = stringResource(Res.string.selection_select)
        words[ADD_BACKING] = stringResource(Res.string.backing_add)
        words[ADD_NOTE] = stringResource(Res.string.piece_notes_add)
        words[READING] = statusLabel(PieceStatus.READING)
    }

    private fun layoutOf(text: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single()
    }

    /** [text] goes on to a next line only at a space, and is not cut: no word of it breaks by the letter. */
    private fun assertWordsWhole(text: String) {
        val layout = layoutOf(text)
        for (line in 0 until layout.lineCount - 1) {
            val end = layout.getLineEnd(line)
            assertTrue("«$text» breaks inside a word after «${text.substring(0, end)}»", text[end - 1].isWhitespace())
        }
        assertFalse("«$text» is cut", layout.isLineEllipsized(layout.lineCount - 1))
    }

    private fun boundsOfText(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot()

    /** What sleeps while a take runs (spec 3.36.4) is there before it — and gone from TalkBack's tree once it runs (0.38, no touch). */
    private fun assertTheTakeDims(sleeping: List<SemanticsMatcher>, awake: List<SemanticsMatcher>) {
        sleeping.forEach { compose.onNode(it).assertExists() }
        compose.runOnIdle { takeState.value = RUNNING_TAKE }
        compose.waitForIdle()
        sleeping.forEach { compose.onAllNodes(it).assertCountEquals(0) }
        awake.forEach { compose.onNode(it).assertExists() }
    }

    @Test
    fun theStatusIsASwitchOfThreeStepsAndATapSendsTheStepAtOnce() {
        show(MINUET)
        compose.onNodeWithText(word(LEARNING)).assertIsSelected()
        compose.onNodeWithText(word(IN_REPERTOIRE)).performClick()
        assertEquals(listOf<PieceIntent>(PieceIntent.StatusSelected(PieceStatus.IN_REPERTOIRE)), intents)
    }

    @Test
    fun withoutTheMicrophoneTheLineAsksAndTheKeySleeps() {
        show(MINUET, take = IDLE.copy(micPermission = false))
        compose.onNodeWithText(word(NO_MIC)).assertExists()
        compose.onNodeWithText(word(RECORD)).assertIsNotEnabled()
        compose.onNodeWithText(word(GRANT)).performClick()
        assertEquals(listOf<PieceIntent>(PieceIntent.GrantMicClicked), intents)
    }

    @Test
    fun withoutHeadphonesAPlateSaysWhyAndTheWayOutAndTheCapsuleSwitchesOverItsFortyEight() {
        show(MINUET, backing = BUDS.copy(route = AudioRoute(BackingOutput.SPEAKER, null)))
        compose.onNode(hasText(word(NEEDS), substring = true) and hasText(word(OR_OFF), substring = true)).assertExists()
        compose.onNodeWithContentDescription(word(NONE)).assertExists()
        compose.onNodeWithText(word(RECORD)).assertIsNotEnabled()
        val chip = compose.onNodeWithText(word(CHIP))
        chip.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        chip.assertHeightIsAtLeast(TOUCH)
        chip.performClick()
        assertEquals(listOf<PieceIntent>(PieceIntent.BackingChipToggled), intents)
    }

    @Test
    fun theHeadphonesAreNamedForTalkBackAsHeadphones() {
        show(MINUET)
        compose.onNodeWithContentDescription(word(BUDS_SAID)).assertExists()
        compose.onNodeWithText(word(RECORD)).performClick()
        assertEquals(listOf<PieceIntent>(PieceIntent.RecordClicked), intents)
    }

    @Test
    fun aRunningTakeIsTheBarThatSaysItsTimeAndStopsWhileEditSleepsAndTheStatusStillAnswers() {
        show(MINUET, take = RUNNING_TAKE)
        compose.onNodeWithContentDescription(word(RUNNING)).assertExists()
        compose.onAllNodesWithText(word(RECORD)).assertCountEquals(0)
        compose.onAllNodesWithText(word(EDIT)).assertCountEquals(0)
        compose.onNodeWithText(word(IN_REPERTOIRE)).performClick()
        compose.onNodeWithContentDescription(word(STOP)).performClick()
        assertEquals(listOf(PieceIntent.StatusSelected(PieceStatus.IN_REPERTOIRE), PieceIntent.RecordClicked), intents)
    }

    // spec 3.36.4: during a take the card of the backing, the summary, the cards of the takes and «Выбрать» sleep; the notes and
    // the status do not (the review of stage 109)
    @Test
    fun uprightARunningTakeDimsTheBackingTheSummaryTheTakesAndSelect() {
        show(TITLED)
        assertTheTakeDims(sleeping = dimmedByTheTake(), awake = listOf(hasText(NOTES), hasText(word(LEARNING))))
    }

    @Test
    fun lyingARunningTakeDimsTheBackingTheSummaryTheTakesAndSelect() {
        show(TITLED, width = 892.dp, height = 412.dp)
        assertTheTakeDims(sleeping = dimmedByTheTake(), awake = listOf(hasText(NOTES), hasText(word(LEARNING))))
    }

    @Test
    fun uprightARunningTakeDimsTheQuietRows() {
        show(MINUET.copy(notes = ""), backing = NO_BACKING)
        assertTheTakeDims(sleeping = listOf(hasText(word(ADD_BACKING)), hasText(word(ADD_NOTE))), awake = listOf(hasText(word(LEARNING))))
    }

    @Test
    fun lyingARunningTakeDimsTheQuietRows() {
        show(MINUET.copy(notes = ""), backing = NO_BACKING, width = 892.dp, height = 412.dp)
        assertTheTakeDims(sleeping = listOf(hasText(word(ADD_BACKING)), hasText(word(ADD_NOTE))), awake = listOf(hasText(word(LEARNING))))
    }

    private fun dimmedByTheTake(): List<SemanticsMatcher> =
        listOf(hasText(BACKING_TITLE), hasContentDescription(word(SUMMARY)), hasText(word(SELECT))) + TAKE_TITLES.map { hasText(it) }

    @Test
    fun untilTheBackingIsReadThereIsNoKeyAndNoSwitch() {
        show(MINUET, backing = null)
        compose.onAllNodesWithText(word(RECORD)).assertCountEquals(0)
        compose.onAllNodesWithText(word(CHIP)).assertCountEquals(0)
        compose.onAllNodesWithText(word(LEARNING)).assertCountEquals(0)
    }

    @Test
    fun withoutTakesItSaysSoWithoutTitleAndWithoutPagesTheTwoWaysAreTiles() {
        show(MINUET.copy(pages = emptyList(), takes = emptyList(), progress = null), backing = NO_BACKING)
        compose.onNodeWithText(word(EMPTY)).assertExists()
        compose.onAllNodes(hasText(word(TAKES)) and isHeading()).assertCountEquals(0)
        compose.onNodeWithText(word(PHOTO)).performClick()
        compose.onNodeWithText(word(GALLERY_WORD)).performClick()
        assertEquals(listOf(CAMERA, GALLERY), photos)
    }

    @Test
    fun theSummaryOfTheTakesIsOnePhrase() {
        show(MINUET)
        compose.onNodeWithContentDescription(word(SUMMARY)).assertExists()
        compose.onNode(hasText(word(TAKES)) and isHeading()).assertExists()
    }

    @Test
    fun lyingTheZoneIsTheLeftColumnOf300AndTheMetaStandsAbove380() {
        show(MINUET, width = 892.dp, height = 412.dp)
        val window = compose.onNodeWithTag(WINDOW).getUnclippedBoundsInRoot()
        val record = compose.onNodeWithText(word(RECORD)).getUnclippedBoundsInRoot()
        assertTrue("the key in the left column: ${record.right - window.left}", record.right - window.left <= LEFT_COLUMN)
        compose.onNodeWithContentDescription(word(META)).assertExists()
        // the name in the bar of 48
        compose.onNode(hasText(TITLE) and isHeading()).assertExists()
    }

    @Test
    fun inAWindowOf360TheMetaGoesAndTheKeyIs48() {
        show(MINUET, width = 640.dp, height = 360.dp)
        compose.onAllNodes(hasContentDescription(word(META))).assertCountEquals(0)
        val record = compose.onNodeWithText(word(RECORD)).getUnclippedBoundsInRoot()
        assertEquals(48f, (record.bottom - record.top).value, 1f)
    }

    // the review of stage 109: in 640 × 360 the column of the name beside the notes would be 42 dp — the cards stand one under the other
    @Test
    fun lyingIn640TheBackingStandsOverTheNotesAndItsNameBreaksNoWord() {
        show(MINUET, width = 640.dp, height = 360.dp)
        val name = boundsOfText(BACKING_TITLE)
        val notes = boundsOfText(NOTES)
        assertTrue("the notes under the backing: $notes against $name", notes.top >= name.bottom)
        assertWordsWhole(BACKING_TITLE)
    }

    @Test
    fun lyingIn892TheBackingAndTheNotesStandSideBySide() {
        show(MINUET, width = 892.dp, height = 412.dp)
        val name = boundsOfText(BACKING_TITLE)
        val notes = boundsOfText(NOTES)
        assertTrue("the notes beside the backing: $notes against $name", notes.left >= name.right && notes.top < name.bottom)
    }

    // the review of stage 109: beside the circle in the column of 300, «Записать дубль» of 17 sp went on two lines; its words step down
    // to 15 sp to keep one — below that (the French «Enregistrer une prise») they break at a space
    @Test
    fun lyingTheKeyKeepsItsWordsOnOneLine() {
        show(MINUET, width = 892.dp, height = 412.dp)
        val layout = layoutOf(word(RECORD))
        assertTrue(
            "«${word(RECORD)}» on ${layout.lineCount} lines at ${layout.layoutInput.style.fontSize}",
            layout.lineCount == 1 || layout.layoutInput.style.fontSize == KEY_LEAST,
        )
        assertWordsWhole(word(RECORD))
    }

    // the review of stage 109: the tiles of an element without pages lying are halves of 130 — «Сфотографировать» of 13 sp is 129
    @Test
    fun lyingWithoutPagesTheTwoTilesBreakNoWord() {
        show(MINUET.copy(pages = emptyList()), width = 892.dp, height = 412.dp)
        assertWordsWhole(word(PHOTO))
        assertWordsWhole(word(GALLERY_WORD))
    }

    @Test
    fun uprightOn360WithALargeFontTheTilesBreakNoWord() {
        show(MINUET.copy(pages = emptyList()), width = 360.dp, height = 640.dp, fontScale = LARGE_FONT)
        assertWordsWhole(word(PHOTO))
        assertWordsWhole(word(GALLERY_WORD))
    }

    // spec 3.36.4: «слова не сокращаются» — on 360 at the font 1.3 a third of the row no longer held «репертуаре» (the review of stage 109)
    @Test
    fun uprightOn360WithALargeFontNoStepOfTheStatusBreaksAWord() {
        show(MINUET, width = 360.dp, height = 640.dp, fontScale = LARGE_FONT)
        listOf(word(READING), word(LEARNING), word(IN_REPERTOIRE)).forEach { assertWordsWhole(it) }
    }

    private companion object {
        const val WINDOW = "window"
        const val TITLE = "Менуэт соль мажор"
        const val COMPOSER = "И. С. Бах"
        const val KEY = "G-dur"
        const val TEMPO = 100
        const val BUDS_NAME = "Pixel Buds"
        const val RECORDED_SECONDS = 72L
        const val CAMERA = "camera"
        const val GALLERY = "gallery"
        val TOUCH = 48.dp
        val LEFT_COLUMN = 300.dp

        const val IN_REPERTOIRE = "inRepertoire"
        const val LEARNING = "learning"
        const val RECORD = "record"
        const val NO_MIC = "noMic"
        const val GRANT = "grant"
        const val NEEDS = "needs"
        const val OR_OFF = "orOff"
        const val NONE = "none"
        const val BUDS_SAID = "budsSaid"
        const val CHIP = "chip"
        const val EDIT = "edit"
        const val STOP = "stop"
        const val RUNNING = "running"
        const val TAKES = "takes"
        const val EMPTY = "empty"
        const val PHOTO = "photo"
        const val GALLERY_WORD = "galleryWord"
        const val META = "meta"
        const val SUMMARY = "summary"
        const val SELECT = "select"
        const val ADD_BACKING = "addBacking"
        const val ADD_NOTE = "addNote"
        const val READING = "reading"
        const val BACKING_TITLE = "Бах — клавесин"
        const val NOTES = "В 12-м такте — сразу в третью позицию."
        const val LARGE_FONT = 1.3f
        val KEY_LEAST = 15.sp
        val TAKE_TITLES = listOf("Прогон 1", "Прогон 2", "Прогон 3")

        val BUDS = BackingUi(title = BACKING_TITLE, durationMs = 220_000, enabled = true, route = AudioRoute(BackingOutput.BLUETOOTH, BUDS_NAME))
        val NO_BACKING = BackingUi(title = null, durationMs = 0, enabled = false, route = AudioRoute(BackingOutput.SPEAKER, null))
        val IDLE = TakeState.idle(micPermission = true, bars = 14)
        val RUNNING_TAKE = TakeState(recording = true, elapsedSeconds = RECORDED_SECONDS, levels = List(14) { 0.5f }, problem = null, micPermission = true)

        private fun take(id: Long, day: Int, title: String? = null) = TakeItem(
            card = HistoryCard(id = id, title = title, startedAtEpochMs = 0, date = LocalDate(2026, 9, day), durationMs = 120_000, pieceId = 1, hasAudio = true),
            best = false,
            isNew = false,
        )

        val MINUET = PieceState(
            loading = false,
            header = PieceHeader(TITLE, COMPOSER, KEY, TEMPO, PieceStatus.LEARNING),
            pages = (1..4).map { SheetTile(pageId = it.toLong(), number = it, thumbPath = null) },
            importing = 0,
            notes = NOTES,
            takes = listOf(take(1, 27), take(2, 25), take(3, 20)),
            progress = TakeProgress(lastScore = 82, maxScore = 88, scores = listOf(76, 88, 82)),
            notesCollapsedLines = 6,
        )

        /** The takes by names of their own: a card is found by its words. */
        val TITLED = MINUET.copy(takes = listOf(take(1, 27, TAKE_TITLES[0]), take(2, 25, TAKE_TITLES[1]), take(3, 20, TAKE_TITLES[2])))
    }
}
