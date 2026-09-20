package com.example.violintuner.feature.journey

import com.example.violintuner.core.domain.journey.JourneyRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyMapMathTest {
    private val points = JourneyMapMath.points()

    @Test
    fun everyLegStartsAndEndsOnItsStops_andStaysOnTheMap() {
        for (index in 0 until points.lastIndex) {
            val leg = JourneyMapMath.leg(index, points)
            assertEquals(points[index], JourneyMapMath.at(leg, 0f))
            val end = JourneyMapMath.at(leg, 1f)
            assertEquals(points[index + 1].x, end.x, 0.001f)
            assertEquals(points[index + 1].y, end.y, 0.001f)
            for (step in 0..10) {
                val at = JourneyMapMath.at(leg, step / 10f)
                assertTrue("leg $index leaves the map at $at", at.x in -20f..JourneyMapMath.WIDTH + 20f && at.y in -20f..JourneyMapMath.HEIGHT + 20f)
            }
        }
    }

    @Test
    fun neighbouringLegsMeetWithoutAKink() {
        for (index in 1 until points.lastIndex) {
            val before = JourneyMapMath.leg(index - 1, points)
            val after = JourneyMapMath.leg(index, points)
            // the tangent of a Catmull-Rom curve is the same on both sides of a stop
            assertEquals(before.to.x - before.c2.x, after.c1.x - after.from.x, 0.001f)
            assertEquals(before.to.y - before.c2.y, after.c1.y - after.from.y, 0.001f)
        }
    }

    @Test
    fun aTapFindsOnlyAStopAlreadyReached() {
        val vienna = JourneyRoute.indexOf("vienna")
        val at = points[vienna]
        assertEquals(vienna, JourneyMapMath.stopAt(at.x + 3f, at.y - 3f, reached = vienna))
        // Vienna is ahead: the nearest reached stop is too far to be meant
        assertNull(JourneyMapMath.stopAt(at.x, at.y, reached = 1)?.takeIf { it == vienna })
        assertNull(JourneyMapMath.stopAt(-100f, -100f, reached = points.lastIndex))
    }

    @Test
    fun theTrainLooksAlongTheRoad() {
        val leg = JourneyMapMath.leg(0, points)
        val heading = JourneyMapMath.headingAt(leg, 0.5f)
        val a = JourneyMapMath.at(leg, 0.4f)
        val b = JourneyMapMath.at(leg, 0.6f)
        val expected = Math.toDegrees(kotlin.math.atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())).toFloat()
        assertEquals(expected, heading, 10f)
    }

    @Test
    fun theMapThatFitsDoesNotMove_theZoomedOneMovesByItsOverhang() {
        val still = JourneyMapMath.clampPan(300f, -300f, k = 1f, width = 412f, height = 800f)
        assertEquals(0f, still.x, 0f)
        assertEquals(0f, still.y, 0f)
        val zoomed = JourneyMapMath.clampPan(1_000f, -1_000f, k = 2f, width = 412f, height = 700f)
        assertEquals(JourneyMapMath.Point(206f, -350f), zoomed)
    }
}
