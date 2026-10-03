package com.violinjourney.app.feature.events.form

import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.events.CalendarEvent
import com.violinjourney.app.core.domain.events.CalendarMarks
import com.violinjourney.app.core.domain.events.DAYS_PER_WEEK
import com.violinjourney.app.core.domain.events.EventKind
import com.violinjourney.app.core.domain.events.EventName
import com.violinjourney.app.core.domain.events.EventRules
import com.violinjourney.app.core.domain.events.EventSeries
import com.violinjourney.app.core.domain.events.EventsConfig
import com.violinjourney.app.core.domain.events.KindLook
import com.violinjourney.app.core.domain.events.KindRef
import com.violinjourney.app.core.domain.events.KindRules
import com.violinjourney.app.core.domain.events.Recurrence
import com.violinjourney.app.core.domain.events.Repeat
import com.violinjourney.app.core.domain.events.UntilSummary
import com.violinjourney.app.core.domain.practice.PracticeStats
import com.violinjourney.app.core.text.takeCodePoints
import com.violinjourney.app.feature.events.EventsDimens
import com.violinjourney.app.feature.events.screen.EventReducer
import com.violinjourney.app.feature.events.screen.SeriesWord
import com.violinjourney.app.feature.practice.CalendarCell
import com.violinjourney.app.feature.practice.CellEvent
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.yearMonth

/**
 * The rules of the form of an event that are not about storage (spec 3.35, 3.36.9, 5.28; plan 7.3): what a new event starts with, what an
 * edit starts from, whether leaving loses something, the end and the line of the repeat, the time, «Весь день», the limit of the date,
 * and the months of the sheets. Pure.
 */
object EventFormReducer {
    private const val MINUTES_PER_HOUR = 60

    /**
     * A new event (spec 3.35, 5.28; plan D28, D49): of [kind] where the way in names one («Добавить выступление»), else of the kind of the
     * event a person created last — «Урок» for the very first one; the time of that kind (the start and the length of its last event —
     * of its repeat's template when it repeats), «весь день» without a length when there is none; on [date]. No repeat, no end.
     */
    fun newDraft(date: LocalDate, kind: KindRef?, events: List<CalendarEvent>, series: List<EventSeries>, kinds: List<EventKind>): FormDraft {
        val defaults = EventRules.defaults(events, series)
        // a kind of one's own the last event was of may be gone: its events are «Другое» now (spec 3.35)
        val chosen = KindRules.resolve(kind ?: defaults.kind, kinds)
        val time = if (chosen == defaults.kind) defaults.time else EventRules.timeOf(chosen, events, series)
        return FormDraft(
            kind = chosen, date = date, startMinutes = time.startMinutes, durationMinutes = time.startMinutes?.let { time.durationMinutes },
            repeat = Repeat.NONE, until = null, place = "", title = "", notes = "",
        )
    }

    /**
     * Another kind chosen (spec 3.36.9 «Недельный урок — четыре касания», 5.28; review of stage 98б): a new event whose time the person has
     * not chosen ([FormDraft.timeTouched]) takes the time of [kind] — the start and the length of its last event, of its repeat's template
     * when it repeats, «весь день» without a length where there is none (a kind of one's own just made) — and forgets what «Весь день» kept
     * aside; an edit ([isNew] false) and a time chosen keep theirs: the kind of a lesson made a performance does not move it.
     */
    fun withKind(draft: FormDraft, kind: KindRef, events: List<CalendarEvent>, series: List<EventSeries>, isNew: Boolean): FormDraft {
        if (!isNew || draft.timeTouched) return draft.copy(kind = kind)
        val time = EventRules.timeOf(kind, events, series)
        return draft.copy(
            kind = kind, startMinutes = time.startMinutes, durationMinutes = time.startMinutes?.let { time.durationMinutes },
            keptStart = null, keptDuration = null,
        )
    }

    /** A length chosen — a chip, or «Готово» of «Длительность»; null — «Без длительности». «Весь день» has none to choose (spec 5.28). */
    fun withDuration(draft: FormDraft, minutes: Int?): FormDraft =
        if (draft.startMinutes == null) draft else draft.copy(durationMinutes = minutes, timeTouched = true)

    /** An edit (spec 3.35): the event as it is stored, and its repeat — its step and its end — when it belongs to one. */
    fun draftOf(event: CalendarEvent, series: EventSeries?): FormDraft = FormDraft(
        kind = event.kind,
        date = event.date,
        startMinutes = event.startMinutes,
        durationMinutes = event.durationMinutes,
        repeat = series?.repeat ?: Repeat.NONE,
        until = series?.until,
        place = event.place,
        title = event.title,
        notes = event.notes,
    )

    /**
     * Whether leaving now loses something: compared as they would be stored ([EventRules.clean]) — a stray space, a time on its step
     * and what «Весь день» keeps aside are no edit; the end of a repeat counts only while there is a repeat.
     */
    fun isDirty(initial: FormDraft, current: FormDraft, config: EventsConfig): Boolean {
        fun stored(draft: FormDraft) = Triple(EventRules.clean(draft.event(), config), draft.repeat, draft.until.takeIf { draft.repeat != Repeat.NONE })
        return stored(initial) != stored(current)
    }

    /** What is typed, no longer than [max] characters as a person counts them: an emoji at the edge is never cut in half. */
    fun capped(text: String, max: Int): String = text.takeCodePoints(max)

    /**
     * The end the form says (spec 3.36.9): the start and the length — «до 17:45», «до 01:00, вс 25 окт.» past midnight; nothing for
     * «весь день» and for an event without a length.
     */
    fun endOf(date: LocalDate, startMinutes: Int?, durationMinutes: Int?): FormEnd? {
        val start = startMinutes ?: return null
        val length = durationMinutes ?: return null
        val end = start + length
        val day = date.plus(end / EventRules.MINUTES_PER_DAY, DateTimeUnit.DAY)
        return FormEnd(end % EventRules.MINUTES_PER_DAY, day.takeIf { it != date })
    }

    fun endOf(draft: FormDraft): FormEnd? = endOf(draft.date, draft.startMinutes, draft.durationMinutes)

    /**
     * The line under «Повтор» (spec 3.36.9): nothing for «Не повторять»; the weekday, the end and how many events there are up to it.
     * [stored] — the repeat the event of an edit belongs to, with [storedEvents] — the line says it as it is: its weekday (of its first
     * date, as the screen of the event says it — an event of it moved to a Tuesday «только этот» leaves the repeat on Mondays), its end
     * and its count; a new step given to it is a new repeat from the date of the form with the same end (spec 5.28 in «Меняет»), as a
     * repeat given to a single event or to a new one is. The word counted follows the kind ([word]).
     */
    fun repeatSummary(draft: FormDraft, stored: EventSeries?, storedEvents: List<CalendarEvent>, word: SeriesWord): RepeatSummary? {
        if (draft.repeat == Repeat.NONE) return null
        if (stored != null && stored.repeat == draft.repeat) {
            return RepeatSummary(Recurrence.weekday(stored.firstDate), stored.until, Recurrence.summary(stored, storedEvents)?.count, word)
        }
        val until = stored?.until ?: draft.until
        return RepeatSummary(Recurrence.weekday(draft.date), until, Recurrence.newSummary(draft.date, draft.repeat, until)?.count, word)
    }

    /** The word of the kind in the lines of a repeat (decision 46): «урок», «репетиция», «событие» of any other kind. */
    fun wordOf(kind: KindRef): SeriesWord = EventReducer.seriesWordOf(kind)

    /**
     * The last month of the sheet «Дата» (spec 3.36.9): the current one and 12, or the month of the date the form opened with, when it
     * is further — an edited event of a far month, a day of the calendar beyond the year.
     */
    fun dateLimit(today: LocalDate, initialDate: LocalDate, config: EventsConfig): YearMonth = EventRules.formLastMonth(today, initialDate, config)

    /** The time of the wheels: [hour] and [minute] on the step of the start (5 minutes, spec 5.28). */
    fun pickTime(hour: Int, minute: Int, config: EventsConfig): Int = EventRules.snapStart(hour * MINUTES_PER_HOUR + minute, config)

    /** The hour of [minutes] after midnight, and its minute. */
    fun hourOf(minutes: Int): Int = minutes / MINUTES_PER_HOUR

    fun minuteOf(minutes: Int): Int = minutes % MINUTES_PER_HOUR

    /**
     * Where the wheels of «Время» stand when the sheet opens: the start of the event; of «весь день» — the start «Весь день» took away
     * ([FormDraft.keptStart]), else the next whole hour after [now] — there is no time of its own to come back to.
     */
    fun wheelStart(draft: FormDraft, now: LocalTime, config: EventsConfig): Int =
        draft.startMinutes ?: draft.keptStart ?: EventRules.snapStart(((now.hour + 1) % HOURS_PER_DAY) * MINUTES_PER_HOUR, config)

    /**
     * «Готово» of «Время» (spec 3.36.9): «Весь день» takes the start and the length away and keeps them aside; a time puts back the
     * length «Весь день» kept, if the event has none — and what was kept aside is spent: an event with a time has nothing aside. Either
     * way the time is the person's own now ([FormDraft.timeTouched]).
     */
    fun withTime(draft: FormDraft, allDay: Boolean, minutes: Int, config: EventsConfig): FormDraft = if (allDay) {
        if (draft.startMinutes == null) {
            draft.copy(timeTouched = true)
        } else {
            draft.copy(
                startMinutes = null, durationMinutes = null, keptStart = draft.startMinutes, keptDuration = draft.durationMinutes, timeTouched = true,
            )
        }
    } else {
        draft.copy(
            startMinutes = EventRules.snapStart(minutes, config),
            durationMinutes = if (draft.startMinutes == null) draft.keptDuration else draft.durationMinutes,
            keptStart = null,
            keptDuration = null,
            timeTouched = true,
        )
    }

    /**
     * A new date (spec 3.36.9): an end of the repeat that is before it goes — a repeat cannot end before it begins, and the sheet «Повторять
     * до» does not offer such days.
     */
    fun withDate(draft: FormDraft, date: LocalDate): FormDraft =
        draft.copy(date = date, until = draft.until?.takeIf { it >= date })

    /** Where the sheet «Длительность» opens (spec 3.36.9): the length of the event, without one — an hour. */
    fun durationSheetStart(draft: FormDraft, config: EventsConfig): Int = draft.durationMinutes ?: config.durationSheetStartMinutes

    /** The third chip of «Дата»: the date chosen, a week on — move a lesson by a week, or make the next one, in one tap. */
    fun weekLater(date: LocalDate): LocalDate = date.plus(DAYS_PER_WEEK, DateTimeUnit.DAY)

    /**
     * The days of [month] in the sheet «Дата» (spec 3.36.9): the marks of every event of a day in its order, today — its ring, [picked]
     * — the ring «выбран»; no time — the grid is laid without its fill.
     */
    fun dateCells(month: YearMonth, picked: LocalDate, today: LocalDate, events: List<CalendarEvent>, kinds: List<EventKind>, config: EventsConfig): List<CalendarCell?> {
        val days = events.filter { it.date.yearMonth == month }.groupBy { it.date }.mapValues { (_, ofDay) -> ofDay.sortedWith(CalendarMarks.DAY_ORDER) }
        return PracticeStats.calendarCells(month).map { date ->
            date?.let {
                val ofDay = days[it].orEmpty()
                val marks = CalendarMarks.marksOf(ofDay, kinds, config)
                CalendarCell(
                    date = it, totalMs = 0, fillLevel = 0, isToday = it == today, isSelected = it == picked, isFuture = it > today,
                    marks = marks.looks, more = marks.more, events = ofDay.map { event -> cellEventOf(event, kinds) },
                )
            }
        }
    }

    /**
     * The days of [month] in «Повторять до» (spec 3.36.9): the marks of this repeat alone — of the form's kind on every date of it from
     * [first] up to [until] (with no end, every date of the month) — [until] with the ring «выбран», the days before [first] asleep.
     */
    fun untilCells(month: YearMonth, first: LocalDate, repeat: Repeat, until: LocalDate?, today: LocalDate, look: KindLook): List<CalendarCell?> {
        val from = month.firstDay
        val to = from.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
        val dates = Recurrence.dates(first, repeat, from, to, until).toSet()
        return PracticeStats.calendarCells(month).map { date ->
            date?.let {
                CalendarCell(
                    date = it, totalMs = 0, fillLevel = 0, isToday = it == today, isSelected = it == until, isFuture = it > today,
                    marks = if (it in dates) listOf(look) else emptyList(), enabled = it >= first,
                )
            }
        }
    }

    /** The day of the form in the preview of the sheet «Вид» (spec 3.36.9): the mark of [look] in it, by the rules of the cell. */
    fun previewCell(date: LocalDate, today: LocalDate, look: KindLook): CalendarCell = CalendarCell(
        date = date, totalMs = 0, fillLevel = 0, isToday = date == today, isSelected = false, isFuture = date > today, marks = listOf(look),
    )

    /** How many events of the kind of one's own [id] there are: «12 событий» of the sheet «Вид», «Его события (12)» of the dialog. */
    fun eventsOf(id: Long, events: List<CalendarEvent>): Int = events.count { it.kind == KindRef.Custom(id) }

    /**
     * The sheet «Вид» of [kind] (spec 3.36.9): its colour and sign; of one's own — its name; of a built-in one — its colour alone.
     */
    fun kindDraftOf(kind: EventKind): KindDraft = KindDraft(kind.ref, kind.ownName.orEmpty(), kind.look.color, kind.look.sign)

    /** A new kind of one's own (spec 3.36.9, plan D30): no name yet, the first colour and the first sign no kind has. */
    fun newKindDraft(kinds: List<EventKind>, config: EventsConfig): KindDraft {
        val free = KindRules.firstFree(kinds, config)
        return KindDraft(ref = null, name = "", color = free.color, sign = free.sign)
    }

    /** The months of «Повторять до» and «Дата» step by one. */
    fun stepMonth(month: YearMonth, by: Int): YearMonth = month.plus(by, DateTimeUnit.MONTH)

    private fun cellEventOf(event: CalendarEvent, kinds: List<EventKind>): CellEvent {
        val kind = KindRules.resolve(event.kind, kinds)
        return CellEvent(kind, ownName = kinds.firstOrNull { it.ref == kind }?.ownName, startMinutes = event.startMinutes)
    }

    /** Is [kind] a lesson: its place is a teacher (spec 3.35). */
    fun isLesson(kind: KindRef): Boolean = kind == KindRef.BuiltIn(BuiltInKind.LESSON)

    /** The end of a repeat not saved yet, for «Повторять до»: «последний урок — пн 28 декабря · всего 14». */
    fun untilSummary(first: LocalDate, repeat: Repeat, until: LocalDate?): UntilSummary? = Recurrence.newSummary(first, repeat, until)

    /**
     * What the form shows (plan 7.4): the draft and its kind, every kind in the order of the form (the built-in four, then one's own by
     * [byName] — the alphabet of the interface), the line of the repeat — of [editedSeries] as it is when the event of an edit belongs to
     * one — the end, and the sheet with what it shows around its choice. [initialDate] — the date the form opened with: the sheet «Дата»
     * goes forward to its month when it is further than the current one and 12. Pure: what is stored comes from outside.
     */
    fun stateOf(
        draft: FormDraft,
        sheet: FormSheet?,
        dialog: EventFormDialog?,
        loading: Boolean,
        isNew: Boolean,
        edited: CalendarEvent?,
        editedSeries: EventSeries?,
        initialDate: LocalDate,
        events: List<CalendarEvent>,
        kinds: List<EventKind>,
        today: LocalDate,
        focusNotes: Boolean,
        config: EventsConfig,
        byName: Comparator<String> = KindRules.LOWERCASE_ORDER,
    ): EventFormState {
        val ordered = KindRules.ordered(kinds, byName)
        val kind = KindRules.kindOf(draft.kind, kinds, config)
        val series = editedSeries?.takeIf { edited?.seriesId == it.id }
        val seriesEvents = series?.let { s -> events.filter { it.seriesId == s.id } }.orEmpty()
        return EventFormState(
            loading = loading,
            isNew = isNew,
            draft = draft,
            today = today,
            kinds = ordered,
            kind = kind,
            canAddOwn = KindRules.canAddOwn(kinds, config),
            ownKinds = kinds.count { it.ref is KindRef.Custom },
            maxOwnKinds = config.maxCustomKinds,
            inSeries = series != null,
            summary = repeatSummary(draft, series, seriesEvents, wordOf(kind.ref)),
            end = endOf(draft),
            savedName = edited?.let { EventName.of(it.title, it.kind, kinds) },
            focusNotes = focusNotes,
            sheet = sheet?.let { sheetOf(it, draft, kind, ordered, events, kinds, today, initialDate, config) },
            dialog = dialog,
            maxTitleLength = config.maxTitleLength,
            maxPlaceLength = config.maxPlaceLength,
            maxNotesLength = config.maxNotesLength,
            maxKindNameLength = config.maxKindNameLength,
            quickDurations = config.quickDurationsMinutes,
        )
    }

    /** A sheet with what it shows around its choice (spec 3.36.9). */
    fun sheetOf(
        sheet: FormSheet,
        draft: FormDraft,
        kind: EventKind,
        ordered: List<EventKind>,
        events: List<CalendarEvent>,
        kinds: List<EventKind>,
        today: LocalDate,
        initialDate: LocalDate,
        config: EventsConfig,
    ): EventFormSheet = when (sheet) {
        is FormSheet.Date -> EventFormSheet.Date(
            picked = sheet.picked,
            month = sheet.month,
            today = today,
            weekLater = weekLater(sheet.picked),
            cells = dateCells(sheet.month, sheet.picked, today, events, ordered, config),
            legend = CalendarMarks.kindsOfMonth(events, sheet.month, ordered),
            canForward = sheet.month < dateLimit(today, initialDate, config),
        )
        is FormSheet.Time -> {
            // the length of the event — of «весь день», the one it keeps aside and a time gives back; an event with a time and without a
            // length has no end to say, whatever was kept aside before (spec 3.36.9: «без длительности — строки нет»)
            val length = if (draft.startMinutes == null) draft.keptDuration else draft.durationMinutes
            EventFormSheet.Time(
                minutes = sheet.minutes,
                allDay = sheet.allDay,
                end = endOf(draft.date, sheet.minutes, length),
                duration = length,
                frequent = EventRules.frequentStarts(events, EventsDimens.FREQUENT_STARTS),
                step = config.startStepMinutes,
            )
        }
        is FormSheet.Duration -> {
            val start = draft.startMinutes ?: 0
            EventFormSheet.Duration(
                minutes = sheet.minutes, start = start,
                end = endOf(draft.date, start, sheet.minutes) ?: FormEnd(start, null),
                min = config.minDurationMinutes, max = config.maxDurationMinutes, step = config.durationStepMinutes,
            )
        }
        is FormSheet.Until -> EventFormSheet.Until(
            picked = sheet.picked,
            month = sheet.month,
            first = draft.date,
            repeat = draft.repeat,
            word = wordOf(kind.ref),
            summary = untilSummary(draft.date, draft.repeat, sheet.picked),
            chips = EventRules.untilChips(draft.date, config),
            cells = untilCells(sheet.month, draft.date, draft.repeat, sheet.picked, today, kind.look),
            canBack = sheet.month > draft.date.yearMonth,
        )
        is FormSheet.Kind -> {
            val own = sheet.draft.ref as? KindRef.Custom
            EventFormSheet.Kind(
                draft = sheet.draft,
                builtIn = (sheet.draft.ref as? KindRef.BuiltIn)?.kind,
                problem = sheet.problem,
                events = own?.let { eventsOf(it.id, events) } ?: 0,
                taken = KindRules.takenSigns(kinds, exceptId = own?.id),
                cell = previewCell(draft.date, today, KindLook(sheet.draft.sign, sheet.draft.color)),
                deletable = own != null,
            )
        }
        is FormSheet.Scope -> EventFormSheet.Scope(sheet.ask)
    }

    private const val HOURS_PER_DAY = 24
}
