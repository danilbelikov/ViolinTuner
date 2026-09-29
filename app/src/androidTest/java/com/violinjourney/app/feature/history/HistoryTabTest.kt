package com.violinjourney.app.feature.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.domain.session.SessionSummary
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.history_empty_video_title
import com.violinjourney.app.shared.resources.history_filter_all
import com.violinjourney.app.shared.resources.history_filter_live
import com.violinjourney.app.shared.resources.history_filter_takes
import com.violinjourney.app.shared.resources.history_filter_video
import com.violinjourney.app.shared.resources.history_open_live
import com.violinjourney.app.shared.resources.history_show_all
import com.violinjourney.app.shared.resources.nav_history
import com.violinjourney.app.shared.resources.selection_delete_records_few
import com.violinjourney.app.shared.resources.selection_delete_records_many
import com.violinjourney.app.shared.resources.selection_delete_records_one
import com.violinjourney.app.shared.resources.selection_select
import com.violinjourney.app.shared.resources.session_delete_confirm
import com.violinjourney.app.shared.resources.session_delete_title
import com.violinjourney.app.shared.resources.video_delete_text
import com.violinjourney.app.testing.assertWholeOnOneLine
import com.violinjourney.app.testing.numbers
import com.violinjourney.app.testing.textLayout
import java.util.Locale
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The tab «Записи» of R5 (spec 3.36.5) by what a finger, an eye and TalkBack meet: the four chips seen whole on 360 with a large font —
 * inside the field of the list, on two lines, no word cut; «Выбрать» on the line of the title, whole on one line — the title gives way
 * where the two do not fit, and stands whole where they do; nothing under a chip — its words and «Показать все записи», without
 * «Выбрать»; nothing at all — «Открыть Live»; while the list is read — the title alone; the last card deleted fades out as every deleted
 * card does; «Удалить…» of one card asks the question of its recording, the picked ones are asked about by their number, both with the
 * weight of the videos.
 *
 * Every screen is laid out in a window of its own size, whatever the device's: a [Box] of that size, the window the bottom zone reads
 * ([LocalWindowInfo]) and the font of the test. The words are read in the composition (the lesson of stage 107); where a case needs a
 * language of its own — French, whose «Enregistrements» gives way beside «Sélectionner», German, whose «Aufnahmen» fits beside
 * «Auswählen» — the test speaks that language itself ([speaking]), whatever the device speaks.
 */
@RunWith(AndroidJUnit4::class)
class HistoryTabTest {
    @get:Rule
    val compose = createComposeRule()

    private val intents = mutableListOf<HistoryIntent>()
    private val words = mutableMapOf<String, String>()
    private val shown = mutableStateOf(HistoryReducer.loading(HistoryFilter.ALL))

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
            Box(Modifier.requiredSize(width, height)) { content() }
        }
    }

    private fun show(state: HistoryState, width: Dp = 412.dp, height: Dp = 800.dp, fontScale: Float = 1f) {
        shown.value = state
        compose.setContent {
            words[TITLE] = stringResource(Res.string.nav_history)
            words[SELECT] = stringResource(Res.string.selection_select)
            words[OPEN_LIVE] = stringResource(Res.string.history_open_live)
            words[SHOW_ALL] = stringResource(Res.string.history_show_all)
            words[NO_VIDEO] = stringResource(Res.string.history_empty_video_title)
            words[ONE_TITLE] = stringResource(Res.string.session_delete_title)
            words[ONE_PICKED_TITLE] = stringResource(picked(1), 1)
            words[TWO_PICKED_TITLE] = stringResource(picked(2), 2)
            words[VIDEO_TEXT] = stringResource(Res.string.video_delete_text, Formats.fileSize(VIDEO_BYTES))
            words[DELETE] = stringResource(Res.string.session_delete_confirm)
            CHIP_WORDS.forEachIndexed { index, word -> words[chipKey(index)] = stringResource(word) }
            ViolinTheme { InWindow(width, height, fontScale) { HistoryScreen(shown.value, onIntent = { intents += it }, zone = UTC) } }
        }
        compose.waitForIdle()
    }

    private fun word(key: String): String = words.getValue(key)

    private fun picked(count: Int) =
        Formats.plural(count, Res.string.selection_delete_records_one, Res.string.selection_delete_records_few, Res.string.selection_delete_records_many)

    private val chips = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    /**
     * Spec 3.36.5: all four chips are seen always — what does not fit the line goes onto the next one instead of sliding away sideways.
     * French at 1.3 on 360 cannot hold them in one line (≈ 386 of the 328 of the field): the fourth stands under the first, every chip
     * in the field of the list (16 from each edge), every word whole on its line — a chip squeezed by a plain row would cut its word.
     */
    @Test
    fun onA360ScreenWithALargeFontTheFourChipsWrapAndStayWhole() {
        speaking("fr")
        show(stateOf(sessions), width = 360.dp, height = 640.dp, fontScale = 1.3f)
        val all = compose.onAllNodes(chips)
        assertEquals(4, all.fetchSemanticsNodes().size)
        val bounds = (0 until 4).map { all[it].getUnclippedBoundsInRoot() }
        bounds.forEachIndexed { index, chip ->
            assertTrue("chip $index starts in the field: $chip", chip.left >= FIELD)
            assertTrue("chip $index ends in the field: $chip", chip.right <= 360.dp - FIELD)
            // not `hasVisualOverflow`: a chip's plain-String text hands a layout laid out at all the width offered it (testing/TextLayouts.kt)
            assertWholeOnOneLine(compose.onNodeWithText(word(chipKey(index)), useUnmergedTree = true), word(chipKey(index)))
        }
        assertTrue("the fourth chip is on a line under the first: ${bounds[3].top} ≥ ${bounds[0].bottom}", bounds[3].top >= bounds[0].bottom)
    }

    /**
     * Spec 3.36.5, the example of the spec: «Sélectionner» beside «Enregistrements» on 360 at a large font — the two do not fit side
     * by side, and the title gives way with an ellipsis.
     */
    @Test
    fun selectStandsWholeBesideTheTitleThatGivesWayInFrench() = selectStandsWholeBesideTheTitle("fr", titleGivesWay = true)

    /**
     * German on the same 360 at 1.3: «Aufnahmen» needs ≈ 159 dp of the ≈ 173 left beside «Auswählen» (measured on the emulator) — both
     * stand whole side by side, the title without an ellipsis it does not need. The spec names no German case of the title giving way.
     */
    @Test
    fun selectAndTheTitleStandWholeSideBySideInGerman() = selectStandsWholeBesideTheTitle("de", titleGivesWay = false)

    /**
     * «Выбрать» is measured first: whole, on one line, to the right of the title and inside the window. The title is one line: where
     * [language] does not fit the two side by side ([titleGivesWay]) it gives way with an ellipsis, so the case is there to be seen — a
     * button measured after the title would be left some 38 dp and break its word; where it does, the title stands whole.
     */
    private fun selectStandsWholeBesideTheTitle(language: String, titleGivesWay: Boolean) {
        speaking(language)
        show(stateOf(sessions), width = 360.dp, height = 640.dp, fontScale = 1.3f)
        val select = compose.onNodeWithText(word(SELECT))
        // one line, whole, no ellipsis — not `hasVisualOverflow`: the button's plain-String text hands a layout laid out at the whole
        // room the button offered it (testing/TextLayouts.kt)
        assertWholeOnOneLine(compose.onNodeWithText(word(SELECT), useUnmergedTree = true), word(SELECT))
        val titleNode = compose.onNode(hasText(word(TITLE)) and isHeading(), useUnmergedTree = true)
        if (titleGivesWay) {
            val titleLayout = titleNode.textLayout()
            assertTrue("«${word(TITLE)}» gives way with an ellipsis — ${titleLayout.numbers(titleNode)}", titleLayout.isLineEllipsized(0))
        } else {
            assertWholeOnOneLine(titleNode, word(TITLE))
        }
        val title = titleNode.getUnclippedBoundsInRoot()
        val bounds = select.getUnclippedBoundsInRoot()
        assertTrue("«${word(SELECT)}» to the right of the title: $bounds, the title $title", bounds.left >= title.right)
        assertTrue("«${word(SELECT)}» inside the window: $bounds", bounds.right <= 360.dp)
        select.performClick()
        assertEquals(listOf<HistoryIntent>(HistoryIntent.Select(SelectionIntent.SelectClicked)), intents)
    }

    @Test
    fun nothingUnderAChipSaysSoAndShowsEverythingBack() {
        show(stateOf(sessions.filter { it.videoPath == null }, HistoryFilter.VIDEO))
        compose.onNodeWithText(word(NO_VIDEO)).assertExists()
        compose.onAllNodesWithText(word(SELECT)).assertCountEquals(0)
        assertEquals("the chips stay", 4, compose.onAllNodes(chips).fetchSemanticsNodes().size)
        compose.onNodeWithText(word(SHOW_ALL)).performClick()
        assertEquals(listOf<HistoryIntent>(HistoryIntent.FilterSelected(HistoryFilter.ALL)), intents)
    }

    @Test
    fun nothingAtAllHasNoChipsAndOpensLive() {
        show(stateOf(emptyList()))
        compose.onNode(hasText(word(TITLE)) and isHeading()).assertExists()
        compose.onAllNodes(chips).assertCountEquals(0)
        compose.onAllNodesWithText(word(SELECT)).assertCountEquals(0)
        compose.onNodeWithText(word(OPEN_LIVE)).performClick()
        assertEquals(listOf<HistoryIntent>(HistoryIntent.OpenLiveClicked), intents)
    }

    @Test
    fun lyingNothingAtAllOpensLiveFromTheRightColumn() {
        show(stateOf(emptyList()), width = 892.dp, height = 412.dp)
        val title = compose.onNode(hasText(word(TITLE)) and isHeading()).getUnclippedBoundsInRoot()
        val button = compose.onNodeWithText(word(OPEN_LIVE)).getUnclippedBoundsInRoot()
        assertTrue("«${word(OPEN_LIVE)}» in the right column", button.left > title.right)
        // the window of the test is wider than the phone held upright: the button is pressed by its action, not by a touch
        compose.onNodeWithText(word(OPEN_LIVE)).performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf<HistoryIntent>(HistoryIntent.OpenLiveClicked), intents)
    }

    @Test
    fun whileTheListIsReadThereIsTheTitleAlone() {
        show(HistoryReducer.loading(HistoryFilter.ALL))
        compose.onNode(hasText(word(TITLE)) and isHeading()).assertExists()
        compose.onAllNodes(chips).assertCountEquals(0)
        compose.onAllNodesWithText(word(SELECT)).assertCountEquals(0)
        compose.onAllNodesWithText(word(OPEN_LIVE)).assertCountEquals(0)
    }

    /** Spec 3.36.5, 5.12: the last card deleted fades out (150) as every deleted card does — the empty tab does not replace it at once. */
    @Test
    fun theLastCardDeletedFadesOutAsEveryDeletedCardDoes() = theLastCardFadesOut(width = 412.dp, height = 800.dp)

    /** The same lying: a window wider than high (410 × 360 — it fits the phone held upright, so it is drawn whole and can be read). */
    @Test
    fun lyingTheLastCardDeletedFadesOutToo() = theLastCardFadesOut(width = 410.dp, height = 360.dp)

    /**
     * One card, then nothing at all. In the frame in which the empty tab comes («Открыть Live» is there), the card is still drawn where it
     * stood, fading — a list swapped for an empty tab at once would show the ground there already; once the fade is over it is gone.
     * The point read is inside the card at its left edge, where the empty tab draws nothing; the ground is read beside the card.
     */
    private fun theLastCardFadesOut(width: Dp, height: Dp) {
        show(stateOf(sessions.take(1)), width = width, height = height)
        val card = compose.onNode(hasContentDescription(MINUET, substring = true) and hasClickAction()).getUnclippedBoundsInRoot()
        val y = (card.top + card.bottom) / 2
        val inside = card.left + PROBE
        val ground = pixelAt(card.left - PROBE, y)
        assertNotEquals("the card is drawn on a ground of its own", ground, pixelAt(inside, y))

        compose.mainClock.autoAdvance = false
        shown.value = stateOf(emptyList())
        var frames = 0
        while (compose.onAllNodesWithText(word(OPEN_LIVE)).fetchSemanticsNodes().isEmpty()) {
            assertTrue("the empty tab came", ++frames <= MAX_FRAMES)
            compose.mainClock.advanceTimeByFrame()
        }
        assertNotEquals("in the frame the tab is empty, the card is still there, fading", ground, pixelAt(inside, y))
        compose.mainClock.advanceTimeBy(FADE_OVER_MS)
        assertEquals("the fade over, the card is gone", ground, pixelAt(inside, y))
        compose.mainClock.autoAdvance = true
    }

    /** The colour at [x], [y] of the root. */
    private fun pixelAt(x: Dp, y: Dp): Color {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        return with(compose.density) { pixels[x.roundToPx(), y.roundToPx()] }
    }

    /** Spec 3.36.5: «Удалить…» of one card opens the question of the recording's own screen — with the weight of a video (3.19). */
    @Test
    fun deleteOfOneCardAsksTheQuestionOfItsRecordingWithTheWeightOfItsVideo() {
        show(withVideoWeight(stateOf(sessions)).copy(selection = Selection(ids = setOf(1L), confirming = true)))
        compose.onNodeWithText(word(ONE_TITLE)).assertExists()
        compose.onNodeWithText(word(VIDEO_TEXT)).assertExists()
        compose.onAllNodesWithText(word(ONE_PICKED_TITLE)).assertCountEquals(0)
        compose.onNodeWithText(word(DELETE)).performClick()
        assertEquals(listOf<HistoryIntent>(HistoryIntent.Select(SelectionIntent.DeleteConfirmed)), intents)
    }

    /** Spec 3.18, 3.36.5: the picked ones are asked about by their number, with the weight of the videos among them. */
    @Test
    fun picksAreAskedAboutByTheirNumberWithTheWeightOfTheirVideos() {
        show(withVideoWeight(stateOf(sessions)).copy(selection = Selection(active = true, ids = setOf(1L, 2L), confirming = true)))
        compose.onNodeWithText(word(TWO_PICKED_TITLE)).assertExists()
        compose.onNodeWithText(word(VIDEO_TEXT)).assertExists()
        compose.onAllNodesWithText(word(ONE_TITLE)).assertCountEquals(0)
    }

    /** The video take 1 with the size of its file, as the view model gives it. */
    private fun withVideoWeight(state: HistoryState) =
        state.copy(cards = state.cards.map { if (it.id == 1L) it.copy(videoBytes = VIDEO_BYTES) else it })

    private fun session(id: Long, at: Long, piece: Long? = null, video: Boolean = false) = SessionSummary(
        id = id, title = null, startedAtEpochMs = at, durationMs = 125_000, a4Hz = 440.0, toleranceCents = 8.0, nearCents = 20.0,
        scorePercent = 82, nearPercent = 12, offPercent = 6, maeCents = 7.4, biasCents = -6.0, previewZones = listOf(Zone.IN_TUNE),
        audioPath = "take-$id.m4a", pieceId = piece, videoPath = if (video) "take-$id.mp4" else null,
    )

    /** 26–27 September 2026: a video take, a free recording, a sound take. */
    private val sessions = listOf(
        session(1, 1_790_530_000_000, piece = 1, video = true),
        session(2, 1_790_520_000_000),
        session(3, 1_790_440_000_000, piece = 1),
    )

    private fun stateOf(list: List<SessionSummary>, filter: HistoryFilter = HistoryFilter.ALL) =
        HistoryReducer.stateOf(list, filter, TODAY, UTC, IntonationConfig(), pieceTitles = mapOf(1L to MINUET))

    private fun chipKey(index: Int) = "chip$index"

    private companion object {
        val UTC = TimeZone.UTC
        val TODAY = LocalDate(2026, 9, 27)
        const val MINUET = "Менуэт соль мажор"

        /** The field of the list: 16 from each edge of the screen (spec 5.29 R5). */
        val FIELD = 16.dp

        /** How far inside the card, and beside it, the colour is read. */
        val PROBE = 6.dp
        const val MAX_FRAMES = 10
        const val FADE_OVER_MS = 500L
        const val VIDEO_BYTES = 214_000_000L
        val CHIP_WORDS = listOf(Res.string.history_filter_all, Res.string.history_filter_takes, Res.string.history_filter_video, Res.string.history_filter_live)
        const val TITLE = "title"
        const val SELECT = "select"
        const val OPEN_LIVE = "openLive"
        const val SHOW_ALL = "showAll"
        const val NO_VIDEO = "noVideo"
        const val ONE_TITLE = "oneTitle"
        const val ONE_PICKED_TITLE = "onePickedTitle"
        const val TWO_PICKED_TITLE = "twoPickedTitle"
        const val VIDEO_TEXT = "videoText"
        const val DELETE = "delete"
    }
}
