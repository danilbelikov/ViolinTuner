package com.violinjourney.app.feature.practice

import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.events.CalendarEvent
import com.violinjourney.app.core.domain.events.EventKind
import com.violinjourney.app.core.domain.events.EventName
import com.violinjourney.app.core.domain.events.EventSeries
import com.violinjourney.app.core.domain.events.EventsConfig
import com.violinjourney.app.core.domain.events.KindLook
import com.violinjourney.app.core.domain.events.KindRef
import com.violinjourney.app.core.domain.events.KindRules
import com.violinjourney.app.core.domain.events.KindSign
import com.violinjourney.app.core.domain.events.ReminderDay
import com.violinjourney.app.core.domain.events.Repeat
import com.violinjourney.app.core.domain.events.StoredKind
import com.violinjourney.app.core.domain.events.TestEvents.LESSON
import com.violinjourney.app.core.domain.events.TestEvents.OTHER
import com.violinjourney.app.core.domain.events.TestEvents.PERFORMANCE
import com.violinjourney.app.core.domain.events.TestEvents.at as clock
import com.violinjourney.app.core.domain.events.TestEvents.event
import com.violinjourney.app.core.domain.events.TestEvents.moment
import com.violinjourney.app.core.domain.events.TestEvents.series
import com.violinjourney.app.core.domain.practice.PieceBlock
import com.violinjourney.app.core.domain.practice.PracticeBlocks
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_MINUTE
import com.violinjourney.app.core.domain.practice.PracticeEntry
import com.violinjourney.app.core.domain.practice.RunningPractice
import com.violinjourney.app.core.domain.progress.Profile
import com.violinjourney.app.core.domain.progress.ProgressConfig
import com.violinjourney.app.core.domain.progress.Trophy
import com.violinjourney.app.core.domain.session.SessionSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.time.Instant
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant

class PracticeReducerTest {
    private val config = PracticeConfig()
    private val intonation = IntonationConfig()
    private val zone: TimeZone = TimeZone.of("Europe/Moscow")
    private val today: LocalDate = LocalDate(2026, 9, 17)
    private val eventsConfig = EventsConfig()

    /** The kinds of the mockups (events-kinds.html): the built-in four, «Оркестр» (Морская волна, полукруг), «Мастер-класс» (Лёд, молния). */
    private val orchestra: KindRef = KindRef.Custom(9)
    private val masterClass: KindRef = KindRef.Custom(10)
    private val kinds: List<EventKind> = KindRules.all(
        listOf(StoredKind.Own(9, "Оркестр", 1, KindSign.ARC, 1), StoredKind.Own(10, "Мастер-класс", 4, KindSign.BOLT, 2)), eventsConfig,
    )

    private fun entry(day: Int, minutes: Int) = PracticeEntry(
        date = LocalDate(2026, 9, day), startedAtEpochMs = 0, durationMs = minutes * MS_PER_MINUTE, manual = false,
    )

    private fun epoch(dateTime: String) = LocalDateTime.parse(dateTime).toInstant(zone).toEpochMilliseconds()

    private fun session(id: Long, startedAt: String) = SessionSummary(
        id = id, title = null, startedAtEpochMs = epoch(startedAt), durationMs = 60_000, a4Hz = 440.0,
        toleranceCents = 8.0, nearCents = 20.0, scorePercent = 80, nearPercent = 15, offPercent = 5,
        maeCents = 5.0, biasCents = -2.0, previewZones = listOf(Zone.IN_TUNE), audioPath = null,
    )

    private fun state(
        entries: List<PracticeEntry> = listOf(entry(1, 30), entry(4, 100), entry(16, 50), entry(17, 45)),
        sessions: List<SessionSummary> = emptyList(),
        runningSince: LocalDate? = null,
        month: YearMonth = YearMonth(2026, 9),
        selected: LocalDate? = null,
        underBackingIds: Set<Long> = emptySet(),
        events: List<CalendarEvent> = emptyList(),
        kinds: List<EventKind> = this.kinds,
        series: List<EventSeries> = emptyList(),
        now: Instant = today.atStartOfDayIn(zone),
    ) = PracticeReducer.stateOf(
        entries, sessions, runningSince, month, selected, sheet = null, today, zone, config,
        trophies = emptyList(), profile = Profile.EMPTY, avatarPath = null, progressConfig = ProgressConfig(), underBackingIds = underBackingIds,
        events = events, kinds = kinds, series = series, now = now, eventsConfig = eventsConfig,
    )

    @Test
    fun `the card of the gift counts what is left from the real time at the violin`() {
        // an empty day set to 12 h gives the trophies of 1 h and 10 h at once (spec 3.36.3): «Канифоль», then «Колок», each with
        // «Струна · 50 ч · ещё 38 ч» — what is left of the 12 h played, not the whole mark
        val table = listOf(Trophy(1, today, shown = false), Trophy(10, today, shown = false))
        fun giftOf(trophies: List<Trophy>) = PracticeReducer.stateOf(
            listOf(entry(16, 12 * 60)), emptyList(), null, YearMonth(2026, 9), null, sheet = null, today, zone, config,
            trophies = trophies, profile = Profile.EMPTY, avatarPath = null, progressConfig = ProgressConfig(),
        ).gift
        val string = NextTrophy(hours = 50, index = 2, remainingMs = 38 * 60 * MS_PER_MINUTE)
        val rosin = giftOf(table)
        assertEquals(1, rosin?.hours)
        assertEquals(string, rosin?.next)
        val peg = giftOf(table.map { if (it.hours == 1) it.copy(shown = true) else it })
        assertEquals(10, peg?.hours)
        assertEquals(string, peg?.next)
    }

    @Test
    fun `calendar cells carry fill level - today - selection and future flags`() {
        val state = state(selected = LocalDate(2026, 9, 16))
        assertEquals(35, state.cells.size)
        assertNull(state.cells[0])
        val day4 = state.cells.filterNotNull().single { it.date.day == 4 }
        assertEquals(4, day4.fillLevel)
        assertFalse(day4.isToday)
        val day16 = state.cells.filterNotNull().single { it.date.day == 16 }
        assertTrue(day16.isSelected && !day16.isToday)
        val day17 = state.cells.filterNotNull().single { it.date.day == 17 }
        assertEquals(3, day17.fillLevel)
        // spec 3.36.2: today is no longer selected by default — only the day whose sheet is open
        assertTrue(day17.isToday && !day17.isSelected && !day17.isFuture)
        val day18 = state.cells.filterNotNull().single { it.date.day == 18 }
        assertEquals(0, day18.fillLevel)
        assertTrue(day18.isFuture)
        assertEquals(0, state.cells.filterNotNull().single { it.date.day == 3 }.fillLevel)
    }

    @Test
    fun `by default no day is selected - not even today`() {
        val state = state()
        assertNull(state.selected)
        assertTrue(state.cells.filterNotNull().none { it.isSelected })
        assertNull(PracticeReducer.loading(today, config, ProgressConfig()).selected)
    }

    @Test
    fun `the selected day is the day of the open sheet with its time`() {
        val past = state(selected = LocalDate(2026, 9, 4)).selected!!
        assertEquals(LocalDate(2026, 9, 4), past.date)
        assertEquals(100 * MS_PER_MINUTE, past.totalMs)
        assertFalse(past.isToday)
        val now = state(selected = today)
        assertTrue(now.selected!!.isToday)
        assertEquals(45 * MS_PER_MINUTE, now.selected!!.totalMs)
        assertEquals(listOf(today), now.cells.filterNotNull().filter { it.isSelected }.map { it.date })
        assertEquals(0L, state(selected = LocalDate(2026, 9, 3)).selected!!.totalMs, "a day without practice has a sheet too")
    }

    @Test
    fun `the month counts its days with practice`() {
        assertEquals(4, state().summary.monthDays)
        assertEquals(0, state(month = YearMonth(2026, 8)).summary.monthDays)
        assertEquals(3, state(entries = listOf(entry(1, 30), entry(4, 100), entry(16, 50), entry(16, 10))).summary.monthDays)
    }

    @Test
    fun `summary and today follow the entries`() {
        val state = state()
        assertEquals(45 * MS_PER_MINUTE, state.todayMs)
        // the week of the 14th to the 20th: the 16th and the 17th, Wednesday and Thursday
        val weekDays = listOf(0L, 0L, 50L, 45L, 0L, 0L, 0L).map { it * MS_PER_MINUTE }
        assertEquals(
            PracticeSummary(weekMs = 95 * MS_PER_MINUTE, monthMs = 225 * MS_PER_MINUTE, streakDays = 2, weekDaysMs = weekDays, monthDays = 4),
            state.summary,
        )
        assertTrue(state.hasHistory)
        assertFalse(state.loading)
    }

    @Test
    fun `the state knows only the day a practice began on - its clock is a flow of its own`() {
        assertFalse(state().running)
        assertNull(state().runningSince)
        val yesterday = LocalDate(2026, 9, 16)
        assertTrue(state(runningSince = today).running)
        assertEquals(today, state(runningSince = today).runningSince)
        assertTrue(state(runningSince = yesterday).running, "a practice left running past midnight still runs")
        assertEquals(yesterday, state(runningSince = yesterday).runningSince)
        assertFalse(PracticeReducer.loading(today, config, ProgressConfig()).running)
    }

    @Test
    fun `only a practice begun today hatches today and adds to it - and only to time saved today`() {
        val yesterday = LocalDate(2026, 9, 16)
        val none = state()
        assertFalse(none.runningToday, "nothing runs")
        assertFalse(none.withRunningToday)

        // begun at 23:50 and still running after midnight: it belongs to yesterday
        val lastNight = state(runningSince = yesterday)
        assertFalse(lastNight.runningToday, "no hatch in the new day")
        assertFalse(lastNight.withRunningToday, "no «Сегодня вместе с ним» although today has 45 minutes")

        // begun today, nothing saved today yet: the bar is hatched, there is nothing to add it to
        val first = state(entries = listOf(entry(16, 50)), runningSince = today)
        assertTrue(first.runningToday)
        assertFalse(first.withRunningToday, "today has nothing saved")

        // begun today and today has 45 minutes saved: both
        val more = state(runningSince = today)
        assertTrue(more.runningToday)
        assertTrue(more.withRunningToday)

        assertFalse(PracticeReducer.loading(today, config, ProgressConfig()).runningToday)
    }

    @Test
    fun `the state carries the day it is and the floor of the week bars`() {
        assertEquals(today, state().today)
        assertEquals(config.fillLevelMinutes.last(), state().weekFloorMinutes)
    }

    @Test
    fun `loading has an empty week of seven days and knows today`() {
        val loading = PracticeReducer.loading(today, config, ProgressConfig())
        assertTrue(loading.loading)
        assertEquals(List(7) { 0L }, loading.summary.weekDaysMs)
        assertEquals(today, loading.today)
        assertNull(loading.runningSince)
        assertEquals(config.fillLevelMinutes.last(), loading.weekFloorMinutes)
    }

    @Test
    fun `no entries is the empty state`() {
        val state = state(entries = emptyList())
        assertFalse(state.hasHistory)
        assertEquals(PracticeSummary(0, 0, 0, weekDaysMs = List(7) { 0L }, monthDays = 0), state.summary)
        assertNull(state.selected)
    }

    /** Forward to max(the current month + 12, the month of the farthest event) (spec 5.28, 3.36.9; was: not past the current month). */
    @Test
    fun `the calendar goes forward twelve months and further to the month of the farthest event`() {
        assertTrue(state().canGoForward, "September 2026: a year ahead is open")
        assertTrue(state(month = YearMonth(2027, 8)).canGoForward)
        assertFalse(state(month = YearMonth(2027, 9)).canGoForward, "the current month + 12 is the last")
        assertTrue(state(month = YearMonth(2026, 8)).canGoForward)
        assertEquals(0L, state(month = YearMonth(2026, 8)).summary.monthMs)
        val far = listOf(event(1, LocalDate(2027, 11, 5)))
        assertTrue(state(month = YearMonth(2027, 9), events = far).canGoForward, "an event further on takes the calendar to its month")
        assertFalse(state(month = YearMonth(2027, 11), events = far).canGoForward)
    }

    @Test
    fun `selected day lists its sessions newest first`() {
        val sessions = listOf(
            session(1, "2026-09-16T10:00:00"), session(2, "2026-09-16T23:30:00"), session(3, "2026-09-17T09:00:00"),
        )
        val state = state(sessions = sessions, selected = LocalDate(2026, 9, 16))
        val day = state.selected!!
        assertEquals(listOf(2L, 1L), day.sessions.map { it.id })
        assertEquals(50 * MS_PER_MINUTE, day.totalMs)
        assertFalse(day.isToday)
        assertEquals(listOf(3L), state(sessions = sessions, selected = today).selected!!.sessions.map { it.id })
    }

    @Test
    fun `a record of the day made under a backing carries its sign as in «Записи»`() {
        val sessions = listOf(session(1, "2026-09-17T08:00:00"), session(2, "2026-09-17T09:00:00"))
        val cards = state(sessions = sessions, selected = today, underBackingIds = setOf(1L)).selected!!.sessions
        assertEquals(listOf(2L to false, 1L to true), cards.map { it.id to it.underBacking })
    }

    @Test
    fun `summary sheet rounds to the minute and trims down to five minutes`() {
        val sheet = PracticeReducer.summarySheet(startedAtEpochMs = 1_000, actualMs = 47 * MS_PER_MINUTE + 30_000, config)
        assertEquals(48, sheet.minutes)
        assertEquals(5, sheet.minMinutes)
        assertEquals(48, sheet.maxMinutes)
        assertFalse(sheet.edited)
        assertEquals(47 * MS_PER_MINUTE + 30_000, PracticeReducer.durationToSave(sheet))

        val down = PracticeReducer.step(sheet, -1, config)
        assertEquals(43, down.minutes)
        assertTrue(down.edited)
        assertEquals(43 * MS_PER_MINUTE, PracticeReducer.durationToSave(down))
        assertEquals(48, PracticeReducer.step(down, +2, config).minutes)
        assertEquals(5, PracticeReducer.step(sheet, -20, config).minutes)
    }

    @Test
    fun `a stepper back where it started saves the exact time again`() {
        val sheet = PracticeReducer.summarySheet(startedAtEpochMs = 1_000, actualMs = 47 * MS_PER_MINUTE + 40_000, config)
        val back = PracticeReducer.step(PracticeReducer.step(sheet, -1, config), +1, config)
        assertEquals(48, back.minutes)
        assertFalse(back.edited)
        assertEquals(47 * MS_PER_MINUTE + 40_000, PracticeReducer.durationToSave(back))
    }

    @Test
    fun `a trim never saves more than was played`() {
        val sheet = PracticeReducer.summarySheet(startedAtEpochMs = 1_000, actualMs = 47 * MS_PER_MINUTE + 40_000, config)
        // the rounding of 47:40 is 48 minutes: as a trim it is still the time played, not twenty seconds more
        assertEquals(47 * MS_PER_MINUTE + 40_000, PracticeReducer.durationToSave(sheet.copy(edited = true)))
    }

    @Test
    fun `the edit sheet remembers the minutes it opened with`() {
        val sheet = PracticeReducer.editSheet(today, 47 * MS_PER_MINUTE + 40_000, config)
        assertEquals(48, sheet.initialMinutes)
        assertEquals(48, PracticeReducer.step(sheet, +1, config).initialMinutes)
    }

    @Test
    fun `a practice shorter than five minutes cannot be trimmed`() {
        val sheet = PracticeReducer.summarySheet(startedAtEpochMs = 1_000, actualMs = 3 * MS_PER_MINUTE, config)
        assertEquals(3, sheet.minMinutes)
        assertEquals(3, sheet.maxMinutes)
        assertEquals(3, PracticeReducer.step(sheet, -1, config).minutes)
    }

    @Test
    fun `edit sheet steps - adds and clamps between zero and twelve hours`() {
        val sheet = PracticeReducer.editSheet(today, 85 * MS_PER_MINUTE, config)
        assertEquals(85, sheet.minutes)
        assertEquals(720, sheet.maxMinutes)
        assertEquals(90, PracticeReducer.step(sheet, +1, config).minutes)
        assertEquals(145, PracticeReducer.add(sheet, 60).minutes)
        assertEquals(720, PracticeReducer.add(sheet, 700).minutes)
        assertEquals(0, PracticeReducer.step(PracticeReducer.editSheet(today, 0, config), -1, config).minutes)
        assertEquals(720, PracticeReducer.editSheet(today, 13 * 60 * MS_PER_MINUTE, config).minutes)
    }

    /** Handoff 30g: the scale 10 of 10, Kaiser 15 of 15, the minuet stopped at 7 of 10, the concerto from minute 34 for 20; 47 minutes. */
    private val lessonStart = 1_000_000_000L
    private fun at(minutes: Long) = lessonStart + minutes * MS_PER_MINUTE
    private val lesson = PracticeBlocks(
        practiceStartedAtEpochMs = lessonStart,
        current = PieceBlock(4, at(34), 20 * MS_PER_MINUTE),
        finished = listOf(
            PieceBlock(1, at(0), 10 * MS_PER_MINUTE, endedAtEpochMs = at(10)),
            PieceBlock(2, at(10), 15 * MS_PER_MINUTE, endedAtEpochMs = at(25)),
            PieceBlock(3, at(25), 10 * MS_PER_MINUTE, endedAtEpochMs = at(32)),
        ),
    )
    private val titles = mapOf(1L to "G-dur · 3 октавы", 2L to "Кайзер № 3", 3L to "Менуэт соль мажор", 4L to "Концерт ля минор, I ч.")

    @Test
    fun `«Что играли» lists the blocks in the order played and follows the stepper - 30g1 and 30g2`() {
        val sheet = PracticeReducer.summarySheet(lessonStart, 47 * MS_PER_MINUTE, config, lesson, titles)
        assertEquals(
            listOf(
                PlayedLine("G-dur · 3 октавы", 10, 10, done = true),
                PlayedLine("Кайзер № 3", 15, 15, done = true),
                PlayedLine("Менуэт соль мажор", 7, 10, done = false),
                PlayedLine("Концерт ля минор, I ч.", 13, 20, done = false),
            ),
            sheet.played,
        )
        // 47 → 27 minutes: the concerto begun at 34 no longer fits — it stays as «не вошёл» (spec 3.36.3), the minuet keeps two of
        // its minutes
        var trimmed = sheet
        repeat(4) { trimmed = PracticeReducer.step(trimmed, -1, config) }
        assertEquals(27, trimmed.minutes)
        trimmed = PracticeReducer.step(PracticeReducer.step(trimmed, +1, config), -1, config)
        assertEquals(
            listOf(
                PlayedLine("G-dur · 3 октавы", 10, 10, done = true),
                PlayedLine("Кайзер № 3", 15, 15, done = true),
                PlayedLine("Менуэт соль мажор", 2, 10, done = false),
                PlayedLine("Концерт ля минор, I ч.", 0, 20, done = false, dropped = true),
            ),
            trimmed.played,
        )
        // without blocks the sheet is the sheet it was
        assertEquals(emptyList<PlayedLine>(), PracticeReducer.summarySheet(lessonStart, 47 * MS_PER_MINUTE, config).played)
    }

    // «Закончить занятие» (spec 3.36.3): 17:55 — 18:42 at 47 minutes.
    private val start1755 = LocalDateTime.parse("2026-09-27T17:55:00").toInstant(zone).toEpochMilliseconds()

    @Test
    fun `the span says start and end of what is saved and what it was once the stepper moved`() {
        val sheet = PracticeReducer.summarySheet(start1755, 47 * MS_PER_MINUTE, config)
        assertEquals(SummarySpan(start1755, start1755 + 47 * MS_PER_MINUTE, wasMs = null), PracticeReducer.spanOf(sheet))
        val down = PracticeReducer.step(PracticeReducer.step(sheet, -1, config), -1, config)
        assertEquals(37, down.minutes)
        assertEquals(SummarySpan(start1755, start1755 + 37 * MS_PER_MINUTE, wasMs = 47 * MS_PER_MINUTE), PracticeReducer.spanOf(down))
        // back where it started: the span of the exact time, no «было»
        val back = PracticeReducer.step(PracticeReducer.step(down, +1, config), +1, config)
        assertEquals(PracticeReducer.spanOf(sheet), PracticeReducer.spanOf(back))
    }

    @Test
    fun `the stepper walks by five from the actual length 47 42 37 32 and back saves the exact time`() {
        val sheet = PracticeReducer.summarySheet(start1755, 47 * MS_PER_MINUTE + 20_000, config)
        var walked = sheet
        val minutes = mutableListOf(walked.minutes)
        repeat(3) {
            walked = PracticeReducer.step(walked, -1, config)
            minutes += walked.minutes
        }
        assertEquals(listOf(47, 42, 37, 32), minutes)
        repeat(3) { walked = PracticeReducer.step(walked, +1, config) }
        assertEquals(47, walked.minutes)
        assertFalse(walked.edited)
        assertEquals(47 * MS_PER_MINUTE + 20_000, PracticeReducer.durationToSave(walked))
    }

    @Test
    fun `a block cut off whole stays as not fitted and the hint says blocks were cut`() {
        val sheet = PracticeReducer.summarySheet(lessonStart, 47 * MS_PER_MINUTE, config, lesson, titles)
        assertFalse(sheet.playedCut)
        assertEquals(SummaryHint.Forgot, PracticeReducer.hintOf(sheet))
        // 42: the concerto from 34 keeps 8 minutes — shortened, nothing cut off yet
        val shortened = PracticeReducer.step(sheet, -1, config)
        assertTrue(shortened.playedCut)
        assertEquals(SummaryHint.Cut, PracticeReducer.hintOf(shortened))
        assertTrue(shortened.played.none { it.dropped })
        // 32: the concerto begun at 34 is cut off whole
        val cut = PracticeReducer.step(PracticeReducer.step(shortened, -1, config), -1, config)
        assertEquals(32, cut.minutes)
        assertEquals(PlayedLine("Концерт ля минор, I ч.", 0, 20, done = false, dropped = true), cut.played.last())
        assertEquals(SummaryHint.Cut, PracticeReducer.hintOf(cut))
        // a block begun half a minute before the new end: shorter than a minute, cut off whole too — «стал короче минуты»
        val lateBlock = lesson.copy(current = PieceBlock(4, at(42) + 30_000, 20 * MS_PER_MINUTE))
        val whole = PracticeReducer.summarySheet(lessonStart, 48 * MS_PER_MINUTE, config, lateBlock, titles)
        assertFalse(whole.played.last().dropped)
        val atFortyThree = PracticeReducer.step(whole, -1, config)
        assertEquals(43, atFortyThree.minutes)
        assertTrue(atFortyThree.played.last().dropped)
    }

    @Test
    fun `the hints a sheet keeps the place of are every one its stepper can bring and no other`() {
        val withBlocks = PracticeReducer.summarySheet(lessonStart, 47 * MS_PER_MINUTE, config, lesson, titles)
        val hints = PracticeReducer.hintsOf(withBlocks)
        assertEquals(listOf(SummaryHint.Forgot, SummaryHint.Cut), hints)
        // every step down to the floor and back shows one of them, and the list is the same at every step
        var walked = withBlocks
        repeat(10) {
            walked = PracticeReducer.step(walked, -1, config)
            assertTrue(PracticeReducer.hintOf(walked) in hints, "${walked.minutes} min")
            assertEquals(hints, PracticeReducer.hintsOf(walked))
        }
        // without blocks nothing is cut: the usual hint alone; too short to trim: its own alone
        assertEquals(listOf(SummaryHint.Forgot), PracticeReducer.hintsOf(PracticeReducer.summarySheet(start1755, 47 * MS_PER_MINUTE, config)))
        assertEquals(listOf(SummaryHint.TooShort), PracticeReducer.hintsOf(PracticeReducer.summarySheet(start1755, 5 * MS_PER_MINUTE, config)))
    }

    @Test
    fun `the number goes into the save button only while it is below the actual one`() {
        val sheet = PracticeReducer.summarySheet(start1755, 47 * MS_PER_MINUTE + 40_000, config)
        assertEquals(48, sheet.minutes)
        assertNull(PracticeReducer.saveLengthMs(sheet))
        val down = PracticeReducer.step(sheet, -2, config)
        assertEquals(38 * MS_PER_MINUTE, PracticeReducer.saveLengthMs(down))
        assertNull(PracticeReducer.saveLengthMs(PracticeReducer.step(down, +2, config)))
    }

    @Test
    fun `five minutes or less cannot be trimmed and says so`() {
        val five = PracticeReducer.summarySheet(start1755, 5 * MS_PER_MINUTE, config)
        assertEquals(5, five.minMinutes)
        assertEquals(5, five.maxMinutes)
        assertEquals(config.minEditableMinutes, five.floorMinutes)
        assertEquals(SummaryHint.TooShort, PracticeReducer.hintOf(five))
        val four = PracticeReducer.summarySheet(start1755, 4 * MS_PER_MINUTE + 20_000, config)
        assertEquals(SummaryHint.TooShort, PracticeReducer.hintOf(four))
        // six can go down to five: both buttons are not dimmed, the usual hint
        val six = PracticeReducer.summarySheet(start1755, 6 * MS_PER_MINUTE, config)
        assertEquals(SummaryHint.Forgot, PracticeReducer.hintOf(six))
        assertEquals(5, PracticeReducer.step(six, -1, config).minutes)
    }

    @Test
    fun `a day over twelve hours opens at twelve and was is its real sum`() {
        val total = 13 * 60 * MS_PER_MINUTE + 10 * MS_PER_MINUTE
        val sheet = PracticeReducer.editSheet(today, total, config)
        assertEquals(720, sheet.minutes)
        assertEquals(total, sheet.dayTotalMs)
        assertEquals(DayCaption.None, PracticeReducer.dayCaptionOf(sheet))
        assertEquals(DayCaption.Was(total), PracticeReducer.dayCaptionOf(PracticeReducer.step(sheet, -1, config)))
    }

    @Test
    fun `an empty day says without the phone and a day not moved has no caption`() {
        val empty = PracticeReducer.editSheet(today, 0, config)
        assertEquals(DayCaption.NoPhone, PracticeReducer.dayCaptionOf(empty))
        assertEquals(DayCaption.NoPhone, PracticeReducer.dayCaptionOf(PracticeReducer.add(empty, 30)))
        val day = PracticeReducer.editSheet(today, 75 * MS_PER_MINUTE, config)
        assertEquals(DayCaption.None, PracticeReducer.dayCaptionOf(day))
        assertEquals(DayCaption.Was(75 * MS_PER_MINUTE), PracticeReducer.dayCaptionOf(PracticeReducer.add(day, 30)))
        // moved and back: no caption
        assertEquals(DayCaption.None, PracticeReducer.dayCaptionOf(PracticeReducer.step(PracticeReducer.step(day, +1, config), -1, config)))
    }

    @Test
    fun `under «Занятие идёт» the running block says what is left or «готово» at its goal`() {
        val running = RunningPractice(lessonStart, lastSoundEpochMs = null)
        assertEquals(RunningBlockLine("Концерт ля минор, I ч.", minutesLeft = 7), PracticeReducer.runningBlockOf(running, lesson, titles, at(47)))
        assertEquals(RunningBlockLine("Концерт ля минор, I ч.", minutesLeft = null), PracticeReducer.runningBlockOf(running, lesson, titles, at(60)))
        assertNull(PracticeReducer.runningBlockOf(running, lesson.copy(current = null), titles, at(47)))
        assertNull(PracticeReducer.runningBlockOf(RunningPractice(lessonStart + 1, null), lesson, titles, at(47)))
        assertNull(PracticeReducer.runningBlockOf(running, lesson, titles - 4L, at(47)))
    }

    /** Saturday 12 September 2026 of the mockups (events-kinds.html, 4): four events — the whole day first, then by start. */
    private val busy = LocalDate(2026, 9, 12)
    private val busyDay = listOf(
        event(4, busy, clock(17), 45, kind = LESSON, place = "Анна Сергеевна", seriesId = 1),
        event(2, busy, clock(11), 120, kind = orchestra),
        event(3, busy, clock(14), null, kind = masterClass, title = "Мастер-класс Когана"),
        event(1, busy, kind = OTHER, title = "Замена струн"),
    )

    /**
     * The marks of a cell (spec 3.36.9, 5.28): the looks of its first three events in the order of the day, «+» for a fourth, and every
     * event as TalkBack names it — the word of its kind and its start; a day without events has none, as before them.
     */
    @Test
    fun `a cell marks its first three events in the order of the day and a plus for more`() {
        val state = state(events = busyDay + event(5, LocalDate(2026, 9, 14), clock(17), 45))
        val cell = state.cells.filterNotNull().single { it.date == busy }
        assertEquals(
            listOf(KindLook(KindSign.OTHER, 7), KindLook(KindSign.ARC, 1), KindLook(KindSign.BOLT, 4)), cell.marks,
            "«весь день» first, then 11:00 and 14:00; the lesson at 17:00 is the fourth",
        )
        assertTrue(cell.more)
        assertEquals(
            listOf(CellEvent(KindRef.OTHER, null, null), CellEvent(orchestra, "Оркестр", clock(11)), CellEvent(masterClass, "Мастер-класс", clock(14)), CellEvent(LESSON, null, clock(17))),
            cell.events,
        )
        val one = state.cells.filterNotNull().single { it.date == LocalDate(2026, 9, 14) }
        assertEquals(listOf(KindLook(KindSign.LESSON, 0)), one.marks)
        assertFalse(one.more)
        val none = state.cells.filterNotNull().single { it.date == LocalDate(2026, 9, 13) }
        assertTrue(none.marks.isEmpty() && !none.more && none.events.isEmpty())
        assertTrue(state.cells.filterNotNull().all { it.enabled }, "every day of «Занятия» answers a touch, one to come too")
    }

    @Test
    fun `an event of a kind of ones own that is gone is marked and named as other`() {
        val gone = KindRef.Custom(77)
        val cell = state(events = listOf(event(1, busy, clock(9), kind = gone))).cells.filterNotNull().single { it.date == busy }
        assertEquals(listOf(KindLook(KindSign.OTHER, 7)), cell.marks)
        assertEquals(listOf(CellEvent(KindRef.OTHER, null, clock(9))), cell.events)
    }

    /**
     * The sheet of a day with events (spec 3.36.9): its rows in the order of the day — the look, the name (the title, or the kind), the
     * start and the end (a minute of the day it ends on), the place and the repeat; a past day keeps its time and records.
     */
    @Test
    fun `the sheet of a day has its events in the order of the day`() {
        val day = state(events = busyDay, series = listOf(series(1, LocalDate(2026, 9, 5), laidUntil = LocalDate(2026, 12, 5))), selected = busy).selected!!
        assertFalse(day.isFuture)
        assertEquals(listOf(1L, 2L, 3L, 4L), day.events.map { it.eventId })
        val (strings, orchestraRow, master, lesson) = day.events
        assertTrue(strings.allDay)
        assertEquals(EventName.Titled("Замена струн"), strings.name)
        assertNull(strings.endMinutes)
        assertEquals(EventName.OfKind(orchestra, "Оркестр"), orchestraRow.name, "no title: the name of the kind")
        assertEquals(clock(11) to clock(13), orchestraRow.startMinutes to orchestraRow.endMinutes)
        assertEquals(clock(14) to null, master.startMinutes to master.endMinutes, "without a length: only the start")
        assertEquals(KindLook(KindSign.BOLT, 4), master.look)
        assertEquals("Анна Сергеевна", lesson.place)
        assertEquals(Repeat.WEEKLY, lesson.repeat)
        assertEquals(Repeat.NONE, master.repeat)
        val late = state(events = listOf(event(9, busy, clock(23, 30), 90)), selected = busy).selected!!.events.single()
        assertEquals(clock(1), late.endMinutes, "23:30 and an hour and a half end at 01:00 of the next day")
    }

    /**
     * A day to come (spec 3.36.9): selected and its sheet opens — no time, no records, its events; tomorrow says so for its chip. The day
     * has a practice and a record of its own here, as after a clock set back a day: its sheet shows neither (its time is not edited and
     * does not exist yet, 3.35), while its cell keeps what is stored.
     */
    @Test
    fun `a day to come has a sheet with its events and without time or records`() {
        val tomorrow = LocalDate(2026, 9, 18)
        val sessions = listOf(session(1, "2026-09-17T10:00:00"), session(2, "2026-09-18T10:00:00"))
        val entries = listOf(entry(16, 50), entry(18, 40))
        val state = state(entries = entries, sessions = sessions, events = listOf(event(1, tomorrow, clock(17), 45)), selected = tomorrow)
        val day = state.selected!!
        assertTrue(day.isFuture && day.isTomorrow && !day.isToday)
        assertEquals(0L, day.totalMs, "the 40 minutes stored on the 18th are not the time of a day to come")
        assertTrue(day.sessions.isEmpty(), "nor is its record of 10:00 one of its records")
        assertEquals(listOf(1L), day.events.map { it.eventId })
        val cell = state.cells.filterNotNull().single { it.date == tomorrow }
        assertTrue(cell.isSelected && cell.isFuture)
        assertEquals(40 * MS_PER_MINUTE, cell.totalMs, "what is stored is on the 18th")
        assertFalse(state(selected = LocalDate(2026, 9, 19)).selected!!.isTomorrow)
        assertFalse(state(selected = today).selected!!.isTomorrow)
    }

    /**
     * The changes of the reminder the screen did not see are counted (spec 3.36.9, «Движение»): a card that came or went while the screen
     * was away, or by the reckoning at its opening, is taken at once — only one that comes or goes on the open screen fades.
     */
    @Test
    fun `a change of the reminder the screen did not see is counted and one it saw is not`() {
        val lesson = event(1, LocalDate(2026, 9, 18), clock(17), 45)
        val without = state(events = listOf(lesson), now = moment(LocalDate(2026, 9, 16), 23, 59))
        val with = state(events = listOf(lesson), now = moment(today, 0, 0))
        assertNull(without.reminder)
        assertNotNull(with.reminder)
        assertEquals(0, PracticeReducer.withReminderOf(with, without, seen = true).reminderEpoch, "come on the open screen: it fades in")
        val unseen = PracticeReducer.withReminderOf(with, without, seen = false)
        assertEquals(1, unseen.reminderEpoch, "come out of sight")
        assertEquals(with.reminder, unseen.reminder)
        assertEquals(1, PracticeReducer.withReminderOf(with, unseen, seen = false).reminderEpoch, "the same card again changes nothing")
        assertEquals(2, PracticeReducer.withReminderOf(without, unseen, seen = false).reminderEpoch, "gone out of sight")
        assertEquals(1, PracticeReducer.withReminderOf(without, unseen, seen = true).reminderEpoch, "gone on the open screen keeps the count")
    }

    /** Under the name of a month to come, the number of its events (spec 3.36.9): the current month and the past ones keep their time. */
    @Test
    fun `a month to come counts its events and the current one does not`() {
        val november = listOf(event(1, LocalDate(2026, 11, 2)), event(2, LocalDate(2026, 11, 14)), event(3, LocalDate(2026, 11, 14)), event(4, LocalDate(2026, 12, 1)))
        val ahead = state(month = YearMonth(2026, 11), events = november)
        assertTrue(ahead.monthIsFuture)
        assertEquals(3, ahead.monthEvents)
        val now = state(events = november + event(5, LocalDate(2026, 9, 20)))
        assertFalse(now.monthIsFuture)
        assertEquals(1, now.monthEvents)
        assertFalse(state(month = YearMonth(2026, 8), events = november).monthIsFuture)
    }

    /**
     * Under the grid (spec 3.36.9): the kinds of the events of the month shown, each once, in the order of the form — the built-in ones,
     * then those of one's own; the hint stands while the app has no event at all, and goes with the first one wherever it is.
     */
    @Test
    fun `the legend is the kinds of the month in the order of the form and the hint is there only without any event`() {
        val ordered = KindRules.ordered(kinds)
        val state = state(events = busyDay + event(6, LocalDate(2026, 9, 21), kind = PERFORMANCE) + event(7, LocalDate(2026, 10, 3), kind = KindRef.BuiltIn(BuiltInKind.REHEARSAL)), kinds = ordered)
        assertEquals(listOf(LESSON, PERFORMANCE, KindRef.OTHER, masterClass, orchestra), state.legend.map { it.ref })
        assertFalse(state.eventsHint)
        assertTrue(state(month = YearMonth(2026, 8), events = busyDay).legend.isEmpty(), "a month without events has nothing under its grid")
        assertFalse(state(month = YearMonth(2026, 8), events = busyDay).eventsHint)
        val none = state()
        assertTrue(none.eventsHint)
        assertTrue(none.legend.isEmpty())
    }

    /**
     * The reminder (spec 3.35, 3.36.9, 5.28) is worked out for the moment of the last tick: tomorrow's lesson from 00:00 of today, two rows
     * of the full card and one of the compact, «ещё N» for the rest — the screen chooses which card stands.
     */
    @Test
    fun `the reminder is the events of its window at the moment of the tick`() {
        val tomorrow = LocalDate(2026, 9, 18)
        val lesson = event(1, tomorrow, clock(17), 45, place = "Анна Сергеевна")
        assertNull(state(events = listOf(lesson), now = moment(LocalDate(2026, 9, 16), 23, 59)).reminder, "two days before: no card")
        val card = assertNotNull(state(events = listOf(lesson), now = moment(today, 0, 0)).reminder)
        assertEquals(ReminderDay.TOMORROW, card.rows.single().day)
        val four = listOf(lesson, event(2, tomorrow, clock(11), kind = orchestra), event(3, tomorrow, clock(14), kind = masterClass), event(4, tomorrow))
        val full = assertNotNull(state(events = four, now = moment(today, 18)).reminder)
        assertEquals(listOf(4L, 2L), full.visible(compact = false).map { it.eventId }, "«весь день» first, then by start")
        assertEquals(listOf(3L, 1L), full.hidden(compact = false).map { it.eventId })
        assertEquals(listOf(4L), full.visible(compact = true).map { it.eventId })
        assertEquals(3, full.hidden(compact = true).size)
        assertEquals(tomorrow, full.firstHiddenDate(compact = true))
        assertNull(state(events = listOf(lesson), now = moment(tomorrow, 17, 45)).reminder, "over: the card goes by itself")
    }
}
