package com.example.violintuner.navigation

import com.example.violintuner.core.domain.backing.BackingRepository
import com.example.violintuner.core.domain.backing.NoBackings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.violintuner.core.audio.playback.SessionWaveforms
import com.example.violintuner.core.audio.share.ShareFiles
import com.example.violintuner.core.data.profile.AvatarFiles
import com.example.violintuner.core.domain.practice.BlockStore
import com.example.violintuner.core.domain.practice.ForgottenPractice
import com.example.violintuner.core.domain.practice.NoBlocks
import com.example.violintuner.core.domain.practice.PracticeCheck
import com.example.violintuner.core.domain.practice.PracticeConfig
import com.example.violintuner.core.domain.practice.PracticeFinisher
import com.example.violintuner.core.domain.practice.PracticeRepository
import com.example.violintuner.core.domain.practice.RunningPractice
import com.example.violintuner.core.domain.practice.RunningPracticeStore
import com.example.violintuner.core.domain.progress.ProfileRepository
import com.example.violintuner.core.domain.progress.Progress
import com.example.violintuner.core.domain.progress.TrophyAwarder
import com.example.violintuner.core.domain.progress.TrophyRepository
import com.example.violintuner.core.domain.repertoire.RepertoireRepository
import com.example.violintuner.core.domain.session.SessionRepository
import com.example.violintuner.core.settings.SettingsRepository
import com.example.violintuner.feature.practice.PracticePrompt
import com.example.violintuner.feature.practice.PracticePromptIntent
import com.example.violintuner.feature.practice.PracticeReducer
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Decides where the app starts. Only the first stored value counts: a start destination that
 * changed under a live NavHost would rebuild the graph, so later moves between onboarding and
 * the tabs are explicit navigation. Also owns what concerns every tab: the mark of a running
 * practice, the forgotten-practice prompt (spec 3.12) and the giving of trophies (spec 5.7).
 */
@HiltViewModel
class AppStartViewModel @Inject constructor(
    repository: SettingsRepository,
    sessions: SessionRepository,
    private val runningPractice: RunningPracticeStore,
    private val finisher: PracticeFinisher,
    private val config: PracticeConfig,
    private val clock: Clock,
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

    /**
     * Every time the app comes to the front. A prompt already on screen stays as it is: a
     * rotation must not turn the summary sheet back into the dialog it came from.
     */
    fun onAppOpened() {
        if (prompt.value != null) return
        viewModelScope.launch {
            val running = runningPractice.running.first() ?: return@launch
            prompt.value = when (val check = ForgottenPractice.check(running, clock.millis(), config)) {
                PracticeCheck.Running -> null
                is PracticeCheck.Forgotten -> PracticePrompt.Forgotten(check.elapsedMs, check.lastSoundEpochMs)
                // Ended by itself; the store still holds it until the sheet is answered, so a
                // process death in between loses nothing.
                is PracticeCheck.Expired -> PracticePrompt.Summary(summaryOf(running, check.endEpochMs - running.startedAtEpochMs))
            }
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
        viewModelScope.launch { backings.deleteUnused() }
    }

    fun onPromptIntent(intent: PracticePromptIntent) {
        when (intent) {
            // The time on the button, not the store's: Live keeps listening under the dialog and
            // may move the last-sound mark while the user reads it.
            PracticePromptIntent.EndAtLastSound -> endForgotten(
                (prompt.value as? PracticePrompt.Forgotten)?.lastSoundEpochMs ?: clock.millis(),
            )
            PracticePromptIntent.EndNow -> endForgotten(clock.millis())
            PracticePromptIntent.Continue -> viewModelScope.launch {
                runningPractice.markSound(clock.millis())
                prompt.value = null
            }
            PracticePromptIntent.EditTime -> viewModelScope.launch {
                val running = runningPractice.running.first() ?: return@launch prompt.update { null }
                val elapsed = running.elapsedMs(clock.millis()).coerceAtMost(config.maxPracticeMs)
                prompt.value = PracticePrompt.Summary(summaryOf(running, elapsed))
            }
            is PracticePromptIntent.SummaryStepped -> prompt.update { current ->
                (current as? PracticePrompt.Summary)?.let { PracticePrompt.Summary(PracticeReducer.step(it.sheet, intent.steps, config)) }
                    ?: current
            }
            PracticePromptIntent.SummarySaved -> viewModelScope.launch {
                val sheet = (prompt.value as? PracticePrompt.Summary)?.sheet ?: return@launch
                finisher.save(sheet.startedAtEpochMs, PracticeReducer.durationToSave(sheet))
                prompt.value = null
            }
            PracticePromptIntent.SummaryDiscarded -> viewModelScope.launch {
                finisher.discard()
                prompt.value = null
            }
        }
    }

    /** The summary sheet with «Что играли» (spec 3.28): the blocks of the practice under the names of their elements. */
    private suspend fun summaryOf(running: RunningPractice, durationMs: Long) = PracticeReducer.summarySheet(
        startedAtEpochMs = running.startedAtEpochMs,
        actualMs = durationMs,
        config = config,
        blocks = blocks.blocks.first(),
        titles = repertoire.pieces.first().associate { it.id to it.title },
    )

    private fun endForgotten(endEpochMs: Long) {
        viewModelScope.launch {
            val running = runningPractice.running.first()
            if (running != null) {
                val duration = (endEpochMs - running.startedAtEpochMs).coerceIn(0L, config.maxPracticeMs)
                if (duration >= config.minPracticeMs) finisher.save(running.startedAtEpochMs, duration) else finisher.discard()
            }
            prompt.value = null
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
