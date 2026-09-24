package com.violinjourney.app.core.domain.backing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory backings for view model tests; ids count up from 1. */
class FakeBackingRepository : BackingRepository {
    override val backings = MutableStateFlow<List<Backing>>(emptyList())
    override val pieceBackings = MutableStateFlow<List<PieceBacking>>(emptyList())
    override val takeBackings = MutableStateFlow<List<TakeBacking>>(emptyList())

    override suspend fun backing(id: Long): Backing? = backings.value.firstOrNull { it.id == id }

    override suspend fun add(backing: Backing): Long {
        val id = (backings.value.maxOfOrNull { it.id } ?: 0) + 1
        backings.update { it + backing.copy(id = id) }
        return id
    }

    override suspend fun setForPiece(pieceId: Long, backingId: Long?) {
        pieceBackings.update { rows -> rows.filter { it.pieceId != pieceId } + listOfNotNull(backingId?.let { PieceBacking(pieceId, it, enabled = true) }) }
    }

    override suspend fun setEnabled(pieceId: Long, enabled: Boolean) {
        pieceBackings.update { rows -> rows.map { if (it.pieceId == pieceId) it.copy(enabled = enabled) else it } }
    }

    override suspend fun saveTake(take: TakeBacking) {
        takeBackings.update { rows -> rows.filter { it.sessionId != take.sessionId } + take }
    }

    override suspend fun setTakeMix(sessionId: Long, offsetMs: Int, gainDb: Float) {
        takeBackings.update { rows -> rows.map { if (it.sessionId == sessionId) it.copy(offsetMs = offsetMs, gainDb = gainDb) else it } }
    }

    override suspend fun deleteUnused(): Set<String> = backings.value.map { it.fileName }.toSet()

    fun backing(title: String = "Piano", fileName: String = "piano.m4a") =
        Backing(fileName = fileName, title = title, durationMs = 220_000, sampleRate = 44_100, channels = 2, sizeBytes = 5_000_000, addedAtEpochMs = 1)
}
