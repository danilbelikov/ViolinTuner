package com.violinjourney.app.core.domain.repertoire

/** How a section stands (spec 3.22, handoff 24b): how much of it there is and how far it has come. */
data class SectionCount(val reading: Int, val learning: Int, val learned: Int) {
    val total: Int get() = reading + learning + learned
    val allLearned: Boolean get() = total > 0 && learned == total

    operator fun plus(other: SectionCount) = SectionCount(reading + other.reading, learning + other.learning, learned + other.learned)

    companion object {
        val EMPTY = SectionCount(0, 0, 0)
    }
}

data class SectionSummary(val ref: SectionRef, /** Null for a built-in section: its name is a word of the interface. */ val name: String?, val count: SectionCount)

/** Pure counting over the pieces. "Learnt" is the third step of the status and nothing else: no takes, no scores, no hours. */
object SectionStats {
    /** The four built-in sections in their order, then the player's own by name. */
    fun summaries(pieces: List<Piece>, groups: List<PieceGroup>): List<SectionSummary> {
        val bySection = pieces.groupBy { PieceRules.sectionOf(it, groups) }
        val builtIn = PieceSection.entries.map { section ->
            val ref = SectionRef.BuiltIn(section)
            SectionSummary(ref, name = null, count = countOf(bySection[ref].orEmpty()))
        }
        val own = groups
            .sortedWith(compareBy<PieceGroup>({ it.name.lowercase() }, { it.id }))
            .map { group -> SectionSummary(SectionRef.Custom(group.id), group.name, countOf(bySection[SectionRef.Custom(group.id)].orEmpty())) }
        return builtIn + own
    }

    fun countOf(pieces: List<Piece>): SectionCount = SectionCount(
        reading = pieces.count { it.status == PieceStatus.READING },
        learning = pieces.count { it.status == PieceStatus.LEARNING },
        learned = pieces.count { it.learned },
    )

    fun piecesOf(ref: SectionRef, pieces: List<Piece>, groups: List<PieceGroup>): List<Piece> =
        pieces.filter { PieceRules.sectionOf(it, groups) == ref }

    /** The same scale is not added twice: same key, kind and octaves. */
    fun sameScale(pieces: List<Piece>, draft: PieceDraft, exceptId: Long? = null): Piece? =
        draft.scale?.let { scale -> pieces.firstOrNull { it.scale == scale && it.id != exceptId } }
}
