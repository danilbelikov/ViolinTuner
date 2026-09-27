package com.violinjourney.app.core.audio

import com.violinjourney.app.core.audio.backing.HostClock
import com.violinjourney.app.core.audio.micring.VJMicRing
import com.violinjourney.app.core.audio.micring.vj_mic_ring_behind
import com.violinjourney.app.core.audio.micring.vj_mic_ring_read
import com.violinjourney.app.core.audio.micring.vj_mic_ring_stamp
import com.violinjourney.app.core.audio.micring.vj_mic_ring_take_counters
import com.violinjourney.app.core.audio.micring.vj_mic_ring_wait
import com.violinjourney.app.core.audio.micring.vj_mic_ring_wake
import com.violinjourney.app.core.audio.micring.vj_mic_sink_create
import kotlin.concurrent.AtomicReference
import kotlin.concurrent.Volatile
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.AVFAudio.AVAudioSinkNode

/**
 * The microphone of iOS between the audio thread and Kotlin (micring.def). The node's block is C: on every I/O cycle it
 * copies the first channel into a ring of [capacity] samples, stamps the block's host time against the index of its
 * first sample and signals — no Kotlin, no allocation, no lock on the real-time thread, where a pause of the Kotlin/Native
 * collector would drop a cycle and shift the rest of a take against its backing (spec 5.25). Kotlin [read]s the ring on a
 * thread of its own, as `AudioRecord.read` is read on Android, and waits for it with [await].
 *
 * The ring lives as long as [node]: hold the node while the ring is used. One reader.
 */
@OptIn(ExperimentalForeignApi::class)
internal class IosMicRing(capacity: Int, private val rate: Int) {
    private val ring: CPointer<VJMicRing>
    val node: AVAudioSinkNode

    init {
        require(capacity > 0 && capacity and (capacity - 1) == 0) { "the ring holds a power of two samples, not $capacity" }
        var made: CPointer<VJMicRing>? = null
        node = memScoped {
            val out = alloc<CPointerVar<VJMicRing>>()
            val sink = checkNotNull(vj_mic_sink_create(capacity.toUInt(), out.ptr)) { "no sink node" }
            made = out.value
            sink
        }
        ring = checkNotNull(made) { "no ring" }
    }

    /** The native ring itself, for what writes into it by hand (tests). */
    internal val pointer: CPointer<VJMicRing> get() = ring

    private val failed = AtomicReference<MicUnavailableException?>(null)

    @Volatile private var lastStamp: Stamp? = null

    /** The first sample of the last stamped block, by its index in the stream, and when it was captured on the host clock. */
    class Stamp(val frame: Long, val hostTime: ULong)

    /** What the input did since the last call: blocks, the largest of them in samples, and the I/O cycles it missed. */
    class Counters(val blocks: Int, val largest: Int, val gaps: Int)

    /** Up to the size of [into] samples, in order; how many — 0 when nothing is waiting. */
    fun read(into: FloatArray): Int = into.usePinned { vj_mic_ring_read(ring, it.addressOf(0), into.size.toUInt()).toInt() }

    /** Waits up to [nanos] for sound or a [wake]; true when one came. */
    fun await(nanos: Long): Boolean = vj_mic_ring_wait(ring, nanos)

    fun wake() = vj_mic_ring_wake(ring)

    /** A block did not fit: the reader fell a whole ring behind the input. */
    val isBehind: Boolean get() = vj_mic_ring_behind(ring)

    /** Ends the input from outside (an interruption, the engine stopping): the reader throws [cause] once the ring is empty. */
    fun fail(cause: MicUnavailableException) {
        if (failed.compareAndSet(null, cause)) wake()
    }

    val failure: MicUnavailableException? get() = failed.value

    fun stamp(): Stamp? = memScoped {
        val frame = alloc<ULongVar>()
        val host = alloc<ULongVar>()
        if (vj_mic_ring_stamp(ring, frame.ptr, host.ptr)) Stamp(frame.value.toLong(), host.value).also { lastStamp = it } else lastStamp
    }

    fun takeCounters(): Counters = memScoped {
        val blocks = alloc<UIntVar>()
        val largest = alloc<UIntVar>()
        val gaps = alloc<UIntVar>()
        vj_mic_ring_take_counters(ring, blocks.ptr, largest.ptr, gaps.ptr)
        Counters(blocks.value.toInt(), largest.value.toInt(), gaps.value.toInt())
    }

    /**
     * When the sample at [tMs] of the stream was captured, in host-clock nanoseconds: the last stamp, less the input's
     * latency ([inputLatencySeconds]), plus the samples between (spec 5.25). Null before the first stamped block.
     */
    fun nanosAt(tMs: Long, inputLatencySeconds: Double): Long? {
        val stamp = stamp() ?: return null
        val sample = tMs * rate / MS_PER_SECOND
        return HostClock.nanosOf(stamp.hostTime) - (inputLatencySeconds * NANOS_PER_SECOND).toLong() +
            (sample - stamp.frame) * NANOS_PER_SECOND.toLong() / rate
    }

    private companion object {
        const val MS_PER_SECOND = 1_000L
        const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}

/**
 * Waits for sound in slices of [sliceNanos] with [signal], so that a collection cancelled meanwhile ([alive] false) is let
 * go within a slice rather than at the end of [limitNanos]. True as soon as [signal] says there is something; false after
 * [limitNanos] without, or once not [alive].
 */
internal fun awaitSound(signal: (Long) -> Boolean, limitNanos: Long, sliceNanos: Long, alive: () -> Boolean): Boolean {
    var waited = 0L
    while (waited < limitNanos) {
        if (!alive()) return false
        val slice = minOf(sliceNanos, limitNanos - waited)
        if (signal(slice)) return true
        waited += slice
    }
    return false
}
