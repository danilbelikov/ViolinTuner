package com.example.violintuner.core.audio.share

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.violintuner.core.audio.playback.PcmDecoder
import com.example.violintuner.core.audio.recording.AacFileEncoder
import com.example.violintuner.core.domain.sound.BuiltInPreset
import com.example.violintuner.core.domain.sound.OutputSettings
import com.example.violintuner.core.domain.sound.SoundConfig
import com.example.violintuner.core.domain.sound.SoundPresets
import com.example.violintuner.core.domain.sound.SoundRules
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
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

/** The real decoder and encoder of the device: `./gradlew :app:connectedDebugAndroidTest`. */
@RunWith(AndroidJUnit4::class)
class SoundFileRendererTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val directory = File(context.cacheDir, "render-test").apply { mkdirs() }
    private val config = SoundConfig()
    private val renderer = SoundFileRenderer(config, Dispatchers.IO)
    private val rate = 48_000

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    private fun recording(name: String, seconds: Int, level: Int = 6_000): File {
        val file = File(directory, name)
        val encoder = AacFileEncoder(file, rate)
        val hop = ShortArray(512)
        var sample = 0L
        repeat(rate * seconds / hop.size) {
            for (i in hop.indices) hop[i] = (sin(2 * PI * 440.0 * sample++ / rate) * level).toInt().toShort()
            assertTrue(encoder.offer(hop, hop.size))
            Thread.sleep(4)
        }
        assertTrue(encoder.finish())
        return file
    }

    /** Length in samples and RMS of seconds [from]…[to] of a file. */
    private fun measure(file: File, from: Double, to: Double): Pair<Long, Double> {
        val decoder = checkNotNull(PcmDecoder.open(file))
        val chunk = ShortArray(4_096)
        var total = 0L
        var sum = 0.0
        var counted = 0L
        while (true) {
            val count = decoder.read(chunk)
            if (count == PcmDecoder.END) break
            for (i in 0 until count) {
                val at = (total + i).toDouble() / rate
                if (at >= from && at < to) {
                    sum += chunk[i].toDouble() * chunk[i]
                    counted++
                }
            }
            total += count
        }
        decoder.release()
        return total to sqrt(sum / counted.coerceAtLeast(1))
    }

    @Test
    fun theFileIsTheRecordingMadeLouderAndNotShifted() = runBlocking {
        val source = recording("source.m4a", seconds = 3)
        val target = File(directory, "out/louder.m4a")
        val louder = SoundRules.off(config).copy(output = OutputSettings(enabled = true, gainDb = 6.0))
        var last = 0f
        assertTrue(renderer.render(source, louder, target) { last = it })
        assertEquals(1f, last, 0.001f)

        val (sourceLength, sourceLevel) = measure(source, 1.0, 2.0)
        val (length, level) = measure(target, 1.0, 2.0)
        assertEquals("twice as loud", 2.0, level / sourceLevel, 0.1)
        // No hall — no tail: the file is as long as the recording, give or take the codec's own frames.
        assertTrue("length $length against $sourceLength", abs(length - sourceLength) < 4_000)
    }

    @Test
    fun theHallRingsOnAfterTheLastNote() = runBlocking {
        val source = recording("dry.m4a", seconds = 2)
        val target = File(directory, "hall.m4a")
        val hall = SoundPresets.settingsOf(BuiltInPreset.GRAND_HALL, config)
        val started = System.nanoTime()
        assertTrue(renderer.render(source, hall, target) { })
        val took = (System.nanoTime() - started) / 1e9
        android.util.Log.i("SoundChainSpeed", "render of 5 s with the hall: ${"%.2f".format(took)} s")
        assertTrue("two seconds of sound and three of tail took $took s", took < 2.5)

        val (sourceLength, _) = measure(source, 0.0, 1.0)
        val (length, _) = measure(target, 0.0, 1.0)
        val tail = (length - sourceLength).toDouble() / rate
        assertEquals("three seconds of hall", 3.0, tail, 0.15)
        val (_, ringing) = measure(target, 2.2, 2.6)
        assertTrue("the tail is sound, not silence: $ringing", ringing > 20)
    }

    @Test
    fun aLoudRecordingStaysUnderTheCeilingInTheFile() = runBlocking {
        val source = recording("loud.m4a", seconds = 2, level = 30_000)
        val target = File(directory, "limited.m4a")
        val pushed = SoundRules.off(config).copy(output = OutputSettings(enabled = true, gainDb = 12.0))
        assertTrue(renderer.render(source, pushed, target) { })
        val decoder = checkNotNull(PcmDecoder.open(target))
        val chunk = ShortArray(4_096)
        var peak = 0
        while (true) {
            val count = decoder.read(chunk)
            if (count == PcmDecoder.END) break
            for (i in 0 until count) peak = maxOf(peak, abs(chunk[i].toInt()))
        }
        decoder.release()
        // −1 dBFS is 29 205; the codec adds a little of its own on top — which is what the headroom is for.
        assertTrue("peak $peak", peak in 20_000..32_700)
    }

    @Test
    fun aCancelledRenderLeavesNoFileAndNeitherDoesAFailedOne() = runBlocking {
        val source = recording("long.m4a", seconds = 6)
        val target = File(directory, "cancelled.m4a")
        val hall = SoundPresets.settingsOf(BuiltInPreset.GRAND_HALL, config)
        val job = launch(Dispatchers.Default) {
            try {
                renderer.render(source, hall, target) { Thread.sleep(2) } // slow enough to be caught midway
            } catch (e: CancellationException) {
                throw e
            }
        }
        Thread.sleep(150)
        job.cancelAndJoin()
        assertFalse(target.exists())

        val junk = File(directory, "junk.m4a").apply { writeText("not sound") }
        assertFalse(renderer.render(junk, hall, File(directory, "never.m4a")) { })
        assertFalse(File(directory, "never.m4a").exists())
    }
}
