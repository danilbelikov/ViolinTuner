package com.violinjourney.app.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.violinjourney.app.core.data.profile.AvatarFiles
import com.violinjourney.app.core.domain.backing.BackingRepository
import com.violinjourney.app.core.domain.backing.takesUnderBacking
import com.violinjourney.app.core.domain.journey.JourneyConfig
import com.violinjourney.app.core.domain.journey.JourneyRepository
import com.violinjourney.app.core.domain.practice.BlockRules
import com.violinjourney.app.core.domain.practice.BlockStore
import com.violinjourney.app.core.domain.practice.ForgottenPractice
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
import com.violinjourney.app.core.domain.practice.SavedPractice
import com.violinjourney.app.core.domain.practice.elapsedTicker
import com.violinjourney.app.core.domain.practice.practiceDateOf
import com.violinjourney.app.core.domain.progress.Profile
import com.violinjourney.app.core.domain.progress.ProfileRepository
import com.violinjourney.app.core.domain.progress.ProgressConfig
import com.violinjourney.app.core.domain.progress.Trophy
import com.violinjourney.app.core.domain.progress.TrophyRepository
import com.violinjourney.app.core.domain.repertoire.Piece
import com.violinjourney.app.core.domain.repertoire.RepertoireRepository
import com.violinjourney.app.core.domain.session.SessionRepository
import com.violinjourney.app.core.domain.session.SessionSummary
import com.violinjourney.app.core.domain.venue.Venues
import com.violinjourney.app.core.io.filePath
import com.violinjourney.app.core.text.takeCodePoints
import com.violinjourney.app.core.time.WallClock
import com.violinjourney.app.core.time.dates
import com.violinjourney.app.core.time.today
import com.violinjourney.app.feature.journey.JourneyMotion
import com.violinjourney.app.feature.journey.JourneyReducer
import com.violinjourney.app.feature.journey.JourneyWindow
import com.violinjourney.app.core.analytics.Analytics
import com.violinjourney.app.core.analytics.LevelUp
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
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

open class PracticeViewModel(
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
    private val journey: JourneyRepository,
    venues: Venues,
    private val blocks: BlockStore,
    private val journeyConfig: JourneyConfig,
    private val finishAsk: FinishPracticeAsk,
    private val analytics: Analytics,
    backings: BackingRepository,
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

    /**
     * What only the screen decides: the month shown and the open sheet. A null month follows today: the screen stays open
     * overnight, and in the morning it is on the new month. Only one moved to by the arrows stays where it was put.
     *
     * One sheet at a time (spec 3.36.2): a sheet opened from another one — «Трофеи» and «Имя и фото» from «Мой путь», «Время за
     * день» from the sheet of the day — takes its place, and [parent] remembers what it stood on, to come back to when it is
     * closed. The day selected in the calendar is the day of the open sheet, nothing more ([selectedDate]). [away] — a record
     * opened from the sheet of the day is on the screen: the sheet steps aside and rises again when the screen is back.
     */
    private data class Ui(
        val month: YearMonth? = null,
        val sheet: PracticeSheet? = null,
        val parent: PracticeSheet? = null,
        val away: Boolean = false,
    ) {
        /** No sheet at all: neither this one nor the one it stood on. */
        fun closed() = copy(sheet = null, parent = null, away = false)

        /** [next] in place of every sheet: the recap, the summary. */
        fun replaced(next: PracticeSheet) = copy(sheet = next, parent = null, away = false)

        /** Back to the sheet this one stood on, or to none. */
        fun back() = copy(sheet = parent, parent = null)

        /** The day whose sheet is open — its own, or «Время за день» over it: its cell keeps the ring «выбран» (spec 3.36.2). */
        fun selectedDate(): LocalDate? = when (sheet) {
            is PracticeSheet.Day -> sheet.date
            is PracticeSheet.EditTime -> sheet.date.takeIf { parent is PracticeSheet.Day }
            else -> null
        }

        /**
         * A day to come has no sheet (spec 3.36.2), and a tap does not open one ([selectDay]); but the date can go back under an open
         * one — a zone crossed westward over midnight, a clock set back. Then the sheet closes, with «Время за день» over it, as a hide
         * would close it: the screen never shows less than the model holds, and nothing waits for a sheet no one can see.
         */
        fun withoutDayAfter(today: LocalDate): Ui = if (selectedDate()?.let { it > today } == true) closed() else this
    }

    private val ui = MutableStateFlow(Ui())

    // The recap's bookkeeping is declared before [state] and [init], which read it: on the main thread a collector
    // launched in init runs at once, and a field declared after init would be set back by its own initializer.

    /** The saved practice the recap was last opened for, or history (read first): a practice is recapped once. */
    private var recapped: SavedPractice? = null

    /** The saved practice whose recap has been dealt with — opened, or history; the gift waits until it is the last saved. */
    private val recapDone = MutableStateFlow(finisher.lastSaved.value)

    /**
     * A practice is being saved, here or by the prompt over the screen, and its recap has not opened yet: the gift of a
     * trophy it crossed waits for it (spec 3.31: «Занятие сохранено», then «Подарок»).
     */
    private val recapPending: Flow<Boolean> =
        combine(finisher.saving, finisher.lastSaved, recapDone) { saving, last, done -> saving || last != done }.distinctUntilChanged()

    /** What the screen decides, the day it is — anew at midnight — and whether a recap is on its way. */
    private data class Screen(val ui: Ui, val today: LocalDate, val recapPending: Boolean)

    /**
     * The sheet of a day that is later than today now ([Ui.withoutDayAfter]) is closed in [ui] itself, not only left out of the
     * state: the model and the screen agree on what is open, so the next tap, the gift and the pill are not held by it.
     */
    private val screen: Flow<Screen> = combine(ui, clock.dates(), recapPending) { shown, today, pending ->
        val kept = shown.withoutDayAfter(today)
        if (kept != shown) ui.update { it.withoutDayAfter(today) }
        Screen(kept, today, pending)
    }

    /**
     * The clock of the running practice (spec 3.12) and the line of its block (spec 3.28), whose minutes left follow
     * the same tick: a flow of its own, like [journeyWindow] — it changes every second, and the calendar, the header
     * and the day's records have nothing to do with it. The time does not wait for the stored blocks and the names of
     * the pieces: it is there as soon as the running practice is read, the block line a moment later.
     */
    val timer: StateFlow<PracticeTimer?> = combine(
        runningStore.elapsedTicker(clock),
        runningStore.running,
        blocks.blocks.onStart { emit(null) },
        repertoire.pieces.map { pieces -> pieces.associate { it.id to it.title } }.onStart { emit(emptyMap()) },
    ) { elapsedMs, running, blocks, titles ->
        elapsedMs?.let { PracticeTimer(it, PracticeReducer.runningBlockOf(running, blocks, titles, clock.millis())) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /**
     * The day the running practice began on, null while none runs: all the rest of the screen needs to know of it — whether
     * one runs, and whether it is today's (spec 3.36.2: only then it hatches today's bar).
     */
    private val runningSince: Flow<LocalDate?> =
        runningStore.running.map { running -> running?.let { practiceDateOf(it.startedAtEpochMs, clock.zone) } }.distinctUntilChanged()

    /** Trophies and the profile travel together: `combine` takes five flows at most. */
    private val progress: Flow<Pair<List<Trophy>, Profile>> = combine(trophies.trophies, profiles.profile, ::Pair)

    /** The day's records and what they need to be named and marked: the pieces, and the takes made under a backing (spec 3.32). */
    private val records: Flow<Records> = combine(sessions.sessions, repertoire.pieces, backings.takesUnderBacking, ::Records)

    private data class Records(val sessions: List<SessionSummary>, val pieces: List<Piece>, val underBacking: Set<Long>)

    val state: StateFlow<PracticeState> =
        combine(repository.entries, records, runningSince, screen, progress) { entries, (sessions, pieces, underBacking), runningSince, screen, (trophies, profile) ->
            val (ui, today) = screen
            PracticeReducer.stateOf(
                entries = entries,
                sessions = sessions,
                runningSince = runningSince,
                month = ui.month ?: today.yearMonth,
                selectedDate = ui.selectedDate(),
                sheet = ui.sheet,
                today = today,
                zone = clock.zone,
                config = config,
                pieces = pieces,
                trophies = trophies,
                profile = profile,
                // A name without its file (cleared storage) is no photo, not a broken one.
                avatarPath = profile.avatarFile?.let(avatarFiles::existing)?.filePath,
                progressConfig = progressConfig,
                underBackingIds = underBacking,
                recapPending = screen.recapPending,
                sheetsAway = ui.away,
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
            PracticeIntent.BackToLiveClicked -> effectChannel.trySend(PracticeEffect.OpenLive)
            PracticeIntent.PathClicked -> openSheet(PracticeSheet.Path)
            // hidden is only hidden, and only «Мой путь» itself: a late swipe must not close what took its place
            PracticeIntent.PathHidden -> ui.update { if (it.sheet == PracticeSheet.Path) it.closed() else it }
            PracticeIntent.PathSettingsClicked -> closePathForSettings()
            is PracticeIntent.SummaryStepped -> updateSummary { PracticeReducer.step(it, intent.steps, config) }
            PracticeIntent.SummarySaved -> saveSummary()
            PracticeIntent.SummaryDiscarded -> discardSummary()
            // hidden is only hidden: the practice runs on, saved or thrown away by a button of the sheet alone
            PracticeIntent.SummaryHidden -> ui.update { if (it.sheet is PracticeSheet.Summary) it.closed() else it }
            is PracticeIntent.DaySelected -> selectDay(intent.date)
            // hidden is only hidden, and only the sheet of the day itself: a late swipe must not close what took its place
            PracticeIntent.DayHidden -> ui.update { if (it.sheet is PracticeSheet.Day) it.closed() else it }
            PracticeIntent.Resumed -> ui.update { if (it.away) it.copy(away = false) else it }
            PracticeIntent.MonthBack -> ui.update { it.copy(month = (it.month ?: today().yearMonth).minus(1, DateTimeUnit.MONTH)) }
            PracticeIntent.MonthForward -> ui.update {
                val current = today().yearMonth
                val shown = it.month ?: current
                // back on the current month the calendar follows today again
                if (shown < current) it.copy(month = shown.plus(1, DateTimeUnit.MONTH).takeIf { next -> next != current }) else it
            }
            PracticeIntent.EditTimeClicked -> openEditSheet()
            is PracticeIntent.EditTimeStepped -> updateEdit { PracticeReducer.step(it, intent.steps, config) }
            is PracticeIntent.EditTimeAdded -> updateEdit { PracticeReducer.add(it, intent.minutes) }
            PracticeIntent.EditTimeCleared -> updateEdit { it.copy(minutes = 0) }
            PracticeIntent.EditTimeSaved -> saveEdit()
            PracticeIntent.EditTimeCancelled -> ui.update { if (it.sheet is PracticeSheet.EditTime) it.back() else it }
            PracticeIntent.JourneyClicked -> effectChannel.trySend(PracticeEffect.OpenJourney)
            PracticeIntent.HomeClicked -> effectChannel.trySend(PracticeEffect.OpenHome)
            is PracticeIntent.SessionClicked -> openSession(intent.id)
            PracticeIntent.ProfileClicked -> openOver<PracticeSheet.Path> { PracticeSheet.Profile(latestProfile.name, importingPhoto = false) }
            is PracticeIntent.ProfileNameChanged ->
                updateProfile { it.copy(nameDraft = intent.text.takeCodePoints(Profile.MAX_NAME_LENGTH)) }
            is PracticeIntent.ProfilePhotoPicked -> importPhoto(intent.uri)
            PracticeIntent.ProfilePhotoRemoved -> replaceAvatar(null)
            PracticeIntent.ProfileClosed -> closeProfile()
            PracticeIntent.TrophiesClicked -> openOver<PracticeSheet.Path> { PracticeSheet.Trophies }
            PracticeIntent.TrophiesClosed -> ui.update { if (it.sheet == PracticeSheet.Trophies) it.back() else it }
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
            // begins only when none runs, the look and the start in one write: a practice begun elsewhere a moment ago keeps its start
            runningStore.startIfIdle(clock.millis())
            effectChannel.send(PracticeEffect.OpenLive)
        }
    }

    /**
     * «Закончить занятие» on this screen, which has heard from the stores already: the sheet is built at once from its
     * mirrors. A practice left running past the limit is summed up to its last sound, not to twelve hours (spec 3.12).
     */
    private fun stop(running: RunningPractice? = latestRunning) {
        running ?: return
        if (answering?.isActive == true) return // the practice is being ended already
        val length = ForgottenPractice.lengthAt(running, clock.millis(), config)
        if (length < config.minPracticeMs) {
            dropTooShort(running)
        } else {
            val blocks = BlockRules.ofPractice(running, latestBlocks)
            ui.update { it.replaced(PracticeReducer.summarySheet(running.startedAtEpochMs, length, config, blocks, latestTitles)) }
        }
    }

    /**
     * «Закончить занятие» asked for from Live (spec 3.12): as [stop], but the sheet — «Что играли» with it — is read
     * from the stores, for a view model made just now for this ask has not heard from them yet ([summarySheetOf]).
     */
    private suspend fun finishFromStores(running: RunningPractice) {
        if (answering?.isActive == true) return
        val length = ForgottenPractice.lengthAt(running, clock.millis(), config)
        if (length < config.minPracticeMs) {
            dropTooShort(running)
        } else {
            val sheet = summarySheetOf(running, length, config, blocks, repertoire)
            ui.update { it.replaced(sheet) }
        }
    }

    /** Too short to keep (spec 3.12): the practice goes, its notes and blocks with it, and the toast says so. */
    private fun dropTooShort(running: RunningPractice) = answer {
        finisher.discard(running.startedAtEpochMs)
        effectChannel.send(PracticeEffect.ShowTooShort)
    }

    // The stores are the truth; the state only mirrors them a moment later, and an intent may
    // arrive in between (a day tapped and «Изменить» of its sheet right after it).
    private var latestRunning: RunningPractice? = null
    private var latestTotals: Map<LocalDate, Long> = emptyMap()
    private var latestProfile: Profile = Profile.EMPTY
    private var latestBlocks: PracticeBlocks? = null
    private var latestTitles: Map<Long, String> = emptyMap()

    /**
     * «Сохранить», «Не сохранять» or a stop too short to keep, still being written: a second tap that lands before the
     * sheet has changed is dropped — else a double tap would store the practice twice, or throw away the recap the first
     * tap has just opened. The finisher takes answers one at a time as well; this only keeps the sheet from answering twice.
     */
    private var answering: Job? = null

    private fun answer(block: suspend () -> Unit) {
        if (answering?.isActive == true) return
        answering = viewModelScope.launch { block() }
    }

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
                if (asked && finishAsk.take()) runningStore.running.first()?.let { finishFromStores(it) }
            }
        }
        // Every practice saved while this screen lives is recapped here — by its own «Сохранить» and by the forgotten-practice
        // prompt over it alike (spec 3.31). What was saved before it opened is history, not news: read here, in the
        // coroutine, so that it is read before the first value is collected whatever the order of the fields.
        viewModelScope.launch {
            val history = finisher.lastSaved.value
            recapped = history
            recapDone.value = history
            finisher.lastSaved.collect { saved -> if (saved != null) openRecap(saved) }
        }
    }

    private var pillJob: Job? = null

    private fun saveSummary() {
        val sheet = ui.value.sheet as? PracticeSheet.Summary ?: return
        answer {
            val earning = finisher.save(sheet.startedAtEpochMs, PracticeReducer.durationToSave(sheet))
            // «Занятие сохранено» takes the summary's place (spec 3.31); without an earning the sheet just goes
            if (earning == null) {
                ui.update { if (it.sheet is PracticeSheet.Summary) it.closed() else it }
            } else {
                finisher.lastSaved.value?.let { openRecap(it) }
            }
        }
    }

    /**
     * Builds the recap from what is stored after the save — the entries and the journey already hold the
     * practice (spec 5.24) — and opens it in place of the summary. Another sheet open (nothing can be
     * saved from under it, but a prompt can): no recap, the pill alone. Once, whoever calls first: the
     * screen's own save and the collector of [PracticeFinisher.lastSaved] both come here.
     */
    private suspend fun openRecap(saved: SavedPractice) {
        if (saved == recapped) return
        recapped = saved
        try {
            val earning = saved.earning
            val recap = RecapRules.of(
                earning, repository.entries.first(), journey.progress.first(), today(), journeyConfig, progressConfig, practice = saved.entry,
            )
            // The level is not stored anywhere — it is worked out from the time, and this is the one
            // place that knows it has just grown (spec 3.13, 5.24).
            if (recap.levelUp) analytics.track(LevelUp(recap.levelAfter.level))
            var opened = false
            ui.update {
                opened = it.sheet == null || it.sheet is PracticeSheet.Summary
                if (opened) it.replaced(PracticeSheet.Recap(recap)) else it
            }
            if (!opened) showPill(earning.takts)
        } finally {
            // the recap is open (or the pill is on its way): the gift may come after it
            recapDone.value = saved
        }
    }

    private fun closeRecap() {
        val sheet = ui.value.sheet as? PracticeSheet.Recap ?: return
        ui.update { if (it.sheet is PracticeSheet.Recap) it.closed() else it }
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

    /** «Не сохранять» answers the summary alone: a late tap must not close the recap or another sheet. */
    private fun discardSummary() {
        val sheet = ui.value.sheet as? PracticeSheet.Summary ?: return
        answer {
            // the practice of this sheet, not whatever runs now: it may have been saved from the prompt and another begun
            finisher.discard(sheet.startedAtEpochMs)
            ui.update { if (it.sheet is PracticeSheet.Summary) it.closed() else it }
        }
    }

    /** A day up to today opens its sheet (spec 3.36.2); a day to come has nothing to open until the events of R9. */
    private fun selectDay(date: LocalDate) {
        if (date > today()) return
        openSheet(PracticeSheet.Day(date))
    }

    /** «Изменить» / «Добавить» of the sheet of the day: «Время за день» of that day in its place, and back to it when closed. */
    private fun openEditSheet() {
        openOver<PracticeSheet.Day> { day -> PracticeReducer.editSheet(day.date, latestTotals[day.date] ?: 0L, config) }
    }

    /**
     * A record of the sheet of the day: its screen opens, and the sheet steps aside while it is there — it comes back with the
     * screen ([PracticeIntent.Resumed]).
     */
    private fun openSession(id: Long) {
        ui.update { if (it.sheet is PracticeSheet.Day) it.copy(away = true) else it }
        effectChannel.trySend(PracticeEffect.OpenSession(id))
    }

    private fun saveEdit() {
        val sheet = ui.value.sheet as? PracticeSheet.EditTime ?: return
        // the number as it opened: the day keeps its exact time rather than one manual entry of its rounding (spec 5.6)
        if (sheet.minutes == sheet.initialMinutes) {
            ui.update { if (it.sheet is PracticeSheet.EditTime) it.back() else it }
            return
        }
        viewModelScope.launch {
            repository.replaceDay(
                date = sheet.date,
                durationMs = sheet.minutes * MS_PER_MINUTE,
                startedAtEpochMs = PracticeStats.manualStartOf(sheet.date, clock.zone),
            )
            ui.update { if (it.sheet is PracticeSheet.EditTime) it.back() else it }
        }
    }

    /** One sheet at a time: a tap that lands while another sheet is open is dropped. */
    private fun openSheet(sheet: PracticeSheet) {
        ui.update { if (it.sheet == null) it.copy(sheet = sheet, parent = null) else it }
    }

    /**
     * A sheet that opens from another one, [T] — «Трофеи» and «Имя и фото» from «Мой путь», «Время за день» from the sheet of the day: it
     * takes that one's place and remembers it (spec 3.36.2). Over anything else, or over nothing, the tap is dropped.
     */
    private inline fun <reified T : PracticeSheet> openOver(make: (T) -> PracticeSheet) {
        ui.update {
            val under = it.sheet
            if (under is T) it.copy(sheet = make(under), parent = under) else it
        }
    }

    /** «Настройки» of «Мой путь»: the sheet closes and the settings open (spec 3.36.2); a second tap finds no sheet and is dropped. */
    private fun closePathForSettings() {
        var closed = false
        ui.update {
            closed = it.sheet == PracticeSheet.Path
            if (closed) it.closed() else it
        }
        if (closed) effectChannel.trySend(PracticeEffect.OpenSettings)
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

    /** The name of «Имя и фото» is stored however the sheet is closed (spec 3.13, 3.36.3), and «Мой путь» comes back. */
    private fun closeProfile() {
        val sheet = ui.value.sheet as? PracticeSheet.Profile ?: return
        ui.update { if (it.sheet is PracticeSheet.Profile) it.back() else it }
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
