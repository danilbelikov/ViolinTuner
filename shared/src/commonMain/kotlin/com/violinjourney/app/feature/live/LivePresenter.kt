package com.violinjourney.app.feature.live

import com.violinjourney.app.core.audio.PitchSource
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.IntonationEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * Live without the app around it: source → engine → what the screen shows, with the mode and the locked string.
 * The iOS app runs Live on it. The Android app runs LiveViewModel on TakePipeline, which records too; both build
 * the screen with [LiveReducer.stateOf] and [LiveReadout], so what a frame looks like cannot drift apart.
 *
 * No recording, practice or place yet, and a source that needs no permission: the microphone of iOS, with its
 * permission and its failures, comes next (the step after the fake one).
 */
class LivePresenter(
    private val source: PitchSource,
    private val config: IntonationConfig,
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    // Mode and lock change together, so they live in one value the engine reads atomically.
    private val target = MutableStateFlow(LiveTarget())

    // The engine is stateful and single-threaded: one per collection, every frame through it, the target read per
    // frame. conflate() drops only finished signals the screen had no time to show, never frames.
    private val signal: Flow<LiveSignal> = flow {
        val engine = IntonationEngine(config)
        val readout = LiveReadout(config)
        emitAll(
            source.frames(config).map { frame ->
                readout.signalOf(frame, engine.process(frame, LiveReducer.targetModeOf(target.value)))
            },
        )
    }.flowOn(dispatcher).conflate()

    val state: StateFlow<LiveState> = combine(target, signal) { target, signal ->
        LiveReducer.stateOf(target, config, signal)
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = LiveReducer.stateOf(target.value, config, LiveSignal.Silence),
    )

    /** The mode and the strings; the rest of the intents belong to parts iOS does not have yet. */
    fun onIntent(intent: LiveIntent) {
        when (intent) {
            is LiveIntent.SelectMode -> target.update { LiveReducer.selectMode(it, intent.mode) }
            is LiveIntent.StringClicked -> target.update { LiveReducer.clickString(it, intent.string) }
            else -> Unit
        }
    }

    private companion object {
        // As on Android: long enough for a rotation, short enough that the source stops soon after the screen goes.
        const val STOP_TIMEOUT_MS = 2_000L
    }
}
