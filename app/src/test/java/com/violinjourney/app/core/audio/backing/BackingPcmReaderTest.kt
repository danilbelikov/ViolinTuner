package com.violinjourney.app.core.audio.backing

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The backing's sound read back at any position on Android, as the mix of a take reads it (spec 5.25). */
class BackingPcmReaderTest {
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
