package com.violinjourney.app.core.ui.components

import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.ui.theme.ViolinTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * The bottom zone (spec 3.36.1, 5.29): the content runs to the bottom of the screen and is told the height of the zone from the
 * first frame; the ground of the zone takes the touches between its buttons, the fade over it lets them through; a window no
 * higher than 360 dp makes its buttons 48; over the keyboard the field in focus stays above the zone.
 */
@RunWith(AndroidJUnit4::class)
class AppDockTest {
    @get:Rule
    val compose = createComposeRule()

    private var inset: Dp = Dp.Unspecified

    /** Every inset the content was composed with, the first frame's first: a zone learned a frame late would give it 0 first. */
    private val insets = mutableListOf<Dp>()
    private var told: Boolean? = null
    private var contentTouches = 0
    private var buttonTouches = 0

    private fun show(metrics: DockMetrics) {
        compose.setContent {
            ViolinTheme {
                AppDock(
                    dock = {
                        told = compact
                        AppButton("Сохранить", onClick = { buttonTouches++ }, Modifier.fillMaxWidth().testTag(BUTTON), compact = compact)
                    },
                    modifier = Modifier.fillMaxSize().testTag(ROOT),
                    metrics = metrics,
                ) {
                    inset = LocalDockInset.current
                    insets += inset
                    Box(Modifier.fillMaxSize().testTag(CONTENT).clickable { contentTouches++ })
                }
            }
        }
        compose.waitForIdle()
    }

    /** The top edge of the zone, from the top of the dock: [metrics] top above its button. */
    private fun zoneTop(metrics: DockMetrics): Dp = compose.onNodeWithTag(BUTTON).getUnclippedBoundsInRoot().top - metrics.top

    private fun assertNear(expected: Dp, actual: Dp, message: String) {
        assertTrue("$message: $actual, not $expected", abs(expected.value - actual.value) <= 1f)
    }

    @Test
    fun theContentIsToldTheHeightOfTheZoneAndRunsUnderIt() {
        show(DockMetrics.Regular)
        val root = compose.onNodeWithTag(ROOT).getUnclippedBoundsInRoot()
        assertNear(root.bottom - zoneTop(DockMetrics.Regular), inset, "the inset is the zone with the system inset under it")
        assertNear(root.bottom, compose.onNodeWithTag(CONTENT).getUnclippedBoundsInRoot().bottom, "the content runs to the bottom")
        // from the first frame: the system inset may come a frame later, the zone itself may not
        compose.runOnIdle { assertFalse("the content was composed without the zone first: $insets", 0.dp in insets) }
    }

    /**
     * A form over the keyboard (R3 «Имя и фото», R4): its last field, just above the zone, is in focus when the keyboard comes up
     * and lifts the zone over it — the field goes up with them and stays above the zone, on the screen. The keyboard is the insets
     * a real one gives the window, handed to the view of the composition; the field is a focus target, so that no real keyboard
     * comes up and hands its own.
     */
    @Test
    fun theFieldInFocusStaysAboveTheZoneWhenTheKeyboardComesUp() {
        val focus = FocusRequester()
        var formTop by mutableStateOf(0.dp)
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            ViolinTheme {
                AppDock(
                    dock = { AppButton("Сохранить", onClick = {}, Modifier.fillMaxWidth().testTag(BUTTON), compact = compact) },
                    modifier = Modifier.fillMaxSize().testTag(ROOT),
                    aboveKeyboard = true,
                    metrics = DockMetrics.Regular,
                ) {
                    inset = LocalDockInset.current
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Spacer(Modifier.height(formTop))
                        Box(Modifier.fillMaxWidth().height(FIELD_HEIGHT).focusRequester(focus).focusable().testTag(FIELD))
                        Spacer(Modifier.height(LocalDockInset.current))
                    }
                }
            }
        }
        compose.waitForIdle()
        val root = compose.onNodeWithTag(ROOT).getUnclippedBoundsInRoot()
        val zoneDown = zoneTop(DockMetrics.Regular)
        formTop = zoneDown - root.top - FIELD_HEIGHT - FIELD_GAP
        compose.runOnIdle { focus.requestFocus() }
        compose.onNodeWithTag(FIELD).assertIsFocused()
        assertNear(zoneDown - FIELD_GAP, compose.onNodeWithTag(FIELD).getUnclippedBoundsInRoot().bottom, "the last field, above the zone")

        val keyboard = with(compose.density) { (root.height / KEYBOARD_SHARE).roundToPx() }
        compose.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(view, keyboardInsets(keyboard)) }
        compose.waitForIdle()

        val zoneUp = zoneTop(DockMetrics.Regular)
        assertTrue("the zone rose with the keyboard: $zoneUp, was $zoneDown", zoneUp < zoneDown - FIELD_HEIGHT - FIELD_GAP)
        val field = compose.onNodeWithTag(FIELD).getUnclippedBoundsInRoot()
        assertTrue("the field in focus is above the zone: ${field.bottom}, the zone at $zoneUp", field.bottom <= zoneUp + 1.dp)
        assertTrue("and on the screen: ${field.top}, the root at ${root.top}", field.top >= root.top - 1.dp)
        compose.runOnIdle { assertEquals("nothing runs under the zone over the keyboard", 0.dp, inset) }
    }

    /** What the window hands its views while the keyboard is up: the keyboard alone, [bottom] px from the bottom. */
    private fun keyboardInsets(bottom: Int): WindowInsetsCompat = WindowInsetsCompat.Builder()
        .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, bottom))
        .setVisible(WindowInsetsCompat.Type.ime(), true)
        .build()

    @Test
    fun aTouchOnTheGroundOfTheZoneDoesNotReachTheContent() {
        show(DockMetrics.Regular)
        val root = compose.onNodeWithTag(ROOT).getUnclippedBoundsInRoot()
        val y = zoneTop(DockMetrics.Regular) - root.top + 2.dp
        compose.onNodeWithTag(ROOT).performTouchInput { click(Offset(width / 2f, y.toPx())) }
        compose.runOnIdle {
            assertEquals("the list under the zone", 0, contentTouches)
            assertEquals("the button is lower", 0, buttonTouches)
        }
    }

    @Test
    fun aTouchOnTheFadeReachesTheContent() {
        show(DockMetrics.Regular)
        val root = compose.onNodeWithTag(ROOT).getUnclippedBoundsInRoot()
        val y = zoneTop(DockMetrics.Regular) - root.top - 10.dp
        compose.onNodeWithTag(ROOT).performTouchInput { click(Offset(width / 2f, y.toPx())) }
        compose.runOnIdle { assertEquals(1, contentTouches) }
    }

    @Test
    fun aLowWindowMakesTheButtonsOfTheZone48() {
        show(DockMetrics.Tiny)
        compose.runOnIdle { assertEquals(true, told) }
        compose.onNodeWithTag(BUTTON).assertHeightIsEqualTo(48.dp)
    }

    @Test
    fun aRegularWindowKeepsTheButtonsOfTheZone56() {
        show(DockMetrics.Regular)
        compose.runOnIdle { assertEquals(false, told) }
        compose.onNodeWithTag(BUTTON).assertHeightIsEqualTo(56.dp)
    }

    private companion object {
        const val ROOT = "dock"
        const val CONTENT = "content"
        const val BUTTON = "button"
        const val FIELD = "field"
        val FIELD_HEIGHT = 56.dp
        val FIELD_GAP = 8.dp

        /** The keyboard takes a third of the window: a field of 56 just above the zone is well under it, in landscape too. */
        const val KEYBOARD_SHARE = 3
    }
}
