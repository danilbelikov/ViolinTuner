package com.violinjourney.app.feature.events.form

import com.violinjourney.app.core.domain.events.AffectedDates
import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.events.ChangedField
import com.violinjourney.app.core.domain.events.EditScope
import com.violinjourney.app.core.domain.events.EventDraft
import com.violinjourney.app.core.domain.events.EventKind
import com.violinjourney.app.core.domain.events.EventName
import com.violinjourney.app.core.domain.events.KindNameProblem
import com.violinjourney.app.core.domain.events.KindRef
import com.violinjourney.app.core.domain.events.KindSign
import com.violinjourney.app.core.domain.events.Repeat
import com.violinjourney.app.core.domain.events.ScopeQuestion
import com.violinjourney.app.core.domain.events.UntilSummary
import com.violinjourney.app.feature.events.screen.SeriesWord
import com.violinjourney.app.feature.practice.CalendarCell
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/**
 * What the form of an event holds (spec 3.35, 3.36.9): the fields of the event as typed, its repeat and the end of the repeat. [keptStart]
 * and [keptDuration] — the start and the length «Весь день» took away: switched off again, the wheels come back to them (3.36.9: «Выключите
 * — и колёса вернутся на прежнее время»). [timeTouched] — the person chose the time or the length themselves («Готово» of «Время» or of
 * «Длительность», a chip of the length): from then on another kind keeps them; until then a new event takes the time of the kind chosen
 * (5.28, [EventFormReducer.withKind]). Neither is stored with the event.
 */
data class FormDraft(
    val kind: KindRef,
    val date: LocalDate,
    /** Null — «весь день». */
    val startMinutes: Int?,
    /** Null — no length; always null for «весь день». */
    val durationMinutes: Int?,
    val repeat: Repeat,
    /** The last day of the repeat, inclusive (spec 5.28); null — no end. Heard only while [repeat] is one. */
    val until: LocalDate?,
    /** The teacher of a lesson, the place of anything else. */
    val place: String,
    val title: String,
    val notes: String,
    val keptStart: Int? = null,
    val keptDuration: Int? = null,
    val timeTouched: Boolean = false,
) {
    /** The fields of the event, as the domain edits them. */
    fun event(): EventDraft = EventDraft(kind, date, startMinutes, durationMinutes, title, place, notes)
}

/** The end of an event as the form says it (spec 3.36.9): «до 17:45», and after midnight with its day — «до 01:00, вс 25 окт.». */
data class FormEnd(
    /** The minute of the day it ends at. */
    val minutes: Int,
    /** The day it ends on when that is not its own; null — the same day. */
    val nextDay: LocalDate?,
)

/**
 * The line under «Повтор» (spec 3.36.9): «по понедельникам · без конца · до…», with an end «по понедельникам · до 31 дек. · 14 уроков».
 * [count] — of the repeat up to its end, null without one; [word] — what is counted: «уроков», «репетиций», «событий».
 */
data class RepeatSummary(val weekday: DayOfWeek, val until: LocalDate?, val count: Int?, val word: SeriesWord)

/**
 * A kind as the sheet «Вид» edits it (spec 3.36.9): a built-in one ([ref] a [KindRef.BuiltIn] — only its colour changes), one of one's own
 * ([KindRef.Custom] — its name, colour and sign), or a new one ([ref] null). What is chosen in the sheet lives here until «Готово».
 */
data class KindDraft(val ref: KindRef?, val name: String, val color: Int, val sign: KindSign)

/**
 * A sheet of the form while it is up (spec 3.36.9, plan D21): what is chosen in it — not yet in the form; «Готово» puts it there, a
 * swipe, «назад» and a tap beside it only hide it. One frame, one face at a time.
 */
sealed interface FormSheet {
    data class Date(val month: YearMonth, val picked: LocalDate) : FormSheet

    /** [minutes] — the wheels: the hour and the minute; [allDay] — «Весь день» switched on, the wheels gone. */
    data class Time(val minutes: Int, val allDay: Boolean) : FormSheet

    data class Duration(val minutes: Int) : FormSheet

    data class Until(val month: YearMonth, val picked: LocalDate?) : FormSheet

    /** [problem] — why the name cannot be saved, reckoned in the language of the interface after each change of it. */
    data class Kind(val draft: KindDraft, val problem: KindNameProblem? = null) : FormSheet

    /** «Урок повторяется» (spec 3.36.9): what «Сохранить» asked of an edit of an event of a repeat. */
    data class Scope(val ask: ScopeAsk) : FormSheet
}

/**
 * The question of an edit of an event of a repeat (spec 3.36.9, plan D6, D31): [question] — both answers with one filled, or «Этот и
 * следующие» alone (a new step, «Не повторять»); [word] — «урок», «репетиция», «событие»; [date] — the date of the event before the edit:
 * «Изменить урок 19 октября …». [moved] — a new date: «Перенести урок 19 октября на вт 20 окт., 18:00?» and what becomes of the repeat in
 * the answers; [change] — the plate «что меняется»; [following] — the dates «Этот и следующие» touches: «19, 26 окт. и дальше».
 */
data class ScopeAsk(
    val question: ScopeQuestion,
    val word: SeriesWord,
    val date: LocalDate,
    val moved: Moved?,
    val change: ChangeLine?,
    val following: AffectedDates,
)

/**
 * Where an event of a repeat is moved to (spec 3.36.9): the new [date] and [startMinutes]; the repeat as the rest of it stays — its
 * weekday and start, «остальные — по понедельникам в 17:00»; the new repeat «Этот и следующие» lays — «с 20 окт. — по вторникам в 18:00».
 */
data class Moved(val date: LocalDate, val startMinutes: Int?, val restWeekday: DayOfWeek, val restStartMinutes: Int?)

/**
 * The plate «что меняется» (spec 3.36.9, plan D6): the first field that changed — a date outranks a time — before and after: «Время
 * 17:00 → 17:30», «пн 19 · 17:00 → вт 20 · 18:00». The kinds — resolved, a kind of one's own with its name.
 */
data class ChangeLine(
    val field: ChangedField,
    val before: EventDraft,
    val after: EventDraft,
    val beforeRepeat: Repeat,
    val afterRepeat: Repeat,
    val beforeKind: EventKind,
    val afterKind: EventKind,
)

/** «Не сохранять?» and «Удалить вид?» (spec 3.36.9): the dialogs of R1 over the form. */
sealed interface EventFormDialog {
    data object Discard : EventFormDialog

    /** «Вид „Сольфеджио“ удалится. Его события (12) станут „Другое“.» — [events] of it now. */
    data class DeleteKind(val id: Long, val name: String, val events: Int) : EventFormDialog
}

/** A sheet of the form as the screen draws it (spec 3.36.9): the choice in it and what it shows around the choice. */
sealed interface EventFormSheet {
    /**
     * «Дата»: the date chosen in words, the chips «Сегодня · Завтра · пн 5 окт.» — [weekLater], the chosen one a week on — and the month
     * [month] with the marks of every event; forward up to [canForward], back without a limit.
     */
    data class Date(
        val picked: LocalDate,
        val month: YearMonth,
        val today: LocalDate,
        val weekLater: LocalDate,
        val cells: List<CalendarCell?>,
        val legend: List<EventKind>,
        val canForward: Boolean,
    ) : EventFormSheet

    /**
     * «Время»: «Весь день» and the wheels at [minutes] — the minutes by [step], the step of a start (5, spec 5.28); under them the end — «до
     * 20:00 · 1 ч 30 мин», after midnight with its day — when the event has a length ([duration]); «Частое» — the starts of the events by
     * how often they are used ([frequent]; none — no line).
     */
    data class Time(val minutes: Int, val allDay: Boolean, val end: FormEnd?, val duration: Int?, val frequent: List<Int>, val step: Int) : EventFormSheet

    /**
     * «Длительность»: the stepper at [minutes] by [step] from [min] to [max] — «с 11:00 до 13:30» under it ([start], [end]); «−» asleep at
     * the floor, «+» at the ceiling with its words.
     */
    data class Duration(val minutes: Int, val start: Int, val end: FormEnd, val min: Int, val max: Int, val step: Int) : EventFormSheet

    /**
     * «Повторять до»: the end chosen ([picked], null — «Без конца»), the last event and how many there are ([summary]) or «каждый
     * понедельник с 28 сентября» ([first], [repeat]); the chips — «Без конца» and [chips]; the month [month] with the marks of this repeat
     * up to its end, the days before [first] asleep; back no further than the month of [first].
     */
    data class Until(
        val picked: LocalDate?,
        val month: YearMonth,
        val first: LocalDate,
        val repeat: Repeat,
        val word: SeriesWord,
        val summary: UntilSummary?,
        val chips: List<LocalDate>,
        val cells: List<CalendarCell?>,
        val canBack: Boolean,
    ) : EventFormSheet

    /**
     * «Вид»: [draft] as it is chosen; [builtIn] — the kind is one of the four, its name a word of the interface, only its colour changes;
     * [problem] — why the name cannot be saved; [events] — of a kind of one's own, «12 событий»; [taken] — the signs other kinds of one's
     * own wear, with the name of each; [cell] — the day of the form with the mark of the kind; [deletable] — «Удалить вид…» in its head.
     */
    data class Kind(
        val draft: KindDraft,
        val builtIn: BuiltInKind?,
        val problem: KindNameProblem?,
        val events: Int,
        val taken: Map<KindSign, String>,
        val cell: CalendarCell,
        val deletable: Boolean,
    ) : EventFormSheet

    data class Scope(val ask: ScopeAsk) : EventFormSheet
}

/**
 * The form of an event (spec 3.35, 3.36.9; plan 7.4): new or an edit. [kinds] — every kind in the order of the form (the built-in four, then
 * one's own by the alphabet); [kind] — the kind of the draft, resolved; [ownKinds] — how many of one's own there are, «Своих видов — 20 из
 * 20». [inSeries] — an edit of an event of a repeat: the line under «Повтор» says the repeat as it is, without «до…». [savedName] — the
 * name before the edit, for «Изменения в „Урок“ пропадут»; null for a new event.
 */
data class EventFormState(
    val loading: Boolean,
    val isNew: Boolean,
    val draft: FormDraft,
    val today: LocalDate,
    val kinds: List<EventKind>,
    val kind: EventKind,
    val canAddOwn: Boolean,
    val ownKinds: Int,
    val maxOwnKinds: Int,
    val inSeries: Boolean,
    val summary: RepeatSummary?,
    val end: FormEnd?,
    val savedName: EventName?,
    val focusNotes: Boolean,
    val sheet: EventFormSheet?,
    val dialog: EventFormDialog?,
    val maxTitleLength: Int,
    val maxPlaceLength: Int,
    val maxNotesLength: Int,
    val maxKindNameLength: Int,
    val quickDurations: List<Int>,
)

sealed interface EventFormIntent {
    /** A tile of a kind: chosen; the second tap of the chosen one opens the sheet «Вид» (spec 3.36.9). */
    data class KindPicked(val ref: KindRef) : EventFormIntent

    /** «Цвет вида „Урок“» / «Изменить вид „Сольфеджио“»: the sheet «Вид» of the chosen kind. */
    data object KindRowClicked : EventFormIntent

    /** «Свой вид»: the sheet «Вид» of a new kind of one's own. */
    data object OwnKindClicked : EventFormIntent

    data object DateRowClicked : EventFormIntent

    data object TimeRowClicked : EventFormIntent

    /** A chip of the length; null — «Без длительности». */
    data class DurationPicked(val minutes: Int?) : EventFormIntent

    /** «Другая…» and the chip of a length of one's own: the sheet «Длительность». */
    data object DurationOtherClicked : EventFormIntent

    data class RepeatSelected(val repeat: Repeat) : EventFormIntent

    /** «до…» under «Повтор»: the sheet «Повторять до». */
    data object UntilClicked : EventFormIntent

    data class PlaceChanged(val text: String) : EventFormIntent

    data class TitleChanged(val text: String) : EventFormIntent

    data class NotesChanged(val text: String) : EventFormIntent

    /** An arrow of the month of «Дата»: −1 or +1. */
    data class DateMonthStep(val by: Int) : EventFormIntent

    /** A day of «Дата» or one of its chips. */
    data class DatePicked(val date: LocalDate) : EventFormIntent

    /** The wheel of the hours stopped at [hour]. */
    data class TimeHour(val hour: Int) : EventFormIntent

    /** The wheel of the minutes stopped at [minute]. */
    data class TimeMinute(val minute: Int) : EventFormIntent

    data object AllDayToggled : EventFormIntent

    /** A chip of «Частое»: the wheels go to it. */
    data class FrequentPicked(val minutes: Int) : EventFormIntent

    /** «−» or «+» of «Длительность». */
    data class DurationStepped(val steps: Int) : EventFormIntent

    /** A day or a chip of «Повторять до»; null — «Без конца». */
    data class UntilPicked(val date: LocalDate?) : EventFormIntent

    data class UntilMonthStep(val by: Int) : EventFormIntent

    data class KindNameChanged(val text: String) : EventFormIntent

    data class KindColorPicked(val color: Int) : EventFormIntent

    data class KindSignPicked(val sign: KindSign) : EventFormIntent

    /** «Удалить вид…» of the sheet of a kind of one's own: the dialog of R1. */
    data object KindDeleteClicked : EventFormIntent

    /** «Готово» of a sheet: what is chosen in it goes into the form — and a kind into the storage at once (decision 49). */
    data object SheetDone : EventFormIntent

    /** A sheet swiped down, tapped beside or closed with «назад»: only hidden — the form does not change. */
    data object SheetHidden : EventFormIntent

    /** An answer of «Урок повторяется». */
    data class ScopeAnswered(val scope: EditScope) : EventFormIntent

    data object SaveClicked : EventFormIntent

    /** ✕ and the system «назад» alike: «Не сохранять?» over a form with edits, else it closes. */
    data object CloseClicked : EventFormIntent

    data object DialogConfirmed : EventFormIntent

    data object DialogDismissed : EventFormIntent
}

sealed interface EventFormEffect {
    /** Nothing saved — back to where the form was opened from. */
    data object Close : EventFormEffect

    /** A new event: its screen takes the place of the form; «Занятия» are told the date it lies on (plan D24). */
    data class OpenCreated(val eventId: Long, val date: LocalDate) : EventFormEffect

    /** An edit saved: back to the screen of the event; «Занятия» are told the date it lies on now. */
    data class Saved(val date: LocalDate) : EventFormEffect
}
