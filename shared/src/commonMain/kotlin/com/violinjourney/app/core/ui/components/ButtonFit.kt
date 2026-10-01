package com.violinjourney.app.core.ui.components

/**
 * The words of a button kept on one line where the button is narrow (spec 3.36.4, 5.29 R4): the size of its style where they fit,
 * else 0.5 sp smaller at a time down to a least size of the caller's — below it they go on two lines at a space, as the words of
 * an [AppButton] do, never broken. Like the labels of the tabs ([com.violinjourney.app.navigation.TabLabels]).
 *
 * Pure: [widthAt] only asks how wide the words are at a size; the caller measures them with its own text measurer, in the units of
 * [room].
 */
internal object ButtonFit {
    const val STEP_SP = 0.5f

    fun size(room: Float, maxSp: Float, minSp: Float, widthAt: (sizeSp: Float) -> Float): Float =
        largest(maxSp, minSp) { sizeSp -> widthAt(sizeSp) <= room }

    /**
     * The largest size from [maxSp] down, 0.5 sp at a time, at which [fits] says yes — [minSp] where none above it does ([minSp]
     * itself is not asked: the caller stands there whatever it says, cut with an ellipsis or on two lines). The lines of one line
     * of R7 ([OneLineText]) ask it of their own layout.
     */
    fun largest(maxSp: Float, minSp: Float, fits: (sizeSp: Float) -> Boolean): Float {
        var sizeSp = maxSp
        while (sizeSp > minSp) {
            if (fits(sizeSp)) return sizeSp
            sizeSp -= STEP_SP
        }
        return minSp
    }

    /**
     * The one size of the words of buttons standing side by side ([appButtonsSharedSize]): [maxSp] where the widest word of each
     * stands whole in its button, else 0.5 sp smaller for all of them together down to [minSp]; null where not even [minSp] keeps
     * every word whole — the caller stands the buttons one under the other. [overflowAt] — how much wider than the room of its button
     * the widest word of the tightest button is at a size: 0 or less, they all fit.
     */
    fun sharedSize(maxSp: Float, minSp: Float, overflowAt: (sizeSp: Float) -> Float): Float? {
        var sizeSp = maxSp
        while (true) {
            if (overflowAt(sizeSp) <= 0f) return sizeSp
            if (sizeSp <= minSp) return null
            sizeSp = maxOf(minSp, sizeSp - STEP_SP)
        }
    }
}
