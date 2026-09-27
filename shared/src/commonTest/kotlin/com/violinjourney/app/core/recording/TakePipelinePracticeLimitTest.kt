package com.violinjourney.app.core.recording

import com.violinjourney.app.core.audio.FakePitchSource
import com.violinjourney.app.core.audio.FakeScenario
import com.violinjourney.app.core.audio.recording.SessionAudioFiles
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.TargetMode
import com.violinjourney.app.core.domain.journey.FakePracticeNotesStore
import com.violinjourney.app.core.domain.journey.JourneyConfig
import com.violinjourney.app.core.domain.journey.NoteCount
import com.violinjourney.app.core.domain.practice.FakeRunningPracticeStore
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.session.FakeSessionRepository
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.time.WallClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import kotlinx.datetime.TimeZone

/**
 * A practice reaches its twelve hours while the violin sounds (spec 3.12): it has ended by itself there. What was heard
 * before the limit is its own — the note still sounding is written out at the limit, not thrown away — and what is
 * heard after it is neither its sound nor its notes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TakePipelinePracticeLimitTest {
    /** Wall time that follows the virtual time of the test, from [startMs]. */
    private class TestClock(private val scope: TestScope, private val startMs: Long) : WallClock {
        override val zone: TimeZone = TimeZone.UTC

        override fun instant(): Instant = Instant.fromEpochMilliseconds(startMs + scope.testScheduler.currentTime)
    }

    /** The chain here records nothing: no file is ever asked for. */
    private object NoAudioFiles : SessionAudioFiles {
        override fun newFile(): PlatformFile = error("nothing is recorded here")

        override fun existing(name: String): PlatformFile? = null

        override fun delete(name: String) = Unit

        override fun deleteOrphans(referenced: Set<String>, nowEpochMs: Long, minAgeMs: Long) = Unit
    }

    private fun TestScope.advance(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun `the note sounding at the limit is the practice's and nothing after it is`() = runTest {
        // a mark every second, so that one past the limit could not hide behind the interval
        val config = PracticeConfig(soundMarkIntervalMs = 1_000)
        val nowMs = Instant.parse("2026-09-27T09:00:00Z").toEpochMilliseconds()
        // three seconds of the test before the practice reaches its limit
        val practiceStart = nowMs - config.maxPracticeMs + 3_000
        val limit = practiceStart + config.maxPracticeMs
        val running = FakeRunningPracticeStore().apply { startIfIdle(practiceStart) }
        val notes = FakePracticeNotesStore()
        val takes = testTakePipeline(
            pitchSource = FakePitchSource(FakeScenario.IN_TUNE, timeSource = testTimeSource),
            sessionRepository = FakeSessionRepository(),
            audioFiles = NoAudioFiles,
            runningPractice = running,
            practiceConfig = config,
            clock = TestClock(this, nowMs),
            dispatcher = StandardTestDispatcher(testScheduler),
            practiceNotes = notes,
            // the periodic flush does not come within the test: only the limit writes the notes out
            journeyConfig = JourneyConfig(notesFlushMs = 60_000),
        )
        backgroundScope.launch { takes.watchPractice() }
        backgroundScope.launch {
            takes.run(IntonationConfig(), pieceId = null, targetMode = { TargetMode.Chromatic }, unavailable = Unit) { _, _ -> }.collect {}
        }

        // one long A4: while it sounds, it is not yet a counted note
        advance(2_500)
        assertEquals(NoteCount.ZERO, notes.countFor(practiceStart), "a note is counted when it ends")
        assertNotNull(running.running.value?.lastSoundEpochMs, "the violin sounded within the practice")

        advance(1_000)
        val atLimit = notes.countFor(practiceStart)
        assertEquals(1, atLimit.played, "the note heard up to the limit is the practice's")

        advance(5_000)
        assertEquals(atLimit, notes.countFor(practiceStart), "nothing heard after the limit is counted")
        val lastSound = running.running.value?.lastSoundEpochMs
        assertNotNull(lastSound)
        assertTrue(lastSound <= limit, "the last sound is within the practice: ${lastSound - limit} ms past its limit")
        assertTrue(lastSound > limit - 2 * config.soundMarkIntervalMs, "marked while it ran, up to its end")
    }
}
