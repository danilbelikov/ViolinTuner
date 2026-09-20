package com.example.violintuner.core.backup

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

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
 * Reads a copy as a stream, from wherever it lies (spec 3.20): the passport alone, a check of the
 * whole, or the unpacking into a folder beside the data. `ZipInputStream` checks the checksum of
 * every entry as it reads; this adds the passport, the completion mark and paths that stay inside.
 */
object BackupReader {
    private const val BUFFER = 256 * 1024

    /** The passport — the first entry. Throws [BackupFileException] for what is not a copy this app can take. */
    fun manifest(input: InputStream, knownFormat: Int = BackupManifest.FORMAT_VERSION, knownDatabase: Int): BackupManifest {
        try {
            ZipInputStream(input.buffered(BUFFER)).use { zip ->
                val first = zip.nextEntry ?: throw BackupFileException(BackupFileProblem.NotOurs)
                if (first.name != BackupManifest.ENTRY) throw BackupFileException(BackupFileProblem.NotOurs)
                val manifest = BackupManifest.readFrom(zip) ?: throw BackupFileException(BackupFileProblem.NotOurs)
                if (manifest.formatVersion > knownFormat || manifest.databaseVersion > knownDatabase) throw BackupFileException(BackupFileProblem.TooNew)
                return manifest
            }
        } catch (e: ZipException) {
            throw BackupFileException(BackupFileProblem.NotOurs)
        }
    }

    /** Reads the whole archive and throws it away: every checksum is checked, and the completion mark has to be there. */
    suspend fun verify(input: InputStream, totalBytes: Long, onProgress: (BackupProgress) -> Unit) = walk(input, null, totalBytes, onProgress)

    /** Unpacks into [target] — `target/sessions/<name>`, `target/db/violin.db` … — and checks as [verify] does. */
    suspend fun extract(input: InputStream, target: File, totalBytes: Long, onProgress: (BackupProgress) -> Unit) = walk(input, target, totalBytes, onProgress)

    private suspend fun walk(input: InputStream, target: File?, totalBytes: Long, onProgress: (BackupProgress) -> Unit) {
        val buffer = ByteArray(BUFFER)
        var done = 0L
        var complete = false
        val seen = HashMap<BackupPart, Int>()
        val root = target?.canonicalFile
        try {
            ZipInputStream(input.buffered(BUFFER)).use { zip ->
                while (true) {
                    coroutineContext.ensureActive()
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    if (name == BackupWriter.COMPLETE_ENTRY) complete = true
                    val isFile = !entry.isDirectory && name != BackupManifest.ENTRY && name != BackupWriter.COMPLETE_ENTRY
                    val part = partOf(name)
                    val index = if (isFile) (seen[part] ?: 0) + 1 else 0
                    if (isFile) seen[part] = index
                    val file = if (isFile && root != null) {
                        // an archive is a file from outside: an entry may not climb out of the folder it is unpacked into
                        File(root, name).canonicalFile.also { if (!it.path.startsWith(root.path + File.separator)) throw BackupFileException(BackupFileProblem.Damaged) }
                    } else {
                        null
                    }
                    file?.parentFile?.mkdirs()
                    val out = file?.outputStream()
                    try {
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = zip.read(buffer)
                            if (read < 0) break
                            out?.write(buffer, 0, read)
                            if (isFile) {
                                done += read
                                onProgress(BackupProgress(part, index, 0, minOf(done, totalBytes), totalBytes))
                            }
                        }
                    } finally {
                        out?.close()
                    }
                }
            }
        } catch (e: ZipException) {
            throw BackupFileException(BackupFileProblem.Damaged)
        } catch (e: java.io.EOFException) {
            throw BackupFileException(BackupFileProblem.Damaged)
        }
        if (!complete) throw BackupFileException(BackupFileProblem.Damaged)
    }

    /** The top folder of a path says which part it belongs to. */
    fun partOf(path: String): BackupPart = when (path.substringBefore('/')) {
        BackupPaths.SHEETS -> BackupPart.SHEETS
        BackupPaths.SESSIONS -> if (path.endsWith(BackupPaths.AUDIO_EXTENSION)) BackupPart.AUDIO else BackupPart.VIDEO
        else -> BackupPart.DATA
    }
}

/** Folders inside a copy; they are also the folders of `files/` the media go back into. */
object BackupPaths {
    const val DATABASE = "db"
    const val SETTINGS = "settings"
    const val PROFILE = "profile"
    const val SHEETS = "repertoire"
    const val SESSIONS = "sessions"
    const val AUDIO_EXTENSION = ".m4a"
}
