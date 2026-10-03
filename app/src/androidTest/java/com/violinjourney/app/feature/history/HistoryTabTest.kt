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
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.test.onNodeWithTag
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
import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.events.CalendarEvent
import com.violinjourney.app.core.domain.events.EventsConfig
import com.violinjourney.app.core.domain.events.KindRef
import com.violinjourney.app.core.domain.events.KindRules
import com.violinjourney.app.core.domain.events.PerformancesLine
import com.violinjourney.app.core.domain.session.SessionSummary
import com.violinjourney.app.core.ui.components.DockMetrics
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.history.components.RecordTileSize
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.dot_separator
import com.violinjourney.app.shared.resources.history_chart_description
import com.violinjourney.app.shared.resources.history_empty_text
import com.violinjourney.app.shared.resources.history_empty_title
import com.violinjourney.app.shared.resources.history_empty_video_title
import com.violinjourney.app.shared.resources.history_filter_all
import com.violinjourney.app.shared.resources.history_filter_live
import com.violinjourney.app.shared.resources.history_filter_takes
import com.violinjourney.app.shared.resources.history_filter_video
import com.violinjourney.app.shared.resources.history_open_live
import com.violinjourney.app.shared.resources.history_show_all
import com.violinjourney.app.shared.resources.nav_history
import com.violinjourney.app.shared.resources.performances_row_in_days_few
import com.violinjourney.app.shared.resources.performances_row_in_days_many
import com.violinjourney.app.shared.resources.performances_row_in_days_one
import com.violinjourney.app.shared.resources.performances_row_past_few
import com.violinjourney.app.shared.resources.performances_row_past_many
import com.violinjourney.app.shared.resources.performances_row_past_one
import com.violinjourney.app.shared.resources.performances_title
import com.violinjourney.app.shared.resources.selection_delete_records_few
import com.violinjourney.app.shared.resources.selection_delete_records_many
import com.violinjourney.app.shared.resources.selection_delete_records_one
import com.violinjourney.app.shared.resources.selection_select
import com.violinjourney.app.shared.resources.session_delete_confirm
import com.violinjourney.app.shared.resources.session_delete_title
import com.violinjourney.app.shared.resources.video_delete_text
import com.violinjourney.app.testing.assertWholeOnOneLine
import com.violinjourney.app.testing.assertWordsWhole
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
            // tagged: a window wider than the phone held upright is centred on it, far left of the root — measure from its left
            Box(Modifier.requiredSize(width, height).testTag(WINDOW)) { content() }
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
            words[PERFORMANCES] = stringResource(Res.string.performances_title)
            words[STRIP] = stringResource(Res.string.history_chart_description)
            words[EMPTY_TITLE] = stringResource(Res.string.history_empty_title)
            words[EMPTY_TEXT] = stringResource(Res.string.history_empty_text)
            // «через 27 дней · 3 прошло»: the line of the row that is never cut
            words[TERM] = stringResource(
                Formats.plural(DAYS, Res.string.performances_row_in_days_one, Res.string.performances_row_in_days_few, Res.string.performances_row_in_days_many),
                DAYS,
            ) + stringResource(Res.string.dot_separator) + stringResource(
                Formats.plural(PAST, Res.string.performances_row_past_one, Res.string.performances_row_past_few, Res.string.performances_row_past_many),
                PAST,
            )
            // «13 прошло»: the start of the caption of only those past
            words[PAST_WORDS] = stringResource(
                Formats.plural(PAST_ONLY, Res.string.performances_row_past_one, Res.string.performances_row_past_few, Res.string.performances_row_past_many),
                PAST_ONLY,
            )
            ViolinTheme { InWindow(width, height, fontScale) { HistoryScreen(shown.value, onIntent = { intents += it }, zone = UTC) } }
        }
        compose.waitForIdle()
    }

    private fun word(key: String): String = words.getValue(key)

    /** The box the screen is laid out in, in the root: a window of 892 or 640 on a phone held upright starts left of the root. */
    private fun window() = compose.onNodeWithTag(WINDOW).getUnclippedBoundsInRoot()

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

    /** The row «Выступления» of [line] over [state], as the view model gives it on 27 September 2026 (spec 3.36.9). */
    private fun withRow(state: HistoryState, line: PerformancesLine = AHEAD) = state.copy(
        performances = HistoryPerformances(line, KindRules.lookOf(KindRef.BuiltIn(BuiltInKind.PERFORMANCE), emptyList(), EventsConfig()), TODAY),
    )

    /** The row: one button, its description begins with «Выступления». */
    private fun row() = compose.onNode(hasContentDescription(word(PERFORMANCES), substring = true) and hasClickAction())

    /**
     * Spec 3.36.9, 5.29 R9: the row «Выступления» is the first under the line of the title — 4 under it — and the strip stands 8 under
     * the row; at least 64 high, in the field of the list; a tap opens «Выступления».
     */
    @Test
    fun uprightTheRowOfPerformancesIsFirstUnderTheTitleAndOverTheStrip() {
        show(withRow(stateOf(sessions)))
        val row = row().getUnclippedBoundsInRoot()
        val strip = compose.onNode(hasContentDescription(word(STRIP), substring = true)).getUnclippedBoundsInRoot()
        assertEquals("4 under the line of the title: $row", (TITLE_ROW + ROW_TOP).value, row.top.value, HALF_DP)
        assertTrue("at least 64 high: $row", row.bottom - row.top >= ROW_MIN)
        assertEquals("in the field of the list: $row", FIELD.value, row.left.value, HALF_DP)
        assertEquals("the strip 8 under the row: $strip, the row $row", (row.bottom + STRIP_TOP).value, strip.top.value, HALF_DP)
        row().performClick()
        assertEquals(listOf<HistoryIntent>(HistoryIntent.PerformancesClicked), intents)
    }

    /** Spec 3.36.9: lying the row stands in the left column under the title, over the strip — at 892 × 412. */
    @Test
    fun lyingTheRowStandsInTheLeftColumnUnderTheTitle() = theRowStandsInTheLeftColumn(width = 892.dp, height = 412.dp)

    /** The same in the window of a phone of 640 × 360 lying, under its bars: its left column is half of what is left (5.29 R5). */
    @Test
    fun lyingInA640By360WindowTheRowStandsInTheLeftColumnToo() = theRowStandsInTheLeftColumn(width = 640.dp, height = 308.dp)

    private fun theRowStandsInTheLeftColumn(width: Dp, height: Dp) {
        show(withRow(stateOf(sessions)), width = width, height = height)
        // from the left and the top of the window of the test: one wider than the phone held upright is centred on it (5.29 R5 lesson)
        val window = window()
        val row = row().getUnclippedBoundsInRoot()
        val strip = compose.onNode(hasContentDescription(word(STRIP), substring = true)).getUnclippedBoundsInRoot()
        // the left column of the tab lying: 360, but no wider than half of what the two leave (5.29 R5; `HistoryColumns` is internal)
        val column = minOf(LEFT_COLUMN, (width - COLUMNS_GAP * 3) / 2)
        assertEquals("4 under the title of 52: $row in $window", (TITLE_ROW_LYING + ROW_TOP).value, (row.top - window.top).value, HALF_DP)
        assertEquals("from the field of the screen: $row in $window", FIELD.value, (row.left - window.left).value, HALF_DP)
        assertEquals("as wide as the left column ($column): $row in $window", (FIELD + column).value, (row.right - window.left).value, HALF_DP)
        assertEquals("the strip 8 under it: $strip", (row.bottom + STRIP_TOP).value, strip.top.value, HALF_DP)
        // the window of the test is wider than the phone held upright: the row is pressed by its action, not by a touch
        row().performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf<HistoryIntent>(HistoryIntent.PerformancesClicked), intents)
    }

    /**
     * Spec 3.36.9 and plan D44: the row is there in an empty tab too, under the title, and the block of the tab stands in the middle of
     * what the row leaves above the zone — one element of the list: the block does not slide down by the height of the row (in the
     * middle of the room from the title, it would stand 34 lower than the middle of the room from the row).
     */
    @Test
    fun inAnEmptyTabTheRowStandsUnderTheTitleAndTheBlockInTheMiddleOfWhatIsLeft() {
        show(withRow(stateOf(emptyList()), PerformancesLine.None))
        val row = row().getUnclippedBoundsInRoot()
        assertEquals("4 under the title: $row", (TITLE_ROW + ROW_TOP).value, row.top.value, HALF_DP)
        val title = compose.onNodeWithText(word(EMPTY_TITLE)).getUnclippedBoundsInRoot()
        val text = compose.onNodeWithText(word(EMPTY_TEXT)).getUnclippedBoundsInRoot()
        val button = compose.onNodeWithText(word(OPEN_LIVE)).getUnclippedBoundsInRoot()
        // the block: the tile of 72, 16 over the title, the title, 8, the words
        val blockTop = title.top - EMPTY_TITLE_TOP - RecordTileSize.EMPTY.circle
        val zoneTop = button.top - DockMetrics.Regular.top
        assertEquals(
            "the block in the middle between the row (${row.bottom}) and the zone ($zoneTop): $blockTop … ${text.bottom}",
            (blockTop - row.bottom).value, (zoneTop - text.bottom).value, 1f,
        )
    }

    /**
     * Plan D44: while picking the row goes as the strip and the chips go when the last card is deleted — it fades out (150) where it
     * stood, and is gone after: in the frames of the fade the plate of its sign is drawn neither as it was nor as what takes its place.
     */
    @Test
    fun whilePickingTheRowFadesOutWhereItStoodAndIsGone() {
        show(withRow(stateOf(sessions)))
        val row = row().getUnclippedBoundsInRoot()
        // the middle of the plate of the sign: 14 into the row, 20 into the plate of 40, in the middle of the row's height
        val x = row.left + ROW_PADDING + PLATE / 2
        val y = (row.top + row.bottom) / 2
        val plate = pixelAt(x, y)
        // the ground of the tab beside the row, in the side field where nothing is drawn: what a row gone at once would leave at the plate
        val ground = pixelAt(row.left / 2, y)
        assertNotEquals("the plate is drawn on a ground of its own", ground, plate)

        compose.mainClock.autoAdvance = false
        shown.value = withRow(stateOf(sessions)).copy(selection = Selection(active = true))
        // some 60 ms into the fade of 150: the row half gone; the strip coming up under it (250) is still lower than the plate
        repeat(FADING_FRAMES) { compose.mainClock.advanceTimeByFrame() }
        val fading = pixelAt(x, y)
        compose.mainClock.advanceTimeBy(FADE_OVER_MS)
        val after = pixelAt(x, y)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        assertNotEquals("the fade has begun: the plate is not drawn as it was", plate, fading)
        // a row taken away at once (no fade) would leave the bare ground there: the strip coming up is still lower (review of stage 99)
        assertNotEquals("in the frames of the fade the row is still drawn — not the bare ground", ground, fading)
        assertNotEquals("the fade is not over in four frames: what takes the place is not drawn yet", after, fading)
        compose.onAllNodes(hasContentDescription(word(PERFORMANCES), substring = true) and hasClickAction()).assertCountEquals(0)
    }

    /**
     * 5.29 R9 «Уточнено на этапе 99», review of stage 99: lying, picking takes the row away as upright does — the strip of the left column
     * comes up into its place over 250 instead of jumping there once the fade of 150 is over; picking closed, the row stands at once and the
     * strip goes down the same way. A window wider than high that fits the phone held upright (410 × 360), the strip's place read frame by
     * frame: some 60 ms in, it is on its way — neither where it stood nor where it ends.
     */
    @Test
    fun lyingWhilePickingTheStripComesUpIntoThePlaceOfTheRowAndGoesBackDown() {
        show(withRow(stateOf(sessions)), width = 410.dp, height = 360.dp)
        // by its tag: dimmed while picking, the strip says nothing to TalkBack — its description is gone for the frames read here
        val strip = compose.onNodeWithTag(STRIP_TAG)
        val below = strip.getUnclippedBoundsInRoot().top

        compose.mainClock.autoAdvance = false
        shown.value = withRow(stateOf(sessions)).copy(selection = Selection(active = true))
        repeat(FADING_FRAMES) { compose.mainClock.advanceTimeByFrame() }
        val goingUp = strip.getUnclippedBoundsInRoot().top
        compose.mainClock.advanceTimeBy(FADE_OVER_MS)
        val up = strip.getUnclippedBoundsInRoot().top

        shown.value = withRow(stateOf(sessions))
        repeat(FADING_FRAMES) { compose.mainClock.advanceTimeByFrame() }
        val goingDown = strip.getUnclippedBoundsInRoot().top
        compose.mainClock.advanceTimeBy(FADE_OVER_MS)
        val down = strip.getUnclippedBoundsInRoot().top
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        assertTrue("picking takes the place of the row: $below → $up", up < below)
        assertTrue("on its way up, not jumped: $below, $goingUp, $up", goingUp < below && goingUp > up)
        assertEquals("back where it stood: $down", below.value, down.value, HALF_DP)
        assertTrue("on its way down, not jumped: $up, $goingDown, $down", goingDown > up && goingDown < down)
    }

    /**
     * Spec 3.36.9, 5.29 R9: on 360 at the font 1.3 the term of the row and the count of those over stand whole — wrapping at a space
     * where they must — and the long name of the nearest gives way with an ellipsis; in German and in French.
     */
    @Test
    fun theTermOfTheRowStandsWholeAtALargeFontInGerman() = theTermStandsWhole("de")

    @Test
    fun theTermOfTheRowStandsWholeAtALargeFontInFrench() = theTermStandsWhole("fr")

    private fun theTermStandsWhole(language: String) {
        speaking(language)
        val long = AHEAD.copy(nearest = AHEAD.nearest.copy(title = LONG_NAME))
        show(withRow(stateOf(sessions), long), width = 360.dp, height = 640.dp, fontScale = 1.3f)
        assertWordsWhole(compose.onNodeWithText(word(TERM), useUnmergedTree = true), "$language: ${word(TERM)}")
        val name = compose.onNodeWithText(LONG_NAME, useUnmergedTree = true)
        val layout = name.textLayout()
        assertTrue("$language: the name gives way with an ellipsis — ${layout.numbers(name)}", layout.lineCount == 1 && layout.isLineEllipsized(0))
    }

    /**
     * 5.29 R9 «Уточнено на этапе 99» (review of stage 99): «13 прошло · последнее 27 декабря 2025» on 360 at the font 1.0 wraps (≈ 240 dp
     * in the 212 the caption has) — before the date, never inside it: the day is not torn from its month, nor the year from them.
     */
    @Test
    fun onlyThosePastTheDateOfTheLastStandsWholeWhereTheCaptionWraps() {
        speaking("ru")
        show(withRow(stateOf(sessions), PerformancesLine.OnlyPast(count = PAST_ONLY, lastDate = LAST)), width = 360.dp, height = 640.dp)
        val caption = compose.onNode(hasText(word(PAST_WORDS), substring = true), useUnmergedTree = true)
        val layout = caption.textLayout()
        // the date as the formats write it, whatever spaces the row put in it
        val text = layout.layoutInput.text.text.replace(NO_BREAK_SPACE, ' ')
        val date = Formats.recordDate(LAST, withYear = true)
        val start = text.indexOf(date)
        assertTrue("the date of the last one, with its year, is in «$text»", start >= 0)
        assertTrue("the caption wraps on 360 — the case asked about: ${layout.numbers(caption)}", layout.lineCount > 1)
        assertEquals(
            "«$date» on one line: ${layout.numbers(caption)}",
            layout.getLineForOffset(start), layout.getLineForOffset(start + date.length - 1),
        )
        assertWordsWhole(caption, text)
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

        /** The autumn concert of the mockups: 24 October, 27 days after the day of the test, three performances over. */
        const val DAYS = 27
        const val PAST = 3
        val AHEAD = PerformancesLine.Ahead(
            CalendarEvent(
                id = 1, kind = KindRef.BuiltIn(BuiltInKind.PERFORMANCE), date = LocalDate(2026, 10, 24), startMinutes = 18 * 60 + 30,
                durationMinutes = 90, title = "Осенний концерт", place = "Малый зал музыкальной школы", notes = "", seriesId = null,
                detached = false, createdAtEpochMs = 1,
            ),
            days = DAYS,
            pastCount = PAST,
        )
        const val LONG_NAME = "Отборочный тур Международного конкурса юных скрипачей имени Л. Когана"

        /** Only those past: thirteen, the last of them in December of the year before the day of the test — written with its year. */
        const val PAST_ONLY = 13
        val LAST = LocalDate(2025, 12, 27)
        const val NO_BREAK_SPACE = '\u00A0'

        /** The line of the title (56 upright, 52 lying), the row 4 under it and at least 64, the strip 8 under the row (5.29 R5, R9). */
        val TITLE_ROW = 56.dp
        val TITLE_ROW_LYING = 52.dp
        val ROW_TOP = 4.dp
        val ROW_MIN = 64.dp
        val STRIP_TOP = 8.dp

        /** The tag of the strip, outside its dimming (`HistoryScreen.kt`, `HISTORY_STRIP_TAG`). */
        const val STRIP_TAG = "history strip"

        /** The row's field at its start and its plate of the sign. */
        val ROW_PADDING = 14.dp
        val PLATE = 40.dp

        val LEFT_COLUMN = 360.dp
        val COLUMNS_GAP = 16.dp

        /** The block of an empty tab: 16 between its tile and its title (HistoryScreen). */
        val EMPTY_TITLE_TOP = 16.dp
        const val HALF_DP = 0.5f
        const val FADING_FRAMES = 4

        /** The field of the list: 16 from each edge of the screen (spec 5.29 R5). */
        val FIELD = 16.dp

        /** How far inside the card, and beside it, the colour is read. */
        val PROBE = 6.dp
        const val MAX_FRAMES = 10
        const val FADE_OVER_MS = 500L
        const val VIDEO_BYTES = 214_000_000L
        val CHIP_WORDS = listOf(Res.string.history_filter_all, Res.string.history_filter_takes, Res.string.history_filter_video, Res.string.history_filter_live)
        const val TITLE = "title"
        const val PERFORMANCES = "performances"
        const val STRIP = "strip"
        const val EMPTY_TITLE = "emptyTitle"
        const val EMPTY_TEXT = "emptyText"
        const val TERM = "term"
        const val PAST_WORDS = "pastWords"
        const val WINDOW = "window"
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
