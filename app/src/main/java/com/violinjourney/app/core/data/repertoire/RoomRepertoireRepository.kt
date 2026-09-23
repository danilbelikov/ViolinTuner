package com.violinjourney.app.core.data.repertoire

import com.violinjourney.app.core.domain.repertoire.Accidental
import com.violinjourney.app.core.domain.repertoire.KeyMode
import com.violinjourney.app.core.domain.repertoire.MusicalKey
import com.violinjourney.app.core.domain.repertoire.Piece
import com.violinjourney.app.core.domain.repertoire.PieceDraft
import com.violinjourney.app.core.domain.repertoire.PieceGroup
import com.violinjourney.app.core.domain.repertoire.PieceSection
import com.violinjourney.app.core.domain.repertoire.scale.ScaleKind
import com.violinjourney.app.core.domain.repertoire.scale.ScaleSpec
import com.violinjourney.app.core.domain.repertoire.PieceRules
import com.violinjourney.app.core.domain.repertoire.PieceStatus
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.domain.repertoire.RepertoireRepository
import com.violinjourney.app.core.domain.repertoire.SheetPage
import com.violinjourney.app.core.domain.repertoire.Tonic
import java.time.Clock
import com.violinjourney.app.core.analytics.Analytics
import com.violinjourney.app.core.analytics.NoOpAnalytics
import com.violinjourney.app.core.analytics.PieceAdded
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomRepertoireRepository @Inject constructor(
    private val dao: RepertoireDao,
    private val files: SheetFiles,
    private val config: RepertoireConfig,
    private val clock: Clock,
    private val analytics: Analytics = NoOpAnalytics(),
) : RepertoireRepository {
    override val pieces: Flow<List<Piece>> = dao.observePieces().map { rows -> rows.map(RepertoireMapper::toPiece) }

    override val groups: Flow<List<PieceGroup>> =
        dao.observeGroups().map { rows -> rows.map { PieceGroup(it.id, it.name, it.createdAtEpochMs) } }

    override val pages: Flow<List<SheetPage>> = dao.observePages().map { rows -> rows.map(RepertoireMapper::toPage) }

    override suspend fun piece(id: Long): Piece? = dao.piece(id)?.let(RepertoireMapper::toPiece)

    override suspend fun add(draft: PieceDraft, nowEpochMs: Long): Long {
        val clean = requireNotNull(PieceRules.clean(draft, config)) { "a piece needs a title" }
        analytics.track(PieceAdded(section = clean.section.name, ownSection = clean.groupId != null, scale = clean.scale != null))
        return dao.insertPiece(RepertoireMapper.toEntity(clean, createdAt = nowEpochMs))
    }

    override suspend fun update(id: Long, draft: PieceDraft, nowEpochMs: Long) {
        val clean = requireNotNull(PieceRules.clean(draft, config)) { "a piece needs a title" }
        dao.updatePiece(RepertoireMapper.toEntity(clean, createdAt = nowEpochMs).copy(id = id), LEARNED, nowEpochMs)
    }

    override suspend fun setStatus(id: Long, status: PieceStatus, nowEpochMs: Long) = dao.setStatus(id, status.name, LEARNED, nowEpochMs)

    override suspend fun addGroup(name: String, nowEpochMs: Long): Long {
        val clean = requireNotNull(PieceRules.cleanGroupName(name, config)) { "a section needs a name" }
        return dao.insertGroup(PieceGroupEntity(name = clean, createdAtEpochMs = nowEpochMs))
    }

    override suspend fun renameGroup(id: Long, name: String) {
        val clean = requireNotNull(PieceRules.cleanGroupName(name, config)) { "a section needs a name" }
        dao.renameGroup(id, clean)
    }

    override suspend fun deleteGroup(id: Long) = dao.deleteGroup(id, PieceSection.PIECES.name)

    override suspend fun setBestTake(id: Long, sessionId: Long?) = dao.setBestTake(id, sessionId)

    // Rows first: a file without a page is cleaned up later, a page without its file would
    // show a hole in the music.
    override suspend fun delete(id: Long) {
        val names = dao.pagesOf(id).flatMap { listOf(it.fileName, it.thumbFileName) }
        dao.deletePiece(id)
        files.delete(names)
    }

    override suspend fun addPage(pieceId: Long, fileName: String, thumbFileName: String, nowEpochMs: Long) {
        if (!dao.appendPage(pieceId, fileName, thumbFileName, nowEpochMs)) files.delete(listOf(fileName, thumbFileName))
    }

    override suspend fun deletePage(pageId: Long, nowEpochMs: Long) {
        val page = dao.page(pageId) ?: return
        dao.removePage(page, nowEpochMs)
        files.delete(listOf(page.fileName, page.thumbFileName))
    }

    override suspend fun deleteOrphanFiles() = files.deleteOrphans(
        referenced = dao.fileNames().toSet(),
        nowEpochMs = clock.millis(),
        minAgeMs = config.orphanPhotoMinAgeMs,
    )
}

private val LEARNED = PieceStatus.IN_REPERTOIRE.name

internal object RepertoireMapper {
    fun toEntity(draft: PieceDraft, createdAt: Long) = PieceEntity(
        title = draft.title,
        composer = draft.composer,
        keyTonic = draft.key?.tonic?.name,
        keyAccidental = draft.key?.accidental?.name,
        keyMode = draft.key?.mode?.name,
        tempoBpm = draft.tempoBpm,
        status = draft.status.name,
        notes = draft.notes,
        createdAtEpochMs = createdAt,
        updatedAtEpochMs = createdAt,
        section = draft.section.name,
        groupId = draft.groupId,
        scaleKind = draft.scale?.kind?.name,
        scaleOctaves = draft.scale?.octaves,
        // a piece may be born learnt: an old warhorse added to the list
        learnedAtEpochMs = createdAt.takeIf { draft.status == PieceStatus.IN_REPERTOIRE },
    )

    fun toPiece(entity: PieceEntity) = Piece(
        id = entity.id,
        title = entity.title,
        composer = entity.composer,
        key = keyOf(entity),
        tempoBpm = entity.tempoBpm,
        // A name this build does not know (a row written by a newer one) reads as the default, not as a crash.
        status = PieceStatus.entries.firstOrNull { it.name == entity.status } ?: PieceStatus.READING,
        notes = entity.notes,
        createdAtEpochMs = entity.createdAtEpochMs,
        updatedAtEpochMs = entity.updatedAtEpochMs,
        bestTakeId = entity.bestTakeId,
        section = PieceSection.entries.firstOrNull { it.name == entity.section } ?: PieceSection.PIECES,
        groupId = entity.groupId,
        scale = scaleOf(entity),
        learnedAtEpochMs = entity.learnedAtEpochMs,
    )

    /** A scale is its key, its kind and its octaves — all of them, or it is not a scale. */
    private fun scaleOf(entity: PieceEntity): ScaleSpec? {
        val key = keyOf(entity) ?: return null
        val kind = ScaleKind.entries.firstOrNull { it.name == entity.scaleKind } ?: return null
        return ScaleSpec(key.tonic, key.accidental, kind, entity.scaleOctaves ?: return null)
    }

    fun toPage(entity: SheetPageEntity) = SheetPage(entity.id, entity.pieceId, entity.position, entity.fileName, entity.thumbFileName)

    /** All three parts or no key at all. */
    private fun keyOf(entity: PieceEntity): MusicalKey? {
        val tonic = Tonic.entries.firstOrNull { it.name == entity.keyTonic } ?: return null
        val accidental = Accidental.entries.firstOrNull { it.name == entity.keyAccidental } ?: return null
        val mode = KeyMode.entries.firstOrNull { it.name == entity.keyMode } ?: return null
        return MusicalKey(tonic, accidental, mode)
    }
}
