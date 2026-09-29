package com.violinjourney.app.feature.session

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.audio.playback.PlayerState
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.Note
import com.violinjourney.app.core.domain.ViolinString
import com.violinjourney.app.core.domain.session.SessionAnalyzer
import com.violinjourney.app.core.domain.session.SessionDetails
import com.violinjourney.app.core.domain.session.SessionSample
import com.violinjourney.app.core.domain.session.SessionSummary
import com.violinjourney.app.core.domain.sound.BuiltInPreset
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.session.components.NotePlace
import com.violinjourney.app.feature.session.components.NoteSheetButtons
import com.violinjourney.app.feature.session.components.NoteSheetContent
import com.violinjourney.app.feature.sound.SoundCaption
import com.violinjourney.app.feature.sound.captionName
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.backing_heard_violin
import com.violinjourney.app.shared.resources.backing_heard_with
import com.violinjourney.app.shared.resources.backing_preparing
import com.violinjourney.app.shared.resources.backing_take_mark
import com.violinjourney.app.shared.resources.card_menu
import com.violinjourney.app.shared.resources.card_menu_delete
import com.violinjourney.app.shared.resources.session_action_rename
import com.violinjourney.app.shared.resources.session_card_per_string
import com.violinjourney.app.shared.resources.session_drift_open
import com.violinjourney.app.shared.resources.session_menu_piece
import com.violinjourney.app.shared.resources.session_note_duration
import com.violinjourney.app.shared.resources.session_note_min_max
import com.violinjourney.app.shared.resources.session_note_range
import com.violinjourney.app.shared.resources.session_notes_label
import com.violinjourney.app.shared.resources.session_percent
import com.violinjourney.app.shared.resources.session_player_play
import com.violinjourney.app.shared.resources.session_summary_in_tune
import com.violinjourney.app.shared.resources.session_take_subtitle
import com.violinjourney.app.shared.resources.sound_caption_everyone
import com.violinjourney.app.shared.resources.sound_session_icon
import com.violinjourney.app.shared.resources.sound_session_row
import com.violinjourney.app.shared.resources.sound_session_silent
import com.violinjourney.app.shared.resources.video_description
import com.violinjourney.app.shared.resources.video_fullscreen
import com.violinjourney.app.shared.resources.video_listen_place
import com.violinjourney.app.shared.resources.video_resolution
import com.violinjourney.app.shared.resources.video_size
import com.violinjourney.app.testing.assertWholeOnOneLine
import com.violinjourney.app.testing.numbers
import com.violinjourney.app.testing.textLayout
import java.util.Locale
import kotlin.math.abs
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The recording of R5 (spec 3.36.5, 5.29 R5) by what a finger, an eye and TalkBack meet: the player at the bottom covers nothing — the
 * last card scrolled into view ends over it; upright on a phone its rows are «play» 56 and the line «Звук», in a window lower than 700
 * one row with the icon of «Звук»; a row of «Что уходит» opens its note; «⋯» of a take leads to its piece and says the weight of its
 * video under «Удалить…», a free recording has no piece to go to; lying — the summary or the picture over the player on the left, the
 * notes on the right; the backing's labels in the narrow column lying break no word; the sheet of a note plays its place, or says why
 * it cannot yet. TalkBack hears the summary as one paragraph, a row of «Что уходит» as a button with «подробно», the line under the
 * name with «под минусовку».
 *
 * Every screen is laid out in a window of its own size, whatever the device's: a [Box] of that size ([WINDOW] — what is measured from
 * the left of the window is measured from it: a window wider than the phone is centred on it), the window the panel of the player
 * reads ([LocalWindowInfo]) and the font of the test. The words are read in the composition, in the language of the device — or in
 * the one a test speaks itself ([speaking]), where the case it checks needs that language.
 */
@RunWith(AndroidJUnit4::class)
class SessionScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val intents = mutableListOf<SessionIntent>()
    private val words = mutableMapOf<String, String>()
    private var light = Color.Unspecified

    /** The language of the device, given back after every test: [speaking] changes the language of the whole process. */
    private val deviceLanguage: Locale = Locale.getDefault()

    @After
    fun backToTheLanguageOfTheDevice() = Locale.setDefault(deviceLanguage)

    /** The words of the composition in [tag], whatever the device speaks: Compose reads the language of the process when it composes. */
    private fun speaking(tag: String) = Locale.setDefault(Locale.forLanguageTag(tag))

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

    private fun show(state: SessionState, width: Dp = 412.dp, height: Dp = 800.dp, fontScale: Float = 1f) = show({ state }, width, height, fontScale)

    /** [state] is read in the composition: a test may change what the screen shows and keep the screen (its scroll). */
    private fun show(state: () -> SessionState, width: Dp = 412.dp, height: Dp = 800.dp, fontScale: Float = 1f) {
        compose.setContent {
            words[PLAY] = stringResource(Res.string.session_player_play)
            words[MORE] = stringResource(Res.string.card_menu)
            words[RENAME] = stringResource(Res.string.session_action_rename)
            words[PIECE] = stringResource(Res.string.session_menu_piece)
            words[DELETE] = stringResource(Res.string.card_menu_delete)
            words[PER_STRING] = stringResource(Res.string.session_card_per_string)
            words[NOTES] = stringResource(Res.string.session_notes_label)
            words[OPEN] = stringResource(Res.string.session_drift_open)
            words[IN_TUNE] = stringResource(Res.string.session_summary_in_tune)
            words[SOUND] = stringResource(Res.string.sound_session_row)
            val caption = stringResource(Res.string.sound_caption_everyone, captionName(HALL.caption))
            words[CAPTION] = caption
            words[SOUND_ICON] = stringResource(Res.string.sound_session_icon, caption)
            words[WITH] = stringResource(Res.string.backing_heard_with)
            words[VIOLIN] = stringResource(Res.string.backing_heard_violin)
            words[BACKING_MARK] = stringResource(Res.string.backing_take_mark)
            words[SUBTITLE] = stringResource(Res.string.session_take_subtitle, Formats.timeOfDay(STARTED, UTC), Formats.duration(TAKE.durationMs))
            words[SIZE] = stringResource(Res.string.video_size, stringResource(Res.string.video_resolution, FULL_HD), Formats.fileSize(BIG_VIDEO))
            words[PICTURE] = stringResource(Res.string.video_description)
            words[LISTEN] = stringResource(Res.string.video_listen_place)
            words[PREPARING] = stringResource(Res.string.backing_preparing)
            words[SILENT] = stringResource(Res.string.sound_session_silent)
            words[FULLSCREEN] = stringResource(Res.string.video_fullscreen)
            words[SMALL_SIZE] = stringResource(Res.string.video_size, stringResource(Res.string.video_resolution, FULL_HD), Formats.fileSize(SMALL_VIDEO))
            ViolinString.entries.forEach { string ->
                TAKE.perString[string]?.let { (percent, _) -> words["$CELL${string.name}"] = "${string.note.letter}, ${stringResource(Res.string.session_percent, percent)}" }
            }
            ViolinTheme {
                light = MaterialTheme.colorScheme.onSurface
                InWindow(width, height, fontScale) { SessionScreen(state(), onIntent = { intents += it }, zone = UTC) }
            }
        }
        compose.waitForIdle()
    }

    /** Where [node] stands from the left of the window of the test. */
    private fun leftOf(node: SemanticsNodeInteraction): Dp = node.getUnclippedBoundsInRoot().left - compose.onNodeWithTag(WINDOW).getUnclippedBoundsInRoot().left

    private fun rightOf(node: SemanticsNodeInteraction): Dp = node.getUnclippedBoundsInRoot().right - compose.onNodeWithTag(WINDOW).getUnclippedBoundsInRoot().left

    private fun assertNear(expected: Dp, actual: Dp, what: String) = assertTrue("$what: $actual, not $expected", abs(expected.value - actual.value) <= 1f)

    /** The cell of «По струнам» of [string]: one node, «G, 91%» to TalkBack. */
    private fun cellOf(string: ViolinString) = compose.onNodeWithContentDescription(word("$CELL${string.name}"))

    /** The four segments of the panel: A/B and the backing — radio buttons. */
    private val radios = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    private fun word(key: String): String = words.getValue(key)

    /** The top of the panel of the player: its first row, «play», stands [top] under it. */
    private fun panelTop(top: Dp): Dp = compose.onNodeWithContentDescription(word(PLAY)).getUnclippedBoundsInRoot().top - top

    /**
     * Spec 3.36.5: «Прокручивается середина, ленту нот плеер не закрывает». The last card brought into view by its scroll ends over the
     * panel — the middle ends at the top of the player, nothing runs under it (with a middle that ran to the bottom, «E» of «По струнам»
     * would stand under the panel, «in view» of its scroll).
     */
    @Test
    fun thePlayerAtTheBottomCoversNothingOfTheMiddle() {
        show(PLAYING)
        val lastCell = cellOf(ViolinString.E5)
        lastCell.performScrollTo()
        compose.waitForIdle()
        val cell = lastCell.getUnclippedBoundsInRoot()
        val panel = panelTop(REGULAR_TOP)
        assertTrue("«E» of «По струнам» ends over the panel: ${cell.bottom} ≤ $panel", cell.bottom <= panel + 0.5.dp)
    }

    /** Upright on a phone (412 × 800): «play» 56, A/B, and the line «Звук · как у всех · Камерный зал» that opens «Звук». */
    @Test
    fun uprightThePlayerHasPlay56AndTheLineOfSound() {
        show(PLAYING)
        compose.onNodeWithContentDescription(word(PLAY)).assertHeightIsEqualTo(56.dp)
        val line = compose.onNode(hasText(word(SOUND), substring = true) and hasText(word(CAPTION), substring = true) and hasClickAction())
        line.assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(SessionIntent.SoundClicked, intents.last()) }
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).assertCountEquals(2)
    }

    /** Spec 3.36.5: lower than 700 — «play» 48 in one row with the compact A/B and the icon «Звук», told «Звук: как у всех · …». */
    @Test
    fun inALowWindowThePlayerIsOneRowWithTheIconOfSound() {
        show(PLAYING, width = 360.dp, height = 640.dp)
        val play = compose.onNodeWithContentDescription(word(PLAY))
        play.assertHeightIsEqualTo(48.dp)
        val icon = compose.onNodeWithContentDescription(word(SOUND_ICON))
        icon.assertIsDisplayed()
        val playBounds = play.getUnclippedBoundsInRoot()
        val iconBounds = icon.getUnclippedBoundsInRoot()
        assertTrue("the icon on the row of «play»: $iconBounds, «play» $playBounds", iconBounds.top < playBounds.bottom && iconBounds.bottom > playBounds.top)
        compose.onAllNodes(hasText(word(SOUND), substring = true) and hasText(word(CAPTION), substring = true)).assertCountEquals(0)
        icon.performClick()
        compose.runOnIdle { assertEquals(SessionIntent.SoundClicked, intents.last()) }
    }

    /**
     * Spec 3.36.5: a row of «Что уходит» is a button «F#5 на струне E, −22 ц, подробно» that opens the sheet of its note. «Подробно» is
     * said once, by the description: its action has no label of its own — TalkBack would say «подробно» again, «Дважды нажмите, чтобы
     * подробно».
     */
    @Test
    fun aRowOfWhatDriftsIsAButtonThatOpensItsNote() {
        show(PLAYING)
        val row = compose.onNode(hasContentDescription("F#5", substring = true) and hasContentDescription(word(OPEN), substring = true))
        row.performScrollTo()
        row.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        assertNull("no label of the action", row.fetchSemanticsNode().config[SemanticsActions.OnClick].label)
        row.performClick()
        compose.runOnIdle { assertEquals(SessionIntent.ProblemNoteClicked(Note(F_SHARP_5)), intents.last()) }
    }

    /** TalkBack hears the summary as one paragraph of what is seen: the score is not a text of its own. */
    @Test
    fun theSummaryIsOneParagraphForTalkBack() {
        show(PLAYING)
        val summary = compose.onNode(hasContentDescription("${TAKE.scorePercent}", substring = true) and hasContentDescription(word(IN_TUNE), substring = true))
        summary.assertIsDisplayed()
        compose.onAllNodesWithText("${TAKE.scorePercent}", useUnmergedTree = false).assertCountEquals(0)
    }

    /**
     * «⋯» of a take (spec 3.36.5): «Переименовать», «К произведению», and after a line «Удалить…» with the weight of its video under it;
     * «К произведению» and «Удалить…» say so.
     */
    @Test
    fun theMenuOfAVideoTakeLeadsToItsPieceAndSaysTheWeightOfItsVideo() {
        show(PLAYING.copy(content = TAKE.copy(hasVideo = true), video = VideoUi(width = 1920, height = 1080, showing = true, sizeBytes = BIG_VIDEO)))
        compose.onNodeWithContentDescription(word(MORE)).performClick()
        compose.onNodeWithText(word(RENAME)).assertIsDisplayed()
        compose.onNodeWithText(word(SIZE)).assertIsDisplayed()
        // 612 MB: a warning — 700 in the colour of danger (5.29 R5)
        assertEquals(FontWeight.Bold, compose.onNodeWithText(word(SIZE), useUnmergedTree = true).textLayout().layoutInput.style.fontWeight)
        compose.onNodeWithText(word(PIECE)).performClick()
        compose.runOnIdle { assertEquals(SessionIntent.OpenPieceClicked, intents.last()) }
        compose.onNodeWithContentDescription(word(MORE)).performClick()
        compose.onNodeWithText(word(DELETE)).performClick()
        compose.runOnIdle { assertEquals(SessionIntent.DeleteClicked, intents.last()) }
    }

    /** A video under 100 MB (5.29 R5): its weight under «Удалить…» is a caption, not a warning. */
    @Test
    fun theWeightOfASmallVideoInTheMenuIsNoWarning() {
        show(PLAYING.copy(content = TAKE.copy(hasVideo = true), video = VideoUi(width = 1920, height = 1080, showing = true, sizeBytes = SMALL_VIDEO)))
        compose.onNodeWithContentDescription(word(MORE)).performClick()
        assertEquals(FontWeight.Normal, compose.onNodeWithText(word(SMALL_SIZE), useUnmergedTree = true).textLayout().layoutInput.style.fontWeight)
    }

    /** A free recording (spec 3.36.5): no star, and «⋯» without «К произведению». */
    @Test
    fun aFreeRecordingHasNoPieceToGoTo() {
        show(PLAYING.copy(content = TAKE.copy(pieceId = null, pieceTitle = null)))
        compose.onNodeWithContentDescription(word(MORE)).performClick()
        compose.onNodeWithText(word(RENAME)).assertIsDisplayed()
        compose.onAllNodesWithText(word(PIECE)).assertCountEquals(0)
    }

    /** The line under the name of a take under a backing: the sign for the eye, «под минусовку» for TalkBack; the name is a heading. */
    @Test
    fun theLineUnderTheNameOfATakeUnderABackingSaysSoToTalkBack() {
        show(PLAYING.copy(content = TAKE.copy(underBacking = true), player = PLAYER.copy(hasBacking = true)))
        compose.onNodeWithContentDescription("${word(SUBTITLE)}, ${word(BACKING_MARK)}").assertIsDisplayed()
        compose.onNode(isHeading() and hasText(MENUET, substring = true)).assertIsDisplayed()
    }

    /**
     * Lying (892 × 412, spec 3.36.5): the summary short on the left over the player, the notes on the right. The left column is
     * min(½ window, 456) = 446: the summary in it from 16 to 446 − 8, the rows of the player flush with it — «play» at 16, the icon of
     * «Звук» ending at 438 — and the notes from 446 + 8 in their card of 16. Measured from the left of the window: a window of 892 on
     * a phone held upright is centred on it, far left of the root.
     */
    @Test
    fun lyingTheSummaryStandsOverThePlayerAndTheNotesOnTheRight() {
        show(PLAYING, width = 892.dp, height = 412.dp)
        val summary = compose.onNode(hasContentDescription(word(IN_TUNE), substring = true) and hasContentDescription("${TAKE.scorePercent}", substring = true))
        val notes = compose.onNode(hasText(word(NOTES).uppercase()) and isHeading())
        val play = compose.onNodeWithContentDescription(word(PLAY))
        val icon = compose.onNodeWithContentDescription(word(SOUND_ICON))
        assertNear(LEFT_COLUMN - COLUMN_GAP, rightOf(summary), "the summary ends at the meeting of the columns")
        assertNear(SCREEN_PADDING, leftOf(play), "«play» at the edge of the column")
        assertNear(rightOf(summary), rightOf(icon), "the rows of the player end where the summary does")
        assertNear(LEFT_COLUMN + COLUMN_GAP + CARD_PADDING, leftOf(notes), "the notes in the right column")
        assertTrue("the summary over the player", summary.getUnclippedBoundsInRoot().bottom <= play.getUnclippedBoundsInRoot().top)
        play.assertHeightIsEqualTo(48.dp)
    }

    /** Lying, a video: the picture over the player on the left, the summary first on the right. */
    @Test
    fun lyingThePictureStandsOverThePlayerAndTheSummaryOnTheRight() {
        show(PLAYING.copy(content = TAKE.copy(hasVideo = true), video = VideoUi(width = 1920, height = 1080, showing = true, sizeBytes = BIG_VIDEO)), width = 892.dp, height = 412.dp)
        val picture = compose.onNodeWithContentDescription(word(PICTURE)).getUnclippedBoundsInRoot()
        val summary = compose.onNode(hasContentDescription(word(IN_TUNE), substring = true) and hasContentDescription("${TAKE.scorePercent}", substring = true))
            .getUnclippedBoundsInRoot()
        val play = compose.onNodeWithContentDescription(word(PLAY)).getUnclippedBoundsInRoot()
        assertTrue("the summary right of the picture: $summary, the picture $picture", summary.left >= picture.right)
        assertTrue("the picture over the player: $picture, «play» $play", picture.bottom <= play.top)
    }

    /**
     * 640 × 360 behind a cutout (≈ 603 wide) at the font 1.3, a take under a backing, in Russian: the rows of the column of 301.5 are
     * 277.5 — «Только скрипка» does not stand in its half at 14 sp (145 + 12 of 139). The compact switch keeps its 28 seen and 48
     * pressed (5.29 R5): its labels go down to one line of 12 sp — two lines of 12 would grow it past its pills of 24 (≈ 60 pressed
     * at this font) — each whole. The compact A/B over it keeps them too: «A» of 14 sp is 18.8 dp at this font on Android, and its
     * line of 21.6 stands in the pill — the box of Manrope's own ascent and descent (26.3) grew it to 50.67 (ExactLines). The touch of
     * 48 is measured without the widening Compose gives a target lower than 48; a segment that is not 48 says what it is made of.
     */
    @Test
    fun inTheNarrowColumnLyingTheLabelsOfTheBackingStandInOneSmallerLineInTheSameHeight() {
        speaking("ru")
        compose.setContent {
            words[WITH] = stringResource(Res.string.backing_heard_with)
            words[VIOLIN] = stringResource(Res.string.backing_heard_violin)
            val base = LocalViewConfiguration.current
            val noWidening = object : ViewConfiguration by base { override val minimumTouchTargetSize = DpSize.Zero }
            ViolinTheme {
                CompositionLocalProvider(LocalViewConfiguration provides noWidening) {
                    InWindow(603.dp, 360.dp, fontScale = 1.3f) {
                        SessionScreen(PLAYING.copy(content = TAKE.copy(underBacking = true), player = PLAYER.copy(hasBacking = true)), onIntent = {}, zone = UTC)
                    }
                }
            }
        }
        compose.waitForIdle()
        listOf(word(WITH), word(VIOLIN)).forEach { label ->
            val node = compose.onNodeWithText(label, useUnmergedTree = true)
            node.assertIsDisplayed()
            assertWholeOnOneLine(node, label)
            assertEquals("«$label» at 12 sp — ${node.textLayout().numbers(node)}", 12f, node.textLayout().layoutInput.style.fontSize.value, 0.01f)
        }
        // A/B and the backing: all four pressed over 48, not grown — from the unmerged tree, where the label is the segment's child
        val segments = compose.onAllNodes(radios, useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(4, segments.size)
        segments.forEach { node ->
            assertNear(48.dp, with(compose.density) { node.boundsInRoot.height.toDp() }, "a compact segment is pressed over 48 (${compactNumbers(node)})")
        }
    }

    /**
     * What a compact segment is made of, for a failure to say why it is not 48 (5.29 R5: a pill of 24 with 10 of air and 2 of edge
     * above and below it): its touch; its pill — what its label is centred in (the content of its parent), which a label's box taller
     * than the pill grows; the box of the label; and where TalkBack reads the words (the backing's — A/B is heard by its description,
     * its words cleared) their lines and size. Pixels; [segment] from the unmerged tree.
     */
    private fun compactNumbers(segment: SemanticsNode): String {
        val label = segment.children.firstOrNull()
        val name = segment.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
            ?: label?.config?.getOrNull(SemanticsProperties.Text)?.joinToString()
        val layouts = mutableListOf<TextLayoutResult>()
        label?.config?.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        val words = layouts.singleOrNull()?.let { layout ->
            val size = layout.layoutInput.style.fontSize
            "${layout.lineCount} line(s) of $size = ${with(layout.layoutInput.density) { size.toPx() }} px"
        } ?: "its lines and size unread — its words are cleared for its description"
        val pill = label?.layoutInfo?.coordinates?.parentLayoutCoordinates?.size?.height
        return "«$name»: touch ${segment.boundsInRoot.height} px, pill $pill px, the label's box ${label?.layoutInfo?.height} px, $words; " +
            "${compose.density.density} px a dp"
    }

    /** The sheet of a note (spec 3.36.5): «Слушать это место» plays its place — a second before it, the view model's part. */
    @Test
    fun theSheetOfANotePlaysItsPlace() {
        show(PLAYING.copy(selectedSegment = F_SHARP_INDEX))
        compose.onNodeWithText(word(LISTEN)).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(SessionIntent.PlaySegmentClicked(F_SHARP_INDEX), intents.last()) }
    }

    /** …and while the backing is made, the button sleeps with «Готовим минусовку…» over it (spec 3.36.5, 5.25). */
    @Test
    fun theSheetOfANoteSaysWhyItCannotPlayYet() {
        show(PLAYING.copy(player = null, preparingBacking = true, content = TAKE.copy(underBacking = true), selectedSegment = F_SHARP_INDEX))
        compose.onNodeWithText(word(LISTEN)).assertIsNotEnabled()
        // in the player and over the button of the sheet
        assertTrue(compose.onAllNodesWithText(word(PREPARING)).fetchSemanticsNodes().size >= 2)
    }

    /**
     * «Готовим минусовку…» (spec 3.36.5, 5.25): A/B — the processing does something — and «С минусовкой | Только скрипка» are seen
     * dimmed and do not answer; the line «Звук» answers and opens «Звук» (it was hidden with the player before).
     */
    @Test
    fun whileTheBackingIsMadeItsSwitchesSleepAndTheLineOfSoundAnswers() {
        show(BACKING_BEING_MADE)
        compose.onNodeWithText(word(PREPARING)).assertIsDisplayed()
        val segments = compose.onAllNodes(radios)
        segments.assertCountEquals(4)
        segments.fetchSemanticsNodes().indices.forEach { segments[it].assertIsDisplayed().assertIsNotEnabled() }
        val line = compose.onNode(hasText(word(SOUND), substring = true) and hasText(word(CAPTION), substring = true) and hasClickAction())
        line.assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(SessionIntent.SoundClicked, intents.last()) }
    }

    /**
     * A sound whose file does not play here (spec 3.17, 3.36.5): the dashed line first, no player at the bottom — no «play», no line
     * «Звук», no A/B — and lying one column in the middle, not wider than 560, instead of two.
     */
    @Test
    fun aSoundThatDoesNotPlayHasNoPlayerAndStandsInOneColumn() {
        val failed = PLAYING.copy(player = null, soundFailed = true)
        show({ failed }, width = 892.dp, height = 412.dp)
        compose.onNodeWithText(word(SILENT)).assertIsDisplayed()
        compose.onAllNodesWithContentDescription(word(PLAY)).assertCountEquals(0)
        compose.onAllNodes(hasText(word(SOUND), substring = true) and hasText(word(CAPTION), substring = true)).assertCountEquals(0)
        compose.onAllNodes(radios).assertCountEquals(0)
        // one column of 560 in the middle of 892: the summary from 166 + 16 to 892 − 166 − 16, the notes under it
        val summary = compose.onNode(hasContentDescription(word(IN_TUNE), substring = true) and hasContentDescription("${TAKE.scorePercent}", substring = true))
        assertNear(ONE_COLUMN_SIDE + SCREEN_PADDING, leftOf(summary), "the summary in the middle column")
        assertNear(892.dp - ONE_COLUMN_SIDE - SCREEN_PADDING, rightOf(summary), "the summary in the middle column")
        val notes = compose.onNode(hasText(word(NOTES).uppercase()) and isHeading())
        assertTrue("the notes under the summary", notes.getUnclippedBoundsInRoot().top >= summary.getUnclippedBoundsInRoot().bottom)
    }

    /**
     * A video take while its backing is made (spec 3.36.5: «Превью видео затемнено, не нажимается, «на весь экран» нет»): the picture
     * shrunk into the row under the bar has no «на весь экран» either. The same screen scrolled the same way, ready, has it in the row —
     * the row is really there.
     */
    @Test
    fun whileTheBackingIsMadeTheRowOfTheShrunkPictureHasNoFullScreen() {
        val video = VideoUi(width = 1920, height = 1080, showing = true, sizeBytes = BIG_VIDEO)
        val ready = PLAYING.copy(content = TAKE.copy(underBacking = true, hasVideo = true), player = PLAYER.copy(hasBacking = true), video = video)
        val shown = mutableStateOf<SessionState>(ready)
        show({ shown.value })
        cellOf(ViolinString.E5).performScrollTo()
        compose.waitForIdle()
        // scrolled far past the picture: only the row's button is left, the frame's own goes with the frame (5.13)
        compose.onAllNodesWithContentDescription(word(FULLSCREEN)).assertCountEquals(1)

        compose.runOnIdle { shown.value = ready.copy(player = null, preparingBacking = true) }
        compose.waitForIdle()
        compose.onAllNodesWithContentDescription(word(FULLSCREEN)).assertCountEquals(0)
    }

    /**
     * «Готовим минусовку…» in the compact panel of 360 × 640, French at the font 1.3, beside the dimmed A/B and «Звук» (116 dp — «l'accom-
     * pagnement…» needs 208): the words stand whole — no word broken, nothing cut — alone in their row, and A/B and «Звук» are still seen.
     */
    @Test
    fun onAPhoneOf360TheWordsOfPreparingStandWholeAndTheSwitchesAreSeen() {
        speaking("fr")
        show(BACKING_BEING_MADE, width = 360.dp, height = 640.dp, fontScale = 1.3f)
        val node = compose.onNodeWithText(word(PREPARING), useUnmergedTree = true)
        node.assertIsDisplayed()
        val layout = node.textLayout()
        val label = word(PREPARING)
        for (line in 0 until layout.lineCount - 1) {
            assertTrue("«$label» goes on at a space — ${layout.numbers(node)}", label[layout.getLineEnd(line) - 1].isWhitespace())
        }
        assertTrue("«$label» is not cut — ${layout.numbers(node)}", !layout.isLineEllipsized(layout.lineCount - 1) && !layout.didOverflowHeight)
        compose.onNodeWithContentDescription(word(SOUND_ICON)).assertIsDisplayed()
        compose.onAllNodes(radios).fetchSemanticsNodes().indices.forEach { compose.onAllNodes(radios)[it].assertIsDisplayed() }
    }

    /** The video over the whole screen: its panels are the glass of R1, and the words on the glass are light — the times too (5.29 R1). */
    @Test
    fun theTimesOnTheGlassOfTheFullScreenAreLight() {
        show(PLAYING.copy(content = TAKE.copy(hasVideo = true), video = VideoUi(width = 1920, height = 1080, showing = true, sizeBytes = BIG_VIDEO), fullscreen = true))
        listOf(Formats.duration(PLAYER.positionMs), Formats.duration(PLAYER.durationMs)).forEach { time ->
            assertEquals("«$time» on the glass", light, compose.onNodeWithText(time).textLayout().layoutInput.style.color)
        }
    }

    /**
     * «По струнам» in the right column of 640 × 360 behind a cutout (cells of ≈ 55 dp), French at the font 1.3: «100 %» of 16 sp would
     * not stand in a cell (≈ 60 dp) — the cell shows the number alone, whole, and TalkBack hears «D, 100 %».
     */
    @Test
    fun inTheNarrowColumnLyingAStringPlayedAllInTuneSaysItsShareWhole() {
        speaking("fr")
        val full = ViolinString.entries.first { TAKE.perString[it]?.first == FULL }
        show(PLAYING, width = 603.dp, height = 360.dp, fontScale = 1.3f)
        val cell = cellOf(full)
        cell.performScrollTo().assertIsDisplayed()
        assertWholeOnOneLine(compose.onNode(hasText("$FULL") and hasAnyAncestor(hasContentDescription(word("$CELL${full.name}"))), useUnmergedTree = true), "$FULL")
    }

    /**
     * The tiles of the sheet of a note on a phone of 360 (the sheet's words 320 wide) at the font 1.3, in Russian: «длительность» is
     * 108 dp at 12 sp in a tile that has 81 for it — the three labels step down together and each stands whole on one line.
     */
    @Test
    fun onAPhoneOf360AtALargeFontTheLabelsOfTheTilesStandWhole() {
        speaking("ru")
        val labels = mutableListOf<String>()
        compose.setContent {
            labels += listOf(stringResource(Res.string.session_note_min_max), stringResource(Res.string.session_note_duration), stringResource(Res.string.session_note_range))
            ViolinTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                    Box(Modifier.width(SHEET_WORDS_ON_360)) { NoteSheetContent(TAKE.segments[F_SHARP_INDEX], nearCents = TAKE.nearCents) }
                }
            }
        }
        compose.waitForIdle()
        labels.distinct().forEach { label -> assertWholeOnOneLine(compose.onNodeWithText(label, useUnmergedTree = true), label) }
    }

    /** «Слушать это место» stands 16 under what is over it (5.29 R5: «кнопка — главная 56, сверху 16»), not the 18 of the sheets of R1. */
    @Test
    fun theButtonOfTheSheetOfANoteStandsSixteenUnderTheAdvice() {
        compose.setContent {
            words[LISTEN] = stringResource(Res.string.video_listen_place)
            ViolinTheme {
                Column(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().height(40.dp).testTag(ABOVE))
                    NoteSheetButtons(NotePlace(video = false, ready = true) {}, index = 0)
                }
            }
        }
        compose.waitForIdle()
        val above = compose.onNodeWithTag(ABOVE).getUnclippedBoundsInRoot()
        val button = compose.onNodeWithText(word(LISTEN)).getUnclippedBoundsInRoot()
        assertNear(16.dp, button.top - above.bottom, "over the button of the sheet")
    }

    private companion object {
        const val PLAY = "play"
        const val MORE = "more"
        const val RENAME = "rename"
        const val PIECE = "piece"
        const val DELETE = "delete"
        const val PER_STRING = "perString"
        const val NOTES = "notes"
        const val OPEN = "open"
        const val IN_TUNE = "inTune"
        const val SOUND = "sound"
        const val CAPTION = "caption"
        const val SOUND_ICON = "soundIcon"
        const val WITH = "with"
        const val VIOLIN = "violin"
        const val BACKING_MARK = "backingMark"
        const val SUBTITLE = "subtitle"
        const val SIZE = "size"
        const val PICTURE = "picture"
        const val LISTEN = "listen"
        const val PREPARING = "preparing"
        const val SILENT = "silent"
        const val FULLSCREEN = "fullscreen"
        const val SMALL_SIZE = "smallSize"
        const val CELL = "cell"
        const val WINDOW = "window"
        const val ABOVE = "above"

        /** A string played all in tune: «100» alone in its cell. */
        const val FULL = 100

        const val MENUET = "Менуэт соль мажор"
        const val FULL_HD = 1080
        const val BIG_VIDEO = 612L * 1024 * 1024
        const val SMALL_VIDEO = 62L * 1024 * 1024

        // The layout of the recording (5.29 R5, SessionScreen.kt): the fields of the screen and of a card, lying — the left column of
        // min(½ window, 456) and 8 on each side of the meeting of the columns; one column — not wider than 560, in the middle.
        val SCREEN_PADDING = 16.dp
        val CARD_PADDING = 16.dp
        val LEFT_COLUMN = 446.dp
        val COLUMN_GAP = 8.dp
        val ONE_COLUMN_SIDE = (892.dp - 560.dp) / 2

        /** The words of a sheet on a phone of 360: its fields of 20 at each side (5.29 R5). */
        val SHEET_WORDS_ON_360 = 320.dp
        const val F_SHARP_5 = 78
        const val STARTED = 1_789_000_000_000L
        val UTC = TimeZone.UTC

        /** The panel of the player upright on a phone: 12 over «play» (5.29 R5). */
        val REGULAR_TOP = 12.dp

        /** A D major scale up and down, F#5 drifting flat by 22, C#5 by 11. */
        val SAMPLES: List<SessionSample?> = buildList {
            repeat(2) { round ->
                val notes = listOf(62 to -3.0, 64 to 2.0, 66 to -4.0, 67 to 1.0, 69 to 4.0, 71 to -2.0, 73 to -11.0, 74 to 0.0, F_SHARP_5 to -22.0)
                (if (round % 2 == 0) notes else notes.reversed()).forEach { (midi, cents) ->
                    repeat(14) { add(SessionSample(midi, cents)) }
                    add(null)
                }
            }
        }

        val TAKE: SessionContent = run {
            val config = IntonationConfig()
            val analysis = SessionAnalyzer.analyze(SAMPLES, config)
            val metrics = analysis.metrics!!
            val summary = SessionSummary(
                id = 1, title = null, startedAtEpochMs = STARTED, durationMs = SAMPLES.size * config.sessionBucketMs, a4Hz = 440.0,
                toleranceCents = config.toleranceCents, nearCents = config.nearCents, scorePercent = metrics.scorePercent,
                nearPercent = metrics.nearPercent, offPercent = metrics.offPercent, maeCents = metrics.maeCents, biasCents = metrics.biasCents,
                previewZones = emptyList(), audioPath = "take.m4a", pieceId = 1,
            )
            SessionContentMapper.contentOf(SessionDetails(summary, SAMPLES, analysis), config).copy(pieceTitle = MENUET, pieceId = 1)
        }

        val F_SHARP_INDEX = TAKE.segments.indexOfFirst { it.note.midi == F_SHARP_5 }
        val PLAYER = PlayerState(ready = true, positionMs = 3_000, durationMs = TAKE.durationMs, processed = true)
        val HALL = SoundRow(SoundCaption.BuiltIn(BuiltInPreset.CHAMBER_HALL), own = false, processed = true)
        val PLAYING = SessionState.Loaded(TAKE, player = PLAYER, sound = HALL, waveform = List(120) { 0.5f })

        /** A take under a backing while the backing is made: no player yet, the processing does something — A/B is there, dimmed. */
        val BACKING_BEING_MADE = PLAYING.copy(content = TAKE.copy(underBacking = true), player = null, preparingBacking = true)
    }
}
