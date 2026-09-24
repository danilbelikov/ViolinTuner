package com.violinjourney.app.core.ui.format

import java.util.Locale

actual fun languageName(tag: String): String = Locale.forLanguageTag(tag).let { locale ->
    locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
}
