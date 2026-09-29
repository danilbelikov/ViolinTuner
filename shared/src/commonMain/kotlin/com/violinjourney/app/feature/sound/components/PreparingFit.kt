package com.violinjourney.app.feature.sound.components

/**
 * How «Готовим минусовку…» stands in the first row of the player while the backing is made (spec 3.36.5, 5.29 R5: on the place of the
 * wave, 15 sp, 800; A/B and «Звук» of the compact panel dimmed beside it), its words whole — no word broken by the letter, none cut:
 *
 * 1. beside what the row holds after it ([Plan.stacked] false) in at most two lines of 15 sp, else 0.5 sp smaller down to
 *    [BESIDE_MIN_SP] — the panel keeps its height;
 * 2. where even that breaks a word (the compact panel of a narrow column lying: 60 dp beside A/B and «Звук»; German, French or Spanish
 *    on a phone of 360; the font 1.3) the words take the row alone and what stood after them goes down a row under them, at the
 *    end, still seen ([Plan.stacked] true — only where the row holds something after the words) — 15 sp, else smaller down to
 *    [MIN_SP];
 * 3. where not even [MIN_SP] keeps them whole, [MIN_SP] alone in the row: the last line ends in an ellipsis.
 *
 * Pure: [stands] tells whether the words stand whole in at most two lines of a room at a size; the caller measures them, in the units
 * of [beside] and [alone] — the room of the words beside what follows them, and without it.
 */
internal object PreparingFit {
    const val MAX_SP = 15f
    const val BESIDE_MIN_SP = 13f
    const val MIN_SP = 12f
    const val STEP_SP = 0.5f

    /** The size of the words and whether what follows them in the row stands a row lower. */
    data class Plan(val sizeSp: Float, val stacked: Boolean)

    fun plan(beside: Float, alone: Float, stands: (room: Float, sizeSp: Float) -> Boolean): Plan {
        sizes(BESIDE_MIN_SP).firstOrNull { stands(beside, it) }?.let { return Plan(it, stacked = false) }
        // nothing follows the words: the room alone is the room beside, there is nowhere to go down to
        val stacked = alone > beside
        return Plan(sizes(MIN_SP).firstOrNull { stands(alone, it) } ?: MIN_SP, stacked)
    }

    /** 15, 14.5 … [minSp]. */
    private fun sizes(minSp: Float): Sequence<Float> = generateSequence(MAX_SP) { it - STEP_SP }.takeWhile { it >= minSp }
}
