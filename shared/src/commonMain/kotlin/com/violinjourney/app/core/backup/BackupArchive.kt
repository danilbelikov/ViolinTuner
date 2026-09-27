package com.violinjourney.app.core.backup

import com.violinjourney.app.core.io.ByteInput
import com.violinjourney.app.core.io.ByteOutput
import com.violinjourney.app.core.io.PlatformFile
import okio.IOException

sealed interface BackupFileProblem {
    /** No passport of ours: somebody else's archive, or no archive at all. */
    data object NotOurs : BackupFileProblem

    /** Made by a version of the app that knows more than this one. */
    data object TooNew : BackupFileProblem

    /** Cut short, or damaged on the way. */
    data object Damaged : BackupFileProblem
}

class BackupFileException(val problem: BackupFileProblem) : IOException(problem.toString())

/**
 * Writes a copy (spec 3.20, 5.14) straight into the stream it is given — the place the person picked — without building
 * it anywhere first. A ZIP, with ZIP64 where sizes ask for it; the passport goes first, [BackupPaths.COMPLETE_ENTRY]
 * last. Each platform zips with what it has; the archives are the same.
 */
expect object BackupWriter {
    /**
     * Answers with the bytes of the entries; a medium that has vanished since the list was made is skipped, a
     * [BackupEntry.required] one fails the copy. [out] is closed either way; a copy written to its end is on the storage
     * itself before it is ([syncToDisk]), a copy that broke is not waited for.
     */
    suspend fun write(out: ByteOutput, manifest: BackupManifest, entries: List<BackupEntry>, onProgress: (BackupProgress) -> Unit): Long
}

/** Reads a copy as a stream, from wherever it lies (spec 3.20): the passport alone, a check of the whole, or the unpacking. */
expect object BackupReader {
    /**
     * The passport — the first entry, read up to [BackupManifest.MAX_BYTES]. Throws [BackupFileException] for what is not a
     * copy this app can take.
     */
    fun manifest(input: ByteInput, knownFormat: Int = BackupManifest.FORMAT_VERSION, knownDatabase: Int): BackupManifest

    /**
     * Reads the whole archive and throws it away: every checksum is checked, the completion mark has to be there and agree
     * with what was read. An archive that unpacks to more than [limitBytes] is damaged: nobody weighed that much against
     * the room of the phone.
     */
    suspend fun verify(input: ByteInput, totalBytes: Long, limitBytes: Long = Long.MAX_VALUE, onProgress: (BackupProgress) -> Unit): BackupSeen

    /**
     * Unpacks the entries of [parts] into [target] — `target/sessions/<name>`, `target/db/violin.db` … — and checks the whole
     * archive as [verify] does. The entries of [BackupPart.DATA] are on the storage itself when this returns: a mark that
     * puts them in place is left right after.
     */
    suspend fun extract(
        input: ByteInput,
        target: PlatformFile,
        totalBytes: Long,
        limitBytes: Long = Long.MAX_VALUE,
        parts: Set<BackupPart> = BackupPart.entries.toSet(),
        onProgress: (BackupProgress) -> Unit,
    ): BackupSeen
}

/** What a pass through an archive found: its files, their bytes, and whether the snapshot of the database is among them. */
data class BackupSeen(val entries: Int, val bytes: Long, val hasDatabase: Boolean)

/**
 * The completion mark, the last entry of a copy: how many files went in, and how many bytes. Copies made before 26.09.2026
 * say only the number of files; what a mark does not say is not checked.
 */
internal object BackupEndMark {
    private const val ENTRIES = "entries"
    private const val BYTES = "bytes"

    /** More than a mark ever holds: what is past it is not read into memory. */
    const val MAX_BYTES = 1024

    fun text(entries: Int, bytes: Long): String = "$ENTRIES=$entries\n$BYTES=$bytes\n"

    /** The number of files and of bytes the mark says; null for what it does not say. */
    fun read(text: String): Pair<Int?, Long?> {
        val values = text.lineSequence().mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } }.toMap()
        return values[ENTRIES]?.toIntOrNull() to values[BYTES]?.toLongOrNull()
    }

    /** True when an archive of [seen] is the one the mark [text] ends. */
    fun agrees(text: String, seen: BackupSeen): Boolean {
        val (entries, bytes) = read(text)
        return (entries == null || entries == seen.entries) && (bytes == null || bytes == seen.bytes)
    }
}

/** Folders inside a copy; the folders of media are the very folders of the data they go back into ([DataLayout]). */
object BackupPaths {
    const val DATABASE = "db"

    /** The snapshot of the database: a copy without it is no copy (spec 5.14). */
    const val DATABASE_ENTRY = "$DATABASE/violin.db"
    const val SETTINGS = "settings"
    const val PROFILE = DataLayout.PROFILE
    const val SHEETS = DataLayout.SHEETS
    const val SESSIONS = DataLayout.SESSIONS

    /** Accompaniment files (spec 3.32); a part of the sound. */
    const val BACKINGS = DataLayout.BACKINGS
    const val AUDIO_EXTENSION = ".m4a"

    /** The last entry of a copy: an archive without it was cut short. */
    const val COMPLETE_ENTRY = "complete.txt"

    /** The top folder of a path says which part it belongs to. */
    fun partOf(path: String): BackupPart = when (path.substringBefore('/')) {
        SHEETS -> BackupPart.SHEETS
        SESSIONS -> if (path.endsWith(AUDIO_EXTENSION)) BackupPart.AUDIO else BackupPart.VIDEO
        BACKINGS -> BackupPart.AUDIO
        else -> BackupPart.DATA
    }
}
