package com.example.violintuner.core.ui.format

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Number and date formats of the analysis screens. The interface is Russian only for now, so
 * the locale is fixed: month names must not follow the device language while the rest does not.
 */
object Formats {
    val LOCALE: Locale = Locale.forLanguageTag("ru")

    private const val MS_PER_SECOND = 1_000L
    private const val SECONDS_PER_MINUTE = 60L
    private const val MINUS = '−'
    private val DAY_AND_MONTH = DateTimeFormatter.ofPattern("d MMMM", LOCALE)
    private val DAY_AND_SHORT_MONTH = DateTimeFormatter.ofPattern("d MMM", LOCALE)

    /** "m:ss", minutes not padded: 0:07, 12:40, 60:00. */
    fun duration(ms: Long): String {
        val seconds = ms / MS_PER_SECOND
        return "%d:%02d".format(LOCALE, seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)
    }

    /** Whole cents with an explicit sign and a real minus: +6, −18, 0. */
    fun signedCents(cents: Double): String {
        val rounded = cents.roundToInt()
        return when {
            rounded > 0 -> "+$rounded"
            rounded < 0 -> "$MINUS${abs(rounded)}"
            else -> "0"
        }
    }

    /** One decimal with a comma: 7,3. */
    fun oneDecimal(value: Double): String = "%.1f".format(LOCALE, value)

    /** "14 сентября" */
    fun dayAndMonth(epochMs: Long, zone: ZoneId): String =
        DAY_AND_MONTH.format(Instant.ofEpochMilli(epochMs).atZone(zone))

    /** "13 сент." as the locale abbreviates it. */
    fun dayAndShortMonth(epochMs: Long, zone: ZoneId): String =
        DAY_AND_SHORT_MONTH.format(Instant.ofEpochMilli(epochMs).atZone(zone))
}
