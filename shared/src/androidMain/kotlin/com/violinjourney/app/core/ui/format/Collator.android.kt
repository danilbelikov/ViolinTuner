package com.violinjourney.app.core.ui.format

import java.text.Collator
import java.util.Locale

// ICU on Android, the JDK's rules on the JVM of the tests. Canonical decomposition: «ё» is «е» with a diaeresis, an
// accent that breaks a tie, not a letter after «я».
internal actual fun collatorOf(languageTag: String): Comparator<String> {
    val collator = Collator.getInstance(Locale.forLanguageTag(languageTag)).apply { decomposition = Collator.CANONICAL_DECOMPOSITION }
    return Comparator { a, b -> collator.compare(a, b) }
}
