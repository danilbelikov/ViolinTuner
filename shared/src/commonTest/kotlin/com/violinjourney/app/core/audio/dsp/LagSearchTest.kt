package com.violinjourney.app.core.audio.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class LagSearchTest {
    private val random = Random(11)

    @Test
    fun `interpolation with three sines is the Lanczos sum it was`() {
        repeat(20) {
            val size = 300
            val values = DoubleArray(size) { random.nextDouble(-1.0, 1.0) }
            repeat(500) {
                val t = random.nextDouble(0.0, size - 1.0)
                assertEquals(ReferenceLanczos.interpolate(values, size, t), LagSearch.interpolate(values, size, t), 1e-12, "at $t")
            }
        }
    }

    @Test
    fun `interpolation is exact at whole lags`() {
        val values = DoubleArray(64) { random.nextDouble(-1.0, 1.0) }
        for (k in values.indices) assertEquals(values[k], LagSearch.interpolate(values, values.size, k.toDouble()), 1e-12, "at $k")
    }

    @Test
    fun `refinement finds the crest it found before`() {
        val period = 17.3
        val values = DoubleArray(80) { cos(2 * PI * it / period) }
        val before = ReferenceLanczos.refine(values, values.size, 17, sign = -1)
        val now = LagSearch.refine(values, values.size, 17, sign = -1)
        assertEquals(before, now, 1e-9)
        // and near the crest of the cosine itself: a windowed kernel on 17 samples a period is off by a hundredth of one
        assertEquals(period, now, 0.02)
    }
}
