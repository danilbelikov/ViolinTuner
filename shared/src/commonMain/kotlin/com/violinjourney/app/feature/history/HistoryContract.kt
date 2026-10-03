package com.violinjourney.app.feature.history

import com.violinjourney.app.core.domain.events.KindLook
import com.violinjourney.app.core.domain.events.PerformancesLine
import com.violinjourney.app.core.domain.events.SessionEvent
import com.violinjourney.app.core.domain.session.DayCount
import kotlinx.datetime.LocalDate

/**
 * What the list of «Записи» shows (spec 3.36.5, 3.36.9): everything; the takes — recordings bound to a piece, video takes too; everything
 * with a video, its file lost or not — the videos of events too; and what came from Live — bound to no piece and no event, and without a
 * video: the sound of an event is only under [ALL]. Where a recording was made is not stored: a sound take of a deleted piece is unbound
 * and stands under [LIVE] like any recording of Live (spec 3.15).
 */
enum class HistoryFilter { ALL, TAKES, VIDEO, LIVE }

data class HistoryCard(
    val id: Long,
    /** Null = default name built from the date. */
    val title: String?,
    val startedAtEpochMs: Long,
    /** Local day of the start: the group of the list it stands in (spec 3.21). */
    val date: LocalDate,
    /** Not this year: wherever the date is written, it is written with its year. */
    val otherYear: Boolean = false,
    val durationMs: Long,
    /** Title of the piece this recording is a take of: names it by default in «Записи». */
    val pieceTitle: String? = null,
    /** The piece this recording is a take of; null for a free recording. */
    val pieceId: Long? = null,
    /**
     * Only a recording with sound can be shared or processed (spec 3.17); without it the tile is a ring. A recording whose
     * file is gone has no sound either (spec 3.20): the repository reads it so.
     */
    val hasAudio: Boolean = false,
    /** A video take (spec 3.19): the tile shows a camera instead of a note — the frame of [thumbPath] where there is one. */
    val hasVideo: Boolean = false,
    /** The thumbnail of the video, whose frame stands in its tile (spec 3.38); null without a video, a found file of it, or a thumbnail. */
    val thumbPath: String? = null,
    /** The take its player marked as the best of its piece (spec 3.21): a star after the title. */
    val best: Boolean = false,
    /** Size of the video, for the dialog that deletes it; zero without one or when the file is gone. */
    val videoBytes: Long = 0,
    /** Made under the backing of its piece (spec 3.32): the sign of the backing and «под минусовку» by its time, in every list. */
    val underBacking: Boolean = false,
    /**
     * The event it is a recording of (spec 3.35): until it is given a name of its own it is named by the event and its date — «Осенний
     * концерт · 24 октября» — and follows the event when the event is renamed.
     */
    val event: SessionEvent? = null,
) {
    val take: Boolean get() = pieceId != null
}

/** The tab «Записи» (spec 3.11, 3.21): the recordings only — the repertoire is a tab of its own (spec 3.36.1). */
data class HistoryState(
    /** True until the stored sessions have been read once. */
    val loading: Boolean,
    /** All recordings, whatever the filter: tells «nothing at all» (no strip, no chips, «Открыть Live») from «nothing under the filter». */
    val totalCount: Int,
    /** Recordings per day, oldest first, the last is today; the filter does not touch it. */
    val days: List<DayCount>,
    /** What the tallest bar of the chart stands for. */
    val chartTop: Int,
    val today: LocalDate,
    val filter: HistoryFilter,
    /** Newest first, after the filter. */
    val cards: List<HistoryCard>,
    /** Picking several sessions to delete (spec 3.18); only what [cards] shows can be picked. */
    val selection: Selection = Selection(),
    /**
     * The row «Выступления» (spec 3.36.9): the first under the line of the title, in an empty tab too; null while the list or the events
     * are read — it comes with the strip and the chips. The screen hides it while picking ([selection]).
     */
    val performances: HistoryPerformances? = null,
) {
    val allSelected: Boolean get() = SelectionRules.allSelected(selection, cards.map { it.id })

    /** «12 записей» of the strip «За две недели» (spec 3.36.5): its fourteen days, whatever the filter. */
    val stripTotal: Int get() = days.sumOf { it.count }

    /** [cards] by day, newest day first: every group gets a header with its date (spec 3.21). */
    val groups: List<DayGroup> get() = cards.groupBy { it.date }.map { (date, cards) -> DayGroup(date, today = date == today, cards = cards) }
}

data class DayGroup(val date: LocalDate, val today: Boolean, val cards: List<HistoryCard>)

/**
 * What the row «Выступления» of «Записи» says (spec 3.36.9): its caption by how things stand — the nearest ahead with its term and how
 * many are over, only those over and the last of them, or none at all — and [look], the sign and the colour of the kind «Выступление»
 * on its plate (a built-in kind can be given another colour, 3.35). [today] — the day it was worked out on: the last date of another
 * year is written with its year.
 */
data class HistoryPerformances(val line: PerformancesLine, val look: KindLook, val today: LocalDate)

sealed interface HistoryIntent {
    data class FilterSelected(val filter: HistoryFilter) : HistoryIntent

    data class SessionClicked(val id: Long) : HistoryIntent

    /** «Отметить лучшим» / «Снять отметку „лучший“» of a take's «⋯» (spec 3.21). */
    data class BestToggled(val id: Long) : HistoryIntent

    /** Everything of the selection mode; a plain [SessionClicked] inside it picks the card. */
    data class Select(val intent: SelectionIntent) : HistoryIntent

    /** «Открыть Live» of an empty tab (spec 3.36.5). «Показать все записи» under an empty filter is [FilterSelected] of «Все». */
    data object OpenLiveClicked : HistoryIntent

    /**
     * The row «Выступления» (spec 3.36.9): the screen «Выступления» — once until the tab is in sight again ([Shown]). Not heard while
     * picking — the row is gone then.
     */
    data object PerformancesClicked : HistoryIntent

    /**
     * The tab is in sight again (its route on every `ON_START`): «Выступления» opened from it have been answered, the row is heard anew. A
     * tap of a double tap that falls through to the tab going away (5.29 R9, review of stage 99) is not.
     */
    data object Shown : HistoryIntent
}

sealed interface HistoryEffect {
    data class OpenSession(val id: Long) : HistoryEffect

    /** The tab Live. */
    data object OpenLive : HistoryEffect

    /** «Выступления» (spec 3.36.9), above the tabs. */
    data object OpenPerformances : HistoryEffect
}
