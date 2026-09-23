package com.violinjourney.app.core.backup

import java.io.InputStream
import java.io.OutputStream
import java.util.Properties

/** Every number of the backup, with starting values from docs/spec.md 5.14. None of it is about intonation. */
data class BackupConfig(
    /** Room that has to stay free after an archive has been built inside the app, or a copy unpacked beside the data. */
    val freeSpaceMarginBytes: Long = 50L * 1024 * 1024,
    /** «Отправить…» is offered up to this size: messengers take no gigabytes. */
    val shareUpToBytes: Long = 200L * 1024 * 1024,
    /** A copy older than this, with recordings made since, says so in its line of the settings. */
    val staleAfterDays: Long = 30,
    /** Where the estimate of a copy starts before this device has measured itself: a minute a gigabyte. */
    val startMsPerMb: Double = 60_000.0 / 1024,
    val estimateBaseMs: Long = 2_000,
)

/** What a copy is made of. [DATA] is the data itself and always goes; the rest is weight that may stay behind. */
enum class BackupPart { DATA, SHEETS, AUDIO, VIDEO }

/** How much of what there is — in the app, or in a copy. */
data class BackupCounts(
    val sessions: Int = 0,
    val takes: Int = 0,
    val pieces: Int = 0,
    val pages: Int = 0,
    val practiceDays: Int = 0,
    val trophies: Int = 0,
    val level: Int = 1,
    val withSound: Int = 0,
    val videos: Int = 0,
) {
    /** Nothing a person would miss: no recordings, no pieces, no days of practice. */
    val isEmpty: Boolean get() = sessions == 0 && pieces == 0 && practiceDays == 0
}

/**
 * The passport of a copy: the first entry of the archive, so that what is inside can be told
 * before anything is unpacked. Plain `key = value` pairs — there is no JSON library in the
 * project, `org.json` does not run in unit tests, and nobody is meant to read this by eye anyway.
 */
data class BackupManifest(
    val formatVersion: Int,
    val appVersion: String,
    val databaseVersion: Int,
    val createdAtEpochMs: Long,
    val device: String,
    val parts: Set<BackupPart>,
    val counts: BackupCounts,
    /** Bytes of each part as they lie in the app — the weight of the copy, not of the archive. */
    val bytes: Map<BackupPart, Long>,
) {
    val totalBytes: Long get() = bytes.filterKeys { it in parts }.values.sum()

    fun writeTo(out: OutputStream) {
        val p = Properties()
        p[MAGIC_KEY] = MAGIC
        p["format"] = formatVersion.toString()
        p["app"] = appVersion
        p["database"] = databaseVersion.toString()
        p["created"] = createdAtEpochMs.toString()
        p["device"] = device
        p["parts"] = parts.joinToString(",") { it.name }
        with(counts) {
            p["sessions"] = sessions.toString(); p["takes"] = takes.toString(); p["pieces"] = pieces.toString()
            p["pages"] = pages.toString(); p["practiceDays"] = practiceDays.toString(); p["trophies"] = trophies.toString()
            p["level"] = level.toString(); p["withSound"] = withSound.toString(); p["videos"] = videos.toString()
        }
        bytes.forEach { (part, size) -> p["bytes.${part.name}"] = size.toString() }
        p.store(out, null)
    }

    companion object {
        /** The version this code writes, and the newest it can read. */
        const val FORMAT_VERSION = 1
        const val ENTRY = "manifest.txt"
        private const val MAGIC_KEY = "violin-intonation-backup"
        private const val MAGIC = "1"

        /** Null when [input] is not the passport of a copy of this app. */
        fun readFrom(input: InputStream): BackupManifest? {
            val p = Properties()
            try {
                p.load(input)
            } catch (_: IllegalArgumentException) {
                return null // a malformed \u escape: whatever this is, it is not ours
            }
            if (p.getProperty(MAGIC_KEY) != MAGIC) return null
            fun int(key: String) = p.getProperty(key)?.toIntOrNull()
            return BackupManifest(
                formatVersion = int("format") ?: return null,
                appVersion = p.getProperty("app").orEmpty(),
                databaseVersion = int("database") ?: return null,
                createdAtEpochMs = p.getProperty("created")?.toLongOrNull() ?: return null,
                device = p.getProperty("device").orEmpty(),
                parts = p.getProperty("parts").orEmpty().split(',').mapNotNull { name -> BackupPart.entries.firstOrNull { it.name == name } }.toSet() + BackupPart.DATA,
                counts = BackupCounts(
                    sessions = int("sessions") ?: 0, takes = int("takes") ?: 0, pieces = int("pieces") ?: 0, pages = int("pages") ?: 0,
                    practiceDays = int("practiceDays") ?: 0, trophies = int("trophies") ?: 0, level = int("level") ?: 1,
                    withSound = int("withSound") ?: 0, videos = int("videos") ?: 0,
                ),
                bytes = BackupPart.entries.mapNotNull { part -> p.getProperty("bytes.${part.name}")?.toLongOrNull()?.let { part to it } }.toMap(),
            )
        }
    }
}

/** One file on its way into a copy. [path] inside the archive says where it goes back to: `sessions/<name>`, `db/violin.db`. */
class BackupEntry(val path: String, val part: BackupPart, val size: Long, val open: () -> InputStream?)

/** Where a copy is in its making, or in its coming back. */
data class BackupProgress(
    val part: BackupPart,
    /** The file of [part] being moved, from one, and how many there are. */
    val index: Int,
    val count: Int,
    val doneBytes: Long,
    val totalBytes: Long,
) {
    val fraction: Float get() = if (totalBytes <= 0) 0f else (doneBytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f)
}
