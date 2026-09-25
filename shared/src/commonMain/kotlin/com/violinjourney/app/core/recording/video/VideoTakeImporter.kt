package com.violinjourney.app.core.recording.video

import com.violinjourney.app.core.di.ElapsedClock
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.practice.RunningPracticeStore
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.domain.session.RecordingBar
import com.violinjourney.app.core.domain.session.SessionRepository
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.deleteFile
import com.violinjourney.app.core.io.fileName
import com.violinjourney.app.core.io.filePath
import com.violinjourney.app.core.io.platformFile
import com.violinjourney.app.core.recording.FileAnalysisResult
import com.violinjourney.app.core.recording.FileTakeAnalyzer
import com.violinjourney.app.core.settings.IntonationConfigSource
import com.violinjourney.app.core.time.WallClock
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class VideoImportFailure {
    NO_SOUND, TOO_LONG, CANNOT_OPEN, NO_NOTES, NO_SPACE,

    /** Not a failure of the video: the player stopped the analysis of a shot and has to say what becomes of it. */
    STOPPED,
}

/** What the sheet of a video take on its way in shows (spec 3.19). */
sealed interface VideoImport {
    data object Idle : VideoImport

    data class Working(
        val pieceId: Long,
        /** Shot a moment ago: it exists nowhere else, so stopping it is a question, not a button. */
        val shot: Boolean,
        val copying: Boolean,
        /** False while a short analysis is given the chance to end before anything is shown: nothing blinks. */
        val visible: Boolean,
        val thumbPath: String? = null,
        val percent: Int = 0,
        /** The notes found so far, as shares of the whole file: the strip fills up like a progress bar. */
        val bars: List<RecordingBar> = emptyList(),
        /** Null until the speed has been measured. */
        val remainingSec: Int? = null,
        /** «Остановить разбор?» is up; the analysis goes on underneath. */
        val asking: Boolean = false,
    ) : VideoImport

    data class Failed(
        val pieceId: Long,
        val reason: VideoImportFailure,
        /** The shot that can still be sent somewhere before it is deleted; null for a picked video — its original is in the gallery. */
        val rescuePath: String? = null,
        val missingMb: Int? = null,
    ) : VideoImport
}

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
 * Turns a video into a take of a piece (spec 3.19): moves or copies it in, listens to its sound
 * track, saves the session. A singleton with a scope of its own — leaving the screen, or the app
 * going to the background, must not tear an analysis that may take a minute; the screen only
 * watches [state]. One video at a time.
 */
class VideoTakeImporter(
    private val files: VideoFiles,
    private val analyzer: FileTakeAnalyzer,
    private val sessions: SessionRepository,
    private val configSource: IntonationConfigSource,
    private val practice: RunningPracticeStore,
    private val repertoireConfig: RepertoireConfig,
    private val intonationDefaults: IntonationConfig,
    private val clock: WallClock,
    private val elapsed: ElapsedClock,
    private val speed: AnalysisSpeed,
    dispatcher: CoroutineDispatcher,
) {
    data class Saved(val pieceId: Long, val sessionId: Long)

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var job: Job? = null
    private var current: PlatformFile? = null

    private val mutableState = MutableStateFlow<VideoImport>(VideoImport.Idle)
    val state: StateFlow<VideoImport> = mutableState.asStateFlow()

    private val mutableSaved = MutableSharedFlow<Saved>(extraBufferCapacity = 4)
    val saved: Flow<Saved> = mutableSaved

    /** The system camera came back with [cameraFile] written. */
    fun shot(pieceId: Long, cameraFile: PlatformFile) {
        if (mutableState.value != VideoImport.Idle) return
        val returnedAt = clock.millis()
        mutableState.value = VideoImport.Working(pieceId, shot = true, copying = false, visible = false)
        job = scope.launch {
            val file = files.adopt(cameraFile)
            if (file == null) {
                cameraFile.deleteFile()
                mutableState.value = VideoImport.Failed(pieceId, VideoImportFailure.CANNOT_OPEN)
            } else {
                take(pieceId, file, shot = true, returnedAtEpochMs = returnedAt)
            }
        }
    }

    /** The system picker returned [uri]. */
    fun picked(pieceId: Long, uri: String) {
        if (mutableState.value != VideoImport.Idle) return
        val size = files.sizeOf(uri)
        val missing = size?.let { it + repertoireConfig.videoFreeSpaceMarginBytes - files.freeBytes() } ?: 0
        if (missing > 0) {
            mutableState.value = VideoImport.Failed(pieceId, VideoImportFailure.NO_SPACE, missingMb = ((missing + BYTES_PER_MB - 1) / BYTES_PER_MB).toInt())
            return
        }
        // Copying is shown at once and without a number: it is seconds as a rule, and no estimate of it is worth the name.
        mutableState.value = VideoImport.Working(pieceId, shot = false, copying = true, visible = true)
        job = scope.launch {
            val file = files.import(uri)
            if (file == null) {
                mutableState.value = VideoImport.Failed(pieceId, VideoImportFailure.CANNOT_OPEN)
            } else {
                take(pieceId, file, shot = false, returnedAtEpochMs = clock.millis())
            }
        }
    }

    private suspend fun take(pieceId: Long, file: PlatformFile, shot: Boolean, returnedAtEpochMs: Long) {
        current = file
        fun fail(reason: VideoImportFailure) {
            current = null
            // A picked video has its original in the gallery; a shot is kept until the player says what becomes of it.
            if (!shot) files.discard(file)
            mutableState.value = VideoImport.Failed(pieceId, reason, rescuePath = file.filePath.takeIf { shot })
        }

        val info = files.info(file) ?: return fail(VideoImportFailure.CANNOT_OPEN)
        if (!info.hasSound) return fail(VideoImportFailure.NO_SOUND)
        if (info.durationMs > intonationDefaults.maxSessionMs) return fail(VideoImportFailure.TOO_LONG)
        files.makeThumb(file)

        val started = elapsed.nowMs()
        val showAtOnce = info.durationMs * speed.factor > SHOW_FROM_MS
        var shownAt: Long? = started.takeIf { showAtOnce || !shot }
        mutableState.value = VideoImport.Working(pieceId, shot, copying = false, visible = shownAt != null, thumbPath = files.thumbOf(file.fileName)?.filePath)
        // The estimate may be wrong — a slow phone, a first analysis: one that drags on gets its sheet after all.
        val late = scope.launch {
            delay(SHOW_FROM_MS)
            mutableState.update { if (it is VideoImport.Working && !it.visible) it.copy(visible = true).also { shownAt = elapsed.nowMs() } else it }
        }
        var lastShown = 0L
        val config = configSource.config.first()
        // A shot is dated by when it was shot; a picked video by what it says of itself, else by now.
        val startedAt = if (shot) returnedAtEpochMs - info.durationMs else info.createdAtEpochMs ?: returnedAtEpochMs
        val result = try {
            analyzer.analyze(file, config, startedAt, audioFileName = file.fileName) { progress ->
                // from the analysing thread; a StateFlow takes that, and ten updates a second are plenty
                val now = elapsed.nowMs()
                if (now - lastShown >= PROGRESS_EVERY_MS) {
                    lastShown = now
                    val spent = now - started
                    val remaining = if (progress.fraction >= REMAINING_FROM) ((spent * (1 - progress.fraction) / progress.fraction) / MS_PER_SECOND).toInt().coerceAtLeast(1) else null
                    mutableState.update {
                        if (it is VideoImport.Working) it.copy(percent = (progress.fraction * PERCENT).toInt().coerceIn(0, PERCENT), bars = progress.bars, remainingSec = remaining) else it
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
                    mutableState.update { if (it is VideoImport.Working) it.copy(percent = PERCENT, remainingSec = null) else it }
                    val stillToShow = MIN_SHOWN_MS - (elapsed.nowMs() - at)
                    if (stillToShow > 0) delay(stillToShow)
                }
                val id = sessions.save(result.session.copy(pieceId = pieceId, videoPath = file.fileName))
                current = null
                // Filming oneself is practising: a practice with a video take in it is not a forgotten one (spec 3.12).
                if (shot) markSound(returnedAtEpochMs)
                mutableState.value = VideoImport.Idle
                mutableSaved.emit(Saved(pieceId, id))
            }
            FileAnalysisResult.NoNotes -> fail(VideoImportFailure.NO_NOTES)
            FileAnalysisResult.TooLong -> fail(VideoImportFailure.TOO_LONG)
            FileAnalysisResult.TooShort, FileAnalysisResult.UnsupportedRate, FileAnalysisResult.CannotOpen -> fail(VideoImportFailure.CANNOT_OPEN)
        }
    }

    private suspend fun markSound(atEpochMs: Long) {
        val running = practice.running.first() ?: return
        if (atEpochMs >= running.startedAtEpochMs && atEpochMs > (running.lastSoundEpochMs ?: 0)) practice.markSound(atEpochMs)
    }

    /** «Отмена»: a picked video simply goes; a shot is asked about, and the analysis goes on meanwhile. */
    fun cancelClicked() {
        val working = mutableState.value as? VideoImport.Working ?: return
        if (working.shot) mutableState.value = working.copy(asking = true, visible = true) else stopAndDiscard()
    }

    /** «Продолжить разбор». */
    fun continueClicked() = mutableState.update { if (it is VideoImport.Working) it.copy(asking = false) else it }

    /**
     * «Отправить видео»: the analysis stops, the shot stays until it is deleted. Answers with the
     * file to hand to the system sheet.
     */
    fun sendClicked(): String? = when (val now = mutableState.value) {
        is VideoImport.Working -> current?.takeIf { now.shot && !now.copying }?.let { file ->
            job?.cancel()
            mutableState.value = VideoImport.Failed(now.pieceId, VideoImportFailure.STOPPED, rescuePath = file.filePath)
            file.filePath
        }
        is VideoImport.Failed -> now.rescuePath
        VideoImport.Idle -> null
    }

    /** «Удалить» — and «Понятно», which has nothing left to delete. */
    fun dismiss() {
        when (val now = mutableState.value) {
            is VideoImport.Working -> stopAndDiscard()
            is VideoImport.Failed -> {
                now.rescuePath?.let { files.discard(platformFile(it)) }
                current = null
                mutableState.value = VideoImport.Idle
            }
            VideoImport.Idle -> Unit
        }
    }

    private fun stopAndDiscard() {
        job?.cancel()
        current?.let(files::discard)
        current = null
        mutableState.value = VideoImport.Idle
    }

    private companion object {
        const val SHOW_FROM_MS = 700L
        const val MIN_SHOWN_MS = 1_200L
        const val PROGRESS_EVERY_MS = 100L
        const val REMAINING_FROM = 0.2f
        const val PERCENT = 100
        const val MS_PER_SECOND = 1_000
        const val BYTES_PER_MB = 1024L * 1024
    }
}
