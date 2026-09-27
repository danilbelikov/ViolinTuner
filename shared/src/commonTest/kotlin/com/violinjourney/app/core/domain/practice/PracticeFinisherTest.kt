package com.violinjourney.app.core.domain.practice

import com.violinjourney.app.core.domain.journey.FakeJourneyRepository
import com.violinjourney.app.core.domain.journey.FakePracticeNotesStore
import com.violinjourney.app.core.domain.journey.JourneyConfig
import com.violinjourney.app.core.domain.journey.NoteCount
import com.violinjourney.app.core.domain.journey.PracticeNotesStore
import com.violinjourney.app.core.time.FixedWallClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone

/** Answers to one practice that meet — a double tap, the sheet and the prompt — end it once (spec 5.6). */
class PracticeFinisherTest {
    private val clock = FixedWallClock(Instant.fromEpochMilliseconds(9_000_000), TimeZone.UTC)
    private val store = FakeRunningPracticeStore()
    private val notes = FakePracticeNotesStore()
    private val journey = FakeJourneyRepository()
    private val blocks = FakeBlockStore()
    private val history = FakePieceBlockRepository()
    private val halfAnHour = 30 * 60_000L

    private fun finisher(practice: PracticeRepository, notes: PracticeNotesStore = this.notes) =
        PracticeFinisher(practice, store, clock, notes, journey, JourneyConfig(), blocks, history, PracticeConfig())

    @Test
    fun `two saves of one practice at once store it once`() = runTest {
        // a row takes a moment to write, as in the database: the second answer comes while the first one writes
        val practice = FakePracticeRepository(addDelayMs = 1)
        val finisher = finisher(practice)
        store.startIfIdle(1_000)
        notes.add(1_000, NoteCount(played = 30, inTune = 30))

        val results = listOf(async { finisher.save(1_000, halfAnHour) }, async { finisher.save(1_000, halfAnHour) }).awaitAll()

        assertEquals(1, practice.entries.value.size, "one row of the practice")
        assertEquals(1, journey.earnings.size, "its takts are paid once")
        assertEquals(1, results.count { it != null }, "one answer stored it, the other found it ended")
        assertNull(store.running.value)
    }

    @Test
    fun `a save whose caller goes away halfway still ends the practice`() = runTest {
        val practice = FakePracticeRepository()
        // the notes are read after the row is written: the caller goes in between
        val slowNotes = object : PracticeNotesStore by notes {
            override suspend fun countFor(practiceStartedAtEpochMs: Long): NoteCount {
                delay(1)
                return notes.countFor(practiceStartedAtEpochMs)
            }
        }
        val finisher = finisher(practice, slowNotes)
        store.startIfIdle(1_000)

        val answer = launch { finisher.save(1_000, halfAnHour) }
        runCurrent()
        assertEquals(1, practice.entries.value.size, "the row is written")
        answer.cancel()
        advanceUntilIdle()

        assertEquals(1, journey.earnings.size, "the takts are paid all the same")
        assertNull(store.running.value, "a stored practice does not run on, to be stored again by the next answer")
        assertNull(finisher.save(1_000, halfAnHour))
        assertEquals(1, practice.entries.value.size)
    }

    @Test
    fun `a save tells what it stored and is saving until then`() = runTest {
        val practice = FakePracticeRepository(addDelayMs = 1)
        val finisher = finisher(practice)
        store.startIfIdle(1_000)
        assertNull(finisher.lastSaved.value)

        val saved = async { finisher.save(1_000, halfAnHour) }
        runCurrent()
        assertTrue(finisher.saving.value, "the row is being written")
        assertNull(finisher.lastSaved.value)

        advanceUntilIdle()
        val earning = saved.await()
        assertFalse(finisher.saving.value)
        val last = assertNotNull(finisher.lastSaved.value)
        assertEquals(earning, last.earning)
        assertEquals(practice.entries.value.single(), last.entry, "the row as stored — with its id")
        assertEquals(halfAnHour, last.entry.durationMs)

        // an answer to a practice that no longer runs stores nothing, and tells nothing new
        assertNull(finisher.save(1_000, halfAnHour))
        assertEquals(last, finisher.lastSaved.value)
        assertFalse(finisher.saving.value)
    }

    @Test
    fun `a discard of another practice leaves the running one`() = runTest {
        val finisher = finisher(FakePracticeRepository())
        store.startIfIdle(1_000)
        notes.add(1_000, NoteCount(played = 10, inTune = 8))

        // the sheet of a practice begun at 999 — saved from the prompt meanwhile — answered «Не сохранять» late
        finisher.discard(999)
        assertEquals(1_000, store.running.value?.startedAtEpochMs, "the running practice is not the sheet's")
        assertEquals(NoteCount(played = 10, inTune = 8), notes.countFor(1_000), "nor are its notes")

        finisher.discard(1_000)
        assertNull(store.running.value)
        assertEquals(NoteCount.ZERO, notes.countFor(1_000))
    }

    @Test
    fun `a discard that meets a save waits for it and takes nothing from it`() = runTest {
        val practice = FakePracticeRepository(addDelayMs = 1)
        val finisher = finisher(practice)
        store.startIfIdle(1_000)
        notes.add(1_000, NoteCount(played = 30, inTune = 30))

        val saved = async { finisher.save(1_000, halfAnHour) }
        runCurrent()
        val discarded = launch { finisher.discard(1_000) }
        runCurrent()
        assertNotNull(store.running.value, "the discard waits while the save writes")

        advanceUntilIdle()
        discarded.join()
        assertNotNull(saved.await())
        assertEquals(1, practice.entries.value.size)
        assertEquals(30, journey.earnings.single().notesInTune, "the notes of the practice were not cleared from under the save")
        assertNull(store.running.value)
    }
}
