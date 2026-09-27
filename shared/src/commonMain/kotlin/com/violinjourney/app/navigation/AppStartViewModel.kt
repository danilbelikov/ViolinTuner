package com.violinjourney.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.violinjourney.app.core.audio.backing.BackingPcm
import com.violinjourney.app.core.audio.playback.SessionWaveforms
import com.violinjourney.app.core.audio.share.ShareFiles
import com.violinjourney.app.core.data.profile.AvatarFiles
import com.violinjourney.app.core.domain.backing.BackingRepository
import com.violinjourney.app.core.domain.backing.NoBackings
import com.violinjourney.app.core.domain.practice.BlockStore
import com.violinjourney.app.core.domain.practice.ForgottenPractice
import com.violinjourney.app.core.domain.practice.NoBlocks
import com.violinjourney.app.core.domain.practice.PracticeCheck
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.practice.PracticeFinisher
import com.violinjourney.app.core.domain.practice.PracticeRepository
import com.violinjourney.app.core.domain.practice.RunningPractice
import com.violinjourney.app.core.domain.practice.RunningPracticeStore
import com.violinjourney.app.core.domain.progress.ProfileRepository
import com.violinjourney.app.core.domain.progress.Progress
import com.violinjourney.app.core.domain.progress.TrophyAwarder
import com.violinjourney.app.core.domain.progress.TrophyRepository
import com.violinjourney.app.core.domain.repertoire.RepertoireRepository
import com.violinjourney.app.core.domain.session.SessionRepository
import com.violinjourney.app.core.settings.SettingsRepository
import com.violinjourney.app.core.time.WallClock
import com.violinjourney.app.feature.practice.PracticePrompt
import com.violinjourney.app.feature.practice.PracticePromptEffect
import com.violinjourney.app.feature.practice.PracticePromptIntent
import com.violinjourney.app.feature.practice.PracticeReducer
import com.violinjourney.app.feature.practice.summarySheetOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Decides where the app starts. Only the first stored value counts: a start destination that
 * changed under a live NavHost would rebuild the graph, so later moves between onboarding and
 * the tabs are explicit navigation. Also owns what concerns every tab: the mark of a running
 * practice, the forgotten-practice prompt (spec 3.12) and the giving of trophies (spec 5.7).
 */
open class AppStartViewModel(
    repository: SettingsRepository,
    sessions: SessionRepository,
    private val runningPractice: RunningPracticeStore,
    private val finisher: PracticeFinisher,
    private val config: PracticeConfig,
    private val clock: WallClock,
    practice: PracticeRepository,
    trophies: TrophyRepository,
    awarder: TrophyAwarder,
    profile: ProfileRepository,
    avatarFiles: AvatarFiles,
    private val repertoire: RepertoireRepository,
    waveforms: SessionWaveforms,
    private val shareFiles: ShareFiles,
    private val blocks: BlockStore = NoBlocks,
    private val backings: BackingRepository = NoBackings,
    private val backingPcm: BackingPcm? = null,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    init {
        viewModelScope.launch { sessions.deleteOrphanAudio() }
        // waveforms are reckoned from the sound and kept beside it; those of sessions that are gone go too
        viewModelScope.launch { waveforms.deleteOrphans(sessions.sessions.first().mapNotNull { it.audioPath }.toSet()) }
        viewModelScope.launch { avatarFiles.deleteOrphans(referenced = profile.profile.first().avatarFile) }
        sweepTemporaries()
        // Trophies are given here rather than where a practice is saved: the entries change
        // from the practice screen, its sheets and the forgotten-practice prompt alike, and
        // this view model lives as long as the app is open. Giving is idempotent, so the
        // second pass that the new trophies trigger finds nothing to do.
        viewModelScope.launch {
            combine(practice.entries, trophies.trophies) { entries, given ->
                Progress.totalMs(entries) to given.mapTo(mutableSetOf()) { it.hours }
            }.collect { (totalMs, givenHours) -> awarder.award(totalMs, givenHours) }
        }
    }

    /** Null while the settings are being read: show nothing rather than the wrong screen. */
    val startRoute: StateFlow<String?> = flow {
        val done = repository.settings.first().onboardingDone
        emit(if (done) TopLevelDestination.START.route else ONBOARDING_ROUTE)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)

    /** The mark on the «Занятия» tab: a practice runs (spec 3.12). */
    val practiceRunning: StateFlow<Boolean> = runningPractice.running
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), initialValue = false)

    private val prompt = MutableStateFlow<PracticePrompt?>(null)
    val practicePrompt: StateFlow<PracticePrompt?> = prompt.asStateFlow()

    private val promptEffectChannel = Channel<PracticePromptEffect>(Channel.BUFFERED)

    /** What the prompt tells without a question: «Слишком коротко» (spec 3.12). */
    val promptEffects: Flow<PracticePromptEffect> = promptEffectChannel.receiveAsFlow()

    /**
     * An answer to the prompt still being written — one that ends the practice, «Продолжаю заниматься», «Изменить
     * время»: a second one before the prompt has changed («Закончить сейчас» tapped twice, «Сохранить» and then «Не
     * сохранять») is dropped. The finisher takes answers one at a time as well; this only keeps the prompt from
     * answering twice.
     */
    private var answering: Job? = null

    private fun answer(block: suspend () -> Unit) {
        if (answering?.isActive == true) return
        answering = viewModelScope.launch { block() }
    }

    /**
     * Every time the app comes to the front. A summary sheet already on screen stays as it is: a rotation must not turn
     * it back into the dialog it came from. The dialog follows the clock: found again later it tells the new time, and
     * past the limit it gives way to the sheet of the practice that has ended by itself (spec 3.12). Nothing is looked
     * at while an answer to the prompt is being written.
     */
    fun onAppOpened() {
        if (answering?.isActive == true) return
        val shown = prompt.value
        if (shown is PracticePrompt.Summary) return
        viewModelScope.launch {
            val running = runningPractice.running.first()
            val now = clock.millis()
            val check = running?.let { ForgottenPractice.check(it, now, config) }
            val next: PracticePrompt? = when {
                running == null || check == null -> null
                // Ended by itself; the store still holds it until the sheet is answered, so a
                // process death in between loses nothing.
                check is PracticeCheck.Expired -> expiredPrompt(running, check.endEpochMs)
                // The dialog on screen keeps the time on its button — «Закончить в 18:42» ends at what it says —
                // and tells how long the practice has run by now.
                shown is PracticePrompt.Forgotten -> shown.copy(elapsedMs = running.elapsedMs(now))
                check is PracticeCheck.Forgotten -> PracticePrompt.Forgotten(check.elapsedMs, check.lastSoundEpochMs)
                else -> null
            }
            // an answer given meanwhile has the last word
            prompt.update { if (it == shown) next else it }
        }
    }

    /**
     * Every time the app goes away. The temporary files are swept here as well as at the start
     * (spec 5.11): a phone that is not restarted for days would otherwise keep every video
     * prepared for sending — a second copy of a take each — until the next cold start.
     */
    fun onAppStopped() = sweepTemporaries()

    /** What no one will read again: files made to be handed to other apps, shots of the camera nobody imported. */
    private fun sweepTemporaries() {
        viewModelScope.launch { shareFiles.sweep(clock.millis()) }
        viewModelScope.launch { repertoire.deleteOrphanFiles() }
        // backings nobody points at any more — a replaced one whose takes are gone too (spec 3.32)
        // and their prepared sound with them: kept while the backing is, as a take under it is listened to again (spec 5.25)
        viewModelScope.launch {
            val kept = backings.deleteUnused()
            withContext(io) { backingPcm?.deleteOrphans(kept) }
        }
    }

    fun onPromptIntent(intent: PracticePromptIntent) {
        when (intent) {
            // The time on the button, not the store's: Live keeps listening under the dialog and
            // may move the last-sound mark while the user reads it.
            PracticePromptIntent.EndAtLastSound -> endForgotten(
                (prompt.value as? PracticePrompt.Forgotten)?.lastSoundEpochMs ?: clock.millis(),
            )
            PracticePromptIntent.EndNow -> endForgotten(clock.millis())
            PracticePromptIntent.Continue -> answer {
                val running = runningPractice.running.first()
                val now = clock.millis()
                prompt.value = if (running != null && !ForgottenPractice.runsAt(running.startedAtEpochMs, now, config)) {
                    // the dialog stayed on screen past the limit: the practice has ended by itself, no sign of life
                    // brings it back — its sheet takes the dialog's place (spec 3.12)
                    expiredPrompt(running, ForgottenPractice.expiredEndOf(running, config))
                } else {
                    runningPractice.markSound(now)
                    null
                }
            }
            PracticePromptIntent.EditTime -> answer {
                val running = runningPractice.running.first() ?: return@answer prompt.update { null }
                prompt.value = sheetOrDrop(running, ForgottenPractice.lengthAt(running, clock.millis(), config))
            }
            is PracticePromptIntent.SummaryStepped -> prompt.update { current ->
                (current as? PracticePrompt.Summary)?.let { PracticePrompt.Summary(PracticeReducer.step(it.sheet, intent.steps, config)) }
                    ?: current
            }
            PracticePromptIntent.SummarySaved -> answer {
                val sheet = (prompt.value as? PracticePrompt.Summary)?.sheet ?: return@answer
                finisher.save(sheet.startedAtEpochMs, PracticeReducer.durationToSave(sheet))
                prompt.value = null
            }
            PracticePromptIntent.SummaryHidden -> prompt.update { if (it is PracticePrompt.Summary) null else it }
            PracticePromptIntent.SummaryDiscarded -> answer {
                val sheet = (prompt.value as? PracticePrompt.Summary)?.sheet ?: return@answer
                // the practice of this sheet only: one begun after it was saved elsewhere runs on
                finisher.discard(sheet.startedAtEpochMs)
                prompt.value = null
            }
        }
    }

    /** The sheet of a practice that has ended by itself at [endEpochMs] (spec 3.12); null when it was too short to keep. */
    private suspend fun expiredPrompt(running: RunningPractice, endEpochMs: Long): PracticePrompt.Summary? =
        sheetOrDrop(running, endEpochMs - running.startedAtEpochMs)

    /**
     * The summary sheet of [running] at [lengthMs], with «Что играли» of this practice (spec 3.28). One under a minute
     * is not kept, as on «Занятия» (spec 3.12): it goes, and the toast says so — null then.
     */
    private suspend fun sheetOrDrop(running: RunningPractice, lengthMs: Long): PracticePrompt.Summary? {
        if (lengthMs < config.minPracticeMs) {
            dropTooShort(running)
            return null
        }
        return PracticePrompt.Summary(summarySheetOf(running, lengthMs, config, blocks, repertoire))
    }

    private suspend fun dropTooShort(running: RunningPractice) {
        finisher.discard(running.startedAtEpochMs)
        promptEffectChannel.send(PracticePromptEffect.ShowTooShort)
    }

    /** «Закончить в 18:42», «Закончить сейчас»: past the limit the practice ends at its last sound, not twelve hours in (spec 3.12). */
    private fun endForgotten(endEpochMs: Long) {
        answer {
            val running = runningPractice.running.first()
            if (running != null) {
                val length = ForgottenPractice.lengthAt(running, endEpochMs, config)
                if (length >= config.minPracticeMs) finisher.save(running.startedAtEpochMs, length) else dropTooShort(running)
            }
            prompt.value = null
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
