package com.example.violintuner.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.violintuner.core.data.profile.AvatarFiles
import com.example.violintuner.core.domain.repertoire.RepertoireRepository
import com.example.violintuner.core.domain.practice.PracticeConfig
import com.example.violintuner.core.domain.practice.PracticeConfig.Companion.MS_PER_MINUTE
import com.example.violintuner.core.domain.journey.JourneyRepository
import com.example.violintuner.core.domain.journey.NoJourney
import com.example.violintuner.core.domain.journey.TaktEarning
import com.example.violintuner.core.domain.practice.PracticeFinisher
import com.example.violintuner.feature.journey.JourneyMotion
import com.example.violintuner.feature.journey.JourneyReducer
import com.example.violintuner.feature.journey.JourneyWindow
import com.example.violintuner.core.domain.practice.PracticeRepository
import com.example.violintuner.core.domain.practice.PracticeStats
import com.example.violintuner.core.domain.practice.RunningPractice
import com.example.violintuner.core.domain.practice.RunningPracticeStore
import com.example.violintuner.core.domain.practice.elapsedTicker
import com.example.violintuner.core.domain.progress.Profile
import com.example.violintuner.core.domain.progress.ProfileRepository
import com.example.violintuner.core.domain.progress.ProgressConfig
import com.example.violintuner.core.domain.progress.Trophy
import com.example.violintuner.core.domain.progress.TrophyRepository
import com.example.violintuner.core.domain.session.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PracticeViewModel @Inject constructor(
    private val repository: PracticeRepository,
    private val runningStore: RunningPracticeStore,
    private val finisher: PracticeFinisher,
    sessions: SessionRepository,
    private val config: PracticeConfig,
    repertoire: RepertoireRepository,
    private val clock: Clock,
    private val trophies: TrophyRepository,
    private val profiles: ProfileRepository,
    private val avatarFiles: AvatarFiles,
    private val progressConfig: ProgressConfig,
    journey: JourneyRepository = NoJourney,
) : ViewModel() {

    /**
     * The window into the journey (spec 3.23): its own flow, like the take on the piece screen — the
     * card changes when takts do, the rest of the screen has nothing to do with it. An earning that
     * arrives while the screen is watched shows as «+340 тактов» for a few seconds; the first value
     * read is history, not news.
     */
    val journeyWindow: StateFlow<JourneyWindow?> = channelFlow {
        var known: TaktEarning? = null
        var first = true
        journey.progress.collectLatest { progress ->
            val fresh = progress.lastEarning?.takeIf { !first && it != known && it.takts > 0 }
            known = progress.lastEarning
            first = false
            if (fresh != null) {
                send(JourneyReducer.windowOf(progress, justEarned = fresh.takts))
                delay(JourneyMotion.EARNED_PILL_MS)
            }
            send(JourneyReducer.windowOf(progress))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** What only the screen decides: the month shown, the day picked and the open sheet. */
    private data class Ui(val month: YearMonth, val selectedDate: LocalDate, val sheet: PracticeSheet?)

    private val ui = MutableStateFlow(Ui(YearMonth.from(today()), today(), sheet = null))

    private val runningMs: Flow<Long?> = runningStore.elapsedTicker(clock)

    /** Trophies and the profile travel together: `combine` takes five flows at most. */
    private val progress: Flow<Pair<List<Trophy>, Profile>> = combine(trophies.trophies, profiles.profile, ::Pair)

    val state: StateFlow<PracticeState> =
        combine(repository.entries, combine(sessions.sessions, repertoire.pieces, ::Pair), runningMs, ui, progress) { entries, (sessions, pieces), runningMs, ui, (trophies, profile) ->
            PracticeReducer.stateOf(
                entries = entries,
                sessions = sessions,
                runningMs = runningMs,
                month = ui.month,
                selectedDate = ui.selectedDate,
                sheet = ui.sheet,
                today = today(),
                zone = clock.zone,
                config = config,
                pieces = pieces,
                trophies = trophies,
                profile = profile,
                // A name without its file (cleared storage) is no photo, not a broken one.
                avatarPath = profile.avatarFile?.let(avatarFiles::existing)?.path,
                progressConfig = progressConfig,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = PracticeReducer.loading(today(), config, progressConfig),
        )

    private val effectChannel = Channel<PracticeEffect>(Channel.BUFFERED)
    val effects: Flow<PracticeEffect> = effectChannel.receiveAsFlow()

    fun onIntent(intent: PracticeIntent) {
        when (intent) {
            PracticeIntent.StartClicked -> start()
            PracticeIntent.StopClicked -> stop()
            is PracticeIntent.SummaryStepped -> updateSummary { PracticeReducer.step(it, intent.steps, config) }
            PracticeIntent.SummarySaved -> saveSummary()
            PracticeIntent.SummaryDiscarded -> discardSummary()
            is PracticeIntent.DaySelected -> selectDay(intent.date)
            PracticeIntent.MonthBack -> ui.update { it.copy(month = it.month.minusMonths(1)) }
            PracticeIntent.MonthForward -> ui.update {
                if (it.month < YearMonth.from(today())) it.copy(month = it.month.plusMonths(1)) else it
            }
            PracticeIntent.EditTimeClicked -> openEditSheet()
            is PracticeIntent.EditTimeStepped -> updateEdit { PracticeReducer.step(it, intent.steps, config) }
            is PracticeIntent.EditTimeAdded -> updateEdit { PracticeReducer.add(it, intent.minutes) }
            PracticeIntent.EditTimeCleared -> updateEdit { it.copy(minutes = 0) }
            PracticeIntent.EditTimeSaved -> saveEdit()
            PracticeIntent.EditTimeCancelled -> ui.update { it.copy(sheet = null) }
            PracticeIntent.JourneyClicked -> effectChannel.trySend(PracticeEffect.OpenJourney)
            is PracticeIntent.SessionClicked -> effectChannel.trySend(PracticeEffect.OpenSession(intent.id))
            PracticeIntent.ProfileClicked -> openSheet(PracticeSheet.Profile(latestProfile.name, importingPhoto = false))
            is PracticeIntent.ProfileNameChanged ->
                updateProfile { it.copy(nameDraft = intent.text.take(Profile.MAX_NAME_LENGTH)) }
            is PracticeIntent.ProfilePhotoPicked -> importPhoto(intent.uri)
            PracticeIntent.ProfilePhotoRemoved -> replaceAvatar(null)
            PracticeIntent.ProfileClosed -> closeProfile()
            PracticeIntent.TrophiesClicked -> openSheet(PracticeSheet.Trophies)
            PracticeIntent.TrophiesClosed -> ui.update { if (it.sheet == PracticeSheet.Trophies) it.copy(sheet = null) else it }
            // The next trophy not seen, if any, becomes the gift by itself: the state follows the table.
            is PracticeIntent.GiftAccepted -> viewModelScope.launch { trophies.markShown(intent.hours) }
        }
    }

    private fun start() {
        viewModelScope.launch {
            if (latestRunning == null) runningStore.start(clock.millis())
            effectChannel.send(PracticeEffect.OpenLive)
        }
    }

    private fun stop() {
        val running = latestRunning ?: return
        val elapsed = running.elapsedMs(clock.millis()).coerceAtMost(config.maxPracticeMs)
        if (elapsed < config.minPracticeMs) {
            viewModelScope.launch {
                runningStore.clear()
                effectChannel.send(PracticeEffect.ShowTooShort)
            }
        } else {
            ui.update { it.copy(sheet = PracticeReducer.summarySheet(running.startedAtEpochMs, elapsed, config)) }
        }
    }

    // The stores are the truth; the state only mirrors them a moment later, and an intent may
    // arrive in between (a day tapped and "Изменить время" right after it).
    private var latestRunning: RunningPractice? = null
    private var latestTotals: Map<LocalDate, Long> = emptyMap()
    private var latestProfile: Profile = Profile.EMPTY

    init {
        viewModelScope.launch { profiles.profile.collect { latestProfile = it } }
        viewModelScope.launch { runningStore.running.collect { latestRunning = it } }
        viewModelScope.launch { repository.entries.collect { latestTotals = PracticeStats.dayTotals(it) } }
    }

    private fun saveSummary() {
        val sheet = ui.value.sheet as? PracticeSheet.Summary ?: return
        viewModelScope.launch {
            finisher.save(sheet.startedAtEpochMs, PracticeReducer.durationToSave(sheet))
            ui.update { it.copy(sheet = null) }
        }
    }

    private fun discardSummary() {
        viewModelScope.launch {
            finisher.discard()
            ui.update { it.copy(sheet = null) }
        }
    }

    private fun selectDay(date: LocalDate) {
        if (date > today()) return
        ui.update { it.copy(selectedDate = date) }
    }

    private fun openEditSheet() {
        ui.update { it.copy(sheet = PracticeReducer.editSheet(it.selectedDate, latestTotals[it.selectedDate] ?: 0L, config)) }
    }

    private fun saveEdit() {
        val sheet = ui.value.sheet as? PracticeSheet.EditTime ?: return
        viewModelScope.launch {
            repository.replaceDay(
                date = sheet.date,
                durationMs = sheet.minutes * MS_PER_MINUTE,
                startedAtEpochMs = PracticeStats.manualStartOf(sheet.date, clock.zone),
            )
            ui.update { it.copy(sheet = null) }
        }
    }

    /** One sheet at a time: a tap that lands while another sheet is open is dropped. */
    private fun openSheet(sheet: PracticeSheet) {
        ui.update { if (it.sheet == null) it.copy(sheet = sheet) else it }
    }

    private fun importPhoto(uri: String) {
        if ((ui.value.sheet as? PracticeSheet.Profile)?.importingPhoto != false) return
        updateProfile { it.copy(importingPhoto = true) }
        viewModelScope.launch {
            val imported = avatarFiles.import(uri)
            if (imported == null) effectChannel.send(PracticeEffect.ShowPhotoFailed) else replaceAvatar(imported).join()
            updateProfile { it.copy(importingPhoto = false) }
        }
    }

    /** The profile points at the new photo before the old file goes: no moment shows a missing one. */
    private fun replaceAvatar(fileName: String?) = viewModelScope.launch {
        val old = latestProfile.avatarFile
        profiles.setAvatarFile(fileName)
        if (old != null && old != fileName) avatarFiles.delete(old)
    }

    private fun closeProfile() {
        val sheet = ui.value.sheet as? PracticeSheet.Profile ?: return
        ui.update { it.copy(sheet = null) }
        viewModelScope.launch { profiles.setName(sheet.nameDraft) }
    }

    private inline fun updateProfile(transform: (PracticeSheet.Profile) -> PracticeSheet.Profile) {
        ui.update { state -> (state.sheet as? PracticeSheet.Profile)?.let { state.copy(sheet = transform(it)) } ?: state }
    }

    private inline fun updateSummary(transform: (PracticeSheet.Summary) -> PracticeSheet.Summary) {
        ui.update { state -> (state.sheet as? PracticeSheet.Summary)?.let { state.copy(sheet = transform(it)) } ?: state }
    }

    private inline fun updateEdit(transform: (PracticeSheet.EditTime) -> PracticeSheet.EditTime) {
        ui.update { state -> (state.sheet as? PracticeSheet.EditTime)?.let { state.copy(sheet = transform(it)) } ?: state }
    }

    private fun today(): LocalDate = LocalDate.now(clock)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
