package com.violinjourney.app.core.ui.format

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import platform.Foundation.NSLocale
import platform.Foundation.NSMakeRange
import platform.Foundation.NSString
import platform.Foundation.compare

// Foundation compares by the locale's collation (ICU underneath) when it is given one: the alphabet of the language,
// case and accents only for a tie.
@OptIn(ExperimentalForeignApi::class)
internal actual fun collatorOf(languageTag: String): Comparator<String> {
    val locale = NSLocale(localeIdentifier = languageTag)
    return Comparator { a, b ->
        @Suppress("CAST_NEVER_SUCCEEDS")
        (a as NSString).compare(b, options = 0u, range = NSMakeRange(0u, a.length.convert()), locale = locale).toInt()
    }
}
