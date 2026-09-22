package com.example.violintuner.core.domain.practice

import com.example.violintuner.core.domain.practice.PracticeConfig.Companion.MS_PER_MINUTE
import java.time.LocalDate
import kotlin.math.ceil

/**
 * The rules of blocks — «подходы» (spec 3.28, 5.21). Pure: time is always given, never read. A block
 * is done when the clock is past its goal; the block on the bookmark keeps no end of its own until
 * it is stopped or replaced, so there is nothing to flip when the goal comes.
 */
object BlockRules {
    /** A block of a practice that ends at a given moment: what the summary lists and the practice saves. */
    data class Played(val pieceId: Long, val startedAtEpochMs: Long, val durationMs: Long, val goalMs: Long, val done: Boolean)

    /** What «Что играем» says of an element for a day: how long it was played, whether a block of it was done, whether one runs. */
    data class DayMark(val playedMs: Long, val done: Boolean, val running: Boolean)

    /** The time an element got over the last days, today included, and today alone. */
    data class PieceTime(val pieceId: Long, val totalMs: Long, val todayMs: Long)

    fun goalEndOf(block: PieceBlock): Long = block.startedAtEpochMs + block.goalMs

    /** The blocks of the practice that runs; null when nothing runs or they were left behind by another practice. */
    fun ofPractice(running: RunningPractice?, blocks: PracticeBlocks?): PracticeBlocks? =
        blocks?.takeIf { running != null && it.practiceStartedAtEpochMs == running.startedAtEpochMs }

    /** Where [block] ends as of [nowEpochMs]: where it was ended; else at its goal once the clock is past it; else now — it still runs. */
    fun endOf(block: PieceBlock, nowEpochMs: Long): Long =
        (block.endedAtEpochMs ?: minOf(nowEpochMs, goalEndOf(block))).coerceAtLeast(block.startedAtEpochMs)

    fun elapsedMs(block: PieceBlock, nowEpochMs: Long): Long = endOf(block, nowEpochMs) - block.startedAtEpochMs

    fun isDone(block: PieceBlock, nowEpochMs: Long): Boolean = elapsedMs(block, nowEpochMs) >= block.goalMs

    /** On the bookmark and short of its goal. */
    fun isRunning(block: PieceBlock, nowEpochMs: Long): Boolean = block.endedAtEpochMs == null && nowEpochMs < goalEndOf(block)

    /** «ещё 7 мин»: whole minutes, rounded up — 6:01 to 7:00 left is seven; never below one while it runs. */
    fun minutesLeft(block: PieceBlock, nowEpochMs: Long): Int =
        ceil((goalEndOf(block) - nowEpochMs).toDouble() / MS_PER_MINUTE).toInt().coerceAtLeast(1)

    /** 0..1, how far the brass line has run. */
    fun progress(block: PieceBlock, nowEpochMs: Long): Float =
        if (block.goalMs <= 0) 1f else (elapsedMs(block, nowEpochMs).toFloat() / block.goalMs).coerceIn(0f, 1f)

    /**
     * A block of [pieceId] starts at [nowEpochMs]. The one on the bookmark ends first — now, or at its
     * goal if that came earlier; blocks of another practice are left behind.
     */
    fun started(blocks: PracticeBlocks?, practiceStartedAtEpochMs: Long, pieceId: Long, goalMs: Long, nowEpochMs: Long): PracticeBlocks {
        val same = blocks?.takeIf { it.practiceStartedAtEpochMs == practiceStartedAtEpochMs }
        val closed = same?.current?.let { closed(it, nowEpochMs) }
        return PracticeBlocks(
            practiceStartedAtEpochMs = practiceStartedAtEpochMs,
            current = PieceBlock(pieceId, nowEpochMs, goalMs),
            finished = same?.finished.orEmpty() + listOfNotNull(closed),
        )
    }

    /** «Остановить»: the block on the bookmark ends now (or at its goal). Nothing changes for another practice. */
    fun stopped(blocks: PracticeBlocks?, practiceStartedAtEpochMs: Long, nowEpochMs: Long): PracticeBlocks? {
        if (blocks == null || blocks.practiceStartedAtEpochMs != practiceStartedAtEpochMs) return blocks
        val current = blocks.current ?: return blocks
        return blocks.copy(current = null, finished = blocks.finished + closed(current, nowEpochMs))
    }

    private fun closed(block: PieceBlock, nowEpochMs: Long): PieceBlock = block.copy(endedAtEpochMs = endOf(block, nowEpochMs))

    /**
     * The blocks of a practice that ends at [practiceEndMs] (spec 5.21), in the order they were started:
     * each is cut at the end of the practice — the stepper and «Закончить в 18:42» cut them too; one begun
     * after the end goes, and so does one shorter than [PracticeConfig.blockMinSavedMs].
     */
    fun played(blocks: PracticeBlocks?, practiceEndMs: Long, config: PracticeConfig): List<Played> =
        blocks?.all.orEmpty()
            .filter { it.startedAtEpochMs < practiceEndMs }
            .sortedBy { it.startedAtEpochMs }
            .map { block ->
                val durationMs = minOf(block.endedAtEpochMs ?: goalEndOf(block), practiceEndMs) - block.startedAtEpochMs
                Played(block.pieceId, block.startedAtEpochMs, durationMs, block.goalMs, done = durationMs >= block.goalMs)
            }
            .filter { it.durationMs >= config.blockMinSavedMs }

    /**
     * The saved form of the [played] blocks of a practice of [date]. The first done block of an element
     * that day is paid for (spec 3.28: thirty takts, once a day), unless one was paid already — [paidThatDay].
     */
    fun settle(played: List<Played>, date: LocalDate, paidThatDay: Set<Long>): List<SavedBlock> {
        val paidPieces = paidThatDay.toMutableSet()
        return played.map { block ->
            SavedBlock(
                pieceId = block.pieceId,
                date = date,
                startedAtEpochMs = block.startedAtEpochMs,
                durationMs = block.durationMs,
                goalMs = block.goalMs,
                done = block.done,
                paid = block.done && paidPieces.add(block.pieceId),
            )
        }
    }

    /**
     * «Сегодня» of every element played on [day]: the saved blocks of that day and the blocks of the
     * running practice, which is of that day too. A finished block too short to be kept is not shown either.
     */
    fun marksOf(saved: List<SavedBlock>, blocks: PracticeBlocks?, day: LocalDate, nowEpochMs: Long, config: PracticeConfig): Map<Long, DayMark> {
        val marks = mutableMapOf<Long, DayMark>()
        fun add(pieceId: Long, playedMs: Long, done: Boolean, running: Boolean) {
            val mark = marks[pieceId]
            marks[pieceId] = DayMark(
                playedMs = (mark?.playedMs ?: 0L) + playedMs,
                done = mark?.done == true || done,
                running = mark?.running == true || running,
            )
        }
        saved.filter { it.date == day }.forEach { add(it.pieceId, it.durationMs, it.done, running = false) }
        blocks?.all.orEmpty().forEach { block ->
            val running = isRunning(block, nowEpochMs)
            val playedMs = elapsedMs(block, nowEpochMs)
            if (running || playedMs >= config.blockMinSavedMs) add(block.pieceId, playedMs, isDone(block, nowEpochMs), running)
        }
        return marks
    }

    /** The goal «Что играем» offers for [pieceId]: its last one, else the last one of any element, else the default (spec 5.21). */
    fun defaultGoalMinutes(pieceId: Long, saved: List<SavedBlock>, blocks: PracticeBlocks?, config: PracticeConfig): Int {
        val goals = saved.map { Goal(it.pieceId, it.startedAtEpochMs, it.goalMs) } +
            blocks?.all.orEmpty().map { Goal(it.pieceId, it.startedAtEpochMs, it.goalMs) }
        val last = goals.filter { it.pieceId == pieceId }.maxByOrNull { it.startedAtEpochMs } ?: goals.maxByOrNull { it.startedAtEpochMs }
        return clampGoal(last?.let { (it.goalMs / MS_PER_MINUTE).toInt() } ?: config.blockDefaultGoalMinutes, config)
    }

    private data class Goal(val pieceId: Long, val startedAtEpochMs: Long, val goalMs: Long)

    /** A goal within its bounds and on the grid of its step. */
    fun clampGoal(minutes: Int, config: PracticeConfig): Int =
        (minutes / config.blockGoalStepMinutes * config.blockGoalStepMinutes).coerceIn(config.blockGoalMinMinutes, config.blockGoalMaxMinutes)

    /** −5 / +5 of the goal panel. */
    fun stepGoal(minutes: Int, steps: Int, config: PracticeConfig): Int = clampGoal(minutes + steps * config.blockGoalStepMinutes, config)

    /** The time of every element played over the last [days] days, today included (spec 5.21), the most first. */
    fun timeByPiece(saved: List<SavedBlock>, today: LocalDate, days: Int): List<PieceTime> {
        val from = today.minusDays(days - 1L)
        return saved.filter { it.date in from..today }
            .groupBy { it.pieceId }
            .map { (pieceId, blocks) -> PieceTime(pieceId, blocks.sumOf { it.durationMs }, blocks.filter { it.date == today }.sumOf { it.durationMs }) }
            .sortedByDescending { it.totalMs }
    }
}
