package com.violinjourney.app.core.ui.components

/**
 * One line of words that must stay one line (spec 3.36.7, 5.29 R7: «не помещаются — мельче, … затем многоточие в названии; числа
 * не режутся»): the second line of «В путь · Прага» — «спишется 1 600 из 47 884» — and of «В дорогу» — «Вена → до Праги 1 128»,
 * where the number may stand anywhere in the line (English: «Vienna → 1 128 to Prague»). What is kept is laid out whole; the words
 * on its two sides give way, each cut with an ellipsis. Pure, with a test; drawn by [OneLineText].
 */
internal object ButtonLine {
    /** A line cut around the part it keeps: [before] and [after] may be cut, [kept] never. */
    data class Parts(val before: String, val kept: String, val after: String)

    /**
     * [line] as «before · kept · after» around the first [keep] in it; the spaces at the edges of [keep] go to it, so a side cut
     * with an ellipsis still stands a space off the number — «спи… 1 600 из 47 884», not «спи…1 600». Null where [keep] is empty or
     * not in [line]: the line is cut at its end, as a whole.
     */
    fun split(line: String, keep: String): Parts? {
        if (keep.isEmpty()) return null
        val at = line.indexOf(keep)
        if (at < 0) return null
        val before = line.substring(0, at)
        val after = line.substring(at + keep.length)
        val lead = before.length - before.trimEnd().length
        val trail = after.length - after.trimStart().length
        return Parts(before.trimEnd(), line.substring(at - lead, at + keep.length + trail), after.trimStart())
    }

    /**
     * The widths the two sides of the kept part get of the [room] beside it, from their own widths [before] and [after]: each its own
     * while both fit; else the narrower keeps its own when it needs no more than half, and the other takes the rest; else halves.
     * Never below 0.
     */
    fun sides(before: Float, after: Float, room: Float): Pair<Float, Float> {
        val free = room.coerceAtLeast(0f)
        return when {
            before + after <= free -> before to after
            before <= free / 2 -> before to free - before
            after <= free / 2 -> free - after to after
            else -> free / 2 to free / 2
        }
    }

    /**
     * The stretch of [line] from the first of [parts] in it to the end of the last — «1 600 из 47 884» of «спишется 1 600 из
     * 47 884»: the numbers and what stands between them, whatever order the language puts them in («47 884 のうち 1 600 を使います»).
     * Null where one of [parts] is not in [line], or none is given.
     */
    fun span(line: String, parts: List<String>): String? {
        var start = Int.MAX_VALUE
        var end = -1
        for (part in parts) {
            if (part.isEmpty()) continue
            val first = line.indexOf(part)
            if (first < 0) return null
            start = minOf(start, first)
            end = maxOf(end, line.lastIndexOf(part) + part.length)
        }
        return if (end < 0) null else line.substring(start, end)
    }
}
