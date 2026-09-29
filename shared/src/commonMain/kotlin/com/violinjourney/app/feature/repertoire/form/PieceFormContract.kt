package com.violinjourney.app.feature.repertoire.form

import com.violinjourney.app.core.domain.repertoire.Accidental
import com.violinjourney.app.core.domain.repertoire.KeyMode
import com.violinjourney.app.core.domain.repertoire.PieceDraft
import com.violinjourney.app.core.domain.repertoire.PieceSection
import com.violinjourney.app.core.domain.repertoire.PieceStatus
import com.violinjourney.app.core.domain.repertoire.SectionRef
import com.violinjourney.app.core.domain.repertoire.Tonic

enum class PieceFormDialog { DISCARD, DELETE }

/** The sheet over the form (spec 3.36.4): «Тональность» or «Раздел». Kept by the view model, so a turn of the phone keeps it up. */
enum class PieceFormSheet { KEY, SECTION }

/** A section the element may live in, as the field «Раздел» offers it; «Гаммы» is there but cannot be picked (spec 3.22). */
data class SectionOption(val ref: SectionRef, /** Null for a built-in section. */ val name: String?, val enabled: Boolean)

/**
 * The form of a piece, new or edited (spec 3.15, 3.36.4). Texts are as typed; cleaning happens on save. Nothing to save — no title —
 * is said over «Сохранить» from the first frame on ([canSave] false), not after a touch.
 */
data class PieceFormState(
    /** True until an edited piece has been read; a new piece is never loading. */
    val loading: Boolean,
    val isNew: Boolean,
    val draft: PieceDraft,
    val canSave: Boolean,
    val dialog: PieceFormDialog?,
    /** Limits for the fields and the counter of the notes. */
    val maxTitleLength: Int,
    val maxComposerLength: Int,
    val maxNotesLength: Int,
    /** Opened by «Добавить заметку»: the notes field takes the focus. */
    val focusNotes: Boolean,
    /** The title as stored: the delete dialog names the piece by it, whatever half-typed one the field holds. */
    val savedTitle: String = "",
    /** Where the element lives: decides the words and the fields of the form. */
    val section: SectionRef = SectionRef.BuiltIn(PieceSection.PIECES),
    /** Every section there is, for the row «Раздел» and its sheet — of a new element too: it can be moved before it is saved. */
    val sections: List<SectionOption> = emptyList(),
    /** The sheet up over the form, if any. */
    val sheet: PieceFormSheet? = null,
) {
    /** A bow stroke has neither an author nor a key; its name comes from a row of suggestions as readily as from the keyboard. */
    val stroke: Boolean get() = section == SectionRef.BuiltIn(PieceSection.STROKES)
    val etude: Boolean get() = section == SectionRef.BuiltIn(PieceSection.ETUDES)
}

sealed interface PieceFormIntent {
    data class TitleChanged(val text: String) : PieceFormIntent

    data class ComposerChanged(val text: String) : PieceFormIntent

    data class NotesChanged(val text: String) : PieceFormIntent

    /** The row «Тональность»: its sheet comes up. */
    data object KeyRowClicked : PieceFormIntent

    /** The row «Раздел»: its sheet comes up. */
    data object SectionRowClicked : PieceFormIntent

    /** A sheet swiped away, tapped outside, closed with «назад» or «Готово»: it goes, and nothing else changes. */
    data object SheetHidden : PieceFormIntent

    /** Picks the tonic; a tap on the picked one takes the key away — it is optional. */
    data class TonicClicked(val tonic: Tonic) : PieceFormIntent

    /** «Без тональности»: the key goes, and so does the sheet. */
    data object KeyCleared : PieceFormIntent

    data class AccidentalSelected(val accidental: Accidental) : PieceFormIntent

    data class ModeSelected(val mode: KeyMode) : PieceFormIntent

    /** The stepper: ±1 on a tap, ±5 while held. */
    data class TempoStepped(val by: Int) : PieceFormIntent

    /** A quick value; null clears the tempo. */
    data class TempoPicked(val bpm: Int?) : PieceFormIntent

    data class StatusSelected(val status: PieceStatus) : PieceFormIntent

    /** A row of the sheet «Раздел»: the section goes into the draft at once and the sheet closes; «Гаммы» cannot be picked. */
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
