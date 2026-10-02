package com.violinjourney.app.core.data

import com.violinjourney.app.core.analytics.NoOpAnalytics
import com.violinjourney.app.core.data.backing.BackingEntity
import com.violinjourney.app.core.data.backing.PieceBackingEntity
import com.violinjourney.app.core.data.backing.TakeBackingEntity
import com.violinjourney.app.core.data.events.RoomEventRepository
import com.violinjourney.app.core.data.journey.EarningEntity
import com.violinjourney.app.core.data.journey.RoomHomeRepository
import com.violinjourney.app.core.data.journey.RoomJourneyRepository
import com.violinjourney.app.core.data.repertoire.RoomRepertoireRepository
import com.violinjourney.app.core.data.session.SessionEntity
import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.events.CalendarEvent
import com.violinjourney.app.core.domain.events.EditScope
import com.violinjourney.app.core.domain.events.EventChange
import com.violinjourney.app.core.domain.events.EventDraft
import com.violinjourney.app.core.domain.events.EventRules
import com.violinjourney.app.core.domain.events.EventTime
import com.violinjourney.app.core.domain.events.EventsConfig
import com.violinjourney.app.core.domain.events.KindRef
import com.violinjourney.app.core.domain.events.KindSave
import com.violinjourney.app.core.domain.events.KindSign
import com.violinjourney.app.core.domain.events.Repeat
import com.violinjourney.app.core.domain.events.SeriesEdits
import com.violinjourney.app.core.domain.events.TestEvents
import com.violinjourney.app.core.domain.events.TestEvents.at
import com.violinjourney.app.core.domain.events.draft
import com.violinjourney.app.core.domain.events.plusDays
import com.violinjourney.app.core.domain.repertoire.PieceDraft
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.time.MutableWallClock
import com.violinjourney.app.core.time.SystemWallClock
import com.violinjourney.app.ios.IosStorage
import com.violinjourney.app.ios.NoSheetFiles
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

/**
 * The rules the database itself keeps, on the real storage of iOS — Room on its own SQLite, in a folder of the simulator:
 * one purse for the road, the souvenirs and the home (spec 3.24, 5.17), and a backing kept while anything points at it
 * (spec 5.25), and the events of the calendar (spec 3.35): what the plans of `SeriesEdits` do to the rows, the horizon laid
 * once, the recordings of an event that goes. On Android the same DAO are checked only by the instrumented tests
 * (DatabaseMigrationTest, RoomEventRepositoryTest); here they run in every `:shared:iosSimulatorArm64Test`.
 */
@OptIn(ExperimentalForeignApi::class)
class IosDaoTest {
    private val directory = NSTemporaryDirectory() + NSUUID().UUIDString

    init {
        NSFileManager.defaultManager.createDirectoryAtPath(directory, true, null, null)
    }

    private val database = IosStorage.database(directory)

    @AfterTest
    fun cleanUp() {
        database.close()
        NSFileManager.defaultManager.removeItemAtPath(directory, null)
    }

    @Test
    fun `one purse pays the road the souvenirs and the home`() = runTest {
        val dao = database.journeyDao()
        val journey = RoomJourneyRepository(dao, NoOpAnalytics())
        val home = RoomHomeRepository(dao, NoOpAnalytics())
        dao.begin("home", now = 1)
        dao.insertEarning(EarningEntity(atEpochMs = 1, notesPlayed = 400, notesInTune = 320, durationMs = 600_000, takts = 1_000))

        assertTrue(dao.arrive("cremona", "home", price = 300, now = 2))
        assertTrue(dao.buyExtra("cremona", "SOUVENIR", price = 40, now = 3))
        assertTrue(dao.buyForHome("cat_ginger", ITEM, price = 600, slot = "pet", now = 4))
        val progress = journey.progress.first()
        assertEquals(1_000, progress.earned)
        assertEquals(940, progress.spent, "the road, the souvenir and the cat from one purse")

        assertFalse(dao.arrive("milan", "cremona", price = 100, now = 5), "60 left: the road costs more")
        assertTrue(dao.buyForHome("tea", ITEM, price = 60, slot = "deskR", now = 6), "the last 60 go on tea")
        assertFalse(dao.buyForHome("lamp", ITEM, price = 1, slot = "lamp", now = 7), "nothing left")
        assertFalse(dao.buyForHome("cat_ginger", ITEM, price = 0, slot = "pet", now = 8), "a thing is bought once")
        assertFalse(dao.buyExtra("cremona", "SOUVENIR", price = 0, now = 9), "a souvenir is bought once")
        assertFalse(dao.arrive("vienna", "milan", price = 0, now = 10), "no stop is skipped")

        assertEquals(setOf("cat_ginger", "tea"), home.state.first().purchased)
        assertEquals(mapOf("pet" to "cat_ginger", "deskR" to "tea"), home.state.first().choices)
        assertEquals(1_000, journey.progress.first().spent)
    }

    @Test
    fun `two purchases at once on the last takts - only one goes through`() = runTest {
        val dao = database.journeyDao()
        repeat(ROUNDS) { round ->
            dao.insertEarning(EarningEntity(atEpochMs = round.toLong(), notesPlayed = 0, notesInTune = 0, durationMs = 0, takts = 100))
            // two taps of «Купить» on two threads, each for all that is left
            val bought = withContext(Dispatchers.Default) {
                coroutineScope {
                    listOf("a$round" to "left$round", "b$round" to "right$round").map { (id, slot) ->
                        async { dao.buyForHome(id, ITEM, price = 100, slot = slot, now = round.toLong()) }
                    }.awaitAll()
                }
            }
            assertEquals(1, bought.count { it }, "round $round: $bought")
        }
        val progress = RoomJourneyRepository(dao, NoOpAnalytics()).progress.first()
        assertEquals(progress.earned, progress.spent, "never more spent than earned")
    }

    @Test
    fun `a backing goes only when nothing points at it`() = runTest {
        val dao = database.backingDao()
        val repertoire = RoomRepertoireRepository(database.repertoireDao(), NoSheetFiles, RepertoireConfig(), SystemWallClock, NoOpAnalytics())
        val pieceId = repertoire.add(PieceDraft(title = "Концерт"), nowEpochMs = 1)
        val sessionId = database.sessionDao().insert(take(pieceId), bucketMs = 50, samples = ByteArray(3))
        val backing = dao.insert(
            BackingEntity(fileName = "a.m4a", title = "Piano", durationMs = 1, sampleRate = 44_100, channels = 2, sizeBytes = 1, addedAtEpochMs = 1),
        )
        dao.upsertPiece(PieceBackingEntity(pieceId, backing, enabled = true))
        dao.insertTake(
            TakeBackingEntity(
                sessionId = sessionId, backingId = backing, offsetMs = 200, recordedOffsetMs = 200, gainDb = -6f, playedMs = 1_000,
                output = "BLUETOOTH", deviceName = "Buds", latencyMs = 180,
            ),
        )
        assertEquals(emptyList(), dao.deleteUnused())

        // the piece goes, its row with it — a foreign key the SQLite of iOS keeps too; the take still holds the file
        repertoire.delete(pieceId)
        assertEquals(emptyList(), dao.pieceBackings().first())
        assertEquals(emptyList(), dao.deleteUnused())
        // the take goes as well: now nothing points at the backing
        database.sessionDao().delete(listOf(sessionId))
        assertEquals(emptyList(), dao.takeBackings().first())
        assertEquals(listOf("a.m4a"), dao.deleteUnused())
        assertEquals(emptyList(), dao.backings().first())
    }

    // ---- events (spec 3.35, plan 5.3)

    private val eventsConfig = EventsConfig()
    private val clock = MutableWallClock(0, TestEvents.MOSCOW)
    private val events = RoomEventRepository(database.eventDao(), eventsConfig, clock, NoOpAnalytics(), Dispatchers.Default)

    /** Moves the clock of the repository: what it creates is created now. */
    private fun now(date: LocalDate, hours: Int, minutes: Int = 0) {
        clock.nowMs = TestEvents.moment(date, hours, minutes).toEpochMilliseconds()
    }

    private suspend fun record(eventId: Long?, title: String? = null): Long =
        database.sessionDao().insert(take(pieceId = null).copy(title = title, eventId = eventId), bucketMs = 50, samples = ByteArray(3))

    private suspend fun lessonsOn(day: DayOfWeek): List<CalendarEvent> = events.events.first().filter { it.date.dayOfWeek == day }

    @Test
    fun `an event goes with its program and its records keep the name it gave them`() = runTest {
        val repertoire = RoomRepertoireRepository(database.repertoireDao(), NoSheetFiles, RepertoireConfig(), SystemWallClock, NoOpAnalytics())
        val concerto = repertoire.add(PieceDraft(title = "Концерт ля минор", composer = "А. Вивальди"), nowEpochMs = 1)
        val melody = repertoire.add(PieceDraft(title = "Мелодия"), nowEpochMs = 1)
        val day = LocalDate(2026, 10, 24)
        now(day, 21)
        val id = events.add(EventDraft(TestEvents.PERFORMANCE, day, at(18, 30), 90, title = " Осенний концерт "), Repeat.NONE, until = null, today = day)
        assertEquals("Осенний концерт", events.event(id)?.title)
        // an element that is not there is left out of the program
        events.setProgram(id, listOf(concerto, melody, 999))
        assertEquals(mapOf(id to listOf(concerto, melody)), events.programs.first())
        val byDefault = record(id)
        val named = record(id, title = "Лучший концерт")
        val alone = record(eventId = null)
        assertEquals(setOf(id), events.recordEvents.first().keys)
        assertEquals("Осенний концерт", events.recordEvents.first().getValue(id).title)
        // an element of the repertoire that goes leaves the program
        repertoire.delete(melody)
        assertEquals(listOf(concerto), events.programs.first()[id])

        val event = assertNotNull(events.event(id))
        val plan = SeriesEdits.deletePlan(event, scope = null, series = null, seriesEvents = emptyList(), clock.instant(), clock.zone, eventsConfig)
        events.apply(plan, frozenTitles = mapOf(id to "Осенний концерт · 24 октября"), today = day)
        assertNull(events.event(id))
        assertEquals(emptyMap(), events.programs.first(), "the program goes with its event")
        val sessions = database.sessionDao().observeAll().first().associateBy { it.id }
        assertEquals(setOf(byDefault, named, alone), sessions.keys, "the records stay in «Записи»")
        assertNull(sessions.getValue(byDefault).eventId)
        assertEquals("Осенний концерт · 24 октября", sessions.getValue(byDefault).title, "the name it wore is written into it")
        assertNull(sessions.getValue(named).eventId)
        assertEquals("Лучший концерт", sessions.getValue(named).title, "a name of its own stays")
        assertNull(sessions.getValue(alone).title)
        assertEquals(emptyMap(), events.recordEvents.first())
    }

    @Test
    fun `a kind of ones own goes and its events and series become other`() = runTest {
        val today = LocalDate(2026, 9, 27)
        now(today, 12)
        val solfege = assertNotNull(events.saveKind(KindSave.NewOwn(" Сольфеджио ", color = 3, sign = KindSign.BOOK)) as? KindRef.Custom)
        assertEquals("Сольфеджио", events.kinds.first().single { it.ref == solfege }.ownName)
        events.add(EventDraft(solfege, LocalDate(2026, 9, 29), at(10), 45), Repeat.WEEKLY, until = null, today = today)
        val laid = events.events.first()
        assertEquals(12, laid.size)
        assertTrue(laid.all { it.kind == solfege })
        record(laid.first().id)
        assertEquals("Сольфеджио", events.recordEvents.first().getValue(laid.first().id).ownName)
        // a built-in kind is given a colour once and then another: one row, the last colour
        events.saveKind(KindSave.BuiltInColor(BuiltInKind.LESSON, 3))
        events.saveKind(KindSave.BuiltInColor(BuiltInKind.LESSON, 4))
        assertEquals(4, events.kinds.first().single { it.ref == TestEvents.LESSON }.look.color)
        assertEquals(1, database.eventDao().observeKinds().first().count { it.builtIn == "LESSON" })

        events.deleteOwnKind(solfege.id)
        assertTrue(events.events.first().all { it.kind == TestEvents.OTHER }, "its events are «Другое»")
        assertEquals(TestEvents.OTHER, events.series.first().single().kind, "and the repeat lays «Другое» from now on")
        assertTrue(events.kinds.first().none { it.ref == solfege })
        assertEquals(TestEvents.OTHER, events.recordEvents.first().getValue(laid.first().id).kind)
        events.layAhead(today.plusDays(7))
        assertTrue(events.events.first().all { it.kind == TestEvents.OTHER })
    }

    @Test
    fun `twenty kinds of ones own and not one more`() = runTest {
        repeat(eventsConfig.maxCustomKinds) { assertNotNull(events.saveKind(KindSave.NewOwn("Вид $it", color = 1, sign = KindSign.HAT))) }
        assertNull(events.saveKind(KindSave.NewOwn("Двадцать первый", color = 1, sign = KindSign.HAT)))
        assertEquals(eventsConfig.maxCustomKinds, events.kinds.first().count { it.ref is KindRef.Custom })
    }

    @Test
    fun `laying ahead twice lays nothing the second time`() = runTest {
        val today = LocalDate(2026, 9, 27)
        now(today, 12)
        events.add(EventDraft(TestEvents.LESSON, LocalDate(2026, 9, 28), at(17), 45, place = "Анна Сергеевна"), Repeat.WEEKLY, until = null, today = today)
        val laid = events.events.first()
        assertEquals(12, laid.size, "28.09 and every Monday to the horizon 20.12")
        assertEquals(LocalDate(2026, 12, 14), laid.maxOf { it.date })
        assertEquals(LocalDate(2026, 12, 20), events.series.first().single().laidUntil)
        assertTrue(laid.all { it.createdAtEpochMs == clock.nowMs && it.place == "Анна Сергеевна" && it.notes.isEmpty() }, "the moment of the repeat (plan D49)")
        events.layAhead(today)
        assertEquals(laid, events.events.first())
        // a week later the app starts and «Занятия» open at the same time: one Monday more, laid once
        val later = today.plusDays(7)
        withContext(Dispatchers.Default) {
            coroutineScope { List(2) { async { events.layAhead(later) } }.awaitAll() }
        }
        val afterWeek = events.events.first()
        assertEquals(13, afterWeek.size)
        assertEquals(1, afterWeek.count { it.date == LocalDate(2026, 12, 21) })
        events.layAhead(later)
        assertEquals(afterWeek, events.events.first())
    }

    @Test
    fun `lessons laid after a concert do not make the next event a lesson`() = runTest {
        val today = LocalDate(2026, 9, 27)
        now(today, 12)
        events.add(EventDraft(TestEvents.LESSON, LocalDate(2026, 9, 28), at(17), 45), Repeat.WEEKLY, until = null, today = today)
        now(today, 13)
        events.add(EventDraft(TestEvents.PERFORMANCE, LocalDate(2026, 10, 24), at(18, 30), 90), Repeat.NONE, until = null, today = today)
        now(today.plusDays(14), 9)
        events.layAhead(today.plusDays(14))
        val all = events.events.first()
        assertEquals(15, all.size, "two Mondays more after the concert")
        assertEquals(TestEvents.PERFORMANCE, EventRules.defaults(all, events.series.first()).kind)
    }

    /**
     * A weekly lesson since 07.09; on Monday 12.10 after the lesson an edit of the lesson of 05.10 gone by: whatever the
     * edit, not one event before the cut — 13.10 — is new, changed or gone (plan D4).
     */
    private suspend fun pastLessonEdited(afterRepeat: Repeat, edit: EventDraft.() -> EventDraft): Pair<List<CalendarEvent>, List<CalendarEvent>> {
        val created = LocalDate(2026, 9, 1)
        now(created, 12)
        events.add(EventDraft(TestEvents.LESSON, LocalDate(2026, 9, 7), at(17), 45, place = "Анна Сергеевна"), Repeat.WEEKLY, until = null, today = created)
        val today = LocalDate(2026, 10, 12)
        now(today, 18)
        events.layAhead(today)
        val before = events.events.first()
        val selected = before.single { it.date == LocalDate(2026, 10, 5) }
        val series = events.series.first().single()
        val change = EventChange.of(selected, selected.draft().edit(), Repeat.WEEKLY, afterRepeat, afterUntil = null, eventsConfig)
        val plan = SeriesEdits.plan(change, EditScope.FOLLOWING, series, before, clock.instant(), clock.zone, eventsConfig)
        events.apply(plan, frozenTitles = emptyMap(), today = today)
        val after = events.events.first()
        assertEquals(before.filter { it.date < CUT }, after.filter { it.date < CUT }, "every event before the cut as it was")
        assertEquals(LocalDate(2026, 10, 12), events.series.first().single { it.id == series.id }.until, "the old repeat ends the day before the cut")
        return before to after
    }

    @Test
    fun `a weekday change at a past lesson makes and changes nothing before today`() = runTest {
        val (_, after) = pastLessonEdited(Repeat.WEEKLY) { copy(date = LocalDate(2026, 10, 6), startMinutes = at(18)) }
        val tuesdays = lessonsOn(DayOfWeek.TUESDAY)
        assertEquals(LocalDate(2026, 10, 13), tuesdays.minOf { it.date }, "the new repeat is laid from the cut")
        assertTrue(tuesdays.all { it.startMinutes == at(18) && it.seriesId != null })
        assertTrue(after.none { it.date >= CUT && it.date.dayOfWeek == DayOfWeek.MONDAY }, "no Monday left from the cut on")
    }

    @Test
    fun `no repeat at a past lesson changes nothing before today`() = runTest {
        val (_, after) = pastLessonEdited(Repeat.NONE) { this }
        assertTrue(after.none { it.date >= CUT }, "no repeat: nothing from the cut on")
        assertEquals(6, after.size, "the five Mondays of September and October gone by, and today's")
    }

    @Test
    fun `every other week from a past lesson begins at the cut`() = runTest {
        pastLessonEdited(Repeat.BIWEEKLY) { this }
        assertEquals(LocalDate(2026, 10, 19), lessonsOn(DayOfWeek.MONDAY).filter { it.date >= CUT }.minOf { it.date }, "every other week from 05.10")
    }

    @Test
    fun `a lesson moved to another weekday from a day to come takes its notes and records into the new repeat`() = runTest {
        val today = LocalDate(2026, 10, 1)
        now(today, 12)
        events.add(EventDraft(TestEvents.LESSON, LocalDate(2026, 9, 28), at(17), 45), Repeat.WEEKLY, until = null, today = today)
        val laid = events.events.first()
        val october19 = laid.single { it.date == LocalDate(2026, 10, 19) }
        events.setNotes(october19.id, "Принести ноты")
        val taken = record(october19.id)
        val selected = assertNotNull(events.event(october19.id))
        val change = EventChange.of(selected, selected.draft().copy(date = LocalDate(2026, 10, 20), startMinutes = at(18)), Repeat.WEEKLY, Repeat.WEEKLY, null, eventsConfig)
        val old = events.series.first().single()
        events.apply(SeriesEdits.plan(change, EditScope.FOLLOWING, old, laid, clock.instant(), clock.zone, eventsConfig), emptyMap(), today)

        val moved = assertNotNull(events.event(october19.id), "the same event")
        assertEquals(LocalDate(2026, 10, 20), moved.date)
        assertEquals("Принести ноты", moved.notes)
        assertEquals(october19.id, database.sessionDao().session(taken)?.eventId)
        val newSeries = events.series.first().single { it.id != old.id }
        assertEquals(newSeries.id, moved.seriesId)
        assertFalse(moved.detached)
        assertEquals(LocalDate(2026, 10, 18), events.series.first().single { it.id == old.id }.until)
        val after = events.events.first()
        assertEquals(laid.filter { it.date < LocalDate(2026, 10, 19) }, after.filter { it.date < LocalDate(2026, 10, 19) })
        val tuesdays = after.filter { it.date >= LocalDate(2026, 10, 19) }
        assertTrue(tuesdays.all { it.date.dayOfWeek == DayOfWeek.TUESDAY && it.startMinutes == at(18) && it.seriesId == newSeries.id })
        assertEquals(1, tuesdays.count { it.date == LocalDate(2026, 10, 20) }, "its first date has the moved lesson and nothing laid over it")
        assertTrue(tuesdays.filter { it.id != moved.id }.all { it.createdAtEpochMs == moved.createdAtEpochMs }, "laid with the moment of the moved lesson")
        assertEquals(EventTime(at(18), 45), EventRules.timeOf(TestEvents.LESSON, after, events.series.first()), "a new lesson starts at the new repeat's time")
    }

    /** A weekly lesson since 28.09 at 17:00 for 45 minutes with Анна Сергеевна, created on [today] at noon. */
    private suspend fun mondays(today: LocalDate) {
        now(today, 12)
        events.add(EventDraft(TestEvents.LESSON, LocalDate(2026, 9, 28), at(17), 45, place = "Анна Сергеевна"), Repeat.WEEKLY, until = null, today = today)
    }

    /** Saves [edit] of the lesson of [date] with the answer [scope] — null, the filled one — as the screen will. */
    private suspend fun edit(date: LocalDate, scope: EditScope?, today: LocalDate, edit: EventDraft.() -> EventDraft) {
        val current = events.events.first()
        val selected = current.single { it.date == date }
        val change = EventChange.of(selected, selected.draft().edit(), Repeat.WEEKLY, Repeat.WEEKLY, afterUntil = null, eventsConfig)
        val series = events.series.first().single { it.id == selected.seriesId }
        events.apply(SeriesEdits.plan(change, scope, series, current, clock.instant(), clock.zone, eventsConfig), emptyMap(), today)
    }

    @Test
    fun `this and following writes what it changed and not what one lesson has by itself`() = runTest {
        val today = LocalDate(2026, 10, 1)
        mondays(today)
        val october19 = LocalDate(2026, 10, 19)
        edit(october19, EditScope.ONLY_THIS, today) { copy(place = "Ольга Петровна") }
        edit(october19, scope = null, today) { copy(durationMinutes = 60) }

        val after = events.events.first()
        val selected = after.single { it.date == october19 }
        assertEquals("Ольга Петровна" to 60, selected.place to selected.durationMinutes, "the selected lesson takes all of its edit")
        assertTrue(after.filter { it.date > october19 }.all { it.place == "Анна Сергеевна" && it.durationMinutes == 60 && it.startMinutes == at(17) }, "its teacher stays its own")
        assertTrue(after.filter { it.date < october19 }.all { it.durationMinutes == 45 })
        val series = events.series.first().single()
        assertEquals("Анна Сергеевна" to 60, series.place to series.durationMinutes, "the repeat keeps its teacher")
        events.layAhead(today.plusDays(7))
        val newest = events.events.first().maxBy { it.date }
        assertEquals("Анна Сергеевна" to 60, newest.place to newest.durationMinutes)
    }

    @Test
    fun `an edit before an earlier one changes the following lessons only in what it changed`() = runTest {
        val today = LocalDate(2026, 9, 30)
        mondays(today)
        edit(LocalDate(2026, 10, 19), scope = null, today) { copy(startMinutes = at(17, 30)) }
        edit(LocalDate(2026, 10, 5), scope = null, today) { copy(place = "Ольга Петровна") }

        val after = events.events.first()
        val october = after.filter { it.date >= LocalDate(2026, 10, 5) && it.date < LocalDate(2026, 10, 19) }
        assertEquals(listOf(LocalDate(2026, 10, 5), LocalDate(2026, 10, 12)), october.map { it.date })
        assertTrue(october.all { it.startMinutes == at(17) && it.place == "Ольга Петровна" }, "05.10 and 12.10 keep 17:00")
        val fromThe19th = after.filter { it.date >= LocalDate(2026, 10, 19) }
        assertTrue(fromThe19th.all { it.startMinutes == at(17, 30) && it.place == "Ольга Петровна" }, "the lessons from 19.10 keep 17:30")
        assertEquals("Анна Сергеевна", after.single { it.date == LocalDate(2026, 9, 28) }.place, "before the cut — as it was")
        val series = events.series.first().single()
        assertEquals(at(17, 30) to "Ольга Петровна", series.startMinutes to series.place)
    }

    @Test
    fun `lessons of the whole day keep no length when the length before them changes`() = runTest {
        val today = LocalDate(2026, 9, 30)
        mondays(today)
        edit(LocalDate(2026, 10, 19), scope = null, today) { copy(startMinutes = null) }
        edit(LocalDate(2026, 10, 5), scope = null, today) { copy(durationMinutes = 60) }

        val after = events.events.first()
        assertTrue(after.filter { it.date >= LocalDate(2026, 10, 5) && it.date < LocalDate(2026, 10, 19) }.all { it.startMinutes == at(17) && it.durationMinutes == 60 })
        val allDay = after.filter { it.date >= LocalDate(2026, 10, 19) }
        assertTrue(allDay.isNotEmpty() && allDay.all { it.startMinutes == null && it.durationMinutes == null }, "«весь день» has no length (spec 5.28)")
        val series = events.series.first().single()
        assertEquals(null to null, series.startMinutes to series.durationMinutes)
    }

    @Test
    fun `a weekday changed at a lesson gone by unlinks the records of todays lesson and leaves the one changed by itself single`() = runTest {
        val created = LocalDate(2026, 9, 1)
        now(created, 12)
        events.add(EventDraft(TestEvents.LESSON, LocalDate(2026, 9, 7), at(17), 45, place = "Анна Сергеевна"), Repeat.WEEKLY, until = null, today = created)
        val createdAt = clock.nowMs
        val today = LocalDate(2026, 10, 12) // a Monday: at noon its lesson is still to come, so the cut is today
        now(today, 12)
        events.layAhead(today)
        val old = events.series.first().single()
        edit(LocalDate(2026, 10, 26), EditScope.ONLY_THIS, today) { copy(startMinutes = at(18)) }
        val current = events.events.first()
        val todays = current.single { it.date == today }
        val earlier = current.single { it.date == LocalDate(2026, 10, 5) }
        val alone = current.single { it.date == LocalDate(2026, 10, 26) }
        val ofToday = record(todays.id)
        val named = record(todays.id, title = "Мой урок")
        val ofEarlier = record(earlier.id)

        // at 05.10, gone by: Tuesdays at 18:00 from now on
        val change = EventChange.of(earlier, earlier.draft().copy(date = LocalDate(2026, 10, 6), startMinutes = at(18)), Repeat.WEEKLY, Repeat.WEEKLY, null, eventsConfig)
        val plan = SeriesEdits.plan(change, EditScope.FOLLOWING, old, current, clock.instant(), clock.zone, eventsConfig)
        assertTrue(todays.id in SeriesEdits.gone(plan, current))
        events.apply(plan, frozenTitles = mapOf(todays.id to "Урок · 12 октября"), today = today)

        val after = events.events.first()
        assertNull(events.event(todays.id), "today's lesson goes with the Mondays from the cut on")
        val sessions = database.sessionDao().observeAll().first().associateBy { it.id }
        assertNull(sessions.getValue(ofToday).eventId, "its records point at nothing gone (plan D12)")
        assertEquals("Урок · 12 октября", sessions.getValue(ofToday).title)
        assertNull(sessions.getValue(named).eventId)
        assertEquals("Мой урок", sessions.getValue(named).title)
        assertEquals(earlier.id, sessions.getValue(ofEarlier).eventId, "the lesson gone by keeps its records")
        val single = after.single { it.id == alone.id }
        assertNull(single.seriesId, "the one changed by itself stays as a single event (spec 5.28)")
        assertFalse(single.detached)
        assertEquals(LocalDate(2026, 10, 26) to at(18), single.date to single.startMinutes)
        // the new repeat lays Tuesdays from the cut with the moment of the old one (plan D49)
        val newSeries = events.series.first().single { it.id != old.id }
        val tuesdays = after.filter { it.seriesId == newSeries.id }
        assertEquals(LocalDate(2026, 10, 13), tuesdays.minOf { it.date })
        assertTrue(tuesdays.all { it.date.dayOfWeek == DayOfWeek.TUESDAY && it.startMinutes == at(18) && it.createdAtEpochMs == createdAt }, "the moment of the old repeat")
        assertEquals(EventTime(at(18), 45), EventRules.timeOf(TestEvents.LESSON, after, events.series.first()), "a new lesson starts at the new repeat's time")
        // a concert created now is the last thing a person created
        now(today, 13)
        events.add(EventDraft(TestEvents.PERFORMANCE, LocalDate(2026, 10, 24), at(18, 30), 90), Repeat.NONE, until = null, today = today)
        assertEquals(TestEvents.PERFORMANCE, EventRules.defaults(events.events.first(), events.series.first()).kind)
    }

    @Test
    fun `a repeat moved from its first lesson leaves no repeat behind`() = runTest {
        val today = LocalDate(2026, 9, 27)
        mondays(today)
        val first = events.events.first().minBy { it.date }
        edit(first.date, EditScope.FOLLOWING, today) { copy(date = LocalDate(2026, 9, 29)) }

        val series = events.series.first()
        assertEquals(listOf(LocalDate(2026, 9, 29)), series.map { it.firstDate }, "the old repeat, ended before it began, goes")
        val tuesdays = events.events.first()
        assertTrue(tuesdays.all { it.seriesId == series.single().id && it.date.dayOfWeek == DayOfWeek.TUESDAY })
        assertEquals(first.id, tuesdays.minBy { it.date }.id, "the first lesson is the same event")
    }

    @Test
    fun `a time changed for this and following leaves the lessons changed by themselves and those gone by`() = runTest {
        val today = LocalDate(2026, 10, 12)
        now(LocalDate(2026, 9, 1), 12)
        events.add(EventDraft(TestEvents.LESSON, LocalDate(2026, 9, 7), at(17), 45), Repeat.WEEKLY, until = null, today = LocalDate(2026, 9, 1))
        now(today, 12)
        events.layAhead(today)
        val laid = events.events.first()
        val series = events.series.first().single()
        val november2 = laid.single { it.date == LocalDate(2026, 11, 2) }
        val alone = EventChange.of(november2, november2.draft().copy(startMinutes = at(19)), Repeat.WEEKLY, Repeat.WEEKLY, null, eventsConfig)
        events.apply(SeriesEdits.plan(alone, EditScope.ONLY_THIS, series, laid, clock.instant(), clock.zone, eventsConfig), emptyMap(), today)
        val current = events.events.first()
        val october19 = current.single { it.date == LocalDate(2026, 10, 19) }
        val later = EventChange.of(october19, october19.draft().copy(startMinutes = at(17, 30)), Repeat.WEEKLY, Repeat.WEEKLY, null, eventsConfig)
        events.apply(SeriesEdits.plan(later, scope = null, events.series.first().single(), current, clock.instant(), clock.zone, eventsConfig), emptyMap(), today)

        val after = events.events.first()
        assertEquals(current.filter { it.date < LocalDate(2026, 10, 19) }, after.filter { it.date < LocalDate(2026, 10, 19) }, "gone by and today's: as they were")
        assertEquals(at(19), after.single { it.id == november2.id }.startMinutes, "changed by itself — left alone")
        assertTrue(after.filter { it.date >= LocalDate(2026, 10, 19) && it.id != november2.id }.all { it.startMinutes == at(17, 30) })
        assertEquals(at(17, 30), events.series.first().single().startMinutes, "the template: what is laid next")
        events.layAhead(today.plusDays(7))
        assertEquals(at(17, 30), events.events.first().maxBy { it.date }.startMinutes)
    }

    @Test
    fun `a single event given a repeat keeps its id notes program and records`() = runTest {
        val repertoire = RoomRepertoireRepository(database.repertoireDao(), NoSheetFiles, RepertoireConfig(), SystemWallClock, NoOpAnalytics())
        val etude = repertoire.add(PieceDraft(title = "Этюд № 3", composer = "Р. Крейцер"), nowEpochMs = 1)
        val today = LocalDate(2026, 10, 1)
        now(today, 12)
        val id = events.add(EventDraft(TestEvents.LESSON, LocalDate(2026, 10, 7), at(16), 60), Repeat.NONE, until = null, today = today)
        events.setNotes(id, "  Этюд к среде ")
        events.setProgram(id, listOf(etude))
        val taken = record(id)
        val single = assertNotNull(events.event(id))
        assertEquals("Этюд к среде", single.notes)

        val change = EventChange.of(single, single.draft(), Repeat.NONE, Repeat.WEEKLY, afterUntil = null, eventsConfig)
        val plan = SeriesEdits.plan(change, scope = null, series = null, seriesEvents = emptyList(), clock.instant(), clock.zone, eventsConfig)
        events.apply(plan, frozenTitles = emptyMap(), today = today)

        val first = assertNotNull(events.event(id), "the same event")
        val series = events.series.first().single()
        assertEquals(series.id, first.seriesId)
        assertEquals("Этюд к среде", first.notes)
        assertEquals(listOf(etude), events.programs.first()[id])
        assertEquals(id, database.sessionDao().session(taken)?.eventId)
        val wednesdays = events.events.first()
        assertEquals(LocalDate(2026, 10, 7), wednesdays.minOf { it.date })
        assertEquals(12, wednesdays.size, "07.10 and the Wednesdays to the horizon 24.12")
        assertTrue(wednesdays.all { it.seriesId == series.id && it.date.dayOfWeek == DayOfWeek.WEDNESDAY })
    }

    @Test
    fun `deleting this and following unlinks the records of the selected event too`() = runTest {
        val today = LocalDate(2026, 10, 1)
        now(today, 12)
        events.add(EventDraft(TestEvents.LESSON, LocalDate(2026, 9, 28), at(17), 45), Repeat.WEEKLY, until = null, today = today)
        val laid = events.events.first()
        val series = events.series.first().single()
        // the lesson of 19.10 was moved by itself to 18:00, the one of 02.11 too
        for (date in listOf(LocalDate(2026, 10, 19), LocalDate(2026, 11, 2))) {
            val lesson = laid.single { it.date == date }
            val change = EventChange.of(lesson, lesson.draft().copy(startMinutes = at(18)), Repeat.WEEKLY, Repeat.WEEKLY, null, eventsConfig)
            events.apply(SeriesEdits.plan(change, EditScope.ONLY_THIS, series, laid, clock.instant(), clock.zone, eventsConfig), emptyMap(), today)
        }
        val current = events.events.first()
        val selected = current.single { it.date == LocalDate(2026, 10, 19) }
        assertTrue(selected.detached)
        val following = current.single { it.date == LocalDate(2026, 10, 26) }
        val earlier = current.single { it.date == LocalDate(2026, 10, 12) }
        val ofSelected = record(selected.id)
        val ofFollowing = record(following.id)
        val ofEarlier = record(earlier.id)

        val plan = SeriesEdits.deletePlan(selected, EditScope.FOLLOWING, events.series.first().single(), current, clock.instant(), clock.zone, eventsConfig)
        val gone = SeriesEdits.gone(plan, current)
        assertTrue(selected.id in gone && following.id in gone)
        events.apply(plan, frozenTitles = mapOf(selected.id to "Урок · 19 октября", following.id to "Урок · 26 октября"), today = today)

        val after = events.events.first()
        assertEquals(current.filter { it.date < LocalDate(2026, 10, 19) }, after.filter { it.date < LocalDate(2026, 10, 19) })
        val left = after.filter { it.date >= LocalDate(2026, 10, 19) }
        assertEquals(listOf(LocalDate(2026, 11, 2)), left.map { it.date }, "only the one changed by itself stays")
        assertNull(left.single().seriesId, "as a single event")
        assertEquals(LocalDate(2026, 10, 18), events.series.first().single().until)
        events.layAhead(today.plusDays(30))
        assertEquals(after, events.events.first(), "the repeat that ended lays nothing more")
        val sessions = database.sessionDao().observeAll().first().associateBy { it.id }
        assertNull(sessions.getValue(ofSelected).eventId, "the selected event changed by itself loses its records too (plan D12)")
        assertEquals("Урок · 19 октября", sessions.getValue(ofSelected).title)
        assertNull(sessions.getValue(ofFollowing).eventId)
        assertEquals("Урок · 26 октября", sessions.getValue(ofFollowing).title)
        assertEquals(earlier.id, sessions.getValue(ofEarlier).eventId, "a lesson before the cut keeps its records")
        assertNull(sessions.getValue(ofEarlier).title)
    }

    @Test
    fun `deleting a repeat from its first lesson leaves nothing of it`() = runTest {
        val today = LocalDate(2026, 9, 27)
        now(today, 12)
        events.add(EventDraft(TestEvents.LESSON, LocalDate(2026, 9, 28), at(17), 45), Repeat.WEEKLY, until = null, today = today)
        val laid = events.events.first()
        val plan = SeriesEdits.deletePlan(laid.first(), EditScope.FOLLOWING, events.series.first().single(), laid, clock.instant(), clock.zone, eventsConfig)
        events.apply(plan, frozenTitles = emptyMap(), today = today)
        assertEquals(emptyList(), events.events.first())
        assertEquals(emptyList(), events.series.first(), "a repeat with no events and no dates ahead goes")
    }

    private fun take(pieceId: Long?) = SessionEntity(
        title = null, startedAtEpochMs = 1_790_000_000_000, durationMs = 60_000, a4Hz = 440.0, toleranceCents = 8.0, nearCents = 20.0,
        scorePercent = 80, nearPercent = 15, offPercent = 5, maeCents = 6.5, biasCents = -2.0, previewZones = "ININ",
        audioPath = "take.m4a", pieceId = pieceId,
    )

    private companion object {
        /** The kind of a thing of the home, as RoomHomeRepository writes it. */
        const val ITEM = "ITEM"
        const val ROUNDS = 20

        /** Where «Этот и следующие» begins for an edit on Monday 12.10 after the lesson: the next day. */
        val CUT = LocalDate(2026, 10, 13)
    }
}
