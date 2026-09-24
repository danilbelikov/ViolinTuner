package com.violinjourney.app.core.domain.journey

import com.violinjourney.app.core.analytics.FakeAnalytics
import com.violinjourney.app.core.domain.practice.BlockRules
import com.violinjourney.app.core.domain.practice.FakeBlockStore
import com.violinjourney.app.core.domain.practice.FakePieceBlockRepository
import com.violinjourney.app.core.domain.practice.FakePracticeRepository
import com.violinjourney.app.core.domain.practice.FakeRunningPracticeStore
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.practice.PracticeFinisher
import com.violinjourney.app.core.domain.practice.SavedBlock
import com.violinjourney.app.core.time.FixedWallClock
import com.violinjourney.app.core.time.WallClock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A saved practice pays for the road (spec 5.17): clean notes and minutes at the stand. */
class PracticeEarnsTaktsTest {
    private val clock: WallClock = FixedWallClock(Instant.fromEpochMilliseconds(9_000_000), TimeZone.UTC)
    private val practice = FakePracticeRepository()
    private val store = FakeRunningPracticeStore()
    private val notes = FakePracticeNotesStore()
    private val journey = FakeJourneyRepository()
    private val blocks = FakeBlockStore()
    private val history = FakePieceBlockRepository()
    private val analytics = FakeAnalytics()
    private val finisher = PracticeFinisher(practice, store, clock, notes, journey, JourneyConfig(), blocks, history, PracticeConfig(), analytics)
    private val min = 60_000L

    @Test
    fun `saving a practice earns a takt for every three notes in tune and two takts a minute, once`() = runTest {
        store.start(1_000)
        notes.add(1_000, NoteCount(played = 412, inTune = 264))
        val saved = finisher.save(1_000, durationMs = 38 * 60_000L)
        assertEquals(TaktEarning(9_000_000, 412, 264, 38 * 60_000L, takts = 164), journey.earnings.single())
        // what the save hands back is the row it stored: «Занятие сохранено» shows exactly that (spec 3.31)
        assertEquals(journey.earnings.single(), saved)
        assertEquals(NoteCount.ZERO, notes.count)
        // the statistics learn the shape of the practice and nothing about what was played (spec 3.34)
        assertEquals(listOf("practice_finished {minutes=38, blocks=0, bars=164}"), analytics.sent())
        // a second answer to the same practice stores and earns nothing
        assertTrue(finisher.save(1_000, 38 * 60_000L) == null)
        assertEquals(1, journey.earnings.size)
        assertEquals("a practice already saved is not counted twice", 1, analytics.sent().size)
    }

    @Test
    fun `notes of another practice are not this one's, and a discarded practice earns nothing`() = runTest {
        notes.add(500, NoteCount(100, 90)) // left behind by a practice that never ended properly
        store.start(1_000)
        assertTrue(finisher.save(1_000, durationMs = 10 * 60_000L) != null)
        assertEquals(20, journey.earnings.single().takts)

        store.start(2_000)
        notes.add(2_000, NoteCount(50, 50))
        finisher.discard()
        assertEquals(1, journey.earnings.size)
        assertEquals(NoteCount.ZERO, notes.countFor(2_000))
    }

    /** Starts a block of [pieceId] for [goalMinutes] at minute [atMinute] of the practice that began at [practiceAt]. */
    private suspend fun play(practiceAt: Long, pieceId: Long, goalMinutes: Long, atMinute: Long) =
        blocks.update { BlockRules.started(it, practiceAt, pieceId, goalMinutes * min, practiceAt + atMinute * min) }

    @Test
    fun `an element played for its goal adds thirty takts, once a day, and the blocks are saved with the practice`() = runTest {
        store.start(1_000)
        play(1_000, pieceId = 1, goalMinutes = 10, atMinute = 0)
        play(1_000, pieceId = 2, goalMinutes = 15, atMinute = 10) // the scale is done at 10
        play(1_000, pieceId = 1, goalMinutes = 5, atMinute = 25) // the etude is done at 25; the scale again — done, but paid already
        play(1_000, pieceId = 3, goalMinutes = 10, atMinute = 30) // cut by the end of the practice at 38
        assertTrue(finisher.save(1_000, durationMs = 38 * min) != null)

        val earning = journey.earnings.single()
        assertEquals(2, earning.piecesPaid)
        assertEquals(38 * 2 + 2 * 30, earning.takts)
        assertEquals(listOf(1L to true, 2L to true, 1L to false, 3L to false), history.blocks.value.map { it.pieceId to it.paid })
        assertEquals(listOf(true, true, true, false), history.blocks.value.map { it.done })
        assertEquals(LocalDate(1970, 1, 1), history.blocks.value.first().date)
        assertEquals(null, blocks.blocks.value)
    }

    @Test
    fun `an element paid for earlier that day brings no second thirty`() = runTest {
        history.blocks.value = listOf(SavedBlock(1, LocalDate(1970, 1, 1), 0, 10 * min, 10 * min, done = true, paid = true, id = 1))
        store.start(2_000_000)
        play(2_000_000, pieceId = 1, goalMinutes = 5, atMinute = 0)
        assertTrue(finisher.save(2_000_000, durationMs = 5 * min) != null)
        assertEquals(0, journey.earnings.single().piecesPaid)
        assertEquals(10, journey.earnings.single().takts)
    }

    @Test
    fun `blocks of a discarded practice go with it, and a deleted element is neither saved nor paid`() = runTest {
        store.start(1_000)
        play(1_000, pieceId = 1, goalMinutes = 5, atMinute = 0)
        finisher.discard()
        assertEquals(null, blocks.blocks.value)

        history.pieces = setOf(2L)
        store.start(2_000_000)
        play(2_000_000, pieceId = 1, goalMinutes = 5, atMinute = 0)
        play(2_000_000, pieceId = 2, goalMinutes = 5, atMinute = 5)
        assertTrue(finisher.save(2_000_000, durationMs = 10 * min) != null)
        assertEquals(listOf(2L), history.blocks.value.map { it.pieceId })
        assertEquals(1, journey.earnings.single().piecesPaid)
    }
}
