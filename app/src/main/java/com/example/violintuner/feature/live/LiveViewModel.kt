package com.example.violintuner.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.violintuner.core.audio.MicUnavailableException
import com.example.violintuner.core.audio.PitchSource
import com.example.violintuner.core.di.DefaultDispatcher
import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.IntonationEngine
import com.example.violintuner.core.settings.IntonationConfigSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

@HiltViewModel
class LiveViewModel @Inject constructor(
    private val pitchSource: PitchSource,
    private val configSource: IntonationConfigSource,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : ViewModel() {

    // Mode and lock change together, so they live in one value the engine reads atomically.
    private val target = MutableStateFlow(LiveTarget())

    // The engine is stateful and single-threaded: every frame goes through this one chain, and
    // the target is read per frame instead of being combined in. conflate() drops only finished
    // readings the UI had no time to show, never frames. A new config (the player changed the
    // reference pitch or the tolerance) gets a new engine and a new source collection.
    private fun pipeline(config: IntonationConfig): Flow<LiveSignal> {
        val engine = IntonationEngine(config)
        return pitchSource.frames(config)
            .onStart { engine.reset() }
            .map { frame -> LiveReducer.signalOf(engine.process(frame, LiveReducer.targetModeOf(target.value))) }
            .retryWhen { cause, _ ->
                // Anything else is a bug and must crash rather than be retried forever.
                if (cause !is MicUnavailableException) return@retryWhen false
                emit(LiveSignal.MicUnavailable)
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
    private val signal: Flow<LiveSignal> =
        combine(micPermissionGranted, configSource.config, ::Pair).flatMapLatest { (granted, config) ->
            when (granted) {
                null -> flowOf(LiveSignal.Silence)
                false -> flowOf(LiveSignal.NoMicPermission)
                true -> pipeline(config)
            }
        }

    val state: StateFlow<LiveState> = combine(target, configSource.config, signal, ::stateOf).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = stateOf(target.value, configSource.default, LiveSignal.Silence),
    )

    private val effectChannel = Channel<LiveEffect>(Channel.BUFFERED)
    val effects: Flow<LiveEffect> = effectChannel.receiveAsFlow()

    fun onIntent(intent: LiveIntent) {
        when (intent) {
            is LiveIntent.SelectMode -> target.update { LiveReducer.selectMode(it, intent.mode) }
            is LiveIntent.StringClicked -> target.update { LiveReducer.clickString(it, intent.string) }
            LiveIntent.RecordClicked -> effectChannel.trySend(LiveEffect.ShowRecordingUnavailable)
            LiveIntent.GrantMicClicked -> effectChannel.trySend(LiveEffect.RequestMicPermission)
            is LiveIntent.MicPermissionChanged ->
                if (pitchSource.requiresMicPermission) micPermissionGranted.value = intent.granted
        }
    }

    private fun stateOf(target: LiveTarget, config: IntonationConfig, signal: LiveSignal) = LiveState(
        mode = target.mode,
        signal = signal,
        tuning = LiveReducer.tuningStateOf(target, signal, config),
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
