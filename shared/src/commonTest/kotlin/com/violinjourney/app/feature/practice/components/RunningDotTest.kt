package com.violinjourney.app.feature.practice.components

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import com.violinjourney.app.feature.journey.art.SECOND_MS
import com.violinjourney.app.feature.journey.art.VSYNC_120
import com.violinjourney.app.feature.journey.art.VirtualDisplay
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest

/** The dot beside «Занятие идёт» (spec 3.16) breathes and turns on the postcards' frames, the same curve as before. */
@OptIn(ExperimentalCoroutinesApi::class)
class RunningDotTest {
    @Test
    fun `the breath is the curve of the infinite transition it replaced`() {
        val transition = TargetBasedAnimation(
            animationSpec = InfiniteRepeatableSpec(tween<Float>(PracticeMotion.DOT_HALF_BREATH_MS, easing = PracticeMotion.Breath), RepeatMode.Reverse),
            typeConverter = Float.VectorConverter,
            initialValue = 0f,
            targetValue = 1f,
        )
        var ms = 0L
        while (ms <= 3 * 2 * PracticeMotion.DOT_HALF_BREATH_MS) {
            val expected = transition.getValueFromNanos(ms * 1_000_000)
            assertTrue(abs(breathAt(ms) - expected) <= 1e-4f, "at $ms ms: ${breathAt(ms)} against $expected")
            ms += 7
        }
    }

    @Test
    fun `the dot steps thirty times a second inside the frame and its arc follows the timer`() = runTest {
        val display = VirtualDisplay(testScheduler, VSYNC_120)
        val angle = mutableFloatStateOf(0f)
        val breath = mutableFloatStateOf(0f)
        var tick = 0L
        var first = -1L
        var last = -1L
        val noted = mutableListOf<Float>()
        display.onTime = { if (first < 0) first = it; last = it }
        display.afterFrame = { noted += angle.floatValue }
        backgroundScope.launch(display) { runDotClock(tick = { tick }, angle = angle, breath = breath) }
        advanceTimeBy(SECOND_MS)
        testScheduler.runCurrent()
        assertTrue(noted.size in 29..31, "${noted.size} frames a second")
        assertTrue(display.asked <= noted.size + 1, "${display.asked} frames asked")
        assertTrue(noted.drop(1).zipWithNext().all { (a, b) -> b > a }, "the arc goes on every frame: $noted")
        assertEquals(noted.last(), angle.floatValue, "written inside the frame")
        assertEquals(breathAt((last - first) / 1_000_000), breath.floatValue)

        // a tick of the timer: the arc stands where the timer says, and is carried on from there
        tick = 30_000
        advanceTimeBy(100)
        assertTrue(angle.floatValue in 180f..181f, "${angle.floatValue}")
    }

    @Test
    fun `off the screen the dot asks for no frames and stands right when it comes back`() = runTest {
        val display = VirtualDisplay(testScheduler, VSYNC_120)
        val angle = mutableFloatStateOf(0f)
        val breath = mutableFloatStateOf(0f)
        val seen = mutableStateOf(true)
        var tick = 0L
        backgroundScope.launch(display) { runDotClock(tick = { tick }, angle = angle, breath = breath, seen = { seen.value }) }
        advanceTimeBy(SECOND_MS)
        seen.value = false
        Snapshot.sendApplyNotifications()
        advanceTimeBy(100) // the step that was already on its way
        val asked = display.asked
        advanceTimeBy(20 * SECOND_MS)
        assertEquals(asked, display.asked, "frames asked off the screen")

        // twenty seconds on, the timer says so, and the arc is there at the first step back
        tick = 21_000
        seen.value = true
        Snapshot.sendApplyNotifications()
        advanceTimeBy(SECOND_MS)
        testScheduler.runCurrent()
        val steps = display.asked - asked
        assertTrue(steps in 29..32, "$steps frames a second after coming back")
        assertTrue(angle.floatValue in 126f..132f, "${angle.floatValue}")
    }
}
