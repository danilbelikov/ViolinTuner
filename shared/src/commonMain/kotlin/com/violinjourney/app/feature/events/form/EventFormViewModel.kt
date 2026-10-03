package com.violinjourney.app.feature.events.form

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.events.CalendarEvent
import com.violinjourney.app.core.domain.events.EditScope
import com.violinjourney.app.core.domain.events.EventChange
import com.violinjourney.app.core.domain.events.EventKind
import com.violinjourney.app.core.domain.events.EventName
import com.violinjourney.app.core.domain.events.EventPlan
import com.violinjourney.app.core.domain.events.EventRepository
import com.violinjourney.app.core.domain.events.EventRules
import com.violinjourney.app.core.domain.events.EventSeries
import com.violinjourney.app.core.domain.events.EventStep
import com.violinjourney.app.core.domain.events.EventsConfig
import com.violinjourney.app.core.domain.events.KindNameProblem
import com.violinjourney.app.core.domain.events.KindRef
import com.violinjourney.app.core.domain.events.KindRules
import com.violinjourney.app.core.domain.events.KindSave
import com.violinjourney.app.core.domain.events.KindSign
import com.violinjourney.app.core.domain.events.Repeat
import com.violinjourney.app.core.domain.events.ScopeQuestion
import com.violinjourney.app.core.domain.events.SeriesEdits
import com.violinjourney.app.core.domain.events.draft
import com.violinjourney.app.core.time.WallClock
import com.violinjourney.app.core.time.dates
import com.violinjourney.app.core.time.today
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.feature.events.EventWords
import com.violinjourney.app.feature.events.EventsDimens
import com.violinjourney.app.feature.events.ResourceEventWords
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth

/**
 * The form of an event (spec 3.35, 3.36.9; plan 7.4): new — from the sheet of a day, with its date — or an edit, from the screen of the
 * event. Its draft and its sheet live in the saved state ([EventFormSaved]). «Сохранить»: a new event goes into the storage and its screen
 * opens; an edit of an event of a repeat asks «Урок повторяется» where the edit is one of the repeat (plan D6) and is carried out as the
 * plan of the answer, the records of what goes keeping the name they wore (D12); one that asks nothing is carried out at once. «Готово» of
 * the sheet «Вид» stores the kind at once — a kind is shared by all the events, «Не сохранять» of the form does not take it back (decision
 * 49). A press is heard once: a second tap of «Сохранить», of an answer or of «Готово» lands on a form that is already on its way.
 */
open class EventFormViewModel(
    private val savedState: SavedStateHandle,
    private val events: EventRepository,
    private val config: EventsConfig,
    private val clock: WallClock,
    private val words: EventWords = ResourceEventWords,
) : ViewModel() {
    /** Null — a new event. */
    private val eventId: Long? = savedState.get<Long>(ARG_EVENT_ID)?.takeIf { it != NEW_EVENT }
    private val dateArg: LocalDate? = savedState.get<String>(ARG_DATE)?.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
    private val kindArg: KindRef? = savedState.get<String>(ARG_KIND)?.let { key -> BuiltInKind.entries.firstOrNull { it.name == key } }?.let { KindRef.BuiltIn(it) }
    private val focusNotes: Boolean = savedState.get<Boolean>(ARG_FOCUS_NOTES) ?: false

    /** What the player had chosen before the system ended the process. */
    private val kept = EventFormSaved.read(savedState)

    /**
     * What only the form decides: its draft, its sheet and its dialog. [keptKind] — the sheet «Вид» hidden by a swipe: its draft lives on
     * until «Готово» or the sheet of another kind (plan D33), in the saved state too — a form the system ended and brought back is the same
     * form for the player.
     */
    private data class Local(
        val loading: Boolean,
        val draft: FormDraft,
        val sheet: FormSheet? = null,
        val dialog: EventFormDialog? = null,
        val keptKind: FormSheet.Kind? = null,
    )

    private val local = MutableStateFlow(
        Local(
            loading = true,
            draft = FormDraft(KindRef.BuiltIn(BuiltInKind.LESSON), clock.today(), null, null, Repeat.NONE, null, "", "", ""),
        ),
    )

    /** What is stored: the events, the kinds and the repeats. */
    private data class Stored(val events: List<CalendarEvent>, val kinds: List<EventKind>, val series: List<EventSeries>)

    private val stored: Flow<Stored> = combine(events.events, events.kinds, events.series, ::Stored)
    private var latest = Stored(emptyList(), emptyList(), emptyList())

    /** The draft the form opened with: «Не сохранять?» compares with it. */
    private var initial: FormDraft? = null

    /** An edit: the event as it was read, and its repeat. */
    private var original: CalendarEvent? = null
    private var originalSeries: EventSeries? = null

    /** «Сохранить» or an answer on its way: a second press finds the form leaving. */
    private var leaving = false

    /** «Готово» of «Вид» on its way. */
    private var kindSaving = false
    private var problemJob: Job? = null

    private val effectChannel = Channel<EventFormEffect>(Channel.BUFFERED)
    val effects: Flow<EventFormEffect> = effectChannel.receiveAsFlow()

    val state: StateFlow<EventFormState> = combine(local, stored, clock.dates()) { local, stored, today -> stateOf(local, stored, today) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), stateOf(local.value, latest, clock.today()))

    init {
        viewModelScope.launch { stored.collect { latest = it } }
        viewModelScope.launch { load() }
    }

    /**
     * The form opens (spec 3.36.9): a new event with the defaults of 5.28 (plan D28) on the day it came from — or today; an edit from the
     * event as stored — gone meanwhile, the form closes. The draft and the sheet of a process the system ended come back over it.
     */
    private suspend fun load() {
        val now = Stored(events.events.first(), events.kinds.first(), events.series.first())
        latest = now
        val start = if (eventId == null) {
            EventFormReducer.newDraft(dateArg ?: clock.today(), kindArg, now.events, now.series, now.kinds)
        } else {
            val event = events.event(eventId)
            if (event == null) {
                effectChannel.send(EventFormEffect.Close)
                return
            }
            original = event
            originalSeries = event.seriesId?.let { id -> now.series.firstOrNull { it.id == id } }
            EventFormReducer.draftOf(event, originalSeries)
        }
        initial = start
        val draft = kept?.draft ?: start
        // «Нужно имя» from the first frame, as a sheet opened anew says it; «Такой вид уже есть» — reckoned again in the language now
        val sheet = kept?.sheet?.let { if (it is FormSheet.Kind) it.copy(problem = emptyProblemOf(it.draft)) else it }
        val hidden = kept?.keptKind?.let { FormSheet.Kind(it, problem = emptyProblemOf(it)) }
        local.update { Local(loading = false, draft = draft, sheet = sheet, keptKind = hidden) }
        (sheet as? FormSheet.Kind)?.let { recheck(it.draft) }
        // the question of a repeat that was up is asked again from the draft that outlived the process
        if (kept?.scopeAsked == true) ask(draft)
    }

    fun onIntent(intent: EventFormIntent) {
        // nothing is pressed while the form is being read: there are no fields yet, only ✕
        if (local.value.loading && intent != EventFormIntent.CloseClicked) return
        when (intent) {
            is EventFormIntent.KindPicked -> pickKind(intent.ref)
            EventFormIntent.KindRowClicked -> openKind(EventFormReducer.kindDraftOf(KindRules.kindOf(local.value.draft.kind, latest.kinds, config)))
            EventFormIntent.OwnKindClicked -> if (KindRules.canAddOwn(latest.kinds, config)) openKind(EventFormReducer.newKindDraft(latest.kinds, config))
            EventFormIntent.DateRowClicked -> local.value.draft.let { openSheet(FormSheet.Date(it.date.yearMonth, it.date)) }
            EventFormIntent.TimeRowClicked -> local.value.draft.let { draft ->
                val now = clock.instant().toLocalDateTime(clock.zone).time
                openSheet(FormSheet.Time(EventFormReducer.wheelStart(draft, now, config), allDay = draft.startMinutes == null))
            }
            is EventFormIntent.DurationPicked -> edit { EventFormReducer.withDuration(it, intent.minutes) }
            EventFormIntent.DurationOtherClicked -> local.value.draft.let { draft ->
                if (draft.startMinutes != null) openSheet(FormSheet.Duration(EventFormReducer.durationSheetStart(draft, config)))
            }
            is EventFormIntent.RepeatSelected -> edit { it.copy(repeat = intent.repeat) }
            // the end of a repeat an event of an edit belongs to is not edited here (spec 3.36.9)
            EventFormIntent.UntilClicked -> local.value.draft.let { draft ->
                if (!inSeries() && draft.repeat != Repeat.NONE) openSheet(FormSheet.Until((draft.until ?: draft.date).yearMonth, draft.until))
            }
            is EventFormIntent.PlaceChanged -> edit { it.copy(place = EventFormReducer.capped(intent.text, config.maxPlaceLength)) }
            is EventFormIntent.TitleChanged -> edit { it.copy(title = EventFormReducer.capped(intent.text, config.maxTitleLength)) }
            is EventFormIntent.NotesChanged -> edit { it.copy(notes = EventFormReducer.capped(intent.text, config.maxNotesLength)) }
            is EventFormIntent.DateMonthStep -> updateSheet<FormSheet.Date> { sheet ->
                val next = EventFormReducer.stepMonth(sheet.month, intent.by)
                // forward no further than the limit of the form (spec 3.36.9); back without one
                if (intent.by > 0 && next > dateLimit()) sheet else sheet.copy(month = next)
            }
            is EventFormIntent.DatePicked -> updateSheet<FormSheet.Date> { sheet ->
                if (intent.date.yearMonth > dateLimit()) sheet else sheet.copy(picked = intent.date, month = intent.date.yearMonth)
            }
            is EventFormIntent.TimeHour -> updateSheet<FormSheet.Time> {
                it.copy(minutes = EventFormReducer.pickTime(intent.hour, EventFormReducer.minuteOf(it.minutes), config))
            }
            is EventFormIntent.TimeMinute -> updateSheet<FormSheet.Time> {
                it.copy(minutes = EventFormReducer.pickTime(EventFormReducer.hourOf(it.minutes), intent.minute, config))
            }
            EventFormIntent.AllDayToggled -> updateSheet<FormSheet.Time> { it.copy(allDay = !it.allDay) }
            is EventFormIntent.FrequentPicked -> updateSheet<FormSheet.Time> { it.copy(minutes = EventRules.snapStart(intent.minutes, config), allDay = false) }
            is EventFormIntent.DurationStepped -> updateSheet<FormSheet.Duration> { it.copy(minutes = EventRules.stepDuration(it.minutes, intent.steps, config)) }
            is EventFormIntent.UntilPicked -> updateSheet<FormSheet.Until> { sheet ->
                val date = intent.date
                // a repeat does not end before it begins: those days sleep (spec 3.36.9)
                when {
                    date == null -> sheet.copy(picked = null)
                    date < local.value.draft.date -> sheet
                    else -> sheet.copy(picked = date, month = date.yearMonth)
                }
            }
            is EventFormIntent.UntilMonthStep -> updateSheet<FormSheet.Until> { sheet ->
                val next = EventFormReducer.stepMonth(sheet.month, intent.by)
                // back no further than the month of the first event: every day before it sleeps
                if (intent.by < 0 && next < local.value.draft.date.yearMonth) sheet else sheet.copy(month = next)
            }
            is EventFormIntent.KindNameChanged -> updateKind { it.copy(name = EventFormReducer.capped(intent.text, config.maxKindNameLength)) }
            is EventFormIntent.KindColorPicked -> updateKind { if (intent.color in 0 until config.colorCount) it.copy(color = intent.color) else it }
            // a built-in kind keeps its sign (spec 3.36.9): only its colour changes
            is EventFormIntent.KindSignPicked -> updateKind { if (it.ref is KindRef.BuiltIn || intent.sign !in OWN_SIGNS) it else it.copy(sign = intent.sign) }
            EventFormIntent.KindDeleteClicked -> askDeleteKind()
            EventFormIntent.SheetDone -> done()
            EventFormIntent.SheetHidden -> hideSheet()
            is EventFormIntent.ScopeAnswered -> answer(intent.scope)
            EventFormIntent.SaveClicked -> save()
            EventFormIntent.CloseClicked -> close()
            EventFormIntent.DialogConfirmed -> confirm()
            EventFormIntent.DialogDismissed -> local.update { it.copy(dialog = null) }
        }
    }

    /**
     * A tile: chosen — or, chosen already, its sheet «Вид» (spec 3.36.9). A new event takes the time of the kind chosen while its time is
     * not the person's own ([EventFormReducer.withKind]): «Урок» after a concert is at the time of the last lesson.
     */
    private fun pickKind(ref: KindRef) {
        val draft = local.value.draft
        if (KindRules.resolve(draft.kind, latest.kinds) == ref) {
            openKind(EventFormReducer.kindDraftOf(KindRules.kindOf(ref, latest.kinds, config)))
        } else if (local.value.sheet == null && local.value.dialog == null) {
            edit { EventFormReducer.withKind(it, ref, latest.events, latest.series, isNew = eventId == null) }
        }
    }

    /**
     * The sheet «Вид» of [draft] (spec 3.36.9): the draft a swipe left of the same kind comes back (plan D33); one of another kind is
     * dropped. A name that cannot be saved says so from the first frame, as a new piece of R4 does.
     */
    private fun openKind(draft: KindDraft) {
        val keptKind = local.value.keptKind?.takeIf { it.draft.ref == draft.ref }
        val sheet = keptKind ?: FormSheet.Kind(draft, problem = emptyProblemOf(draft))
        var opened = false
        local.update {
            opened = it.sheet == null && it.dialog == null
            // the draft kept aside is the sheet's now; one of another kind is dropped (plan D33)
            if (opened) it.copy(sheet = sheet, keptKind = null) else it
        }
        if (!opened) return
        persist()
        recheck(sheet.draft)
    }

    /** One sheet at a time, over no dialog; true — it opened. */
    private fun openSheet(sheet: FormSheet): Boolean {
        var opened = false
        local.update {
            opened = it.sheet == null && it.dialog == null
            if (opened) it.copy(sheet = sheet) else it
        }
        if (opened) persist()
        return opened
    }

    private inline fun <reified T : FormSheet> updateSheet(crossinline transform: (T) -> FormSheet) {
        local.update { now -> (now.sheet as? T)?.let { now.copy(sheet = transform(it)) } ?: now }
        persist()
    }

    private inline fun updateKind(crossinline transform: (KindDraft) -> KindDraft) {
        var changed: KindDraft? = null
        local.update { now ->
            val sheet = now.sheet as? FormSheet.Kind ?: return@update now
            val next = transform(sheet.draft)
            changed = next
            now.copy(sheet = sheet.copy(draft = next, problem = emptyProblemOf(next) ?: sheet.problem.takeIf { next.name == sheet.draft.name }))
        }
        persist()
        changed?.let { recheck(it) }
    }

    /** «Нужно имя» needs no language: a name that is nothing once its spaces go. */
    private fun emptyProblemOf(draft: KindDraft): KindNameProblem? =
        if (draft.ref !is KindRef.BuiltIn && EventRules.cleanKindName(draft.name, config).isEmpty()) KindNameProblem.Empty else null

    /** Why [draft]'s name cannot be saved, the built-in kinds named in the language of the interface (plan D34) — anew after every change. */
    private fun recheck(draft: KindDraft) {
        if (draft.ref is KindRef.BuiltIn) return
        problemJob?.cancel()
        problemJob = viewModelScope.launch {
            val problem = problemOf(draft)
            local.update { now ->
                val sheet = now.sheet as? FormSheet.Kind
                if (sheet == null || sheet.draft != draft) now else now.copy(sheet = sheet.copy(problem = problem))
            }
        }
    }

    private suspend fun problemOf(draft: KindDraft): KindNameProblem? {
        val names = latest.kinds.associate { kind -> kind.ref to (kind.ownName ?: words.nameOf(EventName.OfKind(kind.ref, null))) }
        return KindRules.nameProblem(draft.name, (draft.ref as? KindRef.Custom)?.id, names, config)
    }

    /** A swipe, «назад», a tap beside: the sheet goes and the form does not change; the sheet «Вид» keeps its draft (plan D33). */
    private fun hideSheet() {
        local.update { now -> now.copy(sheet = null, keptKind = (now.sheet as? FormSheet.Kind) ?: now.keptKind) }
        persist()
    }

    /** «Готово» of a sheet: what is chosen in it goes into the form; a kind — into the storage at once. */
    private fun done() {
        when (val sheet = local.value.sheet) {
            is FormSheet.Date -> closeAndEdit(sheet) { EventFormReducer.withDate(it, sheet.picked) }
            is FormSheet.Time -> closeAndEdit(sheet) { EventFormReducer.withTime(it, sheet.allDay, sheet.minutes, config) }
            is FormSheet.Duration -> closeAndEdit(sheet) { EventFormReducer.withDuration(it, sheet.minutes) }
            is FormSheet.Until -> closeAndEdit(sheet) { it.copy(until = sheet.picked?.takeIf { until -> until >= it.date }) }
            is FormSheet.Kind -> saveKind(sheet)
            is FormSheet.Scope, null -> Unit
        }
    }

    /** The sheet [sheet] goes and the draft takes what it chose — once: a second «Готово» finds no sheet. */
    private inline fun closeAndEdit(sheet: FormSheet, crossinline transform: (FormDraft) -> FormDraft) {
        var closed = false
        local.update { now ->
            closed = now.sheet == sheet
            if (closed) now.copy(sheet = null, draft = transform(now.draft)) else now
        }
        if (closed) persist()
    }

    /**
     * «Готово» of «Вид» (spec 3.36.9, decision 49): the colour of a built-in kind, a kind of one's own edited or new — stored at once; a
     * new one is chosen in the form. A name that cannot be saved keeps the sheet up with its reason.
     */
    private fun saveKind(sheet: FormSheet.Kind) {
        if (kindSaving) return
        kindSaving = true
        val draft = sheet.draft
        viewModelScope.launch {
            try {
                val ref = draft.ref
                if (ref is KindRef.BuiltIn) {
                    if (!closeKind(sheet)) return@launch
                    if (draft.color != KindRules.kindOf(ref, latest.kinds, config).look.color) events.saveKind(KindSave.BuiltInColor(ref.kind, draft.color))
                    return@launch
                }
                val problem = problemOf(draft)
                if (problem != null) {
                    local.update { now -> if (now.sheet == sheet) now.copy(sheet = sheet.copy(problem = problem)) else now }
                    return@launch
                }
                if (!closeKind(sheet)) return@launch
                val name = EventRules.cleanKindName(draft.name, config)
                if (ref is KindRef.Custom) {
                    events.saveKind(KindSave.EditOwn(ref.id, name, draft.color, draft.sign))
                } else {
                    // chosen in the form as a tile is: a new event whose time is not the person's own is «весь день» — the kind has no event yet
                    events.saveKind(KindSave.NewOwn(name, draft.color, draft.sign))?.let { saved ->
                        edit { EventFormReducer.withKind(it, saved, latest.events, latest.series, isNew = eventId == null) }
                    }
                }
            } finally {
                kindSaving = false
            }
        }
    }

    private fun closeKind(sheet: FormSheet.Kind): Boolean {
        var closed = false
        local.update { now ->
            closed = now.sheet == sheet
            if (closed) now.copy(sheet = null, keptKind = null) else now
        }
        if (closed) persist()
        return closed
    }

    /** «Удалить вид…» of a kind of one's own (spec 3.36.9): the dialog of R1 says what becomes of its events. */
    private fun askDeleteKind() {
        val sheet = local.value.sheet as? FormSheet.Kind ?: return
        val ref = sheet.draft.ref as? KindRef.Custom ?: return
        val kind = latest.kinds.firstOrNull { it.ref == ref } ?: return
        local.update { if (it.dialog == null) it.copy(dialog = EventFormDialog.DeleteKind(ref.id, kind.ownName.orEmpty(), EventFormReducer.eventsOf(ref.id, latest.events))) else it }
    }

    private fun confirm() {
        when (val dialog = local.value.dialog) {
            EventFormDialog.Discard -> {
                local.update { it.copy(dialog = null) }
                if (!leaving) {
                    leaving = true
                    effectChannel.trySend(EventFormEffect.Close)
                }
            }
            is EventFormDialog.DeleteKind -> deleteKind(dialog)
            null -> Unit
        }
    }

    /**
     * The kind of one's own goes (spec 3.35): its events and repeats become «Другое» in the storage, and so in the form — the draft, and
     * what the form compares with, so that the deletion is not taken for an edit of this event.
     */
    private fun deleteKind(dialog: EventFormDialog.DeleteKind) {
        val gone = KindRef.Custom(dialog.id)
        local.update { now -> now.copy(dialog = null, sheet = now.sheet.takeUnless { it is FormSheet.Kind }, keptKind = null) }
        persist()
        viewModelScope.launch {
            events.deleteOwnKind(dialog.id)
            initial = initial?.let { if (it.kind == gone) it.copy(kind = KindRef.OTHER) else it }
            original = original?.let { if (it.kind == gone) it.copy(kind = KindRef.OTHER) else it }
            originalSeries = originalSeries?.let { if (it.kind == gone) it.copy(kind = KindRef.OTHER) else it }
            edit { if (it.kind == gone) it.copy(kind = KindRef.OTHER) else it }
        }
    }

    /** ✕ and «назад»: «Не сохранять?» over edits, else the form closes — a new one too (spec 3.36.9). */
    private fun close() {
        val now = local.value
        if (leaving || now.dialog != null) return
        val base = initial
        if (!now.loading && base != null && EventFormReducer.isDirty(base, now.draft, config)) {
            local.update { it.copy(dialog = EventFormDialog.Discard) }
        } else {
            leaving = true
            effectChannel.trySend(EventFormEffect.Close)
        }
    }

    /**
     * «Сохранить» (spec 3.35, 3.36.9): a new event — stored, and its screen opens; an edit without changes — the form closes; an edit
     * asks «Урок повторяется» where it is one of the repeat ([ask]).
     */
    private fun save() {
        val now = local.value
        if (leaving || now.loading || now.sheet != null || now.dialog != null) return
        val base = initial ?: return
        val draft = resolved(now.draft)
        if (eventId == null) {
            leaving = true
            viewModelScope.launch {
                val id = events.add(draft.event(), draft.repeat, draft.until.takeIf { draft.repeat != Repeat.NONE }, clock.today())
                effectChannel.send(EventFormEffect.OpenCreated(id, draft.date))
            }
            return
        }
        if (!EventFormReducer.isDirty(base, draft, config)) {
            leaving = true
            effectChannel.trySend(EventFormEffect.Close)
            return
        }
        ask(draft)
    }

    /** The question of an edit (plan D6): none — carried out at once; else the sheet of the repeat with its dates and its plate. */
    private fun ask(draft: FormDraft) {
        val event = original ?: return
        val change = changeOf(event, resolved(draft))
        when (val question = SeriesEdits.suggestedScope(change)) {
            ScopeQuestion.None -> carryOut(change, scope = null)
            else -> {
                val series = originalSeries ?: return carryOut(change, scope = null)
                local.update { now -> if (now.sheet == null || now.sheet is FormSheet.Scope) now.copy(sheet = FormSheet.Scope(askOf(change, question, series))) else now }
                persist()
            }
        }
    }

    /** An answer of «Урок повторяется»: heard once, from the sheet that asked it. */
    private fun answer(scope: EditScope) {
        val event = original ?: return
        var asked = false
        local.update { now ->
            asked = now.sheet is FormSheet.Scope
            if (asked) now.copy(sheet = null) else now
        }
        if (!asked) return
        persist()
        carryOut(changeOf(event, resolved(local.value.draft)), scope)
    }

    /**
     * The edit carried out (spec 3.35, plan D4, D12): its plan from the events as they are now, the names of the records of what goes
     * frozen in the language of the interface, one transaction; then back to the screen of the event, «Занятия» told the date it lies on.
     */
    private fun carryOut(change: EventChange, scope: EditScope?) {
        if (leaving) return
        leaving = true
        viewModelScope.launch {
            val all = events.events.first()
            val before = all.firstOrNull { it.id == change.before.id } ?: change.before
            val fresh = change.copy(before = before)
            val series = before.seriesId?.let { id -> events.series.first().firstOrNull { it.id == id } }
            val plan = SeriesEdits.plan(fresh, scope, series, all, clock.instant(), clock.zone, config)
            val named = events.recordEvents.first()
            val frozen = SeriesEdits.freezing(plan, all, named.keys).associateWith { id -> words.recordTitleOf(named.getValue(id)) }
            events.apply(plan, frozen, clock.today())
            effectChannel.send(EventFormEffect.Saved(if (edits(plan, before.id)) fresh.after.date else before.date))
        }
    }

    /** Whether [plan] changes the event [id] itself — «Этот и следующие» leaves one that is over as it was (plan D4). */
    private fun edits(plan: EventPlan, id: Long): Boolean = plan.steps.any { step ->
        when (step) {
            is EventStep.UpdateOne -> step.id == id
            is EventStep.UpdateFollowing -> step.editedId == id
            is EventStep.Split -> step.editedId == id
            is EventStep.StartSeries -> step.eventId == id
            else -> false
        }
    }

    private fun changeOf(event: CalendarEvent, draft: FormDraft): EventChange = EventChange.of(
        before = event,
        draft = draft.event(),
        beforeRepeat = originalSeries?.takeIf { event.seriesId == it.id }?.repeat ?: Repeat.NONE,
        afterRepeat = draft.repeat,
        afterUntil = draft.until.takeIf { draft.repeat != Repeat.NONE },
        config = config,
    )

    /** The sheet of a repeat (spec 3.36.9): the word of the kind, the dates its answers touch (plan D31), what changes and where to. */
    private fun askOf(change: EventChange, question: ScopeQuestion, series: EventSeries): ScopeAsk {
        val before = change.before
        val after = change.after
        val field = SeriesEdits.changedField(change)
        return ScopeAsk(
            question = question,
            word = EventFormReducer.wordOf(KindRules.resolve(before.kind, latest.kinds)),
            date = before.date,
            moved = if (change.dateChanged) Moved(after.date, after.startMinutes, series.firstDate.dayOfWeek, series.startMinutes) else null,
            change = field?.let {
                ChangeLine(
                    field = it, before = before.draft(), after = after, beforeRepeat = change.beforeRepeat, afterRepeat = change.afterRepeat,
                    beforeKind = KindRules.kindOf(before.kind, latest.kinds, config), afterKind = KindRules.kindOf(after.kind, latest.kinds, config),
                )
            },
            following = SeriesEdits.affectedDates(before, series, latest.events, clock.instant(), clock.zone, config, EventsDimens.ANSWER_DATES),
        )
    }

    /** The draft with its kind as it is: a kind of one's own deleted meanwhile is «Другое» (spec 3.35). */
    private fun resolved(draft: FormDraft): FormDraft = draft.copy(kind = KindRules.resolve(draft.kind, latest.kinds))

    private fun inSeries(): Boolean = original?.seriesId != null && originalSeries != null

    private fun dateLimit() = EventFormReducer.dateLimit(clock.today(), initial?.date ?: local.value.draft.date, config)

    /**
     * The player's own edit of the draft: kept in the saved state as well, so that it outlives the process together with the text of the
     * fields. Not while an edit is being read: the form under it is the placeholder.
     */
    private inline fun edit(crossinline transform: (FormDraft) -> FormDraft) {
        local.update { it.copy(draft = transform(it.draft)) }
        persist()
    }

    private fun persist() {
        val now = local.value
        if (!now.loading) EventFormSaved.write(savedState, now.draft, now.sheet, now.keptKind)
    }

    private fun stateOf(local: Local, stored: Stored, today: LocalDate): EventFormState = EventFormReducer.stateOf(
        draft = local.draft,
        sheet = local.sheet,
        dialog = local.dialog,
        loading = local.loading,
        isNew = eventId == null,
        edited = original,
        editedSeries = originalSeries,
        initialDate = initial?.date ?: local.draft.date,
        events = stored.events,
        kinds = stored.kinds,
        today = today,
        focusNotes = focusNotes,
        config = config,
        byName = Formats.alphabetical(),
    )

    companion object {
        const val ARG_EVENT_ID = "eventId"
        const val ARG_DATE = "date"
        const val ARG_KIND = "kind"
        const val ARG_FOCUS_NOTES = "focusNotes"

        /** Navigation arguments cannot be null longs: this stands for "a new event". */
        const val NEW_EVENT = -1L

        private const val STOP_TIMEOUT_MS = 5_000L
        private val OWN_SIGNS = KindSign.OWN.toSet()
    }
}
