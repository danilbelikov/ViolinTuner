package com.example.violintuner.core.audio.playback

import android.graphics.ImageFormat
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.violintuner.testing.TestVideo
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The picture of a video take through a real decoder onto a real surface. The test video says
 * which frame it shows by its brightness, so "where are we" is read off the surface.
 */
@RunWith(AndroidJUnit4::class)
class VideoPlaybackTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val directory = File(context.cacheDir, "video-playback-test").apply { mkdirs() }
    private val callbacks = HandlerThread("frames").apply { start() }
    private lateinit var reader: ImageReader
    private lateinit var video: File
    private val lastLuma = AtomicInteger(-1)
    private val frames = AtomicInteger(0)
    private var renderer: VideoTrackRenderer? = null

    @Before
    fun setUp() {
        video = TestVideo.make(File(directory, "take.mp4"), seconds = 3)
        reader = ImageReader.newInstance(TestVideo.WIDTH, TestVideo.HEIGHT, ImageFormat.YUV_420_888, 4)
        reader.setOnImageAvailableListener({ source ->
            source.acquireLatestImage()?.use { image ->
                val plane = image.planes[0]
                val centre = (TestVideo.HEIGHT / 2) * plane.rowStride + (TestVideo.WIDTH / 2) * plane.pixelStride
                lastLuma.set(plane.buffer.get(centre).toInt() and 0xFF)
                frames.incrementAndGet()
            }
        }, Handler(callbacks.looper))
    }

    @After
    fun tearDown() {
        renderer?.release()
        reader.close()
        callbacks.quitSafely()
        directory.deleteRecursively()
    }

    private fun await(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            assertTrue("timed out waiting for: $what", System.currentTimeMillis() < deadline)
            Thread.sleep(10)
        }
    }

    /** Which frame a brightness belongs to; the coding loses a level or two, a frame is three apart. */
    private fun frameOf(luma: Int): Int = ((luma - TestVideo.lumaOf(0)) / 3.0).let { Math.round(it).toInt() }

    private fun frameAt(ms: Long) = (ms * TestVideo.FPS / 1_000).toInt()

    private fun start(positionMs: Long = 0, playing: Boolean = false): VideoTrackRenderer =
        VideoTrackRenderer(video, softwareDecoder = true).also {
            renderer = it
            it.follow(positionMs, playing)
            it.setSurface(reader.surface)
        }

    @Test
    fun aPausedPictureShowsTheFrameOfItsPlaceAndThenRests() {
        val renderer = start(positionMs = 1_000)
        await("the first frame") { renderer.state.value.showing }
        await("the frame to arrive") { frames.get() > 0 }
        assertTrue("frame ${frameOf(lastLuma.get())}", abs(frameOf(lastLuma.get()) - frameAt(1_000)) <= 1)
        assertEquals(TestVideo.WIDTH to TestVideo.HEIGHT, renderer.state.value.width to renderer.state.value.height)

        val shown = frames.get()
        Thread.sleep(400)
        assertEquals("standing still decodes nothing", shown, frames.get())
    }

    @Test
    fun aSeekLandsOnTheFrameAskedFor() {
        val renderer = start(positionMs = 0)
        await("the first frame") { frames.get() > 0 }
        listOf(2_000L, 600L, 2_600L, 0L).forEach { target ->
            val before = frames.get()
            renderer.follow(target, playing = false)
            await("a frame after the seek to $target") { frames.get() > before }
            Thread.sleep(150)
            assertTrue("at $target ms: frame ${frameOf(lastLuma.get())}", abs(frameOf(lastLuma.get()) - frameAt(target)) <= 1)
        }
    }

    @Test
    fun thePictureFollowsTheClockOfTheSound() {
        val renderer = start(positionMs = 0)
        await("the first frame") { frames.get() > 0 }
        // the "player": says where it is every 40 ms, as the real one does
        val began = System.currentTimeMillis()
        val before = frames.get()
        while (System.currentTimeMillis() - began < 1_500) {
            renderer.follow(System.currentTimeMillis() - began, playing = true)
            Thread.sleep(40)
        }
        val position = System.currentTimeMillis() - began
        renderer.follow(position, playing = false)
        Thread.sleep(200)
        val shown = frames.get() - before
        assertTrue("frames shown in 1.5 s at ${TestVideo.FPS} fps: $shown", shown in 15..26)
        assertTrue("stopped at frame ${frameOf(lastLuma.get())}, the sound at ${frameAt(position)}", abs(frameOf(lastLuma.get()) - frameAt(position)) <= 2)
    }

    @Test
    fun theSoundThatDoesNotMoveHoldsThePicture() {
        val renderer = start(positionMs = 500, playing = true)
        await("the first frame") { frames.get() > 0 }
        // a player that went silent is carried on for half a second at most, then the picture waits for it
        Thread.sleep(1_200)
        val held = frames.get()
        Thread.sleep(500)
        assertEquals(held, frames.get())
        assertTrue(frameOf(lastLuma.get()) <= frameAt(500 + 500) + 2)
    }

    @Test
    fun aSurfaceThatGoesAndComesBackGetsThePictureAtTheSamePlace() {
        val renderer = start(positionMs = 1_500)
        await("the first frame") { frames.get() > 0 }
        renderer.setSurface(null)
        val before = frames.get()
        renderer.setSurface(reader.surface)
        await("a frame on the new surface") { frames.get() > before }
        Thread.sleep(150)
        assertTrue(abs(frameOf(lastLuma.get()) - frameAt(1_500)) <= 1)
    }

    @Test
    fun releaseInTheMiddleOfPlayingDoesNotHang() {
        val renderer = start(positionMs = 0, playing = true)
        await("the first frame") { frames.get() > 0 }
        val began = System.currentTimeMillis()
        renderer.release()
        assertTrue(System.currentTimeMillis() - began < 1_500)
    }

    @Test
    fun aFileWithoutAPictureSaysSoAndTheSoundIsNobodysBusinessHere() {
        val sound = File(directory, "only-sound.mp4").apply { writeBytes(ByteArray(1_024)) }
        val renderer = VideoTrackRenderer(sound, softwareDecoder = true).also { this.renderer = it }
        renderer.setSurface(reader.surface)
        await("the failure") { renderer.state.value.failed }
        assertFalse(renderer.state.value.showing)
    }
}
