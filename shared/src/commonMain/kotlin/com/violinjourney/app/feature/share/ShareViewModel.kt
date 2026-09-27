package com.violinjourney.app.feature.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.violinjourney.app.core.analytics.Analytics
import com.violinjourney.app.core.analytics.ErrorGroup
import com.violinjourney.app.core.analytics.NoOpAnalytics
import com.violinjourney.app.core.audio.backing.BackingPcm
import com.violinjourney.app.core.audio.recording.SessionAudioFiles
import com.violinjourney.app.core.audio.share.RenderBacking
import com.violinjourney.app.core.audio.share.ShareFiles
import com.violinjourney.app.core.audio.share.ShareNames
import com.violinjourney.app.core.audio.share.SoundRenderer
import com.violinjourney.app.core.di.ElapsedClock
import com.violinjourney.app.core.domain.backing.Backing
import com.violinjourney.app.core.domain.backing.BackingRepository
import com.violinjourney.app.core.domain.backing.NoBackings
import com.violinjourney.app.core.domain.backing.TakeBacking
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
import com.violinjourney.app.core.recording.video.VideoFiles
import com.violinjourney.app.feature.sound.SoundReducer
import kotlin.concurrent.Volatile
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
class RenderSpeed {
    @Volatile var factor: Double = START_FACTOR
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
    private val backings: BackingRepository = NoBackings,
    private val backingPcm: BackingPcm? = null,
    private val analytics: Analytics = NoOpAnalytics(),
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
            val effective = sound.effective(sessionId).first().settings
            val title = texts.title(session.title, pieceTitle, session.startedAtEpochMs)
            // the backing the take was made under, if its copy is still there (spec 3.32)
            val under = backings.takeBackings.first().firstOrNull { it.sessionId == sessionId }
                ?.let { take -> backings.backing(take.backingId)?.takeIf { backingPcm != null }?.let { it to take } }
            takeBacking = under
            val info = ShareInfo(
                sessionId = sessionId,
                fileName = ShareNames.fileName(title),
                durationMs = session.durationMs,
                processedBytes = ((session.durationMs + SoundRules.tailSec(effective, config) * MS_PER_SECOND) / MS_PER_SECOND * SoundRenderer.BIT_RATE / BITS_PER_BYTE).toLong(),
                originalBytes = originalBytes,
                caption = SoundReducer.captionOf(effective, sound.presets.first(), config),
                message = texts.message(session.title, pieceTitle, session.scorePercent, session.startedAtEpochMs),
                videoFileName = session.videoPath?.let { ShareNames.videoFileName(title) },
                originalVideoFileName = session.videoPath?.let { ShareNames.originalVideoFileName(title, it) },
                resolution = picture?.let { minOf(it.width, it.height) } ?: 0,
                processed = !SoundRules.isNeutral(effective),
                backing = under != null,
                backingBytes = ((session.durationMs + SoundRules.tailSec(effective, config) * MS_PER_SECOND) / MS_PER_SECOND * SoundRenderer.STEREO_BIT_RATE / BITS_PER_BYTE).toLong(),
            )
            audio = file
            settings = effective
            when {
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
            ShareIntent.Dismissed -> {
                job?.cancel()
                mutableSheet.value = null
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
                (mutableSheet.value as? ShareSheet.Choose)?.takeIf { it.busy }?.let { showProgress(it.info, 0, null) }
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

    private fun showProgress(info: ShareInfo, percent: Int, remainingSec: Int?) {
        if (progressShownAt == null) progressShownAt = clock.nowMs()
        mutableSheet.value = ShareSheet.Preparing(info, percent, remainingSec)
    }

    private suspend fun sendOriginal(info: ShareInfo, withText: Boolean) {
        val copy = audio?.let { files.original(it, info.fileNameOf(ShareVariant.ORIGINAL)) }
        if (copy == null) {
            mutableSheet.value = ShareSheet.Failed(info)
        } else {
            holdProgress(info)
            mutableSheet.value = null
            effectChannel.send(ShareEffect.Send(copy, info.message.takeIf { withText }))
        }
    }

    private suspend fun sendProcessed(choice: ShareSheet.Choose) {
        val source = audio ?: return
        val current = settings ?: return
        val info = choice.info
        val under = takeBacking?.takeIf { choice.variant == ShareVariant.BACKING }
        // the mix is a file of its own: another shift or level is another file
        val key = under?.let { (backing, take) -> "${source.fileName}-backing-${backing.id}-${take.offsetMs}-${take.gainDb}" } ?: source.fileName
        val target = files.processed(key, current, info.fileNameOf(choice.variant))
        if (withContext(io) { target.sizeBytes() } == 0L) {
            if (!prepare(choice, source, current, target)) {
                mutableSheet.value = ShareSheet.Failed(info)
                return
            }
        }
        holdProgress(info)
        // made now or long ago, it is handed over now: the sweep must not take it from under the receiver
        files.handedOver(target)
        mutableSheet.value = null
        effectChannel.send(ShareEffect.Send(target, info.message.takeIf { choice.withText }))
    }

    /** Renders into a `.part` beside [target] and renames: what lies under the final name is always whole. */
    private suspend fun prepare(choice: ShareSheet.Choose, source: PlatformFile, settings: SoundSettings, target: PlatformFile): Boolean = coroutineScope {
        val info = choice.info
        val soundMs = info.durationMs + (SoundRules.tailSec(settings, config) * MS_PER_SECOND).toLong()
        val started = clock.nowMs()
        lastPercent = 0
        if (soundMs * speed.factor > SHOW_PROGRESS_FROM_MS) {
            showProgress(info, 0, null)
        } else if (mutableSheet.value !is ShareSheet.Preparing) {
            mutableSheet.value = choice.copy(busy = true)
        }

        // The estimate may be wrong — a slow phone, a first render: a preparation that drags on gets its progress screen
        // after all. A child of this work: «Отмена» cancels it at once, not only once the render has let go.
        val late = launch {
            delay(SHOW_PROGRESS_FROM_MS)
            if ((mutableSheet.value as? ShareSheet.Choose)?.busy == true) showProgress(info, lastPercent, null)
        }
        // the render thread tells its progress; a work cancelled meanwhile says nothing more
        val work = coroutineContext.job
        val part = target.sibling(target.fileName + PART)
        var lastShown = 0L
        val whole = try {
            // the picture of a video take is copied as it is; only the sound is rendered, so the estimate of the sound holds
            val video = info.video && (choice.variant == ShareVariant.PROCESSED || choice.variant == ShareVariant.BACKING)
            val under = takeBacking?.takeIf { choice.variant == ShareVariant.BACKING }?.let { (backing, take) ->
                val pcm = backingPcm
                RenderBacking(pcm = { rate -> pcm?.prepare(backing, rate) }, offsetMs = take.offsetMs, gainDb = take.gainDb)
            }
            val render: suspend (PlatformFile, SoundSettings, PlatformFile, (Float) -> Unit) -> Boolean = when {
                under != null && video -> { from, with, to, progress -> renderer.renderVideoWithBacking(from, with, under, to, progress) }
                under != null -> { from, with, to, progress -> renderer.renderWithBacking(from, with, under, to, progress) }
                video -> renderer::renderVideo
                else -> renderer::render
            }
            render(source, settings, part) { fraction ->
                // from the rendering thread; a StateFlow takes that, and ten updates a second are plenty
                if (work.isActive) {
                    val now = clock.nowMs()
                    val percent = (fraction * PERCENT).toInt().coerceIn(0, PERCENT)
                    lastPercent = percent
                    if (mutableSheet.value is ShareSheet.Preparing && now - lastShown >= PROGRESS_EVERY_MS) {
                        lastShown = now
                        val elapsed = now - started
                        val remaining = if (fraction >= REMAINING_FROM) ((elapsed * (1 - fraction) / fraction) / MS_PER_SECOND).toInt().coerceAtLeast(1) else null
                        // in one step: a «Закрыть» or an «Отмена» written meanwhile is never written over
                        mutableSheet.update { if (it is ShareSheet.Preparing) ShareSheet.Preparing(info, percent, remaining) else it }
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
        if (kept) speed.measured(soundMs, clock.nowMs() - started)
        kept
    }

    /** A progress screen that was shown is shown long enough to be read: nothing on this app's screens flashes by. */
    private suspend fun holdProgress(info: ShareInfo) {
        val at = progressShownAt ?: return
        mutableSheet.value = ShareSheet.Preparing(info, PERCENT, null)
        val stillToShow = MIN_PROGRESS_SHOWN_MS - (clock.nowMs() - at)
        if (stillToShow > 0) delay(stillToShow)
    }

    @Volatile private var lastPercent = 0

    private companion object {
        const val SHOW_PROGRESS_FROM_MS = 700L
        const val MIN_PROGRESS_SHOWN_MS = 1_200L
        const val PROGRESS_EVERY_MS = 100L
        const val REMAINING_FROM = 0.2f
        const val PERCENT = 100
        const val MS_PER_SECOND = 1_000.0
        const val BITS_PER_BYTE = 8
        const val PART = ".part"
    }
}
