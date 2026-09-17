package com.example.violintuner.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.violintuner.core.audio.MicUnavailableException
import com.example.violintuner.core.audio.PitchSource
import com.example.violintuner.core.di.DefaultDispatcher
import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.IntonationEngine
import com.example.violintuner.core.domain.session.RecordingProgress
import com.example.violintuner.core.domain.session.RecordingResult
import com.example.violintuner.core.domain.session.SessionRecorder
import com.example.violintuner.core.domain.session.SessionRepository
import com.example.violintuner.core.settings.IntonationConfigSource
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

@HiltViewModel
class LiveViewModel @Inject constructor(
    private val pitchSource: PitchSource,
    private val configSource: IntonationConfigSource,
    private val sessionRepository: SessionRepository,
    private val clock: Clock,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : ViewModel() {

    // Mode and lock change together, so they live in one value the engine reads atomically.
    private val target = MutableStateFlow(LiveTarget())

    // The record button only flips this wish. The recorder itself lives inside the pipeline,
    // next to the engine and on its thread, and follows the wish frame by frame.
    private val recordingRequested = MutableStateFlow(false)

    private val effectChannel = Channel<LiveEffect>(Channel.BUFFERED)
    val effects: Flow<LiveEffect> = effectChannel.receiveAsFlow()

    private class PipelineOutput(val signal: LiveSignal, val recording: RecordingProgress? = null)

    // The engine is stateful and single-threaded: every frame goes through this one chain, and
    // the target is read per frame instead of being combined in. conflate() drops only finished
    // readings the UI had no time to show, never frames. A new config (the player changed the
    // reference pitch or the tolerance) gets a new engine and a new source collection.
    private fun pipeline(config: IntonationConfig): Flow<PipelineOutput> {
        val engine = IntonationEngine(config)
        var recorder: SessionRecorder? = null

        // stoppedByPlayer: the player is looking at Live and gets the session screen or a
        // word about the empty take. Otherwise the recording ended because Live went away or
        // the microphone failed; it is saved quietly (spec 3.9).
        suspend fun finishRecording(stoppedByPlayer: Boolean) {
            val finished = recorder ?: return
            recorder = null
            recordingRequested.value = false
            when (val result = finished.finish()) {
                RecordingResult.TooShort -> Unit
                RecordingResult.NoNotes ->
                    if (stoppedByPlayer) effectChannel.send(LiveEffect.ShowNoNotesRecorded)
                is RecordingResult.Recorded -> {
                    val id = sessionRepository.save(result.session)
                    if (stoppedByPlayer) effectChannel.send(LiveEffect.OpenSession(id))
                }
            }
        }

        return pitchSource.frames(config)
            .onStart { engine.reset() }
            .map { frame ->
                val reading = engine.process(frame, LiveReducer.targetModeOf(target.value))
                if (recordingRequested.value && recorder == null) recorder = SessionRecorder(config, clock.millis())
                val running = recorder
                if (running != null) {
                    running.add(frame.tMs, reading)
                    // reaching the limit counts as the player's stop: they are still at the stand
                    if (!recordingRequested.value || running.limitReached) finishRecording(stoppedByPlayer = true)
                }
                PipelineOutput(LiveReducer.signalOf(reading), recorder?.progress())
            }
            // Runs when the collection is cancelled (Live left, settings changed, permission
            // revoked) and when the source fails, before the retry below: never lose a take.
            .onCompletion {
                withContext(NonCancellable) { finishRecording(stoppedByPlayer = false) }
                recordingRequested.value = false
            }
            .retryWhen { cause, _ ->
                // Anything else is a bug and must crash rather than be retried forever.
                if (cause !is MicUnavailableException) return@retryWhen false
                emit(PipelineOutput(LiveSignal.MicUnavailable))
                delay(MIC_RETRY_DELAY_MS)
                true
            }
            .conflate()
            .flowOn(dispatcher)
    }

    // null = not reported yet: stay silent instead of flashing the permission prompt at users
    // who have already granted it. The source is never collected without the permission.
    private val micPermissionGranted = MutableStateFlow(if (pitchSource.requiresMicPermission) null else true)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val output: Flow<PipelineOutput> =
        combine(micPermissionGranted, configSource.config, ::Pair).flatMapLatest { (granted, config) ->
            when (granted) {
                null -> flowOf(PipelineOutput(LiveSignal.Silence))
                false -> flowOf(PipelineOutput(LiveSignal.NoMicPermission))
                true -> pipeline(config)
            }
        }

    val state: StateFlow<LiveState> =
        combine(target, configSource.config, recordingRequested, output) { target, config, requested, output ->
            stateOf(target, config, requested, output)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = stateOf(target.value, configSource.default, false, PipelineOutput(LiveSignal.Silence)),
        )

    fun onIntent(intent: LiveIntent) {
        when (intent) {
            // the mode is fixed while a recording runs (spec 3.9)
            is LiveIntent.SelectMode ->
                if (!recordingRequested.value) target.update { LiveReducer.selectMode(it, intent.mode) }
            is LiveIntent.StringClicked -> target.update { LiveReducer.clickString(it, intent.string) }
            LiveIntent.RecordClicked -> when {
                recordingRequested.value -> recordingRequested.value = false
                state.value.canRecord -> recordingRequested.value = true
            }
            LiveIntent.GrantMicClicked -> effectChannel.trySend(LiveEffect.RequestMicPermission)
            is LiveIntent.MicPermissionChanged ->
                if (pitchSource.requiresMicPermission) micPermissionGranted.value = intent.granted
        }
    }

    private fun stateOf(
        target: LiveTarget,
        config: IntonationConfig,
        recordingRequested: Boolean,
        output: PipelineOutput,
    ) = LiveState(
        mode = target.mode,
        signal = output.signal,
        tuning = LiveReducer.tuningStateOf(target, output.signal, config),
        // The wish shows at once; the numbers follow with the first recorded frame.
        recording = if (recordingRequested) {
            RecordingState(output.recording?.elapsedMs ?: 0, output.recording?.bars.orEmpty())
        } else {
            null
        },
        canRecord = LiveReducer.canRecord(target, output.signal),
        scale = ScaleSpec(config),
        zoneCrossfadeMs = config.zoneCrossfadeMs,
    )

    private companion object {
        // Long enough to survive a configuration change, short enough that the pitch source
        // (the microphone later on) is released soon after the screen goes away.
        const val STOP_TIMEOUT_MS = 2_000L

        // Pause before reopening a microphone that failed (busy with a call, hardware hiccup).
        const val MIC_RETRY_DELAY_MS = 3_000L
    }
}
