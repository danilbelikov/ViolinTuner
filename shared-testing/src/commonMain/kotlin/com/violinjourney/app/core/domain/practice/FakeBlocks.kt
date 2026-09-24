package com.violinjourney.app.core.domain.practice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** The blocks of the running practice, in memory; the rules are the real ones — the store only keeps what they return. */
class FakeBlockStore : BlockStore {
    override val blocks = MutableStateFlow<PracticeBlocks?>(null)

    override suspend fun update(transform: (PracticeBlocks?) -> PracticeBlocks?) {
        blocks.update(transform)
    }

    override suspend fun clear() {
        blocks.value = null
    }
}

/** Saved blocks in memory; [pieces] stand for the elements that exist — a block of any other is not stored, as by the DAO. */
class FakePieceBlockRepository(var pieces: Set<Long>? = null) : PieceBlockRepository {
    override val blocks = MutableStateFlow<List<SavedBlock>>(emptyList())

    override suspend fun add(blocks: List<SavedBlock>): List<SavedBlock> {
        val next = (this.blocks.value.maxOfOrNull { it.id } ?: 0L) + 1
        val stored = blocks.filter { pieces?.contains(it.pieceId) != false }.mapIndexed { i, block -> block.copy(id = next + i) }
        this.blocks.update { (it + stored).sortedBy { block -> block.startedAtEpochMs } }
        return stored
    }
}
