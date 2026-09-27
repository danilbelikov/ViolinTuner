package com.violinjourney.app.core.audio.playback

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.testing.TestSound
import java.io.File
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real extractor and codecs of the device (spec 5.25: any file the phone decodes, at any rate): the sound is read
 * in the rate and the kind of samples the codec puts out. Until 27.09.2026 the rate came from the container — HE-AAC
 * played at half its speed — and every sample was read as 16 bits, so a 24-bit or float WAV was noise. Emulator only,
 * with `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`.
 */
@RunWith(AndroidJUnit4::class)
class PcmDecoderFormatsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val directory = File(context.cacheDir, "decoder-formats-test").apply { mkdirs() }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    /**
     * The whole of [file] through [PcmDecoder.read] and [PcmDecoder.readStereo], each checked for the tone and for its
     * length, give or take [slackSeconds].
     */
    private fun checkTone(file: File, rate: Int, seconds: Double, slackSeconds: Double = 0.1) {
        val extractor = MediaExtractor().apply { setDataSource(file.absolutePath) }
        Log.i(TAG, "${file.name}: the track is ${extractor.getTrackFormat(0)}")
        extractor.release()
        val mono = checkNotNull(PcmDecoder.open(file)) { "cannot open ${file.name}" }
        assertEquals("the rate of ${file.name}", rate, mono.sampleRate)
        val chunk = ShortArray(4_096)
        val all = ArrayList<Float>()
        while (true) {
            val count = mono.read(chunk)
            if (count == PcmDecoder.END) break
            for (i in 0 until count) all += chunk[i] / 32_768f
        }
        mono.release()
        assertTrue("${all.size} mono samples of ${file.name}", abs(all.size - seconds * rate) < rate * slackSeconds)
        val middle = all.subList(all.size / 4, all.size * 3 / 4).toFloatArray()
        assertEquals("the mono tone of ${file.name}", 440.0, TestSound.pitchOf(middle, middle.size, rate), 10.0)
        assertTrue("the mono level of ${file.name}", middle.maxOf { abs(it) } in 0.2f..0.3f)

        val stereo = checkNotNull(PcmDecoder.open(file))
        val left = FloatArray(4_096)
        val right = FloatArray(4_096)
        val lefts = ArrayList<Float>()
        val rights = ArrayList<Float>()
        while (true) {
            val count = stereo.readStereo(left, right)
            if (count == PcmDecoder.END) break
            for (i in 0 until count) {
                lefts += left[i]
                rights += right[i]
            }
        }
        stereo.release()
        assertEquals("both sides of ${file.name}", lefts.size, rights.size)
        assertTrue("${lefts.size} frames of ${file.name}", abs(lefts.size - seconds * rate) < rate * slackSeconds)
        for (side in listOf(lefts, rights)) {
            val half = side.subList(side.size / 4, side.size * 3 / 4).toFloatArray()
            assertEquals("the stereo tone of ${file.name}", 440.0, TestSound.pitchOf(half, half.size, rate), 10.0)
            assertTrue("the stereo level of ${file.name}", half.maxOf { abs(it) } in 0.2f..0.3f)
        }
    }

    @Test
    fun aWavOf24BitsIsTheToneNotNoise() {
        checkTone(TestSound.wav(File(directory, "deep.wav"), rate = 48_000, channels = 2, bits = 24, seconds = 1.0), 48_000, 1.0)
    }

    @Test
    fun aWavOfFloatsIsTheToneNotNoise() {
        checkTone(TestSound.wav(File(directory, "float.wav"), rate = 44_100, channels = 1, bits = 32, float = true, seconds = 1.0), 44_100, 1.0)
    }

    @Test
    fun aWavOf16BitsStaysAsItWas() {
        checkTone(TestSound.wav(File(directory, "plain.wav"), rate = 44_100, channels = 2, bits = 16, seconds = 1.0), 44_100, 1.0)
    }

    @Test
    fun heAacPlaysAtTheRateItsDecoderPutsOut() {
        val file = TestSound.heAac(File(directory, "he.m4a"), rate = 44_100, seconds = 3)
        assumeTrue("no HE-AAC encoder on this device", file != null)
        val extractor = MediaExtractor().apply { setDataSource(checkNotNull(file).absolutePath) }
        val containerRate = extractor.getTrackFormat(0).getInteger(MediaFormat.KEY_SAMPLE_RATE)
        extractor.release()
        Log.i(TAG, "HE-AAC: the container says $containerRate Hz")
        // the encoder's delay and its last frame add some 0.16 s here; the container's rate would halve the whole
        checkTone(checkNotNull(file), 44_100, 3.0, slackSeconds = 0.25)
    }

    private companion object {
        const val TAG = "PcmDecoderFormatsTest"
    }
}
