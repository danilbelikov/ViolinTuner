package com.violinjourney.app.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.violinjourney.app.core.data.profile.AvatarFiles
import com.violinjourney.app.core.domain.journey.JourneyConfig
import com.violinjourney.app.core.domain.journey.JourneyProgress
import com.violinjourney.app.core.domain.journey.JourneyRepository
import com.violinjourney.app.core.domain.journey.NoJourney
import com.violinjourney.app.core.domain.journey.TaktEarning
import com.violinjourney.app.core.domain.practice.BlockStore
import com.violinjourney.app.core.domain.practice.NoBlocks
import com.violinjourney.app.core.domain.practice.PracticeBlocks
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_MINUTE
import com.violinjourney.app.core.domain.practice.FinishPracticeAsk
import com.violinjourney.app.core.domain.practice.PracticeFinisher
import com.violinjourney.app.core.domain.practice.PracticeRepository
import com.violinjourney.app.core.domain.practice.PracticeStats
import com.violinjourney.app.core.domain.practice.RecapRules
import com.violinjourney.app.core.domain.practice.RunningPractice
import com.violinjourney.app.core.domain.practice.RunningPracticeStore
import com.violinjourney.app.core.domain.practice.elapsedTicker
import com.violinjourney.app.core.domain.progress.Profile
import com.violinjourney.app.core.domain.progress.ProfileRepository
import com.violinjourney.app.core.domain.progress.ProgressConfig
import com.violinjourney.app.core.domain.progress.Trophy
import com.violinjourney.app.core.domain.progress.TrophyRepository
import com.violinjourney.app.core.domain.repertoire.RepertoireRepository
import com.violinjourney.app.core.domain.session.SessionRepository
import com.violinjourney.app.core.domain.venue.FollowTheRoad
import com.violinjourney.app.core.domain.venue.Venues
import com.violinjourney.app.core.time.WallClock
import com.violinjourney.app.core.time.today
import com.violinjourney.app.feature.journey.JourneyMotion
import com.violinjourney.app.feature.journey.JourneyReducer
import com.violinjourney.app.feature.journey.JourneyWindow
import dagger.hilt.android.lifecycle.HiltViewModel
import com.violinjourney.app.core.analytics.Analytics
import com.violinjourney.app.core.analytics.LevelUp
import com.violinjourney.app.core.analytics.NoOpAnalytics
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.yearMonth

@HiltViewModel
class PracticeViewModel @Inject constructor(
    private val repository: PracticeRepository,
    private val runningStore: RunningPracticeStore,
    private val finisher: PracticeFinisher,
    sessions: SessionRepository,
    private val config: PracticeConfig,
    private val repertoire: RepertoireRepository,
    private val clock: WallClock,
    private val trophies: TrophyRepository,
    private val profiles: ProfileRepository,
    private val avatarFiles: AvatarFiles,
    private val progressConfig: ProgressConfig,
    private val journey: JourneyRepository = NoJourney,
    venues: Venues = Venues(FollowTheRoad, journey),
    private val blocks: BlockStore = NoBlocks,
    private val journeyConfig: JourneyConfig = JourneyConfig(),
    private val finishAsk: FinishPracticeAsk = FinishPracticeAsk(),
    private val analytics: Analytics = NoOpAnalytics(),
) : ViewModel() {

    /** Takts of the practice saved a moment ago, as the pill on the card; null the rest of the time. */
    private val earnedPill = MutableStateFlow<Int?>(null)

    /**
     * The window into the journey (spec 3.23): its own flow, like the take on the piece screen — the
     * card changes when takts do, the rest of the screen has nothing to do with it. The pill «+340»
     * comes after «Занятие сохранено» is closed (spec 3.31): it shows where the takts went.
     */
    val journeyWindow: StateFlow<JourneyWindow?> = combine(journey.progress, venues.current, earnedPill) { progress, here, pill ->
        JourneyReducer.windowOf(progress, justEarned = pill, here = here)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** What only the screen decides: the month shown, the day picked and the open sheet. */
    private data class Ui(val month: YearMonth, val selectedDate: LocalDate, val sheet: PracticeSheet?)

    private val ui = MutableStateFlow(Ui(today().yearMonth, today(), sheet = null))

    private val runningMs: Flow<Long?> = runningStore.elapsedTicker(clock)

    /** The practice's time and its blocks travel together: a block's minutes left follow the same tick (spec 3.28). */
    private val running = combine(runningMs, runningStore.running, blocks.blocks, ::Triple)

    /** Trophies and the profile travel together: `combine` takes five flows at most. */
    private val progress: Flow<Pair<List<Trophy>, Profile>> = combine(trophies.trophies, profiles.profile, ::Pair)

    val state: StateFlow<PracticeState> =
        combine(repository.entries, combine(sessions.sessions, repertoire.pieces, ::Pair), running, ui, progress) { entries, (sessions, pieces), (runningMs, running, blocks), ui, (trophies, profile) ->
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
                runningBlock = PracticeReducer.runningBlockOf(running, blocks, pieces.associate { it.id to it.title }, clock.millis()),
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
            // hidden is only hidden: the practice runs on, saved or thrown away by a button of the sheet alone
            PracticeIntent.SummaryHidden -> ui.update { if (it.sheet is PracticeSheet.Summary) it.copy(sheet = null) else it }
            is PracticeIntent.DaySelected -> selectDay(intent.date)
            PracticeIntent.MonthBack -> ui.update { it.copy(month = it.month.minus(1, DateTimeUnit.MONTH)) }
            PracticeIntent.MonthForward -> ui.update {
                if (it.month < today().yearMonth) it.copy(month = it.month.plus(1, DateTimeUnit.MONTH)) else it
            }
            PracticeIntent.EditTimeClicked -> openEditSheet()
            is PracticeIntent.EditTimeStepped -> updateEdit { PracticeReducer.step(it, intent.steps, config) }
            is PracticeIntent.EditTimeAdded -> updateEdit { PracticeReducer.add(it, intent.minutes) }
            PracticeIntent.EditTimeCleared -> updateEdit { it.copy(minutes = 0) }
            PracticeIntent.EditTimeSaved -> saveEdit()
            PracticeIntent.EditTimeCancelled -> ui.update { it.copy(sheet = null) }
            PracticeIntent.JourneyClicked -> effectChannel.trySend(PracticeEffect.OpenJourney)
            PracticeIntent.HomeClicked -> effectChannel.trySend(PracticeEffect.OpenHome)
            is PracticeIntent.SessionClicked -> effectChannel.trySend(PracticeEffect.OpenSession(intent.id))
            PracticeIntent.ProfileClicked -> openSheet(PracticeSheet.Profile(latestProfile.name, importingPhoto = false))
            is PracticeIntent.ProfileNameChanged ->
                updateProfile { it.copy(nameDraft = intent.text.take(Profile.MAX_NAME_LENGTH)) }
            is PracticeIntent.ProfilePhotoPicked -> importPhoto(intent.uri)
            PracticeIntent.ProfilePhotoRemoved -> replaceAvatar(null)
            PracticeIntent.ProfileClosed -> closeProfile()
            PracticeIntent.ProfileSettingsClicked -> {
                closeProfile()
                effectChannel.trySend(PracticeEffect.OpenSettings)
            }
            PracticeIntent.TrophiesClicked -> openSheet(PracticeSheet.Trophies)
            PracticeIntent.TrophiesClosed -> ui.update { if (it.sheet == PracticeSheet.Trophies) it.copy(sheet = null) else it }
            // The next trophy not seen, if any, becomes the gift by itself: the state follows the table.
            is PracticeIntent.GiftAccepted -> viewModelScope.launch { trophies.markShown(intent.hours) }
            PracticeIntent.RecapClosed -> closeRecap()
            PracticeIntent.RecapTravelClicked -> {
                closeRecap()
                effectChannel.trySend(PracticeEffect.OpenHome)
            }
        }
    }

    private fun start() {
        viewModelScope.launch {
            if (latestRunning == null) runningStore.start(clock.millis())
            effectChannel.send(PracticeEffect.OpenLive)
        }
    }

    private fun stop(running: RunningPractice? = latestRunning) {
        running ?: return
        val elapsed = running.elapsedMs(clock.millis()).coerceAtMost(config.maxPracticeMs)
        if (elapsed < config.minPracticeMs) {
            viewModelScope.launch {
                // too short to keep: its notes and blocks go with it
                finisher.discard()
                effectChannel.send(PracticeEffect.ShowTooShort)
            }
        } else {
            ui.update { it.copy(sheet = PracticeReducer.summarySheet(running.startedAtEpochMs, elapsed, config, latestBlocks, latestTitles)) }
        }
    }

    // The stores are the truth; the state only mirrors them a moment later, and an intent may
    // arrive in between (a day tapped and "Изменить время" right after it).
    private var latestRunning: RunningPractice? = null
    private var latestTotals: Map<LocalDate, Long> = emptyMap()
    private var latestProfile: Profile = Profile.EMPTY
    private var latestBlocks: PracticeBlocks? = null
    private var latestTitles: Map<Long, String> = emptyMap()

    init {
        viewModelScope.launch { profiles.profile.collect { latestProfile = it } }
        viewModelScope.launch { blocks.blocks.collect { latestBlocks = it } }
        viewModelScope.launch { repertoire.pieces.collect { pieces -> latestTitles = pieces.associate { it.id to it.title } } }
        viewModelScope.launch { runningStore.running.collect { latestRunning = it } }
        viewModelScope.launch { repository.entries.collect { latestTotals = PracticeStats.dayTotals(it) } }
        // «Закончить занятие» asked for from Live (spec 3.12, handoff nav_bar 35): taken once and forgotten. The store is read
        // rather than [latestRunning]: a view model made just now for this ask has not heard from it yet.
        viewModelScope.launch {
            finishAsk.asked.collect { asked ->
                if (asked && finishAsk.take()) runningStore.running.first()?.let(::stop)
            }
        }
        // A practice saved elsewhere — the forgotten-practice prompt over this screen — is recapped here all the
        // same. The first value read is history, not news.
        viewModelScope.launch {
            var first = true
            journey.progress.collect { progress ->
                val last = progress.lastEarning
                if (first) {
                    recapped = last
                    first = false
                } else if (last != null && last != recapped && last.takts > 0) {
                    openRecap(last, progress)
                }
            }
        }
    }

    /** The earning the recap was last opened for (or history, read first): a practice is recapped once, whoever notices it first. */
    private var recapped: TaktEarning? = null
    private var pillJob: Job? = null

    private fun saveSummary() {
        val sheet = ui.value.sheet as? PracticeSheet.Summary ?: return
        viewModelScope.launch {
            val earning = finisher.save(sheet.startedAtEpochMs, PracticeReducer.durationToSave(sheet))
            // «Занятие сохранено» takes the summary's place (spec 3.31); without an earning the sheet just goes
            if (earning == null) ui.update { if (it.sheet is PracticeSheet.Summary) it.copy(sheet = null) else it } else openRecap(earning)
        }
    }

    /**
     * Builds the recap from what is stored after the save — the entries and the journey already hold the
     * practice (spec 5.24) — and opens it in place of the summary. Another sheet open (nothing can be
     * saved from under it, but a prompt can): no recap, the pill alone.
     */
    private suspend fun openRecap(earning: TaktEarning, progress: JourneyProgress? = null) {
        if (earning == recapped) return
        recapped = earning
        val recap = RecapRules.of(earning, repository.entries.first(), progress ?: journey.progress.first(), today(), journeyConfig, progressConfig)
        // The level is not stored anywhere — it is worked out from the time, and this is the one
        // place that knows it has just grown (spec 3.13, 5.24).
        if (recap.levelUp) analytics.track(LevelUp(recap.levelAfter.level))
        var opened = false
        ui.update {
            opened = it.sheet == null || it.sheet is PracticeSheet.Summary
            if (opened) it.copy(sheet = PracticeSheet.Recap(recap)) else it
        }
        if (!opened) showPill(earning.takts)
    }

    private fun closeRecap() {
        val sheet = ui.value.sheet as? PracticeSheet.Recap ?: return
        ui.update { if (it.sheet is PracticeSheet.Recap) it.copy(sheet = null) else it }
        showPill(sheet.recap.takts)
    }

    /** The pill waits for the gift of a trophy, if the practice brought one (spec 3.31: recap, gift, then the pill). */
    private fun showPill(takts: Int) {
        pillJob?.cancel()
        pillJob = viewModelScope.launch {
            state.first { it.sheet == null && it.gift == null }
            earnedPill.value = takts
            try {
                delay(JourneyMotion.EARNED_PILL_MS)
            } finally {
                earnedPill.value = null
            }
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

    private fun today(): LocalDate = clock.today()

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
