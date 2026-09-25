package com.violinjourney.app.core.backup

/**
 * The passport of a copy as text, in the format of `java.util.Properties` — the one Android has written every copy in
 * so far — for the platforms that have no Properties: a copy made on an iPhone is read on Android and back.
 * Writes what `Properties.store` would (without its date comment) and reads what `Properties.load` reads.
 */
object BackupManifestText {
    fun write(manifest: BackupManifest): String = buildString {
        fun put(key: String, value: String) {
            append(escape(key, isKey = true)).append('=').append(escape(value, isKey = false)).append('\n')
        }
        put(BackupManifest.MAGIC_KEY, BackupManifest.MAGIC)
        with(manifest) {
            put("format", formatVersion.toString())
            put("app", appVersion)
            put("database", databaseVersion.toString())
            put("created", createdAtEpochMs.toString())
            put("device", device)
            put("parts", parts.joinToString(",") { it.name })
            with(counts) {
                put("sessions", sessions.toString()); put("takes", takes.toString()); put("pieces", pieces.toString())
                put("pages", pages.toString()); put("practiceDays", practiceDays.toString()); put("trophies", trophies.toString())
                put("level", level.toString()); put("withSound", withSound.toString()); put("videos", videos.toString())
            }
            bytes.forEach { (part, size) -> put("bytes.${part.name}", size.toString()) }
        }
    }

    /** Null when [text] is not the passport of a copy of this app. */
    fun read(text: String): BackupManifest? {
        val p = parse(text) ?: return null
        if (p[BackupManifest.MAGIC_KEY] != BackupManifest.MAGIC) return null
        fun int(key: String) = p[key]?.toIntOrNull()
        return BackupManifest(
            formatVersion = int("format") ?: return null,
            appVersion = p["app"].orEmpty(),
            databaseVersion = int("database") ?: return null,
            createdAtEpochMs = p["created"]?.toLongOrNull() ?: return null,
            device = p["device"].orEmpty(),
            parts = p["parts"].orEmpty().split(',').mapNotNull { name -> BackupPart.entries.firstOrNull { it.name == name } }.toSet() + BackupPart.DATA,
            counts = BackupCounts(
                sessions = int("sessions") ?: 0, takes = int("takes") ?: 0, pieces = int("pieces") ?: 0, pages = int("pages") ?: 0,
                practiceDays = int("practiceDays") ?: 0, trophies = int("trophies") ?: 0, level = int("level") ?: 1,
                withSound = int("withSound") ?: 0, videos = int("videos") ?: 0,
            ),
            bytes = BackupPart.entries.mapNotNull { part -> p["bytes.${part.name}"]?.toLongOrNull()?.let { part to it } }.toMap(),
        )
    }

    private fun escape(text: String, isKey: Boolean): String = buildString {
        text.forEachIndexed { index, c ->
            when {
                c == ' ' && (isKey || index == 0) -> append("\\ ")
                c == '\\' -> append("\\\\")
                c == '\t' -> append("\\t")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\u000c' -> append("\\f")
                c == '=' || c == ':' || c == '#' || c == '!' -> append('\\').append(c)
                c < ' ' || c > '~' -> append("\\u").append(c.code.toString(HEX).uppercase().padStart(UNICODE_DIGITS, '0'))
                else -> append(c)
            }
        }
    }

    /** The key-value pairs of `Properties.load`; null for a malformed `\u` escape, which `load` refuses too. */
    private fun parse(text: String): Map<String, String>? {
        val result = HashMap<String, String>()
        for (line in logicalLines(text)) {
            var i = 0
            while (i < line.length && line[i].isPropertiesSpace()) i++
            if (i == line.length || line[i] == '#' || line[i] == '!') continue
            val key = StringBuilder()
            while (i < line.length) {
                val c = line[i]
                if (c == '\\' && i + 1 < line.length) {
                    key.append(line, i, i + 2)
                    i += 2
                    continue
                }
                if (c == '=' || c == ':' || c.isPropertiesSpace()) break
                key.append(c)
                i++
            }
            // the separator: spaces, then at most one '=' or ':', then spaces
            while (i < line.length && line[i].isPropertiesSpace()) i++
            if (i < line.length && (line[i] == '=' || line[i] == ':')) i++
            while (i < line.length && line[i].isPropertiesSpace()) i++
            result[unescape(key.toString()) ?: return null] = unescape(line.substring(i)) ?: return null
        }
        return result
    }

    /** Lines joined where one ends in an odd number of backslashes; the spaces that start a continued line go. */
    private fun logicalLines(text: String): List<String> {
        val lines = mutableListOf<String>()
        val current = StringBuilder()
        var continued = false
        for (raw in text.split('\n', '\r')) {
            val part = if (continued) raw.trimStart { it.isPropertiesSpace() } else raw
            val slashes = part.length - part.trimEnd('\\').length
            if (slashes % 2 == 1) {
                current.append(part, 0, part.length - 1)
                continued = true
            } else {
                current.append(part)
                lines += current.toString()
                current.clear()
                continued = false
            }
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines
    }

    private fun unescape(text: String): String? {
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c != '\\' || i + 1 == text.length) {
                out.append(c)
                i++
                continue
            }
            val next = text[i + 1]
            i += 2
            when (next) {
                't' -> out.append('\t')
                'n' -> out.append('\n')
                'r' -> out.append('\r')
                'f' -> out.append('\u000c')
                'u' -> {
                    if (i + UNICODE_DIGITS > text.length) return null
                    out.append((text.substring(i, i + UNICODE_DIGITS).toIntOrNull(HEX) ?: return null).toChar())
                    i += UNICODE_DIGITS
                }
                else -> out.append(next)
            }
        }
        return out.toString()
    }

    private fun Char.isPropertiesSpace() = this == ' ' || this == '\t' || this == '\u000c'

    private const val HEX = 16
    private const val UNICODE_DIGITS = 4
}
