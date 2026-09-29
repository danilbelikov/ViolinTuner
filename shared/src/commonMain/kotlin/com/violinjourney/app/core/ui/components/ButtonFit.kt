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

    fun size(room: Float, maxSp: Float, minSp: Float, widthAt: (sizeSp: Float) -> Float): Float {
        var sizeSp = maxSp
        while (sizeSp > minSp) {
            if (widthAt(sizeSp) <= room) return sizeSp
            sizeSp -= STEP_SP
        }
        return minSp
    }
}
