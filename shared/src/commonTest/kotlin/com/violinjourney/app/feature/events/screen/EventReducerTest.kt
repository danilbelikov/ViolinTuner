package com.violinjourney.app.feature.events.screen

import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.events.CalendarEvent
import com.violinjourney.app.core.domain.events.EventName
import com.violinjourney.app.core.domain.events.EventSeries
import com.violinjourney.app.core.domain.events.EventsConfig
import com.violinjourney.app.core.domain.events.KindRef
import com.violinjourney.app.core.domain.events.KindRules
import com.violinjourney.app.core.domain.events.KindSign
import com.violinjourney.app.core.domain.events.Repeat
import com.violinjourney.app.core.domain.events.SessionEvent
import com.violinjourney.app.core.domain.events.StoredKind
import com.violinjourney.app.core.domain.repertoire.Accidental
import com.violinjourney.app.core.domain.repertoire.Piece
import com.violinjourney.app.core.domain.repertoire.PieceGroup
import com.violinjourney.app.core.domain.repertoire.PieceSection
import com.violinjourney.app.core.domain.repertoire.PieceStatus
import com.violinjourney.app.core.domain.repertoire.SectionRef
import com.violinjourney.app.core.domain.repertoire.Tonic
import com.violinjourney.app.core.domain.repertoire.scale.ScaleKind
import com.violinjourney.app.core.domain.repertoire.scale.ScaleSpec
import com.violinjourney.app.core.domain.session.SessionSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/** An event and what belongs to it → the screen of the event (spec 3.35, 3.36.9). */
class EventReducerTest {
    private val config = EventsConfig()
    private val zone = TimeZone.of("Europe/Moscow")
    private val today = LocalDate(2026, 10, 24)
    private val kinds = KindRules.all(listOf(StoredKind.Own(7, "Оркестр ДК", 3, KindSign.BOOK, 1)), config)
    private val lesson = KindRef.BuiltIn(BuiltInKind.LESSON)
    private val performance = KindRef.BuiltIn(BuiltInKind.PERFORMANCE)

    private fun event(
        id: Long = 1,
        kind: KindRef = lesson,
        date: LocalDate = today,
        start: Int? = 17 * 60,
        duration: Int? = 45,
        title: String = "",
        place: String = "",
        notes: String = "",
        seriesId: Long? = null,
    ) = CalendarEvent(id, kind, date, start, duration, title, place, notes, seriesId, detached = false, createdAtEpochMs = 0)

    private fun piece(id: Long, title: String, composer: String = "", groupId: Long? = null, scale: ScaleSpec? = null) = Piece(
        id = id, title = title, composer = composer, key = null, tempoBpm = null, status = PieceStatus.LEARNING, notes = "",
        createdAtEpochMs = 0, updatedAtEpochMs = id, groupId = groupId, scale = scale,
        section = if (scale != null) PieceSection.SCALES else PieceSection.PIECES,
    )

    private fun record(id: Long, dateTime: String, eventId: Long?) = SessionSummary(
        id = id, title = null, startedAtEpochMs = LocalDateTime.parse(dateTime).toInstant(zone).toEpochMilliseconds(),
        durationMs = 220_000, a4Hz = 440.0, toleranceCents = 8.0, nearCents = 20.0, scorePercent = 70, nearPercent = 20, offPercent = 10,
        maeCents = 5.0, biasCents = 1.0, previewZones = listOf(Zone.IN_TUNE), audioPath = "$id.m4a", eventId = eventId,
    )

    private fun loaded(
        event: CalendarEvent,
        series: List<EventSeries> = emptyList(),
        programIds: List<Long> = emptyList(),
        pieces: List<Piece> = emptyList(),
        sessions: List<SessionSummary> = emptyList(),
        ui: EventReducer.Ui = EventReducer.Ui(),
    ) = EventReducer.loadedOf(
        event, kinds, series, programIds, pieces, groups = emptyList(), sessions = sessions,
        recordEvent = SessionEvent(event.id, event.title, event.date, event.kind, null), today = today, zone = zone, config = config,
        notesCollapsedLines = 6, ui = ui,
    )

    @Test
    fun `the head says when the event starts and ends - its end of the next day too - and nothing for a whole day`() {
        val head = EventReducer.headerOf(event(start = 17 * 60, duration = 45), KindRules.kindOf(lesson, kinds, config), kinds, emptyList())
        assertEquals(17 * 60, head.startMinutes)
        assertEquals(17 * 60 + 45, head.endMinutes)

        val late = EventReducer.headerOf(event(start = 23 * 60 + 30, duration = 60), KindRules.kindOf(lesson, kinds, config), kinds, emptyList())
        assertEquals(30, late.endMinutes, "past midnight the clock goes on from 00:00")

        val open = EventReducer.headerOf(event(duration = null), KindRules.kindOf(lesson, kinds, config), kinds, emptyList())
        assertNull(open.endMinutes, "without a length only the start")

        val allDay = EventReducer.headerOf(event(start = null, duration = null), KindRules.kindOf(lesson, kinds, config), kinds, emptyList())
        assertNull(allDay.startMinutes)
        assertNull(allDay.endMinutes)
    }

    @Test
    fun `the name of an event is its title or its kind`() {
        val named = EventReducer.headerOf(event(title = "Осенний концерт", kind = performance), KindRules.kindOf(performance, kinds, config), kinds, emptyList())
        assertEquals(EventName.Titled("Осенний концерт"), named.name)
        val own = EventReducer.headerOf(event(kind = KindRef.Custom(7)), KindRules.kindOf(KindRef.Custom(7), kinds, config), kinds, emptyList())
        assertEquals(EventName.OfKind(KindRef.Custom(7), "Оркестр ДК"), own.name)
        val gone = EventReducer.headerOf(event(kind = KindRef.Custom(9)), KindRules.kindOf(KindRef.Custom(9), kinds, config), kinds, emptyList())
        assertEquals(EventName.OfKind(KindRef.OTHER, null), gone.name, "a kind of one's own that is gone is «Другое»")
    }

    @Test
    fun `the repeat is said by the weekday of its first date and its end - a lesson moved by itself keeps it`() {
        val series = EventSeries(
            id = 3, kind = lesson, repeat = Repeat.WEEKLY, firstDate = LocalDate(2026, 9, 21), until = LocalDate(2026, 12, 28),
            laidUntil = LocalDate(2026, 12, 28), startMinutes = 17 * 60, durationMinutes = 45, title = "", place = "",
        )
        // moved to a Wednesday «только этот»: the repeat is still on Mondays
        val moved = event(date = LocalDate(2026, 10, 21), seriesId = 3)
        val head = EventReducer.headerOf(moved, KindRules.kindOf(lesson, kinds, config), kinds, listOf(series))
        assertEquals(RepeatLine(Repeat.WEEKLY, DayOfWeek.MONDAY, LocalDate(2026, 12, 28)), head.repeat)
        assertNull(EventReducer.headerOf(event(), KindRules.kindOf(lesson, kinds, config), kinds, listOf(series)).repeat, "a single event has no repeat line")
    }

    @Test
    fun `a lesson names its teacher and any other kind its place - and an empty one no line at all`() {
        val teacher = EventReducer.headerOf(event(place = "  Анна Сергеевна "), KindRules.kindOf(lesson, kinds, config), kinds, emptyList())
        assertEquals("Анна Сергеевна", teacher.person)
        assertTrue(teacher.teacher)
        val place = EventReducer.headerOf(event(kind = performance, place = "Малый зал"), KindRules.kindOf(performance, kinds, config), kinds, emptyList())
        assertFalse(place.teacher)
        assertEquals("", EventReducer.headerOf(event(place = "   "), KindRules.kindOf(lesson, kinds, config), kinds, emptyList()).person)
    }

    @Test
    fun `the empty notes ask by the kind and the time`() {
        val tomorrow = LocalDate(2026, 10, 25)
        assertEquals(NotesAsk.LESSON, EventReducer.notesAskOf(lesson, tomorrow, today))
        assertEquals(NotesAsk.LESSON, EventReducer.notesAskOf(lesson, today, today), "the same before the lesson and after it")
        assertEquals(NotesAsk.BEFORE, EventReducer.notesAskOf(performance, tomorrow, today))
        assertEquals(NotesAsk.AFTER, EventReducer.notesAskOf(performance, today, today))
        assertNull(EventReducer.notesAskOf(KindRef.BuiltIn(BuiltInKind.REHEARSAL), today, today))
        assertNull(EventReducer.notesAskOf(KindRef.Custom(7), today, today))
    }

    @Test
    fun `the programme is numbered in its order - a scale without its composer - an element that is gone left out`() {
        val groups = listOf(PieceGroup(5, "Пьесы к экзамену", 0))
        val pieces = listOf(
            piece(1, "Концерт ля минор", composer = "А. Вивальди"),
            piece(2, "Гамма", composer = "кто-то", scale = ScaleSpec(Tonic.G, Accidental.NATURAL, ScaleKind.MAJOR, 3)),
            piece(3, "Мелодия", groupId = 5),
        )
        val rows = EventReducer.programOf(listOf(3, 404, 1, 2), pieces, groups)
        assertEquals(listOf(3L, 1L, 2L), rows.map { it.pieceId })
        assertEquals(listOf(1, 2, 3), rows.map { it.number })
        assertEquals(listOf(null, "А. Вивальди", null), rows.map { it.composer })
        assertEquals(SectionRef.Custom(5), rows[0].section)
        assertEquals("Пьесы к экзамену", rows[0].sectionName)
        assertEquals(SectionRef.BuiltIn(PieceSection.SCALES), rows[2].section)
        assertNull(rows[2].sectionName, "a built-in section is a word of the interface")
    }

    @Test
    fun `the records are the event's own - newest first - each named by the event`() {
        val sessions = listOf(
            record(1, "2026-10-24T19:02:00", eventId = 1),
            record(2, "2026-10-24T20:15:00", eventId = 1),
            record(3, "2026-10-24T20:30:00", eventId = 2),
            record(4, "2026-10-24T21:00:00", eventId = null),
        )
        val event = SessionEvent(1, "Осенний концерт", today, performance, null)
        val cards = EventReducer.recordsOf(1, sessions, event, today, zone)
        assertEquals(listOf(2L, 1L), cards.map { it.id })
        assertTrue(cards.all { it.event == event })
    }

    @Test
    fun `the screen of an event - its parts - what it offers and the highlight of a fresh record`() {
        val concert = event(kind = performance, title = "Осенний концерт", start = 18 * 60 + 30, duration = null)
        val empty = loaded(concert)
        assertEquals(listOf(EventSection.CAN_ADD), empty.order)
        assertTrue(empty.performance)
        assertTrue(empty.pinnedAddRecord)
        assertFalse(empty.canAddRecord, "a performance adds its records by its pinned button")
        assertEquals(NotesAsk.AFTER, empty.notesAsk)

        val pieces = listOf(piece(1, "Концерт ля минор", composer = "А. Вивальди"))
        val full = loaded(
            concert, programIds = listOf(1), pieces = pieces, sessions = listOf(record(9, "2026-10-24T19:02:00", eventId = 1)),
            ui = EventReducer.Ui(newRecordId = 9, busyImport = true),
        )
        assertEquals(listOf(EventSection.RECORDS, EventSection.PROGRAM, EventSection.NOTES), full.order)
        assertEquals(9L, full.newRecordId)
        assertTrue(full.busyImport)
        assertEquals(1, full.program.single().number)

        val gone = loaded(concert, ui = EventReducer.Ui(newRecordId = 9))
        assertNull(gone.newRecordId, "a highlight of a record that is not among its records is dropped")

        val lessonScreen = loaded(event())
        assertTrue(lessonScreen.canAddRecord)
        assertFalse(lessonScreen.performance)
    }

    @Test
    fun `a mark goes to the end of the programme and a second press takes it off`() {
        assertEquals(listOf(3L, 1L), EventReducer.toggled(listOf(3), 1))
        assertEquals(listOf(1L), EventReducer.toggled(listOf(3, 1), 3))
        assertEquals(listOf(1L, 3L), EventReducer.toggled(listOf(1), 3))
    }

    @Test
    fun `the sheet of a repeat says lesson - rehearsal - or event`() {
        assertEquals(SeriesWord.LESSON, EventReducer.seriesWordOf(lesson))
        assertEquals(SeriesWord.REHEARSAL, EventReducer.seriesWordOf(KindRef.BuiltIn(BuiltInKind.REHEARSAL)))
        assertEquals(SeriesWord.EVENT, EventReducer.seriesWordOf(performance))
        assertEquals(SeriesWord.EVENT, EventReducer.seriesWordOf(KindRef.BuiltIn(BuiltInKind.OTHER)))
        assertEquals(SeriesWord.EVENT, EventReducer.seriesWordOf(KindRef.Custom(7)))
    }
}
