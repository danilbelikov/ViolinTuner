package com.violinjourney.app.core.ui.format

import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.timeZoneForSecondsFromGMT

// A date without a time is read at noon UTC in a Gregorian calendar of UTC: no zone can move it to another day.
private val utc = NSTimeZone.timeZoneForSecondsFromGMT(0)
private val calendar = NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian).apply { timeZone = utc }
private val formatters = HashMap<String, NSDateFormatter>()

internal actual fun formatDate(pattern: String, languageTag: String, date: LocalDate): String {
    val formatter = formatters.getOrPut("$languageTag|$pattern") {
        NSDateFormatter().apply {
            locale = NSLocale(localeIdentifier = languageTag)
            calendar = NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian)
            timeZone = utc
            dateFormat = pattern
        }
    }
    val components = NSDateComponents().apply {
        year = date.year.toLong()
        month = date.month.number.toLong()
        day = date.day.toLong()
        hour = 12
    }
    val moment = calendar.dateFromComponents(components) ?: return date.toString()
    return formatter.stringFromDate(moment)
}
