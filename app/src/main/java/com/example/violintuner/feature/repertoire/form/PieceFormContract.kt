package com.example.violintuner.feature.repertoire.form

import com.example.violintuner.core.domain.repertoire.Accidental
import com.example.violintuner.core.domain.repertoire.KeyMode
import com.example.violintuner.core.domain.repertoire.PieceDraft
import com.example.violintuner.core.domain.repertoire.PieceSection
import com.example.violintuner.core.domain.repertoire.PieceStatus
import com.example.violintuner.core.domain.repertoire.SectionRef
import com.example.violintuner.core.domain.repertoire.Tonic

enum class PieceFormDialog { DISCARD, DELETE }

/** A section the element may live in, as the field «Раздел» offers it; «Гаммы» is there but cannot be picked (spec 3.22). */
data class SectionOption(val ref: SectionRef, /** Null for a built-in section. */ val name: String?, val enabled: Boolean)

/** The form of a piece, new or edited (spec 3.15, handoff 13e). Texts are as typed; cleaning happens on save. */
data class PieceFormState(
    /** True until an edited piece has been read; a new piece is never loading. */
    val loading: Boolean,
    val isNew: Boolean,
    val draft: PieceDraft,
    /** The title is empty and the user has been there: the field says why nothing can be saved. */
    val titleError: Boolean,
    val canSave: Boolean,
    val dialog: PieceFormDialog?,
    /** Limits for the fields and the counter of the notes. */
    val maxTitleLength: Int,
    val maxComposerLength: Int,
    val maxNotesLength: Int,
    /** Opened by «Добавить заметку»: the notes field takes the focus. */
    val focusNotes: Boolean,
    /** Where the element lives: decides the words and the fields of the form. */
    val section: SectionRef = SectionRef.BuiltIn(PieceSection.PIECES),
    /** Every section there is, for the field «Раздел» of an edit; a new element is born where it was asked for. */
    val sections: List<SectionOption> = emptyList(),
) {
    /** A bow stroke has neither an author nor a key; its name comes from a row of suggestions as readily as from the keyboard. */
    val stroke: Boolean get() = section == SectionRef.BuiltIn(PieceSection.STROKES)
    val etude: Boolean get() = section == SectionRef.BuiltIn(PieceSection.ETUDES)
}

sealed interface PieceFormIntent {
    data class TitleChanged(val text: String) : PieceFormIntent

    data class ComposerChanged(val text: String) : PieceFormIntent

    data class NotesChanged(val text: String) : PieceFormIntent

    /** Picks the tonic; a tap on the picked one takes the key away — it is optional. */
    data class TonicClicked(val tonic: Tonic) : PieceFormIntent

    data class AccidentalSelected(val accidental: Accidental) : PieceFormIntent

    data class ModeSelected(val mode: KeyMode) : PieceFormIntent

    /** The stepper: ±1 on a tap, ±5 while held. */
    data class TempoStepped(val by: Int) : PieceFormIntent

    /** A quick value; null clears the tempo. */
    data class TempoPicked(val bpm: Int?) : PieceFormIntent

    data class StatusSelected(val status: PieceStatus) : PieceFormIntent

    /** The field «Раздел» of an edit. */
    data class SectionSelected(val ref: SectionRef) : PieceFormIntent

    data object SaveClicked : PieceFormIntent

    /** The cross and the system back alike. */
    data object CloseClicked : PieceFormIntent

    data object DeleteClicked : PieceFormIntent

    data object DialogConfirmed : PieceFormIntent

    data object DialogDismissed : PieceFormIntent
}

sealed interface PieceFormEffect {
    /** Nothing saved, or an edit saved: back to where the form was opened from. */
    data object Close : PieceFormEffect

    /** A new piece: its screen takes the place of the form. */
    data class OpenCreated(val pieceId: Long) : PieceFormEffect

    /** The piece is gone: the form and its screen both close. */
    data object CloseDeleted : PieceFormEffect
}
