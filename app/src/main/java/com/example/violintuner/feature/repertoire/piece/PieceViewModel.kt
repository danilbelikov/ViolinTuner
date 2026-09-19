package com.example.violintuner.feature.repertoire.piece

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.violintuner.core.data.repertoire.SheetFiles
import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.IntonationReading
import com.example.violintuner.core.domain.LoudnessMeter
import com.example.violintuner.core.domain.TargetMode
import com.example.violintuner.core.domain.repertoire.RepertoireConfig
import com.example.violintuner.core.domain.repertoire.RepertoireRepository
import com.example.violintuner.core.domain.session.SessionRepository
import com.example.violintuner.core.domain.sound.SoundConfig
import com.example.violintuner.core.domain.sound.SoundRepository
import com.example.violintuner.core.recording.TakePipeline
import com.example.violintuner.core.settings.IntonationConfigSource
import com.example.violintuner.feature.history.Selection
import com.example.violintuner.feature.history.SelectionIntent
import com.example.violintuner.feature.history.SelectionRules
import com.example.violintuner.feature.sound.SoundCaption
import com.example.violintuner.feature.sound.SoundReducer
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltViewModel
class PieceViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val repertoire: RepertoireRepository,
    private val sheetFiles: SheetFiles,
    private val config: RepertoireConfig,
    private val clock: Clock,
    private val takes: TakePipeline,
    private val configSource: IntonationConfigSource,
    private val sessions: SessionRepository,
    private val intonationConfig: IntonationConfig,
    sound: SoundRepository,
    soundConfig: SoundConfig,
) : ViewModel() {
    private val pieceId: Long = checkNotNull(savedState[ARG_PIECE_ID]) { "piece id is required" }

    /**
     * Takes whose sound is their own, by what it is set to: the row of such a take names it, so
     * that it is seen where the processing differs from everyone's (spec 3.17). Apart from
     * [state]: it has nothing to do with the piece.
     */
    val takeSounds: StateFlow<Map<Long, SoundCaption>> = combine(sound.own, sound.presets) { own, presets ->
        own.mapValues { (_, settings) -> SoundReducer.captionOf(settings, presets, soundConfig) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyMap())

    /** What only the screen decides: photos on their way in, the open menu, the takes being picked. */
    private data class Ui(val importing: Int = 0, val statusMenuOpen: Boolean = false, val selection: Selection = Selection())

    // The takes the list shows now: only those can be picked. Written where the state is built,
    // read by the intents — both on the main thread; `state.value` would lag a frame behind.
    private var takeIds: List<Long> = emptyList()

    private val ui = MutableStateFlow(Ui())
    private var closed = false
    private val effectChannel = Channel<PieceEffect>(Channel.BUFFERED)
    val effects: Flow<PieceEffect> = effectChannel.receiveAsFlow()

    /** The take recorded a moment ago, while it is still highlighted in the list. */
    private val newTakeId = MutableStateFlow<Long?>(null)

    val state: StateFlow<PieceState> = combine(
        repertoire.pieces, repertoire.pages, ui, sessions.sessions, newTakeId,
    ) { pieces, pages, ui, sessions, newTakeId ->
        val piece = pieces.firstOrNull { it.id == pieceId }
        if (piece == null) {
            // Deleted from its form, or an id from nowhere: there is nothing to show. Said once:
            // a second "close" would take the screen underneath with it.
            if (!closed) effectChannel.trySend(PieceEffect.Close)
            closed = true
            PieceReducer.loading(config)
        } else {
            val shown = PieceReducer.stateOf(
                piece, pages, ui.importing, ui.statusMenuOpen, config,
                takes = PieceReducer.takesOf(pieceId, sessions, newTakeId, LocalDate.now(clock), clock.zone, intonationConfig),
                progress = PieceReducer.progressOf(pieceId, sessions, config),
            ) { sheetFiles.existing(it)?.path }
            takeIds = shown.takes.map { it.card.id }
            shown.copy(selection = SelectionRules.prune(ui.selection, takeIds))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PieceReducer.loading(config))

    // null = not reported yet. The microphone is asked for only when a take is: this screen does not listen by itself.
    private val micPermission = MutableStateFlow(if (takes.requiresMicPermission) null else true)

    // True from the tap on "record" until the chain has dealt with the stop. The source is
    // collected only in between: a piece's screen has no business holding the microphone.
    private val listening = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val takeState: StateFlow<TakeState> = combine(listening, micPermission, configSource.config, ::Triple)
        .flatMapLatest { (wanted, granted, intonation) ->
            if (!wanted || granted != true) {
                flowOf(TakeState.idle(granted, config.levelBars))
            } else {
                blindChain(intonation)
                    .map { output ->
                        // The stop is the chain's to handle, on its next frame: that is where a take
                        // without notes is told apart from one the player simply left. Only then
                        // may the microphone go.
                        if (!takes.recordingRequested.value && output.recording == null) listening.value = false
                        PieceReducer.takeStateOf(output.shown, output.recording, takes.recordingRequested.value, granted, config.levelBars)
                    }
                    // Cancelled from outside — the screen left for good, the settings changed: the
                    // take has been saved quietly, and coming back must not reopen the microphone.
                    .onCompletion { listening.value = false }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(TAKE_STOP_TIMEOUT_MS), TakeState.idle(micPermission.value, config.levelBars))

    /** Blind on purpose (spec 3.15): of all the engine reads, only "too noisy" reaches the screen, beside how loud it is. */
    private fun blindChain(intonation: IntonationConfig): Flow<TakePipeline.Output<BlindShown>> {
        val meter = LoudnessMeter(intonation)
        val history = LevelHistory(config.levelBars, config.levelBarMs)
        val silent = List(config.levelBars) { 0f }
        return takes.run(
            config = intonation,
            pieceId = pieceId,
            targetMode = { TargetMode.Chromatic },
            unavailable = BlindShown(silent, TakeProblem.MIC_UNAVAILABLE),
            onRestart = {
                meter.reset()
                history.reset()
            },
        ) { frame, reading ->
            BlindShown(
                levels = history.add(frame.tMs, meter.process(frame.tMs, frame.rms)),
                problem = TakeProblem.TOO_NOISY.takeIf { reading == IntonationReading.TooNoisy },
            )
        }
    }

    init {
        viewModelScope.launch { takes.watchPractice() }
        viewModelScope.launch {
            takes.events.collect { event ->
                when (event) {
                    // The player stays with the music: the take shows up in the list, highlighted
                    // for a moment, and the session screen does not open by itself (spec 3.15).
                    is TakePipeline.Event.Saved -> highlight(event.sessionId)
                    TakePipeline.Event.NoNotes -> effectChannel.send(PieceEffect.ShowNoNotesRecorded)
                }
            }
        }
    }

    private fun highlight(sessionId: Long) {
        newTakeId.value = sessionId
        viewModelScope.launch {
            delay(config.newTakeHighlightMs)
            newTakeId.update { if (it == sessionId) null else it }
        }
    }

    // Photos go in one at a time and in the order they were picked: that is the order of the pages.
    private val importLock = Mutex()

    fun onIntent(intent: PieceIntent) {
        when (intent) {
            PieceIntent.BackClicked -> effectChannel.trySend(PieceEffect.Close)
            PieceIntent.EditClicked -> effectChannel.trySend(PieceEffect.OpenForm(pieceId, focusNotes = false))
            PieceIntent.AddNotesClicked -> effectChannel.trySend(PieceEffect.OpenForm(pieceId, focusNotes = true))
            PieceIntent.StatusChipClicked -> ui.update { it.copy(statusMenuOpen = true) }
            PieceIntent.StatusMenuDismissed -> ui.update { it.copy(statusMenuOpen = false) }
            is PieceIntent.StatusSelected -> {
                ui.update { it.copy(statusMenuOpen = false) }
                viewModelScope.launch { repertoire.setStatus(pieceId, intent.status, clock.millis()) }
            }
            is PieceIntent.PageClicked -> effectChannel.trySend(PieceEffect.OpenStand(pieceId, intent.index))
            is PieceIntent.PhotosPicked -> import(intent.uris, temporary = emptyList())
            PieceIntent.CameraClicked -> {
                val file = sheetFiles.newCameraFile()
                // The camera app may push this process out of memory: the path has to outlive it.
                savedState[KEY_CAMERA_FILE] = file.path
                effectChannel.trySend(PieceEffect.LaunchCamera(file.path))
            }
            PieceIntent.RecordClicked -> when {
                // picking and recording do not mix (spec 3.18): the button is dimmed, this is the belt to those braces
                ui.value.selection.active -> Unit
                takes.recordingRequested.value -> takes.recordingRequested.value = false
                micPermission.value != true -> effectChannel.trySend(PieceEffect.RequestMicPermission)
                else -> {
                    takes.recordingRequested.value = true
                    listening.value = true
                }
            }
            PieceIntent.GrantMicClicked -> effectChannel.trySend(PieceEffect.RequestMicPermission)
            is PieceIntent.MicPermissionChanged -> if (takes.requiresMicPermission) micPermission.value = intent.granted
            is PieceIntent.TakeClicked ->
                if (ui.value.selection.active) select(SelectionIntent.CardToggled(intent.sessionId)) else effectChannel.trySend(PieceEffect.OpenSession(intent.sessionId))
            is PieceIntent.Select -> select(intent.intent)
            is PieceIntent.CameraFinished -> {
                val file = savedState.remove<String>(KEY_CAMERA_FILE)?.let(::File) ?: return
                if (intent.saved) import(listOf(file.toURI().toString()), temporary = listOf(file)) else file.delete()
            }
        }
    }

    private fun select(intent: SelectionIntent) {
        val current = SelectionRules.prune(ui.value.selection, takeIds)
        // While a take is being recorded — or the chain has not let the microphone go yet — the mode does not open.
        if (!current.active && (takes.recordingRequested.value || listening.value)) return
        if (intent == SelectionIntent.DeleteConfirmed && current.ids.isNotEmpty()) {
            viewModelScope.launch { sessions.delete(current.ids) }
        }
        ui.update { it.copy(selection = SelectionRules.reduce(current, intent, takeIds)) }
    }

    private fun import(uris: List<String>, temporary: List<File>) {
        if (uris.isEmpty()) return
        ui.update { it.copy(importing = it.importing + uris.size) }
        viewModelScope.launch {
            var failed = false
            importLock.withLock {
                uris.forEach { uri ->
                    val stored = sheetFiles.import(uri)
                    if (stored == null) failed = true else repertoire.addPage(pieceId, stored.fileName, stored.thumbFileName, clock.millis())
                    ui.update { it.copy(importing = it.importing - 1) }
                }
            }
            temporary.forEach { it.delete() }
            // One word for the whole batch: the pages that did open are already in the strip.
            if (failed) effectChannel.send(PieceEffect.ShowPhotoFailed)
        }
    }

    companion object {
        const val ARG_PIECE_ID = "pieceId"
        private const val KEY_CAMERA_FILE = "cameraFile"
        private const val STOP_TIMEOUT_MS = 5_000L

        // Long enough to survive a rotation, short enough that the microphone goes soon after the screen does (as on Live).
        private const val TAKE_STOP_TIMEOUT_MS = 2_000L
    }
}
