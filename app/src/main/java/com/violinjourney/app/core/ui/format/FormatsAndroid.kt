package com.violinjourney.app.core.ui.format

import java.util.Locale

/** Follows the language the resources were resolved for — the locale of the configuration. */
fun Formats.use(locale: Locale) = use(locale.toLanguageTag())
