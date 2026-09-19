package com.example.violintuner.feature.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.violintuner.core.audio.recording.SessionAudioFiles
import com.example.violintuner.core.audio.share.ShareFiles
import com.example.violintuner.core.audio.share.ShareNames
import com.example.violintuner.core.audio.share.SoundFileRenderer
import com.example.violintuner.core.audio.share.SoundRenderer
import com.example.violintuner.core.domain.repertoire.RepertoireRepository
import com.example.violintuner.core.domain.session.SessionRepository
import com.example.violintuner.core.domain.sound.SoundConfig
import com.example.violintuner.core.domain.sound.SoundRepository
import com.example.violintuner.core.domain.sound.SoundRules
import com.example.violintuner.core.domain.sound.SoundSettings
import com.example.violintuner.feature.sound.SoundReducer
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Milliseconds that only go forward; the wall clock may jump. */
fun interface ElapsedClock {
    fun nowMs(): Long
}

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
@Singleton
class RenderSpeed @Inject constructor() {
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
 * screen, and one that was shown stays long enough to be read.
 */
@HiltViewModel
class ShareViewModel @Inject constructor(
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
) : ViewModel() {

    private val mutableSheet = MutableStateFlow<ShareSheet?>(null)
    val sheet: StateFlow<ShareSheet?> = mutableSheet.asStateFlow()

    private val effectChannel = Channel<ShareEffect>(Channel.BUFFERED)
    val effects: Flow<ShareEffect> = effectChannel.receiveAsFlow()

    private var audio: File? = null
    private var settings: SoundSettings? = null
    private var job: Job? = null

    /** A recording without sound has nothing to share; the entry points do not offer it, and a stray call is ignored. */
    fun start(sessionId: Long) {
        if (job?.isActive == true || mutableSheet.value != null) return
        job = viewModelScope.launch {
            val session = sessions.sessions.first().firstOrNull { it.id == sessionId } ?: return@launch
            val file = session.audioPath?.let(audioFiles::existing) ?: return@launch
            val pieceTitle = session.pieceId?.let { repertoire.piece(it) }?.title
            val effective = sound.effective(sessionId).first().settings
            val title = texts.title(session.title, pieceTitle, session.startedAtEpochMs)
            val info = ShareInfo(
                sessionId = sessionId,
                fileName = ShareNames.fileName(title),
                durationMs = session.durationMs,
                processedBytes = ((session.durationMs + SoundRules.tailSec(effective, config) * MS_PER_SECOND) / MS_PER_SECOND * SoundFileRenderer.BIT_RATE / BITS_PER_BYTE).toLong(),
                originalBytes = file.length(),
                caption = SoundReducer.captionOf(effective, sound.presets.first(), config),
                message = texts.message(session.title, pieceTitle, session.scorePercent, session.startedAtEpochMs),
            )
            audio = file
            settings = effective
            if (SoundRules.isNeutral(effective)) {
                // Nothing to choose from: the sheet is skipped (spec 3.17).
                sendOriginal(info, withText = false)
            } else {
                mutableSheet.value = ShareSheet.Choose(info, ShareVariant.PROCESSED, withText = true, busy = false)
            }
        }
    }

    fun onIntent(intent: ShareIntent) {
        val current = mutableSheet.value
        when (intent) {
            is ShareIntent.VariantSelected -> mutableSheet.update { (it as? ShareSheet.Choose)?.takeIf { c -> !c.busy }?.copy(variant = intent.variant) ?: it }
            is ShareIntent.TextToggled -> mutableSheet.update { (it as? ShareSheet.Choose)?.takeIf { c -> !c.busy }?.copy(withText = intent.withText) ?: it }
            ShareIntent.ContinueClicked -> (current as? ShareSheet.Choose)?.takeIf { !it.busy }?.let { choice ->
                lastChoice = choice
                job = viewModelScope.launch {
                    if (choice.variant == ShareVariant.ORIGINAL) sendOriginal(choice.info, choice.withText) else sendProcessed(choice)
                }
            }
            ShareIntent.CancelClicked -> {
                job?.cancel() // the renderer removes what it had written
                mutableSheet.value = lastChoice?.copy(busy = false)
            }
            ShareIntent.RetryClicked -> (current as? ShareSheet.Failed)?.let {
                val choice = lastChoice ?: ShareSheet.Choose(it.info, ShareVariant.PROCESSED, withText = true, busy = false)
                job = viewModelScope.launch { if (choice.variant == ShareVariant.ORIGINAL) sendOriginal(choice.info, choice.withText) else sendProcessed(choice) }
            }
            ShareIntent.SendOriginalClicked -> (current as? ShareSheet.Failed)?.let {
                job = viewModelScope.launch { sendOriginal(it.info, lastChoice?.withText ?: false) }
            }
            ShareIntent.Dismissed -> {
                job?.cancel()
                mutableSheet.value = null
            }
        }
    }

    private var lastChoice: ShareSheet.Choose? = null

    private suspend fun sendOriginal(info: ShareInfo, withText: Boolean) {
        val copy = audio?.let { files.original(it, info.fileName) }
        if (copy == null) {
            mutableSheet.value = ShareSheet.Failed(info)
        } else {
            mutableSheet.value = null
            effectChannel.send(ShareEffect.Send(copy, info.message.takeIf { withText }))
        }
    }

    private suspend fun sendProcessed(choice: ShareSheet.Choose) {
        val source = audio ?: return
        val current = settings ?: return
        val info = choice.info
        val target = files.processed(source.name, current, info.fileName)
        if (!target.isFile || target.length() == 0L) {
            if (!prepare(choice, source, current, target)) {
                mutableSheet.value = ShareSheet.Failed(info)
                return
            }
        }
        mutableSheet.value = null
        effectChannel.send(ShareEffect.Send(target, info.message.takeIf { choice.withText }))
    }

    /** Renders into a `.part` beside [target] and renames: what lies under the final name is always whole. */
    private suspend fun prepare(choice: ShareSheet.Choose, source: File, settings: SoundSettings, target: File): Boolean {
        val info = choice.info
        val soundMs = info.durationMs + (SoundRules.tailSec(settings, config) * MS_PER_SECOND).toLong()
        val started = clock.nowMs()
        var shownAt: Long? = null
        fun showProgress(percent: Int, remainingSec: Int?) {
            if (shownAt == null) shownAt = clock.nowMs()
            mutableSheet.value = ShareSheet.Preparing(info, percent, remainingSec)
        }
        if (soundMs * speed.factor > SHOW_PROGRESS_FROM_MS) showProgress(0, null) else mutableSheet.value = choice.copy(busy = true)

        // The estimate may be wrong — a slow phone, a first render: a preparation that drags on gets its progress screen after all.
        val late = viewModelScope.launch {
            delay(SHOW_PROGRESS_FROM_MS)
            if (mutableSheet.value is ShareSheet.Choose) showProgress(lastPercent, null)
        }
        val part = File(target.parentFile, target.name + PART)
        var lastShown = 0L
        val whole = try {
            renderer.render(source, settings, part) { fraction ->
                // from the rendering thread; a StateFlow takes that, and ten updates a second are plenty
                val now = clock.nowMs()
                lastPercent = (fraction * PERCENT).toInt().coerceIn(0, PERCENT)
                if (mutableSheet.value is ShareSheet.Preparing && now - lastShown >= PROGRESS_EVERY_MS) {
                    lastShown = now
                    val elapsed = now - started
                    val remaining = if (fraction >= REMAINING_FROM) ((elapsed * (1 - fraction) / fraction) / MS_PER_SECOND).toInt().coerceAtLeast(1) else null
                    mutableSheet.value = ShareSheet.Preparing(info, lastPercent, remaining)
                }
            }
        } finally {
            late.cancel()
        }
        if (!whole || !part.renameTo(target)) {
            part.delete()
            return false
        }
        speed.measured(soundMs, clock.nowMs() - started)
        // A progress screen that was shown is shown long enough to be read: nothing on this app's screens flashes by.
        shownAt?.let { at ->
            mutableSheet.value = ShareSheet.Preparing(info, PERCENT, null)
            val stillToShow = MIN_PROGRESS_SHOWN_MS - (clock.nowMs() - at)
            if (stillToShow > 0) delay(stillToShow)
        }
        return true
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
