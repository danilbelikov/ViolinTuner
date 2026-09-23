package com.violinjourney.app.core.audio.share

import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.audio.playback.PcmDecoder
import com.violinjourney.app.core.domain.sound.BuiltInPreset
import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.domain.sound.SoundPresets
import com.violinjourney.app.core.domain.sound.SoundRules
import com.violinjourney.app.testing.TestVideo
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The file of a video take that is sent: the picture as it was, the sound as it is heard in the app. */
@RunWith(AndroidJUnit4::class)
class VideoShareRenderTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val directory = File(context.cacheDir, "video-share-test").apply { mkdirs() }
    private val config = SoundConfig()
    private val renderer = SoundFileRenderer(config, Dispatchers.IO)
    private val hall = SoundPresets.settingsOf(BuiltInPreset.GRAND_HALL, config)

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    private class Track(val mime: String, val times: List<Long>, val sizes: List<Int>, val rotation: Int, val durationUs: Long)

    private fun tracksOf(file: File): List<Track> {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        val tracks = (0 until extractor.trackCount).map { index ->
            val format = extractor.getTrackFormat(index)
            extractor.selectTrack(index)
            val times = mutableListOf<Long>()
            val sizes = mutableListOf<Int>()
            val buffer = java.nio.ByteBuffer.allocate(1 shl 20)
            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                times += extractor.sampleTime
                sizes += size
                extractor.advance()
            }
            extractor.unselectTrack(index)
            Track(
                mime = format.getString(MediaFormat.KEY_MIME)!!,
                times = times, sizes = sizes,
                rotation = if (format.containsKey(MediaFormat.KEY_ROTATION)) format.getInteger(MediaFormat.KEY_ROTATION) else 0,
                durationUs = format.getLong(MediaFormat.KEY_DURATION),
            )
        }
        extractor.release()
        return tracks
    }

    @Test
    fun thePictureIsCopiedSampleForSampleAndTheSoundRingsOnForTheHall() = runBlocking {
        val source = TestVideo.make(File(directory, "take.mp4"), seconds = 3, rotation = 90)
        val target = File(directory, "out/Менуэт · 18 сентября.mp4")
        var last = 0f
        assertTrue(renderer.renderVideo(source, hall, target) { assertTrue(it >= last - 1e-3f); last = it })
        assertEquals(1f, last, 0.02f)

        val before = tracksOf(source).single { it.mime.startsWith("video/") }
        val after = tracksOf(target)
        val picture = after.single { it.mime.startsWith("video/") }
        val sound = after.single { it.mime.startsWith("audio/") }
        assertEquals(before.times, picture.times)
        assertEquals(before.sizes, picture.sizes)
        assertEquals(90, picture.rotation)
        // the hall rings on after the last note; the picture does not grow
        assertTrue("sound ${sound.durationUs}, picture ${picture.durationUs}", sound.durationUs > picture.durationUs + 1_000_000)
        assertFalse("the temporary sound is gone", target.parentFile!!.listFiles()!!.any { it.name.endsWith(".m4a") })
    }

    @Test
    fun theSoundOfTheFileIsTheProcessedOne() = runBlocking {
        val source = TestVideo.make(File(directory, "take.mp4"), seconds = 2)
        val target = File(directory, "out.mp4")
        assertTrue(renderer.renderVideo(source, hall, target) {})
        // louder or not, it is a different sound from the source's: a hall was added
        fun energyOf(file: File): Double {
            val decoder = PcmDecoder.open(file)!!
            val pcm = ShortArray(4_096)
            var sum = 0.0
            var seen = 0L
            while (true) {
                val count = decoder.read(pcm)
                if (count == PcmDecoder.END) break
                // only what comes after the source has ended: the tail
                for (i in 0 until count) if (seen + i > 2L * decoder.sampleRate + 4_800) sum += pcm[i].toDouble() * pcm[i]
                seen += count
            }
            decoder.release()
            return sum
        }
        assertEquals(0.0, energyOf(source), 1.0)
        assertTrue("a tail of the hall is in the file", energyOf(target) > 1e6)
    }

    @Test
    fun withEverythingOffTheSoundOnlyFileIsTheSoundAsRecorded() = runBlocking {
        val source = TestVideo.make(File(directory, "take.mp4"), seconds = 2)
        val target = File(directory, "sound.m4a")
        assertTrue(renderer.render(source, SoundRules.off(config), target) {})
        val decoder = PcmDecoder.open(target)!!
        // no tail, no delay: as long as the source, to an encoder frame or two
        assertEquals(2.0, decoder.totalSamples.toDouble() / decoder.sampleRate, 0.1)
        decoder.release()
    }

    @Test
    fun aCancelledRenderLeavesNothing() = runBlocking {
        val source = TestVideo.make(File(directory, "take.mp4"), seconds = 8)
        val target = File(directory, "cancelled/out.mp4")
        val job = launch(Dispatchers.Default) { renderer.renderVideo(source, hall, target) {} }
        Thread.sleep(150)
        job.cancelAndJoin()
        assertFalse(target.exists())
        assertTrue(target.parentFile?.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun aSourceWithoutAPictureCannotBeMadeIntoAVideo() = runBlocking {
        val source = TestVideo.make(File(directory, "take.mp4"), seconds = 2)
        val soundOnly = File(directory, "sound.m4a")
        assertTrue(renderer.render(source, hall, soundOnly) {})
        val target = File(directory, "out.mp4")
        assertFalse(renderer.renderVideo(soundOnly, hall, target) {})
        assertFalse(target.exists())
    }
}
