package com.violinjourney.app.core.audio

import com.violinjourney.app.core.audio.micring.vj_mic_ring_write
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import platform.CoreAudioTypes.AudioTimeStamp
import platform.CoreAudioTypes.kAudioTimeStampHostTimeValid
import platform.CoreAudioTypes.kAudioTimeStampSampleTimeValid
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The ring between the audio thread and Kotlin, written through the very function the sink node's block calls: no
 * microphone is needed, and none is in the simulator's tests.
 */
@OptIn(ExperimentalForeignApi::class)
class IosMicRingTest {
    private fun IosMicRing.write(values: List<Float>, stride: Int = 1, sampleTime: Double? = null, hostTime: ULong? = null) = memScoped {
        val data = allocArray<kotlinx.cinterop.FloatVar>(values.size * stride)
        values.forEachIndexed { i, v ->
            for (c in 0 until stride) data[i * stride + c] = if (c == 0) v else -1f
        }
        val time = alloc<AudioTimeStamp>()
        var flags = 0u
        if (sampleTime != null) {
            time.mSampleTime = sampleTime
            flags = flags or kAudioTimeStampSampleTimeValid
        }
        if (hostTime != null) {
            time.mHostTime = hostTime
            flags = flags or kAudioTimeStampHostTimeValid
        }
        time.mFlags = flags
        vj_mic_ring_write(pointer, data, values.size.toUInt(), stride.toUInt(), time.ptr)
    }

    private fun IosMicRing.readAll(max: Int = 64): List<Float> {
        val out = FloatArray(max)
        return out.copyOf(read(out)).toList()
    }

    @Test
    fun `blocks come out in order across the wrap of the ring`() {
        val ring = IosMicRing(8, 48_000)
        ring.write(listOf(1f, 2f, 3f, 4f, 5f))
        assertEquals(listOf(1f, 2f, 3f), FloatArray(3).let { it.copyOf(ring.read(it)).toList() })
        ring.write(listOf(6f, 7f, 8f, 9f, 10f)) // runs over the end of the ring
        assertEquals(listOf(4f, 5f, 6f, 7f, 8f, 9f, 10f), ring.readAll())
        assertEquals(emptyList(), ring.readAll())
        assertFalse(ring.isBehind)
    }

    @Test
    fun `a block that does not fit is refused and the ring is behind`() {
        val ring = IosMicRing(8, 48_000)
        ring.write(List(6) { it.toFloat() })
        ring.write(List(3) { 9f })
        assertTrue(ring.isBehind)
        assertEquals(List(6) { it.toFloat() }, ring.readAll(), "what was written before stays whole")
    }

    @Test
    fun `once behind the ring takes nothing more — the reader never goes on across the hole`() {
        val ring = IosMicRing(8, 48_000)
        ring.write(List(6) { it.toFloat() })
        ring.write(List(3) { 9f }) // does not fit
        assertEquals(List(4) { it.toFloat() }, FloatArray(4).let { it.copyOf(ring.read(it)).toList() })
        ring.write(List(2) { 7f }) // would fit now
        assertTrue(ring.isBehind, "behind stays behind")
        assertEquals(listOf(4f, 5f), ring.readAll(), "only the sound from before the gap")
        assertEquals(emptyList(), ring.readAll())
    }

    @Test
    fun `interleaved input gives its first channel`() {
        val ring = IosMicRing(8, 48_000)
        ring.write(listOf(0.1f, 0.2f, 0.3f), stride = 2)
        assertContentEquals(listOf(0.1f, 0.2f, 0.3f), ring.readAll())
    }

    @Test
    fun `a wait ends with sound or a wake — and not without`() {
        val ring = IosMicRing(8, 48_000)
        assertFalse(ring.await(20_000_000L), "nothing was written")
        ring.write(listOf(1f))
        assertTrue(ring.await(20_000_000L))
        ring.wake()
        assertTrue(ring.await(20_000_000L))
        assertFalse(ring.await(1_000_000L))
    }

    @Test
    fun `a failure from outside is kept and wakes the reader`() {
        val ring = IosMicRing(8, 48_000)
        assertNull(ring.failure)
        val first = MicUnavailableException(MicUnavailableReason.READ_FAILED, "first")
        ring.fail(first)
        ring.fail(MicUnavailableException(MicUnavailableReason.READ_FAILED, "second"))
        assertEquals(first, ring.failure)
        assertTrue(ring.await(20_000_000L))
    }

    @Test
    fun `the stamp is the first sample of the last block with a host time`() {
        val ring = IosMicRing(64, 48_000)
        assertNull(ring.stamp(), "no stamped block yet")
        ring.write(List(4) { 0f }, sampleTime = 100.0, hostTime = 5_000uL)
        val first = assertNotNull(ring.stamp())
        assertEquals(0L, first.frame)
        assertEquals(5_000uL, first.hostTime)
        ring.write(List(4) { 0f }, sampleTime = 104.0) // no host time: the stamp stays
        assertEquals(0L, ring.stamp()!!.frame)
        ring.write(List(4) { 0f }, sampleTime = 108.0, hostTime = 9_000uL)
        assertEquals(8L, ring.stamp()!!.frame)
        assertEquals(9_000uL, ring.stamp()!!.hostTime)
    }

    @Test
    fun `a sample time that does not follow is a missed cycle`() {
        val ring = IosMicRing(64, 48_000)
        ring.write(List(4) { 0f }, sampleTime = 0.0)
        ring.write(List(4) { 0f }, sampleTime = 4.0)
        ring.write(List(6) { 0f }, sampleTime = 20.0) // cycles 8..19 never came
        val counters = ring.takeCounters()
        assertEquals(3, counters.blocks)
        assertEquals(6, counters.largest)
        assertEquals(1, counters.gaps)
        val again = ring.takeCounters()
        assertEquals(0, again.blocks, "taken to zero")
        assertEquals(0, again.gaps)
    }

    @Test
    fun `a take's clock counts from the stamp`() {
        val ring = IosMicRing(64, 48_000)
        assertNull(ring.nanosAt(0, inputLatencySeconds = 0.0))
        ring.write(List(48) { 0f }, hostTime = 0uL)
        val at = ring.nanosAt(1_000, inputLatencySeconds = 0.002)!!
        val start = ring.nanosAt(0, inputLatencySeconds = 0.002)!!
        assertEquals(1_000_000_000L, at - start, "a second of samples is a second of the host clock")
    }
}
