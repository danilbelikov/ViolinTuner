package com.violinjourney.app.feature.live.block

import com.violinjourney.app.core.domain.practice.BlockRules
import com.violinjourney.app.core.domain.practice.PracticeBlocks
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.practice.RunningPractice
import com.violinjourney.app.core.domain.practice.SavedBlock
import com.violinjourney.app.core.domain.practice.practiceDateOf
import com.violinjourney.app.core.domain.repertoire.Piece
import com.violinjourney.app.core.domain.repertoire.PieceGroup
import com.violinjourney.app.core.domain.repertoire.PieceStats
import com.violinjourney.app.core.domain.repertoire.SectionStats
import com.violinjourney.app.core.domain.session.SessionSummary
import kotlinx.datetime.TimeZone

/**
 * The bookmark and the sheets of blocks on Live (spec 3.28). Pure: the clock, the zone and what is
 * stored come from outside. What only the screen decides — the sheet open, the element picked, the
 * goal — is [Ui].
 */
object BlockReducer {
    data class Ui(val sheetOpen: Boolean = false, val selectedId: Long? = null, val goalMinutes: Int? = null)

    fun stateOf(
        running: RunningPractice?,
        blocks: PracticeBlocks?,
        saved: List<SavedBlock>,
        pieces: List<Piece>,
        groups: List<PieceGroup>,
        sessions: List<SessionSummary>,
        ui: Ui,
        nowEpochMs: Long,
        zone: TimeZone,
        config: PracticeConfig,
    ): BlockState {
        val own = BlockRules.ofPractice(running, blocks)
        val titles = pieces.associate { it.id to it.title }
        return BlockState(
            bookmark = bookmarkOf(own, titles, nowEpochMs),
            sheet = when {
                !ui.sheetOpen -> null
                running == null -> BlockSheet.Offer
                else -> pickerOf(running, own, saved, pieces, groups, sessions, ui, nowEpochMs, zone, config)
            },
        )
    }

    /** The block on the bookmark, unless its element is gone: then there is nothing to show (spec 3.28). */
    fun bookmarkOf(blocks: PracticeBlocks?, titles: Map<Long, String>, nowEpochMs: Long): Bookmark {
        val current = blocks?.current ?: return Bookmark.Entry
        val title = titles[current.pieceId] ?: return Bookmark.Entry
        return if (BlockRules.isRunning(current, nowEpochMs)) {
            Bookmark.Running(title, BlockRules.minutesLeft(current, nowEpochMs), BlockRules.progress(current, nowEpochMs))
        } else {
            Bookmark.Done(title)
        }
    }

    private fun pickerOf(
        running: RunningPractice,
        blocks: PracticeBlocks?,
        saved: List<SavedBlock>,
        pieces: List<Piece>,
        groups: List<PieceGroup>,
        sessions: List<SessionSummary>,
        ui: Ui,
        nowEpochMs: Long,
        zone: TimeZone,
        config: PracticeConfig,
    ): BlockSheet.Picker {
        // «today» is the day of the running practice: one that went past midnight keeps its day (spec 5.21)
        val marks = BlockRules.marksOf(saved, blocks, practiceDateOf(running.startedAtEpochMs, zone), nowEpochMs, config)
        val sections = SectionStats.summaries(pieces, groups).mapNotNull { summary ->
            val own = SectionStats.piecesOf(summary.ref, pieces, groups)
            if (own.isEmpty()) return@mapNotNull null
            // the order of the section's own list: the latest activity first (spec 5.9); marks never move anything
            val listed = own
                .map { piece -> piece to PieceStats.lastActivity(piece, PieceStats.takesOf(piece.id, sessions)) }
                .sortedWith(compareByDescending<Pair<Piece, Long>> { it.second }.thenByDescending { it.first.id })
                .map { (piece, _) -> PickerPiece(piece.id, piece.title, piece.composer.takeIf { it.isNotBlank() && piece.scale == null }, markOf(marks[piece.id], config)) }
            PickerSection(summary.ref, summary.name, doneToday = own.count { marks[it.id]?.done == true }, pieces = listed)
        }
        val current = blocks?.current?.takeIf { BlockRules.isRunning(it, nowEpochMs) }
        val now = current?.let { block -> pieces.firstOrNull { it.id == block.pieceId }?.let { NowLine(it.title, BlockRules.minutesLeft(block, nowEpochMs)) } }
        // a pick of the running element or of one that is gone is no pick
        val selectedId = ui.selectedId?.takeIf { id -> sections.any { section -> section.pieces.any { it.id == id && it.today != TodayMark.Running } } }
        val goal = ui.goalMinutes ?: selectedId?.let { BlockRules.defaultGoalMinutes(it, saved, blocks, config) } ?: config.blockDefaultGoalMinutes
        return BlockSheet.Picker(
            now = now,
            sections = sections,
            selectedId = selectedId,
            goalMinutes = goal,
            quickGoals = config.blockQuickGoalsMinutes,
            goalStep = config.blockGoalStepMinutes,
            canGoalDown = goal > config.blockGoalMinMinutes,
            canGoalUp = goal < config.blockGoalMaxMinutes,
        )
    }

    private fun markOf(mark: BlockRules.DayMark?, config: PracticeConfig): TodayMark = when {
        mark == null -> TodayMark.None
        mark.running -> TodayMark.Running
        mark.done -> TodayMark.Done(mark.playedMs)
        // what is not kept is not shown either
        mark.playedMs >= config.blockMinSavedMs -> TodayMark.Played(mark.playedMs)
        else -> TodayMark.None
    }
}
