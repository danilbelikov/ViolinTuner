package com.violinjourney.app.feature.history

import com.violinjourney.app.core.domain.session.DayCount
import kotlinx.datetime.LocalDate

enum class HistoryFilter { ALL, THIS_WEEK, MONTH }

/** The two halves of the «Записи» tab (spec 3.15): what was recorded and what is being learnt. */
enum class HistorySection { SESSIONS, REPERTOIRE }

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
    /** Only a recording with sound can be shared or processed (spec 3.17); without it the tile is a ring. */
    val hasAudio: Boolean = false,
    /** A video take (spec 3.19): the tile shows a camera instead of a note. */
    val hasVideo: Boolean = false,
    /** The take its player marked as the best of its piece (spec 3.21): a star after the title. */
    val best: Boolean = false,
    /** Size of the video, for the dialog that deletes it; zero without one or when the file is gone. */
    val videoBytes: Long = 0,
) {
    val take: Boolean get() = pieceId != null
}

data class HistoryState(
    val section: HistorySection,
    /** True until the stored sessions have been read once. */
    val loading: Boolean,
    /** All sessions, whatever the filter: "N сессий" and the empty state. */
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
) {
    val allSelected: Boolean get() = SelectionRules.allSelected(selection, cards.map { it.id })

    /** [cards] by day, newest day first: every group gets a header with its date (spec 3.21). */
    val groups: List<DayGroup> get() = cards.groupBy { it.date }.map { (date, cards) -> DayGroup(date, today = date == today, cards = cards) }
}

data class DayGroup(val date: LocalDate, val today: Boolean, val cards: List<HistoryCard>)

sealed interface HistoryIntent {
    data class FilterSelected(val filter: HistoryFilter) : HistoryIntent

    data class SessionClicked(val id: Long) : HistoryIntent

    /** «Отметить лучшим» / «Снять отметку „лучший“» of a take's «⋯» (spec 3.21). */
    data class BestToggled(val id: Long) : HistoryIntent

    data class SectionSelected(val section: HistorySection) : HistoryIntent

    /** Everything of the selection mode; a plain [SessionClicked] inside it picks the card. */
    data class Select(val intent: SelectionIntent) : HistoryIntent
}

sealed interface HistoryEffect {
    data class OpenSession(val id: Long) : HistoryEffect
}
