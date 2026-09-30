package com.violinjourney.app.feature.live.block

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.Note
import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.domain.repertoire.PieceSection
import com.violinjourney.app.core.domain.repertoire.SectionRef
import com.violinjourney.app.core.ui.components.AppSheetCard
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.live.LiveMode
import com.violinjourney.app.feature.live.LiveReducer
import com.violinjourney.app.feature.live.LiveScreen
import com.violinjourney.app.feature.live.LiveSignal
import com.violinjourney.app.feature.live.LiveState
import com.violinjourney.app.feature.live.LiveTarget
import com.violinjourney.app.feature.live.RecordingState
import com.violinjourney.app.feature.repertoire.sections.sectionName
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.block_goal_minus
import com.violinjourney.app.shared.resources.block_goal_option
import com.violinjourney.app.shared.resources.block_goal_plus
import com.violinjourney.app.shared.resources.block_goal_range
import com.violinjourney.app.shared.resources.block_goal_title
import com.violinjourney.app.shared.resources.block_left_few
import com.violinjourney.app.shared.resources.block_left_many
import com.violinjourney.app.shared.resources.block_left_one
import com.violinjourney.app.shared.resources.block_line
import com.violinjourney.app.shared.resources.block_mark_done_description
import com.violinjourney.app.shared.resources.block_now
import com.violinjourney.app.shared.resources.block_now_label
import com.violinjourney.app.shared.resources.block_offer_later
import com.violinjourney.app.shared.resources.block_offer_title
import com.violinjourney.app.shared.resources.block_running
import com.violinjourney.app.shared.resources.block_section_today
import com.violinjourney.app.shared.resources.block_sheet_title
import com.violinjourney.app.shared.resources.block_start
import com.violinjourney.app.shared.resources.block_stop
import com.violinjourney.app.shared.resources.practice_chip_label
import com.violinjourney.app.shared.resources.practice_start
import com.violinjourney.app.shared.resources.practice_step_down
import com.violinjourney.app.shared.resources.practice_step_up
import com.violinjourney.app.testing.assertWholeOnOneLine
import com.violinjourney.app.testing.textLayout
import java.util.Locale
import kotlin.math.abs
import org.jetbrains.compose.resources.stringResource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * «Что играем» of R6 (spec 3.36.6, 5.29 R6) by what an eye and a finger meet: the chips of the goal are pressed over 48 at the least,
 * in one row of seven on the base screen and on a phone of 411 dp — their gaps give way — in two on a phone of 360 and at a large
 * font, their words whole; the words of the choice stand whole in German and French at the font 1.3 — the name of an element alone
 * ends in «…»; «Остановить» leaves the words of «Сейчас» their room, beside them or under them; the chosen row, the running one and
 * the heads say what they show; the header and «Сейчас» scroll away with the list while the goal stays pinned under it — «Начать»
 * whole on the smallest phone at the largest font; lying down the goal is a column of 300 with its four chips in a row. The frame: a
 * swipe, «назад» and a tap beside it only hide the choice — once, nothing started and nothing stopped — and a tick of the clock under a
 * swipe does not bring it back up, even for an owner that answers a frame late; the offer starts the practice, and the same sheet
 * grows into the choice in place, frame by frame; in a low window the offer scrolls rather than squeeze its buttons; on Live the lights
 * of its «Начать занятие» stand still while a note sounds or a take records.
 *
 * The content of the sheet is laid out in a window of its own size ([WINDOW], a fake [LocalWindowInfo] too): a modal sheet is a
 * window of the device's size. The frame itself ([BlockSheetHost]) is the device's. The widening of a touch target under 48 is
 * switched off, so a chip is measured as it lays itself out. The words are read in the composition, in the language of the process.
 */
@RunWith(AndroidJUnit4::class)
class BlockSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val words = mutableMapOf<String, String>()
    private val intents = mutableListOf<BlockIntent>()
    private var picker by mutableStateOf(pickerOf())
    private var sheet by mutableStateOf<BlockSheet?>(null)
    private var landscape by mutableStateOf(false)
    private var windowSize by mutableStateOf(DpSize(412.dp, 892.dp))
    private var fontScale by mutableFloatStateOf(1f)
    private var live by mutableStateOf(liveOf(LiveSignal.Silence))

    /** The language of the device, given back after every test: [speaking] changes the language of the whole process. */
    private val deviceLanguage: Locale = Locale.getDefault()

    @After
    fun backToTheLanguageOfTheDevice() = Locale.setDefault(deviceLanguage)

    /** The words of the composition in [tag], whatever the device speaks: Compose reads the language of the process when it composes. */
    private fun speaking(tag: String) = Locale.setDefault(Locale.forLanguageTag(tag))

    private fun pickerOf(
        selected: Long? = CHARDASH,
        now: NowLine? = NowLine(D_DUR, LEFT_MINUTES),
        sections: List<PickerSection> = lesson,
    ) = BlockSheet.Picker(
        now = now,
        sections = sections,
        selectedId = selected,
        goalMinutes = 15,
        quickGoals = listOf(5, 10, 15, 20, 30),
        goalStep = 5,
        canGoalDown = true,
        canGoalUp = true,
        practiceMs = PRACTICE_MS,
        goalMinMinutes = 5,
        goalMaxMinutes = 60,
    )

    private fun liveOf(signal: LiveSignal, recording: RecordingState? = null): LiveState =
        LiveReducer.stateOf(LiveTarget(LiveMode.PLAY, null), IntonationConfig(), signal, recording = recording)

    private class Window(private val size: IntSize) : WindowInfo {
        override val isWindowFocused: Boolean = true
        override val containerSize: IntSize get() = size
    }

    @Composable
    private fun InWindow(content: @Composable () -> Unit) {
        val density = LocalDensity.current
        val base = LocalViewConfiguration.current
        val noWidening = remember(base) { object : ViewConfiguration by base { override val minimumTouchTargetSize = DpSize.Zero } }
        val size = windowSize
        val info = with(density) { Window(IntSize(size.width.roundToPx(), size.height.roundToPx())) }
        CompositionLocalProvider(
            LocalWindowInfo provides info,
            LocalViewConfiguration provides noWidening,
            LocalDensity provides Density(density.density, fontScale),
        ) {
            Box(Modifier.requiredSize(size.width, size.height).testTag(WINDOW)) { content() }
        }
    }

    @Composable
    private fun ReadWords() {
        words[TITLE] = stringResource(Res.string.block_sheet_title)
        words[TIME] = stringResource(Res.string.practice_chip_label) + " " + Formats.timer(PRACTICE_MS)
        words[NOW_LABEL] = stringResource(Res.string.block_now_label)
        listOf(LEFT_MINUTES, LONG_LEFT).forEach { minutes ->
            val left = stringResource(Formats.plural(minutes, Res.string.block_left_one, Res.string.block_left_few, Res.string.block_left_many), minutes)
            words[LEFT + minutes] = left
            words[TAIL + minutes] = stringResource(Res.string.block_line, "", left).trimStart()
        }
        words[NOW_SAID] = stringResource(Res.string.block_now, stringResource(Res.string.block_line, D_DUR, word(LEFT + LEFT_MINUTES)))
        words[STOP] = stringResource(Res.string.block_stop)
        words[GOAL_TITLE] = stringResource(Res.string.block_goal_title)
        words[GOAL_RANGE] = stringResource(Res.string.block_goal_range, 5, 60)
        words[START] = stringResource(Res.string.block_start, 15)
        words[MINUS] = stringResource(Res.string.block_goal_minus, 5)
        words[PLUS] = stringResource(Res.string.block_goal_plus, 5)
        words[MINUS_SAID] = stringResource(Res.string.practice_step_down, 5)
        words[PLUS_SAID] = stringResource(Res.string.practice_step_up, 5)
        listOf(5, 10, 15, 20, 30).forEach { words["goal$it"] = stringResource(Res.string.block_goal_option, it) }
        words[PIECES] = sectionName(SectionRef.BuiltIn(PieceSection.PIECES), null)
        words[SCALES] = sectionName(SectionRef.BuiltIn(PieceSection.SCALES), null)
        words[TODAY_ONE] = stringResource(Res.string.block_section_today, 1)
        words[RUNNING] = stringResource(Res.string.block_running)
        words[DONE_SAID] = stringResource(Res.string.block_mark_done_description, Formats.minutesInWords(15 * MIN))
        words[PLAYED] = Formats.minutesInWords(7 * MIN)
        words[DONE] = Formats.minutesInWords(15 * MIN)
        words[START_PRACTICE] = stringResource(Res.string.practice_start)
        words[OFFER_TITLE] = stringResource(Res.string.block_offer_title)
        words[OFFER_LATER] = stringResource(Res.string.block_offer_later)
    }

    /** The content of the choice alone, in the window of the test, on the colour of the sheet. */
    private fun showContent() {
        compose.setContent {
            ReadWords()
            ViolinTheme {
                InWindow {
                    Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainer)) {
                        if (landscape) {
                            PickerLandscape(picker, onIntent = { intents += it }, modifier = Modifier.testTag(PICKER))
                        } else {
                            PickerPortrait(picker, onIntent = { intents += it }, modifier = Modifier.testTag(PICKER))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /**
     * The choice upright in the frame of the sheet ([AppSheetCard]: the handle over it, the content as the sheet gives it room), at the
     * bottom of the window of the test — the window of a sheet, which stands over the bar of the system under it.
     */
    private fun showInFrame() {
        compose.setContent {
            ReadWords()
            ViolinTheme {
                InWindow {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                        AppSheetCard(contentPadding = PaddingValues(0.dp)) {
                            PickerPortrait(picker, onIntent = { intents += it }, modifier = Modifier.testTag(PICKER))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** «Сначала — занятие» alone, in the window of the test; its lights stand still. */
    private fun showOffer() {
        compose.setContent {
            ReadWords()
            ViolinTheme {
                InWindow {
                    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)) {
                        OfferContent(onIntent = { intents += it }, calm = { true })
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /**
     * The frame itself, in the device's window; the model starts the practice asked for and drops the sheet as the view model does —
     * [dropsAtOnce] false: a swiped one only when the test says so, as an owner whose state comes a frame or more later would.
     */
    private fun showSheet(first: BlockSheet, reduceMotion: Boolean = false, dropsAtOnce: Boolean = true) {
        compose.setContent {
            ReadWords()
            ViolinTheme {
                BlockSheetHost(
                    sheet = sheet,
                    landscape = false,
                    onIntent = { intent ->
                        intents += intent
                        when (intent) {
                            BlockIntent.SheetDismissed -> if (dropsAtOnce) sheet = null
                            BlockIntent.NotNowClicked -> sheet = null
                            BlockIntent.StartPracticeClicked -> sheet = picker
                            else -> Unit
                        }
                    },
                    calm = { false },
                    reduceMotion = reduceMotion,
                )
            }
        }
        compose.waitForIdle()
        sheet = first
        compose.waitForIdle()
    }

    /** Live itself with «Сначала — занятие» over it: its [live] state decides whether the lights of the offer's button may move. */
    private fun showLive() {
        compose.setContent {
            ReadWords()
            ViolinTheme {
                LiveScreen(state = live, onIntent = {}, showVenue = false, block = BlockState(Bookmark.Entry, BlockSheet.Offer))
            }
        }
        compose.waitForIdle()
    }

    private fun word(key: String): String = words.getValue(key)

    private fun SemanticsNodeInteraction.bounds(): DpRect = getUnclippedBoundsInRoot()

    private fun window(): DpRect = compose.onNodeWithTag(WINDOW).bounds()

    private fun exists(text: String) = compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun top(text: String): Dp = compose.onNodeWithText(text).bounds().top

    /** A chip of the goal by what TalkBack says of it: «Минус 5 минут», «15 мин», «Плюс 5 минут». */
    private fun chipNodes(): List<SemanticsNodeInteraction> =
        listOf(word(MINUS_SAID), word("goal5"), word("goal10"), word("goal15"), word("goal20"), word("goal30"), word(PLUS_SAID))
            .map { compose.onNodeWithContentDescription(it) }

    /** The capsules the eye sees: the bounds of a chip's semantics are its capsule of 44, not the 48 it takes. */
    private fun chips(): List<DpRect> = chipNodes().map { it.bounds() }

    /**
     * The height each chip lays out itself — the slot of 48 its capsule of 44 stands in (minimumInteractiveComponentSize), in dp: the
     * layout node of the chip, since its semantics give the capsule (the lesson of stage 101, ControlsTouchTest).
     */
    private fun slotHeights(): List<Float> = chipNodes().map { node ->
        val info = node.fetchSemanticsNode().layoutInfo
        info.height / info.density.density
    }

    /** The words of the chips as they stand in them. */
    private fun chipWords(): List<String> = listOf(word(MINUS), "5", "10", "15", "20", "30", word(PLUS))

    /** Seven in one row of the panel: each [cell] wide, the gaps between 4 and 6 — they give way before a chip does. */
    private fun assertOneRow(where: String, chips: List<DpRect>, cell: Float) {
        assertTrue("$where: one row: ${chips.map { it.top }}", chips.all { abs((it.top - chips.first().top).value) <= 0.5f })
        chips.forEach { chip -> assertEquals("$where: a chip is $cell wide: $chip", cell, chip.width.value, 0.5f) }
        slotHeights().forEach { assertEquals("$where: a chip is pressed over 48: $it", 48f, it, 0.5f) }
        chips.zipWithNext().forEach { (a, b) ->
            val gap = (b.left - a.right).value
            assertTrue("$where: $gap between two chips, from 4 to 6", gap >= GoalRows.MIN_GAP - 0.5f && gap <= GoalRows.GAP + 0.5f)
        }
        assertTrue("$where: the row inside the panel", chips.last().right <= window().right - 20.dp + 0.5.dp)
    }

    @Test
    fun onTheBaseScreenTheSevenChipsStandInOneRowAndEachIsPressedOver48() {
        windowSize = DpSize(412.dp, 892.dp)
        showContent()
        assertOneRow("412", chips(), cell = 48f)
    }

    /**
     * The review of stage 116: a phone of 1080 px at 420 dpi — Pixel 7, 9a, 10a — is 411.43 dp; the fields of the panel of 20 are 53 px
     * each, and the row is 371.05. The rule of a row of 372 put the goal of every such phone in two rows; the gaps give way instead.
     */
    @Test
    fun onAPhoneOf411TheSevenChipsKeepTheirOneRow() {
        windowSize = DpSize(411.43.dp, 892.dp)
        showContent()
        assertOneRow("411.43", chips(), cell = 48f)
    }

    /**
     * At a large font «30» with the fields of a chip needs more than 48 (≈ 51 at 1.3, ≈ 55 at 1.5): each chip keeps its words whole —
     * seven of them do not stand in the row of the base screen then, and the goal goes in two rows of the same chips, 4 and 3.
     */
    @Test
    fun onTheBaseScreenAtALargeFontTheChipsKeepTheirWordsInTwoRows() {
        speaking("ru")
        windowSize = DpSize(412.dp, 892.dp)
        showContent()
        for (font in listOf(1.3f, 1.5f)) {
            fontScale = font
            compose.waitForIdle()
            val where = "412 at $font"
            val chips = chips()
            chipWords().forEach { assertWholeOnOneLine(compose.onAllNodesWithText(it, useUnmergedTree = true).onFirst(), "$where: $it") }
            chips.forEach { assertTrue("$where: pressed over 48 at the least: $it", it.width >= 47.5.dp) }
            slotHeights().forEach { assertTrue("$where: pressed over 48 at the least: $it", it >= 47.5f) }
            val oneRow = chips.all { abs((it.top - chips.first().top).value) <= 0.5f }
            // at 1.5 the words of a chip are wider by far than 48 less its fields: never one row there
            if (font >= 1.5f) assertFalse("$where: two rows", oneRow)
            if (!oneRow) {
                val first = chips.take(4)
                val second = chips.drop(4)
                first.forEach { assertEquals("$where: the first row", first.first().top.value, it.top.value, 0.5f) }
                second.forEach { assertEquals("$where: the second row", second.first().top.value, it.top.value, 0.5f) }
                val row = first.last().right - first.first().left
                val cell = (row.value - 3 * GoalRows.GAP) / 4
                chips.forEach { assertEquals("$where: every chip as wide as the first row's: $it", cell, it.width.value, 0.5f) }
            }
        }
    }

    @Test
    fun onAPhoneOf360TheChipsStandInTwoRowsOfFourAndThreeAsWideAsEachOther() {
        windowSize = DpSize(360.dp, 640.dp)
        showContent()
        val chips = chips()
        val first = chips.take(4)
        val second = chips.drop(4)
        first.forEach { assertEquals("the first row: $it", first.first().top.value, it.top.value, 0.5f) }
        second.forEach { assertEquals("the second row: $it", second.first().top.value, it.top.value, 0.5f) }
        // 48 pressed, 44 seen: 2 apart in the code are the 6 the eye sees
        assertEquals("the rows 48 + 2 apart", 50f, (second.first().top - first.first().top).value, 0.5f)
        assertEquals("the second row from the start of the first", first.first().left.value, second.first().left.value, 0.5f)
        chips.forEach { chip -> assertEquals("every chip as wide as the first row's: (320 − 18) / 4", 75.5f, chip.width.value, 0.5f) }
        slotHeights().forEach { assertTrue("pressed over 48 at the least: $it", it >= 47.5f) }
    }

    /** No line of [text] breaks inside a word or parts a number from its unit; one line whole where it is one. */
    private fun assertWordsWhole(what: String, text: String, oneLine: Boolean = false) {
        val node = compose.onAllNodesWithText(text, useUnmergedTree = true).onFirst()
        if (oneLine) {
            assertWholeOnOneLine(node, "$what: $text")
            return
        }
        val layout = node.textLayout()
        for (line in 0 until layout.lineCount - 1) {
            val end = layout.getLineEnd(line)
            assertTrue("$what: «$text» breaks its line ${line + 1} inside a word, at $end", end > 0 && text[end - 1] == ' ')
            assertFalse("$what: «$text» parts a number from its unit", text.substring(0, end).trimEnd().last().isDigit())
        }
        for (line in 0 until layout.lineCount) assertFalse("$what: «$text» ends a line in «…»", layout.isLineEllipsized(line))
        assertFalse("$what: «$text» lost a line to maxLines", layout.multiParagraph.didExceedMaxLines)
        assertFalse("$what: «$text» is cut below", layout.didOverflowHeight)
    }

    /**
     * «Сейчас» with [minutes] left stands whole: its label on one line; the minutes after the name on its line («· ещё 7 мин»), or under
     * it alone — «ещё 7 мин», without the «·» that joins them on one line — wrapping at a space only; «Остановить» on one line inside the
     * window, pressed over 48, beside the lines or under them.
     */
    private fun assertNowStandsWhole(where: String, minutes: Int) {
        val left = word(LEFT + minutes)
        val tail = word(TAIL + minutes)
        assertWordsWhole(where, word(NOW_LABEL), oneLine = true)
        val oneLine = exists(tail)
        assertTrue("$where: the minutes stand after the name or under it, once", oneLine != exists(left))
        if (oneLine) assertWordsWhole(where, tail, oneLine = true) else assertWordsWhole(where, left)
        assertWordsWhole(where, word(STOP), oneLine = true)
        val stop = compose.onNodeWithText(word(STOP)).bounds()
        val window = window()
        assertTrue("$where: «Остановить» at $stop inside the window $window", stop.left >= window.left - 0.5.dp && stop.right <= window.right + 0.5.dp)
        assertTrue("$where: «Остановить» pressed over 48: $stop", stop.height >= 47.5.dp)
    }

    /** Every word of the choice but the names of the elements, and whatever of the sheet lies in the window, whole. */
    private fun wordsOfTheChoiceStandWhole(language: String) {
        speaking(language)
        windowSize = DpSize(360.dp, 640.dp)
        fontScale = 1.3f
        showContent()
        val where = "$language, 360 × 640, 1.3"
        assertWordsWhole(where, word(TITLE))
        assertWordsWhole(where, word(TIME), oneLine = true)
        assertNowStandsWhole(where, LEFT_MINUTES)
        assertWordsWhole(where, word(GOAL_TITLE))
        assertWordsWhole(where, word(GOAL_RANGE), oneLine = true)
        assertWordsWhole(where, word(START))
        chipWords().forEach { assertWordsWhole(where, it, oneLine = true) }
        // the heads: the label in capitals and «сегодня 1» at its right
        assertWordsWhole(where, word(PIECES).uppercase(), oneLine = true)
        assertWordsWhole(where, word(TODAY_ONE), oneLine = true)
        assertWordsWhole(where, word(DONE), oneLine = true)
        assertWordsWhole(where, word(PLAYED), oneLine = true)
        // everything inside the window
        val window = window()
        listOf(word(START), word(STOP)).forEach { text ->
            val bounds = compose.onNodeWithText(text).bounds()
            assertTrue("$where: «$text» at $bounds inside the window $window", bounds.left >= window.left - 0.5.dp && bounds.right <= window.right + 0.5.dp)
        }
    }

    @Test
    fun inGermanOnAPhoneOf360AtTheFont1_3TheWordsOfTheChoiceStandWhole() = wordsOfTheChoiceStandWhole("de")

    @Test
    fun inFrenchOnAPhoneOf360AtTheFont1_3TheWordsOfTheChoiceStandWhole() = wordsOfTheChoiceStandWhole("fr")

    @Test
    fun theChosenRowTheRunningOneTheHeadsAndTheCardSayWhatTheyShow() {
        speaking("ru")
        windowSize = DpSize(412.dp, 892.dp)
        picker = pickerOf(selected = CONCERTO)
        showContent()
        val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        // the chosen one: its name, its composer and today, as one phrase — and «выбрано»
        compose.onNodeWithContentDescription("$CONCERTO_TITLE, $VIVALDI, ${word(DONE_SAID)}").assert(radio).assertIsSelected()
        // the element whose block runs: «идёт», and it is not picked again
        compose.onNodeWithContentDescription("$D_DUR, ${word(RUNNING)}").assert(radio).assertIsNotEnabled()
        // the heads — headings, «Произведения, сегодня 1»; without a count only the name
        val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        compose.onNodeWithContentDescription("${word(PIECES)}, ${word(TODAY_ONE)}").assert(heading)
        compose.onNodeWithContentDescription(word(SCALES)).assert(heading)
        compose.onNodeWithText(word(TITLE)).assert(heading)
        // «Сейчас: D-dur · 2 октавы · ещё 7 мин», then «Остановить» — an outline of 48
        compose.onNodeWithContentDescription(word(NOW_SAID)).assertIsDisplayed()
        val stop = compose.onNodeWithText(word(STOP))
        stop.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        assertEquals("«Остановить» is the low outline of 48", 48f, stop.bounds().height.value, 0.5f)
        stop.performClick()
        compose.runOnIdle { assertEquals(listOf<BlockIntent>(BlockIntent.StopClicked), intents) }
    }

    /** The last item of the list of [choice]: the header, «Сейчас» while a block runs, and each section — its head and its rows. */
    private fun lastItemOf(choice: BlockSheet.Picker): Int = 1 + (if (choice.now != null) 1 else 0) + choice.sections.sumOf { it.pieces.size + 1 } - 1

    @Test
    fun theGoalStaysPinnedUnderTheListAsItScrolls() {
        windowSize = DpSize(360.dp, 640.dp)
        picker = pickerOf(sections = many, now = null, selected = 1)
        showContent()
        val start = compose.onNodeWithText(word(START))
        val before = start.bounds()
        val column = compose.onNodeWithTag(PICKER).bounds()
        // the panel of 12 under the button, at the bottom of the choice
        assertEquals("«Начать» at the bottom of the sheet", (column.bottom - 12.dp).value, before.bottom.value, 0.5f)
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(lastItemOf(picker))
        compose.waitForIdle()
        val after = start.bounds()
        assertEquals("the list scrolls, «Начать» stays", before.top.value, after.top.value, 0.5f)
        start.performClick()
        compose.runOnIdle { assertEquals(listOf<BlockIntent>(BlockIntent.StartClicked), intents) }
    }

    /**
     * The smallest phone of the checks (320 × 500) at the largest fonts, a block running and another element chosen (the review of stage
     * 116): the header and «Сейчас» pinned over the list took, with the goal, more than the sheet has — the list got nothing and
     * «Начать» was squeezed out. They scroll with the list now: the list keeps its room, «Начать» stands whole and answers, and the
     * header goes away as the list scrolls. The window of the sheet stands over the three buttons of the system (48): 452 high, the
     * handle takes 49 of it.
     */
    @Test
    fun onThe320x500PhoneAtALargeFontTheHeaderAndNowScrollWithTheListAndStartStandsWhole() {
        speaking("ru")
        windowSize = DpSize(320.dp, 452.dp)
        picker = pickerOf(now = NowLine(D_DUR, LONG_LEFT))
        showInFrame()
        for (font in listOf(1.3f, 1.5f)) {
            fontScale = font
            intents.clear()
            compose.waitForIdle()
            val where = "320 × 500 at $font"
            val list = compose.onNode(hasScrollToIndexAction())
            val listBounds = list.bounds()
            assertTrue("$where: the list keeps a row of room: ${listBounds.height}", listBounds.height >= 56.dp)
            assertNowStandsWhole(where, LONG_LEFT)
            val start = compose.onNodeWithText(word(START))
            val stood = start.assertIsDisplayed().bounds()
            assertWholeOnOneLine(compose.onNodeWithText(word(START), useUnmergedTree = true), "$where: ${word(START)}")
            assertTrue("$where: «Начать» at $stood whole in the window", stood.height >= 47.5.dp && stood.bottom <= window().bottom + 0.5.dp)
            list.performScrollToIndex(lastItemOf(picker))
            compose.waitForIdle()
            val header = compose.onAllNodesWithText(word(TITLE)).fetchSemanticsNodes()
            assertTrue("$where: the header went with the list", header.isEmpty() || compose.onNodeWithText(word(TITLE)).bounds().bottom <= listBounds.top + 0.5.dp)
            assertEquals("$where: «Начать» stays", stood.top.value, start.bounds().top.value, 0.5f)
            start.performClick()
            compose.runOnIdle { assertEquals(listOf<BlockIntent>(BlockIntent.StartClicked), intents) }
            list.performScrollToIndex(0)
            compose.waitForIdle()
        }
    }

    /**
     * Lying down behind a cutout (640 × 360: the sheet 603.5 wide) at the font 1.5, a block running with 12 minutes left and another
     * element chosen: the column of the goal leaves the list about 300, and «Остановить» the lines of «Сейчас» their widest word — or
     * goes under them.
     */
    @Test
    fun lyingDownBehindACutoutAtTheFont1_5TheWordsOfNowStandWhole() {
        speaking("ru")
        windowSize = DpSize(603.5.dp, 360.dp)
        landscape = true
        fontScale = 1.5f
        picker = pickerOf(now = NowLine(D_DUR, LONG_LEFT))
        showContent()
        assertNowStandsWhole("603.5 × 360 at 1.5", LONG_LEFT)
    }

    @Test
    fun lyingDownTheGoalIsAColumnOf300WithFourChipsInARowAndStartAtItsBottom() {
        windowSize = DpSize(640.dp, 360.dp)
        landscape = true
        showContent()
        val chips = chips()
        val first = chips.take(4)
        first.forEach { assertEquals("four in the first row", first.first().top.value, it.top.value, 0.5f) }
        assertTrue("the fifth chip on the second row", chips[4].top > first.first().top)
        // the column of 300 less its sides of 20: chips of (260 − 18) / 4 — laid out in whole pixels (60.5 is 158.8 px at 2.625, the
        // gaps of 6 are 15.75), so a chip may come out up to 2 px narrower; the four stay inside the column
        chips.forEach { assertEquals("a chip of the column: $it", 60.5f, it.width.value, PIXELS_OF_A_ROW) }
        assertTrue("four inside the column: ${first.last().right} ≤ ${first.first().left + 260.dp}", first.last().right <= first.first().left + 260.dp + 0.5.dp)
        val start = compose.onNodeWithText(word(START)).bounds()
        val window = window()
        assertEquals("«Начать» as wide as the column's inside", 260f, start.width.value, 0.5f)
        assertEquals("«Начать» at the right of the sheet, 20 in", (window.right - 20.dp).value, start.right.value, 0.5f)
        assertEquals("«Начать» at the bottom of the column, 16 over it", (window.bottom - 16.dp).value, start.bottom.value, 0.5f)
    }

    private fun swipeDown(on: String) {
        compose.onNodeWithText(on).performTouchInput {
            swipe(start = center, end = Offset(center.x, center.y + SWIPE.dp.toPx()), durationMillis = SWIPE_MS)
        }
    }

    @Test
    fun aSwipeBackAndATapBesideOnlyHideTheChoice() {
        showSheet(pickerOf())
        swipeDown(word(TITLE))
        compose.waitForIdle()
        compose.onNodeWithText(word(TITLE)).assertDoesNotExist()
        compose.runOnIdle { assertEquals("a swipe: hidden, nothing else", listOf<BlockIntent>(BlockIntent.SheetDismissed), intents) }

        sheet = pickerOf()
        compose.waitForIdle()
        // the key itself, not Espresso.pressBack: the time of the practice ticks in the sheet every second, and Espresso waits for its
        // window to stop laying out (RootViewWithoutFocusException)
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        compose.onNodeWithText(word(TITLE)).assertDoesNotExist()
        compose.runOnIdle { assertEquals("«назад»: hidden, nothing else", List(2) { BlockIntent.SheetDismissed }, intents) }

        sheet = pickerOf()
        compose.waitForIdle()
        // the window of the sheet, tapped near its top: the scrim over Live, above the sheet at 88 % of the screen
        compose.onAllNodes(isRoot()).filter(hasAnyDescendant(hasText(word(TITLE)))).onFirst().performTouchInput {
            click(Offset(centerX, SCRIM_TAP.dp.toPx()))
        }
        compose.waitForIdle()
        compose.onNodeWithText(word(TITLE)).assertDoesNotExist()
        compose.runOnIdle { assertEquals("a tap beside: hidden, nothing else", List(3) { BlockIntent.SheetDismissed }, intents) }
    }

    /**
     * The choice changes every second — the time of the practice, the minutes of the block. A second that turns while a swiped sheet
     * slides down, or after it is down, is no new sheet: frame by frame the sheet only goes down, it is hidden once, and it stays down
     * however late its owner drops it. The view model of Live drops it in the frame the sheet comes to rest; the owner here drops it
     * only when the test says so, as one whose state comes a frame or more later would — then a frame given the choice itself, with its
     * clock, would take each tick for a new value and bring the sheet back up (spec 3.36.6: a swipe only hides it).
     */
    @Test
    fun aTickOfTheClockUnderASwipeDoesNotBringTheChoiceBackUpForAnOwnerThatAnswersLate() {
        showSheet(pickerOf(), dropsAtOnce = false)
        val title = compose.onNodeWithText(word(TITLE))
        var last = title.bounds().top
        swipeDown(word(TITLE))
        compose.mainClock.autoAdvance = false
        // a second of the practice turns while the sheet slides down, and more of them once it is down
        sheet = pickerOf().copy(practiceMs = PRACTICE_MS + 1_000)
        repeat(SLIDE_FRAMES) { frame ->
            compose.mainClock.advanceTimeByFrame()
            if (frame % TICK_FRAMES == TICK_FRAMES - 1) sheet = pickerOf().copy(practiceMs = PRACTICE_MS + (frame / TICK_FRAMES + 2) * 1_000L)
            if (exists(word(TITLE))) {
                val top = title.bounds().top
                assertTrue("frame ${frame + 1}: the sheet comes back up, from $last to $top", top >= last - REBOUND)
                last = top
            }
        }
        compose.runOnIdle { assertEquals("hidden once, and told so", listOf<BlockIntent>(BlockIntent.SheetDismissed), intents) }
        // the owner answers at last
        sheet = null
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithText(word(TITLE)).assertDoesNotExist()
        compose.runOnIdle { assertEquals("nothing else on the way", listOf<BlockIntent>(BlockIntent.SheetDismissed), intents) }
    }

    /**
     * «Начать занятие» of the offer: the practice starts here, and the same frame grows into the choice — frame by frame the title of
     * either face never goes below the lower of the places the two stand at; a frame that slid down and rose again, or a window made
     * anew for the choice and rising from the bottom, would take it far below. Nothing hid the sheet on the way.
     */
    private fun theOfferGrowsIntoTheChoiceInPlace(reduceMotion: Boolean) {
        showSheet(BlockSheet.Offer, reduceMotion = reduceMotion)
        val stood = top(word(OFFER_TITLE))
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText(word(START_PRACTICE)).performClick()
        var lowest = stood
        repeat(FACE_FRAMES) {
            compose.mainClock.advanceTimeByFrame()
            listOf(word(OFFER_TITLE), word(TITLE)).forEach { title -> if (exists(title)) lowest = maxOf(lowest, top(title)) }
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithText(word(TITLE)).assertIsDisplayed()
        compose.onNodeWithText(word(TIME)).assertIsDisplayed()
        val settled = top(word(TITLE))
        // the sheet grew: the choice stands higher than the offer did — or this test would prove nothing
        assertTrue("the choice at $settled stands above the offer at $stood", settled < stood - 1.dp)
        assertTrue("in place: at most $lowest, the faces stand at $stood and $settled", lowest <= maxOf(stood, settled) + 1.dp)
        compose.runOnIdle { assertEquals("started here, never hidden on the way", listOf<BlockIntent>(BlockIntent.StartPracticeClicked), intents) }
    }

    @Test
    fun theOfferStartsThePracticeAndTheSameSheetGrowsIntoTheChoiceInPlace() = theOfferGrowsIntoTheChoiceInPlace(reduceMotion = false)

    @Test
    fun withoutAnimationsTheOfferBecomesTheChoiceInPlaceAtOnce() = theOfferGrowsIntoTheChoiceInPlace(reduceMotion = true)

    /**
     * «Сначала — занятие» in a low window at the font 1.5 (the review of stage 116): 640 × 360 lying behind a cutout — the sheet 603.5
     * wide; the status bar of 28, the gestures of 24 and the handle of 49 leave its content 259. Higher than that in German and French,
     * it scrolls: «Не сейчас» keeps its 48 and its words, where the frame that does not scroll squeezed it.
     */
    private fun theOfferScrollsInALowWindow(language: String) {
        speaking(language)
        windowSize = DpSize(603.5.dp, 259.dp)
        fontScale = 1.5f
        showOffer()
        val where = "$language, 603.5 × 259 at 1.5"
        val range = compose.onNode(hasScrollAction()).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue("$where: the offer is higher than its window and scrolls", range.maxValue() > 0f)
        val start = compose.onNodeWithText(word(START_PRACTICE)).bounds()
        assertTrue("$where: «Начать занятие» keeps its 48: $start", start.height >= 47.5.dp)
        val later = compose.onNodeWithText(word(OFFER_LATER))
        assertTrue("$where: «Не сейчас» keeps its 48: ${later.bounds()}", later.bounds().height >= 47.5.dp)
        assertWholeOnOneLine(compose.onNodeWithText(word(OFFER_LATER), useUnmergedTree = true), "$where: ${word(OFFER_LATER)}")
        later.performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf<BlockIntent>(BlockIntent.NotNowClicked), intents) }
    }

    @Test
    fun inGermanInALowWindowAtTheFont1_5TheOfferScrollsAndKeepsItsButtons() = theOfferScrollsInALowWindow("de")

    @Test
    fun inFrenchInALowWindowAtTheFont1_5TheOfferScrollsAndKeepsItsButtons() = theOfferScrollsInALowWindow("fr")

    /** Whether the lights inside «Начать занятие» moved over [LIGHTS_MS] of the test's clock: its pixels before and after. */
    private fun lightsMoved(): Boolean {
        val buttons = compose.onAllNodesWithText(word(START_PRACTICE)).filter(hasClickAction())
        buttons.assertCountEquals(1)
        val before = buttons.onFirst().captureToImage().toPixelMap()
        compose.mainClock.advanceTimeBy(LIGHTS_MS)
        val after = buttons.onFirst().captureToImage().toPixelMap()
        assertEquals("the same button", before.width to before.height, after.width to after.height)
        return (0 until before.width).any { x -> (0 until before.height).any { y -> before[x, y] != after[x, y] } }
    }

    /**
     * Spec 3.36.6: on Live only the ring and the dot of a take move while one plays — the lights of the living «Начать занятие» of the
     * offer stand still while a note sounds or a take records (Live gives the sheet `calm`), and live again in silence.
     */
    @Test
    fun onLiveTheLightsOfStartPracticeStandStillWhileANoteSoundsOrATakeRecords() {
        showLive()
        assertTrue("in silence the lights of «Начать занятие» move — or this test would prove nothing", lightsMoved())
        live = liveOf(sounding)
        // they slow to a stop over 0.8 s
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        assertFalse("a note sounds: the lights stand still", lightsMoved())
        live = liveOf(LiveSignal.Silence, recording = RecordingState(elapsedMs = 1_000))
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        assertFalse("a take records in silence: the lights stand still", lightsMoved())
        live = liveOf(LiveSignal.Silence)
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        assertTrue("silence again: the lights live", lightsMoved())
    }

    private companion object {
        /** Two whole pixels at 2.625 px a dp: what the rounding of a row of four chips and three gaps may take from one chip. */
        const val PIXELS_OF_A_ROW = 0.8f

        const val WINDOW = "window"
        const val PICKER = "picker"
        const val MIN = 60_000L
        const val PRACTICE_MS = 24 * MIN + 18_000
        const val LEFT_MINUTES = 7

        /** Minutes of two digits: «12 мин» is one word, wider than «7 мин». */
        const val LONG_LEFT = 12
        const val D_DUR = "D-dur · 2 октавы"
        const val CONCERTO = 1L
        const val CONCERTO_TITLE = "Концерт ля минор, 1 ч."
        const val VIVALDI = "А. Вивальди"
        const val CHARDASH = 3L
        const val SWIPE = 400
        const val SWIPE_MS = 300L
        const val SCRIM_TAP = 24
        const val SLIDE_FRAMES = 60

        /** A second of the practice every so many frames. */
        const val TICK_FRAMES = 15

        /** The frames of the offer growing into the choice: the cross-fade of 240 and the growth of 320, and more. */
        const val FACE_FRAMES = 40

        /** Long enough for the lights to go a visible way: they cross the button in seconds. */
        const val LIGHTS_MS = 3_000L

        /** Longer than the lights take to slow to a stop or gather speed (0.8 s). */
        const val SETTLE_MS = 1_000L
        const val A4 = 69

        /**
         * Material's spring settles a sheet past its anchor by a fraction of a dp and back (damping 0.9: ≈ 0.15 % of the way); a sheet
         * brought up again climbs tens of dp in its first frame.
         */
        val REBOUND = 2.dp

        const val TITLE = "title"
        const val TIME = "time"
        const val NOW_LABEL = "nowLabel"
        const val LEFT = "left"
        const val TAIL = "tail"
        const val NOW_SAID = "nowSaid"
        const val STOP = "stop"
        const val GOAL_TITLE = "goalTitle"
        const val GOAL_RANGE = "goalRange"
        const val START = "start"
        const val MINUS = "minus"
        const val PLUS = "plus"
        const val MINUS_SAID = "minusSaid"
        const val PLUS_SAID = "plusSaid"
        const val PIECES = "pieces"
        const val SCALES = "scales"
        const val TODAY_ONE = "todayOne"
        const val RUNNING = "running"
        const val DONE_SAID = "doneSaid"
        const val PLAYED = "played"
        const val DONE = "done"
        const val START_PRACTICE = "startPractice"
        const val OFFER_TITLE = "offerTitle"
        const val OFFER_LATER = "offerLater"

        val sounding = LiveSignal.Sounding(note = Note(A4), zone = Zone.IN_TUNE, direction = null, displayCents = 2)

        val lesson = listOf(
            PickerSection(
                SectionRef.BuiltIn(PieceSection.PIECES), null, doneToday = 1,
                listOf(
                    PickerPiece(CONCERTO, CONCERTO_TITLE, VIVALDI, TodayMark.Done(15 * MIN)),
                    PickerPiece(2, "Юмореска", "А. Дворжак", TodayMark.Played(7 * MIN)),
                    PickerPiece(CHARDASH, "Чардаш", "В. Монти", TodayMark.None),
                ),
            ),
            PickerSection(
                SectionRef.BuiltIn(PieceSection.SCALES), null, doneToday = 0,
                listOf(PickerPiece(4, "G-dur · 3 октавы", null, TodayMark.None), PickerPiece(5, D_DUR, null, TodayMark.Running)),
            ),
        )

        /** Three sections of twelve: more than a phone of 360 shows. */
        val many = listOf(PieceSection.PIECES, PieceSection.SCALES, PieceSection.ETUDES).mapIndexed { s, section ->
            PickerSection(
                SectionRef.BuiltIn(section), null, doneToday = 0,
                List(12) { i -> PickerPiece(s * 100L + i + 1, "Элемент ${s * 100 + i + 1}", null, TodayMark.None) },
            )
        }
    }
}
