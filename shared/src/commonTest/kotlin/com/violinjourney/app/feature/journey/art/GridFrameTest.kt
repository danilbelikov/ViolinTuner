package com.violinjourney.app.feature.journey.art

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest

/**
 * The clocks of the living pictures sleep between the cells of their grid and take one frame a cell
 * (docs/plan-performance.md, «На iOS»): on iOS a frame that anybody waits for is a full draw of the
 * window. Run on a display of virtual time, where `delay` and the frames share one clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GridFrameTest {
    @Test
    fun `the time to the next cell is a whole number of milliseconds from one to a cell`() {
        val cell = SceneMotion.FRAME_NANOS
        val start = 1_000 * cell
        assertEquals(33L, SceneMotion.millisToNextFrame(start))
        assertEquals(33L, SceneMotion.millisToNextFrame(start + 1))
        assertEquals(1L, SceneMotion.millisToNextFrame(start + cell - 500_000))
        assertEquals(1L, SceneMotion.millisToNextFrame(start + cell - 1))
        for (offset in 0L until cell step 777_777L) {
            val wait = SceneMotion.millisToNextFrame(start + offset)
            assertTrue(wait in 1L..33L, "$offset: $wait")
            // the wait reaches the next cell and no further than a millisecond into it
            val woken = start + offset + wait * NANOS_PER_MILLI
            assertTrue(SceneMotion.frameDue(woken, start + offset) && woken - (start + cell) < NANOS_PER_MILLI, "$offset")
        }
        assertEquals(66L, SceneMotion.millisToNextFrame(1_000 * SceneMotion.LIVE_FRAME_NANOS, SceneMotion.LIVE_FRAME_NANOS))
    }

    @Test
    fun `the picture behind Live keeps every other cell of the postcard grid`() {
        assertEquals(0L, SceneMotion.LIVE_FRAME_NANOS % SceneMotion.FRAME_NANOS)
    }

    @Test
    fun `a clock on the grid changes once a cell and asks for no more frames than it shows — 120 Hz`() = runTest {
        oneCellOneFrame(VirtualDisplay(testScheduler, VSYNC_120))
    }

    @Test
    fun `a clock on the grid changes once a cell and asks for no more frames than it shows — 60 Hz`() = runTest {
        oneCellOneFrame(VirtualDisplay(testScheduler, VSYNC_60))
    }

    @Test
    fun `a clock on the grid changes once a cell when frame times lead by a vsync as on iOS`() = runTest {
        oneCellOneFrame(VirtualDisplay(testScheduler, VSYNC_120, leadNanos = VSYNC_120))
    }

    private fun TestScope.oneCellOneFrame(display: VirtualDisplay) {
        val changes = gridClock(display)
        advanceTimeBy(SECOND_MS)
        testScheduler.runCurrent()
        assertTrue(changes.size in 29..31, "${changes.size} changes a second")
        // the clock that waited for every frame asked 120 (60) a second
        assertTrue(display.asked <= changes.size + 1, "${display.asked} frames asked for ${changes.size} changes")
        val cells = changes.map { it.floorDiv(SceneMotion.FRAME_NANOS) }
        assertTrue(cells.zipWithNext().all { (a, b) -> b == a + 1 }, "every cell once, none skipped: $cells")
    }

    @Test
    fun `two clocks started apart change on the same frames`() = runTest {
        val display = VirtualDisplay(testScheduler, VSYNC_120)
        val postcard = gridClock(display)
        advanceTimeBy(50)
        val button = gridClock(display)
        advanceTimeBy(SECOND_MS)
        testScheduler.runCurrent()
        // the later one's first step may come in the middle of a cell; from then on they share the frames
        val later = button.drop(1)
        assertTrue(later.size >= 28, "${later.size}")
        assertEquals(postcard.filter { it >= later.first() }, later)
    }

    @Test
    fun `a stopped scene clock still ticks on every cell — the frozen seconds of a debug build`() = runTest {
        val display = VirtualDisplay(testScheduler, VSYNC_120)
        val clock = SceneClock()
        val noted = mutableListOf<Float>()
        display.afterFrame = { noted += clock.value }
        backgroundScope.launch(display) { runSceneClock(clock, frozen = 12.5f) }
        advanceTimeBy(SECOND_MS)
        testScheduler.runCurrent()
        assertTrue(noted.size in 29..31, "${noted.size}")
        noted.forEachIndexed { i, value -> assertEquals(if (i % 2 == 0) 12.5f else 12.5f + FROZEN_TICK, value, "frame $i") }
        assertEquals(noted.last(), clock.value, "written inside the frame")
    }

    @Test
    fun `a scene clock off the screen asks for no frames and counts from its first frame when back`() = runTest {
        val display = VirtualDisplay(testScheduler, VSYNC_120)
        val clock = SceneClock()
        var first = -1L
        var last = -1L
        val noted = mutableListOf<Float>()
        display.onTime = { if (first < 0) first = it; last = it }
        display.afterFrame = { noted += clock.value }
        backgroundScope.launch(display) { runSceneClock(clock, frozen = null) }
        advanceTimeBy(SECOND_MS)
        assertEquals(noted.last(), clock.value, "written inside the frame")
        assertEquals((last - first) / 1e9f, clock.value, 1e-4f)

        clock.seen = false
        Snapshot.sendApplyNotifications()
        advanceTimeBy(100) // the step that was already on its way
        val asked = display.asked
        val standing = clock.value
        advanceTimeBy(SECOND_MS)
        assertEquals(asked, display.asked, "frames asked off the screen")
        assertEquals(standing, clock.value)

        clock.seen = true
        Snapshot.sendApplyNotifications()
        advanceTimeBy(SECOND_MS)
        testScheduler.runCurrent()
        assertTrue(display.asked - asked in 29..32, "${display.asked - asked} frames on return")
        assertEquals((last - first) / 1e9f, clock.value, 1e-4f)
        assertTrue(clock.value > 2f, "the time off the screen is in the scene's: ${clock.value}")
    }

    @Test
    fun `the picture behind Live steps fifteen times a second and not at all in the dark`() = runTest {
        val display = VirtualDisplay(testScheduler, VSYNC_120)
        val seconds = mutableFloatStateOf(0f)
        val lit = mutableStateOf(true)
        val noted = mutableListOf<Float>()
        display.afterFrame = { noted += seconds.floatValue }
        backgroundScope.launch(display) { runPausableSceneClock(seconds, SceneMotion.LIVE_FRAME_NANOS) { lit.value } }
        advanceTimeBy(SECOND_MS)
        testScheduler.runCurrent()
        assertTrue(noted.size in 14..16, "${noted.size} frames a second")
        assertTrue(display.asked <= noted.size + 1, "${display.asked} asked")
        assertEquals(noted.last(), seconds.floatValue, "written inside the frame")
        assertTrue(seconds.floatValue in 0.8f..1f, "${seconds.floatValue}")

        lit.value = false
        Snapshot.sendApplyNotifications()
        advanceTimeBy(200)
        val asked = display.asked
        val dark = seconds.floatValue
        advanceTimeBy(5 * SECOND_MS)
        assertEquals(asked, display.asked, "frames asked in the dark")
        assertEquals(dark, seconds.floatValue)

        lit.value = true
        Snapshot.sendApplyNotifications()
        advanceTimeBy(SECOND_MS)
        testScheduler.runCurrent()
        val gained = seconds.floatValue - dark
        assertTrue(gained in 0.75f..1f, "goes on from where it stood, the dark left out: $gained")
    }

    private fun TestScope.gridClock(display: VirtualDisplay): List<Long> {
        val changes = mutableListOf<Long>()
        backgroundScope.launch(display) {
            var shown = 0L
            while (true) shown = awaitGridFrame(shown) { changes += it }
        }
        return changes
    }
}
