package com.violinjourney.app.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.violinjourney.app.core.analytics.Analytics
import com.violinjourney.app.core.analytics.FramePicture
import com.violinjourney.app.core.analytics.NoOpAnalytics
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.ViolinString
import com.violinjourney.app.core.domain.practice.FinishPracticeAsk
import com.violinjourney.app.core.domain.practice.RunningPracticeStore
import com.violinjourney.app.core.domain.practice.elapsedTicker
import com.violinjourney.app.core.domain.session.RecordingRibbon
import com.violinjourney.app.core.domain.venue.Venue
import com.violinjourney.app.core.domain.venue.Venues
import com.violinjourney.app.core.recording.TakePipeline
import com.violinjourney.app.core.settings.IntonationConfigSource
import com.violinjourney.app.core.time.WallClock
import kotlin.math.roundToInt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

open class LiveViewModel(
    private val takes: TakePipeline,
    private val configSource: IntonationConfigSource,
    private val runningPractice: RunningPracticeStore,
    private val clock: WallClock,
    private val venues: Venues,
    private val analytics: Analytics = NoOpAnalytics(),
    private val finishAsk: FinishPracticeAsk = FinishPracticeAsk(),
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

    // A tap on the practice tag still being answered: a second tap before the store has taken the first is dropped —
    // else a double tap on «Начать занятие» would start the practice and at once ask to finish it.
    private var tagAnswer: Job? = null

    private val effectChannel = Channel<LiveEffect>(Channel.BUFFERED)
    val effects: Flow<LiveEffect> = effectChannel.receiveAsFlow()

    /**
     * The chain itself is [TakePipeline], shared with the takes of the repertoire; Live's part is
     * what a frame looks like on screen. A new config gets a new readout along with the new engine.
     */
    private fun pipeline(config: IntonationConfig): Flow<TakePipeline.Output<LiveReadout.Shown>> {
        val readout = LiveReadout(config)
        // One picture per visit (spec 3.34): the flow ends when the screen goes away or the config
        // changes, and that is exactly when the picture is whole.
        val picture = FramePicture(config.toleranceCents.roundToInt(), config.a4Hz.roundToInt(), config.silenceRms)
        return takes.run(
            config = config,
            pieceId = null,
            targetMode = { LiveReducer.targetModeOf(target.value) },
            unavailable = LiveReadout.Shown(LiveSignal.MicUnavailable),
            onRestart = readout::reset,
            present = { frame, reading ->
                picture.add(frame, reading)
                readout.shownOf(frame, reading)
            },
        ).onCompletion { picture.finish()?.let(analytics::track) }
    }

    // null = not reported yet: stay silent instead of flashing the permission prompt at users
    // who have already granted it. The source is never collected without the permission.
    private val micPermissionGranted = MutableStateFlow(if (takes.requiresMicPermission) null else true)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val output: Flow<TakePipeline.Output<LiveReadout.Shown>> =
        combine(micPermissionGranted, configSource.config, ::Pair).flatMapLatest { (granted, config) ->
            when (granted) {
                null -> flowOf(TakePipeline.Output(LiveReadout.Shown(LiveSignal.Silence)))
                false -> flowOf(TakePipeline.Output(LiveReadout.Shown(LiveSignal.NoMicPermission)))
                true -> pipeline(config)
            }
        }

    /** Where Live takes place (spec 3.27): chosen on the journey, only shown here. */
    private val around: Flow<Pair<Long?, Venue>> = combine(runningPractice.elapsedTicker(clock), venues.current, ::Pair)

    /** A frame on screen: the words, and the numbers that move with every frame ([LiveGauge]). */
    private class Screen(val state: LiveState, val gauge: LiveGauge)

    // the captions of the strings, made again only when the configuration changes, not on every frame
    private var captionsOf: IntonationConfig? = null
    private var captions: Map<ViolinString, Int> = emptyMap()

    // One collection of the microphone for both: the state and the gauge are cut from it below.
    private val screen: StateFlow<Screen> =
        combine(
            target, configSource.config, recordingRequested, output, around,
        ) { target, config, requested, output, (practiceMs, venue) ->
            Screen(stateOf(target, config, requested, output, practiceMs, venue), gaugeOf(requested, output))
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = Screen(
                stateOf(target.value, configSource.default, false, TakePipeline.Output(LiveReadout.Shown(LiveSignal.Silence)), null, null),
                LiveGauge(),
            ),
        )

    /** What Live says: new only when a word, a shape or a whole second changes — a few times a second at most. */
    val state: StateFlow<LiveState> =
        screen.map { it.state }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), screen.value.state)

    /** What moves on Live with every frame (spec 5.8): read while drawing only ([LiveGauge]). */
    val gauge: StateFlow<LiveGauge> =
        screen.map { it.gauge }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), screen.value.gauge)

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
            LiveIntent.PracticeTagClicked -> if (tagAnswer?.isActive != true) tagAnswer = viewModelScope.launch {
                // the store, not the state on screen: a tap in the second the practice starts or ends must not do both
                if (runningPractice.running.first() == null) {
                    // the same start as on «Занятия» (spec 3.12), but Live stays: here one is already where one plays
                    runningPractice.start(clock.millis())
                } else {
                    finishAsk.ask()
                    effectChannel.send(LiveEffect.FinishPractice)
                }
            }
            LiveIntent.SettingsClicked -> effectChannel.trySend(LiveEffect.OpenSettings)
        }
    }

    private fun stateOf(
        target: LiveTarget,
        config: IntonationConfig,
        recordingRequested: Boolean,
        output: TakePipeline.Output<LiveReadout.Shown>,
        practiceMs: Long?,
        venue: Venue?,
    ) = LiveReducer.stateOf(
        target = target,
        config = config,
        signal = output.shown.signal,
        // The wish shows at once; the time follows with the first recorded frame — in whole seconds, as the timer shows it.
        recording = if (recordingRequested) {
            RecordingState((output.recording?.elapsedMs ?: 0) / MS_PER_SECOND * MS_PER_SECOND)
        } else {
            null
        },
        practiceMs = practiceMs,
        venue = venue,
        stringHz = captionsFor(config),
    )

    /** The gauge of a frame, and the notes of the take while one is recorded — none yet before its first frame. */
    private fun gaugeOf(recordingRequested: Boolean, output: TakePipeline.Output<LiveReadout.Shown>): LiveGauge =
        if (recordingRequested) {
            output.shown.gauge.copy(ribbon = output.recording?.ribbon ?: RecordingRibbon.EMPTY)
        } else {
            output.shown.gauge
        }

    private fun captionsFor(config: IntonationConfig): Map<ViolinString, Int> {
        if (config != captionsOf) {
            captions = LiveReducer.stringHzOf(config)
            captionsOf = config
        }
        return captions
    }

    private companion object {
        // Long enough to survive a configuration change, short enough that the pitch source
        // (the microphone later on) is released soon after the screen goes away.
        const val STOP_TIMEOUT_MS = 2_000L
        const val MS_PER_SECOND = 1_000L
    }
}
