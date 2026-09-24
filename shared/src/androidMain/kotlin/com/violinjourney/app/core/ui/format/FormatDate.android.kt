package com.violinjourney.app.core.ui.format

import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate

private val formatters = ConcurrentHashMap<String, DateTimeFormatter>()

internal actual fun formatDate(pattern: String, languageTag: String, date: LocalDate): String =
    formatters.getOrPut("$languageTag|$pattern") { DateTimeFormatter.ofPattern(pattern, Locale.forLanguageTag(languageTag)) }
        .format(date.toJavaLocalDate())
