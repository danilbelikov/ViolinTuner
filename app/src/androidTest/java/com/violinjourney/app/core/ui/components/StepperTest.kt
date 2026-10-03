package com.violinjourney.app.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.ui.theme.ViolinTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * «− 47 мин +» of «Закончить занятие» and «Время за день» (spec 3.12, 3.36.3): a tap is one step when the finger lifts, a
 * hold repeats, and a press that turns into a drag of what holds the stepper — the sheet — changes nothing; the buttons are 64
 * by layout, the number is read with its caption, and a caption that appears does not move the buttons.
 */
@RunWith(AndroidJUnit4::class)
class StepperTest {
    @get:Rule
    val compose = createComposeRule()

    private val steps = mutableListOf<Int>()

    private fun show(canStepDown: Boolean = true, canStepUp: Boolean = true) {
        compose.setContent {
            ViolinTheme {
                // the sheet: a column that scrolls, as the content of `AppSheet`, taller than its window
                Column(Modifier.height(WINDOW.dp).verticalScroll(rememberScrollState())) {
                    Stepper(
                        value = "47 min",
                        onStep = { steps += it },
                        canStepDown = canStepDown,
                        canStepUp = canStepUp,
                        downDescription = DOWN,
                        upDescription = UP,
                    )
                    Spacer(Modifier.height((WINDOW * 3).dp))
                }
            }
        }
    }

    @Test
    fun aTapIsOneStep() {
        show()
        compose.onNodeWithContentDescription(UP).performTouchInput { click() }
        compose.onNodeWithContentDescription(DOWN).performTouchInput { click() }
        compose.waitForIdle()
        assertEquals(listOf(1, -1), steps)
    }

    @Test
    fun aDragThatBeginsOnAButtonChangesNothing() {
        show()
        compose.onNodeWithContentDescription(DOWN).performTouchInput {
            down(center)
            moveBy(Offset(0f, -DRAG.dp.toPx()))
            moveBy(Offset(0f, -DRAG.dp.toPx()))
            up()
        }
        compose.waitForIdle()
        assertEquals(emptyList<Int>(), steps)
    }

    @Test
    fun holdingRepeatsAfterAMomentAndTheLiftAddsNothing() {
        show()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription(DOWN).performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(BEFORE_REPEAT_MS)
        assertEquals("nothing before the hold becomes a hold", emptyList<Int>(), steps)
        compose.mainClock.advanceTimeBy(HOLD_MS)
        val held = steps.size
        assertTrue("400 ms, then every 120 ms: $held steps", held >= MIN_REPEATS)
        compose.onNodeWithContentDescription(DOWN).performTouchInput { up() }
        compose.mainClock.advanceTimeBy(BEFORE_REPEAT_MS)
        assertEquals(List(held) { -1 }, steps)
    }

    @Test
    fun aButtonAtItsLimitDoesNotStep() {
        show(canStepUp = false)
        compose.onNodeWithContentDescription(UP).performTouchInput { click() }
        compose.waitForIdle()
        assertEquals(emptyList<Int>(), steps)
    }

    // The layout itself is measured, not the bounds of touch: Compose widens a small target to 48 by itself (the lesson of stage 101).
    @Test
    fun theButtonsAre64ByLayoutAndAButtonAtItsLimitIsDisabled() {
        show(canStepUp = false)
        listOf(DOWN, UP).forEach { description ->
            val bounds = compose.onNodeWithContentDescription(description).getUnclippedBoundsInRoot()
            assertEquals("$description: width", 64f, (bounds.right - bounds.left).value, 0.5f)
            assertEquals("$description: height", 64f, (bounds.bottom - bounds.top).value, 0.5f)
        }
        compose.onNodeWithContentDescription(DOWN).assertIsEnabled()
        compose.onNodeWithContentDescription(UP).assertIsNotEnabled()
    }

    @Test
    fun theNumberReadsWithItsCaptionAsOneDescription() {
        compose.setContent {
            ViolinTheme {
                Stepper(
                    value = "37 min",
                    onStep = { steps += it },
                    canStepDown = true,
                    canStepUp = true,
                    downDescription = DOWN,
                    upDescription = UP,
                    caption = "17:55 — 18:32 · was 47 min",
                    valueDescription = SPOKEN,
                )
            }
        }
        compose.onNodeWithContentDescription(SPOKEN).assertExists()
        // the number and the caption are not read on their own
        compose.onAllNodesWithText("37 min").assertCountEquals(0)
        compose.onAllNodesWithText("18:32", substring = true).assertCountEquals(0)
    }

    /**
     * Where the kept place of the caption matters (spec 5.29 R3: its place stays, empty or not — the stepper does not jump): a stepper
     * 300 wide at the font of 1.3, as on a narrow phone with a large font. The caption goes on two lines under the number, the column
     * of the two is taller than the buttons of 64, and a caption that appeared in no kept place would lower «−» and «+» under the
     * finger and make the stepper taller — the sheet, standing on the bottom of the screen, would grow upwards.
     */
    @Test
    fun aCaptionThatAppearsDoesNotMoveTheButtons() {
        var caption by mutableStateOf<String?>(null)
        compose.setContent {
            ViolinTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = LARGE_FONT)) {
                    Stepper(
                        value = "47 min",
                        onStep = {},
                        canStepDown = true,
                        canStepUp = true,
                        downDescription = DOWN,
                        upDescription = UP,
                        modifier = Modifier.width(NARROW.dp).testTag(STEPPER),
                        caption = caption,
                        captionReserve = "17:55 — 18:42 · was 47 min",
                    )
                }
            }
        }
        val downBefore = compose.onNodeWithContentDescription(DOWN).getUnclippedBoundsInRoot()
        val before = compose.onNodeWithTag(STEPPER).getUnclippedBoundsInRoot()
        caption = "17:55 — 18:32 · was 47 min"
        compose.waitForIdle()
        val downAfter = compose.onNodeWithContentDescription(DOWN).getUnclippedBoundsInRoot()
        val after = compose.onNodeWithTag(STEPPER).getUnclippedBoundsInRoot()
        // the column under the number outgrows the buttons here — or the test could not tell a kept place from none
        assertTrue("the stepper is taller than its buttons: ${after.height}", after.height > (BUTTON + 8).dp)
        assertEquals("«−» stays where the finger is", downBefore.top.value, downAfter.top.value, 0.5f)
        assertEquals("the stepper keeps its height", before.height.value, after.height.value, 0.5f)
    }

    private companion object {
        const val SPOKEN = "37 min, from 17:55 to 18:32, was 47 min"
        const val DOWN = "minus five minutes"
        const val UP = "plus five minutes"
        const val WINDOW = 200
        const val STEPPER = "stepper"
        const val NARROW = 300
        const val LARGE_FONT = 1.3f
        const val BUTTON = 64
        const val DRAG = 60
        const val BEFORE_REPEAT_MS = 300L
        const val HOLD_MS = 700L
        const val MIN_REPEATS = 5
    }
}
