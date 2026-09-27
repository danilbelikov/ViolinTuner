package com.violinjourney.app.core.backup

import com.violinjourney.app.core.analytics.Analytics
import com.violinjourney.app.core.analytics.BackupCreated
import com.violinjourney.app.core.analytics.BackupRestored
import com.violinjourney.app.core.analytics.ErrorGroup
import com.violinjourney.app.core.di.ElapsedClock
import com.violinjourney.app.core.io.ByteInput
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.StorageException
import com.violinjourney.app.core.io.StorageFailure
import com.violinjourney.app.core.io.deleteFile
import com.violinjourney.app.core.io.openInput
import com.violinjourney.app.core.io.openOutput
import com.violinjourney.app.core.io.sizeBytes
import com.violinjourney.app.core.time.WallClock
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.IOException

/**
 * Why a copy was not saved. [PHONE_FULL] — the room ran out in the phone itself, under the snapshot of the database or the
 * archive for «Отправить…», not in the place that was picked: another card would not help.
 */
enum class SaveFailure { NO_SPACE, PHONE_FULL, UNAVAILABLE, FAILED }

enum class RestorePhase { VERIFYING, EXTRACTING, FINISHING }

/** What the one long job of the app is doing (spec 3.20). The screens only watch this. */
sealed interface BackupJob {
    data object Idle : BackupJob

    data class Saving(
        val fileName: String,
        /** False while a short copy is given the chance to end before a progress screen is shown: nothing blinks. */
        val visible: Boolean,
        /** «Проверяем файл»: the archive is read back; there is nothing left to cancel. */
        val verifying: Boolean = false,
        val progress: BackupProgress? = null,
        /** Null until the speed has been measured. */
        val remainingSec: Int? = null,
    ) : BackupJob

    data class Saved(
        val fileName: String,
        val bytes: Long,
        /** «Загрузки», the name of a folder — when the provider tells. */
        val place: String?,
        val manifest: BackupManifest,
        /** Set for «Отправить…»: the archive waits under `cache/share/` for the system sheet. */
        val shareFile: PlatformFile? = null,
    ) : BackupJob

    data class SaveFailed(val reason: SaveFailure, val missingBytes: Long = 0) : BackupJob

    data class Restoring(
        val phase: RestorePhase,
        /** The way without a safety net has a phase more: the copy is checked whole before anything is deleted. */
        val checked: Boolean,
        val progress: BackupProgress? = null,
        val remainingSec: Int? = null,
    ) : BackupJob

    /** The copy lies unpacked and marked; what is left is to start the process anew. */
    data class Restored(val manifest: BackupManifest) : BackupJob

    data class RestoreFailed(
        /** True when nothing has changed. False — the media had been deleted to make room. */
        val dataIntact: Boolean,
        val uri: String,
        val manifest: BackupManifest,
        /** The way it took: without a safety net the copy is checked first. «Ещё раз» takes the same way. */
        val checked: Boolean,
    ) : BackupJob
}

/**
 * «Остановить» still means something (spec 3.20): a copy before its check, a restore before its point of no return — on
 * the safe way until the mark, without a net until the media go. One rule for the manager, its screens and their dialogs.
 */
val BackupJob.stoppable: Boolean
    get() = when (this) {
        is BackupJob.Saving -> !verifying
        is BackupJob.Restoring -> if (checked) phase == RestorePhase.VERIFYING else phase != RestorePhase.FINISHING
        else -> false
    }

/**
 * Work on its way, or a restore waiting for its restart: nothing else starts over it. An outcome that has been shown —
 * a copy saved, a copy or a restore that failed — is read already, and a new job starts over it.
 */
val BackupJob.underWay: Boolean
    get() = this is BackupJob.Saving || this is BackupJob.Restoring || this is BackupJob.Restored

/** What a picked file turned out to be. */
sealed interface BackupCandidate {
    data class Copy(val uri: String, val fileName: String?, val fileBytes: Long?, val manifest: BackupManifest, val missingBytes: Long) : BackupCandidate

    data class Unfit(val problem: BackupFileProblem) : BackupCandidate
}

/** Keeps the process alive while a copy is on its way — a foreground service in the app, nothing in tests. */
fun interface BackupKeepAlive {
    fun start()
}

/**
 * Saving a copy and bringing one back (spec 3.20, 5.14). A singleton with a scope of its own, as
 * the importer of videos is: minutes of work must not end because a screen did. One job at a time.
 */
class BackupManager(
    private val store: BackupStore,
    private val documents: BackupDocuments,
    private val prefs: BackupPrefs,
    private val keepAlive: BackupKeepAlive,
    private val config: BackupConfig,
    private val clock: WallClock,
    private val elapsed: ElapsedClock,
    private val io: CoroutineDispatcher,
    private val analytics: Analytics,
) {
    private val scope = CoroutineScope(SupervisorJob() + io)

    /** The job whose state [job] shows — or showed, until «Остановить»; a job writes its state only while it is this one. */
    @Volatile private var work: Job? = null

    private val mutableJob = MutableStateFlow<BackupJob>(BackupJob.Idle)
    val job: StateFlow<BackupJob> = mutableJob.asStateFlow()

    val running: Boolean get() = mutableJob.value.let { it is BackupJob.Saving || it is BackupJob.Restoring }

    /** Straight into the place the person picked: nothing is built inside the app first. */
    fun saveTo(uri: String, parts: Set<BackupPart>, fileName: String) = save(parts, fileName, uri)

    /** «Отправить…»: a small copy is built under `cache/share/` and handed to the system sheet. */
    fun share(parts: Set<BackupPart>, fileName: String) = save(parts, fileName, uri = null)

    /**
     * Starts [block] as the one job of the manager, showing [first] — or does nothing while a job is under way (the screens
     * call this from the main thread). A stopped job may still be tidying up after itself — removing its snapshot of the
     * database or its unpacking folder, which are this job's folders too: this one waits for it first. The wait is deaf to
     * «Остановить»: a job stopped while it waits still ends only after the one it waited for, so a chain of stopped jobs is
     * waited for whole and no two jobs ever work in the same folders. [block] starts even for a job stopped by then, and
     * its first step is to see that it was: what it holds — the file the system picker made for it — goes in its finally.
     */
    private fun launchJob(first: BackupJob, block: suspend CoroutineScope.(self: Job) -> Unit) {
        if (mutableJob.value.underWay) return
        val previous = work
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val self = coroutineContext.job
            if (previous != null && !previous.isCompleted) {
                // a copy that waits — for a write stuck in a cloud provider, maybe — gets its screen, and its «Остановить»,
                // as a copy that drags on does
                val shown = launch {
                    delay(SHOW_FROM_MS)
                    self.moveOn { if (it is BackupJob.Saving && !it.visible) it.copy(visible = true) else null }
                }
                withContext(NonCancellable) { previous.join() }
                shown.cancel()
            }
            block(self)
        }
        // the manager's job before its first state is shown: a stopped one still at work can no longer write over it
        work = job
        mutableJob.value = first
        keepAlive.start()
        job.start()
    }

    /**
     * Moves this job's state on in one step — or not at all when it is no longer this job's: stopped by «Остановить», or
     * followed by another job. [next] answers null when the state is not one it moves on from. Whether «Остановить» came
     * first or this step did is settled here, once: a point of no return is passed by the same step that checks it.
     */
    private fun Job.moveOn(next: (BackupJob) -> BackupJob?): Boolean {
        while (true) {
            if (work !== this) return false
            val now = mutableJob.value
            val moved = next(now) ?: return false
            if (mutableJob.compareAndSet(now, moved)) return true
        }
    }

    private fun save(parts: Set<BackupPart>, fileName: String, uri: String?) {
        launchJob(BackupJob.Saving(fileName, visible = false)) { self ->
            val started = elapsed.nowMs()
            // a screen shown while this copy waited for a stopped one is shown already: it is not taken away at once either
            var shownAt: Long? = if ((mutableJob.value as? BackupJob.Saving)?.visible == true && work === self) started else null
            var finished = false
            var shareFile: PlatformFile? = null
            // until the stream into the picked place is written, whatever fills up is the phone's own memory
            var inPhone = true
            try {
                // stopped while it waited for the job before it: nothing to do, and the finally removes the picked file
                currentCoroutineContext().ensureActive()
                if (uri == null) shareFile = store.shareFile(fileName)
                val prepared = store.prepare(parts)
                val total = prepared.entries.sumOf { it.size }
                if (shareFile != null) {
                    val missing = total + config.freeSpaceMarginBytes - store.freeBytes()
                    if (missing > 0) {
                        self.moveOn { if (it is BackupJob.Saving) BackupJob.SaveFailed(SaveFailure.NO_SPACE, missing) else null }
                        return@launchJob
                    }
                }
                inPhone = shareFile != null
                fun show() {
                    if (self.moveOn { if (it is BackupJob.Saving && !it.visible) it.copy(visible = true) else null }) shownAt = elapsed.nowMs()
                }
                // A big copy gets its screen at once; any other only when it drags on — a slow card, a cloud folder.
                if (total > QUICK_BYTES) show()
                val late = launch {
                    delay(SHOW_FROM_MS)
                    show()
                }
                val out = if (shareFile != null) shareFile.openOutput() else documents.openOutput(checkNotNull(uri))
                if (out == null) {
                    late.cancel()
                    self.moveOn { if (it is BackupJob.Saving) BackupJob.SaveFailed(SaveFailure.UNAVAILABLE) else null }
                    return@launchJob
                }
                val throttle = Throttle(started)
                try {
                    BackupWriter.write(out, prepared.manifest, prepared.entries) { progress ->
                        throttle.pass(progress.fraction) { remaining ->
                            self.moveOn { if (it is BackupJob.Saving) it.copy(progress = progress, remainingSec = remaining) else null }
                        }
                    }
                } finally {
                    late.cancel()
                }
                // «Проверяем файл» can no longer be stopped: whether «Остановить» came first is settled by this very step
                if (!self.moveOn { if (it is BackupJob.Saving) it.copy(verifying = true, remainingSec = null) else null }) {
                    throw CancellationException("stopped before the check")
                }
                // The archive is read back from where it went: a copy that cannot be opened is worse than a slow one.
                val readBack = if (shareFile != null) shareFile.openInput() else documents.openInput(checkNotNull(uri))
                // a provider that will not hand back what it has just taken is not a reason to fail a copy that was written whole
                readBack?.use { requireDatabase(BackupReader.verify(it, total, total + config.freeSpaceMarginBytes) {}) }
                // The copy is whole where it went: nothing that happens after this takes it away.
                finished = true
                rememberDate()
                // A screen that was shown is shown long enough to be read: nothing on this app's screens flashes by.
                shownAt?.let { at ->
                    val stillToShow = MIN_SHOWN_MS - (elapsed.nowMs() - at)
                    if (stillToShow > 0) delay(stillToShow)
                }
                analytics.track(BackupCreated(megabytes = (total / BYTES_PER_MB).toInt(), parts = parts.size))
                val saved = BackupJob.Saved(
                    fileName = uri?.let(documents::nameOf) ?: fileName,
                    bytes = uri?.let(documents::sizeOf) ?: shareFile?.sizeBytes() ?: total,
                    place = uri?.let(documents::placeOf),
                    manifest = prepared.manifest,
                    shareFile = shareFile,
                )
                self.moveOn { if (it is BackupJob.Saving) saved else null }
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupFileException) {
                // a copy cancelled while its write broke stays cancelled: «Отмена» has been said already
                currentCoroutineContext().ensureActive()
                analytics.error(ErrorGroup.BACKUP, "the copy could not be written", e)
                self.moveOn { if (it is BackupJob.Saving) BackupJob.SaveFailed(SaveFailure.FAILED) else null }
            } catch (e: Exception) {
                // The stream's IOException, and whatever else the platform throws on the way — SQLite refusing a snapshot
                // on a full disk, a provider's own exception: a copy that failed is said on its screen, not a fall of the app.
                currentCoroutineContext().ensureActive()
                analytics.error(ErrorGroup.BACKUP, "the copy failed", e)
                val reason = failureOf(e, inPhone)
                self.moveOn { if (it is BackupJob.Saving) BackupJob.SaveFailed(reason) else null }
            } finally {
                store.cleanUp()
                // an unfinished file is ours to remove — cancelled, failed, or cut short
                if (!finished) withContext(NonCancellable) {
                    if (uri != null) documents.delete(uri) else shareFile?.deleteFile()
                }
            }
        }
    }

    /** The date of a copy that is whole already: a store that will not keep it is told, and the copy stays. */
    private suspend fun rememberDate() {
        try {
            prefs.setLastBackupAt(clock.millis())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            analytics.error(ErrorGroup.BACKUP, "the date of the copy could not be kept", e)
        }
    }

    // The streams of iOS give the reason as a value ([StorageException], from the errno); on Android a full card says so in
    // the message of the exception, and nowhere else. Room that ran out [inPhone] — under the snapshot of the database or
    // the archive for «Отправить…» — is the phone's own; a place that went away is a failure of the stream into it, and
    // SQLite's own «disk I/O error» is about its snapshot, not about the place.
    private fun failureOf(e: Exception, inPhone: Boolean): SaveFailure {
        val causes = generateSequence<Throwable>(e) { it.cause }
        val told = causes.filterIsInstance<StorageException>().firstOrNull()?.failure
        val message = causes.mapNotNull { it.message }.joinToString(" ")
        val noSpace = told?.let { it == StorageFailure.NO_SPACE } ?: NO_SPACE_WORDS.any { message.contains(it, ignoreCase = true) }
        val gone = told?.let { it == StorageFailure.GONE } ?: (e is IOException && GONE_WORDS.any { message.contains(it, ignoreCase = true) })
        return when {
            noSpace -> if (inPhone) SaveFailure.PHONE_FULL else SaveFailure.NO_SPACE
            !inPhone && gone -> SaveFailure.UNAVAILABLE
            else -> SaveFailure.FAILED
        }
    }

    /** A copy without the snapshot of the database is no copy: its media over this phone's database would be a mixture. */
    private fun requireDatabase(seen: BackupSeen) {
        if (!seen.hasDatabase) throw BackupFileException(BackupFileProblem.Damaged)
    }

    /** What the picked file is; reads the passport only. */
    suspend fun inspect(uri: String): BackupCandidate = withContext(io) {
        try {
            val input = documents.openInput(uri) ?: return@withContext BackupCandidate.Unfit(BackupFileProblem.Damaged)
            val manifest = input.use { BackupReader.manifest(it, knownDatabase = store.databaseVersion) }
            val missing = (manifest.totalBytes + config.freeSpaceMarginBytes - store.freeBytes()).coerceAtLeast(0)
            BackupCandidate.Copy(uri, documents.nameOf(uri), documents.sizeOf(uri), manifest, missing)
        } catch (e: CancellationException) {
            throw e
        } catch (e: BackupFileException) {
            BackupCandidate.Unfit(e.problem)
        } catch (e: IOException) {
            BackupCandidate.Unfit(BackupFileProblem.Damaged)
        } catch (e: Exception) {
            // the file was picked by hand, and the platform read it with whatever it throws: a file that cannot be read
            analytics.error(ErrorGroup.BACKUP, "the picked file could not be read", e)
            BackupCandidate.Unfit(BackupFileProblem.Damaged)
        }
    }

    /**
     * [unsafe] is the way for a phone without room: the copy is checked whole — and what the app opens first, the database
     * and the settings, unpacked and opened on the way — the media are deleted to make the room, and only then is the rest
     * unpacked. Otherwise the copy is unpacked beside the data, and a failure or a cancellation changes nothing.
     */
    fun restore(copy: BackupCandidate.Copy, unsafe: Boolean) = restore(copy, unsafe, mediaGone = false)

    /** [mediaGone] — a retry after the worst case: the media went in the try before, and a failure now cannot bring them back. */
    private fun restore(copy: BackupCandidate.Copy, unsafe: Boolean, mediaGone: Boolean) {
        val manifest = copy.manifest
        launchJob(BackupJob.Restoring(if (unsafe) RestorePhase.VERIFYING else RestorePhase.EXTRACTING, checked = unsafe)) { self ->
            var dataIntact = !mediaGone
            try {
                // stopped while it waited for the job before it: the catch removes the unpacking folder, which nobody uses now
                currentCoroutineContext().ensureActive()
                val total = manifest.totalBytes
                // more than this was never weighed against the room of the phone: an archive that unpacks past it is damaged
                val limit = total + config.freeSpaceMarginBytes
                fun report(phase: RestorePhase, started: Long): (BackupProgress) -> Unit {
                    val throttle = Throttle(started)
                    return { progress ->
                        throttle.pass(progress.fraction) { remaining ->
                            self.moveOn { if (it is BackupJob.Restoring) it.copy(phase = phase, progress = progress, remainingSec = remaining) else null }
                        }
                    }
                }
                // the data of the copy are unpacked while the media are still in place: they need the room now
                if (unsafe) requireRoomForData(manifest)
                val staging = store.newStaging()
                if (unsafe) {
                    val seen = open(copy.uri).use { BackupReader.extract(it, staging, total, limit, setOf(BackupPart.DATA), report(RestorePhase.VERIFYING, elapsed.nowMs())) }
                    requireDatabase(seen)
                    // a copy whose database this app would not open costs nothing yet: the media are still in place
                    store.settleStaging(manifest.createdAtEpochMs)
                    requireRoomWithoutMedia(manifest)
                    // From here on stopping would leave the phone with neither the old data nor the new: whether «Остановить»
                    // came first is settled by the same step that passes the point.
                    if (!self.moveOn { if (it is BackupJob.Restoring) it.copy(phase = RestorePhase.EXTRACTING, progress = null, remainingSec = null) else null }) {
                        throw CancellationException("stopped before the media went")
                    }
                    dataIntact = false
                    store.deleteMedia()
                    open(copy.uri).use { BackupReader.extract(it, staging, total, limit, MEDIA_PARTS, report(RestorePhase.EXTRACTING, elapsed.nowMs())) }
                } else {
                    val seen = open(copy.uri).use { BackupReader.extract(it, staging, total, limit, onProgress = report(RestorePhase.EXTRACTING, elapsed.nowMs())) }
                    requireDatabase(seen)
                    store.settleStaging(manifest.createdAtEpochMs)
                }
                // From here on there is no way back, and no need for one: the rest is renames at the next start.
                if (!self.moveOn { if (it is BackupJob.Restoring) it.copy(phase = RestorePhase.FINISHING, remainingSec = null) else null }) {
                    throw CancellationException("stopped before the mark")
                }
                withContext(NonCancellable) { store.markStagingReady() }
                analytics.track(BackupRestored(ok = true))
                self.moveOn { if (it is BackupJob.Restoring) BackupJob.Restored(manifest) else null }
            } catch (e: CancellationException) {
                withContext(NonCancellable) { store.discardStaging() }
                throw e
            } catch (e: Exception) {
                // The file, the disk, or whatever else the platform throws on the way: said on the screen, not a fall.
                store.discardStaging()
                // a restore cancelled while it broke stays cancelled; what it unpacked is gone either way
                currentCoroutineContext().ensureActive()
                // The one place where a person can lose everything; until now nobody but them knew.
                analytics.track(BackupRestored(ok = false))
                analytics.error(ErrorGroup.BACKUP, "the copy could not be restored", e)
                self.moveOn { if (it is BackupJob.Restoring) BackupJob.RestoreFailed(dataIntact, copy.uri, manifest, checked = unsafe) else null }
            }
        }
    }

    private fun open(uri: String): ByteInput = documents.openInput(uri) ?: throw IOException("the copy is not there any more")

    /**
     * The way without a net unpacks the database, the settings and the profile of the copy before the media go (spec 3.20):
     * a phone without room for them fails the restore before minutes of checking, with nothing changed.
     */
    private fun requireRoomForData(manifest: BackupManifest) {
        val data = manifest.bytes[BackupPart.DATA] ?: 0L
        if (data + config.freeSpaceMarginBytes - store.freeBytes() > 0) throw IOException("no room for the data of the copy beside the media")
    }

    /**
     * The screen offered the way without a net because the media make the room (spec 3.20); minutes of checking later
     * the phone may have less. The media do not go for a restore that would run out of room all the same.
     */
    private suspend fun requireRoomWithoutMedia(manifest: BackupManifest) {
        val media = store.contents().bytes.filterKeys { it != BackupPart.DATA }.values.sum()
        // the data of the copy lie unpacked already; what is left to unpack are its media
        val left = manifest.bytes.filterKeys { it in manifest.parts && it != BackupPart.DATA }.values.sum()
        if (left + config.freeSpaceMarginBytes - store.freeBytes() > media) throw IOException("no room for the copy even without the media")
    }

    /** «Ещё раз», «Ещё раз с этим файлом»: the file of the failure, the way it took — a screen needs no passport of its own. */
    fun retry() {
        val failed = mutableJob.value as? BackupJob.RestoreFailed ?: return
        // after the media went there is nothing left for a safe way to keep safe — and a failure of this try says so again
        restore(
            BackupCandidate.Copy(failed.uri, fileName = null, fileBytes = null, failed.manifest, missingBytes = 0),
            unsafe = failed.checked || !failed.dataIntact,
            mediaGone = !failed.dataIntact,
        )
    }

    /**
     * «Начать с чистого приложения»: the next start wipes everything. True — the caller restarts the process; false — the
     * mark could not be left, a restart would change nothing, and the screen stays as it was.
     */
    fun startClean(): Boolean = try {
        store.markWipe()
        true
    } catch (e: IOException) {
        analytics.error(ErrorGroup.BACKUP, "the app could not be marked to start clean", e)
        false
    }

    /**
     * «Отмена»: at once. A copy being written loses its file; a restore on the safe way loses
     * nothing. On the way without a net the media go as soon as the check has passed — from then
     * on stopping would only leave the phone with neither the old data nor the new, so it cannot be stopped.
     * A stop that comes too late — the job has passed its point already — does nothing.
     */
    fun cancel() {
        while (true) {
            val now = mutableJob.value
            if (!now.stoppable) return
            if (mutableJob.compareAndSet(now, BackupJob.Idle)) break
        }
        work?.cancel()
    }

    val cancellable: Boolean get() = mutableJob.value.stoppable

    /** «Готово», «Закрыть», the system «Назад» from an outcome: it has been read. A job under way is not touched. */
    fun dismiss() {
        mutableJob.update { if (it is BackupJob.Saved || it is BackupJob.SaveFailed || it is BackupJob.RestoreFailed) BackupJob.Idle else it }
    }

    /** Ten updates a second are plenty, and «осталось около» waits until the speed is worth a word (5 % or 10 s). */
    private inner class Throttle(private val started: Long) {
        private var last = 0L

        fun pass(fraction: Float, publish: (remainingSec: Int?) -> Unit) {
            val now = elapsed.nowMs()
            if (now - last < PROGRESS_EVERY_MS && fraction < 1f) return
            last = now
            val spent = now - started
            val known = fraction > 0f && (fraction >= REMAINING_FROM || spent >= REMAINING_AFTER_MS)
            publish(if (known) ((spent * (1 - fraction) / fraction) / MS_PER_SECOND).toInt().coerceAtLeast(1) else null)
        }
    }

    private companion object {
        const val SHOW_FROM_MS = 700L
        const val MIN_SHOWN_MS = 1_200L
        const val PROGRESS_EVERY_MS = 100L
        const val REMAINING_FROM = 0.05f
        const val REMAINING_AFTER_MS = 10_000L
        const val MS_PER_SECOND = 1_000
        const val BYTES_PER_MB = 1024.0 * 1024.0

        /** Above this a copy shows its progress screen at once; below it, only when it has not ended in [SHOW_FROM_MS]. */
        const val QUICK_BYTES = 64L * 1024 * 1024

        /** What the way without a net unpacks after the media have gone: all but the data, unpacked while it checked. */
        val MEDIA_PARTS = BackupPart.entries.toSet() - BackupPart.DATA

        /**
         * «disk is full» is SQLITE_FULL, as android.database and androidx.sqlite both word it. SQLITE_IOERR_SHMSIZE is SQLite
         * failing to enlarge the `-shm` of a database — on a full disk, which is how a snapshot on a full phone ends on
         * Android (seen on the emulator): the errno is only in SQLite's log, not in the exception.
         */
        val NO_SPACE_WORDS = listOf("ENOSPC", "No space left", "disk is full", "SQLITE_IOERR_SHMSIZE")

        /** The words of libcore, as Android's streams write an errno; iOS says it by [StorageException] instead. */
        val GONE_WORDS = listOf("ENOENT", "EIO", "ENODEV", "EPIPE", "No such file", "I/O error")
    }
}
