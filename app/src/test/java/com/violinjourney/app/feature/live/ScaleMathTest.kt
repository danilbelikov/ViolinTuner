package com.violinjourney.app.feature.live

import com.violinjourney.app.core.domain.IntonationConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class ScaleMathTest {
    private val scale = ScaleSpec(IntonationConfig())

    @Test
    fun `zero cents is the center`() {
        assertEquals(0.5, ScaleMath.markerFraction(0.0, scale), EPS)
    }

    @Test
    fun `marker moves linearly, sharp to the right`() {
        assertEquals(0.75, ScaleMath.markerFraction(25.0, scale), EPS)
        assertEquals(0.25, ScaleMath.markerFraction(-25.0, scale), EPS)
        assertEquals(1.0, ScaleMath.markerFraction(50.0, scale), EPS)
        assertEquals(0.0, ScaleMath.markerFraction(-50.0, scale), EPS)
    }

    @Test
    fun `deviations beyond the range stick to the ends`() {
        assertEquals(1.0, ScaleMath.markerFraction(350.0, scale), EPS)
        assertEquals(0.0, ScaleMath.markerFraction(-700.0, scale), EPS)
    }

    @Test
    fun `green segment spans the tolerance on both sides`() {
        assertEquals(0.16, ScaleMath.inTuneFraction(scale), EPS)
        assertEquals(0.10, ScaleMath.inTuneFraction(scale.copy(toleranceCents = 5.0)), EPS)
    }

    @Test
    fun `segment edge is where the marker sits at exactly the tolerance`() {
        val edge = 0.5 + ScaleMath.inTuneFraction(scale) / 2
        assertEquals(edge, ScaleMath.markerFraction(scale.toleranceCents, scale), EPS)
    }

    private companion object {
        const val EPS = 1e-9
    }
}
