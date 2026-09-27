package com.violinjourney.app.core.audio.recording

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HopAudioTapTest {
    private open class FakeEncoder(var accept: Boolean = true, val finishes: Boolean = true) : PcmEncoder {
        var samples = 0
        var finished = 0
        override fun offer(hop: ShortArray, count: Int): Boolean {
            if (accept) samples += count
            return accept
        }

        override fun finish(): Boolean {
            finished++
            return finishes
        }
    }

    private val hop = ShortArray(512)
    private val file = File("take.m4a")
    private var encoder = FakeEncoder()
    private var created = mutableListOf<Pair<File, Int>>()

    private fun TestScope.tap(failing: Boolean = false) = HopAudioTap(
        encoderFactory = { file, rate ->
            if (failing) error("no codec")
            created += file to rate
            encoder
        },
        finishDispatcher = StandardTestDispatcher(testScheduler),
    )

    @Test
    fun `idle tap ignores the stream`() = runTest {
        val tap = tap()
        tap.onHop(hop, 512, hopStartTMs = 0, sampleRateHz = 48_000)
        assertEquals(AudioTap.State.Idle, tap.state)
        assertTrue(created.isEmpty())
        assertFalse(tap.stop())
    }

    @Test
    fun `take starts with the next hop and knows the time of its first sample`() = runTest {
        val tap = tap()
        tap.start(file)
        assertEquals(AudioTap.State.Starting, tap.state)
        tap.onHop(hop, 512, hopStartTMs = 1_066, sampleRateHz = 48_000)
        tap.onHop(hop, 300, hopStartTMs = 1_077, sampleRateHz = 48_000)
        assertEquals(AudioTap.State.Running(startTMs = 1_066), tap.state)
        assertEquals(listOf(file to 48_000), created)
        assertEquals(812, encoder.samples)

        assertTrue(tap.stop())
        assertEquals(1, encoder.finished)
        assertEquals(AudioTap.State.Idle, tap.state)
    }

    @Test
    fun `a second start while running changes nothing`() = runTest {
        val tap = tap()
        tap.start(file)
        tap.onHop(hop, 512, 0, 48_000)
        tap.start(File("other.m4a"))
        tap.onHop(hop, 512, 11, 48_000)
        assertEquals(1, created.size)
        assertEquals(AudioTap.State.Running(0), tap.state)
    }

    @Test
    fun `no encoder on this device means a failed take, not a crash`() = runTest {
        val tap = tap(failing = true)
        tap.start(file)
        tap.onHop(hop, 512, 0, 48_000)
        assertEquals(AudioTap.State.Failed, tap.state)
        assertFalse(tap.stop())
        assertEquals(AudioTap.State.Idle, tap.state)
    }

    @Test
    fun `a stop while the stream is still closing the file waits for it and keeps the take`() = runTest {
        val closing = CountDownLatch(1)
        val mayFinish = CountDownLatch(1)
        encoder = object : FakeEncoder() {
            override fun finish(): Boolean {
                closing.countDown()
                mayFinish.await(5, TimeUnit.SECONDS)
                return super.finish()
            }
        }
        val tap = tap()
        tap.start(file)
        tap.onHop(hop, 512, 0, 48_000)
        // the audio thread going away: it closes the file itself, and that takes a moment
        val stream = Thread { tap.onStreamEnded() }.apply { start() }
        assertTrue(closing.await(5, TimeUnit.SECONDS))

        // the chain stops the take on a thread of its own meanwhile
        val answer = AtomicReference<Boolean?>(null)
        val stopping = Thread { answer.set(runBlocking { tap.stop() }) }.apply { start() }
        Thread.sleep(100)
        assertNull("the stop waits for the file to be closed", answer.get())
        mayFinish.countDown()
        stream.join()
        stopping.join(5_000)

        assertEquals("the file the stream closed is a take", true, answer.get())
        assertEquals(1, encoder.finished)
        assertEquals(AudioTap.State.Idle, tap.state)
    }

    @Test
    fun `an encoder that falls behind fails the take and is still closed`() = runTest {
        val tap = tap()
        tap.start(file)
        tap.onHop(hop, 512, 0, 48_000)
        encoder.accept = false
        tap.onHop(hop, 512, 11, 48_000)
        assertEquals(AudioTap.State.Failed, tap.state)
        tap.onHop(hop, 512, 22, 48_000) // not fed any more
        assertEquals(512, encoder.samples)
        assertFalse(tap.stop())
        assertEquals(1, encoder.finished)
    }

    @Test
    fun `the stream ending closes the file, and stop still reports the take`() = runTest {
        val tap = tap()
        tap.start(file)
        tap.onHop(hop, 512, 0, 48_000)
        tap.onStreamEnded()
        assertEquals(1, encoder.finished)
        assertTrue(tap.stop())
        assertEquals(1, encoder.finished) // not finished twice
        assertEquals(AudioTap.State.Idle, tap.state)
    }

    @Test
    fun `a stream that ends before the take began leaves no take`() = runTest {
        val tap = tap()
        tap.start(file)
        tap.onStreamEnded()
        assertEquals(AudioTap.State.Failed, tap.state)
        assertFalse(tap.stop())
    }

    @Test
    fun `a file the encoder could not complete is not a take`() = runTest {
        encoder = FakeEncoder(finishes = false)
        val tap = tap()
        tap.start(file)
        tap.onHop(hop, 512, 0, 48_000)
        assertFalse(tap.stop())
    }

    @Test
    fun `the tap can be used again after a take`() = runTest {
        val tap = tap()
        tap.start(file)
        tap.onHop(hop, 512, 0, 48_000)
        tap.stop()
        encoder = FakeEncoder()
        tap.start(File("second.m4a"))
        tap.onHop(hop, 512, 5_000, 48_000)
        assertEquals(AudioTap.State.Running(5_000), tap.state)
        assertTrue(tap.stop())
    }
}
