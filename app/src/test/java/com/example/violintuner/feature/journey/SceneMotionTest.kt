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
        assertEquals(SceneCamera.MAX_ZOOM, SceneCamera.zoom(2f, 3f), 0f)
        assertEquals(SceneCamera.MIN_ZOOM, SceneCamera.zoom(1.2f, 0.1f), 0f)
    }
}
