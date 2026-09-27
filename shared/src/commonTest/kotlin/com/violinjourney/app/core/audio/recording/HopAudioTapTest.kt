package com.violinjourney.app.core.audio.recording

import com.violinjourney.app.core.analytics.ErrorGroup
import com.violinjourney.app.core.analytics.FakeAnalytics
import com.violinjourney.app.core.analytics.NoOpAnalytics
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.platformFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** The tap of a take on the audio thread's hops — on the JVM and on iOS, where its lock is an NSRecursiveLock. */
@OptIn(ExperimentalCoroutinesApi::class)
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
    private val file = platformFile("take.m4a")
    private var encoder = FakeEncoder()
    private var created = mutableListOf<Pair<PlatformFile, Int>>()

    private val analytics = FakeAnalytics()

    private fun TestScope.tap(failing: Boolean = false) = HopAudioTap(
        encoderFactory = { file, rate ->
            if (failing) error("no codec")
            created += file to rate
            encoder
        },
        finishDispatcher = StandardTestDispatcher(testScheduler),
        analytics = analytics,
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
        tap.start(platformFile("other.m4a"))
        tap.onHop(hop, 512, 11, 48_000)
        assertEquals(1, created.size)
        assertEquals(AudioTap.State.Running(0), tap.state)
    }

    @Test
    fun `no encoder on this device means a failed take and not a crash — and it is told once`() = runTest {
        val tap = tap(failing = true)
        tap.start(file)
        tap.onHop(hop, 512, 0, 48_000)
        tap.onHop(hop, 512, 11, 48_000) // the take has failed: nothing is made or told again
        assertEquals(AudioTap.State.Failed, tap.state)
        assertEquals(listOf(ErrorGroup.MEDIA), analytics.errors.map { it.first })
        assertFalse(tap.stop())
        assertEquals(AudioTap.State.Idle, tap.state)
    }

    @Test
    fun `a stop while the stream is still closing the file waits for it and keeps the take`() = runTest {
        val closing = CompletableDeferred<Unit>()
        val mayFinish = CompletableDeferred<Unit>()
        encoder = object : FakeEncoder() {
            override fun finish(): Boolean {
                closing.complete(Unit)
                // the encoder's own thread, joined: blocking, as it is on the audio thread
                runBlocking { withTimeout(5.seconds) { mayFinish.await() } }
                return super.finish()
            }
        }
        val tap = tap()
        tap.start(file)
        tap.onHop(hop, 512, 0, 48_000)
        // real threads, not the test's: the audio thread going away closes the file itself, and that takes a moment
        withContext(Dispatchers.Default) {
            val stream = launch { tap.onStreamEnded() }
            withTimeout(5.seconds) { closing.await() }

            // the chain stops the take on a thread of its own meanwhile
            val stopping = async { tap.stop() }
            delay(100.milliseconds)
            assertFalse(stopping.isCompleted, "the stop waits for the file to be closed")
            mayFinish.complete(Unit)
            stream.join()

            assertEquals(true, withTimeout(5.seconds) { stopping.await() }, "the file the stream closed is a take")
        }
        assertEquals(1, encoder.finished)
        assertEquals(AudioTap.State.Idle, tap.state)
    }

    @Test
    fun `stop from a caller cancelled meanwhile still closes the encoder`() = runTest {
        // a dispatcher of its own: the closing really moves off the caller's, as it does onto the encoder's thread
        val tap = HopAudioTap(encoderFactory = { _, _ -> encoder }, finishDispatcher = StandardTestDispatcher(testScheduler), analytics = NoOpAnalytics())
        tap.start(file)
        tap.onHop(hop, 512, hopStartTMs = 0, sampleRateHz = 48_000)

        val stopping = launch(start = CoroutineStart.UNDISPATCHED) { tap.stop() }
        assertEquals(AudioTap.State.Idle, tap.state, "the encoder is out of the tap")
        assertEquals(0, encoder.finished, "its closing waits for the finishing dispatcher")
        stopping.cancel()
        advanceUntilIdle()

        assertEquals(1, encoder.finished, "the file is closed, and the encoder's thread with it")
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
    fun `the stream ending closes the file and stop still reports the take`() = runTest {
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
        tap.start(platformFile("second.m4a"))
        tap.onHop(hop, 512, 5_000, 48_000)
        assertEquals(AudioTap.State.Running(5_000), tap.state)
        assertTrue(tap.stop())
    }
}
