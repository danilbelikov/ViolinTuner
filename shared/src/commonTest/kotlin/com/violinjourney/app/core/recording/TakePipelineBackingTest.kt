package com.violinjourney.app.core.recording

import com.violinjourney.app.core.audio.FakePitchSource
import com.violinjourney.app.core.audio.FakeScenario
import com.violinjourney.app.core.audio.PitchSource
import com.violinjourney.app.core.audio.SampleClock
import com.violinjourney.app.core.audio.backing.BackingPlayback
import com.violinjourney.app.core.audio.recording.AudioTap
import com.violinjourney.app.core.audio.recording.SessionAudioFiles
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.PitchFrame
import com.violinjourney.app.core.domain.TargetMode
import com.violinjourney.app.core.domain.backing.AudioRoute
import com.violinjourney.app.core.domain.backing.BackingOutput
import com.violinjourney.app.core.domain.backing.FakeBackingRepository
import com.violinjourney.app.core.domain.backing.TakeBacking
import com.violinjourney.app.core.domain.practice.FakeRunningPracticeStore
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.session.FakeSessionRepository
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.fileName
import com.violinjourney.app.core.io.platformFile
import com.violinjourney.app.core.time.FixedWallClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import kotlinx.datetime.TimeZone

/**
 * A take under a backing starts with the backing's sound already made at the rate the microphone really opened at — never
 * made on the chain's thread, which is the one that reads the microphone (spec 5.25). At another rate the take does not
 * begin, and the screen is told which rate to make. Its shift is the two clocks plus the headphones' lag, less what the
 * output's clock already held of the lag.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TakePipelineBackingTest {
    private val sessions = FakeSessionRepository()
    private val backings = FakeBackingRepository()
    private val audioFiles = FakeAudioFiles()
    private val playback = FakePlayback()
    private val clock = FixedWallClock(Instant.parse("2026-09-26T09:00:00Z"), TimeZone.UTC)

    private class FakeAudioFiles : SessionAudioFiles {
        val created = mutableListOf<String>()

        override fun newFile(): PlatformFile = platformFile("/violin-test/take-${created.size + 1}.m4a").also { created += it.fileName }

        override fun existing(name: String): PlatformFile? = if (name in created) platformFile("/violin-test/$name") else null

        override fun delete(name: String) = Unit

        override fun deleteOrphans(referenced: Set<String>, nowEpochMs: Long, minAgeMs: Long) = Unit
    }

    /** The microphone's tap at a rate of its own, driven by the frames of its source. */
    private class RateTap(override val sampleRateHz: Int) : AudioTap {
        override var state: AudioTap.State = AudioTap.State.Idle
        var stops = 0

        override fun start(file: PlatformFile) {
            if (state == AudioTap.State.Idle) state = AudioTap.State.Starting
        }

        fun onFrame(tMs: Long) {
            if (state == AudioTap.State.Starting) state = AudioTap.State.Running(tMs)
        }

        override suspend fun stop(): Boolean {
            stops++
            val wasRunning = state is AudioTap.State.Running
            state = AudioTap.State.Idle
            return wasRunning
        }
    }

    /** Its first frame left the output 35 ms after the take's clock says the take began ([takeStartNanos]). */
    private class FakePlayback : BackingPlayback {
        override val position = MutableStateFlow<Long?>(null)
        override var startNanos: Long? = null
        override var includedLatencyMs = 0
        var started: Pair<PlatformFile, Int>? = null

        override fun start(pcm: PlatformFile, sampleRate: Int) {
            started = pcm to sampleRate
            startNanos = TAKE_START_NANOS + 35 * NANOS_PER_MS
        }

        override fun stop(): Long = 1_000
    }

    private fun TestScope.takes(tap: RateTap, sampleClock: SampleClock? = null): TakePipeline {
        val delegate = FakePitchSource(FakeScenario.IN_TUNE, timeSource = testTimeSource)
        val source = object : PitchSource {
            override val requiresMicPermission = false
            override val audioTap: AudioTap = tap
            override val clock: SampleClock? = sampleClock

            override fun frames(config: IntonationConfig): Flow<PitchFrame> = delegate.frames(config).onEach { tap.onFrame(it.tMs) }
        }
        return TakePipeline(
            source, sessions, audioFiles, FakeRunningPracticeStore(), PracticeConfig(), clock, StandardTestDispatcher(testScheduler),
            backings = backings, backingPlaybackFactory = { playback },
        )
    }

    private fun TestScope.advance(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    private suspend fun plan(
        made: Map<Int, PlatformFile>,
        asked: MutableList<Int>,
        route: AudioRoute = AudioRoute(BackingOutput.WIRED, "Jack"),
        latencyMs: Int = 0,
    ): TakePipeline.BackingPlan {
        val backing = backings.backing()
        val id = backings.add(backing)
        return TakePipeline.BackingPlan(
            backing = backing.copy(id = id),
            ready = { rate -> made[rate] },
            route = route,
            latencyMs = latencyMs,
            notReady = { rate -> asked += rate },
        )
    }

    @Test
    fun `at a rate the backing is not made for the take does not begin and the screen is told the rate`() = runTest {
        val tap = RateTap(44_100)
        val takes = takes(tap)
        val asked = mutableListOf<Int>()
        takes.backingPlan = plan(mapOf(48_000 to platformFile("/violin-test/pcm-48000")), asked)
        val chain = launch { takes.run(IntonationConfig(), pieceId = 1, targetMode = { TargetMode.Chromatic }, unavailable = Unit) { _, _ -> }.collect {} }
        advance(500)

        takes.recordingRequested.value = true
        advance(3_000)
        assertEquals(listOf(44_100), asked)
        assertFalse(takes.recordingRequested.value, "the wish to record is dropped")
        assertNull(playback.started, "nothing plays")
        assertEquals(1, tap.stops, "the sound the tap had begun is let go")
        assertTrue(sessions.saved.isEmpty())
        assertTrue(backings.takeBackings.value.isEmpty())
        chain.cancel()
    }

    @Test
    fun `at the rate it is made for the take begins with the backing`() = runTest {
        val tap = RateTap(44_100)
        val takes = takes(tap)
        val asked = mutableListOf<Int>()
        val pcm = platformFile("/violin-test/pcm-44100")
        takes.backingPlan = plan(mapOf(44_100 to pcm), asked)
        val chain = launch { takes.run(IntonationConfig(), pieceId = 1, targetMode = { TargetMode.Chromatic }, unavailable = Unit) { _, _ -> }.collect {} }
        advance(500)

        takes.recordingRequested.value = true
        advance(3_000)
        assertTrue(asked.isEmpty())
        assertEquals(pcm to 44_100, playback.started)
        takes.recordingRequested.value = false
        advance(100)
        assertEquals(1, sessions.saved.size)
        assertEquals(1, backings.takeBackings.value.size)
        chain.cancel()
    }

    /** A take in wireless headphones (a guess of 200 ms in all) whose output's clock held [held] ms of their lag; [sampleClock] — the take's. */
    private suspend fun TestScope.wirelessTake(held: Int, sampleClock: SampleClock?): TakeBacking {
        val tap = RateTap(48_000)
        val takes = takes(tap, sampleClock)
        playback.includedLatencyMs = held
        val pcm = platformFile("/violin-test/pcm-48000")
        takes.backingPlan = plan(mapOf(48_000 to pcm), mutableListOf(), AudioRoute(BackingOutput.BLUETOOTH, "AirPods"), latencyMs = 200)
        val chain = launch { takes.run(IntonationConfig(), pieceId = 1, targetMode = { TargetMode.Chromatic }, unavailable = Unit) { _, _ -> }.collect {} }
        advance(500)
        takes.recordingRequested.value = true
        advance(3_000)
        takes.recordingRequested.value = false
        advance(100)
        chain.cancel()
        return backings.takeBackings.value.single()
    }

    @Test
    fun `the headphones' lag the output's clock already held is not added again`() = runTest {
        // iOS: the output latency of the session, which may hold the Bluetooth link, is in the backing's start
        val take = wirelessTake(held = 160, sampleClock = SampleClock { TAKE_START_NANOS })
        assertEquals(40, take.latencyMs, "only what the clock did not hold")
        assertEquals(35 + 40, take.offsetMs)
        assertEquals(take.offsetMs, take.recordedOffsetMs)
    }

    @Test
    fun `an output clock that holds none of the lag gets the whole guess on top`() = runTest {
        // Android: AudioTrack's timestamps leave a Bluetooth link out
        val take = wirelessTake(held = 0, sampleClock = SampleClock { TAKE_START_NANOS })
        assertEquals(200, take.latencyMs)
        assertEquals(35 + 200, take.offsetMs)
    }

    @Test
    fun `without the take's clock the whole guess is the shift`() = runTest {
        // nothing of the output's latency is in a shift that has no clocks to hold it
        val take = wirelessTake(held = 160, sampleClock = null)
        assertEquals(200, take.latencyMs)
        assertEquals(200, take.offsetMs)
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
        const val TAKE_START_NANOS = 5_000 * NANOS_PER_MS
    }
}
