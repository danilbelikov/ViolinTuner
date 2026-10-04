package com.violinjourney.app.feature.events.screen

import com.violinjourney.app.core.domain.events.EditScope
import com.violinjourney.app.core.domain.events.EventKind
import com.violinjourney.app.core.domain.events.EventName
import com.violinjourney.app.core.domain.events.Repeat
import com.violinjourney.app.core.domain.repertoire.SectionRef
import com.violinjourney.app.core.domain.VideoQuality
import com.violinjourney.app.core.recording.video.VideoPick
import com.violinjourney.app.feature.history.HistoryCard
import com.violinjourney.app.feature.live.block.PickerSection
import com.violinjourney.app.feature.repertoire.piece.ImportAction
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/** What the head of the screen of an event says (spec 3.36.9): the chip of its kind, its name, when, its repeat, who or where. */
data class EventHeader(
    val kind: EventKind,
    val name: EventName,
    val date: LocalDate,
    /** Null — «весь день». */
    val startMinutes: Int?,
    /** The minute of the day the event ends at; null without a length, and for «весь день». */
    val endMinutes: Int?,
    /** Null for a single event. */
    val repeat: RepeatLine?,
    /** The teacher of a lesson, the place of anything else; empty — no line at all. */
    val person: String,
    /** [person] is a teacher (a lesson): the line has the sign of a person, not of a place. */
    val teacher: Boolean,
)

/** «Каждую неделю по понедельникам, до 28 декабря» (spec 3.36.9): the step, the weekday of the repeat and its end, if any. */
data class RepeatLine(val repeat: Repeat, val weekday: DayOfWeek, val until: LocalDate?)

/**
 * An element of the programme (spec 3.36.9): its number in it, its name — a scale named as in 3.22 — and under it the composer, or —
 * without one — its section. [sectionName] — of a section of one's own; a built-in one is a word of the interface.
 */
data class ProgramRow(val pieceId: Long, val number: Int, val title: String, val composer: String?, val section: SectionRef, val sectionName: String?)

/** The question an empty card of the notes asks (spec 3.36.9): «Что задали?», «Во сколько сбор, кто аккомпанирует?», «Как прошло?». */
enum class NotesAsk { LESSON, BEFORE, AFTER }

/** The word of the sheet of a repeat (spec 3.36.9, decision 46): «урок», «репетиция», and «событие» for every other kind. */
enum class SeriesWord { LESSON, REHEARSAL, EVENT }

/** The four ways to add a recording, in the order of the sheet — by how often they are used (spec 3.36.9). */
enum class RecordWay { GALLERY, FILE, MIC, CAMERA }

sealed interface EventSheet {
    /** «Добавить запись»: the four ways ([RecordWay]); «Записать звук» says why it sleeps while there is no microphone. */
    data object AddRecord : EventSheet

    /**
     * The choice of the programme (spec 3.36.9): the repertoire by sections — empty, the repertoire is empty — and [checked], the
     * elements marked, in the order they were marked; the new ones go to the end.
     */
    data class Program(val sections: List<PickerSection>, val checked: List<Long>) : EventSheet

    /**
     * «Удалить урок 19 октября?» of an event of a repeat (spec 3.36.9): «Только этот урок · остальные — по понедельникам» and «Этот и
     * следующие · с 19 октября и дальше». [weekday] — of the repeat; [from] — the first event the second answer deletes.
     */
    data class DeleteScope(val word: SeriesWord, val date: LocalDate, val weekday: DayOfWeek, val from: LocalDate) : EventSheet
}

sealed interface EventDialog {
    /** «Удалить «Осенний концерт»?» of a single event (spec 3.36.9): the dialog of deletion of R1. */
    data object DeleteOne : EventDialog
}

sealed interface EventState {
    /** The event is being read: the bar without its actions. */
    data object Loading : EventState

    /** Gone — deleted elsewhere, or an id from nowhere: «Событие не найдено», as a recording's screen says (decision 57). */
    data object NotFound : EventState

    data class Loaded(
        val eventId: Long,
        val header: EventHeader,
        /** A performance: its pieces are «Программа», and its deletion says «Заметки и программа удалятся». */
        val performance: Boolean,
        /** The parts in their order ([EventSections]); «Записи появятся с …» says [EventHeader.date]. */
        val order: List<EventSection>,
        /** «Добавить запись» pinned at the bottom: a performance from its day on. */
        val pinnedAddRecord: Boolean,
        val notes: String,
        /** What the empty card of the notes asks; null — it asks nothing. */
        val notesAsk: NotesAsk?,
        /** After this many lines the notes fold (spec 3.15). */
        val notesCollapsedLines: Int,
        val program: List<ProgramRow>,
        /** Newest first, as the cards of «Записи». */
        val records: List<HistoryCard>,
        /** «Можно добавить» offers «Запись — звук или видео»: not to a performance, whose pinned button does it. */
        val canAddRecord: Boolean,
        /** The recording added a moment ago, while it is highlighted. */
        val newRecordId: Long? = null,
        /** False — the microphone was refused: «Записать звук» says why. Null — not known yet. */
        val micPermission: Boolean? = null,
        /**
         * A video or a sound from a file is on its way in — of this event or another's — or a video's failure waits for its screen:
         * «Добавить запись» sleeps with a spinner (D37). A sound's failure does not hold it (review of stage 98a).
         */
        val busyImport: Boolean = false,
        val sheet: EventSheet? = null,
        val dialog: EventDialog? = null,
    ) : EventState
}

sealed interface EventIntent {
    data object BackClicked : EventIntent

    /** «Изменить»: the form of the event (the second half of stage 98). */
    data object EditClicked : EventIntent

    /** «Добавить заметку»: the form at its notes (the second half of stage 98). */
    data object AddNotesClicked : EventIntent

    /** «Удалить…» of «⋯»: the dialog of a single event, the sheet of a repeat's. */
    data object DeleteClicked : EventIntent

    /** «Удалить» of the dialog — [scope] null — or an answer of the sheet of a repeat. */
    data class DeleteConfirmed(val scope: EditScope?) : EventIntent

    /** «Отмена» of the dialog. */
    data object DeleteDismissed : EventIntent

    /** «+ Добавить» of the programme and «Программу / Что играли — из репертуара» of «Можно добавить»: the choice. */
    data object ProgramAddClicked : EventIntent

    data class ProgramToggled(val pieceId: Long) : EventIntent

    /** «Готово · N» of the choice. */
    data object ProgramDone : EventIntent

    /** The cross of a row of the programme: away at once, no question (spec 3.36.9). */
    data class ProgramRemoved(val pieceId: Long) : EventIntent

    /** «Открыть репертуар» of the empty choice: the tab «Репертуар». */
    data object OpenRepertoireClicked : EventIntent

    data class PieceClicked(val pieceId: Long) : EventIntent

    data class RecordClicked(val sessionId: Long) : EventIntent

    /** «Добавить запись» pinned, «+ Добавить» of «Записи», «Запись — звук или видео» of «Можно добавить»: the sheet of the ways. */
    data object AddRecordClicked : EventIntent

    /** A row of «Добавить запись»: the sheet goes and the way begins. */
    data class AddRecordWay(val way: RecordWay) : EventIntent

    /** «Разрешить доступ» of «Записать звук»: the system asks, or its settings open; the recording does not begin by itself. */
    data object GrantMicClicked : EventIntent

    /** Told by the route on every return and after the system's question. */
    data class MicPermissionChanged(val granted: Boolean) : EventIntent

    /** «стоп» of the bar of the recording. */
    data object RecordStopClicked : EventIntent

    /** The system camera came back; [saved] is false when the person backed out. */
    data class VideoShotFinished(val saved: Boolean) : EventIntent

    /** The system picker of videos came back — its file there already, or still to be made by the library; null — nothing was picked. */
    data class VideoPicked(val pick: VideoPick?) : EventIntent

    /** The system picker of files came back with a sound; null — nothing was picked. */
    data class SoundPicked(val uri: String?) : EventIntent

    /** A button of the sheet of a file on its way in. */
    data class Import(val action: ImportAction) : EventIntent

    /** A sheet swiped down, tapped beside or closed with «назад»: only hidden — nothing is saved, deleted or begun. */
    data object SheetHidden : EventIntent
}

sealed interface EventEffect {
    data object Close : EventEffect

    data class OpenForm(val eventId: Long, val focusNotes: Boolean) : EventEffect

    data class OpenPiece(val pieceId: Long) : EventEffect

    data class OpenSession(val sessionId: Long) : EventEffect

    /** The tab «Репертуар» (spec 3.36.1). */
    data object OpenRepertoire : EventEffect

    data object RequestMicPermission : EventEffect

    /** [filePath] is where the video has to be written; the route turns it into a content uri. [quality] — «Качество видео» (spec 3.19). */
    data class LaunchVideoCamera(val filePath: String, val quality: VideoQuality) : EventEffect

    data object PickVideo : EventEffect

    /** The system picker of files, the one of a backing (spec 3.32, 5.28). */
    data object PickSound : EventEffect

    /** A shot that did not become a recording, on its way to the system sheet; [filePath] is under `cache/share/`. */
    data class ShareVideo(val filePath: String) : EventEffect

    /** A recording stopped by the person had not a single note in it. */
    data object ShowNoNotes : EventEffect
}
