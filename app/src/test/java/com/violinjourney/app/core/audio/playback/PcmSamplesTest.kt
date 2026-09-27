package com.violinjourney.app.core.audio.playback

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** A decoder's output read in its own kind of samples: until 27.09.2026 all of it was read as 16-bit (spec 5.25). */
class PcmSamplesTest {
    private fun buffer(size: Int, fill: ByteBuffer.() -> Unit): ByteBuffer =
        ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).apply(fill).apply { flip() }

    private fun read(buffer: ByteBuffer, encoding: Int): List<Float> = buildList { while (buffer.hasRemaining()) add(PcmSamples.next(buffer, encoding)) }

    @Test
    fun `16-bit`() {
        val samples = read(buffer(6) { putShort(Short.MIN_VALUE); putShort(0); putShort(16_384) }, PcmSamples.PCM_16BIT)
        assertEquals(listOf(-1f, 0f, 0.5f), samples)
    }

    @Test
    fun `float passes as it is`() {
        val samples = read(buffer(8) { putFloat(-1f); putFloat(0.5f) }, PcmSamples.PCM_FLOAT)
        assertEquals(listOf(-1f, 0.5f), samples)
    }

    @Test
    fun `8-bit is unsigned around 128`() {
        val samples = read(buffer(3) { put(0); put(128.toByte()); put(255.toByte()) }, PcmSamples.PCM_8BIT)
        assertEquals(-1f, samples[0], 0f)
        assertEquals(0f, samples[1], 0f)
        assertEquals(0.992f, samples[2], 0.001f)
    }

    @Test
    fun `24-bit packed keeps its sign in either byte order`() {
        fun put24(buffer: ByteBuffer, value: Int) {
            if (buffer.order() == ByteOrder.LITTLE_ENDIAN) {
                buffer.put(value.toByte()); buffer.put((value shr 8).toByte()); buffer.put((value shr 16).toByte())
            } else {
                buffer.put((value shr 16).toByte()); buffer.put((value shr 8).toByte()); buffer.put(value.toByte())
            }
        }
        for (order in listOf(ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN)) {
            val buffer = ByteBuffer.allocate(12).order(order)
            listOf(0x800000, 0x400000, 0x7FFFFF, 0xFFFFFF).forEach { put24(buffer, it) }
            buffer.flip()
            val samples = read(buffer, PcmSamples.PCM_24BIT_PACKED)
            assertEquals("$order", -1f, samples[0], 0f)
            assertEquals("$order", 0.5f, samples[1], 0f)
            assertEquals("$order", 1f, samples[2], 0.000_001f)
            assertEquals("$order: -1 in 24 bits", -1f / 8_388_608f, samples[3], 0f)
        }
    }

    @Test
    fun `32-bit`() {
        val samples = read(buffer(8) { putInt(Int.MIN_VALUE); putInt(0x40000000) }, PcmSamples.PCM_32BIT)
        assertEquals(listOf(-1f, 0.5f), samples)
    }

    @Test
    fun `bytes per sample, and an encoding not known here is refused rather than played as noise`() {
        assertEquals(listOf(1, 2, 3, 4, 4), listOf(PcmSamples.PCM_8BIT, PcmSamples.PCM_16BIT, PcmSamples.PCM_24BIT_PACKED, PcmSamples.PCM_FLOAT, PcmSamples.PCM_32BIT).map(PcmSamples::bytesPerSample))
        assertThrows(IllegalStateException::class.java) { PcmSamples.bytesPerSample(13) }
        assertThrows(IllegalStateException::class.java) { PcmSamples.next(buffer(4) { putInt(0) }, 13) }
    }

    @Test
    fun `back to 16 bits, clipped at full scale`() {
        assertEquals(Short.MAX_VALUE, PcmSamples.toShort(1.5f))
        assertEquals(Short.MIN_VALUE, PcmSamples.toShort(-1.5f))
        assertEquals(16_384.toShort(), PcmSamples.toShort(0.5f))
    }
}
