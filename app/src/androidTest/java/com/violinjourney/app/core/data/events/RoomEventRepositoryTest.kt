package com.violinjourney.app.core.data.events

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.analytics.Analytics
import com.violinjourney.app.core.analytics.AnalyticsEvent
import com.violinjourney.app.core.analytics.ErrorGroup
import com.violinjourney.app.core.data.AppDatabase
import com.violinjourney.app.core.data.repertoire.RoomRepertoireRepository
import com.violinjourney.app.core.data.repertoire.SheetFiles
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
import com.violinjourney.app.core.domain.events.Repeat
import com.violinjourney.app.core.domain.events.SeriesEdits
import com.violinjourney.app.core.domain.events.draft
import com.violinjourney.app.core.domain.repertoire.PieceDraft
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.time.WallClock
import java.io.File
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The events on Android's own SQLite (spec 3.35, plan 5.4): what the plans of `SeriesEdits` do to the rows, the horizon,
 * the recordings of an event. Runs on a device or emulator: `./gradlew :app:connectedDebugAndroidTest`; the same rules
 * run on iOS in every `:shared:iosSimulatorArm64Test` (`IosDaoTest`).
 */
@RunWith(AndroidJUnit4::class)
class RoomEventRepositoryTest {
    private object NoSheetFiles : SheetFiles {
        override suspend fun import(sourceUri: String): SheetFiles.Stored? = null
        override fun existing(name: String): File? = null
        override suspend fun delete(names: Collection<String>) = Unit
        override suspend fun deleteOrphans(referenced: Set<String>, nowEpochMs: Long, minAgeMs: Long) = Unit
        override fun newCameraFile(): File = File("unused")
    }

    /** A clock the test moves by hand. */
    private class SettableClock(var nowMs: Long, override val zone: TimeZone) : WallClock {
        override fun instant(): Instant = Instant.fromEpochMilliseconds(nowMs)
    }

    /** What would have been sent, as the service would see it. */
    private class RecordingAnalytics : Analytics {
        val sent = mutableListOf<String>()

        override fun track(event: AnalyticsEvent) {
            sent += "${event.name} ${event.params}"
        }

        override fun error(group: ErrorGroup, message: String, cause: Throwable?) = Unit
    }

    private val zone = TimeZone.of("Europe/Moscow")
    private val config = EventsConfig()
    private val clock = SettableClock(0, zone)
    private val analytics = RecordingAnalytics()
    private lateinit var database: AppDatabase
    private lateinit var events: RoomEventRepository

    private val lesson = KindRef.BuiltIn(BuiltInKind.LESSON)
    private val performance = KindRef.BuiltIn(BuiltInKind.PERFORMANCE)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        events = RoomEventRepository(database.eventDao(), config, clock, analytics, Dispatchers.IO)
    }

    @After
    fun tearDown() = database.close()

    private fun minutes(hours: Int, minutes: Int = 0) = hours * 60 + minutes

    private fun now(date: LocalDate, hours: Int, minutes: Int = 0) {
        clock.nowMs = date.atTime(hours, minutes).toInstant(zone).toEpochMilliseconds()
    }

    private fun LocalDate.plusDays(days: Int): LocalDate = plus(days, DateTimeUnit.DAY)

    private suspend fun record(eventId: Long?, title: String? = null): Long = database.sessionDao().insert(
        SessionEntity(
            title = title, startedAtEpochMs = 1_790_000_000_000, durationMs = 60_000, a4Hz = 440.0, toleranceCents = 8.0, nearCents = 20.0,
            scorePercent = 80, nearPercent = 15, offPercent = 5, maeCents = 6.5, biasCents = -2.0, previewZones = "ININ", audioPath = "take.m4a",
            eventId = eventId,
        ),
        bucketMs = 50, samples = ByteArray(3),
    )

    /** A weekly lesson since 07.09; on Monday 12.10 after the lesson, an edit of the lesson of 05.10 gone by with «Этот и следующие». */
    private fun pastLessonEdited(afterRepeat: Repeat, edit: EventDraft.() -> EventDraft): List<CalendarEvent> = runBlocking {
        val created = LocalDate(2026, 9, 1)
        now(created, 12)
        events.add(EventDraft(lesson, LocalDate(2026, 9, 7), minutes(17), 45, place = "Анна Сергеевна"), Repeat.WEEKLY, until = null, today = created)
        val today = LocalDate(2026, 10, 12)
        now(today, 18)
        events.layAhead(today)
        val before = events.events.first()
        val selected = before.single { it.date == LocalDate(2026, 10, 5) }
        val series = events.series.first().single()
        val change = EventChange.of(selected, selected.draft().edit(), Repeat.WEEKLY, afterRepeat, afterUntil = null, config)
        events.apply(SeriesEdits.plan(change, EditScope.FOLLOWING, series, before, clock.instant(), zone, config), emptyMap(), today)
        val after = events.events.first()
        // plan D4: the cut is 13.10 — today's lesson is over; not one event before it is new, changed or gone
        assertEquals(before.filter { it.date < CUT }, after.filter { it.date < CUT })
        assertEquals(LocalDate(2026, 10, 12), events.series.first().single { it.id == series.id }.until)
        after
    }

    @Test
    fun aWeekdayChangedAtAPastLessonChangesNothingBeforeTheCut() {
        val after = pastLessonEdited(Repeat.WEEKLY) { copy(date = LocalDate(2026, 10, 6), startMinutes = minutes(18)) }
        val fromCut = after.filter { it.date >= CUT }
        assertEquals(LocalDate(2026, 10, 13), fromCut.minOf { it.date })
        assertTrue(fromCut.all { it.date.dayOfWeek == DayOfWeek.TUESDAY && it.startMinutes == minutes(18) })
    }

    @Test
    fun aStepChangedAtAPastLessonChangesNothingBeforeTheCut() {
        val after = pastLessonEdited(Repeat.BIWEEKLY) { this }
        assertEquals(LocalDate(2026, 10, 19), after.filter { it.date >= CUT }.minOf { it.date })
    }

    @Test
    fun noRepeatAtAPastLessonChangesNothingBeforeTheCut() {
        val after = pastLessonEdited(Repeat.NONE) { this }
        assertTrue(after.none { it.date >= CUT })
        assertEquals(6, after.size)
    }

    @Test
    fun lessonsLaidAfterAConcertDoNotMakeTheNextEventALesson() = runBlocking {
        val today = LocalDate(2026, 9, 27)
        now(today, 12)
        events.add(EventDraft(lesson, LocalDate(2026, 9, 28), minutes(17), 45), Repeat.WEEKLY, until = null, today = today)
        now(today, 13)
        events.add(EventDraft(performance, LocalDate(2026, 10, 24), minutes(18, 30), 90), Repeat.NONE, until = null, today = today)
        events.layAhead(today.plusDays(14))
        val all = events.events.first()
        assertEquals(15, all.size)
        assertEquals(performance, EventRules.defaults(all, events.series.first()).kind)
        // and the statistics know of the two a person created, not of the lessons the horizon laid (spec 3.34)
        assertEquals(listOf("event_added {kind=lesson, repeat=weekly}", "event_added {kind=performance, repeat=none}"), analytics.sent)
    }

    @Test
    fun layingAheadTwiceAtOnceLaysOnce() = runBlocking {
        val today = LocalDate(2026, 9, 27)
        now(today, 12)
        events.add(EventDraft(lesson, LocalDate(2026, 9, 28), minutes(17), 45), Repeat.WEEKLY, until = null, today = today)
        assertEquals(12, events.events.first().size)
        val later = today.plusDays(7)
        withContext(Dispatchers.Default) { coroutineScope { List(2) { async { events.layAhead(later) } }.awaitAll() } }
        assertEquals(13, events.events.first().size)
        events.layAhead(later)
        assertEquals(13, events.events.first().size)
    }

    @Test
    fun aSingleEventGivenARepeatKeepsItsIdNotesProgramAndRecords() = runBlocking {
        val repertoire = RoomRepertoireRepository(database.repertoireDao(), NoSheetFiles, RepertoireConfig(), clock, analytics)
        val etude = repertoire.add(PieceDraft(title = "Этюд № 3"), nowEpochMs = 1)
        val today = LocalDate(2026, 10, 1)
        now(today, 12)
        val id = events.add(EventDraft(lesson, LocalDate(2026, 10, 7), minutes(16), 60, notes = "Этюд к среде"), Repeat.NONE, until = null, today = today)
        events.setProgram(id, listOf(etude))
        val taken = record(id)
        val single = events.event(id)!!
        val change = EventChange.of(single, single.draft(), Repeat.NONE, Repeat.WEEKLY, afterUntil = null, config)
        events.apply(SeriesEdits.plan(change, null, null, emptyList(), clock.instant(), zone, config), emptyMap(), today)
        val first = events.event(id)!!
        assertEquals(events.series.first().single().id, first.seriesId)
        assertEquals("Этюд к среде", first.notes)
        assertEquals(listOf(etude), events.programs.first()[id])
        assertEquals(id, database.sessionDao().session(taken)?.eventId)
        assertEquals(12, events.events.first().size)
    }

    @Test
    fun thisAndFollowingOfAnEventChangedByItselfUnlinksItsRecordsWithTheirNames() = runBlocking {
        val today = LocalDate(2026, 10, 1)
        now(today, 12)
        events.add(EventDraft(lesson, LocalDate(2026, 9, 28), minutes(17), 45), Repeat.WEEKLY, until = null, today = today)
        val laid = events.events.first()
        val series = events.series.first().single()
        val october19 = laid.single { it.date == LocalDate(2026, 10, 19) }
        val moved = EventChange.of(october19, october19.draft().copy(startMinutes = minutes(18)), Repeat.WEEKLY, Repeat.WEEKLY, null, config)
        events.apply(SeriesEdits.plan(moved, EditScope.ONLY_THIS, series, laid, clock.instant(), zone, config), emptyMap(), today)
        val current = events.events.first()
        val selected = current.single { it.id == october19.id }
        assertTrue(selected.detached)
        val ofSelected = record(selected.id)
        val withName = record(selected.id, title = "Мой урок")

        val plan = SeriesEdits.deletePlan(selected, EditScope.FOLLOWING, events.series.first().single(), current, clock.instant(), zone, config)
        events.apply(plan, mapOf(selected.id to "Урок · 19 октября"), today)
        assertNull(events.event(selected.id))
        assertNull(database.sessionDao().session(ofSelected)?.eventId)
        assertEquals("Урок · 19 октября", database.sessionDao().session(ofSelected)?.title)
        assertEquals("Мой урок", database.sessionDao().session(withName)?.title)
        assertTrue(events.events.first().all { it.date < LocalDate(2026, 10, 19) })
    }

    /** A weekly lesson since 28.09 at 17:00 for 45 minutes with Анна Сергеевна, created on [today] at noon. */
    private suspend fun mondays(today: LocalDate) {
        now(today, 12)
        events.add(EventDraft(lesson, LocalDate(2026, 9, 28), minutes(17), 45, place = "Анна Сергеевна"), Repeat.WEEKLY, until = null, today = today)
    }

    /** Saves [edit] of the lesson of [date] with the answer [scope] — null, the filled one — as the screen will. */
    private suspend fun edit(date: LocalDate, scope: EditScope?, today: LocalDate, edit: EventDraft.() -> EventDraft) {
        val current = events.events.first()
        val selected = current.single { it.date == date }
        val change = EventChange.of(selected, selected.draft().edit(), Repeat.WEEKLY, Repeat.WEEKLY, afterUntil = null, config)
        val series = events.series.first().single { it.id == selected.seriesId }
        events.apply(SeriesEdits.plan(change, scope, series, current, clock.instant(), zone, config), emptyMap(), today)
    }

    @Test
    fun thisAndFollowingWritesWhatItChangedAndNotWhatOneLessonHasByItself() = runBlocking {
        val today = LocalDate(2026, 10, 1)
        mondays(today)
        val october19 = LocalDate(2026, 10, 19)
        edit(october19, EditScope.ONLY_THIS, today) { copy(place = "Ольга Петровна") }
        edit(october19, scope = null, today) { copy(durationMinutes = 60) }

        val after = events.events.first()
        val selected = after.single { it.date == october19 }
        assertEquals("Ольга Петровна" to 60, selected.place to selected.durationMinutes)
        assertTrue(after.filter { it.date > october19 }.all { it.place == "Анна Сергеевна" && it.durationMinutes == 60 && it.startMinutes == minutes(17) })
        assertTrue(after.filter { it.date < october19 }.all { it.durationMinutes == 45 })
        val series = events.series.first().single()
        assertEquals("Анна Сергеевна" to 60, series.place to series.durationMinutes)
    }

    @Test
    fun anEditBeforeAnEarlierOneChangesTheFollowingLessonsOnlyInWhatItChanged() = runBlocking {
        val today = LocalDate(2026, 9, 30)
        mondays(today)
        edit(LocalDate(2026, 10, 19), scope = null, today) { copy(startMinutes = minutes(17, 30)) }
        edit(LocalDate(2026, 10, 5), scope = null, today) { copy(place = "Ольга Петровна") }

        val after = events.events.first()
        val october = after.filter { it.date >= LocalDate(2026, 10, 5) && it.date < LocalDate(2026, 10, 19) }
        assertEquals(listOf(LocalDate(2026, 10, 5), LocalDate(2026, 10, 12)), october.map { it.date })
        assertTrue(october.all { it.startMinutes == minutes(17) && it.place == "Ольга Петровна" })
        assertTrue(after.filter { it.date >= LocalDate(2026, 10, 19) }.all { it.startMinutes == minutes(17, 30) && it.place == "Ольга Петровна" })
        assertEquals("Анна Сергеевна", after.single { it.date == LocalDate(2026, 9, 28) }.place)
        val series = events.series.first().single()
        assertEquals(minutes(17, 30) to "Ольга Петровна", series.startMinutes to series.place)
    }

    @Test
    fun lessonsOfTheWholeDayKeepNoLengthWhenTheLengthBeforeThemChanges() = runBlocking {
        val today = LocalDate(2026, 9, 30)
        mondays(today)
        edit(LocalDate(2026, 10, 19), scope = null, today) { copy(startMinutes = null) }
        edit(LocalDate(2026, 10, 5), scope = null, today) { copy(durationMinutes = 60) }

        val after = events.events.first()
        assertTrue(after.filter { it.date >= LocalDate(2026, 10, 5) && it.date < LocalDate(2026, 10, 19) }.all { it.startMinutes == minutes(17) && it.durationMinutes == 60 })
        val allDay = after.filter { it.date >= LocalDate(2026, 10, 19) }
        assertTrue(allDay.isNotEmpty() && allDay.all { it.startMinutes == null && it.durationMinutes == null })
        val series = events.series.first().single()
        assertEquals(null to null, series.startMinutes to series.durationMinutes)
    }

    @Test
    fun aWeekdayChangedAtALessonGoneByUnlinksTheRecordsOfTodaysLessonAndLeavesTheOneChangedByItselfSingle() = runBlocking {
        val created = LocalDate(2026, 9, 1)
        now(created, 12)
        events.add(EventDraft(lesson, LocalDate(2026, 9, 7), minutes(17), 45, place = "Анна Сергеевна"), Repeat.WEEKLY, until = null, today = created)
        val createdAt = clock.nowMs
        val today = LocalDate(2026, 10, 12) // a Monday: at noon its lesson is still to come, so the cut is today
        now(today, 12)
        events.layAhead(today)
        val old = events.series.first().single()
        edit(LocalDate(2026, 10, 26), EditScope.ONLY_THIS, today) { copy(startMinutes = minutes(18)) }
        val current = events.events.first()
        val todays = current.single { it.date == today }
        val earlier = current.single { it.date == LocalDate(2026, 10, 5) }
        val alone = current.single { it.date == LocalDate(2026, 10, 26) }
        val ofToday = record(todays.id)
        val named = record(todays.id, title = "Мой урок")
        val ofEarlier = record(earlier.id)

        val change = EventChange.of(earlier, earlier.draft().copy(date = LocalDate(2026, 10, 6), startMinutes = minutes(18)), Repeat.WEEKLY, Repeat.WEEKLY, null, config)
        val plan = SeriesEdits.plan(change, EditScope.FOLLOWING, old, current, clock.instant(), zone, config)
        assertTrue(todays.id in SeriesEdits.gone(plan, current))
        events.apply(plan, mapOf(todays.id to "Урок · 12 октября"), today)

        val after = events.events.first()
        assertNull(events.event(todays.id))
        assertNull(database.sessionDao().session(ofToday)?.eventId)
        assertEquals("Урок · 12 октября", database.sessionDao().session(ofToday)?.title)
        assertNull(database.sessionDao().session(named)?.eventId)
        assertEquals("Мой урок", database.sessionDao().session(named)?.title)
        assertEquals(earlier.id, database.sessionDao().session(ofEarlier)?.eventId)
        val single = after.single { it.id == alone.id }
        assertNull(single.seriesId)
        assertFalse(single.detached)
        assertEquals(LocalDate(2026, 10, 26) to minutes(18), single.date to single.startMinutes)
        val newSeries = events.series.first().single { it.id != old.id }
        val tuesdays = after.filter { it.seriesId == newSeries.id }
        assertEquals(LocalDate(2026, 10, 13), tuesdays.minOf { it.date })
        assertTrue(tuesdays.all { it.date.dayOfWeek == DayOfWeek.TUESDAY && it.startMinutes == minutes(18) && it.createdAtEpochMs == createdAt })
        assertEquals(EventTime(minutes(18), 45), EventRules.timeOf(lesson, after, events.series.first()))
        now(today, 13)
        events.add(EventDraft(performance, LocalDate(2026, 10, 24), minutes(18, 30), 90), Repeat.NONE, until = null, today = today)
        assertEquals(performance, EventRules.defaults(events.events.first(), events.series.first()).kind)
    }

    @Test
    fun aRepeatMovedFromItsFirstLessonLeavesNoRepeatBehind() = runBlocking {
        val today = LocalDate(2026, 9, 27)
        mondays(today)
        val first = events.events.first().minBy { it.date }
        edit(first.date, EditScope.FOLLOWING, today) { copy(date = LocalDate(2026, 9, 29)) }

        val series = events.series.first()
        assertEquals(listOf(LocalDate(2026, 9, 29)), series.map { it.firstDate })
        val tuesdays = events.events.first()
        assertTrue(tuesdays.all { it.seriesId == series.single().id && it.date.dayOfWeek == DayOfWeek.TUESDAY })
        assertEquals(first.id, tuesdays.minBy { it.date }.id)
    }

    @Test
    fun aRenamedEventRenamesItsRecordsAtOnce() = runBlocking {
        val today = LocalDate(2026, 10, 24)
        now(today, 21)
        val id = events.add(EventDraft(performance, today, minutes(18, 30), 90, title = "Концерт"), Repeat.NONE, until = null, today = today)
        record(id)
        assertEquals("Концерт", events.recordEvents.first().getValue(id).title)
        val event = events.event(id)!!
        val renamed = EventChange.of(event, event.draft().copy(title = "Осенний концерт"), Repeat.NONE, Repeat.NONE, null, config)
        events.apply(SeriesEdits.plan(renamed, null, null, emptyList(), clock.instant(), zone, config), emptyMap(), today)
        assertEquals("Осенний концерт", events.recordEvents.first().getValue(id).title)
    }

    private companion object {
        val CUT = LocalDate(2026, 10, 13)
    }
}
