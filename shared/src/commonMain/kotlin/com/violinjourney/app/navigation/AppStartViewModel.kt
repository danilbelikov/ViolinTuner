package com.violinjourney.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.violinjourney.app.core.data.Housekeeping
import com.violinjourney.app.core.domain.practice.BlockStore
import com.violinjourney.app.core.domain.practice.ForgottenEndings
import com.violinjourney.app.core.domain.practice.ForgottenPractice
import com.violinjourney.app.core.domain.practice.PracticeCheck
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.practice.PracticeFinisher
import com.violinjourney.app.core.domain.practice.PracticeRepository
import com.violinjourney.app.core.domain.practice.RunningPractice
import com.violinjourney.app.core.domain.practice.RunningPracticeStore
import com.violinjourney.app.core.domain.practice.practiceTicks
import com.violinjourney.app.core.domain.progress.TrophyAwarder
import com.violinjourney.app.core.domain.repertoire.RepertoireRepository
import com.violinjourney.app.core.settings.SettingsRepository
import com.violinjourney.app.core.time.WallClock
import com.violinjourney.app.feature.practice.PracticePrompt
import com.violinjourney.app.feature.practice.PracticePromptEffect
import com.violinjourney.app.feature.practice.PracticePromptIntent
import com.violinjourney.app.feature.practice.PracticeReducer
import com.violinjourney.app.feature.practice.summarySheetOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Decides where the app starts. Only the first stored value counts: a start destination that
 * changed under a live NavHost would rebuild the graph, so later moves between onboarding and
 * the tabs are explicit navigation. Also owns what concerns every tab: the mark of a running
 * practice and the forgotten-practice prompt (spec 3.12). It lives as long as the app is open, so it
 * also says when two things are done that belong to no screen: the sweeping of files no one will read
 * again ([Housekeeping] knows what goes) and the giving of trophies ([TrophyAwarder.follow], spec 5.7).
 */
open class AppStartViewModel(
    repository: SettingsRepository,
    private val runningPractice: RunningPracticeStore,
    private val finisher: PracticeFinisher,
    private val config: PracticeConfig,
    private val clock: WallClock,
    practice: PracticeRepository,
    awarder: TrophyAwarder,
    private val repertoire: RepertoireRepository,
    private val housekeeping: Housekeeping,
    private val blocks: BlockStore,
) : ViewModel() {
    init {
        viewModelScope.launch { housekeeping.atStart() }
        viewModelScope.launch { housekeeping.sweepTemporaries() }
        viewModelScope.launch { awarder.follow(practice.entries) }
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

    /**
     * The numbers of «Занятие не закончено» while it is shown (spec 3.36.3): «Идёт …» and what each ending would save, paced by the
     * clock of the running practice ([practiceTicks] — the one of the timer of «Занятия») and changed once a minute, as the minutes
     * of 5.6 do; fresh again when the app comes back. Each number is what its answer saves: «Закончить сейчас» by the practice the
     * store holds on the tick — the one [endForgotten] ends, with the marks Live took under the sheet — «Закончить в 18:42» at the mark
     * of the question's snapshot ([ForgottenPractice.endings]). Null without the sheet, and when the practice has gone meanwhile.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val promptEndings: StateFlow<ForgottenEndings?> = prompt
        .map { it as? PracticePrompt.Forgotten }
        .distinctUntilChanged()
        .flatMapLatest { shown ->
            if (shown == null) {
                flowOf(null)
            } else {
                runningPractice.practiceTicks(clock).map { tick ->
                    tick?.let {
                        // a practice begun anew under the sheet is not the one it asks about: its numbers stay the snapshot's
                        val practice = it.practice.takeIf { held -> held.startedAtEpochMs == shown.practice.startedAtEpochMs } ?: shown.practice
                        ForgottenPractice.endings(practice, it.nowEpochMs, config, asked = shown.practice)
                    }
                }
            }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), initialValue = null)

    /** The step of «−» and «+» in the forgotten-practice prompt (spec 3.12), from the injected [PracticeConfig]. */
    val promptStepMinutes: Int get() = config.editStepMinutes

    private val promptEffectChannel = Channel<PracticePromptEffect>(Channel.BUFFERED)

    /** What the prompt tells without a question: «Слишком коротко» (spec 3.12). */
    val promptEffects: Flow<PracticePromptEffect> = promptEffectChannel.receiveAsFlow()

    /**
     * An answer to the prompt still being written — one that ends the practice, «Продолжаю заниматься», «Указать, сколько
     * играли»: a second one before the prompt has changed («Закончить сейчас» tapped twice, «Сохранить» and then «Не
     * сохранять») is dropped. The finisher takes answers one at a time as well; this only keeps the prompt from
     * answering twice.
     */
    private var answering: Job? = null

    private fun answer(block: suspend () -> Unit) {
        if (answering?.isActive == true) return
        answering = viewModelScope.launch { block() }
    }

    /**
     * Every time the app comes to the front. «Закончить занятие» already on screen stays as it is: a rotation must not turn
     * it back into the sheet it came from. «Занятие не закончено» found again keeps its snapshot — its numbers follow the clock
     * by themselves ([promptEndings]) — and past the limit it gives way to «Закончить занятие» of the practice that has ended
     * by itself (spec 3.12, 3.36.3). Nothing is looked at while an answer to the prompt is being written.
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
                // The sheet on screen keeps the time on its button — «Закончить в 18:42» ends at what it says; how long the
                // practice has run by now is the business of its numbers. Only a practice begun anew under it is asked anew.
                shown is PracticePrompt.Forgotten && shown.practice.startedAtEpochMs == running.startedAtEpochMs -> shown
                check is PracticeCheck.Forgotten -> PracticePrompt.Forgotten(running)
                else -> null
            }
            // an answer given meanwhile has the last word
            prompt.update { if (it == shown) next else it }
        }
    }

    /** Every time the app goes away: the temporary files are swept here as well as at the start (spec 5.11). */
    fun onAppStopped() {
        viewModelScope.launch { housekeeping.sweepTemporaries() }
    }

    fun onPromptIntent(intent: PracticePromptIntent) {
        when (intent) {
            // The time on the button, not the store's: Live keeps listening under the sheet and
            // may move the last-sound mark while the user reads it.
            PracticePromptIntent.EndAtLastSound -> endForgotten(
                (prompt.value as? PracticePrompt.Forgotten)?.practice?.lastSoundEpochMs ?: clock.millis(),
            )
            PracticePromptIntent.EndNow -> endForgotten(clock.millis())
            PracticePromptIntent.Continue -> answer {
                val running = runningPractice.running.first()
                val now = clock.millis()
                prompt.value = if (running != null && !ForgottenPractice.runsAt(running.startedAtEpochMs, now, config)) {
                    // the sheet stayed on screen past the limit: the practice has ended by itself, no sign of life
                    // brings it back — «Закончить занятие» takes the sheet's place (spec 3.12)
                    expiredPrompt(running, ForgottenPractice.expiredEndOf(running, config))
                } else {
                    // a sign of life as a sound is, but the answer's own: the next question says «вы ответили…» (spec 3.36.3)
                    runningPractice.markContinued(now)
                    null
                }
            }
            PracticePromptIntent.SetLength -> answer {
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
