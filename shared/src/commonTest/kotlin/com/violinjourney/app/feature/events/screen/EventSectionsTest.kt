package com.violinjourney.app.feature.events.screen

import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.events.KindRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

/** The order of the parts of the screen of an event (spec 3.36.9): by the kind of the event and by its time. */
class EventSectionsTest {
    private val today = LocalDate(2026, 10, 24)
    private val tomorrow = LocalDate(2026, 10, 25)
    private val yesterday = LocalDate(2026, 10, 23)
    private val lesson = KindRef.BuiltIn(BuiltInKind.LESSON)
    private val performance = KindRef.BuiltIn(BuiltInKind.PERFORMANCE)

    private fun layout(kind: KindRef, date: LocalDate, notes: Boolean = false, program: Boolean = false, records: Boolean = false) =
        EventSections.of(kind, date, today, notesEmpty = !notes, programEmpty = !program, recordsEmpty = !records)

    @Test
    fun `a lesson to come reads its notes then what was played and says when its records come`() {
        val layout = layout(lesson, tomorrow)
        assertEquals(listOf(EventSection.NOTES, EventSection.PROGRAM, EventSection.RECORDS_LATER), layout.order)
        assertFalse(layout.pinnedAddRecord, "no records of an event to come: nothing to add them with")
    }

    @Test
    fun `a lesson from its day on has its records in their place`() {
        for (date in listOf(today, yesterday)) {
            val layout = layout(lesson, date, notes = true)
            assertEquals(listOf(EventSection.NOTES, EventSection.PROGRAM, EventSection.RECORDS), layout.order, "$date")
            assertFalse(layout.pinnedAddRecord, "a lesson adds its records by «+ Добавить» of their title")
        }
    }

    @Test
    fun `every kind but a performance keeps the order of a lesson`() {
        val kinds = listOf(KindRef.BuiltIn(BuiltInKind.REHEARSAL), KindRef.BuiltIn(BuiltInKind.OTHER), KindRef.Custom(7))
        for (kind in kinds) {
            assertEquals(listOf(EventSection.NOTES, EventSection.PROGRAM, EventSection.RECORDS_LATER), layout(kind, tomorrow).order, "$kind")
            assertEquals(listOf(EventSection.NOTES, EventSection.PROGRAM, EventSection.RECORDS), layout(kind, yesterday, program = true).order, "$kind")
        }
    }

    @Test
    fun `a performance to come is opened to get its programme ready`() {
        val layout = layout(performance, tomorrow, program = true)
        assertEquals(listOf(EventSection.PROGRAM, EventSection.NOTES, EventSection.RECORDS_LATER), layout.order)
        assertFalse(layout.pinnedAddRecord)
    }

    @Test
    fun `a performance from its day on is opened for its records - they come first and their button is pinned`() {
        for (date in listOf(today, yesterday)) {
            val layout = layout(performance, date, records = true)
            assertEquals(listOf(EventSection.RECORDS, EventSection.PROGRAM, EventSection.NOTES), layout.order, "$date")
            assertTrue(layout.pinnedAddRecord, "$date")
        }
        assertTrue(layout(performance, today).pinnedAddRecord, "pinned with nothing in it too")
    }

    @Test
    fun `from its day on an event with nothing in it offers what can be added in one card`() {
        assertEquals(listOf(EventSection.CAN_ADD), layout(lesson, today).order)
        assertEquals(listOf(EventSection.CAN_ADD), layout(performance, yesterday).order)
        assertEquals(listOf(EventSection.CAN_ADD), layout(KindRef.Custom(7), yesterday).order)
        // before its day there is nothing to add records to: no «Можно добавить»
        assertEquals(listOf(EventSection.NOTES, EventSection.PROGRAM, EventSection.RECORDS_LATER), layout(lesson, tomorrow).order)
    }

    @Test
    fun `anything in it brings the parts back in their places`() {
        val full = listOf(EventSection.NOTES, EventSection.PROGRAM, EventSection.RECORDS)
        assertEquals(full, layout(lesson, today, notes = true).order)
        assertEquals(full, layout(lesson, today, program = true).order)
        assertEquals(full, layout(lesson, today, records = true).order)
    }

    @Test
    fun `only a performance is one`() {
        assertTrue(EventSections.isPerformance(performance))
        for (kind in listOf(lesson, KindRef.BuiltIn(BuiltInKind.REHEARSAL), KindRef.BuiltIn(BuiltInKind.OTHER), KindRef.Custom(1))) {
            assertFalse(EventSections.isPerformance(kind), "$kind")
        }
    }
}
