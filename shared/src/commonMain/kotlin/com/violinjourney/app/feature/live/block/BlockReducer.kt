package com.violinjourney.app.feature.live.block

import com.violinjourney.app.core.domain.practice.BlockRules
import com.violinjourney.app.core.domain.practice.PracticeBlocks
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.practice.RunningPractice
import com.violinjourney.app.core.domain.practice.SavedBlock
import com.violinjourney.app.core.domain.practice.practiceDateOf
import com.violinjourney.app.core.domain.repertoire.Piece
import com.violinjourney.app.core.domain.repertoire.PieceGroup
import com.violinjourney.app.core.domain.repertoire.SectionRef
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

    /** A section of the choice: its pieces in the order of its own list — the latest activity first (spec 5.9). */
    data class ShelfSection(val ref: SectionRef, val name: String?, val pieces: List<Piece>)

    /** What the bookmark and the choice are made of, ordered once whenever the repertoire or the takes change. */
    data class Shelf(val titles: Map<Long, String>, val sections: List<ShelfSection>) {
        companion object {
            val EMPTY = Shelf(emptyMap(), emptyList())
        }
    }

    /**
     * The shelf of [pieces]: the titles of the bookmark and the sections of the choice, each in the order of its own list
     * (spec 5.9) — marks never move anything, so the order does not follow the clock. One pass over [sessions] finds the
     * latest take of every piece. The sections go as the landing has them (spec 3.28): the player's own by [byName].
     */
    fun shelfOf(
        pieces: List<Piece>,
        groups: List<PieceGroup>,
        sessions: List<SessionSummary>,
        byName: Comparator<String> = SectionStats.LOWERCASE_ORDER,
    ): Shelf {
        val latestTake = HashMap<Long, Long>()
        for (session in sessions) {
            val id = session.pieceId ?: continue
            if (session.startedAtEpochMs > (latestTake[id] ?: Long.MIN_VALUE)) latestTake[id] = session.startedAtEpochMs
        }
        // PieceStats.lastActivity, with the takes counted above
        fun activityOf(piece: Piece) = maxOf(piece.updatedAtEpochMs, piece.createdAtEpochMs, latestTake[piece.id] ?: 0L)
        val sections = SectionStats.summaries(pieces, groups, byName).mapNotNull { summary ->
            val own = SectionStats.piecesOf(summary.ref, pieces, groups)
            if (own.isEmpty()) return@mapNotNull null
            ShelfSection(summary.ref, summary.name, own.sortedWith(compareByDescending<Piece> { activityOf(it) }.thenByDescending { it.id }))
        }
        return Shelf(pieces.associate { it.id to it.title }, sections)
    }

    /** [zone] is asked only while the choice is open: the day of the marks is its business alone. */
    fun stateOf(
        running: RunningPractice?,
        blocks: PracticeBlocks?,
        saved: List<SavedBlock>,
        shelf: Shelf,
        ui: Ui,
        nowEpochMs: Long,
        zone: () -> TimeZone,
        config: PracticeConfig,
    ): BlockState {
        val own = BlockRules.ofPractice(running, blocks)
        return BlockState(
            bookmark = bookmarkOf(own, shelf.titles, nowEpochMs),
            sheet = when {
                !ui.sheetOpen -> null
                running == null -> BlockSheet.Offer
                else -> pickerOf(running, own, saved, shelf, ui, nowEpochMs, zone(), config)
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
        shelf: Shelf,
        ui: Ui,
        nowEpochMs: Long,
        zone: TimeZone,
        config: PracticeConfig,
    ): BlockSheet.Picker {
        // «today» is the day of the running practice: one that went past midnight keeps its day (spec 5.21)
        val marks = BlockRules.marksOf(saved, blocks, practiceDateOf(running.startedAtEpochMs, zone), nowEpochMs, config)
        val sections = shelf.sections.map { section ->
            val listed = section.pieces.map { piece ->
                PickerPiece(piece.id, piece.title, piece.composer.takeIf { it.isNotBlank() && piece.scale == null }, markOf(marks[piece.id], config))
            }
            PickerSection(section.ref, section.name, doneToday = section.pieces.count { marks[it.id]?.done == true }, pieces = listed)
        }
        val current = blocks?.current?.takeIf { BlockRules.isRunning(it, nowEpochMs) }
        val now = current?.let { block -> shelf.titles[block.pieceId]?.let { NowLine(it, BlockRules.minutesLeft(block, nowEpochMs)) } }
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
            // the clock the line of the block and its minutes are read by: the header says the tag's time, not a second apart
            practiceMs = running.elapsedMs(nowEpochMs),
            goalMinMinutes = config.blockGoalMinMinutes,
            goalMaxMinutes = config.blockGoalMaxMinutes,
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
