package com.example.violintuner.core.audio.recording

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlin.math.PI
import kotlin.math.sin
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The real codec and muxer of the device: `./gradlew :app:connectedDebugAndroidTest`. */
@RunWith(AndroidJUnit4::class)
class AacFileEncoderTest {
    private val directory = File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "encoder-test")
        .apply { mkdirs() }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    /** [seconds] of a 440 Hz tone in hops of 512, offered a little faster than real time. */
    private fun encodeTone(file: File, sampleRate: Int, seconds: Int): Boolean {
        val encoder = AacFileEncoder(file, sampleRate)
        val hop = ShortArray(512)
        var sample = 0L
        var accepted = true
        repeat(sampleRate * seconds / hop.size) {
            for (i in hop.indices) hop[i] = (sin(2 * PI * 440.0 * sample++ / sampleRate) * 12_000).toInt().toShort()
            accepted = accepted && encoder.offer(hop, hop.size)
            Thread.sleep(5)
        }
        return encoder.finish() && accepted
    }

    @Test
    fun encodesPlayableAacOfTheRightLength() {
        for (rate in listOf(48_000, 44_100)) {
            val file = File(directory, "tone-$rate.m4a")
            assertTrue("encoding at $rate", encodeTone(file, rate, seconds = 3))
            assertTrue("file size ${file.length()}", file.length() in 8_000..60_000) // about 24 KB at 64 kbit/s

            val extractor = MediaExtractor().apply { setDataSource(file.absolutePath) }
            val format = extractor.getTrackFormat(0)
            assertEquals(1, extractor.trackCount)
            assertEquals(MediaFormat.MIMETYPE_AUDIO_AAC, format.getString(MediaFormat.KEY_MIME))
            assertEquals(rate, format.getInteger(MediaFormat.KEY_SAMPLE_RATE))
            assertEquals(1, format.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
            val durationMs = format.getLong(MediaFormat.KEY_DURATION) / 1_000
            assertTrue("duration $durationMs ms at $rate", durationMs in 2_900..3_150)
            extractor.release()

            val player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
            }
            assertTrue("player duration ${player.duration}", player.duration in 2_900..3_150)
            player.release()
        }
    }

    @Test
    fun offeringFasterThanTheCodecCanEncodeFailsInsteadOfBlocking() {
        val file = File(directory, "flood.m4a")
        val encoder = AacFileEncoder(file, 48_000)
        val hop = ShortArray(512)
        val startedAt = System.nanoTime()
        val accepted = (0 until 20_000).count { encoder.offer(hop, hop.size) } // 3.5 minutes of audio at once
        val offerMs = (System.nanoTime() - startedAt) / 1_000_000
        assertTrue("accepted $accepted", accepted < 20_000)
        assertTrue("offers took $offerMs ms", offerMs < 2_000)
        encoder.finish()
    }

    /** Cannot happen in the app (the encoder is created with the first hop), but must not hang. */
    @Test
    fun finishingWithoutAnySoundReturnsPromptly() {
        val encoder = AacFileEncoder(File(directory, "empty.m4a"), 48_000)
        val startedAt = System.nanoTime()
        encoder.finish()
        assertTrue((System.nanoTime() - startedAt) / 1_000_000 < 2_000)
    }
}
