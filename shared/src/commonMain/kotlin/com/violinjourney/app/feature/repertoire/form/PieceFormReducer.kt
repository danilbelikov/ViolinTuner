package com.violinjourney.app.feature.repertoire.form

import com.violinjourney.app.core.domain.repertoire.Accidental
import com.violinjourney.app.core.domain.repertoire.KeyMode
import com.violinjourney.app.core.domain.repertoire.MusicalKey
import com.violinjourney.app.core.domain.repertoire.PieceDraft
import com.violinjourney.app.core.domain.repertoire.PieceRules
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.domain.repertoire.Tonic

/** Rules of the piece form that are not about storage (spec 3.15). Pure. */
object PieceFormReducer {
    /** A tap on a tonic picks it — natural and major until said otherwise; a tap on the picked one removes the key. */
    fun clickTonic(key: MusicalKey?, tonic: Tonic): MusicalKey? = when {
        key == null -> MusicalKey(tonic, Accidental.NATURAL, KeyMode.MAJOR)
        key.tonic == tonic -> null
        else -> key.copy(tonic = tonic)
    }

    /** Typed text is capped while typing, so the counter never runs past its limit; edges are left for the save. */
    fun capped(text: String, max: Int): String = text.take(max)

    fun canSave(draft: PieceDraft, config: RepertoireConfig): Boolean = PieceRules.clean(draft, config) != null

    /** Whether leaving now would lose something: compared as they would be stored, so a stray space is not an edit. */
    fun isDirty(initial: PieceDraft, current: PieceDraft, config: RepertoireConfig): Boolean {
        val before = PieceRules.clean(initial, config) ?: initial.copy(title = "")
        val now = PieceRules.clean(current, config) ?: current.copy(title = current.title.trim())
        return before != now
    }
}
