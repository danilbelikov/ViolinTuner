package com.violinjourney.app.feature.repertoire.components

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.MotionDurationScale
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

/** The red dot of a running take (spec 3.15) pulses on the postcards' frames, the same curve as its infinite transition. */
@OptIn(ExperimentalCoroutinesApi::class)
class RecordingPulseTest {
    @Test
    fun `the pulse is the curve of the infinite transition it replaced`() {
        val transition = TargetBasedAnimation(
            animationSpec = InfiniteRepeatableSpec(tween<Float>(PERIOD_MS / 2), RepeatMode.Reverse),
            typeConverter = Float.VectorConverter,
            initialValue = 1f,
            targetValue = MIN_ALPHA,
        )
        var ms = 0L
        while (ms <= 3L * PERIOD_MS) {
            val expected = transition.getValueFromNanos(ms * 1_000_000)
            assertTrue(abs(pulseAt(ms, PERIOD_MS, MIN_ALPHA) - expected) <= 1e-4f, "at $ms ms: ${pulseAt(ms, PERIOD_MS, MIN_ALPHA)} against $expected")
            ms += 7
        }
    }

    @Test
    fun `the dot steps thirty times a second inside the frame`() = runTest {
        val display = VirtualDisplay(testScheduler, VSYNC_120)
        val alpha = mutableFloatStateOf(1f)
        val noted = mutableListOf<Float>()
        display.afterFrame = { noted += alpha.floatValue }
        backgroundScope.launch(display) { runPulse(alpha, PERIOD_MS, MIN_ALPHA) }
        advanceTimeBy(SECOND_MS)
        testScheduler.runCurrent()
        assertTrue(noted.size in 29..31, "${noted.size} frames a second")
        assertTrue(display.asked <= noted.size + 1, "${display.asked} frames asked")
        assertEquals(noted.last(), alpha.floatValue, "written inside the frame")
        assertTrue(noted.minOrNull()!! < 0.5f, "it went down: $noted")
    }

    @Test
    fun `with animations off the dot stands dim and asks for no frame`() = runTest {
        val alpha = mutableFloatStateOf(1f)
        // no frame clock here: asking for a frame would throw
        launch(NoMotion) { runPulse(alpha, PERIOD_MS, MIN_ALPHA) }.join()
        assertEquals(MIN_ALPHA, alpha.floatValue)
    }

    private object NoMotion : MotionDurationScale {
        override val scaleFactor = 0f
    }

    private companion object {
        const val PERIOD_MS = 1_200
        const val MIN_ALPHA = 0.35f
    }
}
