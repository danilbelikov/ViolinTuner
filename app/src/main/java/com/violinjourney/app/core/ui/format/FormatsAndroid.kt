package com.violinjourney.app.core.ui.format

import java.util.Locale

/** Follows the language the resources were resolved for — the locale of the configuration. */
fun Formats.use(locale: Locale) = use(locale.toLanguageTag())

/** The language of the interface as a Java locale: for what the app still writes through the JVM (casing, the language's own name). */
val Formats.LOCALE: Locale get() = Locale.forLanguageTag(language.tag)
