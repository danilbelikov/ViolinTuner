package com.example.violintuner.feature.history

import com.example.violintuner.core.domain.Zone
import com.example.violintuner.core.domain.session.WeekScore
import java.time.LocalDate

enum class HistoryFilter { ALL, THIS_WEEK, MONTH }

/** The two halves of the «Записи» tab (spec 3.15): what was recorded and what is being learnt. */
enum class HistorySection { SESSIONS, REPERTOIRE }

/** Day of a session as the card words it. */
sealed interface DayLabel {
    data object Today : DayLabel

    data object Yesterday : DayLabel

    data class On(val date: LocalDate) : DayLabel
}

data class HistoryCard(
    val id: Long,
    /** Null = default name built from the date. */
    val title: String?,
    val startedAtEpochMs: Long,
    val day: DayLabel,
    val durationMs: Long,
    val biasCents: Double,
    val scorePercent: Int,
    val scoreZone: Zone,
    /** Zones of the first notes: the mini bars on the right. */
    val previewZones: List<Zone>,
    /** Title of the piece this session is a take of: names it by default and earns it the «дубль» chip. */
    val pieceTitle: String? = null,
    /** Only a recording with sound can be shared or processed: only it gets the «⋯» (spec 3.17). */
    val hasAudio: Boolean = false,
)

data class HistoryState(
    val section: HistorySection,
    /** True until the stored sessions have been read once. */
    val loading: Boolean,
    /** All sessions, whatever the filter: "N сессий" and the empty state. */
    val totalCount: Int,
    /** Oldest first, the last is the current week. */
    val weeks: List<WeekScore>,
    /** Last week minus the one before; null unless both have sessions. */
    val weekDelta: Int?,
    val filter: HistoryFilter,
    /** Newest first, after the filter. */
    val cards: List<HistoryCard>,
    /** Picking several sessions to delete (spec 3.18); only what [cards] shows can be picked. */
    val selection: Selection = Selection(),
) {
    val allSelected: Boolean get() = SelectionRules.allSelected(selection, cards.map { it.id })
}

sealed interface HistoryIntent {
    data class FilterSelected(val filter: HistoryFilter) : HistoryIntent

    data class SessionClicked(val id: Long) : HistoryIntent

    data class SectionSelected(val section: HistorySection) : HistoryIntent

    /** Everything of the selection mode; a plain [SessionClicked] inside it picks the card. */
    data class Select(val intent: SelectionIntent) : HistoryIntent
}

sealed interface HistoryEffect {
    data class OpenSession(val id: Long) : HistoryEffect
}
