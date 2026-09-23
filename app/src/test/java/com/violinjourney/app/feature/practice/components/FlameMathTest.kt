package com.violinjourney.app.feature.practice.components

import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlameMathTest {
    private val eps = 1e-4f

    @Test
    fun `a streak earns its flame at three days, the full one at seven, the hot core at thirty`() {
        val stages = listOf(0, 2, 3, 6, 7, 29, 30, 365).map(FlameMath::stageOf)
        assertEquals(
            listOf(
                FlameMath.Stage.NONE, FlameMath.Stage.NONE, FlameMath.Stage.SMALL, FlameMath.Stage.SMALL,
                FlameMath.Stage.FULL, FlameMath.Stage.FULL, FlameMath.Stage.HOT, FlameMath.Stage.HOT,
            ),
            stages,
        )
    }

    @Test
    fun `a cycle starts and ends at rest`() {
        assertEquals(FlameMath.Pose.REST, FlameMath.poseAt(0f, 1f))
        assertEquals(FlameMath.Pose.REST, FlameMath.poseAt(1f, 1f))
    }

    @Test
    fun `the tongue leans left and taller, then right and shorter, the core breathes against it`() {
        val left = FlameMath.poseAt(0.3f, 1f)
        assertEquals(-3f, left.tongueDegrees, eps)
        assertEquals(1.05f, left.tongueScaleY, eps)
        assertEquals(0.94f, left.coreScaleY, eps)
        val right = FlameMath.poseAt(0.6f, 1f)
        assertEquals(2.5f, right.tongueDegrees, eps)
        assertEquals(0.97f, right.tongueScaleY, eps)
        assertEquals(1.06f, right.coreScaleY, eps)
    }

    @Test
    fun `the sway never leaves the bounds of the handoff`() {
        for (i in 0..1_000) {
            val pose = FlameMath.poseAt(i / 1_000f, 1f)
            assertTrue(pose.tongueDegrees in -3.001f..2.501f)
            assertTrue(pose.tongueScaleY in 0.9699f..1.0501f)
            assertTrue(pose.coreScaleY in 0.9399f..1.0601f)
        }
    }

    @Test
    fun `no amplitude is rest, half of it is half the lean`() {
        assertEquals(FlameMath.Pose.REST, FlameMath.poseAt(0.3f, 0f))
        assertEquals(-1.5f, FlameMath.poseAt(0.3f, 0.5f).tongueDegrees, eps)
    }

    @Test
    fun `the flame lives six seconds and comes to rest softly`() {
        assertEquals(1f, FlameMath.amplitudeAt(0), eps)
        assertEquals(1f, FlameMath.amplitudeAt(6_000), eps)
        assertEquals(0.5f, FlameMath.amplitudeAt(6_300), eps)
        assertEquals(0f, FlameMath.amplitudeAt(6_600), eps)
        assertEquals(0f, FlameMath.amplitudeAt(60_000), eps)
        var last = 1f
        for (t in 6_000L..6_600L step 10) {
            val amplitude = FlameMath.amplitudeAt(t)
            assertTrue("never back up", amplitude <= last + eps)
            last = amplitude
        }
        assertEquals(6_600L, FlameMath.swayTotalMs)
    }

    @Test
    fun `the phase wraps with the cycle`() {
        assertEquals(0f, FlameMath.phaseAt(0), eps)
        assertEquals(0.5f, FlameMath.phaseAt(1_300), eps)
        assertEquals(0.5f, FlameMath.phaseAt(2_600 + 1_300), eps)
    }

    @Test
    fun `a flare goes up fast, comes back slowly and leaves nothing behind`() {
        assertEquals(1f, FlameMath.flareScaleAt(-50), eps)
        assertEquals(1f, FlameMath.flareScaleAt(0), eps)
        assertEquals(1.35f, FlameMath.flareScaleAt(200), eps)
        assertEquals(1.175f, FlameMath.flareScaleAt(400), eps)
        assertEquals(1f, FlameMath.flareScaleAt(600), eps)
        assertEquals(1f, FlameMath.flareScaleAt(5_000), eps)
        assertTrue("ease out: most of the way up by the middle", FlameMath.flareScaleAt(100) > 1.25f)
    }

    @Test
    fun `a new flame shows up over its two hundred milliseconds`() {
        assertEquals(0f, FlameMath.appearAlphaAt(0), eps)
        assertEquals(0.5f, FlameMath.appearAlphaAt(100), eps)
        assertEquals(1f, FlameMath.appearAlphaAt(200), eps)
        assertEquals(1f, FlameMath.appearAlphaAt(900), eps)
    }

    private class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float)

    // android.graphics.Path is not there on the JVM: the box of the control points is a box of the curve too.
    private fun boundsOf(d: String): Bounds {
        val xs = mutableListOf<Float>()
        val ys = mutableListOf<Float>()
        PathParser().parsePathString(d).toNodes().forEach { node ->
            when (node) {
                is PathNode.MoveTo -> { xs += node.x; ys += node.y }
                is PathNode.CurveTo -> { xs += listOf(node.x1, node.x2, node.x3); ys += listOf(node.y1, node.y2, node.y3) }
                PathNode.Close -> Unit
                else -> error("the flame is drawn with M, C and Z only: $node")
            }
        }
        return Bounds(xs.min(), ys.min(), xs.max(), ys.max())
    }

    @Test
    fun `both layers parse and stay inside the grid, the core inside the tongue`() {
        val outer = boundsOf(FlameArt.Outer.d)
        val core = boundsOf(FlameArt.Core.d)
        listOf(outer, core).forEach { bounds ->
            assertTrue(bounds.left >= 0f && bounds.top >= 0f && bounds.right <= FlameArt.GRID && bounds.bottom <= FlameArt.GRID)
        }
        assertTrue(core.left > outer.left && core.right < outer.right && core.top > outer.top && core.bottom <= outer.bottom)
        // they move about their bases
        assertEquals(outer.bottom, FlameArt.Outer.pivotY, eps)
        assertEquals(core.bottom, FlameArt.Core.pivotY, eps)
    }
}
