package com.example.violintuner.core.recording

import com.example.violintuner.core.audio.MicUnavailableException
import com.example.violintuner.core.audio.PitchSource
import com.example.violintuner.core.audio.recording.AudioTap
import com.example.violintuner.core.audio.recording.SessionAudioFiles
import com.example.violintuner.core.di.DefaultDispatcher
import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.IntonationEngine
import com.example.violintuner.core.domain.IntonationReading
import com.example.violintuner.core.domain.PitchFrame
import com.example.violintuner.core.domain.TargetMode
import com.example.violintuner.core.domain.practice.PracticeConfig
import com.example.violintuner.core.domain.practice.RunningPracticeStore
import com.example.violintuner.core.domain.session.RecordingProgress
import com.example.violintuner.core.domain.session.RecordingResult
import com.example.violintuner.core.domain.session.SessionRecorder
import com.example.violintuner.core.domain.session.SessionRepository
import java.io.File
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.conflate
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
class TakePipeline @Inject constructor(
    private val pitchSource: PitchSource,
    private val sessionRepository: SessionRepository,
    private val audioFiles: SessionAudioFiles,
    private val runningPractice: RunningPracticeStore,
    private val practiceConfig: PracticeConfig,
    private val clock: Clock,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
    private val watch: RecordingWatch = RecordingWatch(),
) {
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

    private suspend fun markSoundIfDue(reading: IntonationReading) {
        if (practiceStartedAt == null) return
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
        var audioFile: File? = null
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
            if (complete && keep) return file.name
            file.delete()
            return null
        }

        // stoppedByPlayer: the player is looking at the screen and hears about the outcome.
        // Otherwise the recording ended because the screen went away or the microphone
        // failed; it is saved quietly (spec 3.9).
        suspend fun finishRecording(stoppedByPlayer: Boolean) {
            val finished = recorder
            recorder = null
            watch.set(false)
            recordingRequested.value = false
            if (finished == null) {
                closeAudio(keep = false) // stopped while still waiting for the sound to start
                return
            }
            when (val result = finished.finish()) {
                RecordingResult.TooShort -> closeAudio(keep = false)
                RecordingResult.NoNotes -> {
                    closeAudio(keep = false)
                    if (stoppedByPlayer) eventChannel.send(Event.NoNotes)
                }
                is RecordingResult.Recorded -> {
                    val id = sessionRepository.save(result.session.copy(audioPath = closeAudio(keep = true), pieceId = pieceId))
                    if (stoppedByPlayer) eventChannel.send(Event.Saved(id))
                }
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

        return pitchSource.frames(config)
            .onStart {
                engine.reset()
                onRestart()
            }
            .map { frame ->
                if (awaitingSignal) {
                    if (frame.rms == 0.0) return@map Output(unavailable)
                    awaitingSignal = false
                }
                val reading = engine.process(frame, targetMode())
                markSoundIfDue(reading)
                if (recordingRequested.value && recorder == null && mayStartRecorder(frame.tMs)) {
                    recorder = SessionRecorder(config, clock.millis())
                    watch.set(true)
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
            .onCompletion {
                withContext(NonCancellable) { finishRecording(stoppedByPlayer = false) }
                recordingRequested.value = false
            }
            .retryWhen { cause, _ ->
                // Anything else is a bug and must crash rather than be retried forever.
                if (cause !is MicUnavailableException) return@retryWhen false
                awaitingSignal = true
                emit(Output(unavailable))
                delay(MIC_RETRY_DELAY_MS)
                true
            }
            .conflate()
            .flowOn(dispatcher)
    }

    private companion object {
        // Pause before reopening a microphone that failed (busy with a call, hardware hiccup).
        const val MIC_RETRY_DELAY_MS = 3_000L
    }
}
