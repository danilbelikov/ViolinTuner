package com.violinjourney.app.navigation

/**
 * One size for the labels of all four tabs (spec 3.36.1, 5.29 R1): the largest of 12, 11.5 … 10 sp at which every label fits the
 * room of its item on one line. A long word makes all four smaller together, not itself alone — `TextAutoSize` would size each label
 * apart. Where not even 10 sp fits, the short labels come instead (`nav_history_short`: the French «Enreg.», the same word in every
 * other language; `docs/redesign/implementation/open-questions.md`, F), again from 12 sp; and where they do not fit at 10 sp either
 * — a large system font on a narrow phone: «Репертуар» at 1.5 on 320 dp — all four go on down together, half a point at a time, as
 * far as [LEAST_DP] on the screen, 10 sp of the default font, and no further. A label is clipped only past that.
 *
 * Pure: [fit] only asks how wide the labels are at a size; the bar measures them with its own text measurer. The units of the room
 * and of the widths are the caller's, one and the same (pixels with the font scale in them, in the bar).
 */
internal object TabLabels {
    const val MAX_SP = 12f
    const val MIN_SP = 10f
    const val STEP_SP = 0.5f

    /** The least a label is drawn on the screen, whatever the system font: 10 sp of the default font (spec 5.29 R1). */
    const val LEAST_DP = 10f

    /** The size of the labels, and whether they are the short ones. */
    data class Fit(val sizeSp: Float, val short: Boolean)

    /**
     * The labels of the bar in a [room]: [full] and [short] — the widest label (or the whole row of them) at a size in sp; [leastSp] —
     * the size in sp drawn as [LEAST_DP] at the font of the screen (10 at the default font, 6.67 at 1.5). Tried from the largest size
     * down, the first that fits wins.
     */
    fun fit(room: Float, leastSp: Float, full: (sizeSp: Float) -> Float, short: (sizeSp: Float) -> Float): Fit {
        sizes(MIN_SP).firstOrNull { full(it) <= room }?.let { return Fit(it, short = false) }
        val least = minOf(MIN_SP, leastSp)
        sizes(least).firstOrNull { short(it) <= room }?.let { return Fit(it, short = true) }
        return Fit(least, short = true)
    }

    /** The sizes to try, largest first: 12, 11.5, … down to [least] — the last one [least] itself, even off the half points. */
    fun sizes(least: Float): List<Float> = buildList {
        var sizeSp = MAX_SP
        while (sizeSp > least + EPSILON) {
            add(sizeSp)
            sizeSp -= STEP_SP
        }
        add(least)
    }

    /** Half points are exact in a float; a size computed from the font may land a hair off one. */
    private const val EPSILON = 0.001f
}
