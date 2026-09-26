package com.violinjourney.app.core.audio

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest

/**
 * The hand-over of the iOS input: an input that stopped by itself gives no blocks at all and must end as a lost
 * microphone (spec 3.4) instead of freezing Live and the take; a busy analysis is not such an input.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InputBlocksTest {
    private val stalled = { MicUnavailableException(MicUnavailableReason.READ_FAILED, "no sound") }

    @Test
    fun `blocks come out in the order they were offered`() = runTest {
        val blocks = InputBlocks(maxQueuedSamples = 100)
        repeat(3) { i -> assertTrue(blocks.offer(FloatArray(4) { i.toFloat() })) }
        blocks.close()
        val seen = ArrayList<Float>()
        blocks.forEach(LIMIT_MS, stalled) { seen += it.first() }
        assertEquals(listOf(0f, 1f, 2f), seen)
    }

    @Test
    fun `no block for the limit is a lost input`() = runTest {
        val blocks = InputBlocks(maxQueuedSamples = 100)
        val start = testScheduler.currentTime
        val lost = assertFailsWith<MicUnavailableException> { blocks.forEach(LIMIT_MS, stalled) { } }
        assertEquals(MicUnavailableReason.READ_FAILED, lost.reason)
        assertEquals(LIMIT_MS, testScheduler.currentTime - start)
    }

    @Test
    fun `blocks arriving in time are no stall`() = runTest {
        val blocks = InputBlocks(maxQueuedSamples = 100)
        launch {
            repeat(50) {
                delay(BLOCK_EVERY_MS)
                blocks.offer(FloatArray(4))
            }
            blocks.close()
        }
        var count = 0
        blocks.forEach(LIMIT_MS, stalled) { count++ }
        assertEquals(50, count)
    }

    @Test
    fun `a busy action is no stall and what arrived meanwhile is taken at once`() = runTest {
        val blocks = InputBlocks(maxQueuedSamples = 1_000)
        launch {
            repeat(80) {
                delay(BLOCK_EVERY_MS)
                blocks.offer(FloatArray(4))
            }
            blocks.close()
        }
        var count = 0
        blocks.forEach(LIMIT_MS, stalled) {
            // a take being finished holds the analysis for longer than the limit
            if (count++ == 0) delay(5 * LIMIT_MS / 2)
        }
        assertEquals(80, count)
    }

    @Test
    fun `an input that stops while the action is busy is found after the limit`() = runTest {
        val blocks = InputBlocks(maxQueuedSamples = 1_000)
        launch {
            repeat(10) {
                delay(BLOCK_EVERY_MS)
                blocks.offer(FloatArray(4))
            }
            // and nothing more: the engine stopped
        }
        val start = testScheduler.currentTime
        var count = 0
        assertFailsWith<MicUnavailableException> {
            blocks.forEach(LIMIT_MS, stalled) { if (count++ == 0) delay(BUSY_MS) }
        }
        assertEquals(10, count, "what came before the input stopped is all taken")
        assertEquals(BLOCK_EVERY_MS + BUSY_MS + LIMIT_MS, testScheduler.currentTime - start)
    }

    @Test
    fun `a failure comes after what was queued before it`() = runTest {
        val blocks = InputBlocks(maxQueuedSamples = 100)
        val cause = MicUnavailableException(MicUnavailableReason.READ_FAILED, "the engine stopped")
        blocks.offer(FloatArray(4))
        blocks.offer(FloatArray(4))
        blocks.fail(cause)
        assertTrue(blocks.offer(FloatArray(4)), "a block after the end is dropped quietly")
        var count = 0
        val thrown = assertFailsWith<MicUnavailableException> { blocks.forEach(LIMIT_MS, stalled) { count++ } }
        assertSame(cause, thrown)
        assertEquals(2, count)
    }

    @Test
    fun `more sound waiting than the limit is refused once`() = runTest {
        val blocks = InputBlocks(maxQueuedSamples = 10)
        assertTrue(blocks.offer(FloatArray(6)))
        assertFalse(blocks.offer(FloatArray(6)), "the analysis fell behind")
        assertTrue(blocks.offer(FloatArray(6)), "said once: the caller has failed the input by now")
    }

    @Test
    fun `what the analysis took no longer counts against the limit`() = runTest {
        val blocks = InputBlocks(maxQueuedSamples = 10)
        launch {
            repeat(20) {
                assertTrue(blocks.offer(FloatArray(6)), "block $it")
                delay(BLOCK_EVERY_MS)
            }
            blocks.close()
        }
        var count = 0
        blocks.forEach(LIMIT_MS, stalled) { count++ }
        assertEquals(20, count)
    }

    @Test
    fun `cancellation stays cancellation and is no lost input`() = runTest {
        val blocks = InputBlocks(maxQueuedSamples = 100)
        var ended: Throwable? = null
        val listening = launch {
            try {
                blocks.forEach(LIMIT_MS, stalled) { }
            } catch (e: Throwable) {
                ended = e
                throw e
            }
        }
        advanceTimeBy(LIMIT_MS / 2)
        listening.cancel()
        listening.join()
        assertIs<CancellationException>(ended)
        assertFalse(ended is MicUnavailableException)
    }

    private companion object {
        const val LIMIT_MS = 2_000L
        const val BLOCK_EVERY_MS = 100L
        const val BUSY_MS = 3_000L
    }
}
