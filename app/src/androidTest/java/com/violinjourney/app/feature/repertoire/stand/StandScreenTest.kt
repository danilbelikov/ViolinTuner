package com.violinjourney.app.feature.repertoire.stand

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.domain.repertoire.Accidental
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.domain.repertoire.Tonic
import com.violinjourney.app.core.domain.repertoire.scale.ScaleKind
import com.violinjourney.app.core.domain.repertoire.scale.ScaleSpec
import com.violinjourney.app.core.domain.repertoire.scale.Scales
import com.violinjourney.app.core.ui.theme.ViolinTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The upright stand (spec 3.15, 3.22): a drawn scale taller than the stand scrolls instead of losing its last systems,
 * and a photo turned upright keeps no scroll bar of the lying sheet; a page deleted while zoomed leaves the next one at
 * 1×, free to be swiped; a swipe lights the edge it turned through.
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

    private fun show(state: () -> StandState) {
        compose.setContent {
            ViolinTheme {
                // an upright phone: narrower than the screen of the test, so that the size is the same everywhere
                Box(Modifier.size(STAND_WIDTH.dp, STAND_HEIGHT.dp).testTag(STAND)) { StandScreen(state(), idle, onIntent = {}, onRecordClick = {}) }
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

    /** The colours at the left and the right edge of the stand, half-way down. */
    private fun edges(): Pair<Color, Color> {
        val pixels = compose.onNodeWithTag(STAND).captureToImage().toPixelMap()
        val y = pixels.height / 2
        return pixels[1, y] to pixels[pixels.width - 2, y]
    }

    private fun Color.isDark() = red < 0.5f && green < 0.5f && blue < 0.5f

    private companion object {
        const val FRAMES = 120
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
