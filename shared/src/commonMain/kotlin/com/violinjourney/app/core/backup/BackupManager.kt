package com.violinjourney.app.core.backup

import com.violinjourney.app.core.analytics.Analytics
import com.violinjourney.app.core.analytics.BackupCreated
import com.violinjourney.app.core.analytics.BackupRestored
import com.violinjourney.app.core.analytics.ErrorGroup
import com.violinjourney.app.core.analytics.NoOpAnalytics
import com.violinjourney.app.core.di.ElapsedClock
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.deleteFile
import com.violinjourney.app.core.io.openInput
import com.violinjourney.app.core.io.openOutput
import com.violinjourney.app.core.io.sizeBytes
import com.violinjourney.app.core.time.WallClock
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.IOException

enum class SaveFailure { NO_SPACE, UNAVAILABLE, FAILED }

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
        /** True on the safe way: nothing has changed. False — the data had been deleted to make room. */
        val dataIntact: Boolean,
        val uri: String,
        val manifest: BackupManifest,
    ) : BackupJob
}

/** What a picked file turned out to be. */
sealed interface BackupCandidate {
    data class Copy(val uri: String, val fileName: String?, val fileBytes: Long?, val manifest: BackupManifest, val missingBytes: Long) : BackupCandidate

    data class Unfit(val problem: BackupFileProblem) : BackupCandidate
}

/** Keeps the process alive while a copy is on its way — a foreground service in the app, nothing in tests. */
fun interface BackupKeepAlive {
    fun start()
}

/** How long a copy takes against its size — measured on this device by the copies themselves (spec 5.14). */
class BackupSpeed(config: BackupConfig) {
    @Volatile var msPerMb: Double = config.startMsPerMb
        private set

    fun measured(bytes: Long, tookMs: Long) {
        if (bytes > MIN_BYTES) msPerMb = tookMs / (bytes / BYTES_PER_MB)
    }

    private companion object {
        const val BYTES_PER_MB = 1024.0 * 1024.0
        const val MIN_BYTES = 8L * 1024 * 1024
    }
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
    private val speed: BackupSpeed,
    private val clock: WallClock,
    private val elapsed: ElapsedClock,
    private val io: CoroutineDispatcher,
    private val analytics: Analytics = NoOpAnalytics(),
) {
    private val scope = CoroutineScope(SupervisorJob() + io)
    private var work: Job? = null

    private val mutableJob = MutableStateFlow<BackupJob>(BackupJob.Idle)
    val job: StateFlow<BackupJob> = mutableJob.asStateFlow()

    val running: Boolean get() = mutableJob.value.let { it is BackupJob.Saving || it is BackupJob.Restoring }

    /** Straight into the place the person picked: nothing is built inside the app first. */
    fun saveTo(uri: String, parts: Set<BackupPart>, fileName: String) = save(parts, fileName, uri)

    /** «Отправить…»: a small copy is built under `cache/share/` and handed to the system sheet. */
    fun share(parts: Set<BackupPart>, fileName: String) = save(parts, fileName, uri = null)

    private fun save(parts: Set<BackupPart>, fileName: String, uri: String?) {
        if (mutableJob.value != BackupJob.Idle) return
        mutableJob.value = BackupJob.Saving(fileName, visible = false)
        keepAlive.start()
        work = scope.launch {
            val started = elapsed.nowMs()
            var shownAt: Long? = null
            var finished = false
            val shareFile = if (uri == null) store.shareFile(fileName) else null
            try {
                val prepared = store.prepare(parts)
                val total = prepared.entries.sumOf { it.size }
                if (shareFile != null) {
                    val missing = total + config.freeSpaceMarginBytes - store.freeBytes()
                    if (missing > 0) {
                        mutableJob.value = BackupJob.SaveFailed(SaveFailure.NO_SPACE, missing)
                        return@launch
                    }
                }
                val estimate = config.estimateBaseMs + total / BYTES_PER_MB * speed.msPerMb
                fun show() = mutableJob.update { if (it is BackupJob.Saving && !it.visible) it.copy(visible = true).also { shownAt = elapsed.nowMs() } else it }
                if (estimate > SHOW_FROM_MS && total > QUICK_BYTES) show()
                // The estimate may be wrong — a slow card, a cloud folder: a copy that drags on gets its screen after all.
                val late = launch {
                    delay(SHOW_FROM_MS)
                    show()
                }
                val out = if (shareFile != null) shareFile.openOutput() else documents.openOutput(checkNotNull(uri))
                if (out == null) {
                    late.cancel()
                    mutableJob.value = BackupJob.SaveFailed(SaveFailure.UNAVAILABLE)
                    return@launch
                }
                val throttle = Throttle(started)
                try {
                    BackupWriter.write(out, prepared.manifest, prepared.entries) { progress ->
                        throttle.pass(progress.fraction) { remaining ->
                            mutableJob.update { if (it is BackupJob.Saving) it.copy(progress = progress, remainingSec = remaining) else it }
                        }
                    }
                } finally {
                    late.cancel()
                }
                // The archive is read back from where it went: a copy that cannot be opened is worse than a slow one.
                mutableJob.update { if (it is BackupJob.Saving) it.copy(verifying = true, remainingSec = null) else it }
                val readBack = if (shareFile != null) shareFile.openInput() else documents.openInput(checkNotNull(uri))
                // a provider that will not hand back what it has just taken is not a reason to fail a copy that was written whole
                readBack?.use { BackupReader.verify(it, total) {} }
                speed.measured(total, elapsed.nowMs() - started)
                prefs.setLastBackupAt(clock.millis())
                // A screen that was shown is shown long enough to be read: nothing on this app's screens flashes by.
                shownAt?.let { at ->
                    val stillToShow = MIN_SHOWN_MS - (elapsed.nowMs() - at)
                    if (stillToShow > 0) delay(stillToShow)
                }
                finished = true
                analytics.track(BackupCreated(megabytes = (total / BYTES_PER_MB).toInt(), parts = parts.size))
                mutableJob.value = BackupJob.Saved(
                    fileName = uri?.let(documents::nameOf) ?: fileName,
                    bytes = uri?.let(documents::sizeOf) ?: shareFile?.sizeBytes() ?: total,
                    place = uri?.let(documents::placeOf),
                    manifest = prepared.manifest,
                    shareFile = shareFile,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupFileException) {
                analytics.error(ErrorGroup.BACKUP, "the copy could not be written", e)
                mutableJob.value = BackupJob.SaveFailed(SaveFailure.FAILED)
            } catch (e: IOException) {
                analytics.error(ErrorGroup.BACKUP, "the copy failed", e)
                mutableJob.value = BackupJob.SaveFailed(failureOf(e))
            } finally {
                store.cleanUp()
                // an unfinished file is ours to remove — cancelled, failed, or cut short
                if (!finished) withContext(kotlinx.coroutines.NonCancellable) {
                    if (uri != null) documents.delete(uri) else shareFile?.deleteFile()
                }
            }
        }
    }

    // A full card says so in the message of the exception, and nowhere else.
    private fun failureOf(e: IOException): SaveFailure {
        val message = generateSequence<Throwable>(e) { it.cause }.mapNotNull { it.message }.joinToString(" ")
        return when {
            NO_SPACE_WORDS.any { message.contains(it, ignoreCase = true) } -> SaveFailure.NO_SPACE
            GONE_WORDS.any { message.contains(it, ignoreCase = true) } -> SaveFailure.UNAVAILABLE
            else -> SaveFailure.FAILED
        }
    }

    /** What the picked file is; reads the passport only. */
    suspend fun inspect(uri: String): BackupCandidate = withContext(io) {
        try {
            val input = documents.openInput(uri) ?: return@withContext BackupCandidate.Unfit(BackupFileProblem.Damaged)
            val manifest = input.use { BackupReader.manifest(it, knownDatabase = store.databaseVersion) }
            val missing = (manifest.totalBytes + config.freeSpaceMarginBytes - store.freeBytes()).coerceAtLeast(0)
            BackupCandidate.Copy(uri, documents.nameOf(uri), documents.sizeOf(uri), manifest, missing)
        } catch (e: BackupFileException) {
            BackupCandidate.Unfit(e.problem)
        } catch (e: IOException) {
            BackupCandidate.Unfit(BackupFileProblem.Damaged)
        }
    }

    /**
     * [unsafe] is the way for a phone without room: the copy is checked whole, the media are deleted
     * to make that room, and only then is it unpacked. Otherwise the copy is unpacked beside the
     * data, and a failure or a cancellation changes nothing.
     */
    fun restore(copy: BackupCandidate.Copy, unsafe: Boolean) {
        if (mutableJob.value != BackupJob.Idle) return
        val manifest = copy.manifest
        mutableJob.value = BackupJob.Restoring(if (unsafe) RestorePhase.VERIFYING else RestorePhase.EXTRACTING, checked = unsafe)
        keepAlive.start()
        work = scope.launch {
            var dataIntact = true
            try {
                val total = manifest.totalBytes
                fun report(phase: RestorePhase, started: Long): (BackupProgress) -> Unit {
                    val throttle = Throttle(started)
                    return { progress ->
                        throttle.pass(progress.fraction) { remaining ->
                            mutableJob.update { if (it is BackupJob.Restoring) it.copy(phase = phase, progress = progress, remainingSec = remaining) else it }
                        }
                    }
                }
                if (unsafe) {
                    val input = documents.openInput(copy.uri) ?: throw IOException("the copy is not there any more")
                    input.use { BackupReader.verify(it, total, report(RestorePhase.VERIFYING, elapsed.nowMs())) }
                    dataIntact = false
                    store.deleteMedia()
                }
                mutableJob.update { if (it is BackupJob.Restoring) it.copy(phase = RestorePhase.EXTRACTING, progress = null, remainingSec = null) else it }
                val staging = store.newStaging()
                val input = documents.openInput(copy.uri) ?: throw IOException("the copy is not there any more")
                input.use { BackupReader.extract(it, staging, total, report(RestorePhase.EXTRACTING, elapsed.nowMs())) }
                // From here on there is no way back, and no need for one: the rest is renames at the next start.
                mutableJob.update { if (it is BackupJob.Restoring) it.copy(phase = RestorePhase.FINISHING, remainingSec = null) else it }
                withContext(kotlinx.coroutines.NonCancellable) { store.markStagingReady() }
                analytics.track(BackupRestored(ok = true))
                mutableJob.value = BackupJob.Restored(manifest)
            } catch (e: CancellationException) {
                withContext(kotlinx.coroutines.NonCancellable) { store.discardStaging() }
                throw e
            } catch (e: IOException) {
                // The one place where a person can lose everything; until now nobody but them knew.
                analytics.track(BackupRestored(ok = false))
                analytics.error(ErrorGroup.BACKUP, "the copy could not be restored", e)
                store.discardStaging()
                mutableJob.value = BackupJob.RestoreFailed(dataIntact, copy.uri, manifest)
            }
        }
    }

    /** «Начать с чистого приложения»: the next start wipes everything. The caller restarts the process. */
    fun startClean() {
        store.markWipe()
    }

    /**
     * «Отмена»: at once. A copy being written loses its file; a restore on the safe way loses
     * nothing. On the way without a net the media go as soon as the check has passed — from then
     * on stopping would only leave the phone with neither the old data nor the new, so it cannot be stopped.
     */
    fun cancel() {
        if (!cancellable) return
        work?.cancel()
        mutableJob.value = BackupJob.Idle
    }

    val cancellable: Boolean
        get() = when (val now = mutableJob.value) {
            is BackupJob.Saving -> !now.verifying
            is BackupJob.Restoring -> if (now.checked) now.phase == RestorePhase.VERIFYING else now.phase != RestorePhase.FINISHING
            else -> false
        }

    /** «Готово», «Закрыть»: the outcome has been read. */
    fun dismiss() {
        if (!running) mutableJob.value = BackupJob.Idle
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

        /** Below this a copy is seconds whatever the estimate says: the base of the estimate alone must not open a screen. */
        const val QUICK_BYTES = 64L * 1024 * 1024
        val NO_SPACE_WORDS = listOf("ENOSPC", "No space left")
        val GONE_WORDS = listOf("ENOENT", "EIO", "ENODEV", "EPIPE", "No such file", "I/O error")
    }
}
