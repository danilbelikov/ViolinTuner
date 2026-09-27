package com.violinjourney.app.feature.repertoire.scale

import androidx.lifecycle.SavedStateHandle
import com.violinjourney.app.core.domain.repertoire.Accidental
import com.violinjourney.app.core.domain.repertoire.PieceStatus
import com.violinjourney.app.core.domain.repertoire.Tonic
import com.violinjourney.app.core.domain.repertoire.scale.ScaleKind

/**
 * The draft of the scale form in the saved state, as [com.violinjourney.app.feature.repertoire.form.PieceFormSaved]
 * keeps the piece form's: Android may end the process while the player is in another app, the notes field brings its
 * text back by itself (`rememberSaveable`), and the view model has to come back with the same draft — or «Сохранить»
 * would store what was there before and ✕ would close without asking. Written only on the player's own edits, so
 * nothing is kept before an edited scale has been read. Plain values only: a bundle holds them.
 */
internal object ScaleFormSaved {
    fun write(handle: SavedStateHandle, draft: ScaleDraft) {
        handle[TONIC] = draft.tonic?.name
        handle[ACCIDENTAL] = draft.accidental.name
        handle[KIND] = draft.kind.name
        handle[OCTAVES] = draft.octaves
        handle[TEMPO] = draft.tempoBpm
        handle[STATUS] = draft.status.name
        handle[NOTES] = draft.notes
    }

    /** Null when nothing was written: a form that was never edited reads its scale anew. */
    fun read(handle: SavedStateHandle): ScaleDraft? {
        val kind = handle.get<String>(KIND) ?: return null
        val empty = ScaleDraft()
        return ScaleDraft(
            tonic = Tonic.entries.firstOrNull { it.name == handle.get<String>(TONIC) },
            accidental = Accidental.entries.firstOrNull { it.name == handle.get<String>(ACCIDENTAL) } ?: empty.accidental,
            kind = ScaleKind.entries.firstOrNull { it.name == kind } ?: empty.kind,
            octaves = handle.get<Int>(OCTAVES) ?: empty.octaves,
            tempoBpm = handle.get<Int>(TEMPO),
            status = PieceStatus.entries.firstOrNull { it.name == handle.get<String>(STATUS) } ?: empty.status,
            notes = handle.get<String>(NOTES).orEmpty(),
        )
    }

    // None of them is a navigation argument of the form.
    private const val TONIC = "scaleDraft.tonic"
    private const val ACCIDENTAL = "scaleDraft.accidental"
    private const val KIND = "scaleDraft.kind"
    private const val OCTAVES = "scaleDraft.octaves"
    private const val TEMPO = "scaleDraft.tempo"
    private const val STATUS = "scaleDraft.status"
    private const val NOTES = "scaleDraft.notes"
}
