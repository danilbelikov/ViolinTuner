package com.violinjourney.app.feature.practice.components

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import com.violinjourney.app.feature.journey.art.SECOND_MS
import com.violinjourney.app.feature.journey.art.VSYNC_120
import com.violinjourney.app.feature.journey.art.VirtualDisplay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest

/** The clock of the lights of «Начать занятие» (spec 3.16) sleeps between the postcards' frames and takes one a cell. */
@OptIn(ExperimentalCoroutinesApi::class)
class LightClockTest {
    private class Button(scope: TestScope) {
        val display = VirtualDisplay(scope.testScheduler, VSYNC_120)
        val seconds = mutableFloatStateOf(StartButtonMath.REST_SECONDS)
        val seen = mutableStateOf(true)
        val calm = mutableStateOf(false)
        val noted = mutableListOf<Float>()
        var first = -1L
        var last = -1L

        init {
            display.afterFrame = { noted += seconds.floatValue }
            display.onTime = { if (first < 0) first = it; last = it }
            scope.backgroundScope.launch(display) {
                runLightClock(seconds, seen = { seen.value }, widthDp = { StartButtonMath.REFERENCE_WIDTH_DP }, calm = { calm.value })
            }
        }

        fun changes() = noted.zipWithNext().count { (a, b) -> a != b }
    }

    @Test
    fun `the lights step thirty times a second and are written inside the frame`() = runTest {
        val button = Button(this)
        advanceTimeBy(2 * SECOND_MS)
        testScheduler.runCurrent()
        val changes = button.changes()
        assertTrue(changes in 58..62, "$changes changes in two seconds")
        // the first frame of a run only marks the time, and one more may be on its way
        assertTrue(button.display.asked <= changes + 2, "${button.display.asked} frames asked for $changes changes")
        assertEquals(button.noted.last(), button.seconds.floatValue, "written inside the frame")
        // at full pace a second of the clock is a second; the way up to it takes EASE_S and loses about half of it
        val ran = (button.last - button.first) / 1e9f
        val moved = button.seconds.floatValue - StartButtonMath.REST_SECONDS
        assertTrue(moved in (ran - StartButtonMath.EASE_S / 2 - 0.05f)..(ran - StartButtonMath.EASE_S / 2 + 0.05f), "moved $moved in $ran s")
    }

    @Test
    fun `when calm the lights slow to a stop over the ease and then ask for no frames`() = runTest {
        val button = Button(this)
        advanceTimeBy(2 * SECOND_MS)
        button.calm.value = true
        Snapshot.sendApplyNotifications()
        val atCalm = button.display.asked
        advanceTimeBy(2 * SECOND_MS)
        val slowing = button.display.asked - atCalm
        // EASE_S at thirty frames a second
        assertTrue(slowing in 22..28, "$slowing frames to slow down")
        val asked = button.display.asked
        val standing = button.seconds.floatValue
        advanceTimeBy(2 * SECOND_MS)
        assertEquals(asked, button.display.asked, "frames asked while calm")
        assertEquals(standing, button.seconds.floatValue)

        button.calm.value = false
        Snapshot.sendApplyNotifications()
        advanceTimeBy(SECOND_MS)
        assertTrue(button.display.asked - asked in 29..32, "${button.display.asked - asked} frames after the calm")
        assertTrue(button.seconds.floatValue > standing, "the lights go on from where they stood")
    }

    @Test
    fun `off the screen the lights stop at once and ask for no frames`() = runTest {
        val button = Button(this)
        advanceTimeBy(SECOND_MS)
        button.seen.value = false
        Snapshot.sendApplyNotifications()
        advanceTimeBy(100) // the step that was already on its way
        val asked = button.display.asked
        val standing = button.seconds.floatValue
        advanceTimeBy(2 * SECOND_MS)
        assertEquals(asked, button.display.asked, "frames asked off the screen")
        assertEquals(standing, button.seconds.floatValue)
    }
}
