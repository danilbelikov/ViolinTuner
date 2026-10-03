package com.violinjourney.app.feature.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.violinjourney.app.core.analytics.Analytics
import com.violinjourney.app.core.analytics.ErrorGroup
import com.violinjourney.app.core.audio.backing.BackingPcm
import com.violinjourney.app.core.audio.recording.SessionAudioFiles
import com.violinjourney.app.core.audio.share.RenderBacking
import com.violinjourney.app.core.audio.share.ShareFiles
import com.violinjourney.app.core.audio.share.ShareNames
import com.violinjourney.app.core.audio.share.SoundRenderer
import com.violinjourney.app.core.di.ElapsedClock
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.backing.Backing
import com.violinjourney.app.core.domain.backing.BackingRepository
import com.violinjourney.app.core.domain.backing.TakeBacking
import com.violinjourney.app.core.domain.events.EventRepository
import com.violinjourney.app.core.domain.repertoire.RepertoireRepository
import com.violinjourney.app.core.domain.session.SessionRepository
import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.domain.sound.SoundRepository
import com.violinjourney.app.core.domain.sound.SoundRules
import com.violinjourney.app.core.domain.sound.SoundSettings
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.deleteFile
import com.violinjourney.app.core.io.fileName
import com.violinjourney.app.core.io.moveTo
import com.violinjourney.app.core.io.sibling
import com.violinjourney.app.core.io.sizeBytes
import com.violinjourney.app.core.recording.overlay.NotesOverlay
import com.violinjourney.app.core.recording.overlay.NotesOverlays
import com.violinjourney.app.core.recording.overlay.NotesVideoConfig
import com.violinjourney.app.core.recording.overlay.NotesVideoFormat
import com.violinjourney.app.core.recording.overlay.NotesVideoRenderer
import com.violinjourney.app.core.recording.overlay.OverlayWords
import com.violinjourney.app.core.recording.video.VideoFiles
import com.violinjourney.app.core.recording.video.VideoInfo
import com.violinjourney.app.feature.events.EventWords
import com.violinjourney.app.feature.events.ResourceEventWords
import com.violinjourney.app.feature.sound.SoundReducer
import kotlin.concurrent.Volatile
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/** Titles and the message of a shared recording, in the language of the interface. */
interface ShareTexts {
    /** «Менуэт соль мажор · 18 сентября», «Сессия · 14 сентября», or the name the user gave. */
    fun title(title: String?, pieceTitle: String?, startedAtEpochMs: Long): String

    /** «Менуэт · 84 % · 18 сентября». */
    fun message(title: String?, pieceTitle: String?, scorePercent: Int, startedAtEpochMs: Long): String
}

/**
 * How long a render takes against the length of what is rendered — measured on this device, by
 * the renders themselves (spec 5.11). Decides whether a progress screen is worth showing.
 */
class RenderSpeed(startFactor: Double = START_FACTOR) {
    @Volatile var factor: Double = startFactor
        private set

    fun measured(soundMs: Long, tookMs: Long) {
        if (soundMs > MIN_SOUND_MS) factor = tookMs.toDouble() / soundMs
    }

    private companion object {
        const val START_FACTOR = 0.02
        const val MIN_SOUND_MS = 1_000L
    }
}

/**
 * «Поделиться» (spec 3.17), one for every place it is offered from. With processing there is a
 * choice — what is heard in the app, or the recording as it is; without it the original goes
 * straight to the system sheet. Nothing blinks: a short preparation never shows a progress
 * screen, and one that was shown stays long enough to be read. One piece of work at a time ([launchAlone]), and
 * files are written by one at a time ([writingAlone]): a new one waits until the last has let go of them.
 */
open class ShareViewModel(
    private val sessions: SessionRepository,
    private val repertoire: RepertoireRepository,
    private val sound: SoundRepository,
    private val audioFiles: SessionAudioFiles,
    private val files: ShareFiles,
    private val renderer: SoundRenderer,
    private val texts: ShareTexts,
    private val speed: RenderSpeed,
    private val clock: ElapsedClock,
    private val config: SoundConfig,
    private val videos: VideoFiles,
    private val backings: BackingRepository,
    private val backingPcm: BackingPcm?,
    private val analytics: Analytics,
    /** The events of recordings (spec 3.35): a recording of one is named by it, its title and the file too. */
    private val events: EventRepository,
    private val eventWords: EventWords = ResourceEventWords,
    /** «Видео с нотами» (spec 3.37): null where the platform makes none — the variant is not offered then. */
    private val notesRenderer: NotesVideoRenderer? = null,
    /** How fast «Видео с нотами» is made on this device: a measure of its own — the picture is encoded again (spec 5.30). */
    private val notesSpeed: RenderSpeed = RenderSpeed(NotesVideoConfig().renderSpeedStart),
    private val notesConfig: NotesVideoConfig = NotesVideoConfig(),
    /** The config of now: the overlay of a recording takes the recording's own tolerance into it, as its screen does. */
    private val intonation: IntonationConfig = IntonationConfig(),
    /** The words drawn on the picture, in the language of the interface; a JVM test has no resources to read them from. */
    private val overlayWords: suspend (NotesOverlay) -> OverlayWords = { OverlayWords.of(it) },
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val mutableSheet = MutableStateFlow<ShareSheet?>(null)
    val sheet: StateFlow<ShareSheet?> = mutableSheet.asStateFlow()

    private val effectChannel = Channel<ShareEffect>(Channel.BUFFERED)
    val effects: Flow<ShareEffect> = effectChannel.receiveAsFlow()

    private var audio: PlatformFile? = null
    private var settings: SoundSettings? = null
    private var takeBacking: Pair<Backing, TakeBacking>? = null
    private var job: Job? = null

    /** «Менуэт соль мажор · 3 октября» — the title of the recording being shared: the summary of «Видео с нотами» says it. */
    private var shareTitle = ""

    /**
     * Held by the work that writes files to share ([writingAlone]). A render sees a cancel only between its chunks, and
     * the unpacking of a backing (blocking, seconds) not at all: a cancelled one may go on writing its `.part` for a while,
     * and a second render into it would be deleted by the first as it gives up.
     */
    private val writing = Mutex()

    /**
     * A recording without sound has nothing to share; the entry points do not offer it, and a stray call is ignored. A sheet
     * already open stays; one still being opened gives way to this one. It opens at once, even while a render given up
     * a moment ago still lets go: only what writes a file waits for that.
     */
    fun start(sessionId: Long) {
        if (mutableSheet.value != null) return
        launchAlone {
            // this recording's own choice from here on: «Ещё раз» must never take another recording's
            lastChoice = null
            val session = sessions.sessions.first().firstOrNull { it.id == sessionId } ?: return@launchAlone
            val found = withContext(io) {
                session.audioPath?.let(audioFiles::existing)?.let { file ->
                    // the container of a video is opened to read its picture: never on the main thread
                    Triple(file, file.sizeBytes(), session.videoPath?.let { videos.info(file) })
                }
            } ?: return@launchAlone
            val (file, originalBytes, picture) = found
            val pieceTitle = session.pieceId?.let { repertoire.piece(it) }?.title
            // a recording of an event is named by the event until it is given a name of its own (spec 3.35): «Осенний концерт · 24 октября»
            val event = session.eventId?.let { events.recordEvents.first()[it] }
            val effective = sound.effective(sessionId).first().settings
            val title = texts.title(session.title ?: event?.let { eventWords.recordTitleOf(it) }, pieceTitle, session.startedAtEpochMs)
            // the backing the take was made under, if its copy is still there (spec 3.32)
            val under = backings.takeBackings.first().firstOrNull { it.sessionId == sessionId }
                ?.let { take -> backings.backing(take.backingId)?.takeIf { backingPcm != null }?.let { it to take } }
            takeBacking = under
            shareTitle = title
            val info = ShareInfo(
                sessionId = sessionId,
                fileName = ShareNames.fileName(title),
                durationMs = session.durationMs,
                processedBytes = ((session.durationMs + SoundRules.tailSec(effective, config) * MS_PER_SECOND) / MS_PER_SECOND * SoundRenderer.BIT_RATE / BITS_PER_BYTE).toLong(),
                originalBytes = originalBytes,
                caption = SoundReducer.captionOf(effective, sound.presets.first(), config),
                message = texts.message(session.title ?: event?.let { eventWords.nameOf(it.name) }, pieceTitle, session.scorePercent, session.startedAtEpochMs),
                videoFileName = session.videoPath?.let { ShareNames.videoFileName(title) },
                originalVideoFileName = session.videoPath?.let { ShareNames.originalVideoFileName(title, it) },
                // a sound brought in from a file goes as it came, «….mp3» (plan D48); a take's own sound is an `.m4a` either way
                originalAudioFileName = session.audioPath?.takeIf { session.videoPath == null }?.let { ShareNames.originalAudioFileName(title, it) },
                resolution = picture?.let { minOf(it.width, it.height) } ?: 0,
                processed = !SoundRules.isNeutral(effective),
                backing = under != null,
                backingBytes = ((session.durationMs + SoundRules.tailSec(effective, config) * MS_PER_SECOND) / MS_PER_SECOND * SoundRenderer.STEREO_BIT_RATE / BITS_PER_BYTE).toLong(),
                notes = notesOffer(session.durationMs, picture.takeIf { session.videoPath != null }, backing = under != null, processed = !SoundRules.isNeutral(effective)),
            )
            audio = file
            settings = effective
            when {
                // The notes on the picture come first and are chosen, where the recording is not too long for them (spec 3.37).
                info.notes?.tooLong == false -> mutableSheet.value = ShareSheet.Choose(info, ShareVariant.NOTES, withText = true, busy = false)
                // Under a backing there is always a choice, and the backing comes first (spec 3.32).
                info.backing -> mutableSheet.value = ShareSheet.Choose(info, ShareVariant.BACKING, withText = true, busy = false)
                // A video take always has a choice to make — the video or its sound alone (spec 3.19).
                info.video -> mutableSheet.value = ShareSheet.Choose(info, if (info.processed) ShareVariant.PROCESSED else ShareVariant.ORIGINAL, withText = true, busy = false)
                // Nothing to choose from: the sheet is skipped (spec 3.17) — and «Ещё раз» after a failed copy sends it again.
                !info.processed -> {
                    lastChoice = ShareSheet.Choose(info, ShareVariant.ORIGINAL, withText = false, busy = false)
                    writingAlone { sendOriginal(info, withText = false) }
                }
                else -> mutableSheet.value = ShareSheet.Choose(info, ShareVariant.PROCESSED, withText = true, busy = false)
            }
        }
    }

    fun onIntent(intent: ShareIntent) {
        val current = mutableSheet.value
        when (intent) {
            is ShareIntent.VariantSelected -> mutableSheet.update { sheet ->
                val choice = (sheet as? ShareSheet.Choose)?.takeIf { !it.busy } ?: return@update sheet
                // only what the sheet offers: «Только звук» is a video take's, a processed file needs a processing
                val offered = when (intent.variant) {
                    ShareVariant.BACKING -> choice.info.backing
                    ShareVariant.SOUND -> choice.info.video
                    ShareVariant.PROCESSED -> choice.info.processed
                    ShareVariant.ORIGINAL -> true
                    ShareVariant.NOTES -> choice.info.notes?.tooLong == false
                }
                if (offered) choice.copy(variant = intent.variant) else choice
            }
            is ShareIntent.TextToggled -> mutableSheet.update { (it as? ShareSheet.Choose)?.takeIf { c -> !c.busy }?.copy(withText = intent.withText) ?: it }
            ShareIntent.ContinueClicked -> (current as? ShareSheet.Choose)?.takeIf { !it.busy }?.let { choice ->
                lastChoice = choice
                // busy at once, whatever is chosen: a second tap finds it so and sends nothing twice
                mutableSheet.value = choice.copy(busy = true)
                launchAlone { writingAlone { send(choice) } }
            }
            ShareIntent.CancelClicked -> if (current is ShareSheet.Preparing) {
                job?.cancel() // the renderer removes what it had written; the next work to write waits for it to have done so
                mutableSheet.value = lastChoice?.copy(busy = false)
            }
            ShareIntent.RetryClicked -> (current as? ShareSheet.Failed)?.let {
                val choice = lastChoice ?: ShareSheet.Choose(it.info, ShareVariant.PROCESSED, withText = true, busy = false)
                launchAlone { writingAlone { send(choice) } }
            }
            ShareIntent.SendOriginalClicked -> (current as? ShareSheet.Failed)?.let {
                launchAlone { writingAlone { sendOriginal(it.info, lastChoice?.withText ?: false) } }
            }
            // a swipe only hides (spec 3.36.5): the choice and a failure go, sending nothing; a file being made goes on — the sheet
            // holds then, and a word of a window that opened closable still comes here: it is not heard
            ShareIntent.Dismissed -> when (current) {
                is ShareSheet.Choose -> if (!current.busy) mutableSheet.value = null
                is ShareSheet.Failed -> mutableSheet.value = null
                is ShareSheet.Preparing, null -> Unit
            }
        }
    }

    private var lastChoice: ShareSheet.Choose? = null

    /** Runs [block] as the only work of the sheet: the one before is cancelled. If it still writes, it holds [writing]. */
    private fun launchAlone(block: suspend CoroutineScope.() -> Unit) {
        job?.cancel()
        progressShownAt = null
        job = viewModelScope.launch(block = block)
    }

    /**
     * Runs [block] once no other work writes files ([writing]) — never two renders into one `.part`. The wait is
     * cancelled at once, and a work given up while it waits writes nothing; one that drags on under «Готовим…» gets its
     * progress screen — and its «Отмена» — as a preparation does.
     */
    private suspend fun CoroutineScope.writingAlone(block: suspend () -> Unit) {
        if (!writing.tryLock()) {
            val waiting = launch {
                delay(SHOW_PROGRESS_FROM_MS)
                (mutableSheet.value as? ShareSheet.Choose)?.takeIf { it.busy }?.let { showProgress(it.info, it.variant, 0, null) }
            }
            writing.lock() // a cancel while it waits throws here, holding nothing; the waiting child goes with the work
            waiting.cancel()
        }
        try {
            block()
        } finally {
            writing.unlock()
        }
    }

    private suspend fun send(choice: ShareSheet.Choose) {
        if (choice.variant == ShareVariant.ORIGINAL) sendOriginal(choice.info, choice.withText) else sendProcessed(choice)
    }

    /** When the progress screen of the current work came up: it is then shown long enough to be read. Main thread only. */
    private var progressShownAt: Long? = null

    private fun showProgress(info: ShareInfo, variant: ShareVariant, percent: Int, remainingSec: Int?) {
        if (progressShownAt == null) progressShownAt = clock.nowMs()
        mutableSheet.value = ShareSheet.Preparing(info, variant, percent, remainingSec)
    }

    private suspend fun sendOriginal(info: ShareInfo, withText: Boolean) {
        val copy = audio?.let { files.original(it, info.fileNameOf(ShareVariant.ORIGINAL)) }
        if (copy == null) {
            mutableSheet.value = ShareSheet.Failed(info)
        } else {
            holdProgress(info, ShareVariant.ORIGINAL)
            mutableSheet.value = null
            effectChannel.send(ShareEffect.Send(copy, info.message.takeIf { withText }, info.typeOf(ShareVariant.ORIGINAL)))
        }
    }

    private suspend fun sendProcessed(choice: ShareSheet.Choose) {
        val source = audio ?: return
        val current = settings ?: return
        val info = choice.info
        val notesSound = info.notes?.sound?.takeIf { choice.variant == ShareVariant.NOTES }
        val under = takeBacking?.takeIf { choice.variant == ShareVariant.BACKING || notesSound == NotesSound.BACKING }
        // the notes are drawn from the stored analysis in the words of now; the recording may be gone meanwhile
        val notes = if (choice.variant == ShareVariant.NOTES) {
            notesOf(info.sessionId) ?: run {
                mutableSheet.value = ShareSheet.Failed(info)
                return
            }
        } else {
            null
        }
        // the mix is a file of its own: another shift or level is another file; so are the notes in another language
        val backingKey = under?.let { (backing, take) -> "-backing-${backing.id}-${take.offsetMs}-${take.gainDb}" }.orEmpty()
        val notesKey = notes?.let { NOTES_KEY + it.key }.orEmpty()
        val target = files.processed(source.fileName + notesKey + backingKey, current, info.fileNameOf(choice.variant))
        if (withContext(io) { target.sizeBytes() } == 0L) {
            if (!prepare(choice, source, current, target, under, notes)) {
                mutableSheet.value = ShareSheet.Failed(info)
                return
            }
        }
        holdProgress(info, choice.variant)
        // made now or long ago, it is handed over now: the sweep must not take it from under the receiver
        files.handedOver(target)
        mutableSheet.value = null
        effectChannel.send(ShareEffect.Send(target, info.message.takeIf { choice.withText }, info.typeOf(choice.variant)))
    }

    /** Renders into a `.part` beside [target] and renames: what lies under the final name is always whole. */
    private suspend fun prepare(
        choice: ShareSheet.Choose,
        source: PlatformFile,
        settings: SoundSettings,
        target: PlatformFile,
        under: Pair<Backing, TakeBacking>?,
        notes: NotesWork?,
    ): Boolean = coroutineScope {
        val info = choice.info
        // the picture of «Видео с нотами» is encoded again: a measure of its own, counted against the length of the video
        val pace = if (notes != null) notesSpeed else speed
        val soundMs = if (notes != null) info.durationMs else info.durationMs + (SoundRules.tailSec(settings, config) * MS_PER_SECOND).toLong()
        val started = clock.nowMs()
        lastPercent = 0
        if (soundMs * pace.factor > SHOW_PROGRESS_FROM_MS) {
            showProgress(info, choice.variant, 0, null)
        } else if (mutableSheet.value !is ShareSheet.Preparing) {
            mutableSheet.value = choice.copy(busy = true)
        }

        // The estimate may be wrong — a slow phone, a first render: a preparation that drags on gets its progress screen
        // after all. A child of this work: «Отмена» cancels it at once, not only once the render has let go.
        val late = launch {
            delay(SHOW_PROGRESS_FROM_MS)
            if ((mutableSheet.value as? ShareSheet.Choose)?.busy == true) showProgress(info, choice.variant, lastPercent, null)
        }
        // the render thread tells its progress; a work cancelled meanwhile says nothing more
        val work = coroutineContext.job
        val part = target.sibling(target.fileName + PART)
        var lastShown = 0L
        // «осталось около…» of the notes is counted from the picture alone: the sound before it takes a moment or nothing at all,
        // and its tenth of the progress would make the first estimate short and the next ones grow
        val phase = if (notes != null) notesConfig.soundShare else 0f
        phaseStartedAt = if (phase == 0f) started else null
        val whole = try {
            // the picture of a video take is copied as it is; only the sound is rendered, so the estimate of the sound holds
            val video = info.video && (choice.variant == ShareVariant.PROCESSED || choice.variant == ShareVariant.BACKING)
            val mix = under?.let { (backing, take) ->
                val pcm = backingPcm
                RenderBacking(pcm = { rate -> pcm?.prepare(backing, rate) }, offsetMs = take.offsetMs, gainDb = take.gainDb)
            }
            val render: suspend (PlatformFile, SoundSettings, PlatformFile, (Float) -> Unit) -> Boolean = when {
                notes != null -> { from, with, to, progress -> renderNotes(info, notes, mix, from, with, to, progress) }
                mix != null && video -> { from, with, to, progress -> renderer.renderVideoWithBacking(from, with, mix, to, progress) }
                mix != null -> { from, with, to, progress -> renderer.renderWithBacking(from, with, mix, to, progress) }
                video -> renderer::renderVideo
                else -> renderer::render
            }
            render(source, settings, part) { fraction ->
                // from the rendering thread; a StateFlow takes that, and ten updates a second are plenty
                if (work.isActive) {
                    val now = clock.nowMs()
                    val percent = (fraction * PERCENT).toInt().coerceIn(0, PERCENT)
                    lastPercent = percent
                    if (phaseStartedAt == null && fraction >= phase) phaseStartedAt = now
                    if (mutableSheet.value is ShareSheet.Preparing && now - lastShown >= PROGRESS_EVERY_MS) {
                        lastShown = now
                        val phaseFraction = (fraction - phase) / (1 - phase)
                        val elapsed = now - (phaseStartedAt ?: now)
                        val remaining = if (phaseFraction >= REMAINING_FROM) ((elapsed * (1 - phaseFraction) / phaseFraction) / MS_PER_SECOND).roundToInt().coerceAtLeast(1) else null
                        // in one step: a «Закрыть» or an «Отмена» written meanwhile is never written over
                        mutableSheet.update { if (it is ShareSheet.Preparing) it.copy(percent = percent, remainingSec = remaining) else it }
                    }
                }
            }.also { ensureActive() } // a render that finished as it was cancelled: its answer is not wanted
        } catch (e: CancellationException) {
            // one cancelled midway removes what it wrote itself; one that finished as it was cancelled has left it whole
            withContext(NonCancellable + io) { part.deleteFile() }
            throw e
        } catch (e: Exception) {
            // A renderer answers false when it did not work; what the system refuses on iOS — no room for the AAC file,
            // a rate its encoder will not take, the backing's sound gone meanwhile — comes as a throw. Either way it is
            // the sheet «Не получилось», not a fall of the app (spec 3.17). Cancelled meanwhile: that answer stands.
            currentCoroutineContext().ensureActive()
            analytics.error(ErrorGroup.MEDIA, "a file to share could not be made", e)
            false
        } finally {
            late.cancel()
        }
        val kept = withContext(io) {
            if (whole && part.moveTo(target)) {
                true
            } else {
                part.deleteFile()
                false
            }
        }
        if (kept) pace.measured(soundMs, clock.nowMs() - started)
        kept
    }

    /** What «Видео с нотами» of [sessionId] draws: its stored analysis made into the overlay, and the words of now; null when it is gone. */
    private suspend fun notesOf(sessionId: Long): NotesWork? {
        val details = sessions.details(sessionId) ?: return null
        val overlay = NotesOverlays.of(details, sessions.sessions.first(), intonation, shareTitle, notesConfig)
        return NotesWork(overlay, overlayWords(overlay))
    }

    /**
     * «Видео с нотами» (spec 3.37): the sound of the variant first — the mix or the processed sound into an `.m4a` beside the file, the
     * video's own track as it is — then the picture with the notes beside it; the sound is the first [NotesVideoConfig.soundShare] of
     * the progress.
     */
    private suspend fun renderNotes(
        info: ShareInfo,
        notes: NotesWork,
        mix: RenderBacking?,
        source: PlatformFile,
        settings: SoundSettings,
        target: PlatformFile,
        onProgress: (Float) -> Unit,
    ): Boolean {
        val makes = notesRenderer ?: return false
        val share = notesConfig.soundShare
        val soundFile = target.sibling(target.fileName + NOTES_SOUND)
        try {
            val sound = when (info.notes?.sound) {
                NotesSound.BACKING -> soundFile.takeIf { mix != null && renderer.renderWithBacking(source, settings, mix, soundFile) { onProgress(it * share) } }
                NotesSound.PROCESSED -> soundFile.takeIf { renderer.render(source, settings, soundFile) { onProgress(it * share) } }
                NotesSound.ORIGINAL, null -> source
            } ?: return false
            // the picture begins here: «осталось около…» is counted from this moment, made or not made the sound before it
            onProgress(share)
            val made = makes.render(source, sound, notes.overlay, notes.words, target) { onProgress(share + it * (1f - share)) }
            // the renderers answer false and log why; the failure is counted, as an exception of any render is (spec 3.34)
            if (!made && currentCoroutineContext().isActive) analytics.error(ErrorGroup.MEDIA, NOTES_FAILED)
            return made
        } finally {
            withContext(NonCancellable + io) { soundFile.deleteFile() }
        }
    }

    /**
     * «Видео с нотами» for a recording of [durationMs] whose picture is [picture] (spec 3.37, 5.30): the size of the file, about what it
     * will weigh — at the frame rate of the video and, with its own sound, the bit rate of its own track — and the sound in it; null
     * without a picture or where the platform makes none.
     */
    private fun notesOffer(durationMs: Long, picture: VideoInfo?, backing: Boolean, processed: Boolean): NotesOffer? {
        if (notesRenderer == null || picture == null) return null
        val format = NotesVideoFormat.of(picture.width, picture.height, picture.frameRate, notesConfig)
        val sound = when {
            backing -> NotesSound.BACKING
            processed -> NotesSound.PROCESSED
            else -> NotesSound.ORIGINAL
        }
        val soundBitrate = when (sound) {
            NotesSound.BACKING -> SoundRenderer.STEREO_BIT_RATE
            NotesSound.PROCESSED -> SoundRenderer.BIT_RATE
            NotesSound.ORIGINAL -> picture.soundBitrate ?: notesConfig.soundBitrateGuess
        }
        return NotesOffer(
            resolution = format.shortSide,
            bytes = NotesVideoFormat.bytes(format, soundBitrate, durationMs, notesConfig),
            sound = sound,
            tooLong = durationMs > notesConfig.maxDurationMs,
            limitMinutes = (notesConfig.maxDurationMs / MS_PER_MINUTE).toInt(),
        )
    }

    /** The overlay of a recording and the words it is drawn in. */
    private class NotesWork(val overlay: NotesOverlay, val words: OverlayWords) {
        /** The file of these notes: another language, another title or another previous take is another file. */
        val key: String
            get() = (HASH_STEP * (HASH_STEP * words.hashCode() + overlay.title.hashCode()) + overlay.previous.hashCode()).toUInt().toString(HEX)
    }

    /** A progress screen that was shown is shown long enough to be read: nothing on this app's screens flashes by. */
    private suspend fun holdProgress(info: ShareInfo, variant: ShareVariant) {
        val at = progressShownAt ?: return
        mutableSheet.value = ShareSheet.Preparing(info, variant, PERCENT, null)
        val stillToShow = MIN_PROGRESS_SHOWN_MS - (clock.nowMs() - at)
        if (stillToShow > 0) delay(stillToShow)
    }

    @Volatile private var lastPercent = 0

    /** When the part of a preparation that its «осталось около…» is counted from began: the picture of the notes, all of anything else. */
    @Volatile private var phaseStartedAt: Long? = null

    private companion object {
        const val SHOW_PROGRESS_FROM_MS = 700L
        const val MIN_PROGRESS_SHOWN_MS = 1_200L
        const val PROGRESS_EVERY_MS = 100L
        const val REMAINING_FROM = 0.2f
        const val PERCENT = 100
        const val MS_PER_SECOND = 1_000.0
        const val BITS_PER_BYTE = 8
        const val PART = ".part"
        const val MS_PER_MINUTE = 60_000L

        /** The sound of «Видео с нотами» on its way into the file, beside it. */
        const val NOTES_SOUND = ".sound.m4a"
        const val NOTES_KEY = "-notes-"
        const val NOTES_FAILED = "a video with notes could not be made"
        const val HASH_STEP = 31
        const val HEX = 16
    }
}
