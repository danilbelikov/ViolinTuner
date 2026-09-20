package com.example.violintuner.core.domain.journey

import com.example.violintuner.core.domain.practice.FakePracticeRepository
import com.example.violintuner.core.domain.practice.FakeRunningPracticeStore
import com.example.violintuner.core.domain.practice.PracticeFinisher
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A saved practice pays for the road (spec 5.17): clean notes and minutes at the stand. */
class PracticeEarnsTaktsTest {
    private val clock: Clock = Clock.fixed(Instant.ofEpochMilli(9_000_000), ZoneOffset.UTC)
    private val practice = FakePracticeRepository()
    private val store = FakeRunningPracticeStore()
    private val notes = FakePracticeNotesStore()
    private val journey = FakeJourneyRepository()
    private val finisher = PracticeFinisher(practice, store, clock, notes, journey, JourneyConfig())

    @Test
    fun `saving a practice earns its notes in tune and two takts a minute, once`() = runTest {
        store.start(1_000)
        notes.add(1_000, NoteCount(played = 412, inTune = 264))
        assertTrue(finisher.save(1_000, durationMs = 38 * 60_000L))
        assertEquals(TaktEarning(9_000_000, 412, 264, 38 * 60_000L, takts = 340), journey.earnings.single())
        assertEquals(NoteCount.ZERO, notes.count)
        // a second answer to the same practice stores and earns nothing
        assertTrue(!finisher.save(1_000, 38 * 60_000L))
        assertEquals(1, journey.earnings.size)
    }

    @Test
    fun `notes of another practice are not this one's, and a discarded practice earns nothing`() = runTest {
        notes.add(500, NoteCount(100, 90)) // left behind by a practice that never ended properly
        store.start(1_000)
        assertTrue(finisher.save(1_000, durationMs = 10 * 60_000L))
        assertEquals(20, journey.earnings.single().takts)

        store.start(2_000)
        notes.add(2_000, NoteCount(50, 50))
        finisher.discard()
        assertEquals(1, journey.earnings.size)
        assertEquals(NoteCount.ZERO, notes.countFor(2_000))
    }
}
