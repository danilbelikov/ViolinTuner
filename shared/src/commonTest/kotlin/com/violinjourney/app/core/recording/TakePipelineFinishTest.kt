package com.violinjourney.app.core.recording

import com.violinjourney.app.core.audio.FakePitchSource
import com.violinjourney.app.core.audio.FakeScenario
import com.violinjourney.app.core.audio.PitchSource
import com.violinjourney.app.core.audio.recording.AudioTap
import com.violinjourney.app.core.audio.recording.SessionAudioFiles
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.PitchFrame
import com.violinjourney.app.core.domain.TargetMode
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
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
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
 * A take the player stopped is seen through to the end even when its chain is cancelled meanwhile — the screen went
 * away while the sound was being closed or the video made (spec 3.9, 3.32).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TakePipelineFinishTest {
    private val sessions = FakeSessionRepository()
    private val audioFiles = FakeAudioFiles()
    private val watch = RecordingWatch()
    private val clock = FixedWallClock(Instant.parse("2026-09-26T09:00:00Z"), TimeZone.UTC)

    /** Files by name alone: nothing is written, the chain only hands names on. */
    private class FakeAudioFiles : SessionAudioFiles {
        val created = mutableListOf<String>()
        val deleted = mutableListOf<String>()

        override fun newFile(): PlatformFile = platformFile("/violin-test/take-${created.size + 1}.m4a").also { created += it.fileName }

        override fun existing(name: String): PlatformFile? = if (name in created) platformFile("/violin-test/$name") else null

        override fun delete(name: String) {
            deleted += name
        }

        override fun deleteOrphans(referenced: Set<String>, nowEpochMs: Long, minAgeMs: Long) = Unit
    }

    /** A tap driven by the frames of its source, like the microphone one; its stop may be held at [stopGate]. */
    private class FakeAudioTap(private val stopGate: CompletableDeferred<Unit>? = null) : AudioTap {
        override var state: AudioTap.State = AudioTap.State.Idle
        var stopping = false

        override fun start(file: PlatformFile) {
            if (state == AudioTap.State.Idle) state = AudioTap.State.Starting
        }

        fun onFrame(tMs: Long) {
            if (state == AudioTap.State.Starting) state = AudioTap.State.Running(tMs)
        }

        override suspend fun stop(): Boolean {
            val wasRunning = state is AudioTap.State.Running
            state = AudioTap.State.Idle
            stopping = true
            stopGate?.await()
            return wasRunning
        }
    }

    /** The app's camera as the chain sees it: making the video, or throwing the picture away, waits at [gate]. */
    private class FakeVideoHook(private val gate: CompletableDeferred<Unit>) : TakePipeline.VideoHook {
        var finishing = false
        var discarding = false
        var discarded = 0

        override fun onRecordingStarted(recordStartNanos: Long?) = Unit

        override suspend fun onRecordingFinished(audio: PlatformFile, recordStartNanos: Long?): String {
            finishing = true
            gate.await()
            return "video-1.mp4"
        }

        override suspend fun onRecordingDiscarded() {
            discarding = true
            gate.await()
            discarded++
        }
    }

    private fun TestScope.takes(tap: FakeAudioTap, scenario: FakeScenario = FakeScenario.IN_TUNE): TakePipeline {
        val delegate = FakePitchSource(scenario, timeSource = testTimeSource)
        val source = object : PitchSource {
            override val requiresMicPermission = false
            override val audioTap: AudioTap = tap

            override fun frames(config: IntonationConfig): Flow<PitchFrame> = delegate.frames(config).onEach { tap.onFrame(it.tMs) }
        }
        return testTakePipeline(
            source, sessions, audioFiles, FakeRunningPracticeStore(), PracticeConfig(), clock, StandardTestDispatcher(testScheduler), watch,
        )
    }

    /** Records for [millis] and stops it as the player does; the chain goes on, as it does on the screen. */
    private fun TestScope.recordAndStop(takes: TakePipeline, millis: Long): Job {
        val chain = launch {
            takes.run(IntonationConfig(), pieceId = PIECE_ID, targetMode = { TargetMode.Chromatic }, unavailable = Unit) { _, _ -> }.collect {}
        }
        advance(500)
        takes.recordingRequested.value = true
        advance(millis)
        takes.recordingRequested.value = false
        advance(50)
        return chain
    }

    private fun TestScope.advance(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun `a take stopped by the player is kept when its screen goes while the video is made`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val hook = FakeVideoHook(gate)
        val takes = takes(FakeAudioTap()).apply { videoHook = hook }

        val chain = recordAndStop(takes, 3_000)
        assertTrue(hook.finishing, "the video is being made")
        assertTrue(watch.recording.value, "a copy of the data still waits for the take")
        chain.cancel()
        runCurrent()
        gate.complete(Unit)
        chain.join()

        assertEquals(1, sessions.saved.size, "the take is kept")
        val session = sessions.saved.single()
        assertEquals("video-1.mp4", session.videoPath)
        assertEquals("video-1.mp4", session.audioPath)
        assertEquals(PIECE_ID, session.pieceId)
        assertEquals(listOf("take-1.m4a"), audioFiles.deleted, "the sound is in the video now")
        assertFalse(watch.recording.value, "the take is in the database")
    }

    @Test
    fun `a take is kept when its screen goes while its sound is being closed`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val tap = FakeAudioTap(stopGate = gate)
        val takes = takes(tap)

        val chain = recordAndStop(takes, 3_000)
        assertTrue(tap.stopping, "the sound is being closed")
        chain.cancel()
        runCurrent()
        gate.complete(Unit)
        chain.join()

        assertEquals(1, sessions.saved.size, "the take is kept")
        assertEquals("take-1.m4a", sessions.saved.single().audioPath)
    }

    @Test
    fun `a take without notes still throws its picture away when its screen goes`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val hook = FakeVideoHook(gate)
        val takes = takes(FakeAudioTap(), FakeScenario.SILENCE).apply { videoHook = hook }

        val chain = recordAndStop(takes, 3_000)
        assertTrue(hook.discarding, "the picture is being thrown away")
        chain.cancel()
        runCurrent()
        gate.complete(Unit)
        chain.join()

        assertEquals(1, hook.discarded, "no picture is left behind")
        assertTrue(sessions.saved.isEmpty())
    }

    private companion object {
        const val PIECE_ID = 7L
    }
}
