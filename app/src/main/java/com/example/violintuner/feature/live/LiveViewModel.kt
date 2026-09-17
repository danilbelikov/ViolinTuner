package com.example.violintuner.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.violintuner.core.audio.PitchSource
import com.example.violintuner.core.di.DefaultDispatcher
import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.IntonationEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class LiveViewModel @Inject constructor(
    pitchSource: PitchSource,
    config: IntonationConfig,
    @DefaultDispatcher dispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val mode = MutableStateFlow(LiveMode.PLAY)
    private val scale = ScaleSpec(config)
    private val engine = IntonationEngine(config)

    // The engine is stateful and single-threaded: every frame goes through this one chain, and
    // the mode is read per frame instead of being combined in. conflate() drops only finished
    // readings the UI had no time to show, never frames.
    private val signal: Flow<LiveSignal> = pitchSource.frames
        .onStart { engine.reset() }
        .map { frame -> LiveReducer.signalOf(engine.process(frame, LiveReducer.targetModeOf(mode.value))) }
        .conflate()
        .flowOn(dispatcher)

    val state: StateFlow<LiveState> = combine(mode, signal) { mode, signal ->
        LiveState(mode = mode, signal = signal, scale = scale)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = LiveState(mode = mode.value, signal = LiveSignal.Silence, scale = scale),
    )

    private val effectChannel = Channel<LiveEffect>(Channel.BUFFERED)
    val effects: Flow<LiveEffect> = effectChannel.receiveAsFlow()

    fun onIntent(intent: LiveIntent) {
        when (intent) {
            is LiveIntent.SelectMode -> mode.value = intent.mode
            LiveIntent.RecordClicked -> effectChannel.trySend(LiveEffect.ShowRecordingUnavailable)
            LiveIntent.GrantMicClicked -> effectChannel.trySend(LiveEffect.RequestMicPermission)
        }
    }

    private companion object {
        // Long enough to survive a configuration change, short enough that the pitch source
        // (the microphone later on) is released soon after the screen goes away.
        const val STOP_TIMEOUT_MS = 2_000L
    }
}
