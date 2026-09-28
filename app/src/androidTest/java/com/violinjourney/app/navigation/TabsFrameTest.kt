package com.violinjourney.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.ui.theme.ViolinTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The root of the tabs (spec 3.36.2): the body gets the window insets at the top and the sides and, at the bottom, the height of
 * the bar — or the bottom inset when there is no bar — as under Scaffold; and the body lies over the bar — so the glow of
 * «Начать занятие» falls on it — while a touch in the strip of the bar that the body has no target for still reaches the bar.
 *
 * The insets are all different and none is zero, so each padding tells which of them it came from.
 */
@RunWith(AndroidJUnit4::class)
class TabsFrameTest {
    @get:Rule
    val compose = createComposeRule()

    private var padding: PaddingValues? = null
    private var barTouches = 0
    private var bodyTouches = 0
    private var glowTouches = 0

    /** [glow] — a part of the body that reaches down into the strip of the bar, as the glow of the start button does. */
    private fun show(withBar: Boolean = true, glow: Boolean = false) {
        compose.setContent {
            ViolinTheme {
                TabsFrame(
                    bottomBar = {
                        if (withBar) {
                            Box(Modifier.fillMaxWidth().height(BAR).background(BAR_COLOR).testTag(TAG_BAR).clickable { barTouches++ })
                        }
                    },
                    contentWindowInsets = WindowInsets(left = INSET_LEFT, top = INSET_TOP, right = INSET_RIGHT, bottom = INSET_BOTTOM),
                    modifier = Modifier.fillMaxSize().testTag(TAG_ROOT),
                ) { inner ->
                    padding = inner
                    // the body fills the frame, as the NavHost does, and pads itself by what it is given
                    Box(Modifier.fillMaxSize().padding(inner).testTag(TAG_BODY).clickable { bodyTouches++ }) {
                        if (glow) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .offset(y = GLOW / 2)
                                    .size(GLOW)
                                    .background(GLOW_COLOR)
                                    .testTag(TAG_GLOW)
                                    .clickable { glowTouches++ },
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun theBodyIsPaddedByTheHeightOfTheBarAndTheInsetsAtTheTopAndTheSides() {
        show()
        val inner = padding!!
        assertNear(BAR, inner.calculateBottomPadding(), "the bottom padding is the bar alone, not the bar and the inset")
        assertNear(INSET_TOP, inner.calculateTopPadding(), "the top padding")
        assertNear(INSET_LEFT, inner.calculateStartPadding(LayoutDirection.Ltr), "the start padding")
        assertNear(INSET_RIGHT, inner.calculateEndPadding(LayoutDirection.Ltr), "the end padding")
        val root = compose.onNodeWithTag(TAG_ROOT).getUnclippedBoundsInRoot()
        val body = compose.onNodeWithTag(TAG_BODY).getUnclippedBoundsInRoot()
        assertNear(root.height - INSET_TOP - BAR, body.height, "the body ends at the top of the bar")
    }

    @Test
    fun withoutABarTheBodyGetsTheBottomInset() {
        show(withBar = false)
        val inner = padding!!
        assertNear(INSET_BOTTOM, inner.calculateBottomPadding(), "the bottom padding without a bar")
        assertNear(INSET_TOP, inner.calculateTopPadding(), "the top padding")
        assertNear(INSET_LEFT, inner.calculateStartPadding(LayoutDirection.Ltr), "the start padding")
        assertNear(INSET_RIGHT, inner.calculateEndPadding(LayoutDirection.Ltr), "the end padding")
    }

    @Test
    fun theBodyIsDrawnOverTheBar() {
        show(glow = true)
        val root = compose.onNodeWithTag(TAG_ROOT).fetchSemanticsNode().boundsInRoot
        val glow = compose.onNodeWithTag(TAG_GLOW).fetchSemanticsNode().boundsInRoot
        val bar = compose.onNodeWithTag(TAG_BAR).fetchSemanticsNode().boundsInRoot
        assertTrue("the glow reaches into the bar: $glow, $bar", glow.bottom > bar.top + 1)
        val pixels = compose.onNodeWithTag(TAG_ROOT).captureToImage().toPixelMap()
        fun colorAt(x: Float, y: Float) = pixels[(x - root.left).roundToInt(), (y - root.top).roundToInt()]
        // the lower half of the glow lies in the strip of the bar: it is seen there, the bar is under it
        val over = colorAt(glow.center.x, (bar.top + glow.bottom) / 2)
        assertSameColor(GLOW_COLOR, over, "the glow over the bar")
        // beside the glow the bar is seen: the body draws nothing of its own in its strip
        assertSameColor(BAR_COLOR, colorAt(bar.left + bar.width / 8, bar.center.y), "the bar beside the glow")
    }

    @Test
    fun aTouchOnTheBarReachesItThoughTheBodyLiesOverIt() {
        show(glow = true)
        // beside the glow: the body has no target there, the touch goes on to the bar
        compose.onNodeWithTag(TAG_BAR).performTouchInput { click(Offset(width / 8f, centerY)) }
        compose.waitForIdle()
        assertEquals("the bar heard it", 1, barTouches)
        assertEquals("the body did not", 0, bodyTouches)
        assertEquals("the glow did not", 0, glowTouches)
    }

    @Test
    fun aTouchOnThePartOfTheBodyOverTheBarReachesTheBody() {
        show(glow = true)
        // the lower quarter of the glow is in the strip of the bar: what lies on top hears it first
        compose.onNodeWithTag(TAG_GLOW).performTouchInput { click(Offset(centerX, height * 0.85f)) }
        compose.waitForIdle()
        assertEquals("the glow heard it", 1, glowTouches)
        assertEquals("the bar under it did not", 0, barTouches)
    }

    private fun assertNear(expected: Dp, actual: Dp, message: String) {
        assertTrue("$message: $actual, not $expected", abs(expected.value - actual.value) <= 1f)
    }

    private fun assertSameColor(expected: Color, actual: Color, message: String) {
        val close = abs(expected.red - actual.red) < COLOR_SLACK &&
            abs(expected.green - actual.green) < COLOR_SLACK &&
            abs(expected.blue - actual.blue) < COLOR_SLACK
        assertTrue("$message: $actual, not $expected", close)
    }

    private companion object {
        val BAR = 80.dp
        val GLOW = 40.dp
        val INSET_LEFT = 8.dp
        val INSET_TOP = 24.dp
        val INSET_RIGHT = 12.dp
        val INSET_BOTTOM = 48.dp
        val BAR_COLOR = Color(0xFF2040C0)
        val GLOW_COLOR = Color(0xFFE08020)
        const val COLOR_SLACK = 0.05f
        const val TAG_ROOT = "root"
        const val TAG_BODY = "body"
        const val TAG_BAR = "bar"
        const val TAG_GLOW = "glow"
    }
}
