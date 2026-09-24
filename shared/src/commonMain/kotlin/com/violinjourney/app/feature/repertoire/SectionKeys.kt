package com.violinjourney.app.feature.repertoire

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
}
