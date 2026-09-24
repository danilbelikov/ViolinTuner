package com.violinjourney.app.core.ui.format

import kotlin.math.abs
import kotlin.math.roundToInt

/** Cents as the screens write them: whole, with a sign; the minus is the typographic one, as in Formats. */
object CentsFormat {
    private const val MINUS = '−'

    fun signed(cents: Double): String {
        val rounded = cents.roundToInt()
        return when {
            rounded > 0 -> "+$rounded"
            rounded < 0 -> "$MINUS${abs(rounded)}"
            else -> "0"
        }
    }
}
