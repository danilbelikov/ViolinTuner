package com.violinjourney.app.feature.repertoire.scale

/**
 * How the four kinds of a scale stand as tiles (spec 3.36.4, «Вид»: four tiles 2 × 2, the words whole — «минор гармонический», not
 * «гарм.»): two to a row while the widest word of the four fits the room of the words in a tile at 14 sp; else the words step down
 * together by 0.5 to 11.5 sp, as the steps of the status do (5.29 R4); where even then a word would break — one tile a row, at 14. A
 * word is never broken: two words go on two lines at their space (the tile grows). Pure: [widestAt] measures the widest word of
 * the four at a size, in the units of the rooms.
 */
internal object ScaleKindFit {
    const val MAX_SP = 14f
    const val MIN_SP = 11.5f
    const val STEP_SP = 0.5f

    /** The size of the words and how many tiles stand in a row. */
    data class Plan(val sizeSp: Float, val columns: Int)

    /** [halfRoom] — the room of the words in a tile of two to a row; the one tile a row has more. */
    fun plan(halfRoom: Float, widestAt: (sizeSp: Float) -> Float): Plan {
        var sizeSp = MAX_SP
        while (true) {
            if (widestAt(sizeSp) <= halfRoom) return Plan(sizeSp, columns = 2)
            if (sizeSp <= MIN_SP) return Plan(MAX_SP, columns = 1)
            sizeSp = maxOf(MIN_SP, sizeSp - STEP_SP)
        }
    }
}
