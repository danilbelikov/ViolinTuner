package com.violinjourney.app.core.recording.audio

import com.violinjourney.app.core.analytics.Analytics
import com.violinjourney.app.core.analytics.ErrorGroup
import com.violinjourney.app.core.audio.playback.SessionWaveforms
import com.violinjourney.app.core.di.ElapsedClock
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.domain.session.SessionRepository
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.fileName
import com.violinjourney.app.core.recording.FileAnalysisResult
import com.violinjourney.app.core.recording.FileTakeAnalyzer
import com.violinjourney.app.core.recording.ImportFailure
import com.violinjourney.app.core.recording.ImportPacing
import com.violinjourney.app.core.recording.MediaImport
import com.violinjourney.app.core.recording.TakeOwner
import com.violinjourney.app.core.recording.eventId
import com.violinjourney.app.core.recording.owner
import com.violinjourney.app.core.recording.pieceId
import com.violinjourney.app.core.recording.video.AnalysisSpeed
import com.violinjourney.app.core.settings.IntonationConfigSource
import com.violinjourney.app.core.time.WallClock
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
import kotlinx.coroutines.withContext

/**
 * «Звук из файла» (spec 3.35, 5.28; plan D15–D18): turns a sound file picked in the system's picker into a recording of its owner —
 * looks at the room and into the file, copies it in whole and unchanged as `<uuid>.sound.<ext>`, listens to it through the very chain
 * the microphone goes through ([FileTakeAnalyzer], the rates of a video from the gallery, D16) and saves the session. A singleton with a
 * scope of its own, as the importer of videos: leaving the screen, or the app going to the background, does not tear an analysis that
 * may take a minute; the screen only watches [state]. One file at a time. A failure deletes the copy; the original is never touched.
 * A file picked is no playing of the moment: no sound mark of the running practice (3.36.9 «Меняет»).
 *
 * As a video from the gallery (spec 3.36.9: «копируется … и разбирается, как видео из галереи»), only the room is measured unseen: the
 * sheet «Добавляем файл…» is up while the file is looked into — for a file in a cloud that is its download — and copied, and the screen
 * under it waits; a file without sound, longer than an hour or that does not open is still refused before it is copied (review of
 * stage 98a). A failure holds nothing — the copy is gone, the original where it was — so it is only a word for the screen of its owner:
 * the next pick, of whoever's, takes its place ([picked]), and an owner that is gone takes its own with it ([forget]).
 *
 * The date of the recording is its own (spec 3.35): the date the file says of itself, else the time it was last changed less its
 * length, else now less its length (D18). Whatever the platform throws on the way besides the answers of [PickedSounds] and the analyzer
 * is a file that did not open, told to [analytics]: the app does not fall.
 */
class AudioTakeImporter(
    private val files: PickedSounds,
    private val analyzer: FileTakeAnalyzer,
    private val sessions: SessionRepository,
    private val waveforms: SessionWaveforms,
    private val configSource: IntonationConfigSource,
    private val repertoireConfig: RepertoireConfig,
    private val intonationDefaults: IntonationConfig,
    private val clock: WallClock,
    private val elapsed: ElapsedClock,
    private val speed: AnalysisSpeed,
    private val io: CoroutineDispatcher,
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

    /**
     * The system picker returned [uri], for a recording of [owner]. One file at a time: another one while this works is let go. A failure
     * that waits is another screen's word — the screen of its owner shows it over everything, so a pick comes only from elsewhere — and it
     * holds nothing back: this file takes its place.
     */
    fun picked(owner: TakeOwner, uri: String) {
        if (mutableState.value is MediaImport.Working) {
            release(uri)
            return
        }
        current = null
        // Nothing is shown while the room is measured, off the caller's thread: a provider answers when it will.
        val measuring = MediaImport.Working(owner, shot = false, copying = true, visible = false)
        mutableState.value = measuring
        job = scope.launch {
            var file: PlatformFile? = null
            try {
                file = copyIn(owner, uri, measuring)
            } finally {
                // refused, failed, or stopped while looked at or copied: what the platform holds of the pick goes
                if (file == null) files.release(uri)
            }
        }
    }

    /** A pick this screen will not import: one file at a time, none while a recording is made. */
    fun release(uri: String) = files.release(uri)

    /** The room, the file, the copy and the analysis; null when the file does not come in, the state saying why. */
    private suspend fun copyIn(owner: TakeOwner, uri: String, measuring: MediaImport.Working): PlatformFile? {
        val missing = withContext(io) { missingBytes(uri) }
        // stopped meanwhile: that answer stands
        currentCoroutineContext().ensureActive()
        if (missing > 0) {
            mutableState.compareAndSet(measuring, MediaImport.Failed(owner, ImportFailure.NO_SPACE, missingMb = ImportPacing.missingMb(missing)))
            return null
        }
        // The sheet is up from here on, as a video's copy is, and without a number: looking into a file in a cloud is its download, and the
        // screen must not be left meanwhile — the sheet holds it; «Отмена» stops at once.
        val shown = measuring.copy(visible = true)
        if (!mutableState.compareAndSet(measuring, shown)) return null
        val looked = try {
            withContext(io) { look(uri) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // a provider is somebody else's code and answers with whatever it throws
            currentCoroutineContext().ensureActive()
            analytics.error(ErrorGroup.MEDIA, "a picked sound could not be looked into", e)
            Look.Refused(ImportFailure.CANNOT_OPEN)
        }
        // stopped meanwhile: that answer stands
        currentCoroutineContext().ensureActive()
        val probe = when (looked) {
            is Look.Refused -> {
                mutableState.compareAndSet(shown, MediaImport.Failed(owner, looked.reason))
                return null
            }
            is Look.Fine -> looked.probe
        }
        val file = try {
            files.import(uri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            analytics.error(ErrorGroup.MEDIA, "a picked sound could not be copied in", e)
            null
        }
        if (file == null) {
            mutableState.value = MediaImport.Failed(owner, ImportFailure.CANNOT_OPEN)
            return null
        }
        take(owner, file, probe)
        return file
    }

    /** What looking into the file found: a file to copy in, or why it does not come in. */
    private sealed interface Look {
        data class Fine(val probe: SoundProbe) : Look

        data class Refused(val reason: ImportFailure) : Look
    }

    /**
     * How much more room the picked file needs than there is; 0 when it fits, or when its size or the room is not told — the copy itself
     * finds out then. Blocking: a file that does not fit is not opened at all.
     */
    private fun missingBytes(uri: String): Long = try {
        files.sizeOf(uri)?.let { it + repertoireConfig.videoFreeSpaceMarginBytes - files.freeBytes() } ?: 0
    } catch (e: Exception) {
        // a provider is somebody else's code and answers with whatever it throws
        analytics.error(ErrorGroup.MEDIA, "a picked sound would not tell its size", e)
        0
    }

    /** Blocking: the decoder of the platform looks into the file — a file in a cloud comes down for it. */
    private fun look(uri: String): Look {
        val probe = files.probe(uri) ?: return Look.Refused(ImportFailure.CANNOT_OPEN)
        return when {
            !probe.hasSound -> Look.Refused(ImportFailure.NO_SOUND)
            probe.durationMs > intonationDefaults.maxSessionMs -> Look.Refused(ImportFailure.TOO_LONG)
            else -> Look.Fine(probe)
        }
    }

    private suspend fun take(owner: TakeOwner, file: PlatformFile, probe: SoundProbe) {
        current = file
        fun fail(reason: ImportFailure) {
            current = null
            files.discard(file)
            mutableState.value = MediaImport.Failed(owner, reason)
        }

        try {
            analyse(owner, file, probe, ::fail)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Stopped meanwhile — «Отмена»: that answer stands, and a next file may be on its way already.
            currentCoroutineContext().ensureActive()
            analytics.error(ErrorGroup.MEDIA, "a sound could not be taken in", e)
            fail(ImportFailure.CANNOT_OPEN)
        }
    }

    private suspend fun analyse(owner: TakeOwner, file: PlatformFile, probe: SoundProbe, fail: (ImportFailure) -> Unit) {
        val started = elapsed.nowMs()
        // the sheet of the copy is up already: the analysis goes on in it, never hidden again
        val shownAt = started
        mutableState.value = MediaImport.Working(owner, shot = false, copying = false, visible = true)
        val config = configSource.config.first()
        var lastShown = 0L
        val now = clock.millis()
        val startedAt = probe.createdAtEpochMs ?: probe.modifiedAtEpochMs?.let { it - probe.durationMs } ?: (now - probe.durationMs)
        val result = analyzer.analyze(file, config, startedAt, audioFileName = file.fileName) { progress ->
            // from the analysing thread; a StateFlow takes that, and ten updates a second are plenty
            val at = elapsed.nowMs()
            if (at - lastShown >= ImportPacing.PROGRESS_EVERY_MS) {
                lastShown = at
                val remaining = ImportPacing.remainingSec(at - started, progress.fraction)
                mutableState.update {
                    if (it is MediaImport.Working) it.copy(percent = ImportPacing.percentOf(progress.fraction), bars = progress.bars, remainingSec = remaining) else it
                }
            }
        }
        when (result) {
            is FileAnalysisResult.Recorded -> {
                speed.measured(probe.durationMs, elapsed.nowMs() - started)
                // shown long enough to be read: nothing on this app's screens flashes by
                mutableState.update { if (it is MediaImport.Working) it.copy(percent = ImportPacing.PERCENT, remainingSec = null) else it }
                val stillToShow = ImportPacing.MIN_SHOWN_MS - (elapsed.nowMs() - shownAt)
                if (stillToShow > 0) delay(stillToShow)
                // the waveform heard along with the notes: the first opening of the recording decodes nothing (spec 5.13, 0.94)
                result.waveform?.let { waveforms.keep(file, it) }
                val id = sessions.save(result.session.copy(pieceId = owner.pieceId, eventId = owner.eventId, videoPath = null))
                current = null
                mutableState.value = MediaImport.Idle
                mutableSaved.emit(Saved(owner, id))
            }
            FileAnalysisResult.NoNotes -> fail(ImportFailure.NO_NOTES)
            FileAnalysisResult.TooLong -> fail(ImportFailure.TOO_LONG)
            // a rate the detector is not tuned for (16 kHz of a dictaphone, D16) is a file that does not open, as a video's
            FileAnalysisResult.TooShort, FileAnalysisResult.UnsupportedRate, FileAnalysisResult.CannotOpen -> fail(ImportFailure.CANNOT_OPEN)
        }
    }

    /** «Отмена»: a picked file simply goes — its original is where it was. */
    fun cancelClicked() {
        if (mutableState.value is MediaImport.Working) stopAndDiscard()
    }

    /** «Понятно» of a failure; while it works, the same as «Отмена». */
    fun dismiss() {
        when (mutableState.value) {
            is MediaImport.Working -> stopAndDiscard()
            is MediaImport.Failed -> {
                current = null
                mutableState.value = MediaImport.Idle
            }
            MediaImport.Idle -> Unit
        }
    }

    /**
     * [owner] is gone — an event deleted (spec 3.35): what is on its way in for it goes with it. A file being copied or heard stops and its
     * copy goes — the original is where it was; a failure that waits for its screen is dropped — no screen will ever show it.
     */
    fun forget(owner: TakeOwner) {
        if (mutableState.value.owner == owner) dismiss()
    }

    private fun stopAndDiscard() {
        job?.cancel()
        current?.let(files::discard)
        current = null
        mutableState.value = MediaImport.Idle
    }
}
