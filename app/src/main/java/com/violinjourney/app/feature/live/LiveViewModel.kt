package com.violinjourney.app.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.practice.RunningPracticeStore
import com.violinjourney.app.core.domain.practice.elapsedTicker
import com.violinjourney.app.core.domain.venue.Venue
import com.violinjourney.app.core.domain.venue.Venues
import com.violinjourney.app.core.recording.TakePipeline
import com.violinjourney.app.core.settings.IntonationConfigSource
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class LiveViewModel @Inject constructor(
    private val takes: TakePipeline,
    private val configSource: IntonationConfigSource,
    private val runningPractice: RunningPracticeStore,
    private val clock: Clock,
    private val venues: Venues,
) : ViewModel() {

    init {
        // The sound mark of a running practice (spec 5.6) is the chain's business now; it only needs a scope.
        viewModelScope.launch { takes.watchPractice() }
        // A recording the player stopped: show it, or say that there was nothing in it (spec 3.9).
        viewModelScope.launch {
            takes.events.collect { event ->
                effectChannel.send(
                    when (event) {
                        is TakePipeline.Event.Saved -> LiveEffect.OpenSession(event.sessionId)
                        TakePipeline.Event.NoNotes -> LiveEffect.ShowNoNotesRecorded
                    },
                )
            }
        }
    }

    // Mode and lock change together, so they live in one value the engine reads atomically.
    private val target = MutableStateFlow(LiveTarget())

    // The wish to record lives in the chain, which follows it frame by frame.
    private val recordingRequested get() = takes.recordingRequested

    private val effectChannel = Channel<LiveEffect>(Channel.BUFFERED)
    val effects: Flow<LiveEffect> = effectChannel.receiveAsFlow()

    /**
     * The chain itself is [TakePipeline], shared with the takes of the repertoire; Live's part is
     * what a frame looks like on screen. A new config gets a new readout along with the new engine.
     */
    private fun pipeline(config: IntonationConfig): Flow<TakePipeline.Output<LiveSignal>> {
        val readout = LiveReadout(config)
        return takes.run(
            config = config,
            pieceId = null,
            targetMode = { LiveReducer.targetModeOf(target.value) },
            unavailable = LiveSignal.MicUnavailable,
            onRestart = readout::reset,
            present = readout::signalOf,
        )
    }

    // null = not reported yet: stay silent instead of flashing the permission prompt at users
    // who have already granted it. The source is never collected without the permission.
    private val micPermissionGranted = MutableStateFlow(if (takes.requiresMicPermission) null else true)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val output: Flow<TakePipeline.Output<LiveSignal>> =
        combine(micPermissionGranted, configSource.config, ::Pair).flatMapLatest { (granted, config) ->
            when (granted) {
                null -> flowOf(TakePipeline.Output<LiveSignal>(LiveSignal.Silence))
                false -> flowOf(TakePipeline.Output<LiveSignal>(LiveSignal.NoMicPermission))
                true -> pipeline(config)
            }
        }

    /** Where Live takes place (spec 3.27): chosen on the journey, only shown here. */
    private val around: Flow<Pair<Long?, Venue>> = combine(runningPractice.elapsedTicker(clock), venues.current, ::Pair)

    val state: StateFlow<LiveState> =
        combine(
            target, configSource.config, recordingRequested, output, around,
        ) { target, config, requested, output, (practiceMs, venue) ->
            stateOf(target, config, requested, output, practiceMs, venue)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = stateOf(target.value, configSource.default, false, TakePipeline.Output(LiveSignal.Silence), null, null),
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
                if (takes.requiresMicPermission) micPermissionGranted.value = intent.granted
            LiveIntent.PracticeChipClicked -> effectChannel.trySend(LiveEffect.OpenPractice)
        }
    }

    private fun stateOf(
        target: LiveTarget,
        config: IntonationConfig,
        recordingRequested: Boolean,
        output: TakePipeline.Output<LiveSignal>,
        practiceMs: Long?,
        venue: Venue?,
    ) = LiveState(
        mode = target.mode,
        signal = output.shown,
        tuning = LiveReducer.tuningStateOf(target, output.shown, config),
        // The wish shows at once; the numbers follow with the first recorded frame.
        recording = if (recordingRequested) {
            RecordingState(output.recording?.elapsedMs ?: 0, output.recording?.bars.orEmpty())
        } else {
            null
        },
        canRecord = LiveReducer.canRecord(target, output.shown),
        scale = ScaleSpec(config),
        zoneCrossfadeMs = config.zoneCrossfadeMs,
        glowTarget = LiveReducer.glowTargetOf(output.shown, config),
        glowStep = LiveReducer.glowTargetOf(output.shown, config, stepped = true),
        statusLine = LiveReducer.statusLineOf(target, output.shown),
        practiceMs = practiceMs,
        venue = venue,
    )

    private companion object {
        // Long enough to survive a configuration change, short enough that the pitch source
        // (the microphone later on) is released soon after the screen goes away.
        const val STOP_TIMEOUT_MS = 2_000L

    }
}
