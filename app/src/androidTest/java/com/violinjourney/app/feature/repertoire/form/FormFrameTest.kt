package com.violinjourney.app.feature.repertoire.form

import android.view.View
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.ui.components.AppButton
import com.violinjourney.app.core.ui.theme.ViolinTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The frame of the forms of R4 over the keyboard (spec 3.36.4, «Клавиатура»; the review of stage 110): the field in focus comes up to
 * the top of the column once the keyboard stands — and only the keyboard brings it up. The bottom zone growing over the column (a
 * reason coming, the plate of a twin) and the fields redrawn by a tap elsewhere leave the column where the player scrolled it.
 *
 * The field is a focus target, not a text field, so that no real keyboard comes up and hands the window insets of its own (as in
 * AppDockTest); the keyboard is the insets a real one gives the window, handed to the view of the composition. Laid out in a window of
 * 412 × 800 with a font of 1.0, whatever the device's.
 */
@RunWith(AndroidJUnit4::class)
class FormFrameTest {
    @get:Rule
    val compose = createComposeRule()

    private val focus = FocusRequester()
    private var reason by mutableStateOf(false)
    private var redraws by mutableIntStateOf(0)
    private lateinit var view: View

    private class Window(private val size: IntSize) : WindowInfo {
        override val isWindowFocused: Boolean = true
        override val containerSize: IntSize get() = size
    }

    @Composable
    private fun InWindow(width: Dp, height: Dp, content: @Composable () -> Unit) {
        val density = LocalDensity.current
        val window = with(density) { Window(IntSize(width.roundToPx(), height.roundToPx())) }
        CompositionLocalProvider(LocalWindowInfo provides window, LocalDensity provides Density(density.density, 1f)) {
            Box(Modifier.requiredSize(width, height)) { content() }
        }
    }

    private fun show() {
        compose.setContent {
            view = LocalView.current
            ViolinTheme {
                InWindow(412.dp, 800.dp) {
                    FormFrame(
                        title = TITLE,
                        loading = false,
                        onClose = {},
                        dock = { _ ->
                            // a reason over the button, as «Без названия не сохранить» comes when the title is wiped
                            if (reason) Text(REASON)
                            AppButton(SAVE, onClick = {}, modifier = Modifier.fillMaxWidth(), compact = compact)
                        },
                    ) {
                        Box(Modifier.fillMaxWidth().height(ABOVE).testTag(ABOVE_TAG))
                        Box(Modifier.fillMaxWidth().height(FIELD).then(riseWhenFocused()).focusRequester(focus).focusable().testTag(FIELD_TAG))
                        // what the fields read of the state: a tap on the tempo or the status redraws them
                        Text("$redraws")
                        Box(Modifier.fillMaxWidth().height(BELOW))
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** What the window hands its views while the keyboard is up: the keyboard alone, [height] from the bottom. */
    private fun keyboard(height: Dp) {
        val bottom = with(compose.density) { height.roundToPx() }
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, bottom))
            .setVisible(WindowInsetsCompat.Type.ime(), bottom > 0)
            .build()
        compose.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(view, insets) }
        compose.waitForIdle()
    }

    /** Past the 150 ms the keyboard stands still for, and the scroll that brings the field up. */
    private fun settle() {
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.waitForIdle()
    }

    private fun fieldTop(): Dp = compose.onNodeWithTag(FIELD_TAG).getUnclippedBoundsInRoot().top

    private fun assertNear(expected: Dp, actual: Dp, message: String) {
        assertEquals("$message: $actual, not $expected", expected.value, actual.value, 1f)
    }

    @Test
    fun onlyTheKeyboardBringsTheFieldInFocusUpToTheTopOfTheColumn() {
        show()
        // the top of the column, 8 under the bar: where the first field stands before anything is scrolled
        val top = compose.onNodeWithTag(ABOVE_TAG).getUnclippedBoundsInRoot().top

        keyboard(KEYBOARD)
        compose.runOnIdle { focus.requestFocus() }
        compose.onNodeWithTag(FIELD_TAG).assertIsFocused()
        settle()
        assertNear(top, fieldTop(), "the field in focus comes up to the top of the column 150 ms after the focus, the keyboard up already")

        // the player scrolls back up to what is above it; the field stays in sight, lower
        compose.onNodeWithTag(ABOVE_TAG).performScrollTo()
        compose.waitForIdle()
        val scrolled = fieldTop()
        assertTrue("scrolled down the column: $scrolled, the top $top", scrolled > top + ABOVE / 2)

        // the zone grows over the column: its window is lower — the column stays where the player left it
        reason = true
        settle()
        assertNear(scrolled, fieldTop(), "a reason in the zone does not bring the field back up")

        // a tap elsewhere redraws the fields — the column stays
        redraws++
        settle()
        assertNear(scrolled, fieldTop(), "a redraw of the form does not bring the field back up")

        // the keyboard moves: now the field comes up again
        keyboard(LOWER_KEYBOARD)
        settle()
        assertNear(top, fieldTop(), "the keyboard, standing again, brings it up")
    }

    private companion object {
        const val TITLE = "Новое произведение"
        const val SAVE = "Сохранить"
        const val REASON = "Без названия не сохранить"
        const val ABOVE_TAG = "above"
        const val FIELD_TAG = "field"
        val ABOVE = 120.dp
        val FIELD = 56.dp

        /** Enough under the field for the column to bring it up to its top whatever the keyboard. */
        val BELOW = 600.dp

        /** 800 − 56 of the bar − 300 − the zone of 78: a window of 366 over the keyboard, the field of 56 with 8 of air in it. */
        val KEYBOARD = 300.dp
        val LOWER_KEYBOARD = 250.dp
        const val SETTLE_MS = 600L
    }
}
