package com.violinjourney.app.feature.events.screen

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.violinjourney.app.core.audio.share.ShareFiles
import com.violinjourney.app.core.audio.share.ShareNames
import com.violinjourney.app.core.domain.events.CalendarEvent
import com.violinjourney.app.core.domain.events.EditScope
import com.violinjourney.app.core.domain.events.EventKind
import com.violinjourney.app.core.domain.events.EventRepository
import com.violinjourney.app.core.domain.events.EventSeries
import com.violinjourney.app.core.domain.events.EventsConfig
import com.violinjourney.app.core.domain.events.KindRules
import com.violinjourney.app.core.domain.events.SeriesEdits
import com.violinjourney.app.core.domain.events.SessionEvent
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.domain.repertoire.RepertoireRepository
import com.violinjourney.app.core.domain.session.SessionRepository
import com.violinjourney.app.core.domain.UserSettings
import com.violinjourney.app.core.domain.VideoQuality
import com.violinjourney.app.core.io.deleteFile
import com.violinjourney.app.core.io.filePath
import com.violinjourney.app.core.io.platformFile
import com.violinjourney.app.core.recording.MediaImport
import com.violinjourney.app.core.recording.TakeOwner
import com.violinjourney.app.core.recording.TakePipeline
import com.violinjourney.app.core.recording.audio.AudioTakeImporter
import com.violinjourney.app.core.recording.of
import com.violinjourney.app.core.recording.video.VideoFiles
import com.violinjourney.app.core.recording.video.VideoTakeImporter
import com.violinjourney.app.core.settings.IntonationConfigSource
import com.violinjourney.app.core.time.WallClock
import com.violinjourney.app.core.time.dates
import com.violinjourney.app.core.time.today
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.feature.events.EventWords
import com.violinjourney.app.feature.events.ResourceEventWords
import com.violinjourney.app.feature.repertoire.piece.BlindTake
import com.violinjourney.app.feature.repertoire.piece.ImportAction
import com.violinjourney.app.feature.repertoire.piece.ImportWords
import com.violinjourney.app.feature.repertoire.piece.PieceReducer
import com.violinjourney.app.feature.repertoire.piece.TakeState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The file on its way in that the screen of an event shows its sheet for, and in whose words (plan D19). */
data class EventImport(val import: MediaImport, val words: ImportWords)

/**
 * The screen of an event (spec 3.35, 3.36.9): its head, its notes, its programme and its records, and the four ways to add a recording —
 * a sound recorded blind as a take is ([BlindTake]), a video of the system camera or of the gallery ([VideoTakeImporter]), a sound from a
 * file ([AudioTakeImporter]); its recordings are the event's ([TakeOwner.Event]). A sound recorded here marks the sound of the running
 * practice and counts its notes, as a take does (plan D35: the chain follows the practice from the first moment); a shot of the camera
 * marks it too, a picked file does not. While a recording runs, every way off the screen and every change of it sleeps, as on the screen of
 * a piece; «Добавить запись» sleeps while any file is on its way in, whoever's it is (D37). The deletion of the event asks — the dialog of
 * a single one, the sheet of a repeat's — and the recordings of what goes keep the name they wore (D12).
 */
open class EventViewModel(
    private val savedState: SavedStateHandle,
    private val events: EventRepository,
    private val sessions: SessionRepository,
    private val repertoire: RepertoireRepository,
    private val configSource: IntonationConfigSource,
    private val takes: TakePipeline,
    private val importer: VideoTakeImporter,
    private val audioImporter: AudioTakeImporter,
    private val videos: VideoFiles,
    private val shareFiles: ShareFiles,
    private val config: EventsConfig,
    private val repertoireConfig: RepertoireConfig,
    private val clock: WallClock,
    private val words: EventWords = ResourceEventWords,
    /** «Качество видео» of the settings (spec 3.19): the system camera is told it at each shot. */
    videoQuality: Flow<VideoQuality> = flowOf(UserSettings().videoQuality),
) : ViewModel() {
    private val eventId: Long = checkNotNull(savedState[ARG_EVENT_ID]) { "event id is required" }

    private val videoQuality = videoQuality.stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings().videoQuality)

    /** Whose recordings this screen makes (plan D13). */
    private val owner = TakeOwner.Event(eventId)

    /** What only the screen decides: its sheet and its dialog. */
    private data class Local(val sheet: EventSheet? = null, val dialog: EventDialog? = null)

    private val local = MutableStateFlow(Local())

    /** The recording added a moment ago, while it is still highlighted. */
    private val newRecordId = MutableStateFlow<Long?>(null)

    // null = not reported yet. The microphone is asked for only when a recording is: this screen does not listen by itself.
    private val micPermission = MutableStateFlow(if (takes.requiresMicPermission) null else true)

    // True from the press of «Записать звук» until the chain has dealt with the stop. The source is collected only in between.
    private val listening = MutableStateFlow(false)

    /** The event is being deleted from here: the screen keeps what it showed until it has closed, not «Событие не найдено». */
    private var deleting = false
    private var shown: EventState.Loaded? = null

    private val effectChannel = Channel<EventEffect>(Channel.BUFFERED)
    val effects: Flow<EventEffect> = effectChannel.receiveAsFlow()

    /** A file of anyone's on its way in: «Добавить запись» sleeps meanwhile with a spinner (D37; [blocks]). */
    private val busyImport: Flow<Boolean> = combine(importer.state, audioImporter.state) { video, sound -> blocks(video, sound) }

    /** The sheet of a file on its way in for this event: a video, or a sound from a file — one at a time. */
    val mediaImport: StateFlow<EventImport> = combine(importer.state, audioImporter.state) { video, sound ->
        val ours = video.of(owner)
        if (ours != MediaImport.Idle) EventImport(ours, ImportWords.VIDEO_RECORD) else EventImport(sound.of(owner), ImportWords.SOUND_FILE)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), EventImport(MediaImport.Idle, ImportWords.VIDEO_RECORD))

    private data class Stored(
        val all: List<CalendarEvent>,
        val kinds: List<EventKind>,
        val series: List<EventSeries>,
        val programs: Map<Long, List<Long>>,
        val recordEvents: Map<Long, SessionEvent>,
    )

    private val stored: Flow<Stored> = combine(events.events, events.kinds, events.series, events.programs, events.recordEvents, ::Stored)
    private val shelf = combine(repertoire.pieces, repertoire.groups, sessions.sessions, ::Triple)
    private val ui = combine(local, newRecordId, micPermission, busyImport) { local, newId, mic, busy ->
        EventReducer.Ui(sheet = local.sheet, dialog = local.dialog, newRecordId = newId, micPermission = mic, busyImport = busy)
    }

    val state: StateFlow<EventState> = combine(stored, shelf, ui, clock.dates()) { stored, (pieces, groups, records), ui, today ->
        val event = stored.all.firstOrNull { it.id == eventId }
        if (event == null) {
            // deleted from here: what was shown stays while the screen goes; deleted elsewhere — «Событие не найдено»
            shown?.takeIf { deleting } ?: EventState.NotFound
        } else {
            EventReducer.loadedOf(
                event = event, kinds = stored.kinds, series = stored.series, programIds = stored.programs[eventId].orEmpty(), pieces = pieces,
                groups = groups, sessions = records, recordEvent = stored.recordEvents[eventId], today = today, zone = clock.zone,
                config = config, notesCollapsedLines = repertoireConfig.notesCollapsedLines, ui = ui,
            ).also { shown = it }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), EventState.Loading)

    /** Blind on purpose (spec 3.15, 3.36.9): of all the engine reads, only «слишком шумно» reaches the screen, beside how loud it is. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val takeState: StateFlow<TakeState> = combine(listening, micPermission, configSource.config, ::Triple)
        .flatMapLatest { (wanted, granted, intonation) ->
            if (!wanted || granted != true) {
                flowOf(TakeState.idle(granted, repertoireConfig.levelBars))
            } else {
                BlindTake.chain(takes, intonation, owner, repertoireConfig)
                    .map { output ->
                        // The stop is the chain's to handle, on its next frame: only then may the microphone go.
                        if (!takes.recordingRequested.value && output.recording == null) listening.value = false
                        PieceReducer.takeStateOf(output.shown, output.recording, takes.recordingRequested.value, granted, repertoireConfig.levelBars)
                    }
                    // Cancelled from outside — the screen left for good: the recording has been saved quietly (spec 3.9).
                    .onCompletion { listening.value = false }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(TAKE_STOP_TIMEOUT_MS), TakeState.idle(micPermission.value, repertoireConfig.levelBars))

    init {
        // the sound mark of the running practice and its notes: the chain follows the practice from the first moment (D35)
        viewModelScope.launch { takes.watchPractice() }
        // a recording lands among the records the way it does on a piece: on top, highlighted, its screen shut (spec 3.36.9)
        viewModelScope.launch { importer.saved.collect { if (it.owner == owner) highlight(it.sessionId) } }
        viewModelScope.launch { audioImporter.saved.collect { if (it.owner == owner) highlight(it.sessionId) } }
        viewModelScope.launch {
            takes.events.collect { event ->
                when (event) {
                    is TakePipeline.Event.Saved -> highlight(event.sessionId)
                    // saved quietly — the microphone was lost: it stands among the records with the same highlight
                    is TakePipeline.Event.Kept -> highlight(event.sessionId)
                    TakePipeline.Event.NoNotes -> effectChannel.send(EventEffect.ShowNoNotes)
                }
            }
        }
    }

    private fun highlight(sessionId: Long) {
        newRecordId.value = sessionId
        viewModelScope.launch {
            delay(repertoireConfig.newTakeHighlightMs)
            newRecordId.update { if (it == sessionId) null else it }
        }
    }

    fun onIntent(intent: EventIntent) {
        when (intent) {
            EventIntent.BackClicked -> effectChannel.trySend(EventEffect.Close)
            // While a recording runs, the ways off the screen that would end it sleep (spec 3.36.9): dimmed, and this is the belt.
            EventIntent.EditClicked -> if (!recordingRuns()) effectChannel.trySend(EventEffect.OpenForm(eventId, focusNotes = false))
            EventIntent.AddNotesClicked -> if (!recordingRuns()) effectChannel.trySend(EventEffect.OpenForm(eventId, focusNotes = true))
            // nor while a file of this event is on its way in, seen or not yet: its recording would lose its event (review of stage 98a)
            EventIntent.DeleteClicked -> if (!recordingRuns() && !importingHere()) askDelete()
            is EventIntent.DeleteConfirmed -> delete(intent.scope)
            EventIntent.DeleteDismissed -> local.update { it.copy(dialog = null) }
            EventIntent.ProgramAddClicked -> if (!recordingRuns()) openProgram()
            is EventIntent.ProgramToggled -> local.update { now ->
                val sheet = now.sheet as? EventSheet.Program ?: return@update now
                now.copy(sheet = sheet.copy(checked = EventReducer.toggled(sheet.checked, intent.pieceId)))
            }
            EventIntent.ProgramDone -> doneProgram()
            is EventIntent.ProgramRemoved -> if (!recordingRuns()) viewModelScope.launch { events.removeFromProgram(eventId, intent.pieceId) }
            EventIntent.OpenRepertoireClicked -> {
                local.update { if (it.sheet is EventSheet.Program) it.copy(sheet = null) else it }
                effectChannel.trySend(EventEffect.OpenRepertoire)
            }
            is EventIntent.PieceClicked -> if (!recordingRuns()) effectChannel.trySend(EventEffect.OpenPiece(intent.pieceId))
            is EventIntent.RecordClicked -> if (!recordingRuns()) effectChannel.trySend(EventEffect.OpenSession(intent.sessionId))
            EventIntent.AddRecordClicked -> if (mayAdd()) local.update { if (it.sheet == null && it.dialog == null) it.copy(sheet = EventSheet.AddRecord) else it }
            is EventIntent.AddRecordWay -> addRecord(intent.way)
            EventIntent.GrantMicClicked -> effectChannel.trySend(EventEffect.RequestMicPermission)
            is EventIntent.MicPermissionChanged -> if (takes.requiresMicPermission) micPermission.value = intent.granted
            EventIntent.RecordStopClicked -> if (takes.recordingRequested.value) takes.recordingRequested.value = false
            is EventIntent.VideoShotFinished -> {
                val file = savedState.remove<String>(KEY_VIDEO_FILE)?.let(::platformFile) ?: return
                if (intent.saved) importer.shot(owner, file) else file.deleteFile()
            }
            // a pick that is not wanted now is let go: on iOS it is the app's own copy, maybe gigabytes
            is EventIntent.VideoPicked -> intent.uri?.let { uri -> if (mayAdd()) importer.picked(owner, uri) else importer.release(uri) }
            is EventIntent.SoundPicked -> intent.uri?.let { uri -> if (mayAdd()) audioImporter.picked(owner, uri) else audioImporter.release(uri) }
            is EventIntent.Import -> onImport(intent.action)
            EventIntent.SheetHidden -> local.update { it.copy(sheet = null) }
        }
    }

    /**
     * A row of «Добавить запись» (spec 3.36.9): heard only while the sheet is up — the press closes it, and a second tap of a double tap
     * lands on a sheet that is going and starts nothing. «Записать звук» without the microphone asks for it and stays: the recording
     * begins by the press of the row, never by itself after the answer (R1, R4).
     */
    private fun addRecord(way: RecordWay) {
        if (local.value.sheet != EventSheet.AddRecord) return
        if (way == RecordWay.MIC && micPermission.value != true) {
            effectChannel.trySend(EventEffect.RequestMicPermission)
            return
        }
        local.update { it.copy(sheet = null) }
        if (!mayAdd()) return
        when (way) {
            RecordWay.MIC -> {
                takes.recordingRequested.value = true
                listening.value = true
            }
            RecordWay.GALLERY -> effectChannel.trySend(EventEffect.PickVideo)
            RecordWay.FILE -> effectChannel.trySend(EventEffect.PickSound)
            RecordWay.CAMERA -> {
                val file = videos.newCameraFile()
                // The camera app may push this process out of memory: the path has to outlive it.
                savedState[KEY_VIDEO_FILE] = file.filePath
                effectChannel.trySend(EventEffect.LaunchVideoCamera(file.filePath, videoQuality.value))
            }
        }
    }

    /** What a button of the sheet of a file on its way in asks — of the importer at work for this event. */
    private fun onImport(action: ImportAction) {
        val video = importer.state.value.of(owner) != MediaImport.Idle
        when (action) {
            ImportAction.Cancel -> if (video) importer.cancelClicked() else audioImporter.cancelClicked()
            ImportAction.Continue -> if (video) importer.continueClicked()
            ImportAction.Dismiss -> if (video) importer.dismiss() else audioImporter.dismiss()
            // a shot that did not become a recording, to the system sheet: the only way to keep it (spec 3.19)
            ImportAction.Send -> if (video) {
                importer.sendClicked()?.let { path ->
                    viewModelScope.launch {
                        shareFiles.original(platformFile(path), RESCUE_BASE_NAME + ShareNames.videoExtensionOf(path))?.let {
                            effectChannel.send(EventEffect.ShareVideo(it.filePath))
                        }
                    }
                }
            }
        }
    }

    /** «Удалить…»: the dialog of a single event; the sheet of an event of a repeat, with the dates its answers touch (spec 3.36.9). */
    private fun askDelete() {
        viewModelScope.launch {
            val all = events.events.first()
            val event = all.firstOrNull { it.id == eventId } ?: return@launch
            val series = event.seriesId?.let { id -> events.series.first().firstOrNull { it.id == id } }
            val ask = if (series == null) {
                Local(dialog = EventDialog.DeleteOne)
            } else {
                val kind = KindRules.resolve(event.kind, events.kinds.first())
                val from = SeriesEdits.affectedDates(event, series, all, clock.instant(), clock.zone, config, limit = 1).dates.firstOrNull() ?: event.date
                Local(sheet = EventSheet.DeleteScope(EventReducer.seriesWordOf(kind), event.date, series.firstDate.dayOfWeek, from))
            }
            local.update { if (it.sheet == null && it.dialog == null) ask else it }
        }
    }

    /**
     * The deletion (spec 3.35, 3.36.9): one event, or of a repeat «Только этот» or «Этот и следующие» — heard only from the dialog or the
     * sheet that asked it, once. The recordings of the events that go keep the name they wore (D12), what is on its way in for them goes
     * with them — a failure no screen would show any more would hold a video back for good ([VideoTakeImporter.forget],
     * [AudioTakeImporter.forget]); then the screen closes.
     */
    private fun delete(scope: EditScope?) {
        val asked = local.value
        if (deleting || (asked.dialog != EventDialog.DeleteOne && asked.sheet !is EventSheet.DeleteScope)) return
        deleting = true
        local.update { Local() }
        viewModelScope.launch {
            val all = events.events.first()
            val event = all.firstOrNull { it.id == eventId }
            if (event == null) {
                effectChannel.send(EventEffect.Close)
                return@launch
            }
            val series = event.seriesId?.let { id -> events.series.first().firstOrNull { it.id == id } }
            val plan = SeriesEdits.deletePlan(event, scope, series, all, clock.instant(), clock.zone, config)
            val named = events.recordEvents.first()
            val gone = SeriesEdits.gone(plan, all)
            val frozen = SeriesEdits.freezing(plan, all, named.keys).associateWith { id -> words.recordTitleOf(named.getValue(id)) }
            events.apply(plan, frozen, clock.today())
            gone.forEach { id ->
                importer.forget(TakeOwner.Event(id))
                audioImporter.forget(TakeOwner.Event(id))
            }
            effectChannel.send(EventEffect.Close)
        }
    }

    /** The choice of the programme: the repertoire by sections, what is in the programme marked in its order (spec 3.36.9). */
    private fun openProgram() {
        if (local.value.sheet != null) return
        viewModelScope.launch {
            val pieces = repertoire.pieces.first()
            val sections = EventReducer.choiceOf(pieces, repertoire.groups.first(), sessions.sessions.first(), Formats.alphabetical())
            val present = pieces.mapTo(HashSet()) { it.id }
            val checked = events.programs.first()[eventId].orEmpty().filter { it in present }
            local.update { if (it.sheet == null && it.dialog == null) it.copy(sheet = EventSheet.Program(sections, checked)) else it }
        }
    }

    /** «Готово · N»: the programme is the marked ones in the order of their marks; a second tap finds no sheet. */
    private fun doneProgram() {
        val sheet = local.value.sheet as? EventSheet.Program ?: return
        local.update { it.copy(sheet = null) }
        viewModelScope.launch { events.setProgram(eventId, sheet.checked) }
    }

    /** From the press of «Записать звук» until the chain has let the microphone go. */
    private fun recordingRuns(): Boolean = takes.recordingRequested.value || listening.value

    /**
     * One recording at a time, one file on its way in at a time, whoever's it is (spec 3.19, D37) — and a failure of this event's own
     * answered first: its sheet stands over the screen. A sound's failure of another, gone screen holds nothing back ([blocks]).
     */
    private fun mayAdd(): Boolean {
        val sound = audioImporter.state.value
        return !recordingRuns() && !blocks(importer.state.value, sound) && sound.of(owner) == MediaImport.Idle
    }

    /** A file of this event on its way in, or its failure up: the screen is not deleted from under its sheet. */
    private fun importingHere(): Boolean =
        importer.state.value.of(owner) != MediaImport.Idle || audioImporter.state.value.of(owner) != MediaImport.Idle

    companion object {
        /**
         * What keeps «Добавить запись» asleep (D37): a video on its way in, or its failure waiting for its screen — a shot it keeps exists
         * nowhere else, and the importer takes no other video until it is answered (spec 3.19); a sound only while it is copied and heard:
         * its failure holds nothing, and the next file takes its place.
         */
        private fun blocks(video: MediaImport, sound: MediaImport): Boolean = video != MediaImport.Idle || sound is MediaImport.Working

        const val ARG_EVENT_ID = "eventId"
        private const val KEY_VIDEO_FILE = "videoFile"
        private const val RESCUE_BASE_NAME = "video"
        private const val STOP_TIMEOUT_MS = 5_000L

        // Long enough to survive a rotation, short enough that the microphone goes soon after the screen does (as on Live).
        private const val TAKE_STOP_TIMEOUT_MS = 2_000L
    }
}
