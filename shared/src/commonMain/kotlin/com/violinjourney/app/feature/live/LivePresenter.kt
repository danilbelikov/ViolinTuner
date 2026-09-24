package com.violinjourney.app.feature.live

import com.violinjourney.app.core.audio.MicUnavailableException
import com.violinjourney.app.core.audio.PitchSource
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.IntonationEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * Live without the app around it: source → engine → what the screen shows, with the mode and the locked string.
 * The iOS app runs Live on it. The Android app runs LiveViewModel on TakePipeline, which records too; both build
 * the screen with [LiveReducer.stateOf] and [LiveReadout], and follow the microphone the same way:
 *
 * - until the platform has said whether the microphone is allowed, the screen says «Играйте…» rather than flash the
 *   prompt at those who have allowed it; without the permission the source is never opened (spec 3.4);
 * - a microphone that cannot be opened or breaks down is MicUnavailable, tried again every
 *   [IntonationConfig.micRetryDelayMs], and stays so until the reopened input gives more than exact zeros;
 *   any other failure of the source is a bug and is not caught;
 * - while [active] is false (the app is in the background) the source is not listened to at all.
 *
 * No recording, practice or place yet.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LivePresenter(
    private val source: PitchSource,
    private val config: IntonationConfig,
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    active: Flow<Boolean> = flowOf(true),
) {
    // Mode and lock change together, so they live in one value the engine reads atomically.
    private val target = MutableStateFlow(LiveTarget())

    // null = not reported yet.
    private val micPermissionGranted = MutableStateFlow(if (source.requiresMicPermission) null else true)

    private val effectChannel = Channel<LiveEffect>(Channel.BUFFERED)

    /** Only [LiveEffect.RequestMicPermission] so far: the platform asks, or opens its settings when it may not ask. */
    val effects: Flow<LiveEffect> = effectChannel.receiveAsFlow()

    private val signal: Flow<LiveSignal> = combine(micPermissionGranted, active, ::Pair).flatMapLatest { (granted, active) ->
        when {
            !active || granted == null -> flowOf(LiveSignal.Silence)
            !granted -> flowOf(LiveSignal.NoMicPermission)
            else -> listening().flowOn(dispatcher).conflate()
        }
    }

    // The engine is stateful and single-threaded: one per listening, every frame through it, the target read per
    // frame. conflate() drops only finished signals the screen had no time to show, never frames.
    private fun listening(): Flow<LiveSignal> = flow {
        val engine = IntonationEngine(config)
        val readout = LiveReadout(config)
        // After a failure the reopened input must give something other than exact zeros before the screen
        // believes it: otherwise it would flash between the two lines on every attempt (spec 3.4).
        var awaitingSignal = false
        emitAll(
            source.frames(config)
                .onStart {
                    engine.reset()
                    readout.reset()
                }
                .map { frame ->
                    if (awaitingSignal) {
                        if (frame.rms == 0.0) return@map LiveSignal.MicUnavailable
                        awaitingSignal = false
                    }
                    readout.signalOf(frame, engine.process(frame, LiveReducer.targetModeOf(target.value)))
                }
                .retryWhen { cause, _ ->
                    if (cause !is MicUnavailableException) return@retryWhen false
                    awaitingSignal = true
                    emit(LiveSignal.MicUnavailable)
                    delay(config.micRetryDelayMs)
                    true
                },
        )
    }

    val state: StateFlow<LiveState> = combine(target, signal) { target, signal ->
        LiveReducer.stateOf(target, config, signal)
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = LiveReducer.stateOf(target.value, config, LiveSignal.Silence),
    )

    /** The mode, the strings and the microphone; the rest of the intents belong to parts iOS does not have yet. */
    fun onIntent(intent: LiveIntent) {
        when (intent) {
            is LiveIntent.SelectMode -> target.update { LiveReducer.selectMode(it, intent.mode) }
            is LiveIntent.StringClicked -> target.update { LiveReducer.clickString(it, intent.string) }
            LiveIntent.GrantMicClicked -> effectChannel.trySend(LiveEffect.RequestMicPermission)
            is LiveIntent.MicPermissionChanged -> if (source.requiresMicPermission) micPermissionGranted.value = intent.granted
            else -> Unit
        }
    }

    private companion object {
        // As on Android: long enough for a rotation, short enough that the source stops soon after the screen goes.
        const val STOP_TIMEOUT_MS = 2_000L
    }
}
