package com.violinjourney.app.feature.repertoire

import com.violinjourney.app.core.domain.repertoire.Piece
import com.violinjourney.app.core.domain.repertoire.PieceSection
import com.violinjourney.app.core.domain.repertoire.SectionRef

/** A section as a navigation argument: the name of a built-in one, or «g» and the id of the player's own. */
object SectionKeys {
    private const val GROUP_PREFIX = "g"

    fun keyOf(ref: SectionRef): String = when (ref) {
        is SectionRef.BuiltIn -> ref.section.name
        is SectionRef.Custom -> GROUP_PREFIX + ref.groupId
    }

    /** An unknown key reads as «Произведения»: a stale link must not crash the screen. */
    fun refOf(key: String?): SectionRef {
        val section = PieceSection.entries.firstOrNull { it.name == key }
        if (section != null) return SectionRef.BuiltIn(section)
        val groupId = key?.removePrefix(GROUP_PREFIX)?.toLongOrNull()
        return if (key?.startsWith(GROUP_PREFIX) == true && groupId != null) SectionRef.Custom(groupId) else SectionRef.BuiltIn(PieceSection.PIECES)
    }

    /** In «Гаммы», «Этюды» and «Штрихи» the third step of the status reads «Выучено» (spec 3.22). */
    fun isExercise(ref: SectionRef): Boolean = ref is SectionRef.BuiltIn && ref.section != PieceSection.PIECES

    /*
     * A piece in a section of the player's own is in no built-in one, whatever its `section` says: deleting that section
     * moves its pieces out (`groupId` to null) in the same transaction, so a `groupId` always names a living group — the
     * rule of `PieceRules.sectionOf`, told without the list of groups.
     */

    /** The piece stands in «Гаммы», «Этюды» or «Штрихи»: its status ends in «Выучено». */
    fun isExercise(piece: Piece): Boolean = piece.groupId == null && isExercise(SectionRef.BuiltIn(piece.section))

    /** The piece stands in «Штрихи»: its tile shows a bow rather than a missing photo. */
    fun isStroke(piece: Piece): Boolean = piece.groupId == null && piece.section == PieceSection.STROKES

    /** The piece stands in «Этюды»: its tile is the one of an étude rather than a missing photo (spec 3.36.4). */
    fun isEtude(piece: Piece): Boolean = piece.groupId == null && piece.section == PieceSection.ETUDES
}
