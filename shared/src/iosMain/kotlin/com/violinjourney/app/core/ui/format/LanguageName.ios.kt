package com.violinjourney.app.core.ui.format

import platform.Foundation.NSLocale
import platform.Foundation.localizedStringForLanguageCode

actual fun languageName(tag: String): String {
    val locale = NSLocale(localeIdentifier = tag)
    return (locale.localizedStringForLanguageCode(tag) ?: tag).replaceFirstChar { it.titlecase() }
}
