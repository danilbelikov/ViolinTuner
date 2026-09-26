package com.violinjourney.app.core.analytics

/**
 * The text of a handled exception as the statistics may take it (spec 3.34, rule 1 — no names of files): the messages of
 * file systems and providers name the file — a path, a `content://` address with the name of the document, a name in
 * quotes, which may be the name a person gave a copy or a backing. Those are masked; the codes that tell the cause —
 * ENOSPC, EIO, SQLite's «database or disk is full» — stay. What cannot be told apart is masked rather than kept.
 */
object ErrorText {
    const val URI = "<uri>"
    const val NAME = "<name>"
    const val PATH = "<path>"

    private val uri = Regex("""\b[a-zA-Z][a-zA-Z0-9+.-]*://\S+""")
    private val quoted = listOf(Regex("“[^”\\n]*”"), Regex("«[^»\\n]*»"), Regex("\"[^\"\\n]*\""))

    // an ASCII quote opens only after a separator and closes only before one: «can't», «doesn't» are words, not quotes
    private val singleQuoted = Regex("""(^|[\s(\[=:,])'[^'\n]*'($|[\s)\].,:;])""")

    // a path starts at a slash after the start or a separator — «I/O error» is no path — and runs to the colon that follows
    // it, so that a folder with a space in its name («Application Support») leaves nothing behind
    private val path = Regex("""(^|[\s(\[=:,])/[^:\n]*""")

    fun scrub(message: String?): String? {
        if (message == null) return null
        var text = uri.replace(message, URI)
        quoted.forEach { text = it.replace(text, NAME) }
        text = singleQuoted.replace(text) { "${it.groupValues[1]}$NAME${it.groupValues[2]}" }
        text = path.replace(text) { "${it.groupValues[1]}$PATH" }
        return text
    }
}
