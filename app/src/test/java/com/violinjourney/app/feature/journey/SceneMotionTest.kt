package com.violinjourney.app.feature.journey

import com.violinjourney.app.feature.journey.art.HighSky
import com.violinjourney.app.feature.journey.art.SceneAnim
import com.violinjourney.app.feature.journey.art.SceneCamera
import com.violinjourney.app.feature.journey.art.SceneFrame
import com.violinjourney.app.feature.journey.art.SceneGrid
import com.violinjourney.app.feature.journey.art.SceneLayer
import com.violinjourney.app.feature.journey.art.SceneMode
import com.violinjourney.app.feature.journey.art.SceneMotion
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
    fun aDashAloneIsNoLife_everyOtherMovementIs() {
        assertFalse(SceneAnim.parse("dash:4:3")!!.lives)
        assertFalse(SceneMotion.moves(layer("trim").copy(anim = SceneAnim.parse("dash:4:3")), SceneMode.EVENING))
        for (text in listOf("flick:4", "glint:3", "flash:1.4", "blink:5", "ride:16:-192:516", "peck:3:-14:1:1", "rise:5:0:30", "swing:9:2.4:93:-140", "fall:7:122:126")) {
            assertTrue(text, SceneAnim.parse(text)!!.lives)
        }
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

    // the full screen of a stop within its frame (spec 3.23): screens in pixels — tall, small, lying down, a tablet
    private val boxes = listOf(1080f to 2400f, 720f to 1280f, 2400f to 1080f, 1600f to 2560f)
    private val tall = SceneFrame(-420f, 600f)
    private val stage = SceneFrame(-240f, 480f)

    /** What the box shows at [zoom] and pan, in units of the grid: left, top, right, bottom of the near plane. */
    private fun seen(frame: SceneFrame, zoom: Float, panX: Float, panY: Float, w: Float, h: Float): List<Float> {
        val k = SceneCamera.cover(w, h) * zoom
        val left = (w - SceneGrid.WIDTH * k) / 2 + panX
        val top = frame.originY(zoom, w, h) + panY
        return listOf(-left / k, -top / k, (w - left) / k, (h - top) / k)
    }

    @Test
    fun aCardAloneOpensAsItDid_byItsHeight_andThereIsNoWayOutIntoTheDark() {
        val frame = SceneFrame.CARD
        for ((w, h) in boxes) {
            assertEquals(SceneCamera.COVER_ZOOM, frame.openZoom(w, h), 0.0001f)
            assertEquals(SceneCamera.COVER_ZOOM, frame.zoom(1f, 0.01f, w, h), 0.0001f)
            // where it stands is where the card always stood
            assertEquals(SceneCamera.top(1f, w, h, outdoors = true), frame.originY(1f, w, h), 0.01f)
        }
        // covering, closer, and back where it opened — there is no whole card under an empty sky any more
        val closer = frame.nextZoom(1f, 1080f, 2400f)
        assertEquals(SceneCamera.DOUBLE_TAP_ZOOM, closer, 0f)
        assertEquals(1f, frame.nextZoom(closer, 1080f, 2400f), 0f)
        assertFalse(frame.beyondTheCard)
    }

    @Test
    fun aPlaceDrawnAsTallAsTheRoomsOfTheHomeOpensWholeByItsWidth_theCardInTheMiddle() {
        val (w, h) = 1080f to 2400f
        val open = tall.openZoom(w, h)
        assertEquals(SceneCamera.wholeZoom(w, h), open, 0.0001f)
        val (left, top, right, bottom) = seen(tall, open, 0f, 0f, w, h)
        assertEquals(0f, left, 0.01f)
        assertEquals(SceneGrid.WIDTH, right, 0.01f)
        // the card's band is in the middle of the screen: as much drawn above it as below
        assertEquals(SceneGrid.HEIGHT / 2, (top + bottom) / 2, 0.01f)
        assertTrue(top >= tall.top && bottom <= tall.bottom)
        assertTrue(tall.beyondTheCard)
        // lying down it opens as the card did: by the width, which is the card's own cover there
        assertEquals(SceneCamera.COVER_ZOOM, tall.openZoom(h, w), 0.0001f)
    }

    @Test
    fun theFullScreenNeverShowsWhatIsNotDrawn_atAnyZoomAndAnyPan() {
        for (frame in listOf(SceneFrame.CARD, tall, stage)) for ((w, h) in boxes) {
            for (zoom in listOf(0f, frame.openZoom(w, h), 1f, 1.7f, SceneCamera.MAX_ZOOM, 9f)) {
                val z = frame.zoom(zoom, 1f, w, h)
                for (panX in listOf(-99_999f, 0f, 99_999f)) for (panY in listOf(-99_999f, 0f, 99_999f)) {
                    val (x, y) = frame.clamp(panX, panY, z, w, h)
                    val (left, top, right, bottom) = seen(frame, z, x, y, w, h)
                    val where = "$frame on ${w.toInt()}×${h.toInt()} at $z"
                    assertTrue("$where shows left of the drawing", left >= -0.01f)
                    assertTrue("$where shows right of the drawing", right <= SceneGrid.WIDTH + 0.01f)
                    assertTrue("$where shows above the drawing", top >= frame.top - 0.01f)
                    assertTrue("$where shows below the drawing", bottom <= frame.bottom + 0.01f)
                }
            }
        }
    }

    @Test
    fun aDoubleTapWalksRound_whereItOpened_theCardByItsHeight_closer() {
        val (w, h) = 1080f to 2400f
        val open = tall.openZoom(w, h)
        // 0 is the view it opened at: the size of the box was not known yet
        assertEquals(SceneCamera.COVER_ZOOM, tall.nextZoom(0f, w, h), 0f)
        assertEquals(SceneCamera.COVER_ZOOM, tall.nextZoom(open, w, h), 0f)
        assertEquals(SceneCamera.DOUBLE_TAP_ZOOM, tall.nextZoom(1f, w, h), 0f)
        assertEquals(open, tall.nextZoom(2f, w, h), 0f)
        // a pinch from the view it opened at starts from there as well
        assertEquals(open * 1.5f, tall.zoom(0f, 1.5f, w, h), 0.0001f)
    }

    @Test
    fun aFrameTheBoxHasOutgrownIsCentred_notACrash() {
        // a zoom below the frame's own — the phone turned, the other view came in — until it is settled
        val (w, h) = 1080f to 2400f
        val origin = SceneFrame.CARD.originY(0.2f, w, h)
        val k = SceneCamera.cover(w, h) * 0.2f
        assertEquals((h - SceneGrid.HEIGHT * k) / 2, origin, 0.01f)
        assertEquals(0f to 0f, SceneFrame.CARD.clamp(500f, 500f, 0.2f, w, h))
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
    fun aPigeonStandsMostOfThePeriod_pecksHeadDownAboutItsFeet_andStepsAside() {
        val pigeon = SceneAnim.parse("peck:3:-14:120:430")!!
        assertTrue(pigeon.travels)
        // most of the period it only stands
        for (step in 0 until 17) assertEquals(SceneMotion.Moved(0f, 0f, 1f, 1f, 0f, 120f, 430f), SceneMotion.moved(pigeon, 118f, 424f, 5, step * 0.1f))
        // bent at 66 %: the head goes down — the pigeons look left, so the turn is against the clock
        val bent = SceneMotion.moved(pigeon, 118f, 424f, 5, 0.66f * 3f)
        assertEquals(-14f, bent.degrees, 0.01f)
        assertEquals(120f, bent.pivotX, 0f)
        assertEquals(430f, bent.pivotY, 0f)
        assertEquals(0f, bent.dx, 0f)
        // then a step aside and back
        val stepped = SceneMotion.moved(pigeon, 118f, 424f, 5, 0.82f * 3f)
        assertEquals(SceneMotion.PECK_STEP, stepped.dx, 0.01f)
        assertEquals(0f, stepped.degrees, 0f)
        assertEquals(0f, SceneMotion.moved(pigeon, 118f, 424f, 5, 2.999f).dx, 0.01f)
    }

    @Test
    fun aSparkInCrystalIsDim_andBrightForAMomentOfItsPeriod() {
        val spark = SceneAnim.parse("glint:3")!!
        assertFalse(spark.travels)
        val seen = (0..300).map { SceneMotion.moved(spark, 0f, 0f, 1, it / 100f).alpha }
        assertTrue(seen.all { it in SceneMotion.GLINT_DIM - 0.001f..1.001f })
        // dim nearly all the time, bright only about the end of the period
        assertTrue(seen.count { it > 0.9f } in 1..8)
        assertEquals(SceneMotion.GLINT_DIM, SceneMotion.moved(spark, 0f, 0f, 1, 1.5f).alpha, 0.001f)
        assertEquals(1f, SceneMotion.moved(spark, 0f, 0f, 1, 0.96f * 3f).alpha, 0.01f)
    }

    @Test
    fun aMoteGoesItsWayUp_showingItselfOnTheWayAndFadingAtTheTop_theSmokeIsAsItWas() {
        val mote = SceneAnim.parse("rise:5:0:30")!!
        assertEquals(0f, SceneMotion.moved(mote, 0f, 0f, 1, 0f).alpha, 0.001f)
        assertEquals(SceneMotion.MOTE_ALPHA, SceneMotion.moved(mote, 0f, 0f, 1, SceneMotion.MOTE_SHOWN * 5f).alpha, 0.001f)
        assertEquals(-15f, SceneMotion.moved(mote, 0f, 0f, 1, 2.5f).dy, 0.001f)
        assertTrue(SceneMotion.moved(mote, 0f, 0f, 1, 4.99f).alpha < 0.01f)
        // the smoke of the home keeps its ten units and its fade from 0,7
        val smoke = SceneAnim.parse("rise:3:1.2")!!
        assertEquals(0.7f, SceneMotion.moved(smoke, 0f, 0f, 1, 1.8f).alpha, 0.001f)
        assertEquals(-5f, SceneMotion.moved(smoke, 0f, 0f, 1, 0.3f).dy, 0.001f)
    }

    @Test
    fun theHighSkyIsTheSameSkyEveryTime_starsOverTheCardCloudsRideAndComeBack() {
        assertEquals(44, SceneMotion.HIGH_STARS)
        for (index in 0 until SceneMotion.HIGH_STARS) {
            val star = SceneMotion.highStar(index, 0f)
            assertEquals(star.x, SceneMotion.highStar(index, 40f).x, 0f)
            assertTrue("a star at ${star.y} is outside its band", star.y in -430f..40f)
            val alphas = (0..120).map { SceneMotion.highStar(index, it / 10f).alpha }
            assertTrue(alphas.max() > alphas.min())
            assertTrue(alphas.all { it in 0f..1f })
        }
        assertEquals(5, HighSky.dayClouds.size / SceneMotion.HIGH_CLOUD_NUMBERS)
        assertEquals(2, HighSky.eveningClouds.size / SceneMotion.HIGH_CLOUD_NUMBERS)
        val start = SceneMotion.highCloud(HighSky.dayClouds, 0, 0f)
        assertEquals(HighSky.dayClouds[0], start.x, 0.001f)
        assertTrue(SceneMotion.highCloud(HighSky.dayClouds, 0, 5f).x > start.x)
        // after the whole way it is where it began
        val way = (HighSky.dayClouds[6] - HighSky.dayClouds[5]) / HighSky.dayClouds[4]
        assertEquals(start.x, SceneMotion.highCloud(HighSky.dayClouds, 0, way).x, 0.05f)
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

    @Test
    fun twoLivingPicturesStartedOnDifferentFrames_changeOnTheSameFrames_thirtyTimesASecond() {
        val vsync = 16_666_667L
        val frames = (1_000L..1_240L).map { it * vsync }
        // the one starts a frame after the other: each keeps what it last showed
        fun changes(from: Int): List<Long> {
            var shown = 0L
            return frames.drop(from).filter { now -> SceneMotion.frameDue(now, shown).also { if (it) shown = now } }
        }
        val postcard = changes(from = 0).drop(1)
        val button = changes(from = 1).drop(1)
        assertEquals(postcard.filter { it >= button.first() }, button)
        val perSecond = postcard.size / ((frames.last() - frames.first()) / 1e9)
        assertTrue("$perSecond", perSecond in 29.0..31.0)
    }
}
