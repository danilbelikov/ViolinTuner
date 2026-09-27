package com.violinjourney.app.core.recording

import kotlin.concurrent.Volatile
import com.violinjourney.app.core.io.deleteFile
import com.violinjourney.app.core.io.fileName
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.domain.journey.JourneyConfig
import com.violinjourney.app.core.domain.journey.NoPracticeNotes
import com.violinjourney.app.core.domain.journey.NoteCount
import com.violinjourney.app.core.domain.journey.NoteCounter
import com.violinjourney.app.core.domain.journey.PracticeNotesStore
import com.violinjourney.app.core.analytics.Analytics
import com.violinjourney.app.core.analytics.ErrorGroup
import com.violinjourney.app.core.analytics.MicUnavailable
import com.violinjourney.app.core.analytics.NoOpAnalytics
import com.violinjourney.app.core.analytics.TakeRecorded
import com.violinjourney.app.core.audio.MicUnavailableException
import com.violinjourney.app.core.audio.PitchSource
import com.violinjourney.app.core.audio.recording.AudioTap
import com.violinjourney.app.core.audio.backing.BackingPlayback
import com.violinjourney.app.core.audio.backing.BackingPlaybackFactory
import com.violinjourney.app.core.domain.backing.AudioRoute
import com.violinjourney.app.core.domain.backing.Backing
import com.violinjourney.app.core.domain.backing.BackingConfig
import com.violinjourney.app.core.domain.backing.BackingOffset
import com.violinjourney.app.core.domain.backing.BackingProgress
import com.violinjourney.app.core.domain.backing.BackingRepository
import com.violinjourney.app.core.domain.backing.NoBackings
import com.violinjourney.app.core.domain.backing.TakeBacking
import com.violinjourney.app.core.audio.recording.SessionAudioFiles
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.IntonationEngine
import com.violinjourney.app.core.domain.IntonationReading
import com.violinjourney.app.core.domain.PitchFrame
import com.violinjourney.app.core.domain.TargetMode
import com.violinjourney.app.core.domain.practice.ForgottenPractice
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.practice.RunningPracticeStore
import com.violinjourney.app.core.domain.session.RecordingProgress
import com.violinjourney.app.core.domain.session.RecordingResult
import com.violinjourney.app.core.domain.session.SessionRecorder
import com.violinjourney.app.core.domain.session.SessionRepository
import com.violinjourney.app.core.time.WallClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.withContext

/**
 * Listening and recording in one chain: source → engine → recorder → sound (spec 3.9). Live
 * shows what the engine reads while it records; a take of a piece is recorded blind (spec
 * 3.15) — the chain is the same, only what is shown differs, and that is the caller's [run]
 * arguments. One instance per screen: it holds the wish to record and the sound mark of the
 * running practice. Not a singleton on purpose.
 *
 * The engine is stateful and single-threaded: every frame goes through this one chain, and
 * the target is read per frame instead of being combined in. conflate() drops only finished
 * outputs the UI had no time to show, never frames.
 */
class TakePipeline(
    private val pitchSource: PitchSource,
    private val sessionRepository: SessionRepository,
    private val audioFiles: SessionAudioFiles,
    private val runningPractice: RunningPracticeStore,
    private val practiceConfig: PracticeConfig,
    private val clock: WallClock,
    private val dispatcher: CoroutineDispatcher,
    private val watch: RecordingWatch = RecordingWatch(),
    private val practiceNotes: PracticeNotesStore = NoPracticeNotes,
    private val journeyConfig: JourneyConfig = JourneyConfig(),
    private val backings: BackingRepository = NoBackings,
    private val backingPlaybackFactory: BackingPlaybackFactory? = null,
    private val backingConfig: BackingConfig = BackingConfig(),
    private val analytics: Analytics = NoOpAnalytics(),
) {
    /**
     * A take to be made under a backing (spec 3.32): which one, its sound at the take's rate, and the headphones it
     * goes to with what they are believed to lag. Set by the screen before it asks for the recording; read when
     * the recording starts.
     */
    class BackingPlan(
        val backing: Backing,
        /**
         * The backing's sound already made at a rate, or null — never made here: it is asked on the thread that reads the
         * microphone, and an unpack there would hold the input for seconds (spec 5.25).
         */
        val ready: (sampleRate: Int) -> PlatformFile?,
        val route: AudioRoute,
        val latencyMs: Int,
        /**
         * The microphone opened at a rate the sound is not made for ([ready] gave nothing): the take does not begin, and
         * the screen makes the sound at that rate — the next press records. Called on the chain's thread.
         */
        val notReady: (sampleRate: Int) -> Unit,
    )

    @Volatile
    var backingPlan: BackingPlan? = null

    /**
     * A take shot by the app's own camera (spec 3.32): the camera is started with the sound and told when the sound is
     * whole; what it gives back — an `.mp4` of its picture and this sound — becomes the take's file.
     */
    interface VideoHook {
        /** The take's sound has begun; its first sample is at [recordStartNanos] on `CLOCK_MONOTONIC`, when known. */
        fun onRecordingStarted(recordStartNanos: Long?)

        /** The sound is whole in [audio]; the name of the video made of it and the picture, or null — the take stays sound only. */
        suspend fun onRecordingFinished(audio: PlatformFile, recordStartNanos: Long?): String?

        /** The take came to nothing (too short, no notes): the picture goes too. */
        suspend fun onRecordingDiscarded()
    }

    @Volatile
    var videoHook: VideoHook? = null

    private val playback: BackingPlayback? by lazy { backingPlaybackFactory?.create() }

    /**
     * How far the backing of the running take has played, in ms, in steps of [BackingConfig.progressStepMs]; null while
     * none plays. For the screens only: the playback tells it with every chunk, and the bar and the words under the
     * timer need no finer. What a take stores is the playback's own exact count at its stop.
     */
    val backingPosition: Flow<Long?>
        get() = (playback?.position ?: NO_POSITION).map { played -> played?.let { BackingProgress.shownMs(it, backingConfig) } }.distinctUntilChanged()

    /** What one frame came to: what the screen shows, and how the recording stands, if one runs. */
    class Output<T>(val shown: T, val recording: RecordingProgress? = null)

    /** Only for a recording the player stopped: every other end is saved without a word (spec 3.9). */
    sealed interface Event {
        data class Saved(val sessionId: Long) : Event

        /** Stopped by the player, but there was not a single note in it. */
        data object NoNotes : Event
    }

    val requiresMicPermission: Boolean get() = pitchSource.requiresMicPermission

    // The record button only flips this wish. The recorder itself lives inside the chain,
    // next to the engine and on its thread, and follows the wish frame by frame.
    val recordingRequested = MutableStateFlow(false)

    private val eventChannel = Channel<Event>(Channel.BUFFERED)
    val events: Flow<Event> = eventChannel.receiveAsFlow()

    // "The violin sounded" for the forgotten-practice rule (spec 5.6): written from the engine
    // thread at most once a minute, and only while a practice runs. The start of the running
    // practice is mirrored here because the chain must not suspend on the store per frame.
    @Volatile
    private var practiceStartedAt: Long? = null
    private var lastSoundMarkAt: Long? = null

    /** Follows the running practice for the sound mark; runs until cancelled — launch it in the screen's scope. */
    suspend fun watchPractice() {
        runningPractice.running.collect { running ->
            if (running?.startedAtEpochMs != practiceStartedAt) lastSoundMarkAt = null
            practiceStartedAt = running?.startedAtEpochMs
        }
    }

    /**
     * The start of the practice the chain hears for, or null: none runs, or it is past the limit — it has ended by
     * itself then (spec 3.12), and a note played now is neither its sound nor its takts.
     */
    private fun practiceHeard(): Long? = practiceStartedAt?.takeIf { ForgottenPractice.runsAt(it, clock.millis(), practiceConfig) }

    /**
     * [practice] is [practiceHeard]: past the limit there is none, for a mark then would move the end of the expired
     * practice from its last sound to twelve hours (spec 3.12).
     */
    private suspend fun markSoundIfDue(practice: Long?, reading: IntonationReading) {
        if (practice == null) return
        if (reading !is IntonationReading.Active || reading.held) return
        val now = clock.millis()
        val last = lastSoundMarkAt
        if (last != null && now - last < practiceConfig.soundMarkIntervalMs) return
        lastSoundMarkAt = now
        runningPractice.markSound(now)
    }

    /**
     * The chain for one configuration; a new config (the player changed the reference pitch or
     * the tolerance) means a new call, with a new engine and a new collection of the source.
     * [present] runs on the engine's thread for every frame; [onRestart] whenever the source is
     * (re)opened; [unavailable] is shown while the microphone cannot be had. Sessions recorded
     * here belong to [pieceId], or to no piece.
     */
    fun <T> run(
        config: IntonationConfig,
        pieceId: Long?,
        targetMode: () -> TargetMode,
        unavailable: T,
        onRestart: () -> Unit = {},
        present: (frame: PitchFrame, reading: IntonationReading) -> T,
    ): Flow<Output<T>> {
        val engine = IntonationEngine(config)
        val tap = pitchSource.audioTap
        var recorder: SessionRecorder? = null
        var audioFile: PlatformFile? = null
        // the backing of the running take: what played, and from which moment of the take's clock
        var backingStarted: BackingPlan? = null
        var recordStartNanos: Long? = null
        // After a microphone failure the reopened input is trusted only once it delivers
        // something: a dead input (emulator bridge off, capture silenced by the system) reads as
        // exact zeros, and showing "play…" for the two seconds before the watchdog gives up
        // again would make the screen flip between the two messages forever.
        var awaitingSignal = false

        // Closes the take, if one was asked for, and says whether the file is worth keeping.
        suspend fun closeAudio(keep: Boolean): String? {
            val file = audioFile ?: return null
            audioFile = null
            val complete = tap?.stop() == true
            if (complete && keep) return file.fileName
            file.deleteFile()
            return null
        }

        // What a stopped take comes to: a session (with its video, when the camera shot one), or nothing.
        suspend fun settleTake(finished: SessionRecorder, stoppedByPlayer: Boolean, plan: BackingPlan?, playedMs: Long, backingStartNanos: Long?, clockHoldsMs: Int) {
            val hook = videoHook
            when (val result = finished.finish()) {
                RecordingResult.TooShort -> {
                    closeAudio(keep = false)
                    hook?.onRecordingDiscarded()
                }
                RecordingResult.NoNotes -> {
                    closeAudio(keep = false)
                    hook?.onRecordingDiscarded()
                    if (stoppedByPlayer) eventChannel.send(Event.NoNotes)
                }
                is RecordingResult.Recorded -> {
                    val audioName = closeAudio(keep = true)
                    // shot by the app's camera: the file of the take is the video made of this sound and the picture
                    val video = if (hook != null && audioName != null) audioFiles.existing(audioName)?.let { hook.onRecordingFinished(it, recordStartNanos) } else null
                    if (video != null) audioName?.let(audioFiles::delete) else if (hook != null) hook.onRecordingDiscarded()
                    val session = result.session.copy(audioPath = video ?: audioName, videoPath = video, pieceId = pieceId)
                    val id = sessionRepository.save(session)
                    if (plan != null) {
                        // the headphones' lag goes on top of the clocks only as far as the output's clock does not hold it
                        val latency = BackingOffset.latencyAddedMs(backingStartNanos, recordStartNanos, plan.latencyMs, clockHoldsMs)
                        val offset = BackingOffset.offsetMs(backingStartNanos, recordStartNanos, latency, backingConfig)
                        backings.saveTake(
                            TakeBacking(
                                sessionId = id, backingId = plan.backing.id, offsetMs = offset, recordedOffsetMs = offset,
                                gainDb = backingConfig.defaultGainDb, playedMs = playedMs, output = plan.route.output,
                                deviceName = plan.route.deviceName, latencyMs = latency,
                            ),
                        )
                    }
                    analytics.track(
                        TakeRecorded(
                            seconds = (result.session.durationMs / MS_PER_SECOND).toInt(),
                            video = video != null,
                            backing = plan != null,
                        ),
                    )
                    if (stoppedByPlayer) eventChannel.send(Event.Saved(id))
                }
            }
        }

        // stoppedByPlayer: the player is looking at the screen and hears about the outcome.
        // Otherwise the recording ended because the screen went away or the microphone
        // failed; it is saved quietly (spec 3.9).
        // A stopped take is seen through to the end whatever becomes of the chain: kept, or — too short, no notes —
        // thrown away with its file and its picture. The screen may be gone long before the sound is closed and the
        // video made (spec 3.9, 3.32), and a take cut off halfway would be lost with its files left behind.
        suspend fun finishRecording(stoppedByPlayer: Boolean): Unit = withContext(NonCancellable) {
            val finished = recorder
            recorder = null
            recordingRequested.value = false
            try {
                val plan = backingStarted
                backingStarted = null
                val playedMs = if (plan != null) playback?.stop() ?: 0 else 0
                // both kept by the playback after its stop
                val backingStartNanos = playback?.startNanos
                val clockHoldsMs = playback?.includedLatencyMs ?: 0
                if (finished == null) {
                    closeAudio(keep = false) // stopped while still waiting for the sound to start
                    return@withContext
                }
                settleTake(finished, stoppedByPlayer, plan, playedMs, backingStartNanos, clockHoldsMs)
            } finally {
                // a copy of the data waits for the take until it is in the database, not only until it stopped (spec 3.20)
                if (finished != null) watch.set(false)
            }
        }

        // With sound, the first recorded frame is the one the take starts at (or the one after
        // it): frames and audio share the sample clock, so the session and its file begin
        // within one hop of each other and the player needs no offset. Costs a few frames.
        fun mayStartRecorder(frameTMs: Long): Boolean {
            if (tap == null) return true
            return when (val state = tap.state) {
                AudioTap.State.Idle -> {
                    audioFile = audioFiles.newFile().also(tap::start)
                    false
                }
                AudioTap.State.Starting -> false
                is AudioTap.State.Running -> frameTMs >= state.startTMs
                AudioTap.State.Failed -> true // no encoder here: record without sound
            }
        }

        // Notes of the running practice, for the journey (spec 5.17): counted here, next to the
        // engine and on its thread, and written out every few seconds — never per frame.
        val counter = NoteCounter(journeyConfig)
        var lastNotesFlushTMs = 0L
        suspend fun flushNotes(count: NoteCount) {
            val startedAt = practiceStartedAt
            if (startedAt != null && count.played > 0) practiceNotes.add(startedAt, count)
        }

        return pitchSource.frames(config)
            .onStart {
                engine.reset()
                // A source opened anew counts its tMs from zero: the mark of the stream before would otherwise keep the
                // notes from being written out for as long as that stream ran (spec 5.17). Its counter was emptied by
                // the onCompletion of that stream.
                lastNotesFlushTMs = 0L
                onRestart()
            }
            .map { frame ->
                if (awaitingSignal) {
                    if (frame.rms == 0.0) return@map Output(unavailable)
                    awaitingSignal = false
                }
                val reading = engine.process(frame, targetMode())
                val practice = practiceHeard()
                markSoundIfDue(practice, reading)
                if (practice != null) {
                    counter.add(frame.tMs, reading)
                    if (frame.tMs - lastNotesFlushTMs >= journeyConfig.notesFlushMs) {
                        lastNotesFlushTMs = frame.tMs
                        flushNotes(counter.take())
                    }
                } else {
                    // no practice, no journey: what sounded outside it is not counted (flushNotes writes nothing without
                    // one) — but a practice that has just reached its limit keeps what it heard before it
                    flushNotes(counter.finish())
                }
                if (recordingRequested.value && recorder == null && mayStartRecorder(frame.tMs)) {
                    // the backing, when there is one to play, at the rate the take is really recorded at (spec 3.32)
                    val plan = backingPlan?.takeIf { playback != null }
                    val rate = tap?.sampleRateHz ?: DEFAULT_RATE
                    val pcm = plan?.ready?.invoke(rate)
                    if (plan != null && pcm == null) {
                        // not made for this rate: the take does not begin — no unpacking here (spec 5.25). The sound the tap
                        // has begun is dropped just below, as for a mind changed before the first frame.
                        recordingRequested.value = false
                        plan.notReady(rate)
                    } else {
                        recorder = SessionRecorder(config, clock.millis())
                        watch.set(true)
                        val takeStartTMs = (tap?.state as? AudioTap.State.Running)?.startTMs ?: frame.tMs
                        recordStartNanos = pitchSource.clock?.nanosAt(takeStartTMs)
                        if (plan != null && pcm != null) {
                            playback?.start(pcm, rate)
                            backingStarted = plan
                        }
                        videoHook?.onRecordingStarted(recordStartNanos)
                    }
                }
                val running = recorder
                if (running != null) {
                    running.add(frame.tMs, reading)
                    // reaching the limit counts as the player's stop: they are still at the stand
                    if (!recordingRequested.value || running.limitReached) finishRecording(stoppedByPlayer = true)
                } else if (!recordingRequested.value && audioFile != null) {
                    finishRecording(stoppedByPlayer = true) // changed their mind before the first frame
                }
                Output(present(frame, reading), recorder?.progress())
            }
            // Runs when the collection is cancelled (the screen left, settings changed, permission
            // revoked) and when the source fails, before the retry below: never lose a take.
            // finishRecording cannot be cut short by itself; the last notes of the practice need the same.
            .onCompletion {
                withContext(NonCancellable) {
                    finishRecording(stoppedByPlayer = false)
                    flushNotes(counter.finish())
                }
                recordingRequested.value = false
            }
            .retryWhen { cause, _ ->
                // Anything else is a bug and must crash rather than be retried forever.
                if (cause !is MicUnavailableException) return@retryWhen false
                // Counted once per failure, not once per retry: while the input stays dead the
                // retry runs every few seconds, and the event is about losing it, not about waiting.
                // Both halves of spec 3.34: the event for the picture of why, the error of its group for the console's count.
                if (!awaitingSignal) {
                    analytics.track(MicUnavailable(cause.reason))
                    analytics.error(ErrorGroup.MIC, "the microphone went away (${cause.reason.key})", cause)
                }
                awaitingSignal = true
                emit(Output(unavailable))
                delay(config.micRetryDelayMs)
                true
            }
            .conflate()
            .flowOn(dispatcher)
    }

    companion object {
        private const val MS_PER_SECOND = 1_000L

        /** A source without sound (the fake one) records no file: the backing plays at the usual rate, and is made ready at it. */
        const val DEFAULT_RATE = 48_000
        private val NO_POSITION: StateFlow<Long?> = MutableStateFlow(null)
    }
}
