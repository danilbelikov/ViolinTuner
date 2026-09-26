package com.violinjourney.app.core.backup

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/** The checksum of the iOS zip is ZIP's CRC-32, whole or in pieces — the archives of Android say it the same way. */
class Crc32Test {
    @Test
    fun `the check value of CRC-32 comes out`() {
        val crc = Crc32()
        val digits = "123456789".encodeToByteArray()
        crc.update(digits, 0, digits.size)
        assertEquals(0xCBF43926.toInt(), crc.value)
    }

    @Test
    fun `a sum in pieces is the sum of the whole`() {
        val bytes = Random(11).nextBytes(300_000)
        val whole = Crc32().apply { update(bytes, 0, bytes.size) }.value
        val pieces = Crc32()
        var at = 0
        listOf(1, 7, 8_192, 65_536, 100_003).forEach { size ->
            pieces.update(bytes, at, size)
            at += size
        }
        pieces.update(bytes, at, bytes.size - at)
        assertEquals(whole, pieces.value)
    }

    @Test
    fun `nothing added changes nothing`() {
        val crc = Crc32()
        assertEquals(0, crc.value)
        val bytes = byteArrayOf(1, 2, 3)
        crc.update(bytes, 0, bytes.size)
        val before = crc.value
        crc.update(bytes, 3, 0)
        crc.update(ByteArray(0), 0, 0)
        assertEquals(before, crc.value)
    }
}
