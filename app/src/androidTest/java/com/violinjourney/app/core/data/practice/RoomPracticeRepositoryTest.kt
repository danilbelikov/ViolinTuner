package com.violinjourney.app.core.data.practice

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.data.AppDatabase
import com.violinjourney.app.core.domain.practice.PracticeEntry
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on a device or emulator: `./gradlew :app:connectedDebugAndroidTest`. */
@RunWith(AndroidJUnit4::class)
class RoomPracticeRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: RoomPracticeRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        repository = RoomPracticeRepository(database.practiceDao())
    }

    @After
    fun tearDown() = database.close()

    private fun entry(date: String, startedAt: Long, minutes: Int) = PracticeEntry(
        date = LocalDate.parse(date),
        startedAtEpochMs = startedAt,
        durationMs = minutes * 60_000L,
        manual = false,
    )

    @Test
    fun entriesComeBackOrderedByDayThenStart() = runBlocking {
        repository.add(entry("2026-09-17", startedAt = 5_000, minutes = 10))
        repository.add(entry("2026-09-16", startedAt = 9_000, minutes = 20))
        repository.add(entry("2026-09-17", startedAt = 1_000, minutes = 30))
        val entries = repository.entries.first()
        assertEquals(listOf(9_000L, 1_000L, 5_000L), entries.map { it.startedAtEpochMs })
        assertEquals(listOf(20, 30, 10), entries.map { (it.durationMs / 60_000).toInt() })
    }

    @Test
    fun replaceDayLeavesOneManualEntryAndOtherDaysAlone() = runBlocking {
        repository.add(entry("2026-09-17", startedAt = 1_000, minutes = 10))
        repository.add(entry("2026-09-17", startedAt = 5_000, minutes = 20))
        repository.add(entry("2026-09-16", startedAt = 9_000, minutes = 40))

        repository.replaceDay(LocalDate.parse("2026-09-17"), durationMs = 45 * 60_000L, startedAtEpochMs = 7_000)

        val entries = repository.entries.first()
        assertEquals(2, entries.size)
        val edited = entries.single { it.date == LocalDate.parse("2026-09-17") }
        assertEquals(45 * 60_000L, edited.durationMs)
        assertEquals(7_000L, edited.startedAtEpochMs)
        assertEquals(true, edited.manual)
        assertEquals(40 * 60_000L, entries.single { it.date == LocalDate.parse("2026-09-16") }.durationMs)
    }

    @Test
    fun replaceDayWithZeroRemovesTheDay() = runBlocking {
        repository.add(entry("2026-09-17", startedAt = 1_000, minutes = 10))
        repository.replaceDay(LocalDate.parse("2026-09-17"), durationMs = 0, startedAtEpochMs = 7_000)
        assertEquals(emptyList<PracticeEntry>(), repository.entries.first())
    }
}
