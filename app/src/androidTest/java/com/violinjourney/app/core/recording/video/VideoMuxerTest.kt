package com.violinjourney.app.core.recording.video

import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.testing.TestVideo
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The picture of the app's camera and the take's sound made into one file (spec 3.32). */
@RunWith(AndroidJUnit4::class)
class VideoMuxerTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val scratch = File(context.cacheDir, "video-muxer-test").apply { mkdirs() }

    @After
    fun tearDown() {
        scratch.deleteRecursively()
    }

    @Test
    fun a_later_picture_starts_later_and_the_sound_starts_at_zero() {
        val picture = TestVideo.make(File(scratch, "picture.mp4"), seconds = 3, withSound = false)
        val sound = TestVideo.make(File(scratch, "sound.mp4"), seconds = 3)
        val target = File(scratch, "take.mp4")

        assertTrue(VideoMuxer.mux(picture, sound, target, shiftUs = 200_000))

        val tracks = firstTimes(target)
        assertEquals(2, tracks.size)
        assertEquals(0L, tracks.getValue("audio"), 30_000.0)
        assertEquals(200_000L, tracks.getValue("video"), 1_000.0)
    }

    @Test
    fun an_earlier_picture_loses_its_head_and_still_starts_on_a_key_frame() {
        val picture = TestVideo.make(File(scratch, "picture.mp4"), seconds = 3, withSound = false)
        val sound = TestVideo.make(File(scratch, "sound.mp4"), seconds = 3)
        val target = File(scratch, "take.mp4")

        assertTrue(VideoMuxer.mux(picture, sound, target, shiftUs = -500_000))

        // key frames every second: the first kept is the one at 1 s, moved to 0.5 s
        assertEquals(500_000L, firstTimes(target).getValue("video"), 1_000.0)
    }

    @Test
    fun a_splice_given_up_halfway_throws_its_cancellation_and_leaves_no_file() {
        val picture = TestVideo.make(File(scratch, "picture.mp4"), seconds = 3, withSound = false)
        val sound = TestVideo.make(File(scratch, "sound.mp4"), seconds = 3)
        val target = File(scratch, "shared.mp4")
        var samples = 0
        // «Отмена» of «Поделиться» in the middle of the splice: not a failure to be told, a cancellation to be passed on
        assertThrows(CancellationException::class.java) {
            VideoMuxer.splice(picture, sound, target, pictureShiftUs = 0, keepGoing = { if (++samples > 20) throw CancellationException("share given up") })
        }
        assertFalse(target.exists())
    }

    @Test
    fun a_splice_without_a_shift_keeps_every_picture_sample_and_tells_its_progress_to_the_end() {
        val picture = TestVideo.make(File(scratch, "picture.mp4"), seconds = 3, withSound = false, rotation = 90)
        val sound = TestVideo.make(File(scratch, "sound.mp4"), seconds = 3)
        val target = File(scratch, "shared.mp4")
        var last = 0f
        assertTrue(VideoMuxer.splice(picture, sound, target, pictureShiftUs = 0, onProgress = { assertTrue(it >= last); last = it }))
        assertEquals(videoTimes(picture), videoTimes(target))
        assertTrue("progress $last", last > 0.9f)
    }

    @Test
    fun without_a_sound_track_nothing_is_made() {
        val picture = TestVideo.make(File(scratch, "picture.mp4"), seconds = 2, withSound = false)
        assertFalse(VideoMuxer.mux(picture, picture, File(scratch, "take.mp4"), shiftUs = 0))
    }

    private fun assertEquals(expected: Long, actual: Long, delta: Double) = assertEquals(expected.toDouble(), actual.toDouble(), delta)

    private fun videoTimes(file: File): List<Long> {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        val track = (0 until extractor.trackCount).first { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!!.startsWith("video/") }
        extractor.selectTrack(track)
        val times = mutableListOf<Long>()
        val buffer = java.nio.ByteBuffer.allocate(1 shl 20)
        while (extractor.readSampleData(buffer, 0) >= 0) {
            times += extractor.sampleTime
            extractor.advance()
        }
        extractor.release()
        return times
    }

    /** The first sample's time of each kind of track. */
    private fun firstTimes(file: File): Map<String, Long> {
        val extractor = MediaExtractor().apply { setDataSource(file.absolutePath) }
        return try {
            (0 until extractor.trackCount).associate { track ->
                val mime = extractor.getTrackFormat(track).getString(MediaFormat.KEY_MIME).orEmpty()
                extractor.selectTrack(track)
                extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                val first = extractor.sampleTime
                extractor.unselectTrack(track)
                mime.substringBefore('/') to first
            }
        } finally {
            extractor.release()
        }
    }
}
