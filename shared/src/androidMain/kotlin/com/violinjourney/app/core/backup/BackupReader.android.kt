package com.violinjourney.app.core.backup

import com.violinjourney.app.core.io.syncToDisk
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Reads a copy as a stream, from wherever it lies (spec 3.20): the passport alone, a check of the
 * whole, or the unpacking into a folder beside the data. `ZipInputStream` checks the checksum of
 * every entry as it reads; this adds the passport, the completion mark and paths that stay inside.
 */
actual object BackupReader {
    private const val BUFFER = 256 * 1024

    /** The passport — the first entry. Throws [BackupFileException] for what is not a copy this app can take. */
    actual fun manifest(input: InputStream, knownFormat: Int, knownDatabase: Int): BackupManifest {
        try {
            ZipInputStream(input.buffered(BUFFER)).use { zip ->
                val first = zip.nextEntry ?: throw BackupFileException(BackupFileProblem.NotOurs)
                if (first.name != BackupManifest.ENTRY) throw BackupFileException(BackupFileProblem.NotOurs)
                // a first entry bigger than any passport is somebody else's file — and is not read whole into memory to find out
                val bytes = zip.readUpTo(BackupManifest.MAX_BYTES + 1)
                if (bytes.size > BackupManifest.MAX_BYTES) throw BackupFileException(BackupFileProblem.NotOurs)
                val manifest = BackupManifest.readFrom(ByteArrayInputStream(bytes)) ?: throw BackupFileException(BackupFileProblem.NotOurs)
                if (manifest.formatVersion > knownFormat || manifest.databaseVersion > knownDatabase) throw BackupFileException(BackupFileProblem.TooNew)
                return manifest
            }
        } catch (e: ZipException) {
            throw BackupFileException(BackupFileProblem.NotOurs)
        } catch (e: IllegalArgumentException) {
            // a name not in UTF-8 and not flagged as such — how an archiver of Windows writes Cyrillic: somebody else's archive
            throw BackupFileException(BackupFileProblem.NotOurs)
        }
    }

    /** Reads the whole archive and throws it away: every checksum is checked, and the completion mark has to be there and agree. */
    actual suspend fun verify(input: InputStream, totalBytes: Long, limitBytes: Long, onProgress: (BackupProgress) -> Unit): BackupSeen =
        walk(input, null, emptySet(), totalBytes, limitBytes, onProgress)

    /** Unpacks the entries of [parts] into [target] — `target/sessions/<name>`, `target/db/violin.db` … — and checks as [verify] does. */
    actual suspend fun extract(input: InputStream, target: File, totalBytes: Long, limitBytes: Long, parts: Set<BackupPart>, onProgress: (BackupProgress) -> Unit): BackupSeen =
        walk(input, target, parts, totalBytes, limitBytes, onProgress)

    private suspend fun walk(input: InputStream, target: File?, parts: Set<BackupPart>, totalBytes: Long, limitBytes: Long, onProgress: (BackupProgress) -> Unit): BackupSeen {
        val buffer = ByteArray(BUFFER)
        var done = 0L
        var files = 0
        var hasDatabase = false
        var mark: String? = null
        val seen = HashMap<BackupPart, Int>()
        val root = target?.canonicalFile
        try {
            ZipInputStream(input.buffered(BUFFER)).use { zip ->
                while (true) {
                    coroutineContext.ensureActive()
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    if (name == BackupPaths.COMPLETE_ENTRY) {
                        mark = zip.readUpTo(BackupEndMark.MAX_BYTES).decodeToString()
                        // the rest is read below: its checksum is checked all the same
                    }
                    val isFile = !entry.isDirectory && name != BackupManifest.ENTRY && name != BackupPaths.COMPLETE_ENTRY
                    val part = BackupPaths.partOf(name)
                    val index = if (isFile) (seen[part] ?: 0) + 1 else 0
                    if (isFile) {
                        seen[part] = index
                        files++
                        if (name == BackupPaths.DATABASE_ENTRY) hasDatabase = true
                    }
                    val file = if (isFile && root != null && part in parts) {
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
                                // more than the passport declared was never weighed against the room of the phone
                                if (done > limitBytes) throw BackupFileException(BackupFileProblem.Damaged)
                                onProgress(BackupProgress(part, index, 0, minOf(done, totalBytes), totalBytes))
                            }
                        }
                        // the database and the settings lie on the disk itself before the mark that puts them in place is left
                        if (part == BackupPart.DATA) out?.syncToDisk()
                    } finally {
                        out?.close()
                    }
                }
            }
        } catch (e: ZipException) {
            throw BackupFileException(BackupFileProblem.Damaged)
        } catch (e: java.io.EOFException) {
            throw BackupFileException(BackupFileProblem.Damaged)
        } catch (e: IllegalArgumentException) {
            // names are not under the checksum: a spoilt byte in a name halfway through a copy of ours is damage
            throw BackupFileException(BackupFileProblem.Damaged)
        }
        val result = BackupSeen(files, done, hasDatabase)
        // no mark — cut short; a mark that disagrees with what was read — not the archive it ended
        if (mark?.let { BackupEndMark.agrees(it, result) } != true) throw BackupFileException(BackupFileProblem.Damaged)
        return result
    }

    /** Up to [limit] bytes of the entry the stream stands at; the rest stays to be read. */
    private fun ZipInputStream.readUpTo(limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val chunk = ByteArray(minOf(limit, BUFFER))
        while (out.size() < limit) {
            val read = read(chunk, 0, minOf(chunk.size, limit - out.size()))
            if (read < 0) break
            out.write(chunk, 0, read)
        }
        return out.toByteArray()
    }
}
