package com.violinjourney.app.core.domain.events

import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/** The answers of the sheet «Урок повторяется» (spec 3.36.9). */
enum class EditScope { ONLY_THIS, FOLLOWING }

/** What the sheet of a repeat asks (spec 3.36.9, plan D6): nothing, both answers with one filled, or «Этот и следующие» alone. */
sealed interface ScopeQuestion {
    data object None : ScopeQuestion

    data class Both(val filled: EditScope) : ScopeQuestion

    data object FollowingOnly : ScopeQuestion
}

/** The field the plate «что меняется» names (plan D6): the first one that changed, in this order — a date outranks a time. */
enum class ChangedField { REPEAT, DATE, TIME, DURATION, KIND, TITLE, PLACE }

/**
 * An edit of an event: [before] as it is stored, [after] as the form leaves it — cleaned ([of] cleans it), so that a
 * stray space is no change. [afterUntil] — the end of a repeat given to a single event; an event of a repeat keeps the
 * end of its repeat, the form does not edit it (spec 3.36.9).
 */
data class EventChange(
    val before: CalendarEvent,
    val after: EventDraft,
    val beforeRepeat: Repeat,
    val afterRepeat: Repeat,
    val afterUntil: LocalDate? = null,
) {
    val repeatChanged: Boolean get() = afterRepeat != beforeRepeat
    val dateChanged: Boolean get() = after.date != before.date
    val notesChanged: Boolean get() = after.notes != before.notes

    /**
     * The fields of the template the edit changed — the time, the length, the kind, the title, the teacher or the place:
     * what «Этот и следующие» changes at the following events and in the repeat, and nothing else (spec 3.35).
     */
    val templateFields: Set<TemplateField> get() = after.template().fieldsChangedFrom(before.draft().template())

    val templateChanged: Boolean get() = templateFields.isNotEmpty()

    companion object {
        fun of(before: CalendarEvent, draft: EventDraft, beforeRepeat: Repeat, afterRepeat: Repeat, afterUntil: LocalDate?, config: EventsConfig) =
            EventChange(before, EventRules.clean(draft, config), beforeRepeat, afterRepeat, afterUntil)
    }
}

/**
 * One step of an [EventPlan]: what the storage does to the events and repeats, in one transaction with the other steps.
 * No step touches an event dated before its [cut][Split.cut], but for the selected event itself — edited «only this»,
 * edited or moved while it is not over, deleted (plan D4).
 */
sealed interface EventStep {
    /** One event takes [fields]; [detach] — and leaves the edits of its repeat for good: «Только этот» (plan D5). */
    data class UpdateOne(val id: Long, val fields: EventDraft, val detach: Boolean) : EventStep

    /** The notes of one event and nothing else: a change of notes alone asks nothing (plan D5). */
    data class UpdateNotes(val id: Long, val notes: String) : EventStep

    /**
     * «Этот и следующие» without a new date or step (spec 3.35): the [fields] the edit changed take their values from
     * [edited] at every event of the repeat from [cut] on that was not changed by itself — each keeps the fields that were
     * not changed, and one left «весь день» keeps no length; the repeat lays [template] from now on (its own with [fields]
     * of [edited]). [editedId] — the selected event while it is not over: it takes all of [edited].
     */
    data class UpdateFollowing(
        val seriesId: Long,
        val cut: LocalDate,
        val fields: Set<TemplateField>,
        val template: SeriesTemplate,
        val editedId: Long?,
        val edited: EventDraft,
    ) : EventStep

    /**
     * «Этот и следующие» with a new date or step, or «Не повторять» (spec 5.28 and its «Меняет»): the repeat ends the day
     * before [cut]; its events from [cut] on go, those changed by themselves stay as single events; [newSeries] takes over
     * — null when none does — with the template of the old repeat and the fields the edit changed. [editedId] — the
     * selected event while it is not over: it takes [edited] and moves into the new repeat as its first event, or becomes
     * single. The events [newSeries] lays carry the moment of the old repeat (plan D49).
     */
    data class Split(val oldSeriesId: Long, val cut: LocalDate, val editedId: Long?, val edited: EventDraft, val newSeries: EventSeries?) : EventStep

    /** A single event given a repeat (plan D50): it stays itself — its id, notes, program and records — and becomes the first of [series]. */
    data class StartSeries(val eventId: Long, val fields: EventDraft, val series: EventSeries) : EventStep

    data class DeleteOne(val id: Long) : EventStep

    /**
     * «Этот и следующие» of a deletion (spec 3.35): the repeat ends the day before [cut], its events from [cut] on go — those
     * changed by themselves stay as single events — and [selectedId] goes whatever its date is: «Удалить» was pressed on it.
     */
    data class DeleteFollowing(val seriesId: Long, val cut: LocalDate, val selectedId: Long) : EventStep
}

/** What an edit or a deletion does: its steps in order, one transaction (`EventDao.apply`); the horizon is laid after it. */
data class EventPlan(val steps: List<EventStep>) {
    constructor(vararg steps: EventStep) : this(steps.toList())

    companion object {
        val NOTHING = EventPlan(emptyList())
    }
}

/** The dates the answer «Этот и следующие» names (plan D31): the first few, and whether more follow — «19, 26 окт. и дальше». */
data class AffectedDates(val dates: List<LocalDate>, val andOn: Boolean)

/**
 * Edits and deletions of an event and of its repeat (spec 3.35, 3.36.9, 5.28; plan D4–D6, D31, D50), as plans the storage
 * carries out. **What is over is never touched by an edit of a repeat** (spec 3.35): its notes and records are history.
 * Over means its end has come ([EventRules.isPast]) — what «Выступления» divide by. The cut ([cutOf]) is where the
 * repeat's «following» begins: the date of the selected event, but not before today, and the next day when the repeat
 * has an event there that is over already.
 */
object SeriesEdits {
    /**
     * The question of the sheet (spec 3.36.9, plan D6): a new step or «Не повторять» — «Этот и следующие» alone; a new date,
     * with a new time or without — both, «Только этот» filled; a field of the template — both, «Этот и следующие» filled;
     * notes alone, nothing, or a single event (given a repeat or not) — no question.
     */
    fun suggestedScope(change: EventChange): ScopeQuestion = when {
        change.beforeRepeat == Repeat.NONE -> ScopeQuestion.None
        change.repeatChanged -> ScopeQuestion.FollowingOnly
        change.dateChanged -> ScopeQuestion.Both(EditScope.ONLY_THIS)
        change.templateChanged -> ScopeQuestion.Both(EditScope.FOLLOWING)
        else -> ScopeQuestion.None
    }

    /** What the plate «что меняется» names: the first field that changed in the order of [ChangedField]; null — nothing did. */
    fun changedField(change: EventChange): ChangedField? {
        val before = change.before
        val after = change.after
        return when {
            change.repeatChanged -> ChangedField.REPEAT
            change.dateChanged -> ChangedField.DATE
            after.startMinutes != before.startMinutes -> ChangedField.TIME
            after.durationMinutes != before.durationMinutes -> ChangedField.DURATION
            after.kind != before.kind -> ChangedField.KIND
            after.title != before.title -> ChangedField.TITLE
            after.place != before.place -> ChangedField.PLACE
            else -> null
        }
    }

    /**
     * Where «Этот и следующие» begins (plan D4): the date of [selected], not before today; when the repeat has an event on
     * that day that is over already, the next day — what is over is not changed, deleted or laid again by an edit.
     */
    fun cutOf(selected: CalendarEvent, seriesEvents: List<CalendarEvent>, now: Instant, zone: TimeZone, config: EventsConfig): LocalDate {
        val today = now.toLocalDateTime(zone).date
        val day = maxOf(selected.date, today)
        val overThere = seriesEvents.any { it.seriesId == selected.seriesId && it.date == day && EventRules.isPast(it, now, zone, config) }
        return if (overThere) day.plus(1, DateTimeUnit.DAY) else day
    }

    /**
     * What saving [change] does (spec 3.35, 3.36.9). [scope] — the answer of the sheet; for a question of both answers
     * null means the filled one, and it is not heard where there is no choice. [series] and [seriesEvents] — the repeat of
     * the event and its events as stored; null for a single event.
     */
    fun plan(
        change: EventChange,
        scope: EditScope?,
        series: EventSeries?,
        seriesEvents: List<CalendarEvent>,
        now: Instant,
        zone: TimeZone,
        config: EventsConfig,
    ): EventPlan {
        val clean = change.copy(after = EventRules.clean(change.after, config))
        val before = clean.before
        val after = clean.after
        if (series == null || before.seriesId != series.id) return singlePlan(clean, now, zone)
        val notes = if (clean.notesChanged) EventStep.UpdateNotes(before.id, after.notes) else null
        val answer = when (val question = suggestedScope(clean)) {
            ScopeQuestion.None -> return EventPlan(listOfNotNull(notes))
            ScopeQuestion.FollowingOnly -> EditScope.FOLLOWING
            is ScopeQuestion.Both -> scope ?: question.filled
        }
        if (answer == EditScope.ONLY_THIS) return EventPlan(EventStep.UpdateOne(before.id, after, detach = true))
        val cut = cutOf(before, seriesEvents, now, zone, config)
        val over = EventRules.isPast(before, now, zone, config)
        val editedId = before.id.takeUnless { over }
        // the notes of an event that is over are still its own: written whatever the answer, and nothing else of it is
        val ownNotes = notes?.takeIf { over }
        // what the edit changed, and only that: a field the selected event has by itself — a teacher changed «only this»,
        // a time older than the repeat's — does not spread to the following events (spec 3.35)
        val fields = clean.templateFields
        val template = series.template().taking(fields, from = after.template())
        val step = if (clean.repeatChanged || clean.dateChanged) {
            EventStep.Split(series.id, cut, editedId, after, newSeriesOf(after.date, template, clean.afterRepeat, series.until, cut, moved = !over))
        } else {
            EventStep.UpdateFollowing(series.id, cut, fields, template, editedId, after)
        }
        return EventPlan(listOfNotNull(step, ownNotes))
    }

    /**
     * What «Удалить» does (spec 3.35): a single event, or «Только этот» — the event alone (its repeat lays it never again);
     * «Этот и следующие» — the repeat ends the day before the cut and the selected event goes as well, over or not.
     * [scope] null — the narrower answer.
     */
    fun deletePlan(
        event: CalendarEvent,
        scope: EditScope?,
        series: EventSeries?,
        seriesEvents: List<CalendarEvent>,
        now: Instant,
        zone: TimeZone,
        config: EventsConfig,
    ): EventPlan {
        if (series == null || event.seriesId != series.id || scope != EditScope.FOLLOWING) return EventPlan(EventStep.DeleteOne(event.id))
        return EventPlan(EventStep.DeleteFollowing(series.id, cutOf(event, seriesEvents, now, zone, config), event.id))
    }

    /**
     * The dates «Этот и следующие» touches (plan D31): the events of the repeat from the cut on that were not changed by
     * themselves, the selected one while it is not over, and the dates still to be laid up to «до» — the first [limit] of
     * them; [AffectedDates.andOn] — more follow, or the repeat has no end. For a selected event that is over — from the
     * first date on or after the cut.
     */
    fun affectedDates(
        selected: CalendarEvent,
        series: EventSeries,
        seriesEvents: List<CalendarEvent>,
        now: Instant,
        zone: TimeZone,
        config: EventsConfig,
        limit: Int,
    ): AffectedDates {
        val cut = cutOf(selected, seriesEvents, now, zone, config)
        val selectedOn = !EventRules.isPast(selected, now, zone, config)
        val laid = seriesEvents
            .filter { it.seriesId == series.id && ((it.id == selected.id && selectedOn) || (it.date >= cut && !it.detached && it.id != selected.id)) }
            .map { it.date }
        val ahead = series.until?.let { until ->
            Recurrence.dates(series.firstDate, series.repeat, series.laidUntil.plus(1, DateTimeUnit.DAY), until, until)
        }.orEmpty()
        val all = (laid + ahead).sorted()
        return AffectedDates(all.take(limit), andOn = series.until == null || all.size > limit)
    }

    /**
     * The events [plan] deletes, as [events] stand: whose records keep the name they had (plan D12) — the screen reckons
     * those names before the plan is carried out. The storage unlinks by the same rule, not by this list.
     */
    fun gone(plan: EventPlan, events: List<CalendarEvent>): Set<Long> = plan.steps.flatMapTo(mutableSetOf()) { step ->
        when (step) {
            is EventStep.DeleteOne -> listOf(step.id)
            is EventStep.DeleteFollowing -> following(events, step.seriesId, step.cut, keepId = null).map { it.id } + step.selectedId
            is EventStep.Split -> following(events, step.oldSeriesId, step.cut, keepId = step.editedId).map { it.id }
            else -> emptyList()
        }
    }

    /**
     * The events whose records keep the name they wore when [plan] is carried out (plan D12): of those it deletes ([gone]), the ones
     * with records — [recorded], the events of `EventRepository.recordEvents`. The screen reckons their names in the language of the
     * interface before the plan goes to the storage; an event without records has nothing to name.
     */
    fun freezing(plan: EventPlan, events: List<CalendarEvent>, recorded: Set<Long>): Set<Long> =
        gone(plan, events).filterTo(mutableSetOf()) { it in recorded }

    private fun following(events: List<CalendarEvent>, seriesId: Long, cut: LocalDate, keepId: Long?) =
        events.filter { it.seriesId == seriesId && it.date >= cut && !it.detached && it.id != keepId }

    /**
     * A single event edited (plan D50): given no repeat — the event alone; given one — it stays itself and becomes the first
     * event of a new repeat, laid from today on: an edit lays no event in days gone by (plan D4). A repeat whose «до» is
     * before the event is none: the event is single.
     */
    private fun singlePlan(change: EventChange, now: Instant, zone: TimeZone): EventPlan {
        val before = change.before
        val after = change.after
        val until = change.afterUntil
        if (change.afterRepeat == Repeat.NONE || (until != null && until < after.date)) {
            return if (after == before.draft()) EventPlan.NOTHING else EventPlan(EventStep.UpdateOne(before.id, after, detach = false))
        }
        val today = now.toLocalDateTime(zone).date
        val series = EventSeries(
            id = 0, kind = after.kind, repeat = change.afterRepeat, firstDate = after.date, until = until,
            laidUntil = maxOf(after.date, Recurrence.dayBefore(today)),
            startMinutes = after.startMinutes, durationMinutes = after.durationMinutes, title = after.title, place = after.place,
        )
        return EventPlan(EventStep.StartSeries(before.id, after, series))
    }

    /**
     * The repeat that takes over from the cut (spec 5.28 «Меняет»): from [firstDate] — the new date of the selected event —
     * with the step chosen, the «до» of the old one and [template] — the old one's with what the edit changed; none for
     * «Не повторять», or when that «до» is before the new date. Laid from the cut on: [moved] — the selected event moves
     * into it and stands on its first date; one that is over stays where it was, and the first date is laid when it is not
     * before the cut.
     */
    private fun newSeriesOf(
        firstDate: LocalDate,
        template: SeriesTemplate,
        repeat: Repeat,
        until: LocalDate?,
        cut: LocalDate,
        moved: Boolean,
    ): EventSeries? {
        if (repeat == Repeat.NONE || (until != null && until < firstDate)) return null
        val laidUntil = maxOf(Recurrence.dayBefore(cut), if (moved) firstDate else Recurrence.dayBefore(firstDate))
        return EventSeries(
            id = 0, kind = template.kind, repeat = repeat, firstDate = firstDate, until = until, laidUntil = laidUntil,
            startMinutes = template.startMinutes, durationMinutes = template.durationMinutes, title = template.title, place = template.place,
        )
    }
}
