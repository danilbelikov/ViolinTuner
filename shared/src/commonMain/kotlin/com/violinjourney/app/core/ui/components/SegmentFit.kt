package com.violinjourney.app.core.ui.components

/**
 * How the segments of a [SegmentedSwitch] share its row so that no label breaks inside a word (spec 3.36.4: «слова не
 * сокращаются: не помещаются — сегмент растёт до двух строк»). A label of two words may take two lines, breaking at its space; a
 * word wider than its segment would be cut by the letter («В / репертуа…»), so:
 *
 * 1. [Share.Equal] — equal shares while each holds the widest word of its label (a label that does not fit its share in one line
 *    goes on two at its space, as before);
 * 2. otherwise, and always for [Share.ByWords], by the words: where the row holds every label in one line, each segment gets its
 *    line and the rest in proportion; where it does not, each gets at least its widest word, the labels that need the least more
 *    to stand in one line get it first, and the others break at their spaces — never a single word that could stand whole while
 *    a label of two words could wrap;
 * 3. where even the widest words do not fit, the labels step down together — 14, 13.5 … [MIN_SP] — to the largest size at which
 *    they do, as the labels of the tabs (5.29 R1). Below [MIN_SP] they do not go: a word still too wide breaks then.
 *
 * Pure: the widths come from the caller's text measurer; the units are the caller's, one and the same (pixels).
 */
internal object SegmentFit {
    /** 11.5: at the font 1.3 each of the ten languages keeps its words whole in the column of 268 of landscape (Spanish needs it). */
    const val MIN_SP = 11.5f
    const val STEP_SP = 0.5f

    enum class Share { Equal, ByWords }

    /** A label at a size: [line] — its width on one line, [word] — the width of its widest word. */
    class Label(val line: Float, val word: Float)

    /** The size of all the labels and the width of each segment (they add up to the room). */
    data class Plan(val sizeSp: Float, val widths: List<Float>)

    /**
     * [room] — the width of the row; [around] — the room around each label in its segment (the edges and gaps of its pill and its
     * padding); [slack] — per segment, for the row laid out in whole pixels; [labelsAt] — the labels measured at a size in sp.
     */
    fun plan(room: Float, around: List<Float>, slack: Float, maxSp: Float, share: Share, labelsAt: (sizeSp: Float) -> List<Label>): Plan {
        var sizeSp = maxSp
        while (true) {
            val labels = labelsAt(sizeSp)
            val least = labels.mapIndexed { i, label -> label.word + around[i] + slack }
            if (share == Share.Equal && least.all { it <= room / labels.size }) return Plan(sizeSp, List(labels.size) { room / labels.size })
            shared(room, labels, least, around, slack)?.let { return Plan(sizeSp, it) }
            if (sizeSp <= MIN_SP) {
                // below the least size a word breaks: the segments share the row by their widest words
                val total = least.sum()
                return Plan(sizeSp, least.map { room * it / total })
            }
            sizeSp = maxOf(MIN_SP, sizeSp - STEP_SP)
        }
    }

    /**
     * A switch of equal shares whose labels step down to two smaller lines where they do not all stand in one ([SegmentedSwitch]'s
     * `shrinkToTwoLines`, spec 5.29 R5): true when a label's [lines] — its width in one line — with the room [around] it and the
     * [slack] is wider than its share of the [room].
     */
    fun shrinks(room: Float, around: List<Float>, slack: Float, lines: List<Float>): Boolean =
        lines.indices.any { i -> lines[i] + around[i] + slack > room / lines.size }

    /**
     * Labels that must each stand in one line — the compact switch of a player (spec 5.29 R5: seen 28, pressed 48), whose pills of 24
     * hold one line of 12 sp and would grow the switch with a second: from [maxSp] down by [STEP_SP] to [MIN_SP], the largest size at
     * which every label stands in one line — in equal shares where each fits its own, else each its line and the rest of the row in
     * proportion (Spanish «Con acompañamiento» beside «Solo violín» in the narrow column lying). Null where not even [MIN_SP] holds them
     * in one line: the caller lets them go on two lines rather than break a word. [linesAt] — the width of each label in one line at a
     * size; [around] and [slack] as for [plan].
     */
    fun oneLine(room: Float, around: List<Float>, slack: Float, maxSp: Float, linesAt: (sizeSp: Float) -> List<Float>): Plan? {
        var sizeSp = maxSp
        while (true) {
            val whole = linesAt(sizeSp).mapIndexed { i, line -> line + around[i] + slack }
            if (whole.all { it <= room / whole.size }) return Plan(sizeSp, List(whole.size) { room / whole.size })
            val sum = whole.sum()
            if (sum <= room) return Plan(sizeSp, whole.map { it + (room - sum) * it / sum })
            if (sizeSp <= MIN_SP) return null
            sizeSp = maxOf(MIN_SP, sizeSp - STEP_SP)
        }
    }

    /** The widths by the words, or null when the widest words do not fit the row. */
    private fun shared(room: Float, labels: List<Label>, least: List<Float>, around: List<Float>, slack: Float): List<Float>? {
        val whole = labels.mapIndexed { i, label -> label.line + around[i] + slack }
        val wholeSum = whole.sum()
        // every label in one line: each its own, the rest in proportion
        if (wholeSum <= room) return whole.map { it + (room - wholeSum) * it / wholeSum }
        if (least.sum() > room) return null
        val widths = least.toMutableList()
        var spare = room - least.sum()
        // one line for the labels that need the least more for it; the others break at their spaces
        for (i in labels.indices.sortedBy { whole[it] - least[it] }) {
            val more = whole[i] - least[i]
            if (more <= spare) {
                widths[i] = whole[i]
                spare -= more
            }
        }
        val sum = widths.sum()
        return widths.map { it + spare * it / sum }
    }
}
