package com.violinjourney.app.feature.events.screen

import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.events.KindRef
import kotlinx.datetime.LocalDate

/** A part of the screen of an event, under its title (spec 3.36.9). */
enum class EventSection {
    /** «Заметки». */
    NOTES,

    /** «Программа» of a performance, «Что играли» of any other kind. */
    PROGRAM,

    /** «Записи» — from the day of the event on. */
    RECORDS,

    /** «Записи появятся с …» — a dashed line in their place before the day of the event. */
    RECORDS_LATER,

    /** «Можно добавить» — one card in the place of the three empty parts, from the day of the event on. */
    CAN_ADD,
}

/**
 * The order of the parts of the screen of an event (spec 3.36.9, plan, the check of 3.35 items 16–20): by the kind of the event and by its
 * time — the screen is opened for what is done with it then. Pure.
 *
 * - a lesson, a rehearsal, «Другое» and a kind of one's own — the notes («что задали»), «Что играли», the records;
 * - a performance before its day — the programme, the notes, «Записи появятся с …»: it is opened to get the programme ready;
 * - a performance from its day on — the records, the programme, the notes: after the concert it is opened for its records, and «Добавить
 *   запись» is pinned at the bottom.
 *
 * Before the day of the event there are no records — of any kind (spec 3.35 item 4): «Записи появятся с …» stands in their place, with no
 * «+ Добавить». From its day on, an event without notes, programme and records shows «Можно добавить» in the place of the three empty parts.
 */
object EventSections {
    /** The parts in their order, and whether «Добавить запись» stands pinned at the bottom. */
    data class Layout(val order: List<EventSection>, val pinnedAddRecord: Boolean)

    /** [kind] — resolved already: a kind of one's own that is gone is «Другое». [date] — the day the event starts on. */
    fun of(kind: KindRef, date: LocalDate, today: LocalDate, notesEmpty: Boolean, programEmpty: Boolean, recordsEmpty: Boolean): Layout {
        val performance = isPerformance(kind)
        val fromItsDay = date <= today
        val order = when {
            !fromItsDay && performance -> listOf(EventSection.PROGRAM, EventSection.NOTES, EventSection.RECORDS_LATER)
            !fromItsDay -> listOf(EventSection.NOTES, EventSection.PROGRAM, EventSection.RECORDS_LATER)
            notesEmpty && programEmpty && recordsEmpty -> listOf(EventSection.CAN_ADD)
            performance -> listOf(EventSection.RECORDS, EventSection.PROGRAM, EventSection.NOTES)
            else -> listOf(EventSection.NOTES, EventSection.PROGRAM, EventSection.RECORDS)
        }
        return Layout(order, pinnedAddRecord = performance && fromItsDay)
    }

    /** «Выступление»: its pieces are a programme, and its records are what it is opened for after the concert. */
    fun isPerformance(kind: KindRef): Boolean = kind == KindRef.BuiltIn(BuiltInKind.PERFORMANCE)
}
