package com.violinjourney.app.core.domain.events

import com.violinjourney.app.core.domain.events.TestEvents.MOSCOW
import com.violinjourney.app.core.domain.events.TestEvents.REHEARSAL
import com.violinjourney.app.core.domain.events.TestEvents.at
import com.violinjourney.app.core.domain.events.TestEvents.event
import com.violinjourney.app.core.domain.events.TestEvents.moment
import com.violinjourney.app.core.domain.events.TestEvents.series
import com.violinjourney.app.core.domain.events.TestEvents.weekly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/**
 * Edits of a repeat (spec 3.35, 3.36.9, 5.28; plan D4–D6, D31, D50): a weekly lesson on Mondays 17:00–17:45 with
 * Анна Сергеевна from 28.09.2026, laid to 20.12 — the Mondays 28.09 … 14.12 are events 1 … 12.
 */
class SeriesEditsTest {
    private val config = EventsConfig()
    private val zone = MOSCOW
    private val first = LocalDate(2026, 9, 28)
    private val mondays = (0..11).map { first.plusDays(7 * it) }
    private val repeat = series(1, first = first, laidUntil = LocalDate(2026, 12, 20), start = at(17), duration = 45, place = "Анна Сергеевна")
    private val lessons = weekly(seriesId = 1, dates = mondays, start = at(17), duration = 45, firstId = 1, createdAt = 100, place = "Анна Сергеевна")

    /** The lesson of [date]. */
    private fun lesson(date: LocalDate) = lessons.first { it.date == date }

    private fun change(before: CalendarEvent, afterRepeat: Repeat = Repeat.WEEKLY, edit: EventDraft.() -> EventDraft) =
        EventChange.of(before, before.draft().edit(), Repeat.WEEKLY, afterRepeat, afterUntil = null, config)

    private fun plan(change: EventChange, scope: EditScope?, now: Instant, events: List<CalendarEvent> = lessons, series: EventSeries = repeat) =
        SeriesEdits.plan(change, scope, series, events, now, zone, config)

    // ---- the question (plan D6)

    @Test
    fun `the sheet asks by what was changed`() {
        val october19 = lesson(LocalDate(2026, 10, 19))
        assertEquals(ScopeQuestion.Both(EditScope.FOLLOWING), SeriesEdits.suggestedScope(change(october19) { copy(startMinutes = at(17, 30)) }))
        assertEquals(ScopeQuestion.Both(EditScope.FOLLOWING), SeriesEdits.suggestedScope(change(october19) { copy(durationMinutes = 60) }))
        assertEquals(ScopeQuestion.Both(EditScope.FOLLOWING), SeriesEdits.suggestedScope(change(october19) { copy(kind = REHEARSAL) }))
        assertEquals(ScopeQuestion.Both(EditScope.FOLLOWING), SeriesEdits.suggestedScope(change(october19) { copy(title = "Урок у Анны") }))
        assertEquals(ScopeQuestion.Both(EditScope.FOLLOWING), SeriesEdits.suggestedScope(change(october19) { copy(place = "Ольга Петровна") }))
        assertEquals(ScopeQuestion.Both(EditScope.ONLY_THIS), SeriesEdits.suggestedScope(change(october19) { copy(date = LocalDate(2026, 10, 20)) }))
        assertEquals(
            ScopeQuestion.Both(EditScope.ONLY_THIS),
            SeriesEdits.suggestedScope(change(october19) { copy(date = LocalDate(2026, 10, 20), startMinutes = at(18)) }),
            "a date with a time is a date",
        )
        assertEquals(ScopeQuestion.FollowingOnly, SeriesEdits.suggestedScope(change(october19, Repeat.BIWEEKLY) { this }))
        assertEquals(ScopeQuestion.FollowingOnly, SeriesEdits.suggestedScope(change(october19, Repeat.NONE) { this }))
        assertEquals(ScopeQuestion.None, SeriesEdits.suggestedScope(change(october19) { copy(notes = "Гаммы в терцию") }), "notes alone ask nothing")
        assertEquals(ScopeQuestion.None, SeriesEdits.suggestedScope(change(october19) { copy(place = " Анна Сергеевна  ") }), "stray spaces are no change")
        assertEquals(ScopeQuestion.None, SeriesEdits.suggestedScope(change(october19) { this }))
        // a single event: edited or given a repeat — no question (plan D50)
        val single = event(50, LocalDate(2026, 10, 24), at(18, 30), 90)
        assertEquals(ScopeQuestion.None, SeriesEdits.suggestedScope(EventChange.of(single, single.draft().copy(startMinutes = at(19)), Repeat.NONE, Repeat.NONE, null, config)))
        assertEquals(ScopeQuestion.None, SeriesEdits.suggestedScope(EventChange.of(single, single.draft(), Repeat.NONE, Repeat.WEEKLY, null, config)))
    }

    @Test
    fun `the plate names the first field that changed and a date outranks a time`() {
        val october19 = lesson(LocalDate(2026, 10, 19))
        assertEquals(ChangedField.DATE, SeriesEdits.changedField(change(october19) { copy(date = LocalDate(2026, 10, 20), startMinutes = at(18)) }))
        assertEquals(ChangedField.TIME, SeriesEdits.changedField(change(october19) { copy(startMinutes = at(17, 30), place = "Ольга") }))
        assertEquals(ChangedField.REPEAT, SeriesEdits.changedField(change(october19, Repeat.BIWEEKLY) { copy(date = LocalDate(2026, 10, 20)) }))
        assertEquals(ChangedField.PLACE, SeriesEdits.changedField(change(october19) { copy(place = "Ольга") }))
        assertNull(SeriesEdits.changedField(change(october19) { copy(notes = "Гаммы") }))
    }

    // ---- the cut (plan D4)

    @Test
    fun `the cut is the date of the selected event but not before today and not on a day already over`() {
        val october19 = lesson(LocalDate(2026, 10, 19))
        assertEquals(LocalDate(2026, 10, 19), SeriesEdits.cutOf(october19, lessons, moment(LocalDate(2026, 10, 1), 12), zone, config))
        val october5 = lesson(LocalDate(2026, 10, 5))
        val monday12 = LocalDate(2026, 10, 12)
        assertEquals(monday12, SeriesEdits.cutOf(october5, lessons, moment(monday12, 12), zone, config), "today's lesson is still to come")
        assertEquals(monday12.plusDays(1), SeriesEdits.cutOf(october5, lessons, moment(monday12, 17, 45), zone, config), "today's lesson is over")
        assertEquals(monday12.plusDays(1), SeriesEdits.cutOf(lesson(monday12), lessons, moment(monday12, 18), zone, config))
    }

    // ---- plans of an event not over

    @Test
    fun `only this one changes the event alone and leaves it out of the repeat edits`() {
        val october19 = lesson(LocalDate(2026, 10, 19))
        val edit = change(october19) { copy(startMinutes = at(17, 30)) }
        assertEquals(EventPlan(EventStep.UpdateOne(october19.id, edit.after, detach = true)), plan(edit, EditScope.ONLY_THIS, moment(LocalDate(2026, 10, 1), 12)))
    }

    @Test
    fun `this and following changes the template from the selected event on`() {
        val october19 = lesson(LocalDate(2026, 10, 19))
        val edit = change(october19) { copy(startMinutes = at(17, 30), notes = "Принести ноты") }
        val result = plan(edit, scope = null, moment(LocalDate(2026, 10, 1), 12))
        assertEquals(
            EventPlan(
                EventStep.UpdateFollowing(
                    1, LocalDate(2026, 10, 19), setOf(TemplateField.START), SeriesTemplate(TestEvents.LESSON, at(17, 30), 45, "", "Анна Сергеевна"),
                    october19.id, edit.after,
                ),
            ),
            result,
            "the filled answer is «Этот и следующие»; the selected event takes its notes too",
        )
    }

    @Test
    fun `this and following changes only what the edit changed and not what the selected one has by itself`() {
        val now = moment(LocalDate(2026, 10, 1), 12)
        // 19.10 was taught by Ольга Петровна once («Только этот»); then its length goes to an hour for this and following
        val substituted = lesson(LocalDate(2026, 10, 19)).copy(place = "Ольга Петровна", detached = true)
        val withSubstitute = lessons.map { if (it.id == substituted.id) substituted else it }
        val longer = change(substituted) { copy(durationMinutes = 60) }
        val step = assertIs<EventStep.UpdateFollowing>(plan(longer, scope = null, now, withSubstitute).steps.single())
        assertEquals(setOf(TemplateField.DURATION), step.fields, "the teacher of one lesson does not spread")
        assertEquals(SeriesTemplate(TestEvents.LESSON, at(17), 60, "", "Анна Сергеевна"), step.template, "the repeat keeps its teacher")
        assertEquals("Ольга Петровна", step.edited.place, "the selected lesson keeps its own")
        // «Этот и следующие» at 19.10 moved the lessons to 17:30 — the repeat lays 17:30 — and 05.10, 12.10 kept 17:00; now the
        // teacher of 05.10 changes for this and following: the lessons from 19.10 stay at 17:30, the repeat too
        val later = lessons.map { if (it.date >= LocalDate(2026, 10, 19)) it.copy(startMinutes = at(17, 30)) else it }
        val october5 = later.first { it.date == LocalDate(2026, 10, 5) }
        val teacher = change(october5) { copy(place = "Ольга Петровна") }
        val following = assertIs<EventStep.UpdateFollowing>(
            plan(teacher, scope = null, moment(LocalDate(2026, 9, 30), 12), later, repeat.copy(startMinutes = at(17, 30))).steps.single(),
        )
        assertEquals(setOf(TemplateField.PLACE), following.fields, "the time of 05.10 is not written over 17:30")
        assertEquals(SeriesTemplate(TestEvents.LESSON, at(17, 30), 45, "", "Ольга Петровна"), following.template)
    }

    @Test
    fun `a template left for the whole day keeps no length`() {
        // the repeat lays 17:30 for an hour; 05.10 is at 17:00 without a length and goes «весь день» for this and following
        val laying = repeat.copy(startMinutes = at(17, 30), durationMinutes = 60)
        val october5 = lesson(LocalDate(2026, 10, 5)).copy(durationMinutes = null)
        val allDay = change(october5) { copy(startMinutes = null) }
        val step = assertIs<EventStep.UpdateFollowing>(
            plan(allDay, scope = null, moment(LocalDate(2026, 9, 30), 12), lessons, laying).steps.single(),
        )
        assertEquals(setOf(TemplateField.START), step.fields)
        assertEquals(SeriesTemplate(TestEvents.LESSON, null, null, "", "Анна Сергеевна"), step.template, "«весь день» has no length (spec 5.28)")
    }

    @Test
    fun `a lesson moved to another weekday ends the repeat the day before and starts a new one`() {
        val october19 = lesson(LocalDate(2026, 10, 19))
        val edit = change(october19) { copy(date = LocalDate(2026, 10, 20), startMinutes = at(18)) }
        val split = assertIs<EventStep.Split>(plan(edit, EditScope.FOLLOWING, moment(LocalDate(2026, 10, 1), 12)).steps.single())
        assertEquals(LocalDate(2026, 10, 19), split.cut, "the old repeat ends on 18.10")
        assertEquals(october19.id, split.editedId, "the selected lesson moves into the new repeat")
        val newSeries = assertNotNull(split.newSeries)
        assertEquals(LocalDate(2026, 10, 20), newSeries.firstDate)
        assertEquals(LocalDate(2026, 10, 20), newSeries.laidUntil, "it stands on its first date; the horizon lays from the week after")
        assertEquals(at(18), newSeries.startMinutes)
        assertNull(newSeries.until, "«до» of the old one: none")
        // «Только этот» — filled for a new date — moves one lesson
        assertEquals(EventPlan(EventStep.UpdateOne(october19.id, edit.after, detach = true)), plan(edit, scope = null, moment(LocalDate(2026, 10, 1), 12)))
    }

    @Test
    fun `a lesson changed by itself and moved for this and following gives the new repeat the template of the old one`() {
        // 19.10 was taught by Ольга Петровна once; then it moves to Tuesday 20.10 at 18:00 for this and following
        val substituted = lesson(LocalDate(2026, 10, 19)).copy(place = "Ольга Петровна", detached = true)
        val withSubstitute = lessons.map { if (it.id == substituted.id) substituted else it }
        val moved = change(substituted) { copy(date = LocalDate(2026, 10, 20), startMinutes = at(18)) }
        val split = assertIs<EventStep.Split>(plan(moved, EditScope.FOLLOWING, moment(LocalDate(2026, 10, 1), 12), withSubstitute).steps.single())
        assertEquals(
            SeriesTemplate(TestEvents.LESSON, at(18), 45, "", "Анна Сергеевна"),
            assertNotNull(split.newSeries).template(),
            "the time the move changed, the rest — of the old repeat",
        )
        assertEquals("Ольга Петровна", split.edited.place, "the moved lesson keeps its own teacher")
    }

    @Test
    fun `a lesson moved to the same weekday a week later starts a new repeat too`() {
        val october19 = lesson(LocalDate(2026, 10, 19))
        val split = assertIs<EventStep.Split>(
            plan(change(october19) { copy(date = LocalDate(2026, 10, 26)) }, EditScope.FOLLOWING, moment(LocalDate(2026, 10, 1), 12)).steps.single(),
        )
        assertEquals(LocalDate(2026, 10, 19), split.cut)
        assertEquals(LocalDate(2026, 10, 26), assertNotNull(split.newSeries).firstDate)
    }

    @Test
    fun `the new repeat keeps the end of the old one and none comes when that end is before the new date`() {
        val ending = repeat.copy(until = LocalDate(2026, 10, 25), laidUntil = LocalDate(2026, 10, 25))
        val october19 = lesson(LocalDate(2026, 10, 19))
        val now = moment(LocalDate(2026, 10, 1), 12)
        val within = assertIs<EventStep.Split>(plan(change(october19) { copy(date = LocalDate(2026, 10, 20)) }, EditScope.FOLLOWING, now, series = ending).steps.single())
        assertEquals(LocalDate(2026, 10, 25), assertNotNull(within.newSeries).until)
        val beyond = assertIs<EventStep.Split>(plan(change(october19) { copy(date = LocalDate(2026, 10, 27)) }, EditScope.FOLLOWING, now, series = ending).steps.single())
        assertNull(beyond.newSeries, "the old «до» is before the new date: the lesson is single")
        assertEquals(october19.id, beyond.editedId)
    }

    @Test
    fun `no repeat ends the repeat the day before and leaves the event single`() {
        val october19 = lesson(LocalDate(2026, 10, 19))
        val step = plan(change(october19, Repeat.NONE) { this }, EditScope.ONLY_THIS, moment(LocalDate(2026, 10, 1), 12)).steps.single()
        assertEquals(EventStep.Split(1, LocalDate(2026, 10, 19), october19.id, october19.draft(), newSeries = null), step, "«Только этот» is not heard: one answer")
        val biweekly = assertIs<EventStep.Split>(plan(change(october19, Repeat.BIWEEKLY) { this }, EditScope.FOLLOWING, moment(LocalDate(2026, 10, 1), 12)).steps.single())
        assertEquals(Repeat.BIWEEKLY, assertNotNull(biweekly.newSeries).repeat)
        assertEquals(LocalDate(2026, 10, 19), assertNotNull(biweekly.newSeries).firstDate)
    }

    @Test
    fun `notes alone are written to the event alone`() {
        val october19 = lesson(LocalDate(2026, 10, 19))
        assertEquals(
            EventPlan(EventStep.UpdateNotes(october19.id, "Гаммы в терцию")),
            plan(change(october19) { copy(notes = "  Гаммы в терцию ") }, EditScope.FOLLOWING, moment(LocalDate(2026, 10, 1), 12)),
        )
        assertEquals(EventPlan.NOTHING, plan(change(october19) { this }, EditScope.FOLLOWING, moment(LocalDate(2026, 10, 1), 12)))
    }

    // ---- plans of an event that is over (plan D4): nothing before today and the cut changes

    @Test
    fun `a weekday changed at a lesson gone by changes nothing before the cut`() {
        val october5 = lesson(LocalDate(2026, 10, 5))
        val monday12 = LocalDate(2026, 10, 12)
        val now = moment(monday12, 18) // today's lesson is over too: the cut is tomorrow
        val split = assertIs<EventStep.Split>(plan(change(october5) { copy(date = LocalDate(2026, 10, 6), startMinutes = at(18)) }, EditScope.FOLLOWING, now).steps.single())
        assertEquals(monday12.plusDays(1), split.cut)
        assertNull(split.editedId, "the lesson gone by is not moved")
        val newSeries = assertNotNull(split.newSeries)
        assertEquals(LocalDate(2026, 10, 6), newSeries.firstDate, "Tuesdays, counted from the new date")
        assertEquals(monday12, newSeries.laidUntil, "laid from the cut on, never before")
        assertTrue(Recurrence.toLay(newSeries, monday12, config.seriesHorizonWeeks).dates.all { it >= split.cut })
        assertEquals(LocalDate(2026, 10, 13), Recurrence.toLay(newSeries, monday12, config.seriesHorizonWeeks).dates.first())
    }

    @Test
    fun `a lesson gone by moved to a day to come starts the new repeat on that day`() {
        // the lesson of 05.10 is not moved — it is over; the Tuesdays begin on 20.10, and that day is laid too
        val october5 = lesson(LocalDate(2026, 10, 5))
        val monday12 = LocalDate(2026, 10, 12)
        val split = assertIs<EventStep.Split>(plan(change(october5) { copy(date = LocalDate(2026, 10, 20)) }, EditScope.FOLLOWING, moment(monday12, 18)).steps.single())
        assertNull(split.editedId)
        val newSeries = assertNotNull(split.newSeries)
        assertEquals(LocalDate(2026, 10, 19), newSeries.laidUntil)
        assertEquals(LocalDate(2026, 10, 20), Recurrence.toLay(newSeries, monday12, config.seriesHorizonWeeks).dates.first())
    }

    @Test
    fun `a step changed at a lesson gone by and no repeat there change nothing before the cut`() {
        val october5 = lesson(LocalDate(2026, 10, 5))
        val monday12 = LocalDate(2026, 10, 12)
        val now = moment(monday12, 12) // today's lesson is still to come: the cut is today
        val biweekly = assertIs<EventStep.Split>(plan(change(october5, Repeat.BIWEEKLY) { this }, scope = null, now).steps.single())
        assertEquals(monday12, biweekly.cut)
        assertNull(biweekly.editedId)
        val newSeries = assertNotNull(biweekly.newSeries)
        assertEquals(LocalDate(2026, 10, 11), newSeries.laidUntil)
        assertEquals(LocalDate(2026, 10, 19), Recurrence.toLay(newSeries, monday12, config.seriesHorizonWeeks).dates.first(), "every other week from 05.10: 19.10, not 12.10")
        val none = plan(change(october5, Repeat.NONE) { this }, scope = null, now)
        assertEquals(EventPlan(EventStep.Split(1, monday12, editedId = null, edited = october5.draft(), newSeries = null)), none)
    }

    @Test
    fun `only this one still edits a lesson gone by and its notes are its own whatever the answer`() {
        val october5 = lesson(LocalDate(2026, 10, 5))
        val now = moment(LocalDate(2026, 10, 12), 12)
        val onlyThis = change(october5) { copy(date = LocalDate(2026, 10, 6)) }
        assertEquals(EventPlan(EventStep.UpdateOne(october5.id, onlyThis.after, detach = true)), plan(onlyThis, EditScope.ONLY_THIS, now))
        val withNotes = change(october5) { copy(startMinutes = at(17, 30), notes = "Как прошло: хорошо") }
        val steps = plan(withNotes, EditScope.FOLLOWING, now).steps
        assertEquals(2, steps.size)
        val following = assertIs<EventStep.UpdateFollowing>(steps[0])
        assertNull(following.editedId, "the template does not reach the lesson gone by")
        assertEquals(LocalDate(2026, 10, 12), following.cut)
        assertEquals(EventStep.UpdateNotes(october5.id, "Как прошло: хорошо"), steps[1])
    }

    // ---- a single event given a repeat (plan D50)

    @Test
    fun `a single event given a repeat stays itself and is laid from today on`() {
        val today = LocalDate(2026, 10, 1)
        val single = event(50, LocalDate(2026, 10, 7), at(16), 60, notes = "Этюд")
        val step = assertIs<EventStep.StartSeries>(
            SeriesEdits.plan(EventChange.of(single, single.draft(), Repeat.NONE, Repeat.WEEKLY, LocalDate(2026, 12, 31), config), null, null, emptyList(), moment(today, 12), zone, config).steps.single(),
        )
        assertEquals(50, step.eventId)
        assertEquals(LocalDate(2026, 10, 7), step.series.firstDate)
        assertEquals(LocalDate(2026, 10, 7), step.series.laidUntil)
        assertEquals(LocalDate(2026, 12, 31), step.series.until)
        // one gone by: laid from today, not from its date
        val past = event(51, LocalDate(2026, 9, 9), at(16), 60)
        val fromPast = assertIs<EventStep.StartSeries>(
            SeriesEdits.plan(EventChange.of(past, past.draft(), Repeat.NONE, Repeat.WEEKLY, null, config), null, null, emptyList(), moment(today, 12), zone, config).steps.single(),
        )
        assertEquals(LocalDate(2026, 9, 30), fromPast.series.laidUntil)
        // one of 28.10 moved into the past as it is given a repeat: laid from today on — the Wednesdays between today and
        // its old date are not skipped, as they would be by a cut on its old date
        val later = event(52, LocalDate(2026, 10, 28), at(16), 60)
        val moved = EventChange.of(later, later.draft().copy(date = LocalDate(2026, 9, 2)), Repeat.NONE, Repeat.WEEKLY, null, config)
        val fromMoved = assertIs<EventStep.StartSeries>(SeriesEdits.plan(moved, null, null, emptyList(), moment(today, 12), zone, config).steps.single())
        assertEquals(LocalDate(2026, 10, 7), Recurrence.toLay(fromMoved.series, today, config.seriesHorizonWeeks).dates.first())
        // a repeat ending before the event is none
        val ended = EventChange.of(single, single.draft(), Repeat.NONE, Repeat.WEEKLY, LocalDate(2026, 10, 6), config)
        assertEquals(EventPlan.NOTHING, SeriesEdits.plan(ended, null, null, emptyList(), moment(today, 12), zone, config))
    }

    // ---- deletion

    @Test
    fun `a deletion of this and following takes the selected one whatever its date`() {
        val october19 = lesson(LocalDate(2026, 10, 19))
        val now = moment(LocalDate(2026, 10, 1), 12)
        assertEquals(EventPlan(EventStep.DeleteOne(october19.id)), SeriesEdits.deletePlan(october19, EditScope.ONLY_THIS, repeat, lessons, now, zone, config))
        assertEquals(EventPlan(EventStep.DeleteOne(october19.id)), SeriesEdits.deletePlan(october19, null, repeat, lessons, now, zone, config))
        assertEquals(
            EventPlan(EventStep.DeleteFollowing(1, LocalDate(2026, 10, 19), october19.id)),
            SeriesEdits.deletePlan(october19, EditScope.FOLLOWING, repeat, lessons, now, zone, config),
        )
        // from the very first date: the repeat ends before it began — the storage drops it
        assertEquals(
            EventPlan(EventStep.DeleteFollowing(1, first, 1)),
            SeriesEdits.deletePlan(lesson(first), EditScope.FOLLOWING, repeat, lessons, moment(LocalDate(2026, 9, 27), 12), zone, config),
        )
        // a lesson gone by: the cut is today; the lesson itself goes too
        val october5 = lesson(LocalDate(2026, 10, 5))
        assertEquals(
            EventPlan(EventStep.DeleteFollowing(1, LocalDate(2026, 10, 12), october5.id)),
            SeriesEdits.deletePlan(october5, EditScope.FOLLOWING, repeat, lessons, moment(LocalDate(2026, 10, 12), 12), zone, config),
        )
        val single = event(50, LocalDate(2026, 10, 24))
        assertEquals(EventPlan(EventStep.DeleteOne(50)), SeriesEdits.deletePlan(single, EditScope.FOLLOWING, null, emptyList(), now, zone, config))
    }

    @Test
    fun `what goes is the following events not changed by themselves and the selected one`() {
        val changedAlone = lessons.map { if (it.date == LocalDate(2026, 11, 2)) it.copy(detached = true) else it }
        val october19 = lesson(LocalDate(2026, 10, 19))
        val deletion = EventPlan(EventStep.DeleteFollowing(1, LocalDate(2026, 10, 26), october19.id))
        val expected = changedAlone.filter { it.date >= LocalDate(2026, 10, 26) && !it.detached }.map { it.id }.toSet() + october19.id
        assertEquals(expected, SeriesEdits.gone(deletion, changedAlone))
        val split = EventPlan(EventStep.Split(1, LocalDate(2026, 10, 19), october19.id, october19.draft(), newSeries = null))
        assertEquals(changedAlone.filter { it.date > LocalDate(2026, 10, 19) && !it.detached }.map { it.id }.toSet(), SeriesEdits.gone(split, changedAlone))
        assertEquals(emptySet(), SeriesEdits.gone(EventPlan(EventStep.UpdateNotes(1, "")), changedAlone))
    }

    @Test
    fun `the names that freeze are those of the events that go and have records - none of a moved one or of one without records`() {
        val october19 = lesson(LocalDate(2026, 10, 19))
        val now = moment(LocalDate(2026, 10, 1), 12)
        // moved to Tuesday for this and following: the old repeat ends on the 18th, its Mondays from the 26th go, the 19th moves
        val move = change(october19) { copy(date = LocalDate(2026, 10, 20)) }
        val split = plan(move, EditScope.FOLLOWING, now)
        val october26 = lesson(LocalDate(2026, 10, 26)).id
        val november9 = lesson(LocalDate(2026, 11, 9)).id
        val recorded = setOf(october19.id, october26, november9, lesson(LocalDate(2026, 10, 5)).id)
        assertEquals(setOf(october26, november9), SeriesEdits.freezing(split, lessons, recorded), "the moved one keeps its event, the 5th is not touched")
        // only this one: nothing goes, nothing freezes
        assertEquals(emptySet(), SeriesEdits.freezing(plan(move, EditScope.ONLY_THIS, now), lessons, recorded))
        // a deletion of this and following: the selected one goes as well, and its records keep their name
        val deletion = SeriesEdits.deletePlan(october19, EditScope.FOLLOWING, repeat, lessons, now, zone, config)
        assertEquals(setOf(october19.id, october26, november9), SeriesEdits.freezing(deletion, lessons, recorded))
        // no records at all: no name to reckon
        assertEquals(emptySet(), SeriesEdits.freezing(deletion, lessons, emptySet()))
    }

    // ---- the dates of the answers (plan D31)

    @Test
    fun `the answer names two dates and says whether more follow`() {
        val october19 = lesson(LocalDate(2026, 10, 19))
        val now = moment(LocalDate(2026, 10, 1), 12)
        assertEquals(
            AffectedDates(listOf(LocalDate(2026, 10, 19), LocalDate(2026, 10, 26)), andOn = true),
            SeriesEdits.affectedDates(october19, repeat, lessons, now, zone, config, limit = 2),
        )
        val untilNovember = repeat.copy(until = LocalDate(2026, 10, 26), laidUntil = LocalDate(2026, 10, 26))
        val shorter = lessons.filter { it.date <= LocalDate(2026, 10, 26) }
        assertEquals(AffectedDates(listOf(LocalDate(2026, 10, 19), LocalDate(2026, 10, 26)), andOn = false), SeriesEdits.affectedDates(october19, untilNovember, shorter, now, zone, config, limit = 2))
        val untilItself = repeat.copy(until = LocalDate(2026, 10, 19), laidUntil = LocalDate(2026, 10, 19))
        assertEquals(AffectedDates(listOf(LocalDate(2026, 10, 19)), andOn = false), SeriesEdits.affectedDates(october19, untilItself, lessons.filter { it.date <= LocalDate(2026, 10, 19) }, now, zone, config, limit = 2))
        // the last lesson laid of a repeat without an end: one date, and still «и дальше» — the horizon lays more
        assertEquals(
            AffectedDates(listOf(LocalDate(2026, 12, 14)), andOn = true),
            SeriesEdits.affectedDates(lesson(LocalDate(2026, 12, 14)), repeat, lessons, now, zone, config, limit = 2),
        )
        // a repeat with an end still to be laid: the dates ahead count
        val withEnd = repeat.copy(until = LocalDate(2026, 12, 31))
        assertTrue(SeriesEdits.affectedDates(lesson(LocalDate(2026, 12, 14)), withEnd, lessons, now, zone, config, limit = 2).andOn, "14.12, 21.12, 28.12")
        // one changed by itself is not touched, and not named
        val changedAlone = lessons.map { if (it.date == LocalDate(2026, 10, 26)) it.copy(detached = true) else it }
        assertEquals(listOf(LocalDate(2026, 10, 19), LocalDate(2026, 11, 2)), SeriesEdits.affectedDates(october19, repeat, changedAlone, now, zone, config, limit = 2).dates)
        // a lesson gone by: from the first date on the cut
        val october5 = lesson(LocalDate(2026, 10, 5))
        assertEquals(
            listOf(LocalDate(2026, 10, 19), LocalDate(2026, 10, 26)),
            SeriesEdits.affectedDates(october5, repeat, lessons, moment(LocalDate(2026, 10, 12), 18), zone, config, limit = 2).dates,
        )
    }
}
