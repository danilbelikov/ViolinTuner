package com.violinjourney.app.feature.events

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.events.CalendarEvent
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
import com.violinjourney.app.core.domain.repertoire.PieceSection
import com.violinjourney.app.core.domain.repertoire.PieceStatus
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.domain.repertoire.Tonic
import com.violinjourney.app.core.domain.repertoire.scale.ScaleKind
import com.violinjourney.app.core.domain.repertoire.scale.ScaleSpec
import com.violinjourney.app.core.domain.session.SessionSummary
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.events.screen.EventReducer
import com.violinjourney.app.feature.events.screen.EventScreen
import com.violinjourney.app.feature.events.screen.EventSheet
import com.violinjourney.app.feature.events.screen.EventSheetCard
import com.violinjourney.app.feature.events.screen.EventState
import com.violinjourney.app.feature.events.screen.SeriesWord
import com.violinjourney.app.feature.repertoire.piece.TakeProblem
import com.violinjourney.app.feature.repertoire.piece.TakeState
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

// The screen of an event (spec 3.36.9; events-views.html 5–6, practice-sheets.html 9) on the data of the mockups: Sunday 27 September
// 2026; the lessons with Анна Сергеевна on Mondays at 17:00 for 45 minutes, until 28 December; «Академический концерт» gone by on the
// 13th, «Осенний концерт» to come on 24 October; the repertoire — Вивальди, Госсек and a scale. Over the tabs: the status bar over it,
// no bar of the tabs; «Изменить» and «Добавить заметку» open the form of the event (EventFormPreviews).

private object Sample {
    val zone: TimeZone = TimeZone.of("Europe/Moscow")
    val today = LocalDate(2026, 9, 27)
    val config = EventsConfig()
    val kinds = KindRules.all(listOf(StoredKind.Own(9, "Оркестр", 1, KindSign.ARC, 1)), config)
    const val TEACHER = "Анна Сергеевна"
    const val SCHOOL = "Малый зал музыкальной школы"

    val lesson = KindRef.BuiltIn(BuiltInKind.LESSON)
    val performance = KindRef.BuiltIn(BuiltInKind.PERFORMANCE)
    val lessons = EventSeries(
        id = 1, kind = lesson, repeat = Repeat.WEEKLY, firstDate = LocalDate(2026, 9, 7), until = LocalDate(2026, 12, 28),
        laidUntil = LocalDate(2026, 12, 28), startMinutes = 17 * 60, durationMinutes = 45, title = "", place = TEACHER,
    )

    private fun piece(id: Long, title: String, composer: String = "", scale: ScaleSpec? = null) = Piece(
        id = id, title = title, composer = composer, key = null, tempoBpm = null, status = PieceStatus.LEARNING, notes = "", createdAtEpochMs = id,
        updatedAtEpochMs = id, section = if (scale != null) PieceSection.SCALES else PieceSection.PIECES, scale = scale,
    )

    val pieces = listOf(
        piece(1, "Концерт ля минор, 1 ч.", "А. Вивальди"),
        piece(2, "Гавот", "Ф. Госсек"),
        piece(3, "G-dur · 3 октавы", scale = ScaleSpec(Tonic.G, Accidental.NATURAL, ScaleKind.MAJOR, 3)),
    )

    fun event(
        id: Long,
        kind: KindRef,
        date: LocalDate,
        start: Int?,
        duration: Int?,
        title: String = "",
        place: String = "",
        notes: String = "",
        seriesId: Long? = null,
    ) = CalendarEvent(id, kind, date, start, duration, title, place, notes, seriesId, detached = false, createdAtEpochMs = id)

    fun record(id: Long, at: LocalDateTime, seconds: Int, eventId: Long, video: Boolean = false) = SessionSummary(
        id = id, title = null, startedAtEpochMs = at.toInstant(zone).toEpochMilliseconds(), durationMs = seconds * 1_000L, a4Hz = 440.0,
        toleranceCents = 8.0, nearCents = 20.0, scorePercent = 80, nearPercent = 15, offPercent = 5, maeCents = 4.0, biasCents = 1.0,
        previewZones = listOf(Zone.IN_TUNE), audioPath = "$id.m4a", videoPath = if (video) "$id.mp4" else null, eventId = eventId,
    )

    fun screen(event: CalendarEvent, program: List<Long> = emptyList(), records: List<SessionSummary> = emptyList(), sheet: EventSheet? = null, mic: Boolean? = true) =
        EventReducer.loadedOf(
            event = event, kinds = kinds, series = listOf(lessons), programIds = program, pieces = pieces, groups = emptyList(), sessions = records,
            recordEvent = SessionEvent(event.id, event.title, event.date, KindRules.resolve(event.kind, kinds), null), today = today, zone = zone,
            config = config, notesCollapsedLines = RepertoireConfig().notesCollapsedLines,
            ui = EventReducer.Ui(sheet = sheet, micPermission = mic),
        )

    /** Tomorrow's lesson: the notes ask «Что задали?», «Что играли» is a title, the records come on its day. */
    val lessonTomorrow = screen(event(2, lesson, LocalDate(2026, 9, 28), 17 * 60, 45, place = TEACHER, seriesId = 1))

    /** Last Monday's lesson: what was set, what was played, and the record made there. */
    val lessonPast = screen(
        event(
            3, lesson, LocalDate(2026, 9, 21), 17 * 60, 45, place = TEACHER, seriesId = 1,
            notes = "Этюд Кайзера № 3 — до конца, деташе у колодки, не зажимать кисть. Гамма G-dur в три октавы: темп 72, " +
                "четвертями, потом восьмыми. Вивальди — первая часть наизусть к 12 октября.",
        ),
        program = listOf(1, 3),
        records = listOf(record(31, LocalDateTime(2026, 9, 21, 17, 32), 214, eventId = 3)),
    )

    /** «Другое» gone by with nothing in it: «Можно добавить» in the place of the three empty parts. */
    val otherEmpty = screen(event(4, KindRef.BuiltIn(BuiltInKind.OTHER), LocalDate(2026, 9, 26), null, null, title = "Замена струн", place = "Мастерская на Мясницкой"))

    /** «Осенний концерт» to come: the programme first, the notes, «Записи появятся с 24 октября». */
    val concertAhead = screen(
        event(5, performance, LocalDate(2026, 10, 24), 18 * 60 + 30, 90, title = "Осенний концерт", place = SCHOOL),
        program = listOf(1, 2),
    )

    /** «Академический концерт» gone by: its records first, «Добавить запись» pinned at the bottom. */
    val concertPast = screen(
        event(6, performance, LocalDate(2026, 9, 13), 15 * 60, 60, title = "Академический концерт", place = SCHOOL, notes = "Как прошло: волновалась в начале, Госсек — чисто."),
        program = listOf(2, 1),
        records = listOf(
            record(61, LocalDateTime(2026, 9, 13, 15, 12), 228, eventId = 6, video = true),
            record(62, LocalDateTime(2026, 9, 13, 15, 20), 141, eventId = 6),
        ),
    )

    /** «Академический концерт» to come — no programme yet: «Что будете играть?». */
    val concertEmpty = screen(event(7, performance, LocalDate(2026, 10, 24), 18 * 60 + 30, null, title = "Осенний концерт", place = SCHOOL))

    val choice = EventSheet.Program(
        sections = EventReducer.choiceOf(pieces, emptyList(), emptyList(), Formats.alphabetical()),
        checked = listOf(2, 1),
    )
}

private val Idle = TakeState.idle(micPermission = true, bars = RepertoireConfig().levelBars)
private val Levels = listOf(0.3f, 0.55f, 0.8f, 0.62f, 0.4f, 0.7f, 0.92f, 0.66f, 0.38f, 0.52f, 0.74f, 0.48f, 0.28f, 0.44f)
private val Recording = TakeState(recording = true, elapsedSeconds = 72, levels = Levels, problem = null, micPermission = true)

/** The status bar of the phone over the screen of the preview. */
private val StatusBar = 24.dp

@Composable
private fun Screen(state: EventState, take: TakeState = Idle) {
    ViolinTheme {
        CompositionLocalProvider(LocalReduceMotion provides true) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(top = StatusBar)) {
                EventScreen(state = state, take = remember { mutableStateOf(take) }, onIntent = {}, zone = Sample.zone)
            }
        }
    }
}

/** A face of the sheets of the screen, at the bottom of the screen it rises over. */
@Composable
private fun Sheet(state: EventState.Loaded) {
    ViolinTheme {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.BottomCenter) {
            EventSheetCard(state)
        }
    }
}

@Preview(name = "Урок завтра: «Что задали?», «Что играли», «Записи появятся с 28 сентября»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun LessonTomorrowPreview() = Screen(Sample.lessonTomorrow)

@Preview(name = "Прошедший урок: заметки, «Что играли», запись урока", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun LessonPastPreview() = Screen(Sample.lessonPast)

@Preview(name = "«Другое» без ничего: «Можно добавить»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun OtherEmptyPreview() = Screen(Sample.otherEmpty)

@Preview(name = "Выступление впереди: программа, «Во сколько сбор…», «Записи появятся с …»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun ConcertAheadPreview() = Screen(Sample.concertAhead)

@Preview(name = "Выступление впереди без программы: «Что будете играть?»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun ConcertEmptyPreview() = Screen(Sample.concertEmpty)

@Preview(name = "Выступление прошло: записи, программа, заметки, «Добавить запись» внизу", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun ConcertPastPreview() = Screen(Sample.concertPast)

@Preview(name = "Выступление прошло · идёт запись: полоса «Идёт дубль» внизу, приглушено то, что увело бы с экрана", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun RecordingPreview() = Screen(Sample.concertPast, take = Recording)

@Preview(name = "Прошедший урок · идёт запись, шумно: зона появилась на время записи", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun LessonRecordingPreview() = Screen(Sample.lessonPast, take = Recording.copy(problem = TakeProblem.TOO_NOISY))

@Preview(name = "Выступление прошло · landscape 892 × 412: слева 372, зона — у низа правой колонки", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun LandscapePreview() = Screen(Sample.concertPast)

@Preview(name = "Выступление прошло · landscape 640 × 360: слева 288 (45 %), кнопка 48", locale = "ru", device = "spec:width=640dp,height=360dp")
@Composable
private fun SmallLandscapePreview() = Screen(Sample.concertPast)

@Preview(name = "Прошедший урок · окно эмулятора лёжа 603 × 308: слева 271", locale = "ru", device = "spec:width=603dp,height=308dp")
@Composable
private fun EmulatorLandscapePreview() = Screen(Sample.lessonPast)

@Preview(name = "Прошедший урок · 360 × 640, шрифт 1,3", locale = "ru", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun SmallPreview() = Screen(Sample.lessonPast)

@Preview(name = "Прошедший урок · de, 360, шрифт 1,3", locale = "de", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun GermanPreview() = Screen(Sample.lessonPast)

@Preview(name = "Лист «Добавить запись»: четыре способа", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun AddRecordPreview() = Sheet(Sample.concertPast.copy(sheet = EventSheet.AddRecord))

@Preview(name = "Лист «Добавить запись» без микрофона: причина и «Разрешить доступ»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun AddRecordNoMicPreview() = Sheet(Sample.concertPast.copy(sheet = EventSheet.AddRecord, micPermission = false))

@Preview(name = "Лист «Добавить запись» без микрофона · de, 360, шрифт 1,3", locale = "de", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun AddRecordNoMicGermanPreview() = Sheet(Sample.concertPast.copy(sheet = EventSheet.AddRecord, micPermission = false))

@Preview(name = "Выбор программы: отмечены Госсек и Вивальди, «Готово · 2»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun ProgramChoicePreview() = Sheet(Sample.concertAhead.copy(sheet = Sample.choice))

@Preview(name = "Выбор программы · репертуар пуст: «Открыть репертуар» и «Не сейчас»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun ProgramEmptyPreview() = Sheet(Sample.concertAhead.copy(sheet = EventSheet.Program(sections = emptyList(), checked = emptyList())))

@Preview(name = "Удаление урока из повтора: «Только этот урок» и «Этот и следующие», обе коралловые", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun DeleteScopePreview() = Sheet(
    Sample.lessonTomorrow.copy(
        sheet = EventSheet.DeleteScope(SeriesWord.LESSON, LocalDate(2026, 9, 28), DayOfWeek.MONDAY, from = LocalDate(2026, 9, 28)),
    ),
)

@Preview(name = "Удаление урока из повтора · de, 360, шрифт 1,3", locale = "de", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun DeleteScopeGermanPreview() = DeleteScopePreview()
