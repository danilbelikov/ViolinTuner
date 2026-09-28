package com.violinjourney.app.navigation

/**
 * One size for the labels of all four tabs (spec 3.36.1, 5.29): the largest of 12, 11.5 … 10 sp at which every label fits
 * the room of its item on one line. A long word (the French «Enregistrements» on a narrow screen) makes all four smaller
 * together, not itself alone — `TextAutoSize` would size each label apart. Never below 10 sp: past that a short form from
 * the translator is the answer (`docs/redesign/implementation/open-questions.md`, F), and the label is clipped until then.
 *
 * Pure: [size] only asks how wide the labels are at a size; the bar measures them with its own text measurer. The units of
 * the room and of the widths are the caller's, one and the same (pixels with the font scale in them, in the bar).
 */
internal object TabLabels {
    const val MAX_SP = 12f
    const val MIN_SP = 10f
    const val STEP_SP = 0.5f

    /** [widthAt] — the width the widest label takes at a size in sp; tried from the largest size down, the first that fits wins. */
    fun size(room: Float, widthAt: (sizeSp: Float) -> Float): Float {
        val steps = ((MAX_SP - MIN_SP) / STEP_SP).toInt()
        for (step in 0 until steps) {
            val sizeSp = MAX_SP - step * STEP_SP
            if (widthAt(sizeSp) <= room) return sizeSp
        }
        return MIN_SP
    }
}
