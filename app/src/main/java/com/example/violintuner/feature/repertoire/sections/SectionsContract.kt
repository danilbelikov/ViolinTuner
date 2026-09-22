package com.example.violintuner.feature.repertoire.sections

import com.example.violintuner.core.domain.repertoire.SectionCount
import com.example.violintuner.core.domain.repertoire.SectionRef

/** One card of the sections screen (spec 3.22, handoff 24b). */
data class SectionCard(
    val ref: SectionRef,
    /** Null for a built-in section: its name is a word of the interface. */
    val name: String?,
    val count: SectionCount,
)

/** One row of «Время по элементам» (spec 3.28, handoff 30h): an element and its time over the period, today apart. */
data class PieceTimeRow(val pieceId: Long, val title: String, val totalMs: Long, val todayMs: Long)

/**
 * «Время по элементам · 30 дней»: the elements played over the last [days] days, the most first. Empty — nothing
 * was played in the period: the card says what it will show. [expanded] — «Все N» was tapped.
 */
data class PieceTimeCard(val rows: List<PieceTimeRow>, val days: Int, val expanded: Boolean)

/** The way into the repertoire: its sections, each with how far it has come. */
data class SectionsState(
    /** True until the stored pieces have been read once. */
    val loading: Boolean,
    val cards: List<SectionCard>,
    /** Everything in every section: «выучено 9 из 27» at the top. */
    val total: SectionCount,
    /** Non-null while «Новый раздел» is open: what has been typed so far. */
    val newName: String? = null,
    val maxNameLength: Int,
    /** Null while nothing is read or the repertoire is empty: then there is nothing to count time for. */
    val time: PieceTimeCard? = null,
) {
    val canCreate: Boolean get() = !newName.isNullOrBlank()
}

sealed interface SectionsIntent {
    data class SectionClicked(val ref: SectionRef) : SectionsIntent

    data object AddClicked : SectionsIntent

    data class NameChanged(val text: String) : SectionsIntent

    data object CreateConfirmed : SectionsIntent

    data object DialogDismissed : SectionsIntent

    /** «Все N» / «Свернуть» of the time card. */
    data object TimeToggled : SectionsIntent

    data class TimePieceClicked(val id: Long) : SectionsIntent
}

sealed interface SectionsEffect {
    data class OpenSection(val ref: SectionRef) : SectionsEffect

    /** A row of the time card: the element's own screen. */
    data class OpenPiece(val id: Long) : SectionsEffect
}
