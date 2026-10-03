package com.violinjourney.app.core.backup

import com.violinjourney.app.core.io.ByteInput

/** Every number of the backup, with starting values from docs/spec.md 5.14. None of it is about intonation. */
data class BackupConfig(
    /** Room that has to stay free after an archive has been built inside the app, or a copy unpacked beside the data. */
    val freeSpaceMarginBytes: Long = 50L * 1024 * 1024,
    /** «Отправить…» is offered up to this size: messengers take no gigabytes. */
    val shareUpToBytes: Long = 200L * 1024 * 1024,
    /** A copy older than this, with recordings made since, says so in its line of the settings. */
    val staleAfterDays: Long = 30,
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
    /**
     * The events of the calendar and the kinds of one's own (spec 3.35): they go in the snapshot of the database, and neither the passport
     * nor the chips of a copy name them (plan D39) — only what is in the app now counts them, so that a calendar alone is not taken for
     * nothing (review of stage 98б): its copy can be saved, and a restore over it asks first and names it.
     */
    val events: Int = 0,
    val ownKinds: Int = 0,
) {
    /** Nothing a person would miss: no recordings, no pieces, no days of practice, no events and no kinds of one's own. */
    val isEmpty: Boolean get() = sessions == 0 && pieces == 0 && practiceDays == 0 && events == 0 && ownKinds == 0
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

    companion object {
        /** The version this code writes, and the newest it can read. */
        const val FORMAT_VERSION = 1
        const val ENTRY = "manifest.txt"
        const val MAGIC_KEY = "violin-intonation-backup"
        const val MAGIC = "1"

        /**
         * A passport is a couple of kilobytes of keys; a first entry larger than this is not one of ours, and is not read
         * whole into memory to find that out. A limit of the format, not a number of the spec to tune.
         */
        const val MAX_BYTES = 64 * 1024
    }
}

/**
 * One file on its way into a copy. [path] inside the archive says where it goes back to: `sessions/<name>`, `db/violin.db`.
 * [required] — the copy is nothing without it: the snapshot of the database, the settings. A medium that has vanished since
 * the list was made is skipped (its recording comes back without its sound); a required file that has vanished fails the copy.
 */
class BackupEntry(val path: String, val part: BackupPart, val size: Long, val required: Boolean = false, val open: () -> ByteInput?)

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
