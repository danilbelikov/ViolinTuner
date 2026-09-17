package com.example.violintuner.feature.history

import com.example.violintuner.core.domain.Zone
import com.example.violintuner.core.domain.session.WeekScore
import java.time.LocalDate

enum class HistoryFilter { ALL, THIS_WEEK, MONTH }

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
)

data class HistoryState(
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
)

sealed interface HistoryIntent {
    data class FilterSelected(val filter: HistoryFilter) : HistoryIntent

    data class SessionClicked(val id: Long) : HistoryIntent
}

sealed interface HistoryEffect {
    data class OpenSession(val id: Long) : HistoryEffect
}
