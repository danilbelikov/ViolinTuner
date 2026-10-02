package com.violinjourney.app.core.recording.video

import com.violinjourney.app.core.analytics.Analytics
import com.violinjourney.app.core.analytics.ErrorGroup
import com.violinjourney.app.core.di.ElapsedClock
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.practice.ForgottenPractice
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.practice.RunningPracticeStore
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.domain.session.SessionRepository
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.deleteFile
import com.violinjourney.app.core.io.fileName
import com.violinjourney.app.core.io.filePath
import com.violinjourney.app.core.io.platformFile
import com.violinjourney.app.core.io.sizeBytes
import com.violinjourney.app.core.recording.FileAnalysisResult
import com.violinjourney.app.core.recording.FileTakeAnalyzer
import com.violinjourney.app.core.recording.ImportFailure
import com.violinjourney.app.core.recording.ImportPacing
import com.violinjourney.app.core.recording.MediaImport
import com.violinjourney.app.core.recording.TakeOwner
import com.violinjourney.app.core.recording.eventId
import com.violinjourney.app.core.recording.pieceId
import com.violinjourney.app.core.settings.IntonationConfigSource
import com.violinjourney.app.core.time.WallClock
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How long an analysis takes against the length of the sound — measured on this device by the analyses themselves (spec 5.13). */
class AnalysisSpeed() {
    @Volatile var factor: Double = START_FACTOR
        private set

    fun measured(soundMs: Long, tookMs: Long) {
        if (soundMs > MIN_SOUND_MS) factor = tookMs.toDouble() / soundMs
    }

    private companion object {
        const val START_FACTOR = 0.35
        const val MIN_SOUND_MS = 1_000L
    }
}

/**
 * Turns a video into a take of a piece (spec 3.19) or a recording of an event (spec 3.35) — its [TakeOwner]: moves or copies it in,
 * listens to its sound track, saves the session. A singleton with a scope of its own — leaving the screen, or the app
 * going to the background, must not tear an analysis that may take a minute; the screen only
 * watches [state]. One video at a time. Whatever the platform throws on the way besides the answers
 * of [VideoFiles] and the analyzer — a codec giving up halfway, a database on a full disk — is a
 * video that did not open, told to [analytics]: the app does not fall, and a shot stays to be sent.
 */
class VideoTakeImporter(
    private val files: VideoFiles,
    private val analyzer: FileTakeAnalyzer,
    private val sessions: SessionRepository,
    private val configSource: IntonationConfigSource,
    private val practice: RunningPracticeStore,
    private val practiceConfig: PracticeConfig,
    private val repertoireConfig: RepertoireConfig,
    private val intonationDefaults: IntonationConfig,
    private val clock: WallClock,
    private val elapsed: ElapsedClock,
    private val speed: AnalysisSpeed,
    dispatcher: CoroutineDispatcher,
    private val analytics: Analytics,
) {
    data class Saved(val owner: TakeOwner, val sessionId: Long)

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var job: Job? = null
    private var current: PlatformFile? = null

    private val mutableState = MutableStateFlow<MediaImport>(MediaImport.Idle)
    val state: StateFlow<MediaImport> = mutableState.asStateFlow()

    private val mutableSaved = MutableSharedFlow<Saved>(extraBufferCapacity = 4)
    val saved: Flow<Saved> = mutableSaved

    /** The system camera came back with [cameraFile] written, for a recording of [owner]. */
    fun shot(owner: TakeOwner, cameraFile: PlatformFile) {
        if (mutableState.value != MediaImport.Idle) return
        val returnedAt = clock.millis()
        mutableState.value = MediaImport.Working(owner, shot = true, copying = false, visible = false)
        job = scope.launch {
            val file = try {
                files.adopt(cameraFile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                analytics.error(ErrorGroup.MEDIA, "a shot could not be moved in", e)
                null
            }
            if (file == null) {
                // A shot exists nowhere else: one with anything in it stays where the camera left it, to be sent or deleted
                // by the player (spec 3.19); an empty one is nothing to keep.
                val rescue = cameraFile.filePath.takeIf { cameraFile.sizeBytes() > 0 }
                if (rescue == null) cameraFile.deleteFile()
                mutableState.value = MediaImport.Failed(owner, ImportFailure.CANNOT_OPEN, rescuePath = rescue)
            } else {
                take(owner, file, shot = true, returnedAtEpochMs = returnedAt)
            }
        }
    }

    /** The system picker returned [uri], for a recording of [owner]. */
    fun picked(owner: TakeOwner, uri: String) {
        if (mutableState.value != MediaImport.Idle) {
            release(uri)
            return
        }
        // Nothing is shown while the room is measured, and it is measured off the caller's thread — the main one: a
        // provider answers when it will, and iOS counts in the room what it would free, which takes a while on a full phone.
        val measuring = MediaImport.Working(owner, shot = false, copying = true, visible = false)
        mutableState.value = measuring
        job = scope.launch {
            var file: PlatformFile? = null
            try {
                file = copyIn(owner, uri, measuring)
            } finally {
                // Refused for room, failed, or stopped while measured or copied: what the platform holds of the pick goes
                // (on iOS a copy in tmp — maybe the very gigabytes the room was short of).
                if (file == null) files.release(uri)
            }
            file?.let { take(owner, it, shot = false, returnedAtEpochMs = clock.millis()) }
        }
    }

    /** A pick this screen will not import: one video at a time, none while a take is recorded (spec 3.19). */
    fun release(uri: String) = files.release(uri)

    /** The picked video copied in; null when it does not come in, the state saying why. */
    private suspend fun copyIn(owner: TakeOwner, uri: String, measuring: MediaImport.Working): PlatformFile? {
        val missing = missingBytes(uri)
        // stopped meanwhile: that answer stands
        currentCoroutineContext().ensureActive()
        if (missing > 0) {
            mutableState.compareAndSet(measuring, MediaImport.Failed(owner, ImportFailure.NO_SPACE, missingMb = ImportPacing.missingMb(missing)))
            return null
        }
        // Copying is shown at once and without a number: it is seconds as a rule, and no estimate of it is worth the name.
        if (!mutableState.compareAndSet(measuring, measuring.copy(visible = true))) return null
        val file = try {
            files.import(uri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            analytics.error(ErrorGroup.MEDIA, "a picked video could not be copied in", e)
            null
        }
        if (file == null) mutableState.value = MediaImport.Failed(owner, ImportFailure.CANNOT_OPEN)
        return file
    }

    /** How much more room the picked video needs than there is; 0 when it fits, or when its size is not told. Blocking. */
    private fun missingBytes(uri: String): Long {
        val size = try {
            files.sizeOf(uri)
        } catch (e: Exception) {
            // a provider is somebody else's code and answers with whatever it throws: a size it would not tell, and the
            // copy itself finds out whether the video can be read
            analytics.error(ErrorGroup.MEDIA, "a picked video would not tell its size", e)
            null
        }
        return size?.let { it + repertoireConfig.videoFreeSpaceMarginBytes - files.freeBytes() } ?: 0
    }

    private suspend fun take(owner: TakeOwner, file: PlatformFile, shot: Boolean, returnedAtEpochMs: Long) {
        current = file
        fun fail(reason: ImportFailure) {
            current = null
            // A picked video has its original in the gallery; a shot is kept until the player says what becomes of it.
            if (!shot) files.discard(file)
            mutableState.value = MediaImport.Failed(owner, reason, rescuePath = file.filePath.takeIf { shot })
        }

        try {
            analyse(owner, file, shot, returnedAtEpochMs, ::fail)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Stopped meanwhile — «Отмена», «Отправить видео»: that answer stands, and a next video may be on its way already.
            currentCoroutineContext().ensureActive()
            analytics.error(ErrorGroup.MEDIA, "a video could not be taken in", e)
            fail(ImportFailure.CANNOT_OPEN)
        }
    }

    private suspend fun analyse(owner: TakeOwner, file: PlatformFile, shot: Boolean, returnedAtEpochMs: Long, fail: (ImportFailure) -> Unit) {
        val info = files.info(file) ?: return fail(ImportFailure.CANNOT_OPEN)
        if (!info.hasSound) return fail(ImportFailure.NO_SOUND)
        if (info.durationMs > intonationDefaults.maxSessionMs) return fail(ImportFailure.TOO_LONG)
        files.makeThumb(file)

        val started = elapsed.nowMs()
        val showAtOnce = info.durationMs * speed.factor > ImportPacing.SHOW_FROM_MS
        var shownAt: Long? = started.takeIf { showAtOnce || !shot }
        mutableState.value = MediaImport.Working(owner, shot, copying = false, visible = shownAt != null, thumbPath = files.thumbOf(file.fileName)?.filePath)
        // before the timer below: whatever throws from here on must not leave it running for the next video
        val config = configSource.config.first()
        // The estimate may be wrong — a slow phone, a first analysis: one that drags on gets its sheet after all.
        val late = scope.launch {
            delay(ImportPacing.SHOW_FROM_MS)
            mutableState.update { if (it is MediaImport.Working && !it.visible) it.copy(visible = true).also { shownAt = elapsed.nowMs() } else it }
        }
        var lastShown = 0L
        // A shot is dated by when it was shot; a picked video by what it says of itself, else by now.
        val startedAt = if (shot) returnedAtEpochMs - info.durationMs else info.createdAtEpochMs ?: returnedAtEpochMs
        val result = try {
            analyzer.analyze(file, config, startedAt, audioFileName = file.fileName) { progress ->
                // from the analysing thread; a StateFlow takes that, and ten updates a second are plenty
                val now = elapsed.nowMs()
                if (now - lastShown >= ImportPacing.PROGRESS_EVERY_MS) {
                    lastShown = now
                    val remaining = ImportPacing.remainingSec(now - started, progress.fraction)
                    mutableState.update {
                        if (it is MediaImport.Working) it.copy(percent = ImportPacing.percentOf(progress.fraction), bars = progress.bars, remainingSec = remaining) else it
                    }
                }
            }
        } finally {
            late.cancel()
        }
        when (result) {
            is FileAnalysisResult.Recorded -> {
                speed.measured(info.durationMs, elapsed.nowMs() - started)
                // A sheet that was shown is shown long enough to be read: nothing on this app's screens flashes by.
                shownAt?.let { at ->
                    mutableState.update { if (it is MediaImport.Working) it.copy(percent = ImportPacing.PERCENT, remainingSec = null) else it }
                    val stillToShow = ImportPacing.MIN_SHOWN_MS - (elapsed.nowMs() - at)
                    if (stillToShow > 0) delay(stillToShow)
                }
                val id = sessions.save(result.session.copy(pieceId = owner.pieceId, eventId = owner.eventId, videoPath = file.fileName))
                current = null
                // Filming oneself is practising: a practice with a video take in it is not a forgotten one (spec 3.12).
                if (shot) markSound(returnedAtEpochMs)
                mutableState.value = MediaImport.Idle
                mutableSaved.emit(Saved(owner, id))
            }
            FileAnalysisResult.NoNotes -> fail(ImportFailure.NO_NOTES)
            FileAnalysisResult.TooLong -> fail(ImportFailure.TOO_LONG)
            FileAnalysisResult.TooShort, FileAnalysisResult.UnsupportedRate, FileAnalysisResult.CannotOpen -> fail(ImportFailure.CANNOT_OPEN)
        }
    }

    /** The take is saved by now: a practice that cannot be marked is told, and is no failure of the video. */
    private suspend fun markSound(atEpochMs: Long) {
        try {
            val running = practice.running.first() ?: return
            // past the limit the practice has ended by itself (spec 3.12): a shot then is not of it and must not move its end
            val ofIt = atEpochMs >= running.startedAtEpochMs && ForgottenPractice.runsAt(running.startedAtEpochMs, atEpochMs, practiceConfig)
            if (ofIt && atEpochMs > (running.lastSoundEpochMs ?: 0)) practice.markSound(atEpochMs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            analytics.error(ErrorGroup.MEDIA, "a shot could not mark the practice", e)
        }
    }

    /** «Отмена»: a picked video simply goes; a shot is asked about, and the analysis goes on meanwhile. */
    fun cancelClicked() {
        val working = mutableState.value as? MediaImport.Working ?: return
        if (working.shot) mutableState.value = working.copy(asking = true, visible = true) else stopAndDiscard()
    }

    /** «Продолжить разбор». */
    fun continueClicked() = mutableState.update { if (it is MediaImport.Working) it.copy(asking = false) else it }

    /**
     * «Отправить видео»: the analysis stops, the shot stays until it is deleted. Answers with the
     * file to hand to the system sheet.
     */
    fun sendClicked(): String? = when (val now = mutableState.value) {
        is MediaImport.Working -> current?.takeIf { now.shot && !now.copying }?.let { file ->
            job?.cancel()
            mutableState.value = MediaImport.Failed(now.owner, ImportFailure.STOPPED, rescuePath = file.filePath)
            file.filePath
        }
        is MediaImport.Failed -> now.rescuePath
        MediaImport.Idle -> null
    }

    /** «Удалить» — and «Понятно», which has nothing left to delete. */
    fun dismiss() {
        when (val now = mutableState.value) {
            is MediaImport.Working -> stopAndDiscard()
            is MediaImport.Failed -> {
                now.rescuePath?.let { files.discard(platformFile(it)) }
                current = null
                mutableState.value = MediaImport.Idle
            }
            MediaImport.Idle -> Unit
        }
    }

    /**
     * [owner] is gone — an event deleted (spec 3.35): a failure that waits for its screen is dropped — no screen will ever show it, and
     * it would hold every other video back; with it goes a shot it kept to be sent, as by «Удалить». A picked video being copied or heard
     * stops: its original is in the gallery. A shot being heard is left to become a recording: it exists nowhere else.
     */
    fun forget(owner: TakeOwner) {
        when (val now = mutableState.value) {
            is MediaImport.Failed -> if (now.owner == owner) dismiss()
            is MediaImport.Working -> if (now.owner == owner && !now.shot) stopAndDiscard()
            MediaImport.Idle -> Unit
        }
    }

    private fun stopAndDiscard() {
        job?.cancel()
        current?.let(files::discard)
        current = null
        mutableState.value = MediaImport.Idle
    }

}
