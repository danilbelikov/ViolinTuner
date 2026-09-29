package com.violinjourney.app.feature.repertoire.stand

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.domain.repertoire.Accidental
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.domain.repertoire.Tonic
import com.violinjourney.app.core.domain.repertoire.scale.ScaleKind
import com.violinjourney.app.core.domain.repertoire.scale.ScaleSpec
import com.violinjourney.app.core.domain.repertoire.scale.Scales
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.repertoire.scale.scaleTitle
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.record_stop
import com.violinjourney.app.shared.resources.stand_counter
import com.violinjourney.app.shared.resources.stand_hint_card
import com.violinjourney.app.shared.resources.stand_page_description
import com.violinjourney.app.shared.resources.take_record
import org.jetbrains.compose.resources.stringResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The upright stand (spec 3.15, 3.22): a drawn scale taller than the stand scrolls instead of losing its last systems,
 * and a photo turned upright keeps no scroll bar of the lying sheet; a page deleted while zoomed leaves the next one at
 * 1×, free to be swiped; a swipe lights the edge it turned through; a drawn page is named by its number and its scale. The first
 * visit (3.36.4): the hint hides the capsule of the count but not that of a running take, and goes at the first touch of any kind.
 */
@RunWith(AndroidJUnit4::class)
class StandScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val config = RepertoireConfig()
    private val idle = StandTake(recording = false, elapsedSeconds = 0, problem = null)

    private fun stateOf(pages: List<StandPage>) =
        StandState(loading = false, pages = pages, initialPage = 0, panelVisible = false, deleteDialog = false, showHint = false)

    private fun drawn(tonic: Tonic, accidental: Accidental, octaves: Int) =
        StandPage(StandPage.DRAWN_ID, path = null, scale = Scales.build(ScaleSpec(tonic, accidental, ScaleKind.MAJOR, octaves), config.scaleLowestMidi, config.scaleHighestMidi))

    private fun show(onIntent: (StandIntent) -> Unit = {}, state: () -> StandState) {
        compose.setContent {
            ViolinTheme {
                // an upright phone: narrower than the screen of the test, so that the size is the same everywhere
                Box(Modifier.size(STAND_WIDTH.dp, STAND_HEIGHT.dp).testTag(STAND)) { StandScreen(state(), idle, onIntent = onIntent, onRecordClick = {}) }
            }
        }
    }

    private val verticalScroll = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
    private val pager = SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange)

    private fun scrollRange(): Float = compose.onNode(verticalScroll).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue()

    @Test
    fun aLongDrawnScaleScrollsDownToItsLastSystem() {
        // Cis-dur in three octaves: six systems and more, taller than the stand
        show { stateOf(listOf(drawn(Tonic.C, Accidental.SHARP, octaves = 3))) }
        assertTrue("the systems below the stand are reached by scrolling", scrollRange() > 0f)
    }

    @Test
    fun aDrawnPageIsNamedByItsNumberAndByItsScale() {
        val page = drawn(Tonic.G, Accidental.NATURAL, octaves = 3)
        var title = ""
        var number = ""
        compose.setContent {
            title = scaleTitle(page.scale!!.spec)
            number = stringResource(Res.string.stand_page_description, 1, 1)
            ViolinTheme { Box(Modifier.size(STAND_WIDTH.dp, STAND_HEIGHT.dp)) { StandScreen(stateOf(listOf(page)), idle, onIntent = {}, onRecordClick = {}) } }
        }
        // one stop for a reader: «Страница 1 из 1», then «Ноты гаммы G-dur · 3 октавы» — not «Ноты гаммы Страница 1 из 1»
        val sheet = SemanticsMatcher("the sheet") { it.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() == number }
        val said = compose.onNode(sheet).fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
        assertEquals(number, said.first())
        assertEquals(2, said.size)
        assertTrue("the notes are named by the scale: $said", title in said[1])
        assertFalse("the notes are not named by the page: $said", number in said[1])
    }

    @Test
    fun aShortDrawnScaleStandsWhole() {
        show { stateOf(listOf(drawn(Tonic.C, Accidental.NATURAL, octaves = 1))) }
        assertEquals(0f, scrollRange(), 0f)
    }

    @Test
    fun aPhotoTurnedUprightShowsNoScrollBarOfTheLyingSheet() {
        // iOS turns the screen without composing it anew: the same stand, only wider than high and then the other way
        var lying by mutableStateOf(true)
        compose.setContent {
            ViolinTheme {
                val (width, height) = if (lying) TURNED_LONG to TURNED_SHORT else TURNED_SHORT to TURNED_LONG
                Box(Modifier.size(width.dp, height.dp).testTag(STAND)) {
                    StandScreen(stateOf(listOf(StandPage(1, null))), idle, onIntent = {}, onRecordClick = {})
                }
            }
        }
        assertTrue("the lying sheet is longer than the stand and scrolls", scrollRange() > 0f)

        compose.runOnIdle { lying = false }
        compose.waitForIdle()
        val pixels = compose.onNodeWithTag(STAND).captureToImage().toPixelMap()
        val y = pixels.height / 2
        val barX = pixels.width - with(compose.density) { SCROLL_BAR_FROM_RIGHT.dp.roundToPx() }
        assertEquals("the right margin of an upright photo is the stand's background", pixels[1, y], pixels[barX, y])
    }

    @Test
    fun aPageDeletedWhileZoomedLeavesTheNextOneAtOneAndFreeToSwipe() {
        var state by mutableStateOf(stateOf(listOf(StandPage(1, null), StandPage(2, null), StandPage(3, null))))
        show { state }
        compose.onNodeWithTag(STAND).performTouchInput { doubleClick(center) }
        compose.waitForIdle()
        fun canSwipe() = SemanticsActions.ScrollToIndex in compose.onNode(pager).fetchSemanticsNode().config
        assertFalse("a zoomed page does not turn", canSwipe())

        // what the view model does on «Удалить»: the next page takes the index of the one deleted
        compose.runOnIdle { state = stateOf(listOf(StandPage(2, null), StandPage(3, null))) }
        compose.waitForIdle()
        assertTrue("the page in its place is a new one, at 1×", canSwipe())
    }

    @Test
    fun aSwipeLightsTheEdgeItTurnedThrough() {
        show { stateOf(listOf(StandPage(1, null), StandPage(2, null), StandPage(3, null))) }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val background = edges().first
        compose.onNodeWithTag(STAND).performTouchInput { swipeLeft() }
        // frame by frame until the sheet has come to rest and a little after: the flash lasts 200 ms from the rest
        var lit = false
        repeat(FRAMES) {
            compose.mainClock.advanceTimeByFrame()
            val (left, right) = edges()
            // the edge of the stand is dark; lit, it takes the tint of the accent while the other edge keeps the background
            if (left == background && right != background && right.isDark()) lit = true
        }
        compose.mainClock.autoAdvance = true
        assertTrue("the right edge was lit by the swipe to the next page", lit)
    }

    // spec 3.36.4: the first visit — a card with both phrases; the hint is alone over the sheet: no capsule of the count over it.
    // That there is no panel is the view model's (StandViewModelTest); the capsule is the screen's own rule.
    @Test
    fun aFirstVisitShowsTheCardOfTheHintAndNoCapsuleOfTheCount() {
        var card = ""
        var counter = ""
        var hint by mutableStateOf(true)
        compose.setContent {
            card = stringResource(Res.string.stand_hint_card)
            counter = stringResource(Res.string.stand_counter, 1, 2)
            ViolinTheme {
                Box(Modifier.size(STAND_WIDTH.dp, STAND_HEIGHT.dp)) {
                    StandScreen(stateOf(listOf(StandPage(1, null), StandPage(2, null))).copy(showHint = hint), idle, onIntent = {}, onRecordClick = {})
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText(card).assertExists()
        compose.onAllNodesWithText(counter).assertCountEquals(0)

        // the hint gone, the panel still away: the capsule keeps the count
        compose.runOnIdle { hint = false }
        compose.waitForIdle()
        compose.onNodeWithText(counter).assertExists()
    }

    // the review of stage 109: a first visit during a take — the capsule «● 1:12 · 1 / 2» is the only sign of the take while the
    // panel is away, and the hint does not hide it
    @Test
    fun aFirstVisitDuringATakeKeepsTheCapsuleOfTheTake() {
        var running = ""
        compose.setContent {
            running = Formats.timer(RECORDED_SECONDS * 1_000L) + " · " + stringResource(Res.string.stand_counter, 1, 2)
            ViolinTheme {
                Box(Modifier.size(STAND_WIDTH.dp, STAND_HEIGHT.dp)) {
                    StandScreen(
                        stateOf(listOf(StandPage(1, null), StandPage(2, null))).copy(showHint = true),
                        StandTake(recording = true, elapsedSeconds = RECORDED_SECONDS, problem = null),
                        onIntent = {},
                        onRecordClick = {},
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText(running).assertExists()
    }

    // spec 3.36.4: the hint holds 4 s or until the first touch — of any kind: a swipe, a pinch, a double tap, not only the taps the
    // gestures of the sheet answer
    @Test
    fun anyFirstTouchOfTheSheetTakesTheHintAway() {
        val intents = mutableListOf<StandIntent>()
        show(onIntent = { intents += it }) { stateOf(listOf(StandPage(1, null), StandPage(2, null), StandPage(3, null))).copy(showHint = true) }
        compose.waitForIdle()

        compose.onNodeWithTag(STAND).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertTrue("a swipe: $intents", StandIntent.Touched in intents)

        compose.runOnIdle { intents.clear() }
        compose.onNodeWithTag(STAND).performTouchInput {
            pinch(center - Offset(20f, 0f), center - Offset(120f, 0f), center + Offset(20f, 0f), center + Offset(120f, 0f))
        }
        compose.waitForIdle()
        assertTrue("a pinch: $intents", StandIntent.Touched in intents)

        compose.runOnIdle { intents.clear() }
        compose.onNodeWithTag(STAND).performTouchInput { doubleClick(center) }
        compose.waitForIdle()
        assertTrue("a double tap: $intents", StandIntent.Touched in intents)
    }

    // spec 3.36.4: «Записать дубль» is the main button of the panel; a running take is the bar, and its «стоп» stops it
    @Test
    fun thePanelRecordsWithItsMainButtonAndARunningTakeStopsWithItsStop() {
        var take by mutableStateOf(idle)
        var record = ""
        var stop = ""
        var presses = 0
        compose.setContent {
            record = stringResource(Res.string.take_record)
            stop = stringResource(Res.string.record_stop)
            ViolinTheme {
                Box(Modifier.size(STAND_WIDTH.dp, STAND_HEIGHT.dp)) {
                    StandScreen(stateOf(listOf(StandPage(1, null))).copy(panelVisible = true), take, onIntent = {}, onRecordClick = { presses++ })
                }
            }
        }
        compose.onNodeWithText(record).performClick()
        compose.runOnIdle { take = StandTake(recording = true, elapsedSeconds = 12, problem = null) }
        compose.waitForIdle()
        compose.onAllNodesWithText(record).assertCountEquals(0)
        compose.onNodeWithContentDescription(stop).performClick()
        assertEquals(2, presses)
    }

    /** The colours at the left and the right edge of the stand, half-way down. */
    private fun edges(): Pair<Color, Color> {
        val pixels = compose.onNodeWithTag(STAND).captureToImage().toPixelMap()
        val y = pixels.height / 2
        return pixels[1, y] to pixels[pixels.width - 2, y]
    }

    private fun Color.isDark() = red < 0.5f && green < 0.5f && blue < 0.5f

    private companion object {
        const val FRAMES = 120
        const val RECORDED_SECONDS = 72L
        const val STAND = "stand"
        const val STAND_WIDTH = 400
        const val STAND_HEIGHT = 800

        // A turned stand that fits the screen of the test either way; lying, a sheet as wide as it allows is longer.
        const val TURNED_LONG = 400
        const val TURNED_SHORT = 200

        /** The middle of the scroll bar, which is 3 dp wide and stands 3 dp in from the right edge. */
        const val SCROLL_BAR_FROM_RIGHT = 4.5f
    }
}
