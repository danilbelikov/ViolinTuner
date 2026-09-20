package com.example.violintuner.feature.journey

import com.example.violintuner.feature.journey.art.SceneCamera
import com.example.violintuner.feature.journey.art.SceneGrid
import com.example.violintuner.feature.journey.art.SceneLayer
import com.example.violintuner.feature.journey.art.SceneMode
import com.example.violintuner.feature.journey.art.SceneMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneMotionTest {
    private fun layer(fill: String, warmGlow: Boolean = false) = SceneLayer(fill, 1, 1f, 0f, 0f, 1f, warmGlow, null, 0f, false, "M0 0Z")

    @Test
    fun onlyLightAndWaterMove_andLightOnlyInTheEvening() {
        assertTrue(SceneMotion.moves(layer("window"), SceneMode.EVENING))
        assertTrue(SceneMotion.moves(layer(SceneLayer.GLOW), SceneMode.EVENING))
        assertTrue(SceneMotion.moves(layer("rgba(255,196,110,.5)", warmGlow = true), SceneMode.EVENING))
        assertTrue(SceneMotion.moves(layer("waterLit"), SceneMode.DAY))
        assertFalse(SceneMotion.moves(layer("window"), SceneMode.DAY))
        assertFalse(SceneMotion.moves(layer("wall"), SceneMode.EVENING))
        assertFalse(SceneMotion.moves(layer("hero"), SceneMode.EVENING))
    }

    @Test
    fun aWindowWaversALittleAndNeverGoesOut() {
        val window = layer("window")
        val alphas = (0..400).map { SceneMotion.alpha(window, index = 7, SceneMode.EVENING, it / 10f) }
        assertTrue(alphas.all { it in (1f - SceneMotion.WINDOW_DIP - 0.001f)..1.001f })
        assertTrue(alphas.max() - alphas.min() > SceneMotion.WINDOW_DIP * 0.9f)
        assertTrue(alphas.min() > 0.5f)
        // neighbours do not blink together
        assertTrue((0..40).any { SceneMotion.alpha(window, 7, SceneMode.EVENING, it / 2f) != SceneMotion.alpha(window, 8, SceneMode.EVENING, it / 2f) })
        assertEquals(1f, SceneMotion.alpha(window, 7, SceneMode.DAY, 3f), 0f)
        assertEquals(1f, SceneMotion.alpha(layer("wall"), 7, SceneMode.EVENING, 3f), 0f)
    }

    @Test
    fun onlyTheGlintsOnTheWaterDrift() {
        val drifts = (0..200).map { SceneMotion.drift(layer("waterLit"), 3, it / 10f) }
        assertTrue(drifts.all { it in -SceneMotion.WATER_DRIFT..SceneMotion.WATER_DRIFT })
        assertTrue(drifts.max() > SceneMotion.WATER_DRIFT * 0.9f)
        assertEquals(0f, SceneMotion.drift(layer("water"), 3, 4f), 0f)
    }

    @Test
    fun upright_thePictureIsWiderThanTheScreen_andThePanStopsAtItsEdges() {
        val width = 1080f
        val height = 2340f
        val k = SceneCamera.cover(width, height)
        assertEquals(height / SceneGrid.HEIGHT, k, 0.001f)
        val over = (SceneGrid.WIDTH * k - width) / 2
        val right = SceneCamera.clamp(99_999f, 500f, 1f, width, height)
        val left = SceneCamera.clamp(-99_999f, -500f, 1f, width, height)
        assertEquals(over, right.first, 0.001f)
        assertEquals(-over, left.first, 0.001f)
        // upright the picture is exactly as tall as the screen: no looking up or down
        assertEquals(0f, right.second, 0f)
        assertEquals(0f, left.second, 0f)
        // closer — and there is somewhere to look up and down as well
        assertTrue(SceneCamera.clamp(0f, 99_999f, 2f, width, height).second > 0f)
    }

    @Test
    fun noPlaneEverShowsItsEdge() {
        val width = 1080f
        val height = 2340f
        for (zoom in listOf(1f, 1.7f, SceneCamera.MAX_ZOOM)) {
            val k = SceneCamera.cover(width, height) * zoom
            val (pan, _) = SceneCamera.clamp(99_999f, 0f, zoom, width, height)
            for (sign in listOf(-1f, 1f)) for (depth in 0..2) {
                val left = (width - SceneGrid.WIDTH * k) / 2 + SceneCamera.shift(sign * pan, depth)
                assertTrue("depth $depth shows its left edge", left <= 0.5f)
                assertTrue("depth $depth shows its right edge", left + SceneGrid.WIDTH * k >= width - 0.5f)
            }
        }
        // the far plane lags: that is the parallax
        assertTrue(SceneCamera.shift(100f, 0) < SceneCamera.shift(100f, 1))
        assertEquals(100f, SceneCamera.shift(100f, 2), 0f)
    }

    @Test
    fun theZoomStaysWithinItsLimits() {
        assertEquals(SceneCamera.MAX_ZOOM, SceneCamera.zoom(2f, 3f, 1080f, 2340f), 0f)
        // upright the way out ends where the whole card is seen by its width
        val whole = SceneCamera.wholeZoom(1080f, 2340f)
        assertEquals((1080f / SceneGrid.WIDTH) / (2340f / SceneGrid.HEIGHT), whole, 0.0001f)
        assertEquals(whole, SceneCamera.zoom(1.2f, 0.01f, 1080f, 2340f), 0f)
        // on its side the card already fits: there is no further out than covering
        assertEquals(1f, SceneCamera.wholeZoom(2340f, 1080f), 0f)
        assertEquals(1f, SceneCamera.zoom(1.2f, 0.01f, 2340f, 1080f), 0f)
    }

    @Test
    fun aDoubleTapWalksRound_coveringCloserWholeCovering() {
        val w = 1080f
        val h = 2340f
        val closer = SceneCamera.nextZoom(1f, w, h)
        assertEquals(SceneCamera.DOUBLE_TAP_ZOOM, closer, 0f)
        val whole = SceneCamera.nextZoom(closer, w, h)
        assertEquals(SceneCamera.wholeZoom(w, h), whole, 0f)
        assertEquals(1f, SceneCamera.nextZoom(whole, w, h), 0f)
        // on its side there is no whole to go to: closer and back
        assertEquals(1f, SceneCamera.nextZoom(2f, h, w), 0f)
    }

    @Test
    fun theWholeCardStandsOnTheBottomUnderItsSky_aRoomIsCentred() {
        val w = 1080f
        val h = 2340f
        val whole = SceneCamera.wholeZoom(w, h)
        val tall = SceneGrid.HEIGHT * SceneCamera.cover(w, h) * whole
        assertEquals(h - tall, SceneCamera.top(whole, w, h, outdoors = true), 0.01f)
        assertEquals((h - tall) / 2, SceneCamera.top(whole, w, h, outdoors = false), 0.01f)
        // covering, both stand the same: nothing jumps on the way through 1×
        assertEquals(SceneCamera.top(1f, w, h, outdoors = false), SceneCamera.top(1f, w, h, outdoors = true), 0.01f)
        // and the whole card cannot be dragged anywhere
        val (x, y) = SceneCamera.clamp(500f, 500f, whole, w, h)
        assertEquals(0f, x, 0f)
        assertEquals(0f, y, 0f)
    }

    @Test
    fun theSkyIsTheSameSkyEveryTime_starsTwinkleCloudsSail() {
        assertEquals(SceneMotion.star(5, 0f).x, SceneMotion.star(5, 40f).x, 0f)
        assertEquals(SceneMotion.star(5, 0f).y, SceneMotion.star(5, 40f).y, 0f)
        val alphas = (0..100).map { SceneMotion.star(5, it / 10f).alpha }
        assertTrue(alphas.all { it in 0f..1f })
        assertTrue(alphas.max() > alphas.min())
        assertTrue((0 until SceneMotion.STARS).map { SceneMotion.star(it, 0f).x.toInt() }.toSet().size > SceneMotion.STARS / 2)
        assertTrue(SceneMotion.cloud(1, 3f).x != SceneMotion.cloud(1, 0f).x)
        assertEquals(SceneMotion.cloud(1, 0f).y, SceneMotion.cloud(1, 30f).y, 0f)
    }

    @Test
    fun aBirdCrossesTheWindowAndFadesAtItsEnds_itNeverLeavesTheFrame() {
        val bird = layer(SceneMotion.BIRD)
        assertTrue(SceneMotion.moves(bird, SceneMode.DAY))
        assertTrue(SceneMotion.moves(bird, SceneMode.EVENING))
        val right = SceneMotion.BIRD_LEFT + SceneMotion.BIRD_SPAN
        for (base in listOf(268f, 306f, 338f)) for (step in 0..600) {
            val flight = SceneMotion.flight(base, index = 9, seconds = step / 10f)
            val x = base + flight.dx
            assertTrue("a bird at $x is outside the window", x in SceneMotion.BIRD_LEFT..right)
            assertTrue(flight.alpha in 0f..1f)
            assertTrue(flight.flap in 0.3f..1.01f)
            assertTrue(kotlin.math.abs(flight.dy) <= SceneMotion.BIRD_BOB + 0.001f)
            // by the frame it is gone: nothing has to cut it
            if (x - SceneMotion.BIRD_LEFT < 1f || right - x < 1f) assertTrue(flight.alpha < 0.1f)
        }
        // when nothing has moved yet the bird is where it is drawn
        assertEquals(0f, SceneMotion.flight(306f, 9, 0f).dx, 0.001f)
        // and it does fly: to the right
        assertTrue(SceneMotion.flight(268f, 9, 1f).dx > SceneMotion.flight(268f, 9, 0f).dx)
    }
}
