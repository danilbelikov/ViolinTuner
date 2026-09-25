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
import com.violinjourney.app.core.domain.session.SessionRepository
import com.violinjourney.app.core.recording.RecordingWatch
import com.violinjourney.app.core.recording.video.VideoImport
import com.violinjourney.app.core.recording.video.VideoTakeImporter
import com.violinjourney.app.core.time.WallClock
import com.violinjourney.app.core.io.PlatformFile
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

/** A recording or the analysis of a video is under way: neither a copy nor a restore starts under them (spec 3.20). */
private fun busyFlow(watch: RecordingWatch, importer: VideoTakeImporter): Flow<Boolean> =
    combine(watch.recording, importer.state) { recording, import -> recording || import != VideoImport.Idle }

open class BackupViewModel(
    private val manager: BackupManager,
    private val store: BackupStore,
    private val config: BackupConfig,
    watch: RecordingWatch,
    importer: VideoTakeImporter,
) : ViewModel() {
    private val mutableState = MutableStateFlow(BackupState(shareUpToBytes = config.shareUpToBytes))
    val state: StateFlow<BackupState> = mutableState.asStateFlow()

    private val effectChannel = Channel<BackupEffect>(Channel.BUFFERED)
    val effects: Flow<BackupEffect> = effectChannel.receiveAsFlow()

    init {
        viewModelScope.launch { mutableState.update { it.copy(contents = store.contents()) } }
        viewModelScope.launch { busyFlow(watch, importer).collect { busy -> mutableState.update { it.copy(busy = busy) } } }
        viewModelScope.launch {
            var shared: PlatformFile? = null
            manager.job.collect { job ->
                mutableState.update { it.copy(job = job, stopDialog = it.stopDialog && job is BackupJob.Saving) }
                // «Отправить…»: the archive is built, the system sheet takes it from here — once
                val file = (job as? BackupJob.Saved)?.shareFile
                if (file != null && file != shared) {
                    shared = file
                    effectChannel.send(BackupEffect.ShareFile(file.filePath))
                }
            }
        }
    }

    fun onIntent(intent: BackupIntent) {
        val now = mutableState.value
        when (intent) {
            is BackupIntent.PartToggled -> if (intent.part != BackupPart.DATA && now.job == BackupJob.Idle) {
                mutableState.update { it.copy(parts = if (intent.part in it.parts) it.parts - intent.part else it.parts + intent.part) }
            }
            BackupIntent.SaveClicked -> if (now.mayStart) effectChannel.trySend(BackupEffect.PickPlace)
            is BackupIntent.PlacePicked -> if (intent.uri != null && now.mayStart) manager.saveTo(intent.uri, now.parts, intent.fileName)
            is BackupIntent.ShareClicked -> if (now.mayStart && now.canShare) manager.share(now.parts, intent.fileName)
            BackupIntent.CancelClicked -> if (manager.cancellable) mutableState.update { it.copy(stopDialog = true) }
            BackupIntent.StopDismissed -> mutableState.update { it.copy(stopDialog = false) }
            BackupIntent.StopConfirmed -> {
                mutableState.update { it.copy(stopDialog = false) }
                manager.cancel()
            }
            BackupIntent.DoneClicked -> {
                manager.dismiss()
                effectChannel.trySend(BackupEffect.Close)
            }
            BackupIntent.RetryClicked -> {
                manager.dismiss()
                effectChannel.trySend(BackupEffect.PickPlace)
            }
            // a copy on its way goes on without its screen
            BackupIntent.BackClicked -> effectChannel.trySend(BackupEffect.Close)
        }
    }

    private val BackupState.mayStart: Boolean get() = (job == BackupJob.Idle || job is BackupJob.SaveFailed) && !busy && contents != null && !nothingToSave
}

open class RestoreViewModel(
    private val manager: BackupManager,
    private val store: BackupStore,
    watch: RecordingWatch,
    importer: VideoTakeImporter,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val mutableState = MutableStateFlow(RestoreState())
    val state: StateFlow<RestoreState> = mutableState.asStateFlow()

    private val effectChannel = Channel<RestoreEffect>(Channel.BUFFERED)
    val effects: Flow<RestoreEffect> = effectChannel.receiveAsFlow()

    init {
        savedState.get<String>(ARG_URI)?.let(::inspect)
        viewModelScope.launch { busyFlow(watch, importer).collect { busy -> mutableState.update { it.copy(busy = busy) } } }
        viewModelScope.launch {
            manager.job.collect { job ->
                mutableState.update { it.copy(job = job, dialog = it.dialog.takeIf { dialog -> dialog != RestoreDialog.STOP || job is BackupJob.Restoring }) }
                if (job is BackupJob.Restored) finish()
            }
        }
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
        val now = mutableState.value
        val ready = now.stage as? RestoreStage.Ready
        when (intent) {
            RestoreIntent.RestoreClicked -> if (ready != null && !now.busy && ready.copy.missingBytes == 0L) {
                // into an empty app there is nothing to lose, and nothing to ask about
                if (ready.current.counts.isEmpty) manager.restore(ready.copy, unsafe = false) else mutableState.update { it.copy(dialog = RestoreDialog.REPLACE) }
            }
            RestoreIntent.UnsafeClicked -> if (ready != null && !now.busy) mutableState.update { it.copy(dialog = RestoreDialog.UNSAFE) }
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
            RestoreIntent.SaveFirstClicked -> effectChannel.trySend(RestoreEffect.OpenBackup)
            RestoreIntent.CancelClicked -> if (manager.cancellable) mutableState.update { it.copy(dialog = RestoreDialog.STOP) }
            RestoreIntent.RetryClicked -> (now.job as? BackupJob.RestoreFailed)?.let { failed ->
                manager.dismiss()
                // the data are gone already — there is nothing left for a safe way to keep safe
                ready?.let { manager.restore(it.copy, unsafe = !failed.dataIntact) }
            }
            RestoreIntent.StartCleanClicked -> {
                manager.startClean()
                effectChannel.trySend(RestoreEffect.Restart)
            }
            RestoreIntent.CloseClicked -> {
                manager.dismiss()
                effectChannel.trySend(RestoreEffect.Close)
            }
        }
    }

    companion object {
        const val ARG_URI = "uri"
        private const val DONE_SHOWN_MS = 1_500L
        private const val OPENING_SHOWN_MS = 600L
    }
}

/** The block «Данные» of the settings: when the last copy was made, whether it has grown old, and a copy on its way. */
open class DataBlockViewModel(
    manager: BackupManager,
    prefs: BackupPrefs,
    sessions: SessionRepository,
    private val store: BackupStore,
    private val config: BackupConfig,
    private val clock: WallClock,
) : ViewModel() {
    private val total = MutableStateFlow<Long?>(null)

    val state: StateFlow<DataBlockState> = combine(manager.job, prefs.lastBackupAtEpochMs, sessions.sessions, total) { job, last, all, bytes ->
        val stale = last != null && (clock.millis() - last) / MS_PER_DAY > config.staleAfterDays
        DataBlockState(
            lastBackupAtEpochMs = last,
            // a quiet word in the same line, never a badge: the app still asks for nothing (spec 3.20)
            newSinceStale = if (stale) all.count { it.startedAtEpochMs > last!! } else 0,
            runningPercent = when (job) {
                is BackupJob.Saving -> ((job.progress?.fraction ?: 0f) * PERCENT).toInt()
                is BackupJob.Restoring -> ((job.progress?.fraction ?: 0f) * PERCENT).toInt()
                else -> null
            },
            restoring = job is BackupJob.Restoring,
            totalBytes = bytes,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DataBlockState())

    /** Weighing walks the folders of the media: on every return to the tab, not on every frame. */
    fun refresh() {
        viewModelScope.launch { total.value = store.contents().totalBytes }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val PERCENT = 100
    }
}

private const val MS_PER_DAY = 24 * 60 * 60 * 1_000L
