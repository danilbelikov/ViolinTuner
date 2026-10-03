package com.violinjourney.app.feature.events.form

/** How the plate «что меняется» stands: its caption over the values ([captionAbove]) or in their row; the widths of its texts in order. */
data class PlateLayout(val captionAbove: Boolean, val widths: List<Int>)

/**
 * The plate «что меняется» of the sheet of a repeat (spec 3.36.9, 5.29 R9 «Листы повтора»; review of stage 98б): the caption of the field,
 * the old value, the arrow and the new value in one row, as the row of the mockup lays them (events-form.html 6, `.diff` — a flex row, no
 * ellipsis): each text takes the width its words need on one line; where they do not all fit, they give way in proportion to what they
 * need — none narrower than its longest word — and wrap there by their words, so a short old value leaves its room to a long new one;
 * where even their longest words do not fit one row, the caption stands over the values. Nothing is cut. Pure, in pixels, with a test.
 */
object ChangePlateMath {
    /**
     * The plate [width] wide: [needs] — what each text needs on one line, [least] — its longest word, in the order caption (when
     * [captioned]), old value, new value; the arrow [arrow] wide between the values, [gap] between every two parts.
     */
    fun layout(needs: List<Int>, least: List<Int>, width: Int, arrow: Int, gap: Int, captioned: Boolean): PlateLayout {
        val inRow = width - arrow - gap * needs.size
        if (!captioned || least.sum() <= inRow) return PlateLayout(captionAbove = false, widths = shrink(needs, least, inRow))
        // the caption over the values, as wide as the plate; the values have the whole row
        val values = shrink(needs.drop(1), least.drop(1), width - arrow - gap * (needs.size - 1))
        return PlateLayout(captionAbove = true, widths = listOf(minOf(needs.first(), width)) + values)
    }

    /**
     * [needs] in [room]: as they are where they fit; else shrunk in proportion to what each needs, a text that comes to its [least]
     * staying there while the rest shrink on (the flex-shrink of CSS); where even the least do not fit, each gets the share of [room] its
     * least is of theirs.
     */
    fun shrink(needs: List<Int>, least: List<Int>, room: Int): List<Int> {
        if (needs.sum() <= room) return needs
        val leastSum = least.sum()
        if (leastSum >= room) return least.map { if (leastSum == 0) 0 else (room.toLong() * it / leastSum).toInt() }
        val out = needs.toMutableList()
        val held = BooleanArray(needs.size)
        while (true) {
            val free = room - needs.indices.sumOf { if (held[it]) out[it] else needs[it] }
            val scaled = needs.indices.sumOf { if (held[it]) 0L else needs[it].toLong() }
            if (scaled == 0L) break
            var heldNow = false
            for (i in needs.indices) {
                if (held[i]) continue
                // floor: the widths never add up to more than the room
                val shrunk = needs[i] + (free.toLong() * needs[i]).floorDiv(scaled).toInt()
                if (shrunk < least[i]) {
                    out[i] = least[i]
                    held[i] = true
                    heldNow = true
                } else {
                    out[i] = shrunk
                }
            }
            if (!heldNow) break
        }
        return out
    }
}
