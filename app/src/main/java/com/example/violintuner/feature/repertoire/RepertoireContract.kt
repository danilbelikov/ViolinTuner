package com.example.violintuner.feature.repertoire

import com.example.violintuner.core.domain.Zone
import com.example.violintuner.core.domain.repertoire.PieceStatus
import com.example.violintuner.feature.history.DayLabel

/** One piece in the list (spec 3.15, handoff 13b). Fields a piece does not have are null and simply not shown. */
data class PieceCard(
    val id: Long,
    val title: String,
    /** Empty when not given. */
    val composer: String,
    /** "G-dur", "a-moll". */
    val keyName: String?,
    val tempoBpm: Int?,
    val status: PieceStatus,
    /** Score of the latest take with its color and day; null without takes — the card shows a dash. */
    val lastScore: Int?,
    val lastScoreZone: Zone?,
    val lastDay: DayLabel?,
    val takes: Int,
    /** Absolute path of the first page's thumbnail; null without pages. */
    val thumbPath: String?,
)

data class RepertoireState(
    /** True until the stored pieces have been read once. */
    val loading: Boolean,
    /** All pieces, whatever the filter: the empty state. */
    val totalCount: Int,
    /** Null = all. */
    val filter: PieceStatus?,
    /** By last activity, freshest first, after the filter. */
    val cards: List<PieceCard>,
)

sealed interface RepertoireIntent {
    data class FilterSelected(val status: PieceStatus?) : RepertoireIntent

    data class PieceClicked(val id: Long) : RepertoireIntent

    data object AddClicked : RepertoireIntent
}

sealed interface RepertoireEffect {
    data class OpenPiece(val id: Long) : RepertoireEffect

    data object OpenNewPiece : RepertoireEffect
}
