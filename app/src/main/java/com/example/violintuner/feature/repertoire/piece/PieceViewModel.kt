package com.example.violintuner.feature.repertoire.piece

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.violintuner.core.audio.share.ShareFiles
import com.example.violintuner.core.audio.backing.AudioRoutes
import com.example.violintuner.core.audio.backing.BackingFileImporter
import com.example.violintuner.core.audio.backing.BackingImport
import com.example.violintuner.core.audio.backing.BackingPcm
import com.example.violintuner.core.audio.backing.BackingPreview
import com.example.violintuner.core.di.IoDispatcher
import com.example.violintuner.core.domain.backing.AudioRoute
import com.example.violintuner.core.domain.backing.BackingOffset
import com.example.violintuner.core.domain.backing.BackingConfig
import com.example.violintuner.core.domain.backing.Backing
import com.example.violintuner.core.domain.backing.BackingFiles
import com.example.violintuner.core.domain.backing.BackingOutput
import com.example.violintuner.core.domain.backing.BackingRepository
import com.example.violintuner.core.domain.backing.NoBackings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import com.example.violintuner.core.data.repertoire.SheetFiles
import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.IntonationReading
import com.example.violintuner.core.domain.LoudnessMeter
import com.example.violintuner.core.domain.TargetMode
import com.example.violintuner.core.domain.repertoire.RepertoireConfig
import com.example.violintuner.core.domain.repertoire.RepertoireRepository
import com.example.violintuner.core.domain.session.SessionRepository
import com.example.violintuner.core.recording.TakePipeline
import com.example.violintuner.core.recording.video.VideoFiles
import com.example.violintuner.core.recording.video.VideoImport
import com.example.violintuner.core.recording.video.VideoTakeImporter
import com.example.violintuner.core.settings.IntonationConfigSource
import com.example.violintuner.feature.history.Selection
import com.example.violintuner.feature.history.SelectionIntent
import com.example.violintuner.feature.history.SelectionRules
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
    private val videos: VideoFiles,
    private val importer: VideoTakeImporter,
    private val shareFiles: ShareFiles,
    private val backings: BackingRepository = NoBackings,
    private val backingFiles: BackingFiles? = null,
    private val backingPcm: BackingPcm? = null,
    private val backingImporter: BackingFileImporter? = null,
    private val backingPreview: BackingPreview? = null,
    private val routes: AudioRoutes? = null,
    @IoDispatcher private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val pieceId: Long = checkNotNull(savedState[ARG_PIECE_ID]) { "piece id is required" }

    /** What only the screen decides: photos on their way in, the open menu, the takes being picked. */
    private data class Ui(val importing: Int = 0, val statusMenuOpen: Boolean = false, val selection: Selection = Selection())

    // The takes the list shows now: only those can be picked. Written where the state is built,
    // read by the intents — both on the main thread; `state.value` would lag a frame behind.
    private var takeIds: List<Long> = emptyList()

    private val ui = MutableStateFlow(Ui())
    private var closed = false
    private val effectChannel = Channel<PieceEffect>(Channel.BUFFERED)
    val effects: Flow<PieceEffect> = effectChannel.receiveAsFlow()

    /**
     * A video on its way to becoming a take of this piece (spec 3.19). The importer is a singleton
     * that outlives the screen; what it does for another piece is none of this screen's business.
     */
    val videoImport: StateFlow<VideoImport> = importer.state
        .map { import ->
            val owner = when (import) {
                is VideoImport.Working -> import.pieceId
                is VideoImport.Failed -> import.pieceId
                VideoImport.Idle -> pieceId
            }
            if (owner == pieceId) import else VideoImport.Idle
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), VideoImport.Idle)

    /** The take recorded a moment ago, while it is still highlighted in the list. */
    private val newTakeId = MutableStateFlow<Long?>(null)

    val state: StateFlow<PieceState> = combine(
        repertoire.pieces, repertoire.pages, ui, combine(sessions.sessions, backings.takeBackings, ::Pair), newTakeId,
    ) { pieces, pages, ui, (sessions, underBacking), newTakeId ->
        val underBackingIds = underBacking.mapTo(HashSet()) { it.sessionId }
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
                takes = PieceReducer.takesOf(piece, sessions, newTakeId, LocalDate.now(clock), clock.zone),
                progress = PieceReducer.progressOf(pieceId, sessions, config),
            ) { sheetFiles.existing(it)?.path }
            takeIds = shown.takes.map { it.card.id }
            val videoNames = sessions.mapNotNull { session -> session.videoPath?.let { session.id to it } }.toMap()
            shown.copy(
                takes = shown.takes.map { take ->
                    val withVideo = videoNames[take.card.id]?.let { take.copy(card = take.card.copy(videoBytes = videos.existing(it)?.length() ?: 0)) } ?: take
                    if (take.card.id in underBackingIds) withVideo.copy(underBacking = true) else withVideo
                },
                selection = SelectionRules.prune(ui.selection, takeIds),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PieceReducer.loading(config))

    // null = not reported yet. The microphone is asked for only when a take is: this screen does not listen by itself.
    private val micPermission = MutableStateFlow(if (takes.requiresMicPermission) null else true)

    // True from the tap on "record" until the chain has dealt with the stop. The source is
    // collected only in between: a piece's screen has no business holding the microphone.
    private val listening = MutableStateFlow(false)

    /** What only the screen knows of the backing: a file on its way in, one that did not open, its sound being prepared. */
    private data class BackingEphemeral(
        val importing: Boolean = false,
        val problem: BackingProblem? = null,
        val preparing: Boolean = false,
        val askingRemove: Boolean = false,
    )

    private val backingConfig = BackingConfig()
    private val backingEphemeral = MutableStateFlow(BackingEphemeral())
    private val route = routes?.changes ?: flowOf(AudioRoute(BackingOutput.SPEAKER, null))
    private val previewing = backingPreview?.playing ?: flowOf(false)

    /** The block «Минусовка» (spec 3.32); apart from [state], as the take is: it follows the headphones, which come and go. */
    val backing: StateFlow<BackingUi?> = combine(
        combine(backings.pieceBackings, backings.backings, backings.takeBackings, ::Triple),
        sessions.sessions,
        route,
        backingEphemeral,
        previewing,
    ) { (pieceRows, all, takeRows), allSessions, currentRoute, ephemeral, playing ->
        PieceBackingReducer.uiOf(
            pieceId = pieceId, pieceBackings = pieceRows, backings = all, takeBackings = takeRows,
            takeIds = allSessions.filter { it.pieceId == pieceId }.mapTo(HashSet()) { it.id },
            route = currentRoute,
            fileExists = { backingFiles?.existing(it.fileName) != null },
            importing = ephemeral.importing, problem = ephemeral.problem, previewing = playing, preparing = ephemeral.preparing,
            askingRemove = ephemeral.askingRemove,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val blindTake: Flow<TakeState> = combine(listening, micPermission, configSource.config, ::Triple)
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


    /** The blind take, and — under a backing — how far the backing has played (spec 3.32). */
    val takeState: StateFlow<TakeState> = combine(blindTake, takes.backingPosition, backing) { take, played, block ->
        if (take.recording && played != null) take.copy(backingPlayedMs = played, backingDurationMs = block?.durationMs) else take
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(TAKE_STOP_TIMEOUT_MS), TakeState.idle(micPermission.value, config.levelBars))

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
        // The backing's sound is made ready for the mix as soon as the piece has one (spec 5.25): a take must not wait for a decoder.
        viewModelScope.launch {
            combine(backings.pieceBackings, backings.backings) { rows, all ->
                rows.firstOrNull { it.pieceId == pieceId }?.let { row -> all.firstOrNull { it.id == row.backingId } }
            }.distinctUntilChanged().collect { found -> if (found != null) prepare(found) }
        }
        // A video take lands in the list the way a recorded one does: on top, highlighted, the session screen shut.
        viewModelScope.launch { importer.saved.collect { if (it.pieceId == pieceId) highlight(it.sessionId) } }
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
            PieceIntent.EditClicked -> effectChannel.trySend(PieceEffect.OpenForm(pieceId, focusNotes = false, scale = state.value.scale != null))
            PieceIntent.AddNotesClicked -> effectChannel.trySend(PieceEffect.OpenForm(pieceId, focusNotes = true, scale = state.value.scale != null))
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
                else -> startTake()
            }
            PieceIntent.StandRecordClicked -> when {
                takes.recordingRequested.value -> takes.recordingRequested.value = false
                micPermission.value != true -> effectChannel.trySend(PieceEffect.RequestMicPermission)
                else -> startTake(underBacking = false)
            }
            PieceIntent.GrantMicClicked -> effectChannel.trySend(PieceEffect.RequestMicPermission)
            is PieceIntent.MicPermissionChanged -> if (takes.requiresMicPermission) micPermission.value = intent.granted
            is PieceIntent.TakeClicked ->
                if (ui.value.selection.active) select(SelectionIntent.CardToggled(intent.sessionId)) else effectChannel.trySend(PieceEffect.OpenSession(intent.sessionId))
            is PieceIntent.Select -> select(intent.intent)
            is PieceIntent.BestToggled -> toggleBest(intent.sessionId)
            PieceIntent.VideoShootClicked -> if (videoAllowed()) {
                val file = videos.newCameraFile()
                // The camera app may push this process out of memory: the path has to outlive it.
                savedState[KEY_VIDEO_FILE] = file.path
                effectChannel.trySend(PieceEffect.LaunchVideoCamera(file.path))
            }
            // under the backing, the same rule as a take: headphones only (spec 3.32); a plain video needs none
            PieceIntent.OwnCameraClicked -> backing.value?.takeIf { it.present && videoAllowed() && (!it.wanted || it.route.output.isHeadphones) }?.let {
                effectChannel.trySend(PieceEffect.OpenCapture(pieceId))
            }
            is PieceIntent.VideoShotFinished -> {
                val file = savedState.remove<String>(KEY_VIDEO_FILE)?.let(::File) ?: return
                if (intent.saved) importer.shot(pieceId, file) else file.delete()
            }
            is PieceIntent.VideoPicked -> if (intent.uri != null && videoAllowed()) importer.picked(pieceId, intent.uri)
            PieceIntent.VideoImportCancelClicked -> importer.cancelClicked()
            PieceIntent.VideoImportContinueClicked -> importer.continueClicked()
            PieceIntent.VideoImportDismissed -> importer.dismiss()
            PieceIntent.VideoImportSendClicked -> importer.sendClicked()?.let { path ->
                viewModelScope.launch {
                    // The provider hands out only `cache/share/`: the shot gets a second name there, not a copy.
                    shareFiles.original(File(path), RESCUE_FILE_NAME)?.let { effectChannel.send(PieceEffect.ShareVideo(it.path)) }
                }
            }
            PieceIntent.BackingAddClicked -> if (!takes.recordingRequested.value) effectChannel.trySend(PieceEffect.PickBackingFile)
            is PieceIntent.BackingPicked -> intent.uri?.let(::importBacking)
            PieceIntent.BackingPreviewClicked -> previewBacking()
            PieceIntent.BackingRemoveClicked -> {
                val block = backing.value
                if (block?.takesUnder ?: 0 > 0) backingEphemeral.update { it.copy(askingRemove = true) } else removeBacking()
            }
            PieceIntent.BackingRemoveConfirmed -> removeBacking()
            PieceIntent.BackingRemoveDismissed -> backingEphemeral.update { it.copy(askingRemove = false) }
            PieceIntent.BackingChipToggled -> backing.value?.takeIf { it.present && !takes.recordingRequested.value }?.let { block ->
                viewModelScope.launch { backings.setEnabled(pieceId, !block.enabled) }
            }
            PieceIntent.BackingProblemDismissed -> backingEphemeral.update { it.copy(problem = null) }
            is PieceIntent.CameraFinished -> {
                val file = savedState.remove<String>(KEY_CAMERA_FILE)?.let(::File) ?: return
                if (intent.saved) import(listOf(file.toURI().toString()), temporary = listOf(file)) else file.delete()
            }
        }
    }

    /** A take, under the backing when the chip is on (spec 3.32): never through the speaker. */
    private fun startTake(underBacking: Boolean = true) {
        val block = backing.value
        if (underBacking && block != null && block.wanted) {
            if (block.blocksRecording) return
            val found = backingOf() ?: return
            val pcm = backingPcm ?: return
            backingPreview?.stop()
            takes.backingPlan = TakePipeline.BackingPlan(
                backing = found,
                pcm = { rate -> pcm.cached(found, rate) ?: pcm.prepare(found, rate) },
                route = block.route,
                latencyMs = BackingOffset.latencyMs(block.route, backingConfig),
            )
        } else {
            takes.backingPlan = null
        }
        takes.recordingRequested.value = true
        listening.value = true
    }


    private var knownBacking: Backing? = null

    private fun backingOf(): Backing? = knownBacking

    private fun prepare(found: Backing) {
        knownBacking = found
        val pcm = backingPcm ?: return
        viewModelScope.launch {
            backingEphemeral.update { it.copy(preparing = true) }
            withContext(io) { PREPARED_RATES.forEach { rate -> pcm.cached(found, rate) ?: pcm.prepare(found, rate) } }
            backingEphemeral.update { it.copy(preparing = false) }
        }
    }

    private fun importBacking(uri: String) {
        val importer = backingImporter ?: return
        backingEphemeral.update { it.copy(importing = true, problem = null) }
        viewModelScope.launch {
            val result = withContext(io) { importer.import(uri) }
            val problem = when (result) {
                is BackingImport.Added -> {
                    val id = backings.add(result.backing)
                    backings.setForPiece(pieceId, id)
                    null
                }
                BackingImport.Unreadable -> BackingProblem.Unreadable
                BackingImport.TooLong -> BackingProblem.TooLong
                is BackingImport.NoSpace -> BackingProblem.NoSpace((result.neededBytes + BYTES_PER_MB - 1) / BYTES_PER_MB)
            }
            backingEphemeral.update { it.copy(importing = false, problem = problem) }
        }
    }

    private fun previewBacking() {
        val found = backingOf() ?: return
        if (takes.recordingRequested.value) return
        val file = backingFiles?.existing(found.fileName) ?: return
        backingPreview?.toggle(file)
    }

    private fun removeBacking() {
        backingEphemeral.update { it.copy(askingRemove = false) }
        backingPreview?.stop()
        knownBacking = null
        viewModelScope.launch { backings.setForPiece(pieceId, null) }
    }

    override fun onCleared() {
        backingPreview?.stop()
    }

    // Two takes are not made at once, and takes are not made while others are being picked for deletion.
    private fun videoAllowed(): Boolean =
        !takes.recordingRequested.value && !listening.value && !ui.value.selection.active && importer.state.value == VideoImport.Idle

    private fun toggleBest(sessionId: Long) {
        // Read from what is on screen: the one place that already knows which take carries the mark.
        val take = state.value.takes.firstOrNull { it.card.id == sessionId } ?: return
        viewModelScope.launch { repertoire.setBestTake(pieceId, if (take.best) null else sessionId) }
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
        private const val KEY_VIDEO_FILE = "videoFile"
        private const val RESCUE_FILE_NAME = "video.mp4"
        private const val STOP_TIMEOUT_MS = 5_000L

        private const val BYTES_PER_MB = 1024L * 1024

        /** The rates a take is recorded at (spec 5.1): the backing is made ready for both. */
        private val PREPARED_RATES = listOf(48_000, 44_100)

        // Long enough to survive a rotation, short enough that the microphone goes soon after the screen does (as on Live).
        private const val TAKE_STOP_TIMEOUT_MS = 2_000L
    }
}
