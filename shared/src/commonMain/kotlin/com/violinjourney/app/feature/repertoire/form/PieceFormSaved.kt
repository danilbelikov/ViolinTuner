package com.violinjourney.app.feature.repertoire.form

import androidx.lifecycle.SavedStateHandle
import com.violinjourney.app.core.domain.repertoire.Accidental
import com.violinjourney.app.core.domain.repertoire.KeyMode
import com.violinjourney.app.core.domain.repertoire.MusicalKey
import com.violinjourney.app.core.domain.repertoire.PieceDraft
import com.violinjourney.app.core.domain.repertoire.PieceSection
import com.violinjourney.app.core.domain.repertoire.PieceStatus
import com.violinjourney.app.core.domain.repertoire.Tonic

/**
 * The draft of the piece form in the saved state. Android may end the process while the player is in another app,
 * and the fields bring their text back by themselves (`rememberSaveable`): the view model has to come back with the
 * same draft, or «Сохранить» would store what was there before and ✕ would close without asking. Written only on the
 * player's own edits, so nothing is kept before the piece has been read. Plain values only: a bundle holds them.
 */
internal object PieceFormSaved {
    /** A draft that outlived its process, and whether the title field had been there. */
    class Kept(private val draft: PieceDraft, val titleTouched: Boolean) {
        /** The form's fields over [base]; the scale is not the piece form's to change, so it stays as [base] has it. */
        fun over(base: PieceDraft): PieceDraft = draft.copy(scale = base.scale)
    }

    fun write(handle: SavedStateHandle, draft: PieceDraft, titleTouched: Boolean) {
        handle[TITLE] = draft.title
        handle[COMPOSER] = draft.composer
        handle[NOTES] = draft.notes
        handle[TONIC] = draft.key?.tonic?.name
        handle[ACCIDENTAL] = draft.key?.accidental?.name
        handle[MODE] = draft.key?.mode?.name
        handle[TEMPO] = draft.tempoBpm
        handle[STATUS] = draft.status.name
        handle[SECTION] = draft.section.name
        handle[GROUP] = draft.groupId
        handle[TITLE_TOUCHED] = titleTouched
    }

    /** Null when nothing was written: a form that was never edited reads its piece anew. */
    fun read(handle: SavedStateHandle): Kept? {
        val title = handle.get<String>(TITLE) ?: return null
        val tonic = Tonic.entries.firstOrNull { it.name == handle.get<String>(TONIC) }
        val accidental = Accidental.entries.firstOrNull { it.name == handle.get<String>(ACCIDENTAL) }
        val mode = KeyMode.entries.firstOrNull { it.name == handle.get<String>(MODE) }
        val draft = PieceDraft(
            title = title,
            composer = handle.get<String>(COMPOSER).orEmpty(),
            // a key is all three parts or none
            key = if (tonic != null && accidental != null && mode != null) MusicalKey(tonic, accidental, mode) else null,
            tempoBpm = handle.get<Int>(TEMPO),
            status = PieceStatus.entries.firstOrNull { it.name == handle.get<String>(STATUS) } ?: PieceStatus.READING,
            notes = handle.get<String>(NOTES).orEmpty(),
            section = PieceSection.entries.firstOrNull { it.name == handle.get<String>(SECTION) } ?: PieceSection.PIECES,
            groupId = handle.get<Long>(GROUP),
        )
        return Kept(draft, titleTouched = handle.get<Boolean>(TITLE_TOUCHED) ?: false)
    }

    // None of them is a navigation argument of the form.
    private const val TITLE = "form.title"
    private const val COMPOSER = "form.composer"
    private const val NOTES = "form.notes"
    private const val TONIC = "form.tonic"
    private const val ACCIDENTAL = "form.accidental"
    private const val MODE = "form.mode"
    private const val TEMPO = "form.tempo"
    private const val STATUS = "form.status"
    private const val SECTION = "form.section"
    private const val GROUP = "form.group"
    private const val TITLE_TOUCHED = "form.titleTouched"
}
