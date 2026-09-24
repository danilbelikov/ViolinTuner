package com.violinjourney.app.core.domain.practice

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate

/**
 * «Подход» (spec 3.28): a stretch of the running practice given to one element of the repertoire,
 * for a length chosen beforehand — its goal. Whether it is done is never stored: the clock decides
 * ([BlockRules]), so a killed process, a locked screen or a night away change nothing.
 */
data class PieceBlock(
    val pieceId: Long,
    val startedAtEpochMs: Long,
    val goalMs: Long,
    /** When it was stopped or replaced by the next one; null for the block on the bookmark. */
    val endedAtEpochMs: Long? = null,
)

/**
 * The blocks of the running practice, kept beside it until it is saved or discarded. [current] is
 * the block on the bookmark — running, or done and still shown; [finished] came before it, in order.
 */
data class PracticeBlocks(
    val practiceStartedAtEpochMs: Long,
    val current: PieceBlock?,
    val finished: List<PieceBlock>,
) {
    /** In the order they were started. */
    val all: List<PieceBlock> get() = finished + listOfNotNull(current)
}

/** A block of a saved practice (spec 5.21): its day is the practice's day. */
data class SavedBlock(
    val pieceId: Long,
    val date: LocalDate,
    val startedAtEpochMs: Long,
    val durationMs: Long,
    val goalMs: Long,
    val done: Boolean,
    /** The first done block of its element that day: the one the takts were paid for. */
    val paid: Boolean,
    val id: Long = 0,
)

/**
 * The blocks of the running practice, on disk beside the practice itself: they have to outlive the
 * process, like its timer does. Kept under the start of their practice — [BlockRules.ofPractice]
 * turns away what belongs to another one.
 */
interface BlockStore {
    /** Null when nothing is stored. */
    val blocks: Flow<PracticeBlocks?>

    /** One atomic change: the rules of what a start or a stop does are [BlockRules.started] and [BlockRules.stopped]. */
    suspend fun update(transform: (PracticeBlocks?) -> PracticeBlocks?)

    suspend fun clear()
}

interface PieceBlockRepository {
    /** Every saved block, oldest first: a few a day — a year is a couple of thousand rows, sums are taken in memory. */
    val blocks: Flow<List<SavedBlock>>

    /**
     * Stores the blocks of a saved practice and returns those that were stored: a block whose
     * element was deleted in the meantime is skipped rather than failing the whole save.
     */
    suspend fun add(blocks: List<SavedBlock>): List<SavedBlock>
}

/** For code that keeps no blocks: tests of the practice, builds before 3.28. */
object NoBlocks : BlockStore {
    override val blocks: Flow<PracticeBlocks?> = flowOf(null)

    override suspend fun update(transform: (PracticeBlocks?) -> PracticeBlocks?) = Unit

    override suspend fun clear() = Unit
}

object NoBlockHistory : PieceBlockRepository {
    override val blocks: Flow<List<SavedBlock>> = flowOf(emptyList())

    override suspend fun add(blocks: List<SavedBlock>): List<SavedBlock> = emptyList()
}
