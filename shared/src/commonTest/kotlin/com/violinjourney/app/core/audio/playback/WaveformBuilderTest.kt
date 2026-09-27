package com.violinjourney.app.core.audio.playback

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WaveformBuilderTest {
    @Test
    fun `the loudest part is the tallest bar and the rest stand against it`() {
        val builder = WaveformBuilder(totalSamples = 400, bars = 4)
        builder.add(ShortArray(100) { 1_000 }, 100)
        builder.add(ShortArray(100) { 4_000 }, 100)
        builder.add(ShortArray(100), 100)
        builder.add(ShortArray(100) { (if (it % 2 == 0) 2_000 else -2_000).toShort() }, 100)
        assertClose(floatArrayOf(0.25f, 1f, 0f, 0.5f), builder.build(), 1e-6f)
    }

    @Test
    fun `pieces of any size land in the same bars`() {
        val whole = WaveformBuilder(1_000, 10).apply { add(ShortArray(1_000) { (it * 30).toShort() }, 1_000) }.build()
        val pieces = WaveformBuilder(1_000, 10)
        val samples = ShortArray(1_000) { (it * 30).toShort() }
        var from = 0
        for (size in listOf(7, 333, 1, 659)) {
            pieces.add(samples.copyOfRange(from, from + size), size)
            from += size
        }
        assertClose(whole, pieces.build(), 1e-6f)
    }

    @Test
    fun `a file longer than it said ends in the last bar - a silent one is flat`() {
        val builder = WaveformBuilder(totalSamples = 100, bars = 4)
        builder.add(ShortArray(160) { 500 }, 160)
        assertEquals(4, builder.build().size)
        assertTrue(WaveformBuilder(100, 4).apply { add(ShortArray(100), 100) }.build().all { it == 0f })
        assertTrue(WaveformBuilder(0, 4).build().all { it == 0f })
    }

    @Test
    fun `a bar is kept in a byte`() {
        val waveform = floatArrayOf(0f, 0.5f, 1f, 0.2f)
        assertClose(waveform, WaveformBuilder.decode(WaveformBuilder.encode(waveform)), 1f / 255)
    }

    private fun assertClose(expected: FloatArray, actual: FloatArray, tolerance: Float) {
        assertEquals(expected.size, actual.size, "bars")
        expected.indices.forEach { assertTrue(abs(expected[it] - actual[it]) <= tolerance, "bar $it: ${actual.toList()} against ${expected.toList()}") }
    }
}
