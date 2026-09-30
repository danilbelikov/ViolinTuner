package com.violinjourney.app.feature.live

import com.violinjourney.app.core.analytics.ErrorGroup
import com.violinjourney.app.core.analytics.FakeAnalytics
import com.violinjourney.app.core.analytics.NoOpAnalytics
import com.violinjourney.app.core.audio.FakePitchSource
import com.violinjourney.app.core.audio.FakeScenario
import com.violinjourney.app.core.audio.MicUnavailableException
import com.violinjourney.app.core.audio.MicUnavailableReason
import com.violinjourney.app.core.audio.PitchSource
import com.violinjourney.app.core.audio.recording.AudioTap
import com.violinjourney.app.core.audio.recording.SessionAudioFiles
import com.violinjourney.app.core.domain.Direction
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.PitchFrame
import com.violinjourney.app.core.domain.TolerancePreset
import com.violinjourney.app.core.domain.ViolinString
import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.domain.journey.Arrival
import com.violinjourney.app.core.domain.journey.FakeJourneyRepository
import com.violinjourney.app.core.domain.journey.FakePracticeNotesStore
import com.violinjourney.app.core.domain.journey.JourneyConfig
import com.violinjourney.app.core.domain.journey.JourneyProgress
import com.violinjourney.app.core.domain.journey.JourneyRoute
import com.violinjourney.app.core.domain.journey.NoteCount
import com.violinjourney.app.core.domain.practice.FakeRunningPracticeStore
import com.violinjourney.app.core.domain.practice.FinishPracticeAsk
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.practice.RunningPractice
import com.violinjourney.app.core.domain.session.FakeSessionRepository
import com.violinjourney.app.core.domain.venue.FakeVenueStore
import com.violinjourney.app.core.domain.venue.Venue
import com.violinjourney.app.core.domain.venue.VenueRules
import com.violinjourney.app.core.domain.venue.Venues
import com.violinjourney.app.core.recording.testTakePipeline
import com.violinjourney.app.core.settings.FakeSettingsRepository
import com.violinjourney.app.core.settings.SettingsConfigSource
import com.violinjourney.app.core.time.FixedWallClock
import com.violinjourney.app.core.time.WallClock
import java.io.File
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.testTimeSource
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(mainDispatcher)

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        audioFiles.deleteAll()
    }

    private val settings = FakeSettingsRepository()
    private val sessions = FakeSessionRepository()
    private val startedAt = Instant.parse("2026-09-17T09:00:00Z")
    private val audioFiles = FakeAudioFiles()
    private val practice = FakeRunningPracticeStore()
    private val finishAsk = FinishPracticeAsk()
    private val practiceNotes = FakePracticeNotesStore()
    private val journey = FakeJourneyRepository()
    private val venueStore = FakeVenueStore()
    private val analytics = FakeAnalytics()

    private class FakeAudioFiles : SessionAudioFiles {
        val created = mutableListOf<File>()
        private val directory = File(System.getProperty("java.io.tmpdir"), "violin-test-${System.nanoTime()}").apply { mkdirs() }
        override fun newFile(): File = File(directory, "take-${created.size + 1}.m4a").also {
            it.writeText("audio")
            created += it
        }

        override fun existing(name: String): File? = File(directory, name).takeIf(File::isFile)
        override fun delete(name: String) { File(directory, name).delete() }
        override fun deleteOrphans(referenced: Set<String>, nowEpochMs: Long, minAgeMs: Long) = Unit

        /** The folder of this test's takes goes with the test: one left per test filled the machine's temporary folder. */
        fun deleteAll() {
            directory.deleteRecursively()
        }
    }

    /** A tap driven by the frames of the source it sits on, like the microphone one. */
    private class FakeAudioTap(private val failsToStart: Boolean = false, private val completes: Boolean = true) : AudioTap {
        override var state: AudioTap.State = AudioTap.State.Idle
        var stops = 0
        override fun start(file: File) { if (state == AudioTap.State.Idle) state = AudioTap.State.Starting }
        fun onFrame(tMs: Long) {
            if (state == AudioTap.State.Starting) state = if (failsToStart) AudioTap.State.Failed else AudioTap.State.Running(tMs)
        }

        override suspend fun stop(): Boolean {
            val wasRunning = state is AudioTap.State.Running
            state = AudioTap.State.Idle
            stops++
            return wasRunning && completes
        }
    }

    private fun TestScope.sourceWithSound(tap: FakeAudioTap, scenario: FakeScenario = FakeScenario.IN_TUNE): PitchSource {
        val delegate = fakeSource(scenario)
        return object : PitchSource {
            override val requiresMicPermission = false
            override val audioTap = tap
            override fun frames(config: IntonationConfig): Flow<PitchFrame> = delegate.frames(config).onEach { tap.onFrame(it.tMs) }
        }
    }

    private fun TestScope.viewModel(
        source: PitchSource,
        base: IntonationConfig = IntonationConfig(),
        initialMode: LiveMode = LiveMode.PLAY,
    ): LiveViewModel {
        val clock = FixedWallClock(startedAt, TimeZone.UTC)
        val takes = testTakePipeline(
            source, sessions, audioFiles, practice, PracticeConfig(), clock, StandardTestDispatcher(testScheduler),
            practiceNotes = practiceNotes, journeyConfig = JourneyConfig(notesFlushMs = 1_000), analytics = analytics,
        )
        return LiveViewModel(
            takes, SettingsConfigSource(base, settings), practice, clock, Venues(venueStore, journey), finishAsk = finishAsk,
            nanos = { testScheduler.currentTime * NANOS_PER_MS }, analytics = NoOpAnalytics(), practiceConfig = PracticeConfig(),
            initialMode = initialMode,
        )
    }

    private fun TestScope.advance(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    private fun TestScope.fakeSource(scenario: FakeScenario) =
        FakePitchSource(scenario, timeSource = testTimeSource)

    private fun TestScope.viewModel(scenario: FakeScenario) = viewModel(fakeSource(scenario))

    /** Subscribes like the screen does — to the words and to the gauge — and lets [millis] of signal through. */
    private fun TestScope.observe(viewModel: LiveViewModel, millis: Long): Job {
        val job = backgroundScope.launch {
            launch { viewModel.gauge.collect {} }
            viewModel.state.collect {}
        }
        advanceTimeBy(millis)
        runCurrent()
        return job
    }

    @Test
    fun `starts silent in play mode with the scale from config`() = runTest {
        val state = viewModel(FakeScenario.IN_TUNE).state.value
        assertEquals(LiveMode.PLAY, state.mode)
        assertEquals(LiveSignal.Silence, state.signal)
        assertEquals(ScaleSpec(rangeCents = 50.0, toleranceCents = 8.0), state.scale)
    }

    /** `-liveMode tuning` of the iOS owner's app (spec 3.36.6): the screenshot of «Настройка» needs it from the first frame. */
    @Test
    fun `opens in the mode it is asked for, and records nothing there`() = runTest {
        val viewModel = viewModel(fakeSource(FakeScenario.IN_TUNE), initialMode = LiveMode.TUNING)
        assertEquals(LiveMode.TUNING, viewModel.state.value.mode)
        assertFalse(viewModel.state.value.canRecord)
        observe(viewModel, 500)
        assertEquals(LiveMode.TUNING, viewModel.state.value.mode)
        assertEquals(ViolinString.A4, viewModel.state.value.tuning.targetString)
        assertFalse(viewModel.state.value.canRecord)
    }

    @Test
    fun `in tune scenario shows the note with a filling ring`() = runTest {
        val viewModel = viewModel(FakeScenario.IN_TUNE)
        observe(viewModel, 1_200)
        val signal = viewModel.state.value.signal as LiveSignal.Sounding
        assertEquals("A4", signal.note.name)
        assertEquals(Zone.IN_TUNE, signal.zone)
        // the hold is 0.4..0.6 of the way: the glow the ring moves to is 0.6 + 0.4 × that
        assertFalse(signal.holdComplete)
        assertTrue("glow ${viewModel.gauge.value.glowTarget}", viewModel.gauge.value.glowTarget in 0.76f..0.84f)
    }

    @Test
    fun `silence and noise scenarios map to their states`() = runTest {
        val silent = viewModel(FakeScenario.SILENCE)
        observe(silent, 1_500)
        assertEquals(LiveSignal.Silence, silent.state.value.signal)

        val noisy = viewModel(FakeScenario.NOISE)
        observe(noisy, 1_500)
        assertEquals(LiveSignal.TooNoisy, noisy.state.value.signal)
    }

    @Test
    fun `selecting tuning mode switches the mode and retargets to open strings`() = runTest {
        val viewModel = viewModel(FakeScenario.IN_TUNE)
        observe(viewModel, 500)
        viewModel.onIntent(LiveIntent.SelectMode(LiveMode.TUNING))
        runCurrent()
        assertEquals(LiveMode.TUNING, viewModel.state.value.mode)

        advanceTimeBy(50) // the engine restarted, the string has to lock again
        runCurrent()
        assertEquals(LiveSignal.Silence, viewModel.state.value.signal)
        advanceTimeBy(300)
        runCurrent()
        assertEquals("A4", (viewModel.state.value.signal as LiveSignal.Sounding).note.name)
    }

    @Test
    fun `locking a string measures against it and a second tap returns to auto`() = runTest {
        val viewModel = viewModel(FakeScenario.IN_TUNE) // plays A4
        viewModel.onIntent(LiveIntent.SelectMode(LiveMode.TUNING))
        observe(viewModel, 500)
        assertEquals(ViolinString.A4, viewModel.state.value.tuning.targetString)

        viewModel.onIntent(LiveIntent.StringClicked(ViolinString.D4))
        advanceTimeBy(500)
        runCurrent()
        val locked = viewModel.state.value
        assertEquals(ViolinString.D4, locked.tuning.lockedString)
        val signal = locked.signal as LiveSignal.Sounding
        assertEquals("D4", signal.note.name)
        assertEquals(Zone.OFF, signal.zone)
        assertEquals(Direction.SHARP, signal.direction)

        viewModel.onIntent(LiveIntent.StringClicked(ViolinString.D4))
        advanceTimeBy(500)
        runCurrent()
        assertEquals(null, viewModel.state.value.tuning.lockedString)
        assertEquals("A4", (viewModel.state.value.signal as LiveSignal.Sounding).note.name)
    }

    @Test
    fun `leaving the tuning mode drops the lock`() = runTest {
        val viewModel = viewModel(FakeScenario.IN_TUNE)
        observe(viewModel, 100)
        viewModel.onIntent(LiveIntent.SelectMode(LiveMode.TUNING))
        viewModel.onIntent(LiveIntent.StringClicked(ViolinString.E5))
        viewModel.onIntent(LiveIntent.SelectMode(LiveMode.PLAY))
        viewModel.onIntent(LiveIntent.SelectMode(LiveMode.TUNING))
        advanceTimeBy(100)
        runCurrent()
        assertEquals(null, viewModel.state.value.tuning.lockedString)
    }

    @Test
    fun `wider tolerance from the settings turns a near reading into in tune`() = runTest {
        val tenCentsSharp = object : PitchSource {
            override val requiresMicPermission = false
            override val audioTap: AudioTap? = null
            override fun frames(config: IntonationConfig): Flow<PitchFrame> = flow {
                var t = 0L
                while (true) {
                    emit(PitchFrame.pitched(t, 440.0 * Math.pow(2.0, 10.0 / 1200), 0.97, 0.2, config.a4Hz))
                    delay(10)
                    t += 10
                }
            }
        }
        val viewModel = viewModel(tenCentsSharp)
        observe(viewModel, 500)
        assertEquals(Zone.NEAR, (viewModel.state.value.signal as LiveSignal.Sounding).zone)
        assertEquals(8.0, viewModel.state.value.scale.toleranceCents, 0.0)

        settings.setTolerance(TolerancePreset.BEGINNER)
        advanceTimeBy(500)
        runCurrent()
        assertEquals(Zone.IN_TUNE, (viewModel.state.value.signal as LiveSignal.Sounding).zone)
        assertEquals(12.0, viewModel.state.value.scale.toleranceCents, 0.0)
    }

    @Test
    fun `reference pitch from the settings moves the target and the string captions`() = runTest {
        val plays442 = object : PitchSource {
            override val requiresMicPermission = false
            override val audioTap: AudioTap? = null
            override fun frames(config: IntonationConfig): Flow<PitchFrame> = flow {
                var t = 0L
                while (true) {
                    emit(PitchFrame.pitched(t, 442.0, 0.97, 0.2, config.a4Hz))
                    delay(10)
                    t += 10
                }
            }
        }
        val viewModel = viewModel(plays442)
        observe(viewModel, 500)
        assertEquals(7.85, viewModel.gauge.value.cents!!, 0.05)

        settings.setA4(442)
        advanceTimeBy(500)
        runCurrent()
        assertEquals(0.0, viewModel.gauge.value.cents!!, 0.01)
        assertEquals(442, viewModel.state.value.tuning.stringHz.getValue(ViolinString.A4))
    }

    // ---- recording (spec 3.9)

    @Test
    fun `record button starts a recording that shows its time and notes`() = runTest {
        val viewModel = viewModel(FakeScenario.IN_TUNE)
        observe(viewModel, 500)
        assertEquals(null, viewModel.state.value.recording)
        assertTrue(viewModel.state.value.canRecord)

        viewModel.onIntent(LiveIntent.RecordClicked)
        runCurrent()
        assertEquals(RecordingState(0), viewModel.state.value.recording) // shows at once
        assertEquals(0, viewModel.gauge.value.ribbon!!.pieces.size) // with no notes yet

        advance(3_000)
        val recording = viewModel.state.value.recording!!
        // whole seconds, as the timer shows them: about three seconds recorded is 0:02 or 0:03
        assertTrue("elapsed ${recording.elapsedMs}", recording.elapsedMs in 2_000..3_000 && recording.elapsedMs % 1_000 == 0L)
        assertEquals(listOf(Zone.IN_TUNE), viewModel.gauge.value.ribbon!!.pieces.map { it.zone })
        assertTrue(sessions.saved.isEmpty())
    }

    @Test
    fun `second tap saves the session and opens it`() = runTest {
        val viewModel = viewModel(FakeScenario.IN_TUNE)
        observe(viewModel, 500)
        viewModel.onIntent(LiveIntent.RecordClicked)
        advance(3_000)
        viewModel.onIntent(LiveIntent.RecordClicked)
        advance(100)

        assertEquals(null, viewModel.state.value.recording)
        val saved = sessions.saved.single()
        assertEquals(startedAt.toEpochMilliseconds(), saved.startedAtEpochMs)
        assertTrue("duration ${saved.durationMs}", saved.durationMs in 2_900..3_100)
        assertEquals(100, saved.metrics.scorePercent)
        assertEquals(LiveEffect.OpenSession(1), viewModel.effects.first())
    }

    @Test
    fun `the session keeps the settings it was recorded with`() = runTest {
        settings.setA4(442)
        settings.setTolerance(TolerancePreset.BEGINNER)
        val viewModel = viewModel(FakeScenario.IN_TUNE)
        observe(viewModel, 500)
        viewModel.onIntent(LiveIntent.RecordClicked)
        advance(2_500)
        viewModel.onIntent(LiveIntent.RecordClicked)
        advance(100)
        assertEquals(442.0, sessions.saved.single().config.a4Hz, 0.0)
        assertEquals(12.0, sessions.saved.single().config.toleranceCents, 0.0)
    }

    @Test
    fun `a take under two seconds is dropped without a word`() = runTest {
        val viewModel = viewModel(FakeScenario.IN_TUNE)
        observe(viewModel, 500)
        viewModel.onIntent(LiveIntent.RecordClicked)
        advance(1_500)
        viewModel.onIntent(LiveIntent.RecordClicked)
        advance(100)
        assertEquals(null, viewModel.state.value.recording)
        assertTrue(sessions.saved.isEmpty())
        viewModel.onIntent(LiveIntent.GrantMicClicked) // the next effect is this one: nothing was queued before
        assertEquals(LiveEffect.RequestMicPermission, viewModel.effects.first())
    }

    @Test
    fun `a take without notes is not saved and says so`() = runTest {
        val viewModel = viewModel(FakeScenario.SILENCE)
        observe(viewModel, 500)
        viewModel.onIntent(LiveIntent.RecordClicked)
        advance(3_000)
        viewModel.onIntent(LiveIntent.RecordClicked)
        advance(100)
        assertTrue(sessions.saved.isEmpty())
        assertEquals(LiveEffect.ShowNoNotesRecorded, viewModel.effects.first())
    }

    @Test
    fun `leaving Live saves the take quietly`() = runTest {
        val viewModel = viewModel(FakeScenario.IN_TUNE)
        val observer = observe(viewModel, 500)
        viewModel.onIntent(LiveIntent.RecordClicked)
        advance(3_000)

        observer.cancel()
        advance(2_500) // the source stops 2 s after the last subscriber left
        assertEquals(1, sessions.saved.size)

        observe(viewModel, 500) // back on Live: not recording, and no session screen pops up
        assertEquals(null, viewModel.state.value.recording)
        viewModel.onIntent(LiveIntent.GrantMicClicked)
        assertEquals(LiveEffect.RequestMicPermission, viewModel.effects.first())
    }

    // ---- leaving Live and coming back (spec 3.27, 5.20)

    @Test
    fun `leaving Live mid-note takes the note off show once the chain has stopped`() = runTest {
        inCremona()
        val viewModel = viewModel(FakeScenario.IN_TUNE)
        viewModel.onIntent(LiveIntent.SelectMode(LiveMode.TUNING))
        val observer = observe(viewModel, 1_200)
        assertTrue(viewModel.state.value.signal is LiveSignal.Sounding)
        assertNull("nothing has ended yet", viewModel.state.value.quietSinceNanos)

        val leftAt = testScheduler.currentTime
        observer.cancel()
        advance(2_500)
        val state = viewModel.state.value
        assertEquals(LiveSignal.Silence, state.signal)
        assertEquals("no glow, no cents, no level to come back to", LiveGauge(), viewModel.gauge.value)
        assertEquals(LiveMode.TUNING, state.mode)
        assertEquals(Venue.Hall("cremona"), state.venue)
        assertEquals("the light counts from when the screen went", leftAt * NANOS_PER_MS, state.quietSinceNanos)
    }

    @Test
    fun `a return within the stop timeout finds the note still sounding`() = runTest {
        val viewModel = viewModel(FakeScenario.IN_TUNE)
        val observer = observe(viewModel, 1_200)
        observer.cancel()
        advance(500)
        observe(viewModel, 100)
        assertTrue(viewModel.state.value.signal is LiveSignal.Sounding)
        assertNull(viewModel.state.value.quietSinceNanos)
    }

    @Test
    fun `a take left in silence is not on show after leaving`() = runTest {
        val viewModel = viewModel(FakeScenario.SILENCE)
        val observer = observe(viewModel, 500)
        viewModel.onIntent(LiveIntent.RecordClicked)
        advance(3_000)
        assertTrue(viewModel.state.value.recording != null)

        val leftAt = testScheduler.currentTime
        observer.cancel()
        advance(2_500)
        assertNull(viewModel.state.value.recording)
        assertEquals(LiveSignal.Silence, viewModel.state.value.signal)
        assertEquals(leftAt * NANOS_PER_MS, viewModel.state.value.quietSinceNanos)
    }

    @Test
    fun `the prompt for the permission stays on show after leaving`() = runTest {
        val source = CountingSource(fakeSource(FakeScenario.IN_TUNE), requiresMicPermission = true)
        val viewModel = viewModel(source)
        val observer = observe(viewModel, 100)
        viewModel.onIntent(LiveIntent.MicPermissionChanged(granted = false))
        advance(100)
        assertEquals(LiveSignal.NoMicPermission, viewModel.state.value.signal)
        observer.cancel()
        advance(2_500)
        assertEquals(LiveSignal.NoMicPermission, viewModel.state.value.signal)
    }

    @Test
    fun `the end of a note is stamped for the light`() = runTest {
        val playsASecond = object : PitchSource {
            override val requiresMicPermission = false
            override val audioTap: AudioTap? = null
            override fun frames(config: IntonationConfig): Flow<PitchFrame> = flow {
                var t = 0L
                while (true) {
                    emit(if (t < 1_000) PitchFrame.pitched(t, 440.0, 0.97, 0.2, config.a4Hz) else PitchFrame.unpitched(t, clarity = 0.0, rms = 0.0005))
                    delay(10)
                    t += 10
                }
            }
        }
        val viewModel = viewModel(playsASecond)
        observe(viewModel, 800)
        assertTrue(viewModel.state.value.signal is LiveSignal.Sounding)
        advance(1_200)
        assertEquals(LiveSignal.Silence, viewModel.state.value.signal)
        // the silence is shown 300 ms after the last sound (spec 5.1), and the light counts from there
        val quietSince = requireNotNull(viewModel.state.value.quietSinceNanos)
        assertTrue("stamped at ${quietSince / NANOS_PER_MS} ms", quietSince in 1_250 * NANOS_PER_MS..1_400 * NANOS_PER_MS)
    }

    @Test
    fun `a rotation does not interrupt the take`() = runTest {
        val viewModel = viewModel(FakeScenario.IN_TUNE)
        val observer = observe(viewModel, 500)
        viewModel.onIntent(LiveIntent.RecordClicked)
        advance(1_500)
        observer.cancel()
        advance(500) // the new activity subscribes well within the stop timeout
        observe(viewModel, 1_500)
        assertTrue(viewModel.state.value.recording!!.elapsedMs >= 3_000)
        assertTrue(sessions.saved.isEmpty())
    }

    @Test
    fun `a microphone failure ends and saves the take`() = runTest {
        val working = fakeSource(FakeScenario.IN_TUNE)
        val breaksAfterFourSeconds = object : PitchSource {
            override val requiresMicPermission = false
            override val audioTap: AudioTap? = null
            override fun frames(config: IntonationConfig): Flow<PitchFrame> = flow {
                working.frames(config).collect { frame ->
                    if (frame.tMs > 4_000) throw MicUnavailableException(MicUnavailableReason.READ_FAILED, "unplugged")
                    emit(frame)
                }
            }
        }
        val viewModel = viewModel(breaksAfterFourSeconds)
        observe(viewModel, 500)
        viewModel.onIntent(LiveIntent.RecordClicked)
        advance(4_000)
        assertEquals(LiveSignal.MicUnavailable, viewModel.state.value.signal)
        assertEquals(null, viewModel.state.value.recording)
        assertFalse(viewModel.state.value.canRecord)
        assertEquals(1, sessions.saved.size)
    }

    @Test
    fun `the limit stops and opens the session`() = runTest {
        val viewModel = viewModel(fakeSource(FakeScenario.IN_TUNE), base = IntonationConfig(maxSessionMs = 5_000))
        observe(viewModel, 500)
        viewModel.onIntent(LiveIntent.RecordClicked)
        advance(6_000)
        assertEquals(null, viewModel.state.value.recording)
        assertTrue(sessions.saved.single().durationMs in 5_000..5_100)
        assertEquals(LiveEffect.OpenSession(1), viewModel.effects.first())
    }

    @Test
    fun `no recording in tuning mode, and no mode switch while recording`() = runTest {
        val viewModel = viewModel(FakeScenario.IN_TUNE)
        observe(viewModel, 300)
        viewModel.onIntent(LiveIntent.SelectMode(LiveMode.TUNING))
        advance(300)
        assertFalse(viewModel.state.value.canRecord)
        viewModel.onIntent(LiveIntent.RecordClicked)
        advance(300)
        assertEquals(null, viewModel.state.value.recording)

        viewModel.onIntent(LiveIntent.SelectMode(LiveMode.PLAY))
        advance(300)
        viewModel.onIntent(LiveIntent.RecordClicked)
        advance(300)
        viewModel.onIntent(LiveIntent.SelectMode(LiveMode.TUNING))
        advance(300)
        assertEquals(LiveMode.PLAY, viewModel.state.value.mode)
        assertTrue(viewModel.state.value.recording != null)
    }

    // ---- recording with sound (spec 3.9, stage 11)

    @Test
    fun `a take with sound is saved with its audio file`() = runTest {
        val tap = FakeAudioTap()
        val viewModel = viewModel(sourceWithSound(tap))
        observe(viewModel, 500)
        viewModel.onIntent(LiveIntent.RecordClicked)
        advance(3_000)
        assertTrue(tap.state is AudioTap.State.Running)
        viewModel.onIntent(LiveIntent.RecordClicked)
        advance(100)

        val saved = sessions.saved.single()
        assertEquals("take-1.m4a", saved.audioPath)
        assertTrue(audioFiles.created.single().exists())
        assertEquals(1, tap.stops)
        assertEquals(AudioTap.State.Idle, tap.state)
        // the recorder waited for the sound: the session is a few frames shorter than the wish
        assertTrue("duration ${saved.durationMs}", saved.durationMs in 2_900..3_000)
    }

    @Test
    fun `without an encoder the session is saved without sound and the file is removed`() = runTest {
        val tap = FakeAudioTap(failsToStart = true)
        val viewModel = viewModel(sourceWithSound(tap))
        observe(viewModel, 500)
        viewModel.onIntent(LiveIntent.RecordClicked)
        advance(3_000)
        viewModel.onIntent(LiveIntent.RecordClicked)
        advance(100)
        assertEquals(null, sessions.saved.single().audioPath)
        assertFalse(audioFiles.created.single().exists())
        assertEquals(AudioTap.State.Idle, tap.state)
    }

    @Test
    fun `an incomplete audio file is not attached`() = runTest {
        val tap = FakeAudioTap(completes = false)
        val viewModel = viewModel(sourceWithSound(tap))
        observe(viewModel, 500)
        viewModel.onIntent(LiveIntent.RecordClicked)
        advance(3_000)
        viewModel.onIntent(LiveIntent.RecordClicked)
        advance(100)
        assertEquals(null, sessions.saved.single().audioPath)
        assertFalse(audioFiles.created.single().exists())
    }

    @Test
    fun `dropped takes leave no audio file behind`() = runTest {
        val tap = FakeAudioTap()
        val tooShort = viewModel(sourceWithSound(tap))
        observe(tooShort, 500)
        tooShort.onIntent(LiveIntent.RecordClicked)
        advance(1_000)
        tooShort.onIntent(LiveIntent.RecordClicked)
        advance(100)

        val silentTap = FakeAudioTap()
        val noNotes = viewModel(sourceWithSound(silentTap, FakeScenario.SILENCE))
        observe(noNotes, 500)
        noNotes.onIntent(LiveIntent.RecordClicked)
        advance(3_000)
        noNotes.onIntent(LiveIntent.RecordClicked)
        advance(100)

        assertTrue(sessions.saved.isEmpty())
        assertEquals(2, audioFiles.created.size)
        assertTrue(audioFiles.created.none { it.exists() })
        assertEquals(listOf(1, 1), listOf(tap.stops, silentTap.stops))
    }

    @Test
    fun `leaving Live closes the audio and keeps it with the session`() = runTest {
        val tap = FakeAudioTap()
        val viewModel = viewModel(sourceWithSound(tap))
        val observer = observe(viewModel, 500)
        viewModel.onIntent(LiveIntent.RecordClicked)
        advance(3_000)
        observer.cancel()
        advance(2_500)
        assertEquals("take-1.m4a", sessions.saved.single().audioPath)
        assertEquals(AudioTap.State.Idle, tap.state)
    }

    @Test
    fun `no recording without the microphone permission`() = runTest {
        val source = CountingSource(fakeSource(FakeScenario.IN_TUNE), requiresMicPermission = true)
        val viewModel = viewModel(source)
        observe(viewModel, 100)
        viewModel.onIntent(LiveIntent.MicPermissionChanged(granted = false))
        advance(100)
        assertFalse(viewModel.state.value.canRecord)
        viewModel.onIntent(LiveIntent.RecordClicked)
        advance(100)
        assertEquals(null, viewModel.state.value.recording)
    }

    @Test
    fun `grant button asks the route to request the permission`() = runTest {
        val viewModel = viewModel(FakeScenario.SILENCE)
        viewModel.onIntent(LiveIntent.GrantMicClicked)
        assertEquals(LiveEffect.RequestMicPermission, viewModel.effects.first())
    }

    @Test
    fun `pitch source runs only while the state is observed`() = runTest {
        val source = CountingSource(fakeSource(FakeScenario.IN_TUNE))
        val viewModel = viewModel(source)
        advanceTimeBy(1_000)
        assertEquals(0, source.starts)

        val observer = observe(viewModel, 500)
        assertEquals(1, source.starts)
        assertEquals(0, source.stops)

        observer.cancel()
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(1, source.stops)
    }

    @Test
    fun `source that needs the permission is not touched until it is granted`() = runTest {
        val source = CountingSource(fakeSource(FakeScenario.IN_TUNE), requiresMicPermission = true)
        val viewModel = viewModel(source)
        observe(viewModel, 500)
        assertEquals(LiveSignal.Silence, viewModel.state.value.signal) // not reported yet: no prompt flash
        assertEquals(0, source.starts)

        viewModel.onIntent(LiveIntent.MicPermissionChanged(granted = false))
        advanceTimeBy(500)
        runCurrent()
        assertEquals(LiveSignal.NoMicPermission, viewModel.state.value.signal)
        assertEquals(0, source.starts)
    }

    @Test
    fun `granting starts the pipeline and revoking stops it`() = runTest {
        val source = CountingSource(fakeSource(FakeScenario.IN_TUNE), requiresMicPermission = true)
        val viewModel = viewModel(source)
        observe(viewModel, 100)

        viewModel.onIntent(LiveIntent.MicPermissionChanged(granted = true))
        advanceTimeBy(500)
        runCurrent()
        assertEquals(1, source.starts)
        assertTrue(viewModel.state.value.signal is LiveSignal.Sounding)

        viewModel.onIntent(LiveIntent.MicPermissionChanged(granted = false))
        advanceTimeBy(100)
        runCurrent()
        assertEquals(LiveSignal.NoMicPermission, viewModel.state.value.signal)
        assertEquals(1, source.stops)
    }

    @Test
    fun `repeated permission reports on resume do not restart the source`() = runTest {
        val source = CountingSource(fakeSource(FakeScenario.IN_TUNE), requiresMicPermission = true)
        val viewModel = viewModel(source)
        observe(viewModel, 100)
        repeat(3) {
            viewModel.onIntent(LiveIntent.MicPermissionChanged(granted = true))
            advanceTimeBy(300)
            runCurrent()
        }
        assertEquals(1, source.starts)
    }

    @Test
    fun `fake source ignores permission reports`() = runTest {
        val viewModel = viewModel(FakeScenario.IN_TUNE)
        observe(viewModel, 500)
        viewModel.onIntent(LiveIntent.MicPermissionChanged(granted = false))
        advanceTimeBy(200)
        runCurrent()
        assertTrue(viewModel.state.value.signal is LiveSignal.Sounding)
    }

    @Test
    fun `unavailable microphone is shown and retried until it works`() = runTest {
        val working = fakeSource(FakeScenario.IN_TUNE)
        var attempts = 0
        val flaky = object : PitchSource {
            override val requiresMicPermission = false
            override val audioTap: AudioTap? = null
            override fun frames(config: IntonationConfig): Flow<PitchFrame> = flow {
                if (++attempts <= 2) throw MicUnavailableException(MicUnavailableReason.OPEN_FAILED, "busy")
                emitAll(working.frames(config))
            }
        }
        val viewModel = viewModel(flaky)
        observe(viewModel, 100)
        assertEquals(LiveSignal.MicUnavailable, viewModel.state.value.signal)
        assertEquals(1, attempts)

        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(2, attempts)
        assertEquals(LiveSignal.MicUnavailable, viewModel.state.value.signal)

        advanceTimeBy(3_500)
        runCurrent()
        assertEquals(3, attempts)
        assertTrue(viewModel.state.value.signal is LiveSignal.Sounding)
    }

    @Test
    fun `a dead input keeps saying so until real sound is back`() = runTest {
        // Like the emulator with its host microphone off: exact zeros, the watchdog gives up
        // after two seconds, the reopened input is dead again, and only the third is alive.
        val alive = fakeSource(FakeScenario.IN_TUNE)
        var attempts = 0
        val source = object : PitchSource {
            override val requiresMicPermission = false
            override val audioTap: AudioTap? = null
            override fun frames(config: IntonationConfig): Flow<PitchFrame> = flow {
                if (++attempts <= 2) {
                    var t = 0L
                    while (t < 2_000) {
                        emit(PitchFrame.unpitched(t, clarity = 0.0, rms = 0.0))
                        delay(10)
                        t += 10
                    }
                    throw MicUnavailableException(MicUnavailableReason.DIGITAL_SILENCE, "input is digitally silent")
                }
                emitAll(alive.frames(config))
            }
        }
        val viewModel = viewModel(source)
        val seen = mutableListOf<LiveSignal>()
        backgroundScope.launch { viewModel.state.collect { seen += it.signal } }

        advance(2_500) // first attempt: nothing to tell it from a silent room yet, then it fails
        assertEquals(LiveSignal.MicUnavailable, viewModel.state.value.signal)
        val firstFailure = seen.size

        advance(6_000) // second attempt is dead as well: no flip back to "play…" in between
        assertEquals(2, attempts)
        assertTrue(seen.drop(firstFailure).all { it == LiveSignal.MicUnavailable })

        advance(3_500) // third attempt delivers sound
        assertEquals(3, attempts)
        assertTrue(viewModel.state.value.signal is LiveSignal.Sounding)
        assertTrue(viewModel.state.value.canRecord)
        // one failure, however many reopenings it took: one event and one error of the microphone's group (spec 3.34)
        assertEquals(listOf("mic_unavailable {reason=digital_silence}"), analytics.sent().filter { it.startsWith("mic_unavailable") })
        assertEquals(listOf(ErrorGroup.MIC), analytics.errors.map { it.first })
    }

    private class CountingSource(
        private val delegate: PitchSource,
        override val requiresMicPermission: Boolean = false,
    ) : PitchSource {
        var starts = 0
        var stops = 0
        override val audioTap: AudioTap? = null
        override fun frames(config: IntonationConfig): Flow<PitchFrame> = delegate.frames(config)
            .onStart { starts++ }
            .onCompletion { stops++ }
    }

    // Practice tracking on Live (spec 3.12, 5.6)

    @Test
    fun `the tag shows the running practice, and a tap on it leads to finishing it`() = runTest {
        val viewModel = viewModel(FakeScenario.SILENCE)
        val effects = mutableListOf<LiveEffect>()
        backgroundScope.launch { viewModel.effects.collect { effects += it } }
        observe(viewModel, 300)
        assertEquals(null, viewModel.state.value.practiceMs)

        practice.startIfIdle(startedAt.toEpochMilliseconds() - 754_000)
        advance(100)
        assertEquals(754_000L, viewModel.state.value.practiceMs)

        viewModel.onIntent(LiveIntent.PracticeTagClicked)
        runCurrent()
        assertEquals(listOf<LiveEffect>(LiveEffect.FinishPractice), effects)
        assertTrue("«Занятия» are asked for the sheet", finishAsk.asked.value)

        practice.clear()
        advance(100)
        assertEquals(null, viewModel.state.value.practiceMs)
    }

    @Test
    fun `without a practice a tap on the tag starts one here, and Live stays`() = runTest {
        val viewModel = viewModel(FakeScenario.SILENCE)
        val effects = mutableListOf<LiveEffect>()
        backgroundScope.launch { viewModel.effects.collect { effects += it } }
        observe(viewModel, 300)

        viewModel.onIntent(LiveIntent.PracticeTagClicked)
        advance(100)
        assertEquals(0L, viewModel.state.value.practiceMs)
        assertTrue("no screen opens", effects.isEmpty())
        assertFalse(finishAsk.asked.value)
    }

    @Test
    fun `a double tap on «Начать занятие» starts the practice and does not finish it at once`() = runTest {
        val viewModel = viewModel(FakeScenario.SILENCE)
        val effects = mutableListOf<LiveEffect>()
        backgroundScope.launch { viewModel.effects.collect { effects += it } }
        observe(viewModel, 300)

        viewModel.onIntent(LiveIntent.PracticeTagClicked)
        viewModel.onIntent(LiveIntent.PracticeTagClicked)
        advance(100)

        assertEquals(0L, viewModel.state.value.practiceMs)
        assertTrue("no «Закончить занятие»", effects.isEmpty())
        assertFalse(finishAsk.asked.value)
    }

    @Test
    fun `a second tap right after the tag started a practice does not finish it`() = runTest {
        val viewModel = viewModel(FakeScenario.SILENCE)
        val effects = mutableListOf<LiveEffect>()
        backgroundScope.launch { viewModel.effects.collect { effects += it } }
        observe(viewModel, 300)

        // the first tap is written before the second lands: the double tap of a slower finger
        viewModel.onIntent(LiveIntent.PracticeTagClicked)
        runCurrent()
        viewModel.onIntent(LiveIntent.PracticeTagClicked)
        advance(100)

        assertEquals(0L, viewModel.state.value.practiceMs)
        assertTrue("no «Закончить занятие»", effects.isEmpty())
        assertFalse(finishAsk.asked.value)
    }

    @Test
    fun `while a take is recorded the tag of a running practice does not lead away`() = runTest {
        practice.startIfIdle(startedAt.toEpochMilliseconds() - 754_000)
        val viewModel = viewModel(FakeScenario.IN_TUNE)
        val effects = mutableListOf<LiveEffect>()
        backgroundScope.launch { viewModel.effects.collect { effects += it } }
        observe(viewModel, 500)
        viewModel.onIntent(LiveIntent.RecordClicked)
        advance(500)
        assertTrue(viewModel.state.value.recording != null)

        viewModel.onIntent(LiveIntent.PracticeTagClicked)
        advance(100)
        assertTrue("leaving Live would end the take", effects.none { it == LiveEffect.FinishPractice })
        assertFalse(finishAsk.asked.value)
        assertTrue(viewModel.state.value.recording != null)
    }

    @Test
    fun `the gear opens the settings`() = runTest {
        val viewModel = viewModel(FakeScenario.SILENCE)
        val effects = mutableListOf<LiveEffect>()
        backgroundScope.launch { viewModel.effects.collect { effects += it } }
        viewModel.onIntent(LiveIntent.SettingsClicked)
        runCurrent()
        assertEquals(listOf<LiveEffect>(LiveEffect.OpenSettings), effects)
    }

    @Test
    fun `while a practice runs the notes are counted for the journey, and the last one is not lost when the screen leaves`() = runTest {
        val practiceStart = startedAt.toEpochMilliseconds() - 60_000
        practice.startIfIdle(practiceStart)
        val viewModel = viewModel(FakeScenario.IN_TUNE) // one long A4, in tune
        val job = observe(viewModel, 3_000)
        assertEquals("a note that still sounds is not counted yet", NoteCount.ZERO, practiceNotes.count)
        job.cancel()
        advance(3_000) // the chain stops two seconds after the last subscriber: the sounding note ends there
        assertEquals(NoteCount(played = 1, inTune = 1), practiceNotes.countFor(practiceStart))
    }

    @Test
    fun `without a practice nothing is counted`() = runTest {
        val viewModel = viewModel(FakeScenario.IN_TUNE)
        val job = observe(viewModel, 3_000)
        job.cancel()
        advance(3_000)
        assertEquals(NoteCount.ZERO, practiceNotes.count)
    }

    @Test
    fun `past twelve hours a practice hears no sound and counts no notes`() = runTest {
        // it has ended by itself at its last sound (spec 3.12): a morning's playing must not stretch it to twelve hours
        val practiceStart = startedAt.toEpochMilliseconds() - PracticeConfig().maxPracticeMs - 60_000
        practice.startIfIdle(practiceStart)
        val viewModel = viewModel(FakeScenario.IN_TUNE)
        val job = observe(viewModel, 3_000)
        job.cancel()
        advance(3_000)
        assertNull(practice.running.value!!.lastSoundEpochMs)
        assertEquals(NoteCount.ZERO, practiceNotes.countFor(practiceStart))
    }

    @Test
    fun `after the microphone is reopened the notes are written out every few seconds again`() = runTest {
        practice.startIfIdle(startedAt.toEpochMilliseconds() - 60_000)
        // like both microphones: every opening counts its frames from zero; the first one breaks after six seconds
        var attempts = 0
        val source = object : PitchSource {
            override val requiresMicPermission = false
            override val audioTap: AudioTap? = null
            override fun frames(config: IntonationConfig): Flow<PitchFrame> = flow {
                val attempt = ++attempts
                var t = 0L
                while (true) {
                    if (attempt == 1 && t > 6_000) throw MicUnavailableException(MicUnavailableReason.READ_FAILED, "read failed")
                    // A4 and B4 by turns, half a second each: a note ends every half second
                    val hz = if ((t / 500) % 2 == 0L) 440.0 else 493.88
                    emit(PitchFrame.pitched(t, hz, clarity = 0.95, rms = 0.1, a4Hz = config.a4Hz))
                    delay(10)
                    t += 10
                }
            }
        }
        val viewModel = viewModel(source)
        observe(viewModel, 6_100)
        assertEquals(LiveSignal.MicUnavailable, viewModel.state.value.signal)
        val beforeReopening = practiceNotes.count.played
        assertTrue(beforeReopening > 0)

        advance(IntonationConfig().micRetryDelayMs + 3_000)
        assertEquals(2, attempts)
        assertTrue(
            "notes of the reopened stream are written while it plays: ${practiceNotes.count.played} after $beforeReopening",
            practiceNotes.count.played > beforeReopening,
        )
    }

    @Test
    fun `a sounding note marks the practice once per interval`() = runTest {
        practice.startIfIdle(startedAt.toEpochMilliseconds() - 60_000)
        val viewModel = viewModel(FakeScenario.IN_TUNE)
        observe(viewModel, 1_500)
        // the clock is fixed, so every frame is "now": one mark, not one per frame
        assertEquals(RunningPractice(startedAt.toEpochMilliseconds() - 60_000, startedAt.toEpochMilliseconds()), practice.running.value)
    }

    @Test
    fun `silence marks nothing, and nothing is marked without a practice`() = runTest {
        practice.startIfIdle(startedAt.toEpochMilliseconds() - 60_000)
        val silent = viewModel(FakeScenario.SILENCE)
        observe(silent, 1_500)
        assertEquals(null, practice.running.value!!.lastSoundEpochMs)

        practice.clear()
        val sounding = viewModel(FakeScenario.IN_TUNE)
        observe(sounding, 1_500)
        assertEquals(null, practice.running.value)
    }

    @Test
    fun `a held note moves the gauge every frame and the words only a few times a second`() = runTest {
        val viewModel = viewModel(FakeScenario.IN_TUNE)
        observe(viewModel, 1_000) // the note is locked and its digits have settled
        val states = mutableListOf<LiveState>()
        val gauges = mutableListOf<LiveGauge>()
        val counting = backgroundScope.launch {
            launch { viewModel.gauge.collect { gauges += it } }
            viewModel.state.collect { states += it }
        }
        advance(2_000)
        counting.cancel()
        // the digits may change every readout interval, and the hold completes once; nothing else is new
        val allowed = 2_000 / IntonationConfig().centsReadoutIntervalMs + 2
        assertTrue("${states.size} states in two seconds", states.size <= allowed)
        assertTrue("${gauges.size} gauges in two seconds", gauges.size >= 50)
    }

    @Test
    fun `a take in silence changes the words once a second`() = runTest {
        val viewModel = viewModel(FakeScenario.SILENCE)
        observe(viewModel, 500)
        viewModel.onIntent(LiveIntent.RecordClicked)
        advance(500)
        val states = mutableListOf<LiveState>()
        val counting = backgroundScope.launch { viewModel.state.collect { states += it } }
        advance(3_000)
        counting.cancel()
        // the first is the state as it was; after it only the seconds of the timer
        assertTrue("${states.map { it.recording }}", states.size <= 1 + 3 + 1)
        assertTrue(states.all { it.recording!!.elapsedMs % 1_000 == 0L })
    }

    @Test
    fun `the glow target, the level, the calm cents and the status line reach the state`() = runTest {
        val silent = viewModel(FakeScenario.SILENCE)
        val silentJob = observe(silent, 500)
        assertEquals(LiveGauge(), silent.gauge.value)
        assertEquals(StatusLine(StatusDot.READY, StatusMessage.PLAY), silent.state.value.statusLine)
        silentJob.cancel()

        val playing = viewModel(FakeScenario.IN_TUNE)
        val job = observe(playing, 1_500)
        val state = playing.state.value
        val signal = state.signal as LiveSignal.Sounding
        assertNull(state.statusLine)
        val gauge = playing.gauge.value
        assertTrue("in tune and held for a while: past the base glow", gauge.glowTarget > 0.6f)
        assertTrue(gauge.level > 0f)
        assertTrue(signal.noteSerial > 0)
        assertEquals(gauge.cents!!, signal.displayCents.toDouble(), 1.5)
        job.cancel()
    }

    // ---- where Live takes place (spec 3.27)

    private fun inCremona() {
        journey.progress.value = JourneyProgress.EMPTY.copy(arrivals = listOf(Arrival(JourneyRoute.HOME, 0), Arrival("cremona", 1)))
    }

    @Test
    fun `Live takes place where the player is, and follows when the player moves`() = runTest {
        inCremona()
        val viewModel = viewModel(FakeScenario.SILENCE)
        observe(viewModel, 300)
        assertEquals(Venue.Hall("cremona"), viewModel.state.value.venue)
        // the place is chosen on the journey: going home there is seen here at once
        venueStore.store(VenueRules.HOME)
        advance(100)
        assertEquals(Venue.Home, viewModel.state.value.venue)
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
    }
}
