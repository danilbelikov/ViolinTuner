package com.violinjourney.app.feature.practice.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.ui.theme.ViolinTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * «− 47 мин +» of «Закончить занятие» and «Время за день» (spec 3.12): a tap is one step when the finger lifts, a
 * hold repeats, and a press that turns into a drag of what holds the stepper — the sheet — changes nothing.
 */
@RunWith(AndroidJUnit4::class)
class StepperTest {
    @get:Rule
    val compose = createComposeRule()

    private val steps = mutableListOf<Int>()

    private fun show(canStepDown: Boolean = true, canStepUp: Boolean = true) {
        compose.setContent {
            ViolinTheme {
                // the sheet: a column that scrolls, as `SheetColumn`, taller than its window
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

    private companion object {
        const val DOWN = "minus five minutes"
        const val UP = "plus five minutes"
        const val WINDOW = 200
        const val DRAG = 60
        const val BEFORE_REPEAT_MS = 300L
        const val HOLD_MS = 700L
        const val MIN_REPEATS = 5
    }
}
