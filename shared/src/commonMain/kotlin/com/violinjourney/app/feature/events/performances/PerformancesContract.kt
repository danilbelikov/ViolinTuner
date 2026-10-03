package com.violinjourney.app.feature.events.performances

import com.violinjourney.app.core.domain.events.KindLook
import com.violinjourney.app.core.domain.events.PerformanceRow

/**
 * A row of «Выступления» as the screen shows it (spec 3.36.9): what the domain says of the performance ([row]) and the file of the
 * thumbnail of its newest video ([thumbPath], plan D43) — null without a video, and for a video whose thumbnail is not there (a copy
 * restored without the videos, a thumbnail never made, a video lost) or not yet made anew by the current rule (spec 5.31): then the
 * row says the number of its recordings.
 */
data class PerformanceCard(val row: PerformanceRow, val thumbPath: String?)

/**
 * The screen «Выступления» (spec 3.35, 3.36.9): «Впереди» — the nearest first — and «Прошли» — the freshest first; [look] — the sign and
 * the colour of the kind «Выступление» (a built-in kind can be given another colour, 3.35): its plates and chips. While the events are
 * read ([loading]) — the bar and an empty bottom zone of the same height, nothing else.
 */
data class PerformancesState(
    val loading: Boolean,
    val ahead: List<PerformanceCard> = emptyList(),
    val past: List<PerformanceCard> = emptyList(),
    val look: KindLook,
) {
    /** No performance at all (spec 3.36.9, «Пусто»): the plate of the sign, «Здесь будут ваши выступления», the words, the same button. */
    val empty: Boolean get() = !loading && ahead.isEmpty() && past.isEmpty()
}

sealed interface PerformancesIntent {
    /** A row: the screen of its event (spec 3.36.9); «назад» from it comes back here. */
    data class RowClicked(val eventId: Long) : PerformancesIntent

    /** «Добавить выступление»: the form with the kind «Выступление» and the date of today (spec 3.35, plan D28). */
    data object AddClicked : PerformancesIntent

    /** «назад» of the bar. */
    data object BackClicked : PerformancesIntent

    /** The screen is in sight again (its route on every `ON_START`): what was pressed before has been answered — presses count anew. */
    data object Shown : PerformancesIntent
}

sealed interface PerformancesEffect {
    data class OpenEvent(val eventId: Long) : PerformancesEffect

    /** The form of a new performance of today. */
    data object OpenNewPerformance : PerformancesEffect

    data object Close : PerformancesEffect
}
