package com.violinjourney.app.core.audio.backing

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A backing brought to the rate of the take (spec 5.25), and read back at any position. */
class ResamplerTest {
    private fun sine(hz: Double, rate: Int, seconds: Double, amplitude: Float = 0.5f) =
        FloatArray((rate * seconds).toInt()) { (amplitude * sin(2 * PI * hz * it / rate)).toFloat() }

    private fun run(resampler: Resampler, input: FloatArray, chunk: Int): FloatArray {
        val out = ArrayList<Float>()
        var at = 0
        while (at < input.size) {
            val n = minOf(chunk, input.size - at)
            resampler.process(input.copyOfRange(at, at + n), n) { c, k -> for (i in 0 until k) out += c[i] }
            at += n
        }
        resampler.finish { c, k -> for (i in 0 until k) out += c[i] }
        return out.toFloatArray()
    }

    /** Upward zero crossings per second, interpolated: the frequency of what came out. */
    private fun frequency(samples: FloatArray, rate: Int): Double {
        val crossings = ArrayList<Double>()
        for (i in 1 until samples.size) {
            if (samples[i - 1] < 0 && samples[i] >= 0) crossings += i - 1 + samples[i - 1] / (samples[i - 1] - samples[i]).toDouble()
        }
        return (crossings.size - 1) / ((crossings.last() - crossings.first()) / rate)
    }

    @Test
    fun `the same rate passes through untouched`() {
        val input = sine(440.0, 48_000, 0.1)
        val out = run(Resampler(48_000, 48_000), input, 1_000)
        assertArrayEquals(input, out, 0f)
    }

    @Test
    fun `44·1 to 48 kHz keeps the length, the pitch and the level`() {
        val input = sine(440.0, 44_100, 1.0)
        val out = run(Resampler(44_100, 48_000), input, 1_024)
        assertEquals(48_000.0, out.size.toDouble(), 2.0)
        assertEquals(440.0, frequency(out, 48_000), 0.05)
        val middle = out.copyOfRange(4_800, 43_200)
        assertEquals(0.5, middle.maxOf { it }.toDouble(), 0.005)
    }

    @Test
    fun `48 to 44·1 kHz keeps a high note and lets nothing fold back`() {
        val input = sine(3_520.0, 48_000, 0.5)
        val out = run(Resampler(48_000, 44_100), input, 777)
        assertEquals(22_050.0, out.size.toDouble(), 2.0)
        assertEquals(3_520.0, frequency(out, 44_100), 0.5)
        // a tone above the new Nyquist is taken out, not mirrored into the band
        val ultrasonic = run(Resampler(48_000, 44_100), sine(23_000.0, 48_000, 0.5), 777)
        assertTrue(ultrasonic.copyOfRange(2_000, 20_000).maxOf { abs(it) } < 0.05f)
    }

    @Test
    fun `chunk boundaries leave no trace`() {
        val input = sine(1_000.0, 44_100, 0.3)
        val whole = run(Resampler(44_100, 48_000), input, input.size)
        val chunked = run(Resampler(44_100, 48_000), input, 333)
        assertArrayEquals(whole, chunked, 1e-6f)
    }

    @Test
    fun `the reader gives silence before the start and after the end, the backing between, scaled`() {
        val file = File.createTempFile("backing", ".pcm")
        try {
            val frames = 10
            val bytes = ByteBuffer.allocate(frames * BackingPcmCache.BYTES_PER_FRAME).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until frames) {
                bytes.putShort((i * 1_000).toShort())
                bytes.putShort((-i * 1_000).toShort())
            }
            file.writeBytes(bytes.array())
            BackingPcmReader(file).use { reader ->
                assertEquals(10L, reader.frames)
                val left = FloatArray(6)
                val right = FloatArray(6)
                reader.read(position = -3, count = 6, gain = 2f, left = left, right = right)
                assertArrayEquals(floatArrayOf(0f, 0f, 0f, 0f, 2_000 / 32_768f, 4_000 / 32_768f), left, 1e-6f)
                assertArrayEquals(floatArrayOf(0f, 0f, 0f, 0f, -2_000 / 32_768f, -4_000 / 32_768f), right, 1e-6f)
                reader.read(position = 8, count = 6, gain = 1f, left = left, right = right)
                assertArrayEquals(floatArrayOf(8_000 / 32_768f, 9_000 / 32_768f, 0f, 0f, 0f, 0f), left, 1e-6f)
                reader.read(position = 40, count = 6, gain = 1f, left = left, right = right)
                assertTrue(left.all { it == 0f })
            }
        } finally {
            file.delete()
        }
    }
}
