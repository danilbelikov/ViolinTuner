package com.violinjourney.app.feature.backup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.violinjourney.app.core.backup.BackupCandidate
import com.violinjourney.app.core.backup.BackupConfig
import com.violinjourney.app.core.backup.BackupJob
import com.violinjourney.app.core.backup.BackupManager
import com.violinjourney.app.core.backup.BackupPart
import com.violinjourney.app.core.backup.BackupPrefs
import com.violinjourney.app.core.backup.BackupStore
import com.violinjourney.app.core.backup.stoppable
import com.violinjourney.app.core.backup.underWay
import com.violinjourney.app.core.domain.session.SessionRepository
import com.violinjourney.app.core.domain.session.SessionSummary
import com.violinjourney.app.core.recording.MediaImport
import com.violinjourney.app.core.recording.RecordingWatch
import com.violinjourney.app.core.recording.audio.AudioTakeImporter
import com.violinjourney.app.core.recording.video.VideoTakeImporter
import com.violinjourney.app.core.time.WallClock
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.exists
import com.violinjourney.app.core.io.filePath
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A recording, or the analysis of a video or of a sound from a file is under way: neither a copy nor a restore starts under them (spec
 * 3.20, plan D37). A video's failure waiting for its screen too — it may keep a shot among the recordings, to be sent or deleted; a
 * sound's not: its copy is gone already, and a failure no screen shows any more must not hold the copy back for good.
 */
private fun busyFlow(watch: RecordingWatch, importer: VideoTakeImporter, audioImporter: AudioTakeImporter): Flow<Boolean> =
    combine(watch.recording, importer.state, audioImporter.state) { recording, video, sound ->
        recording || video != MediaImport.Idle || sound is MediaImport.Working
    }

/**
 * «Копия данных» (spec 3.20, 3.36.8). [savedState] keeps the parts chosen: a screen made anew under the system's «Сохранить как…» — the
 * activity or the process gone while the person picked the place — saves the copy that was chosen, not all of it.
 */
open class BackupViewModel(
    private val manager: BackupManager,
    private val store: BackupStore,
    private val config: BackupConfig,
    watch: RecordingWatch,
    importer: VideoTakeImporter,
    audioImporter: AudioTakeImporter,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val mutableState = MutableStateFlow(BackupState(parts = partsKept(savedState), shareUpToBytes = config.shareUpToBytes))
    val state: StateFlow<BackupState> = mutableState.asStateFlow()

    private val effectChannel = Channel<BackupEffect>(Channel.BUFFERED)
    val effects: Flow<BackupEffect> = effectChannel.receiveAsFlow()

    /**
     * The answer of «Сохранить как…» that came before what is in the app was counted (review of stage 122). A screen made anew under
     * the picker gets it as soon as it is composed, while the counting has only begun; dropped, it left the empty file the system had
     * made in the place picked, and no copy. It is acted on once the parts are counted.
     */
    private var pickedEarly: BackupIntent.PlacePicked? = null

    /** The parts last written to [savedState]. */
    private var keptParts: Set<BackupPart>? = null

    init {
        viewModelScope.launch {
            val contents = store.contents()
            mutableState.update { it.copy(contents = contents) }
            pickedEarly?.let { picked ->
                pickedEarly = null
                onIntent(picked)
            }
        }
        viewModelScope.launch { busyFlow(watch, importer, audioImporter).collect { busy -> mutableState.update { it.copy(busy = busy) } } }
        viewModelScope.launch {
            var shared: PlatformFile? = null
            manager.job.collect { job ->
                // «Остановить?» goes by itself as soon as there is nothing left to stop. A copy on its way or failed brings its own choice
                // (spec 3.36.8): a screen opened anew on it shows what it is made of, and «Ещё раз» repeats the same copy — the switches
                // are shut meanwhile (PartToggled waits for Idle), so nothing the person chose here is overwritten
                mutableState.update {
                    it.copy(
                        job = job,
                        stopDialog = it.stopDialog && job.stoppable,
                        parts = (job as? BackupJob.Saving)?.parts ?: (job as? BackupJob.SaveFailed)?.parts ?: it.parts,
                    )
                }
                keepParts()
                // «Отправить…»: the archive is built, the system sheet takes it from here — once, and only while it is there:
                // a screen opened anew over an old outcome does not hand the sheet a file swept from `cache/share/` since
                val file = (job as? BackupJob.Saved)?.shareFile
                if (file != null && file != shared && file.exists()) {
                    shared = file
                    effectChannel.send(BackupEffect.ShareFile(file.filePath))
                }
            }
        }
    }

    fun onIntent(intent: BackupIntent) {
        val now = mutableState.value
        // A press belongs to the face it was made on (the lesson of stage 120). A screen on its way out takes nothing more: a second
        // tap of «Готово» before the next frame would close the screen under it too, and one after it would reach what the live state
        // shows there — «Отправить…» under «Готово» and «Закрыть» — and start a copy behind a screen that is going.
        if (now.closing) return
        when (intent) {
            is BackupIntent.PartToggled -> if (intent.part != BackupPart.DATA && now.job == BackupJob.Idle) {
                mutableState.update { it.copy(parts = if (intent.part in it.parts) it.parts - intent.part else it.parts + intent.part) }
                keepParts()
            }
            BackupIntent.SaveClicked -> if (now.mayStart) effectChannel.trySend(BackupEffect.PickPlace)
            is BackupIntent.PlacePicked -> when {
                intent.uri == null -> Unit
                // a screen made anew under the picker: the place waits for the counting (pickedEarly)
                now.contents == null -> pickedEarly = intent
                now.mayStart -> manager.saveTo(intent.uri, now.parts, intent.fileName)
            }
            is BackupIntent.ShareClicked -> if (now.mayStart && now.canShare) manager.share(now.parts, intent.fileName)
            BackupIntent.CancelClicked -> if (manager.cancellable) mutableState.update { it.copy(stopDialog = true) }
            BackupIntent.StopDismissed -> mutableState.update { it.copy(stopDialog = false) }
            BackupIntent.StopConfirmed -> {
                mutableState.update { it.copy(stopDialog = false) }
                manager.cancel()
            }
            BackupIntent.DoneClicked -> close()
            // only on the failure it was pressed on — asked of the manager, which the first tap has moved on already: a second tap
            // before the next frame finds the outcome read and asks for no second «Сохранить как…»
            BackupIntent.RetryClicked -> if (manager.job.value is BackupJob.SaveFailed) {
                manager.dismiss()
                effectChannel.trySend(BackupEffect.PickPlace)
            }
            // A copy on its way goes on without its screen, and dismiss does not touch it; an outcome shown on the screen
            // has been read and goes with it — a restore started next must not find it in the way.
            BackupIntent.BackClicked -> close()
        }
    }

    /** «Готово», «Закрыть», «назад»: once. The face stays as it was while the screen fades ([BackupState.closing]). */
    private fun close() {
        mutableState.update { it.copy(closing = true) }
        manager.dismiss()
        effectChannel.trySend(BackupEffect.Close)
    }

    private val BackupState.mayStart: Boolean get() = !job.underWay && !busy && contents != null && !nothingToSave

    /** The parts now chosen, into the saved state of the screen ([partsKept] reads them back) — when they change, not on every step of a job. */
    private fun keepParts() {
        val parts = mutableState.value.parts
        if (parts == keptParts) return
        keptParts = parts
        savedState[KEPT_PARTS] = parts.joinToString(PART_SEPARATOR) { it.name }
    }

    private companion object {
        const val KEPT_PARTS = "parts"
        const val PART_SEPARATOR = ","

        /** The parts a screen made anew had chosen — the data always among them; all of them for a screen opened anew. */
        fun partsKept(savedState: SavedStateHandle): Set<BackupPart> {
            val kept = savedState.get<String>(KEPT_PARTS)?.split(PART_SEPARATOR) ?: return BackupPart.entries.toSet()
            return BackupPart.entries.filter { it.name in kept }.toSet() + BackupPart.DATA
        }
    }
}

open class RestoreViewModel(
    private val manager: BackupManager,
    private val store: BackupStore,
    watch: RecordingWatch,
    importer: VideoTakeImporter,
    audioImporter: AudioTakeImporter,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val mutableState = MutableStateFlow(RestoreState())
    val state: StateFlow<RestoreState> = mutableState.asStateFlow()

    private val effectChannel = Channel<RestoreEffect>(Channel.BUFFERED)
    val effects: Flow<RestoreEffect> = effectChannel.receiveAsFlow()

    /** Opened without a file — from the notification or the block «Данные» — to watch a restore that is on its way. */
    private val watching = savedState.get<String>(ARG_URI) == null
    private var closed = false

    /**
     * «Сначала сохранить текущие данные» has opened the copy, and this screen has not been shown since (the lead's fix of stage 122,
     * verified). A second tap that reaches the button before the copy is on screen asks for nothing: its effect would wait in the channel
     * while this screen is stopped (its collector runs only while it is started — navigation stops a screen at once) and open the copy
     * again after «назад».
     */
    private var backupOpened = false

    init {
        savedState.get<String>(ARG_URI)?.let(::inspect)
        viewModelScope.launch { busyFlow(watch, importer, audioImporter).collect { busy -> mutableState.update { it.copy(busy = busy) } } }
        viewModelScope.launch {
            manager.job.collect { job ->
                // «Остановить?» goes by itself as soon as the restore has passed the point where stopping meant anything
                mutableState.update { it.copy(job = job, dialog = it.dialog.takeIf { dialog -> dialog != RestoreDialog.STOP || job.stoppable }) }
                if (job is BackupJob.Restored) finish()
                // a screen that came to watch a restore has nothing to show once there is none — not «Читаем копию…» for ever
                if (watching && job !is BackupJob.Restoring && job !is BackupJob.Restored && job !is BackupJob.RestoreFailed) close()
            }
        }
    }

    /** Once; the face stays as it was while the screen fades ([RestoreState.closing]). */
    private fun close() {
        if (closed) return
        closed = true
        mutableState.update { it.copy(closing = true) }
        effectChannel.trySend(RestoreEffect.Close)
    }

    private fun inspect(uri: String) {
        mutableState.update { it.copy(stage = RestoreStage.Reading) }
        viewModelScope.launch {
            val stage = when (val candidate = manager.inspect(uri)) {
                is BackupCandidate.Copy -> RestoreStage.Ready(candidate, store.contents())
                is BackupCandidate.Unfit -> RestoreStage.Unfit(candidate.problem)
            }
            mutableState.update { it.copy(stage = stage) }
        }
    }

    // «Данные восстановлены» is read for a moment; then the start screen, and the process starts anew under it — one scene, not two.
    private suspend fun finish() {
        delay(DONE_SHOWN_MS)
        mutableState.update { it.copy(opening = true) }
        delay(OPENING_SHOWN_MS)
        effectChannel.send(RestoreEffect.Restart)
    }

    fun onIntent(intent: RestoreIntent) {
        // A screen on its way out takes nothing more (the lesson of stage 120): after «Закрыть» on a failure the outcome is read and
        // gone, and a second tap would reach «Восстановить» of the passport the live state shows there — into an empty app, a restore
        // behind a screen that is going, with no question and no screen to restart the app.
        if (closed) return
        val now = mutableState.value
        val ready = now.stage as? RestoreStage.Ready
        when (intent) {
            RestoreIntent.RestoreClicked -> if (ready != null && !now.busy && !now.savingCopy && ready.copy.missingBytes == 0L) {
                // into an empty app there is nothing to lose, and nothing to ask about
                if (ready.current.counts.isEmpty) manager.restore(ready.copy, unsafe = false) else mutableState.update { it.copy(dialog = RestoreDialog.REPLACE) }
            }
            // the media do not go for a restore that would run out of room all the same
            RestoreIntent.UnsafeClicked -> if (ready != null && !now.busy && !now.savingCopy && ready.missingEvenUnsafeBytes == 0L) {
                mutableState.update { it.copy(dialog = RestoreDialog.UNSAFE) }
            }
            RestoreIntent.DialogConfirmed -> {
                val dialog = now.dialog
                mutableState.update { it.copy(dialog = null) }
                when (dialog) {
                    RestoreDialog.REPLACE -> ready?.let { manager.restore(it.copy, unsafe = false) }
                    RestoreDialog.UNSAFE -> ready?.let { manager.restore(it.copy, unsafe = true) }
                    RestoreDialog.STOP -> manager.cancel()
                    null -> Unit
                }
            }
            RestoreIntent.DialogDismissed -> mutableState.update { it.copy(dialog = null) }
            RestoreIntent.PickAnotherClicked -> effectChannel.trySend(RestoreEffect.PickFile)
            is RestoreIntent.FilePicked -> intent.uri?.let(::inspect)
            RestoreIntent.SaveFirstClicked -> if (!backupOpened) {
                backupOpened = true
                effectChannel.trySend(RestoreEffect.OpenBackup)
            }
            RestoreIntent.ScreenShown -> backupOpened = false
            RestoreIntent.CancelClicked -> if (manager.cancellable) mutableState.update { it.copy(dialog = RestoreDialog.STOP) }
            // the failure knows its file and its way: a screen opened from the notification has no passport of its own
            RestoreIntent.RetryClicked -> manager.retry()
            // a mark that could not be left would restart into the same app: the screen stays, the button can be pressed again
            RestoreIntent.StartCleanClicked -> if (manager.startClean()) effectChannel.trySend(RestoreEffect.Restart)
            // the face is held first: the failure read and gone, the screen fades as it was pressed
            RestoreIntent.CloseClicked -> {
                close()
                manager.dismiss()
            }
        }
    }

    companion object {
        const val ARG_URI = "uri"
        private const val DONE_SHOWN_MS = 1_500L
        private const val OPENING_SHOWN_MS = 600L
    }
}

/**
 * The block «Данные» of the settings (spec 3.36.8): when the last copy was made, whether it has grown old, what the app weighs, and a
 * copy or a restore on its way with its phase. Until the date is read ([DataBlockState.dateRead]) «ещё не сохраняли» is not said: it
 * is said only of a date that has been read and is not there.
 *
 * Nothing blinks (3.36.8 «Загрузка»), so the two halves of the state are kept apart. The job is in the manager's memory and is read at
 * once: the first value already has it, and the slow half never holds it back. The slow half — the date in DataStore, the recordings in
 * Room with a look at the file of every one with sound — is read once and kept. The block follows both for as long as it lives — the
 * screen of a copy or a restore opened over «Настройки» and closed again — so a return starts from the rows as they stand, not from what
 * they were when it left.
 */
open class DataBlockViewModel(
    manager: BackupManager,
    prefs: BackupPrefs,
    sessions: SessionRepository,
    private val store: BackupStore,
    private val config: BackupConfig,
    private val clock: WallClock,
) : ViewModel() {
    private val total = MutableStateFlow<Long?>(null)

    /** When the last copy was made and how many recordings have come since an old one; null until both are read. */
    private val dated: StateFlow<Dated?> = combine(prefs.lastBackupAtEpochMs, sessions.sessions) { last, all -> Dated(last, newSince(last, all)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val state: StateFlow<DataBlockState> = combine(manager.job, dated, total) { job, dated, bytes -> stateOf(job, dated, bytes) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, stateOf(manager.job.value, dated = null, bytes = null))

    /** Weighing walks the folders of the media: on every return to the tab, not on every frame. */
    fun refresh() {
        viewModelScope.launch { total.value = store.contents().totalBytes }
    }

    private fun stateOf(job: BackupJob, dated: Dated?, bytes: Long?) = DataBlockState(
        dateRead = dated != null,
        lastBackupAtEpochMs = dated?.last,
        newSinceStale = dated?.newSince ?: 0,
        running = BackupPhases.runningOf(job),
        totalBytes = bytes,
    )

    /**
     * The recordings started after a copy older than [BackupConfig.staleAfterDays] whole days (spec 3.20: «копии больше 30 дней и с тех
     * пор появились записи»); none for a younger copy and without one. A quiet word in the same line, never a badge: the app still asks
     * for nothing.
     */
    private fun newSince(last: Long?, all: List<SessionSummary>): Int {
        if (last == null || (clock.millis() - last) / MS_PER_DAY <= config.staleAfterDays) return 0
        return all.count { it.startedAtEpochMs > last }
    }

    /** The slow half of the state, once read. */
    private data class Dated(val last: Long?, val newSince: Int)
}

private const val MS_PER_DAY = 24 * 60 * 60 * 1_000L
