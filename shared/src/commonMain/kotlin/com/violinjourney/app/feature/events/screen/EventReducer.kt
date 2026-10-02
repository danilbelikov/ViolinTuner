package com.violinjourney.app.feature.events.screen

import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.events.CalendarEvent
import com.violinjourney.app.core.domain.events.EventKind
import com.violinjourney.app.core.domain.events.EventName
import com.violinjourney.app.core.domain.events.EventRules
import com.violinjourney.app.core.domain.events.EventSeries
import com.violinjourney.app.core.domain.events.EventsConfig
import com.violinjourney.app.core.domain.events.KindRef
import com.violinjourney.app.core.domain.events.KindRules
import com.violinjourney.app.core.domain.events.SessionEvent
import com.violinjourney.app.core.domain.repertoire.Piece
import com.violinjourney.app.core.domain.repertoire.PieceGroup
import com.violinjourney.app.core.domain.repertoire.PieceRules
import com.violinjourney.app.core.domain.repertoire.SectionRef
import com.violinjourney.app.core.domain.session.SessionSummary
import com.violinjourney.app.feature.history.HistoryReducer
import com.violinjourney.app.feature.live.block.BlockReducer
import com.violinjourney.app.feature.live.block.PickerPiece
import com.violinjourney.app.feature.live.block.PickerSection
import com.violinjourney.app.feature.live.block.TodayMark
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** An event and what belongs to it → the screen of the event (spec 3.35, 3.36.9). Pure: the day, the zone and what is stored come from outside. */
object EventReducer {
    /** What only the screen decides: the sheet, the dialog, the recording highlighted, the microphone, an import under way. */
    data class Ui(
        val sheet: EventSheet? = null,
        val dialog: EventDialog? = null,
        val newRecordId: Long? = null,
        val micPermission: Boolean? = null,
        val busyImport: Boolean = false,
    )

    fun loadedOf(
        event: CalendarEvent,
        kinds: List<EventKind>,
        series: List<EventSeries>,
        programIds: List<Long>,
        pieces: List<Piece>,
        groups: List<PieceGroup>,
        sessions: List<SessionSummary>,
        recordEvent: SessionEvent?,
        today: LocalDate,
        zone: TimeZone,
        config: EventsConfig,
        notesCollapsedLines: Int,
        ui: Ui = Ui(),
    ): EventState.Loaded {
        val kind = KindRules.kindOf(event.kind, kinds, config)
        val performance = EventSections.isPerformance(kind.ref)
        val program = programOf(programIds, pieces, groups)
        val records = recordsOf(event.id, sessions, recordEvent, today, zone)
        val layout = EventSections.of(
            kind.ref, event.date, today,
            notesEmpty = event.notes.isBlank(), programEmpty = program.isEmpty(), recordsEmpty = records.isEmpty(),
        )
        return EventState.Loaded(
            eventId = event.id,
            header = headerOf(event, kind, kinds, series),
            performance = performance,
            order = layout.order,
            pinnedAddRecord = layout.pinnedAddRecord,
            notes = event.notes,
            notesAsk = notesAskOf(kind.ref, event.date, today),
            notesCollapsedLines = notesCollapsedLines,
            program = program,
            records = records,
            canAddRecord = !performance,
            newRecordId = ui.newRecordId?.takeIf { id -> records.any { it.id == id } },
            micPermission = ui.micPermission,
            busyImport = ui.busyImport,
            sheet = ui.sheet,
            dialog = ui.dialog,
        )
    }

    /**
     * «21 сентября, понедельник · 17:00–17:45», the repeat of a repeat's event — its step, its weekday (that of its first date, which a
     * lesson moved «only this» does not change) and its end — and the teacher of a lesson or the place of anything else (spec 3.36.9).
     */
    fun headerOf(event: CalendarEvent, kind: EventKind, kinds: List<EventKind>, series: List<EventSeries>): EventHeader {
        val repeat = event.seriesId?.let { id -> series.firstOrNull { it.id == id } }
        return EventHeader(
            kind = kind,
            name = EventName.of(event.title, event.kind, kinds),
            date = event.date,
            startMinutes = event.startMinutes,
            endMinutes = event.startMinutes?.let { start -> event.durationMinutes?.let { (start + it) % EventRules.MINUTES_PER_DAY } },
            repeat = repeat?.let { RepeatLine(it.repeat, it.firstDate.dayOfWeek, it.until) },
            person = event.place.trim(),
            teacher = kind.ref == KindRef.BuiltIn(BuiltInKind.LESSON),
        )
    }

    /**
     * What the empty card of the notes asks (spec 3.36.9): a lesson — «Что задали?», before and after alike; a performance before its
     * day — «Во сколько сбор, кто аккомпанирует?», from its day on — «Как прошло?»; any other kind asks nothing.
     */
    fun notesAskOf(kind: KindRef, date: LocalDate, today: LocalDate): NotesAsk? = when (kind) {
        KindRef.BuiltIn(BuiltInKind.LESSON) -> NotesAsk.LESSON
        KindRef.BuiltIn(BuiltInKind.PERFORMANCE) -> if (date > today) NotesAsk.BEFORE else NotesAsk.AFTER
        else -> null
    }

    /** The programme in its order, numbered from one; an element of the repertoire that is gone is not in it. */
    fun programOf(programIds: List<Long>, pieces: List<Piece>, groups: List<PieceGroup>): List<ProgramRow> {
        val byId = pieces.associateBy { it.id }
        return programIds.mapNotNull(byId::get).mapIndexed { index, piece ->
            val section = PieceRules.sectionOf(piece, groups)
            ProgramRow(
                pieceId = piece.id,
                number = index + 1,
                title = piece.title,
                composer = piece.composer.trim().takeIf { it.isNotEmpty() && piece.scale == null },
                section = section,
                sectionName = (section as? SectionRef.Custom)?.let { custom -> groups.firstOrNull { it.id == custom.groupId }?.name },
            )
        }
    }

    /** The recordings of the event as the cards of «Записи», newest first; named by the event until they are given names of their own. */
    fun recordsOf(eventId: Long, sessions: List<SessionSummary>, event: SessionEvent?, today: LocalDate, zone: TimeZone) = sessions
        .filter { it.eventId == eventId }
        .sortedWith(compareByDescending<SessionSummary> { it.startedAtEpochMs }.thenByDescending { it.id })
        .map { HistoryReducer.cardOf(it, today, zone, event = event) }

    /**
     * The choice of the programme (spec 3.36.9): the repertoire as «Что играем» lists it — its sections in their order, the elements of
     * each by their latest activity, the composer under the title — without the marks of today: they are about the blocks of Live.
     */
    fun choiceOf(pieces: List<Piece>, groups: List<PieceGroup>, sessions: List<SessionSummary>, byName: Comparator<String>): List<PickerSection> =
        BlockReducer.shelfOf(pieces, groups, sessions, byName).sections.map { section ->
            PickerSection(
                ref = section.ref,
                name = section.name,
                doneToday = 0,
                pieces = section.pieces.map { piece ->
                    PickerPiece(piece.id, piece.title, piece.composer.takeIf { it.isNotBlank() && piece.scale == null }, TodayMark.None)
                },
            )
        }

    /** The element [pieceId] marked or unmarked: a new mark goes to the end — the order of the programme is the order of the marks. */
    fun toggled(checked: List<Long>, pieceId: Long): List<Long> = if (pieceId in checked) checked - pieceId else checked + pieceId

    /** The word of the sheet of a repeat: «урок», «репетиция», «событие» of any other kind (decision 46). */
    fun seriesWordOf(kind: KindRef): SeriesWord = when (kind) {
        KindRef.BuiltIn(BuiltInKind.LESSON) -> SeriesWord.LESSON
        KindRef.BuiltIn(BuiltInKind.REHEARSAL) -> SeriesWord.REHEARSAL
        else -> SeriesWord.EVENT
    }
}
