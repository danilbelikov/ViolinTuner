package com.violinjourney.app.core.domain.practice

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Blocks — «подходы» (spec 3.28, 5.21): the clock decides, the end of the practice cuts, an element is paid once a day. */
class BlockRulesTest {
    private val config = PracticeConfig()
    private val min = 60_000L
    private val practice = 1_000_000_000L
    private val day = LocalDate.of(2026, 9, 22)

    private fun at(minutes: Long) = practice + minutes * min

    @Test
    fun `a block is done once the clock is past its goal, and then it ends at the goal`() {
        val block = PieceBlock(7, startedAtEpochMs = at(0), goalMs = 10 * min)
        assertTrue(BlockRules.isRunning(block, at(9)))
        assertFalse(BlockRules.isDone(block, at(9)))
        assertTrue(BlockRules.isDone(block, at(10)))
        assertFalse(BlockRules.isRunning(block, at(10)))
        // a night away changes nothing: it ended at its goal
        assertEquals(at(10), BlockRules.endOf(block, at(600)))
        assertEquals(1f, BlockRules.progress(block, at(600)))
    }

    @Test
    fun `what is left is whole minutes rounded up and never zero while it runs`() {
        val block = PieceBlock(7, at(0), goalMs = 20 * min)
        assertEquals(7, BlockRules.minutesLeft(block, at(13))) // 7:00 left
        assertEquals(7, BlockRules.minutesLeft(block, at(14) - 1_000)) // 6:01 left
        assertEquals(6, BlockRules.minutesLeft(block, at(14)))
        assertEquals(1, BlockRules.minutesLeft(block, at(20) - 100))
        assertEquals(0.65f, BlockRules.progress(block, at(13)), 1e-6f)
    }

    @Test
    fun `a new block ends the one on the bookmark at once, or at its goal if that came earlier`() {
        var blocks = BlockRules.started(null, practice, pieceId = 1, goalMs = 10 * min, nowEpochMs = at(0))
        blocks = BlockRules.started(blocks, practice, pieceId = 2, goalMs = 5 * min, nowEpochMs = at(4))
        blocks = BlockRules.started(blocks, practice, pieceId = 3, goalMs = 15 * min, nowEpochMs = at(20))
        assertEquals(
            listOf(PieceBlock(1, at(0), 10 * min, endedAtEpochMs = at(4)), PieceBlock(2, at(4), 5 * min, endedAtEpochMs = at(9))),
            blocks.finished,
        )
        assertEquals(PieceBlock(3, at(20), 15 * min), blocks.current)
    }

    @Test
    fun `a stop ends the block on the bookmark now, and there is nothing to stop twice`() {
        val blocks = BlockRules.started(null, practice, 1, 10 * min, at(0))
        val stopped = BlockRules.stopped(blocks, practice, at(3))!!
        assertNull(stopped.current)
        assertEquals(listOf(PieceBlock(1, at(0), 10 * min, endedAtEpochMs = at(3))), stopped.finished)
        assertEquals(stopped, BlockRules.stopped(stopped, practice, at(5)))
    }

    @Test
    fun `blocks of another practice are left behind and a stop does not touch them`() {
        val old = BlockRules.started(null, practice, 1, 10 * min, at(0))
        val next = at(60)
        val fresh = BlockRules.started(old, next, 2, 10 * min, at(61))
        assertEquals(emptyList<PieceBlock>(), fresh.finished)
        assertEquals(old, BlockRules.stopped(old, next, at(62)))
        assertEquals(old, BlockRules.ofPractice(RunningPractice(practice, lastSoundEpochMs = null), old))
        assertNull(BlockRules.ofPractice(RunningPractice(next, lastSoundEpochMs = null), old))
        assertNull(BlockRules.ofPractice(null, old))
    }

    /** Handoff 30g1, 30g2: scale 10 of 10, Kaiser 15 of 15, the minuet stopped at 7 of 10, the concerto from minute 34 for 20. */
    private val lesson = PracticeBlocks(
        practiceStartedAtEpochMs = practice,
        current = PieceBlock(4, at(34), 20 * min),
        finished = listOf(
            PieceBlock(1, at(0), 10 * min, endedAtEpochMs = at(10)),
            PieceBlock(2, at(10), 15 * min, endedAtEpochMs = at(25)),
            PieceBlock(3, at(25), 10 * min, endedAtEpochMs = at(32)),
        ),
    )

    @Test
    fun `the end of the practice cuts its blocks, in the order they were played`() {
        assertEquals(
            listOf(
                BlockRules.Played(1, at(0), 10 * min, 10 * min, done = true),
                BlockRules.Played(2, at(10), 15 * min, 15 * min, done = true),
                BlockRules.Played(3, at(25), 7 * min, 10 * min, done = false),
                BlockRules.Played(4, at(34), 13 * min, 20 * min, done = false),
            ),
            BlockRules.played(lesson, practiceEndMs = at(47), config),
        )
        // trimmed to 30 minutes: the concerto began after the end and goes, the minuet is cut to three minutes
        assertEquals(
            listOf(3L to 5 * min),
            BlockRules.played(lesson, practiceEndMs = at(30), config).filter { it.pieceId >= 3 }.map { it.pieceId to it.durationMs },
        )
        // cut to under a minute: a tap by mistake is not kept
        assertEquals(listOf(1L, 2L), BlockRules.played(lesson, practiceEndMs = at(25) + 30_000, config).map { it.pieceId })
    }

    @Test
    fun `the block on the bookmark is saved to its goal if the practice ran past it`() {
        val done = BlockRules.played(lesson, practiceEndMs = at(120), config).last()
        assertEquals(BlockRules.Played(4, at(34), 20 * min, 20 * min, done = true), done)
    }

    @Test
    fun `the first done block of an element a day is paid, and not again once it was paid that day`() {
        val played = listOf(
            BlockRules.Played(1, at(0), 10 * min, 10 * min, done = true),
            BlockRules.Played(2, at(10), 3 * min, 10 * min, done = false),
            BlockRules.Played(1, at(20), 5 * min, 5 * min, done = true),
            BlockRules.Played(3, at(30), 5 * min, 5 * min, done = true),
            BlockRules.Played(2, at(40), 10 * min, 10 * min, done = true),
        )
        val saved = BlockRules.settle(played, day, paidThatDay = setOf(3L))
        assertEquals(listOf(true, false, false, false, true), saved.map { it.paid })
        assertTrue(saved.all { it.date == day })
    }

    @Test
    fun `marks of the day - done, played short of the goal, running - other days and taps by mistake are not counted`() {
        val saved = listOf(
            SavedBlock(1, day, at(-120), 10 * min, 10 * min, done = true, paid = true),
            SavedBlock(1, day.minusDays(1), at(-2000), 20 * min, 20 * min, done = true, paid = true),
            SavedBlock(2, day, at(-100), 7 * min, 10 * min, done = false, paid = false),
        )
        val blocks = PracticeBlocks(
            practiceStartedAtEpochMs = practice,
            current = PieceBlock(4, at(10), 15 * min),
            finished = listOf(PieceBlock(3, at(0), 10 * min, endedAtEpochMs = at(0) + 20_000), PieceBlock(1, at(1), 5 * min, endedAtEpochMs = at(6))),
        )
        val marks = BlockRules.marksOf(saved, blocks, day, nowEpochMs = at(13), config)
        assertEquals(
            mapOf(
                1L to BlockRules.DayMark(15 * min, done = true, running = false),
                2L to BlockRules.DayMark(7 * min, done = false, running = false),
                4L to BlockRules.DayMark(3 * min, done = false, running = true),
            ),
            marks,
        )
        // once the clock is past its goal, the block on the bookmark is done and no longer runs
        assertEquals(BlockRules.DayMark(15 * min, done = true, running = false), BlockRules.marksOf(emptyList(), blocks, day, at(40), config)[4L])
    }

    @Test
    fun `the goal offered is the element's last, else anyone's last, else ten minutes`() {
        assertEquals(10, BlockRules.defaultGoalMinutes(1, emptyList(), null, config))
        val saved = listOf(
            SavedBlock(1, day, at(-300), 20 * min, 20 * min, done = true, paid = true),
            SavedBlock(2, day, at(-200), 15 * min, 15 * min, done = true, paid = true),
        )
        assertEquals(20, BlockRules.defaultGoalMinutes(1, saved, null, config))
        assertEquals(15, BlockRules.defaultGoalMinutes(9, saved, null, config))
        val running = BlockRules.started(null, practice, 2, 30 * min, at(0))
        assertEquals(30, BlockRules.defaultGoalMinutes(9, saved, running, config))
        assertEquals(20, BlockRules.defaultGoalMinutes(1, saved, running, config))
    }

    @Test
    fun `a goal stays between five and sixty minutes, on the grid of five`() {
        assertEquals(5, BlockRules.stepGoal(5, -1, config))
        assertEquals(60, BlockRules.stepGoal(60, +1, config))
        assertEquals(35, BlockRules.stepGoal(30, +1, config))
        assertEquals(10, BlockRules.clampGoal(12, config))
        assertEquals(5, BlockRules.clampGoal(0, config))
    }

    @Test
    fun `time by element is the last thirty days with today, today apart, the most first`() {
        val saved = listOf(
            SavedBlock(1, day, at(0), 7 * min, 10 * min, done = false, paid = false),
            SavedBlock(1, day.minusDays(29), at(0), 30 * min, 30 * min, done = true, paid = true),
            SavedBlock(1, day.minusDays(30), at(0), 60 * min, 60 * min, done = true, paid = true),
            SavedBlock(2, day.minusDays(1), at(0), 50 * min, 50 * min, done = true, paid = true),
        )
        assertEquals(
            listOf(BlockRules.PieceTime(2, 50 * min, 0), BlockRules.PieceTime(1, 37 * min, 7 * min)),
            BlockRules.timeByPiece(saved, today = day, days = 30),
        )
    }
}
