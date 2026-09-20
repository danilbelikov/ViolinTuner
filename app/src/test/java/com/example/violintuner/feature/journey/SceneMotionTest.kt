package com.example.violintuner.feature.journey

import com.example.violintuner.feature.journey.art.SceneAnim
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

    private fun moving(anim: String, fill: String = "tram") = layer(fill).copy(anim = SceneAnim.parse(anim))

    @Test
    fun aBirdCrossesItsWayAndFadesAtTheEnds_itNeverLeavesIt() {
        val bird = moving("bird:250:96", "bird")
        assertTrue(SceneMotion.moves(bird, SceneMode.DAY))
        assertTrue(SceneMotion.moves(bird, SceneMode.EVENING))
        for (base in listOf(268f, 306f, 338f)) for (step in 0..600) {
            val moved = SceneMotion.moved(bird.anim!!, base, 60f, index = 9, seconds = step / 10f)
            val x = base + moved.dx
            assertTrue("a bird at $x is outside the window", x in 250f..346f)
            assertTrue(moved.alpha in 0f..1f)
            assertTrue(moved.flap in 0.3f..1.01f)
            assertTrue(kotlin.math.abs(moved.dy) <= SceneMotion.BIRD_BOB + 0.001f)
            // by the frame it is gone: nothing has to cut it
            if (x - 250f < 1f || 346f - x < 1f) assertTrue(moved.alpha < 0.1f)
        }
        assertEquals(0f, SceneMotion.moved(bird.anim!!, 306f, 60f, 9, 0f).dx, 0.001f)
        assertTrue(SceneMotion.moved(bird.anim!!, 268f, 60f, 9, 1f).dx > 0f)
    }

    @Test
    fun allLayersOfOneTramGoAsOne_itLeavesTheFrameAndComesBackFromTheOtherSide() {
        val anim = SceneAnim.parse("ride:16:-192:516")!!
        // where it is drawn is where it stands when nothing moves
        assertEquals(0f, SceneMotion.moved(anim, 100f, 220f, 3, 0f).dx, 0.001f)
        for (step in 0..900) {
            val t = step / 5f
            val body = SceneMotion.moved(anim, 100f, 220f, index = 3, seconds = t)
            val wheel = SceneMotion.moved(anim, 60f, 240f, index = 57, seconds = t)
            assertEquals(body.dx, wheel.dx, 0f)
            assertTrue(body.dx in -192f..516f)
            assertEquals(1f, body.alpha, 0f)
        }
        // it does wrap: after the whole way it is where it began
        assertEquals(0f, SceneMotion.moved(anim, 100f, 220f, 3, (516f + 192f) / 16f).dx, 0.01f)
        // and it goes to the right
        assertTrue(SceneMotion.moved(anim, 100f, 220f, 3, 2f).dx > 0f)
    }

    @Test
    fun aBoatBobsWhileItSails_aLightBlinks_aPetalFallsFromWhereItIsDrawn() {
        val boat = SceneAnim.parse("ride:6:-150:436+bob:0.8:3.2")!!
        val heights = (0..64).map { SceneMotion.moved(boat, 90f, 222f, 1, it / 10f).dy }
        assertTrue(heights.max() > 0.7f && heights.min() < -0.7f)
        assertTrue(heights.all { it in -0.8f..0.8f })

        val light = SceneAnim.parse("ride:11:-156:564+blink:1.4")!!
        val seen = (0..140).map { SceneMotion.moved(light, 120f, 32f, 1, it / 100f).alpha }
        assertTrue(seen.any { it == 0f } && seen.any { it == 1f })

        val petal = SceneAnim.parse("fall:7:122:126")!!
        assertEquals(0f, SceneMotion.moved(petal, 40f, 180f, 5, 0f).dy, 0.001f)
        for (step in 0..400) {
            val moved = SceneMotion.moved(petal, 40f, 180f, 5, step / 4f)
            assertTrue("a petal at ${180f + moved.dy} is out of its fall", 180f + moved.dy in 122f..248f)
            assertTrue(moved.alpha in 0f..1f)
        }
    }

    @Test
    fun aMovementIsReadFromTheLayersLastField() {
        assertEquals(null, SceneAnim.parse(""))
        assertEquals(SceneAnim(ride = SceneAnim.Ride(16f, -192f, 516f)), SceneAnim.parse("ride:16:-192:516"))
        assertEquals(SceneAnim(bird = SceneAnim.Way(-30f, 472f)), SceneAnim.parse("bird:-30:472"))
        assertEquals(SceneAnim(fall = SceneAnim.Fall(7f, 122f, 126f)), SceneAnim.parse("fall:7:122:126"))
        val both = SceneAnim.parse("ride:6:-150:436+bob:0.8:3.2")!!
        assertEquals(SceneAnim.Bob(0.8f, 3.2f), both.bob)
        assertEquals(6f, both.ride!!.speed, 0f)
    }
}
