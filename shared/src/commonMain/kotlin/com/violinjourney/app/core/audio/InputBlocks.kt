package com.violinjourney.app.core.audio

import com.violinjourney.app.core.concurrent.PlatformLock
import com.violinjourney.app.core.concurrent.withLock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Blocks of sound on their way from the thread the system hands them over on to the analysis — the input of iOS, whose
 * audio engine gives float blocks of its own length on its own thread. The system's side never waits; the analysis
 * takes the blocks in order.
 *
 * Two ways the input is found lost, both ending the stream with the caller's [MicUnavailableException] (spec 3.4: the
 * screen says so and the input is opened again):
 * - more than [maxQueuedSamples] would wait: the analysis has fallen far behind. Dropping sound instead would break the
 *   sample clock the frames and the take share. The limit is a length of sound, not a count of blocks — the system
 *   picks the block length.
 * - no block has arrived for the stall limit of [forEach]: an input the system stopped by itself gives no blocks at
 *   all, and the digital-silence watchdog, fed by blocks, never sees that. The limit is counted on each wait for the
 *   next block, never from the last block taken: an action that is busy — a take being finished can hold it for
 *   seconds — is no stall, and what arrived meanwhile is taken at once.
 *
 * One per stream.
 */
class InputBlocks(private val maxQueuedSamples: Long) {
    private val channel = Channel<FloatArray>(Channel.UNLIMITED)
    private val lock = PlatformLock()
    private var queuedSamples = 0L
    private var ended = false

    /**
     * The system's side, never waiting: queues [block]. False — once — when it would leave more than
     * [maxQueuedSamples] waiting: the caller then [fail]s the input. A block that comes after the end is dropped.
     */
    fun offer(block: FloatArray): Boolean = lock.withLock {
        when {
            ended -> true
            queuedSamples + block.size > maxQueuedSamples -> {
                ended = true
                false
            }
            else -> {
                queuedSamples += block.size
                channel.trySend(block)
                true
            }
        }
    }

    /** Ends the input with [cause]: what is queued is still taken, then [forEach] throws it. After the end, nothing changes. */
    fun fail(cause: Throwable) {
        lock.withLock { ended = true }
        channel.close(cause)
    }

    /** Ends the input plainly: [forEach] returns once the queue is empty. */
    fun close() {
        lock.withLock { ended = true }
        channel.close()
    }

    /**
     * Gives [action] every block in order until the input ends: returns after [close], throws the cause of [fail], and
     * throws what [stalled] gives when no block has arrived for [stallLimitMs]. Cancellation stays cancellation.
     */
    suspend fun forEach(stallLimitMs: Long, stalled: () -> Throwable, action: suspend (FloatArray) -> Unit) {
        while (true) {
            val next = withTimeoutOrNull(stallLimitMs) { channel.receiveCatching() } ?: throw stalled()
            if (next.isClosed) {
                next.exceptionOrNull()?.let { throw it }
                return
            }
            val block = next.getOrThrow()
            lock.withLock { queuedSamples -= block.size }
            action(block)
        }
    }
}
