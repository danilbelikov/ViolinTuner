package com.violinjourney.app.core.audio.backing

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The weights of the resampler reckoned once per position between input samples (spec 5.25: a backing brought to the
 * rate of the take) give what the kernel reckoned for every sample gave — only many times sooner.
 */
class ResamplerPhasesTest {
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

    private fun check(inRate: Int, outRate: Int) {
        // a chord with a high partial: the kernel's shape shows at every position
        val input = FloatArray(inRate / 4) { (0.4 * sin(2 * PI * 440.0 * it / inRate) + 0.2 * sin(2 * PI * 5_000.0 * it / inRate)).toFloat() }
        val table = run(Resampler(inRate, outRate), input, 1_000)
        val kernel = run(Resampler(inRate, outRate, halfWidth = 16, maxPhases = 0), input, 1_000)
        assertEquals(kernel.size, table.size, "$inRate to $outRate: the length")
        val worst = table.indices.maxOf { abs(table[it] - kernel[it]) }
        assertTrue(worst < 1e-5f, "$inRate to $outRate: the table is off by $worst")
    }

    @Test
    fun `44_1 to 48 kHz`() = check(44_100, 48_000)

    @Test
    fun `48 to 44_1 kHz`() = check(48_000, 44_100)

    @Test
    fun `22_05 to 48 kHz`() = check(22_050, 48_000)

    @Test
    fun `96 to 48 kHz`() = check(96_000, 48_000)
}
