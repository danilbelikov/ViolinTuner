package com.example.violintuner.core.domain.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleCodecTest {
    @Test
    fun `round trip keeps notes, pauses and hundredths of a cent`() {
        val samples = listOf(SessionSample(69, 2.37), null, SessionSample(55, -48.91), SessionSample(100, 0.0), null)
        val decoded = SampleCodec.decode(SampleCodec.encode(samples))
        assertEquals(samples.size, decoded.size)
        samples.zip(decoded).forEach { (expected, actual) ->
            assertEquals(expected?.midi, actual?.midi)
            if (expected != null) assertEquals(expected.cents, actual!!.cents, 0.005)
        }
    }

    @Test
    fun `three bytes per bucket`() {
        assertEquals(3 * 72_000, SampleCodec.encode(List(72_000) { SessionSample(69, 1.0) }).size) // one hour
        assertEquals(0, SampleCodec.encode(emptyList()).size)
    }

    @Test
    fun `deviations beyond 16 bits are clamped, not wrapped`() {
        val decoded = SampleCodec.decode(SampleCodec.encode(listOf(SessionSample(69, 700.0), SessionSample(69, -700.0))))
        assertEquals(327.67, decoded[0]!!.cents, 1e-9)
        assertEquals(-327.68, decoded[1]!!.cents, 1e-9)
    }

    @Test
    fun `negative values keep their sign across the byte boundary`() {
        val decoded = SampleCodec.decode(SampleCodec.encode(listOf(SessionSample(69, -0.01), SessionSample(69, -2.56))))
        assertEquals(-0.01, decoded[0]!!.cents, 1e-9)
        assertEquals(-2.56, decoded[1]!!.cents, 1e-9)
    }

    @Test
    fun `broken blobs and notes are rejected`() {
        assertTrue(runCatching { SampleCodec.decode(ByteArray(4)) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { SampleCodec.encode(listOf(SessionSample(255, 0.0))) }.exceptionOrNull() is IllegalArgumentException)
    }
}
