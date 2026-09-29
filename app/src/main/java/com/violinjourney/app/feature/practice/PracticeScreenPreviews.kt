package com.violinjourney.app.feature.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_MINUTE
import com.violinjourney.app.core.domain.practice.PracticeEntry
import com.violinjourney.app.core.domain.progress.Profile
import com.violinjourney.app.core.domain.progress.ProgressConfig
import com.violinjourney.app.core.domain.progress.Trophy
import com.violinjourney.app.core.domain.session.SessionSummary
import com.violinjourney.app.core.ui.components.AppSheetCard
import com.violinjourney.app.core.ui.components.standInPhoto
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.journey.JourneyWindow
import com.violinjourney.app.navigation.AppBottomBar
import com.violinjourney.app.navigation.TopLevelDestination
import com.violinjourney.app.feature.journey.JourneyWindowCard
import com.violinjourney.app.feature.journey.WindowSample
import com.violinjourney.app.feature.practice.components.CalendarMetrics
import com.violinjourney.app.feature.practice.components.DaySheetContent
import com.violinjourney.app.feature.practice.components.PathRow
import com.violinjourney.app.feature.practice.components.PathSheetContent
import com.violinjourney.app.feature.practice.components.PracticeCalendar
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant

/**
 * The data of the mockups (practice.html, practice-extra.html): Sunday 27 September 2026; the week of the 21st to the 27th —
 * 35, 50, 20, 65, 40, 90 and 45 min, 5 h 45 min in all; a streak of 8 days (20–27, the 19th missed); 24 days of September for
 * 17 h 27 min; with August 47 h 17 min at the violin — level 5, «Гаммы», 2 h 43 min to the 6th; the trophies of 1 and 10 hours.
 */
private object Sample {
    val zone: TimeZone = TimeZone.of("Europe/Moscow")
    val today: LocalDate = LocalDate(2026, 9, 27)
    private val september = mapOf(
        1 to 30, 2 to 50, 3 to 30, 4 to 15, 5 to 55, 6 to 30, 7 to 35, 8 to 50,
        11 to 60, 12 to 30, 13 to 50, 14 to 15, 15 to 55, 16 to 35, 17 to 27, 18 to 40,
        20 to 95, 21 to 35, 22 to 50, 23 to 20, 24 to 65, 25 to 40, 26 to 90, 27 to 45,
    )
    val entries = september.map { (day, minutes) -> entry(LocalDate(2026, 9, day), minutes) }

    /** August, set by hand: brings the whole time to 47 h 17 min. */
    val august = listOf(entry(LocalDate(2026, 8, 10), 600, manual = true), entry(LocalDate(2026, 8, 20), 600, manual = true), entry(LocalDate(2026, 8, 30), 590, manual = true))

    /** The same month after a skip: nothing on Friday, Saturday and today — no streak, dashes. */
    val skipped = entries.filter { it.date.day !in 25..27 }

    val sessions = listOf(session(1, 24, 18), session(2, 24, 19))

    /** A long day of records: the sheet of the day scrolls. */
    val manySessions = (8..21).map { hour -> session(100L + hour, 26, hour) }
    val trophies = listOf(Trophy(1, LocalDate(2026, 8, 10), shown = true), Trophy(10, LocalDate(2026, 8, 20), shown = true))

    private fun entry(date: LocalDate, minutes: Int, manual: Boolean = false) =
        PracticeEntry(date, startedAtEpochMs = 0, durationMs = minutes * MS_PER_MINUTE, manual = manual)

    private fun session(id: Long, day: Int, hour: Int) = SessionSummary(
        id = id, title = null,
        startedAtEpochMs = LocalDate(2026, 9, day).atTime(hour, 0).toInstant(zone).toEpochMilliseconds(),
        durationMs = 2 * MS_PER_MINUTE + 31_000, a4Hz = 440.0, toleranceCents = 8.0, nearCents = 20.0,
        scorePercent = 84, nearPercent = 16, offPercent = 0, maeCents = 5.0, biasCents = -4.0,
        previewZones = listOf(Zone.IN_TUNE, Zone.NEAR, Zone.IN_TUNE, Zone.OFF, Zone.IN_TUNE, Zone.IN_TUNE, Zone.NEAR, Zone.IN_TUNE),
        audioPath = null,
    )

    fun state(
        running: Boolean = false,
        selected: LocalDate? = null,
        entries: List<PracticeEntry> = this.entries + august,
        sheet: PracticeSheet? = selected?.let { PracticeSheet.Day(it) },
        trophies: List<Trophy> = if (entries.isEmpty()) emptyList() else this.trophies,
        name: String = "Аня",
        sessions: List<SessionSummary> = this.sessions,
        month: YearMonth = YearMonth(2026, 9),
    ): PracticeState = PracticeReducer.stateOf(
        entries = entries, sessions = sessions, runningSince = today.takeIf { running }, month = month,
        selectedDate = selected, sheet = sheet, today = today, zone = zone, config = PracticeConfig(),
        trophies = trophies, profile = Profile(name, avatarFile = null),
        avatarPath = null, progressConfig = ProgressConfig(),
    )

    /** 24:18 of «D-dur · 2 октавы» with 6 minutes of its goal left. */
    val timer = PracticeTimer(24 * MS_PER_MINUTE + 18_000, RunningBlockLine("D-dur · 2 октавы", minutesLeft = 6))

    /** The header of [hours] of practice, with every trophy it has crossed seen. */
    fun header(hours: Int, name: String = "Аня"): ProfileHeader {
        val config = ProgressConfig()
        val given = config.trophyHours.filter { it <= hours }.map { Trophy(it, today, shown = true) }
        return ProgressReducer.headerOf(hours * 60 * MS_PER_MINUTE, given, name, avatarPath = null, config)
    }
}

/** The status bar of the mockups over the screen of a phone (practice-extra.html): the window of the home is fitted to what is left. */
private val StatusBar = 24.dp

/**
 * The whole screen with the window of the home of the mockups (at home, enough for Prague); the first run — «Ваша комната».
 * [framed] — as on a phone of the preview's size: the status bar over it and the tab bar under it, as the root of the tabs gives
 * them, so that the scroll window the picture is fitted to is the phone's (without the system's gesture bar).
 */
@Composable
private fun Screen(
    state: PracticeState,
    timer: PracticeTimer? = null,
    window: JourneyWindow? = if (state.hasHistory) WindowSample.enough else WindowSample.firstRun,
    framed: Boolean = false,
) {
    ViolinTheme {
        val screen: @Composable (Modifier) -> Unit = { modifier ->
            PracticeScreen(
                state = state,
                onIntent = {},
                modifier = modifier,
                zone = Sample.zone,
                journeyCard = { look -> window?.let { JourneyWindowCard(it, look, onClick = {}) } },
                timer = { timer },
            )
        }
        if (framed) {
            BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                val landscape = maxWidth > maxHeight
                Column(Modifier.fillMaxSize().padding(top = StatusBar)) {
                    screen(Modifier.weight(1f))
                    AppBottomBar(TopLevelDestination.PRACTICE, onSelect = {}, practiceRunning = state.running, compact = landscape)
                }
            }
        } else {
            screen(Modifier)
        }
    }
}

@Preview(name = "Занятия · обычное: окно 148", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun IdlePreview() = Screen(Sample.state())

@Preview(name = "Занятия · занятие идёт: «Сегодня вместе с ним», подход, штриховка", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun RunningPreview() = Screen(Sample.state(running = true), Sample.timer)

@Preview(name = "Занятия · после пропуска: без чипа, пунктиры, «Ещё не играли»", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun SkippedPreview() = Screen(Sample.state(entries = Sample.skipped + Sample.august))

@Preview(name = "Занятия · первый запуск", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun FirstRunPreview() = Screen(Sample.state(entries = emptyList(), name = ""))

@Preview(name = "Занятия · первое занятие идёт", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun FirstPracticeRunningPreview() = Screen(Sample.state(running = true, entries = emptyList(), name = ""), PracticeTimer(3 * MS_PER_MINUTE + 5_000, block = null))

@Preview(name = "Занятия · загрузка: строка пути держит место, зона пуста", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun LoadingPreview() = Screen(PracticeReducer.loading(Sample.today, PracticeConfig(), ProgressConfig()))

@Preview(name = "Занятия · 360 × 640: окно от остатка первого экрана", locale = "ru", device = "spec:width=360dp,height=640dp")
@Composable
private fun SmallPreview() = Screen(Sample.state(), framed = true)

@Preview(name = "Занятия · 360 × 640, занятие идёт", locale = "ru", device = "spec:width=360dp,height=640dp")
@Composable
private fun SmallRunningPreview() = Screen(Sample.state(running = true), Sample.timer, framed = true)

@Preview(name = "Занятия · 360 × 640, шрифт 1,3: окно — строка с миниатюрой", locale = "ru", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun SmallLargePreview() = Screen(Sample.state(), framed = true)

@Preview(name = "Занятия · 360 × 640, не хватает до Праги: с полосой картинка не помещается — строка", locale = "ru", device = "spec:width=360dp,height=640dp")
@Composable
private fun SmallShortPreview() = Screen(Sample.state(), window = WindowSample.short, framed = true)

@Preview(name = "Занятия · 360 × 640, первый запуск", locale = "ru", device = "spec:width=360dp,height=640dp")
@Composable
private fun SmallFirstRunPreview() = Screen(Sample.state(entries = emptyList(), name = ""), framed = true)

@Preview(name = "Занятия · 412 × 892, «+340» после итога", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun EarnedPreview() = Screen(Sample.state(), window = WindowSample.earned, framed = true)

@Preview(name = "Занятия · landscape 892 × 412", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun LandscapePreview() = Screen(Sample.state(), framed = true)

@Preview(name = "Занятия · landscape 892 × 412, занятие идёт", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun LandscapeRunningPreview() = Screen(Sample.state(running = true), Sample.timer, framed = true)

@Preview(name = "Занятия · landscape 892 × 412, первый запуск", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun LandscapeFirstRunPreview() = Screen(Sample.state(entries = emptyList(), name = ""), framed = true)

@Preview(name = "Занятия · 640 × 360: кнопка 48, окно 120 × 96", locale = "ru", device = "spec:width=640dp,height=360dp")
@Composable
private fun TinyLandscapePreview() = Screen(Sample.state(), framed = true)

@Preview(name = "Занятия · 640 × 360: не хватает, в городе", locale = "ru", device = "spec:width=640dp,height=360dp")
@Composable
private fun TinyLandscapeCityPreview() = Screen(Sample.state(), window = WindowSample.inCity, framed = true)

@Preview(name = "Занятия · de, 360, шрифт 1,3", locale = "de", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun GermanLargePreview() = Screen(Sample.state(), framed = true)

@Preview(name = "Занятия · fr, 360", locale = "fr", device = "spec:width=360dp,height=640dp")
@Composable
private fun FrenchPreview() = Screen(Sample.state(running = true), Sample.timer, framed = true)

// the sheet itself is a window, which a preview does not draw: the ring of the day under it is what is seen here
@Preview(name = "Занятия · под листом дня: кольцо «выбран» у 24-го", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun DaySelectedPreview() = Screen(Sample.state(selected = LocalDate(2026, 9, 24), sheet = null))

/**
 * Every cell of the calendar (events-kinds.html, «Геометрия клетки»): a row a tone — empty, tones 1–4 — plain, today, selected,
 * today and selected; the last row, days to come.
 */
private fun cellsOfEveryKind(): List<CalendarCell?> {
    var day = 0
    fun cell(tone: Int, today: Boolean = false, selected: Boolean = false, future: Boolean = false) = CalendarCell(
        date = LocalDate(2026, 9, ++day), totalMs = tone * 20 * MS_PER_MINUTE, fillLevel = tone, isToday = today, isSelected = selected, isFuture = future,
    )
    val tones = (0..4).flatMap { tone ->
        listOf(cell(tone), cell(tone, today = true), cell(tone, selected = true), cell(tone, today = true, selected = true), null, null, null)
    }
    return tones + listOf(cell(0, future = true), cell(0, future = true), cell(0, future = true), null, null, null, null)
}

@Composable
private fun CalendarPreview(
    cells: List<CalendarCell?> = cellsOfEveryKind(),
    month: YearMonth = YearMonth(2026, 9),
    monthMs: Long = (17 * 60 + 27) * MS_PER_MINUTE,
    monthDays: Int = 24,
    metrics: CalendarMetrics = CalendarMetrics.Portrait,
) = ViolinTheme {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
        PracticeCalendar(
            month = month, cells = cells, canGoForward = month < YearMonth(2026, 9), onMonthBack = {}, onMonthForward = {}, onDaySelected = {},
            currentYear = 2026, monthMs = monthMs, monthDays = monthDays, metrics = metrics,
        )
    }
}

@Preview(name = "Календарь · все клетки, 412", widthDp = 412, locale = "ru")
@Composable
private fun CalendarCellsPreview() = CalendarPreview()

@Preview(name = "Календарь · все клетки, 360: колонка 47", widthDp = 360, locale = "ru")
@Composable
private fun CalendarCellsNarrowPreview() = CalendarPreview()

@Preview(name = "Календарь · landscape: сетка 350 у левого края", widthDp = 596, locale = "ru")
@Composable
private fun CalendarCellsLandscapePreview() = CalendarPreview(metrics = CalendarMetrics.Landscape)

@Preview(name = "Календарь · месяц макета: «Сентябрь», сумма и дни", widthDp = 412, locale = "ru")
@Composable
private fun CalendarMonthPreview() = Sample.state().let { CalendarPreview(it.cells, monthMs = it.summary.monthMs, monthDays = it.summary.monthDays) }

@Preview(name = "Календарь · прошлый год: «Декабрь 2025», без занятий — без строки", widthDp = 412, locale = "ru")
@Composable
private fun CalendarPastYearPreview() = Sample.state(month = YearMonth(2025, 12)).let {
    CalendarPreview(it.cells, month = YearMonth(2025, 12), monthMs = it.summary.monthMs, monthDays = it.summary.monthDays)
}

@Preview(name = "Календарь · август: месяц с ручными днями, стрелка вперёд", widthDp = 412, locale = "ru")
@Composable
private fun CalendarAugustPreview() = Sample.state(month = YearMonth(2026, 8)).let {
    CalendarPreview(it.cells, month = YearMonth(2026, 8), monthMs = it.summary.monthMs, monthDays = it.summary.monthDays)
}

@Composable
private fun DaySheetPreview(day: SelectedDay) = ViolinTheme {
    AppSheetCard { DaySheetContent(day, onIntent = {}, zone = Sample.zone) }
}

@Preview(name = "Лист дня · сегодня", widthDp = 412, locale = "ru")
@Composable
private fun DaySheetTodayPreview() = DaySheetPreview(Sample.state(selected = Sample.today).selected!!)

@Preview(name = "Лист дня · прошлый день с записями", widthDp = 412, locale = "ru")
@Composable
private fun DaySheetPastPreview() = DaySheetPreview(Sample.state(selected = LocalDate(2026, 9, 24)).selected!!)

@Preview(name = "Лист дня · пустой день: «Не занимались», «Добавить»", widthDp = 412, locale = "ru")
@Composable
private fun DaySheetEmptyPreview() = DaySheetPreview(Sample.state(selected = LocalDate(2026, 9, 19)).selected!!)

@Preview(name = "Лист дня · много записей", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun DaySheetManyPreview() = DaySheetPreview(Sample.state(selected = LocalDate(2026, 9, 26), sessions = Sample.manySessions).selected!!)

@Preview(name = "Лист дня · de, 360, шрифт 1,3", widthDp = 360, locale = "de", fontScale = 1.3f)
@Composable
private fun DaySheetGermanPreview() = DaySheetPreview(Sample.state(selected = Sample.today).selected!!)

/** A day of several practices, 12 h 45 min: the widest time there is beside «Изменить» — it gets smaller, never on two lines. */
private val LongDay = SelectedDay(date = LocalDate(2026, 9, 26), isToday = false, totalMs = (12 * 60 + 45) * MS_PER_MINUTE, sessions = emptyList())

@Preview(name = "Лист дня · 360: «12 ч 45 мин» в одну строку", widthDp = 360, locale = "ru")
@Composable
private fun DaySheetLongTimePreview() = DaySheetPreview(LongDay)

@Preview(name = "Лист дня · de, 360: «12 Std. 45 Min.» в одну строку", widthDp = 360, locale = "de")
@Composable
private fun DaySheetGermanLongTimePreview() = DaySheetPreview(LongDay)

@Preview(name = "Лист дня · de, 360, шрифт 1,3: «12 Std. 45 Min.»", widthDp = 360, locale = "de", fontScale = 1.3f)
@Composable
private fun DaySheetGermanLongTimeLargePreview() = DaySheetPreview(LongDay)

@Preview(name = "Лист дня · landscape 892: не шире 640, по центру", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun DaySheetLandscapePreview() = ViolinTheme {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.BottomCenter) {
        AppSheetCard { DaySheetContent(Sample.state(selected = LocalDate(2026, 9, 24)).selected!!, onIntent = {}, zone = Sample.zone) }
    }
}

@Composable
private fun PathRows(content: @Composable () -> Unit) = ViolinTheme {
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Preview(name = "Строка пути · с фото, 15-й уровень, первый запуск", widthDp = 412, heightDp = 260, locale = "ru")
@Composable
private fun PathRowPreview() = PathRows {
    val photo = remember { standInPhoto() }
    PathRow(Sample.state().header, photo = photo, onClick = {})
    PathRow(Sample.header(10_000), photo = null, onClick = {})
    PathRow(Sample.header(0, name = ""), photo = null, onClick = {})
}

@Preview(name = "Строка пути · 360: подпись в две строки", widthDp = 360, heightDp = 120, locale = "ru")
@Composable
private fun PathRowNarrowPreview() = PathRows { PathRow(Sample.state().header, photo = null, onClick = {}) }

@Composable
private fun PathSheetPreview(header: ProfileHeader, withPhoto: Boolean = false) = ViolinTheme {
    val photo = if (withPhoto) remember { standInPhoto() } else null
    AppSheetCard { PathSheetContent(header, photo = photo, onIntent = {}) }
}

@Preview(name = "Мой путь · без имени и фото", widthDp = 412, locale = "ru")
@Composable
private fun PathSheetNoNamePreview() = PathSheetPreview(Sample.state(name = "").header)

@Preview(name = "Мой путь · с именем", widthDp = 412, locale = "ru")
@Composable
private fun PathSheetNamePreview() = PathSheetPreview(Sample.state().header)

@Preview(name = "Мой путь · с фото и именем", widthDp = 412, locale = "ru")
@Composable
private fun PathSheetPhotoPreview() = PathSheetPreview(Sample.state().header, withPhoto = true)

@Preview(name = "Мой путь · трофеев нет: только следующий", widthDp = 412, locale = "ru")
@Composable
private fun PathSheetNoTrophiesPreview() = PathSheetPreview(Sample.state(trophies = emptyList()).header)

@Preview(name = "Мой путь · все 10 трофеев, 15-й уровень", widthDp = 412, locale = "ru")
@Composable
private fun PathSheetAllPreview() = PathSheetPreview(Sample.header(10_000))

@Preview(name = "Мой путь · первый запуск", widthDp = 412, locale = "ru")
@Composable
private fun PathSheetFirstRunPreview() = PathSheetPreview(Sample.header(0, name = ""))

@Preview(name = "Мой путь · de, шрифт 1,3", widthDp = 360, locale = "de", fontScale = 1.3f)
@Composable
private fun PathSheetGermanPreview() = PathSheetPreview(Sample.state().header)
