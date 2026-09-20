package com.example.violintuner.core.domain.repertoire

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory repertoire for view model tests; ids count up from 1, rules are the real ones. */
class FakeRepertoireRepository(private val config: RepertoireConfig = RepertoireConfig()) : RepertoireRepository {
    override val pieces = MutableStateFlow<List<Piece>>(emptyList())
    override val pages = MutableStateFlow<List<SheetPage>>(emptyList())
    override val groups = MutableStateFlow<List<PieceGroup>>(emptyList())
    private var nextGroupId = 1L
    val deletedFiles = mutableListOf<String>()
    var orphanCleanups = 0
    private var nextPieceId = 1L
    private var nextPageId = 1L

    override suspend fun piece(id: Long): Piece? = pieces.value.firstOrNull { it.id == id }

    override suspend fun add(draft: PieceDraft, nowEpochMs: Long): Long {
        val clean = requireNotNull(PieceRules.clean(draft, config))
        val id = nextPieceId++
        pieces.update {
            it + Piece(
                id, clean.title, clean.composer, clean.key, clean.tempoBpm, clean.status, clean.notes, nowEpochMs, nowEpochMs,
                section = clean.section, groupId = clean.groupId, scale = clean.scale,
                learnedAtEpochMs = nowEpochMs.takeIf { clean.status == PieceStatus.IN_REPERTOIRE },
            )
        }
        return id
    }

    override suspend fun update(id: Long, draft: PieceDraft, nowEpochMs: Long) {
        val clean = requireNotNull(PieceRules.clean(draft, config))
        pieces.update { list ->
            list.map {
                if (it.id != id) it
                else it.copy(
                    title = clean.title, composer = clean.composer, key = clean.key, tempoBpm = clean.tempoBpm,
                    status = clean.status, notes = clean.notes, updatedAtEpochMs = nowEpochMs,
                    section = clean.section, groupId = clean.groupId, scale = clean.scale,
                    learnedAtEpochMs = learnedAt(it, clean.status, nowEpochMs),
                )
            }
        }
    }

    override suspend fun setStatus(id: Long, status: PieceStatus, nowEpochMs: Long) {
        pieces.update { list -> list.map { if (it.id == id) it.copy(status = status, updatedAtEpochMs = nowEpochMs, learnedAtEpochMs = learnedAt(it, status, nowEpochMs)) else it } }
    }

    private fun learnedAt(piece: Piece, status: PieceStatus, now: Long): Long? =
        if (status != PieceStatus.IN_REPERTOIRE) null else piece.learnedAtEpochMs ?: now

    override suspend fun addGroup(name: String, nowEpochMs: Long): Long {
        val clean = requireNotNull(PieceRules.cleanGroupName(name, config))
        val id = nextGroupId++
        groups.update { it + PieceGroup(id, clean, nowEpochMs) }
        return id
    }

    override suspend fun renameGroup(id: Long, name: String) {
        val clean = requireNotNull(PieceRules.cleanGroupName(name, config))
        groups.update { list -> list.map { if (it.id == id) it.copy(name = clean) else it } }
    }

    override suspend fun deleteGroup(id: Long) {
        pieces.update { list -> list.map { if (it.groupId == id) it.copy(groupId = null, section = PieceSection.PIECES) else it } }
        groups.update { list -> list.filterNot { it.id == id } }
    }

    override suspend fun setBestTake(id: Long, sessionId: Long?) {
        pieces.update { list -> list.map { if (it.id == id) it.copy(bestTakeId = sessionId) else it } }
    }

    override suspend fun delete(id: Long) {
        deletedFiles += pages.value.filter { it.pieceId == id }.flatMap { listOf(it.fileName, it.thumbFileName) }
        pages.update { list -> list.filterNot { it.pieceId == id } }
        pieces.update { list -> list.filterNot { it.id == id } }
    }

    override suspend fun addPage(pieceId: Long, fileName: String, thumbFileName: String, nowEpochMs: Long) {
        if (piece(pieceId) == null) {
            deletedFiles += listOf(fileName, thumbFileName)
            return
        }
        val position = (pages.value.filter { it.pieceId == pieceId }.maxOfOrNull { it.position } ?: -1) + 1
        pages.update { it + SheetPage(nextPageId++, pieceId, position, fileName, thumbFileName) }
        touch(pieceId, nowEpochMs)
    }

    override suspend fun deletePage(pageId: Long, nowEpochMs: Long) {
        val page = pages.value.firstOrNull { it.id == pageId } ?: return
        pages.update { list -> list.filterNot { it.id == pageId } }
        deletedFiles += listOf(page.fileName, page.thumbFileName)
        touch(page.pieceId, nowEpochMs)
    }

    override suspend fun deleteOrphanFiles() {
        orphanCleanups++
    }

    private fun touch(pieceId: Long, nowEpochMs: Long) {
        pieces.update { list -> list.map { if (it.id == pieceId) it.copy(updatedAtEpochMs = nowEpochMs) else it } }
    }
}
