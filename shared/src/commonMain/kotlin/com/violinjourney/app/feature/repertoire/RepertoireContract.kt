package com.violinjourney.app.feature.repertoire

import com.violinjourney.app.core.domain.repertoire.PieceSection
import com.violinjourney.app.core.domain.repertoire.PieceStatus
import com.violinjourney.app.core.domain.repertoire.SectionCount
import com.violinjourney.app.core.domain.repertoire.SectionRef
import com.violinjourney.app.core.domain.repertoire.scale.ScaleSpec
import kotlinx.datetime.LocalDate

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
    /** Day of the latest take; null without takes — the card says «нет дублей» (spec 3.21: no score, no zone here). */
    val lastDate: LocalDate?,
    /** Not this year: the date is written with its year. */
    val lastDateOtherYear: Boolean = false,
    val takes: Int,
    /** The piece has a take marked as the best: a star by the date. */
    val hasBest: Boolean = false,
    /** Absolute path of the first page's thumbnail; null without pages. */
    val thumbPath: String?,
    /** A scale: its tile shows the clef with the key signature, its second line the kind and the octaves (handoff 24c). */
    val scale: ScaleSpec? = null,
    /** A bow stroke: a tile with a bow rather than a missing photo. */
    val stroke: Boolean = false,
    /** In «Гаммы», «Этюды» and «Штрихи» the third step of the status reads «Выучено». */
    val exercise: Boolean = false,
)

enum class SectionDialog { RENAME, DELETE }

/** The list of one section of the repertoire (spec 3.22, handoff 24c). */
data class RepertoireState(
    /** True until the stored pieces have been read once. */
    val loading: Boolean,
    /** All pieces, whatever the filter: the empty state. */
    val totalCount: Int,
    /** Null = all. */
    val filter: PieceStatus?,
    /** By last activity, freshest first, after the filter. */
    val cards: List<PieceCard>,
    val section: SectionRef = SectionRef.BuiltIn(PieceSection.PIECES),
    /** Null for a built-in section, and for one of the player's own that is gone. */
    val sectionName: String? = null,
    /** Everything in the section, whatever the filter: «выучено 2 из 7» under the name. */
    val count: SectionCount = SectionCount.EMPTY,
    val dialog: SectionDialog? = null,
    /** What has been typed into «Переименовать раздел». */
    val nameDraft: String = "",
    val maxNameLength: Int = 0,
) {
    val custom: Boolean get() = section is SectionRef.Custom
    val canRename: Boolean get() = nameDraft.isNotBlank()
}

sealed interface RepertoireIntent {
    data class FilterSelected(val status: PieceStatus?) : RepertoireIntent

    data class PieceClicked(val id: Long) : RepertoireIntent

    data object AddClicked : RepertoireIntent

    data object BackClicked : RepertoireIntent

    /** «Переименовать» and «Удалить раздел» of a section of the player's own. */
    data class DialogRequested(val dialog: SectionDialog) : RepertoireIntent

    data class NameChanged(val text: String) : RepertoireIntent

    data object DialogConfirmed : RepertoireIntent

    data object DialogDismissed : RepertoireIntent
}

sealed interface RepertoireEffect {
    data class OpenPiece(val id: Long) : RepertoireEffect

    /** The form of a new element of this section; «Гаммы» has a form of its own. */
    data class OpenNew(val section: SectionRef) : RepertoireEffect

    data object Close : RepertoireEffect
}
