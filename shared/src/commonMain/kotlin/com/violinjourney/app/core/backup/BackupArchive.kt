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
    /** Answers with the bytes written into entries; a file that has vanished since the list was made is skipped. */
    suspend fun write(out: ByteOutput, manifest: BackupManifest, entries: List<BackupEntry>, onProgress: (BackupProgress) -> Unit): Long
}

/** Reads a copy as a stream, from wherever it lies (spec 3.20): the passport alone, a check of the whole, or the unpacking. */
expect object BackupReader {
    /** The passport — the first entry. Throws [BackupFileException] for what is not a copy this app can take. */
    fun manifest(input: ByteInput, knownFormat: Int = BackupManifest.FORMAT_VERSION, knownDatabase: Int): BackupManifest

    /** Reads the whole archive and throws it away: every checksum is checked, and the completion mark has to be there. */
    suspend fun verify(input: ByteInput, totalBytes: Long, onProgress: (BackupProgress) -> Unit)

    /** Unpacks into [target] — `target/sessions/<name>`, `target/db/violin.db` … — and checks as [verify] does. */
    suspend fun extract(input: ByteInput, target: PlatformFile, totalBytes: Long, onProgress: (BackupProgress) -> Unit)
}

/** Folders inside a copy; they are also the folders of `files/` the media go back into. */
object BackupPaths {
    const val DATABASE = "db"
    const val SETTINGS = "settings"
    const val PROFILE = "profile"
    const val SHEETS = "repertoire"
    const val SESSIONS = "sessions"

    /** Accompaniment files (spec 3.32); a part of the sound. */
    const val BACKINGS = "backings"
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
