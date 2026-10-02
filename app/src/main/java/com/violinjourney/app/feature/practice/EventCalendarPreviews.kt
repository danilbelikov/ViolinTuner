package com.violinjourney.app.feature.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.events.CalendarEvent
import com.violinjourney.app.core.domain.events.EventKind
import com.violinjourney.app.core.domain.events.EventReminder
import com.violinjourney.app.core.domain.events.EventSeries
import com.violinjourney.app.core.domain.events.EventsConfig
import com.violinjourney.app.core.domain.events.KindLook
import com.violinjourney.app.core.domain.events.KindRef
import com.violinjourney.app.core.domain.events.KindRules
import com.violinjourney.app.core.domain.events.KindSign
import com.violinjourney.app.core.domain.events.Reminder
import com.violinjourney.app.core.domain.events.Repeat
import com.violinjourney.app.core.domain.events.StoredKind
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_MINUTE
import com.violinjourney.app.core.domain.practice.PracticeEntry
import com.violinjourney.app.core.domain.progress.Profile
import com.violinjourney.app.core.domain.progress.ProgressConfig
import com.violinjourney.app.core.domain.session.SessionSummary
import com.violinjourney.app.core.ui.components.AppSheetCard
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.practice.components.AddEventButtons
import com.violinjourney.app.feature.practice.components.AddEventRow
import com.violinjourney.app.feature.practice.components.CalendarMetrics
import com.violinjourney.app.feature.practice.components.DaySheetContent
import com.violinjourney.app.feature.practice.components.EventsHint
import com.violinjourney.app.feature.practice.components.KindLegend
import com.violinjourney.app.feature.practice.components.MonthGrid
import com.violinjourney.app.feature.practice.components.PracticeCalendar
import com.violinjourney.app.feature.practice.components.ReminderCard
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant

/**
 * The events of the mockups (events-kinds.html, events-views.html): Sunday 27 September 2026, 18:42; the kinds — the built-in four and
 * «Оркестр» (Морская волна, полукруг), «Мастер-класс» (Лёд, молния), «Сольфеджио» (Орхидея, книга); the lessons with Анна Сергеевна on
 * Mondays at 17:00 for 45 minutes; the September and the November of the mockups.
 */
internal object EventSample {
    val zone: TimeZone = TimeZone.of("Europe/Moscow")
    val today: LocalDate = LocalDate(2026, 9, 27)
    val config = EventsConfig()

    val lesson: KindRef = KindRef.BuiltIn(BuiltInKind.LESSON)
    val rehearsal: KindRef = KindRef.BuiltIn(BuiltInKind.REHEARSAL)
    val performance: KindRef = KindRef.BuiltIn(BuiltInKind.PERFORMANCE)
    val other: KindRef = KindRef.BuiltIn(BuiltInKind.OTHER)
    val orchestra: KindRef = KindRef.Custom(9)
    val masterClass: KindRef = KindRef.Custom(10)
    val solfege: KindRef = KindRef.Custom(11)

    /** The kinds in the order of the form: the built-in four, then those of one's own by the alphabet. */
    val kinds: List<EventKind> = KindRules.ordered(
        KindRules.all(
            listOf(
                StoredKind.Own(9, "Оркестр", 1, KindSign.ARC, 1),
                StoredKind.Own(10, "Мастер-класс", 4, KindSign.BOLT, 2),
                StoredKind.Own(11, "Сольфеджио", 3, KindSign.BOOK, 3),
            ),
            config,
        ),
    )

    /** The lessons repeat every week from the 7th of September. */
    val series: List<EventSeries> = listOf(
        EventSeries(1, lesson, Repeat.WEEKLY, LocalDate(2026, 9, 7), until = null, laidUntil = LocalDate(2026, 12, 28), startMinutes = LESSON_AT, durationMinutes = 45, title = "", place = TEACHER),
    )

    private var nextId = 100L

    fun event(
        date: LocalDate,
        kind: KindRef,
        start: Int? = null,
        duration: Int? = null,
        title: String = "",
        place: String = "",
        seriesId: Long? = null,
    ) = CalendarEvent(nextId++, kind, date, start, duration, title, place, notes = "", seriesId = seriesId, detached = false, createdAtEpochMs = nextId)

    private fun lessonOn(date: LocalDate) = event(date, lesson, LESSON_AT, 45, place = TEACHER, seriesId = 1)
    private fun orchestraOn(date: LocalDate) = event(date, orchestra, 11 * 60, 120, place = "ДК «Строитель»")
    private fun solfegeOn(date: LocalDate) = event(date, solfege, 16 * 60, 45)

    private fun september(day: Int) = LocalDate(2026, 9, day)
    private fun november(day: Int) = LocalDate(2026, 11, day)

    /** September (events-kinds.html, the month under «Занятия»): four events on Saturday the 12th, a fourth one a «+». */
    val septemberEvents: List<CalendarEvent> = listOf(
        orchestraOn(september(5)),
        lessonOn(september(7)),
        orchestraOn(september(12)),
        event(september(12), rehearsal, 13 * 60, 90, place = "Малый зал музыкальной школы"),
        lessonOn(september(12)).copy(seriesId = null),
        event(september(12), other, 19 * 60, title = "Замена струн", place = "Мастерская на Мясницкой"),
        event(september(13), performance, 15 * 60, 60, title = "Академический концерт", place = "Малый зал музыкальной школы"),
        lessonOn(september(14)),
        event(september(17), other, 18 * 60, title = "Купить канифоль"),
        orchestraOn(september(19)),
        event(september(19), other, 15 * 60, title = "Прогон перед выходом"),
        lessonOn(september(21)),
        event(september(24), rehearsal, 18 * 60, 60, place = "Малый зал музыкальной школы"),
        orchestraOn(september(26)),
        lessonOn(september(28)),
    )

    /** November (events-kinds.html, 4): seventeen events, four on Saturday the 14th, the qualifying round all day on the 21st. */
    val novemberEvents: List<CalendarEvent> = listOf(
        lessonOn(november(2)),
        solfegeOn(november(5)),
        orchestraOn(november(7)),
        lessonOn(november(9)),
        solfegeOn(november(12)),
        event(november(14), other, title = "Замена струн"),
        orchestraOn(november(14)),
        event(november(14), masterClass, 14 * 60, 90, place = "Консерватория"),
        lessonOn(november(14)).copy(seriesId = null),
        lessonOn(november(16)),
        solfegeOn(november(19)),
        event(november(21), performance, title = "Отборочный тур Международного конкурса юных скрипачей имени Л. Когана", place = "Москва, Малый зал консерватории"),
        orchestraOn(november(21)),
        lessonOn(november(23)),
        solfegeOn(november(26)),
        orchestraOn(november(28)),
        lessonOn(november(30)),
    )

    /** What a day of September took, as in the previews of «Занятия»; the 19th missed. */
    val entries: List<PracticeEntry> = listOf(
        1 to 30, 2 to 50, 3 to 30, 4 to 15, 5 to 55, 6 to 30, 7 to 35, 8 to 50, 11 to 60, 12 to 30, 13 to 50, 14 to 15, 15 to 55,
        16 to 35, 17 to 27, 18 to 40, 20 to 95, 21 to 35, 22 to 50, 23 to 20, 24 to 65, 25 to 40, 26 to 90, 27 to 45,
    ).map { (day, minutes) -> PracticeEntry(september(day), startedAtEpochMs = 0, durationMs = minutes * MS_PER_MINUTE, manual = false) }

    /** A recording made on Saturday the 12th, after the lesson. */
    val sessions: List<SessionSummary> = listOf(
        SessionSummary(
            id = 1, title = null, startedAtEpochMs = moment(september(12), 18, 10).toEpochMilliseconds(), durationMs = 3 * MS_PER_MINUTE + 40_000,
            a4Hz = 440.0, toleranceCents = 8.0, nearCents = 20.0, scorePercent = 84, nearPercent = 16, offPercent = 0, maeCents = 5.0, biasCents = -4.0,
            previewZones = listOf(Zone.IN_TUNE, Zone.NEAR, Zone.IN_TUNE, Zone.IN_TUNE), audioPath = null,
        ),
    )

    fun moment(date: LocalDate, hours: Int, minutes: Int = 0): Instant = date.atTime(hours, minutes).toInstant(zone)

    /** «Занятия» on [month] at [now] with [events] — the sheet of [selected] open, if one is given. */
    fun state(
        events: List<CalendarEvent> = septemberEvents + novemberEvents,
        month: YearMonth = YearMonth(2026, 9),
        selected: LocalDate? = null,
        now: Instant = moment(today, 18, 42),
        running: Boolean = false,
    ): PracticeState = PracticeReducer.stateOf(
        entries = entries, sessions = sessions, runningSince = today.takeIf { running }, month = month, selectedDate = selected,
        sheet = selected?.let { PracticeSheet.Day(it) }, today = today, zone = zone, config = PracticeConfig(), trophies = emptyList(),
        profile = Profile("Аня", avatarFile = null), avatarPath = null, progressConfig = ProgressConfig(),
        events = events, kinds = kinds, series = series, now = now, eventsConfig = config,
    )

    fun reminder(events: List<CalendarEvent>, now: Instant): Reminder = EventReminder.of(events, kinds, now, zone, config)!!

    private const val LESSON_AT = 17 * 60
    const val TEACHER = "Анна Сергеевна"
}

// --- The cell: every tone, today and the selected day, days to come, one to four events (events-kinds.html, 3) -------------------------

private val Marks1 = listOf(KindLook(KindSign.LESSON, 0))
private val Marks2 = listOf(KindLook(KindSign.ARC, 1), KindLook(KindSign.LESSON, 0))
private val Marks3 = listOf(KindLook(KindSign.OTHER, 7), KindLook(KindSign.ARC, 1), KindLook(KindSign.BOLT, 4))

/**
 * A row a tone — none, then tones 1–4: the plain day with one event, today with two, the selected day with three, today and selected
 * with four (three and «+»), a day without events; then days to come — one event, two, the selected one with three, and with four.
 */
private fun cellsOfEveryKind(): List<CalendarCell?> {
    var day = 0
    fun cell(tone: Int, marks: List<KindLook>, more: Boolean = false, today: Boolean = false, selected: Boolean = false, future: Boolean = false) = CalendarCell(
        date = LocalDate(2026, 9, day++ % 28 + 1), totalMs = tone * 20 * MS_PER_MINUTE, fillLevel = tone, isToday = today, isSelected = selected, isFuture = future,
        marks = marks, more = more,
    )
    val tones = (0..4).flatMap { tone ->
        listOf(
            cell(tone, Marks1), cell(tone, Marks2, today = true), cell(tone, Marks3, selected = true), cell(tone, Marks3, more = true, today = true, selected = true),
            cell(tone, emptyList()), null, null,
        )
    }
    val future = listOf(
        cell(0, Marks1, future = true), cell(0, Marks2, future = true), cell(0, Marks3, selected = true, future = true),
        cell(0, Marks3, more = true, future = true), cell(0, Marks3, more = true, selected = true, future = true), cell(0, emptyList(), future = true), null,
    )
    return tones + future
}

@Composable
private fun Cells(width: Dp, metrics: CalendarMetrics = CalendarMetrics.Portrait) = ViolinTheme {
    Box(Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
        MonthGrid(cellsOfEveryKind(), metrics, onDaySelected = {}, modifier = Modifier.width(width))
    }
}

@Preview(name = "Клетка · 412: колонка 54, знаки 10", widthDp = 412, locale = "ru")
@Composable
private fun CellsWidePreview() = Cells(380.dp)

@Preview(name = "Клетка · 360: колонка 47, знаки 9", widthDp = 360, locale = "ru")
@Composable
private fun CellsNarrowPreview() = Cells(328.dp)

@Preview(name = "Клетка · 320: колонка 41, знаки 8, круг меньше 40", widthDp = 320, locale = "ru")
@Composable
private fun CellsNarrowestPreview() = Cells(288.dp)

@Preview(name = "Клетка · landscape: колонка 50, круг 38, метки с 36", widthDp = 382, locale = "ru")
@Composable
private fun CellsLyingPreview() = Cells(350.dp, CalendarMetrics.Landscape)

/** The hardest cell, as large as the matrix of the mockup: the fourth tone, selected, three events and «+». */
@Preview(name = "Клетка · самая трудная: тон 4, выбрана, три события и «+»", widthDp = 120, locale = "ru")
@Composable
private fun HardestCellPreview() = ViolinTheme {
    Box(Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
        val cell = CalendarCell(LocalDate(2026, 9, 26), 90 * MS_PER_MINUTE, fillLevel = 4, isToday = false, isSelected = true, isFuture = false, marks = Marks3, more = true)
        MonthGrid(listOf(cell, null, null, null, null, null, null), CalendarMetrics.Portrait, onDaySelected = {}, modifier = Modifier.width(54.dp * 7))
    }
}

// --- The month: marks, a month to come, the legend and the hint ---------------------------------------------------------------

@Composable
private fun Month(state: PracticeState, metrics: CalendarMetrics = CalendarMetrics.Portrait, legendBeside: Boolean = false) = ViolinTheme {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
        PracticeCalendar(
            month = state.month, cells = state.cells, canGoForward = state.canGoForward, onMonthBack = {}, onMonthForward = {}, onDaySelected = {},
            currentYear = state.today.year, monthMs = state.summary.monthMs, monthDays = state.summary.monthDays, metrics = metrics,
            legend = state.legend, hint = state.eventsHint, monthEvents = state.monthEvents, monthIsFuture = state.monthIsFuture, legendBeside = legendBeside,
        )
    }
}

@Preview(name = "Месяц · сентябрь с метками, «+» у 12-го, легенда", widthDp = 412, locale = "ru")
@Composable
private fun SeptemberPreview() = Month(EventSample.state())

@Preview(name = "Месяц · ноябрь: «17 событий», выбрано 21-е — жирное число будущего дня, 412", widthDp = 412, locale = "ru")
@Composable
private fun NovemberPreview() = Month(EventSample.state(month = YearMonth(2026, 11), selected = LocalDate(2026, 11, 21)))

@Preview(name = "Месяц · ноябрь, 360: знаки 9, легенда в две строки", widthDp = 360, locale = "ru")
@Composable
private fun NovemberNarrowPreview() = Month(EventSample.state(month = YearMonth(2026, 11), selected = LocalDate(2026, 11, 21)))

@Preview(name = "Месяц · ноябрь, 320: знаки 8", widthDp = 320, locale = "ru")
@Composable
private fun NovemberNarrowestPreview() = Month(EventSample.state(month = YearMonth(2026, 11)))

@Preview(name = "Месяц · ноябрь, landscape 892: легенда столбиком справа", widthDp = 596, locale = "ru")
@Composable
private fun NovemberLyingPreview() = Month(EventSample.state(month = YearMonth(2026, 11)), CalendarMetrics.Landscape, legendBeside = true)

@Preview(name = "Месяц · ноябрь, 640 × 360: сетка во всю колонку, легенда под ней", widthDp = 344, locale = "ru")
@Composable
private fun NovemberSmallLyingPreview() = Month(EventSample.state(month = YearMonth(2026, 11)), CalendarMetrics.Landscape)

@Preview(name = "Месяц · будущий без событий: одно название, под сеткой пусто", widthDp = 412, locale = "ru")
@Composable
private fun EmptyFutureMonthPreview() = Month(EventSample.state(month = YearMonth(2027, 2)))

@Preview(name = "Месяц · до первого события: подсказка вместо легенды", widthDp = 412, locale = "ru")
@Composable
private fun HintPreview() = Month(EventSample.state(events = emptyList()))

@Preview(name = "Месяц · подсказка, landscape 892: справа от сетки", widthDp = 596, locale = "ru")
@Composable
private fun HintLyingPreview() = Month(EventSample.state(events = emptyList()), CalendarMetrics.Landscape, legendBeside = true)

@Preview(name = "Месяц · de, 360, шрифт 1,3: «17 Termine», легенда переносится", widthDp = 360, locale = "de", fontScale = 1.3f)
@Composable
private fun NovemberGermanPreview() = Month(EventSample.state(month = YearMonth(2026, 11)))

@Preview(name = "Легенда и подсказка · fr, 360, шрифт 1,3", widthDp = 360, locale = "fr", fontScale = 1.3f)
@Composable
private fun LegendFrenchPreview() = ViolinTheme {
    Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        KindLegend(EventSample.kinds, column = false)
        EventsHint()
    }
}

// --- The sheet of a day with events (events-views.html, 3; events-kinds.html, 4) ------------------------------------------------

@Composable
private fun DaySheet(date: LocalDate, events: List<CalendarEvent> = EventSample.septemberEvents + EventSample.novemberEvents, main: Boolean = false) = ViolinTheme {
    val day = EventSample.state(events = events, month = YearMonth(date.year, date.month), selected = date).selected!!
    AppSheetCard(bottom = if (main) ({ AddEventButtons(onClick = {}) }) else null) {
        DaySheetContent(day, onIntent = {}, zone = EventSample.zone, onAddEvent = { })
    }
}

@Preview(name = "Лист дня · сегодня без событий: «Событие в этот день» пунктиром", widthDp = 412, locale = "ru")
@Composable
private fun TodayNoEventsPreview() = DaySheet(EventSample.today)

@Preview(name = "Лист дня · суббота: четыре события и запись", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun SaturdayPreview() = DaySheet(LocalDate(2026, 9, 12))

@Preview(name = "Лист дня · «Не занимались» и события", widthDp = 412, locale = "ru")
@Composable
private fun MissedDayPreview() = DaySheet(LocalDate(2026, 9, 19))

@Preview(name = "Лист дня · завтра: «Время появится…», урок", widthDp = 412, locale = "ru")
@Composable
private fun TomorrowPreview() = DaySheet(LocalDate(2026, 9, 28))

@Preview(name = "Лист дня · будущий: «весь день» и оркестр, 21 ноября", widthDp = 412, locale = "ru")
@Composable
private fun FutureDayPreview() = DaySheet(LocalDate(2026, 11, 21))

@Preview(name = "Лист дня · будущий без событий: «Событий нет» и главная", widthDp = 412, locale = "ru")
@Composable
private fun FutureEmptyPreview() = DaySheet(LocalDate(2026, 9, 29), main = true)

@Preview(name = "Лист дня · 360, de, шрифт 1,3: многоточия, «Termin an diesem Tag»", widthDp = 360, locale = "de", fontScale = 1.3f)
@Composable
private fun GermanDayPreview() = DaySheet(LocalDate(2026, 11, 21))

@Preview(name = "Лист дня · fr, шрифт 1,3: главная будущего пустого дня", widthDp = 360, locale = "fr", fontScale = 1.3f)
@Composable
private fun FrenchFutureEmptyPreview() = DaySheet(LocalDate(2026, 9, 29), main = true)

@Preview(name = "«Событие в этот день» · пунктирная строка, 320, шрифт 1,3", widthDp = 320, locale = "ru", fontScale = 1.3f)
@Composable
private fun AddEventRowPreview() = ViolinTheme {
    Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp)) { AddEventRow(onClick = {}) }
}

// --- The reminder (events-views.html, 1–2) ----------------------------------------------------------------------------------------

@Composable
private fun Reminders(compact: Boolean = false, lying: Boolean = false, content: List<Reminder>) = ViolinTheme {
    Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        content.forEach { ReminderCard(it, compact = compact, lying = lying, onMore = {}) }
    }
}

private val Tomorrow = LocalDate(2026, 9, 28)

/** Tomorrow's lesson; today's lesson going on; the concert of today with its hall; the qualifying round all day; four tomorrow — «ещё 2». */
private fun remindersOfTheMockups(): List<Reminder> {
    val lesson = EventSample.event(Tomorrow, EventSample.lesson, 17 * 60, 45, place = EventSample.TEACHER)
    val going = EventSample.event(EventSample.today, EventSample.lesson, 17 * 60, 45, place = EventSample.TEACHER)
    val concert = EventSample.event(EventSample.today, EventSample.performance, 18 * 60 + 30, 90, title = "Осенний концерт", place = "Малый зал музыкальной школы")
    val round = EventSample.event(Tomorrow, EventSample.performance, title = "Отборочный тур Международного конкурса юных скрипачей имени Л. Когана", place = "Москва, Малый зал консерватории")
    val four = listOf(
        lesson,
        EventSample.event(Tomorrow, EventSample.orchestra, 11 * 60, 120, place = "ДК «Строитель»"),
        EventSample.event(Tomorrow, EventSample.masterClass, 14 * 60, 90),
        EventSample.event(Tomorrow, EventSample.other, title = "Замена струн"),
    )
    return listOf(
        EventSample.reminder(listOf(lesson), EventSample.moment(EventSample.today, 18, 42)),
        EventSample.reminder(listOf(going), EventSample.moment(EventSample.today, 17, 20)),
        EventSample.reminder(listOf(concert), EventSample.moment(EventSample.today, 14, 10)),
        EventSample.reminder(listOf(round), EventSample.moment(EventSample.today, 18, 42)),
        EventSample.reminder(four, EventSample.moment(EventSample.today, 18, 42)),
    )
}

@Preview(name = "Напоминание · завтра, урок идёт, концерт, весь день, «ещё 2»", widthDp = 412, heightDp = 760, locale = "ru")
@Composable
private fun RemindersPreview() = Reminders(content = remindersOfTheMockups())

@Preview(name = "Напоминание · компактное: одно событие и «ещё 3»", widthDp = 360, heightDp = 640, locale = "ru")
@Composable
private fun CompactRemindersPreview() = Reminders(compact = true, content = remindersOfTheMockups())

@Preview(name = "Напоминание · компактное лёжа: цель 52", widthDp = 280, heightDp = 640, locale = "ru")
@Composable
private fun LyingRemindersPreview() = Reminders(compact = true, lying = true, content = remindersOfTheMockups())

@Preview(name = "Напоминание · вчера за полночь: «Вчера в 23:00 — …», идёт до 01:00", widthDp = 412, locale = "ru")
@Composable
private fun PastMidnightPreview() = Reminders(
    content = listOf(
        EventSample.reminder(
            listOf(EventSample.event(LocalDate(2026, 9, 26), EventSample.performance, 23 * 60, 120, title = "Ночной концерт", place = "Клуб «Синяя птица»")),
            EventSample.moment(EventSample.today, 0, 30),
        ),
    ),
)

@Preview(name = "Напоминание · de, 360, шрифт 1,3", widthDp = 360, heightDp = 900, locale = "de", fontScale = 1.3f)
@Composable
private fun GermanRemindersPreview() = Reminders(content = remindersOfTheMockups())

@Preview(name = "Напоминание · fr, 360, шрифт 1,3, компактное", widthDp = 360, heightDp = 640, locale = "fr", fontScale = 1.3f)
@Composable
private fun FrenchCompactRemindersPreview() = Reminders(compact = true, content = remindersOfTheMockups())

@Preview(name = "Лист дня · landscape 892: суббота с событиями, не шире 640", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun DaySheetLyingPreview() = ViolinTheme {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.BottomCenter) {
        AppSheetCard { DaySheetContent(EventSample.state(selected = LocalDate(2026, 9, 12)).selected!!, onIntent = {}, zone = EventSample.zone, onAddEvent = {}) }
    }
}
