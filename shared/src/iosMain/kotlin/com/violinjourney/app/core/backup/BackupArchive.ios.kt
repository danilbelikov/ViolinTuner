package com.violinjourney.app.core.backup

import com.violinjourney.app.core.io.ByteInput
import com.violinjourney.app.core.io.ByteOutput
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.child
import com.violinjourney.app.core.io.makeDirectories
import com.violinjourney.app.core.io.openOutput
import com.violinjourney.app.core.io.syncToDisk
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

private const val BUFFER = 256 * 1024

actual object BackupWriter {
    actual suspend fun write(out: ByteOutput, manifest: BackupManifest, entries: List<BackupEntry>, onProgress: (BackupProgress) -> Unit): Long {
        val total = entries.sumOf { it.size }
        var done = 0L
        var written = 0
        var writtenBytes = 0L
        val counts = entries.groupingBy { it.part }.eachCount()
        val seen = HashMap<BackupPart, Int>()
        val buffer = ByteArray(BUFFER)
        val zip = ZipWriter(out)
        // the file is let go however the copy ends — a copy that broke or was stopped leaves no descriptor open, and
        // no zlib state of the entry it was writing either (ZipOutputStream.use does both on Android)
        try {
            zip.beginEntry(BackupManifest.ENTRY, compress = true)
            BackupManifestText.write(manifest).encodeToByteArray().let { zip.write(it, 0, it.size) }
            zip.endEntry()
            for (entry in entries) {
                coroutineContext.ensureActive()
                val index = (seen[entry.part] ?: 0) + 1
                seen[entry.part] = index
                val input = entry.open()
                if (input == null) {
                    // the snapshot of the database and the settings are the copy itself; a vanished medium is skipped
                    if (entry.required) throw okio.IOException("${entry.path} has gone")
                    done += entry.size
                    continue
                }
                // sound, video and photos are compressed already: deflating them again costs minutes and wins nothing
                zip.beginEntry(entry.path, compress = entry.part == BackupPart.DATA)
                input.use { from ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = from.read(buffer, 0, buffer.size)
                        if (read < 0) break
                        zip.write(buffer, 0, read)
                        done += read
                        writtenBytes += read
                        onProgress(BackupProgress(entry.part, index, counts.getValue(entry.part), minOf(done, total), total))
                    }
                }
                zip.endEntry()
                written++
            }
            zip.beginEntry(BackupPaths.COMPLETE_ENTRY, compress = true)
            BackupEndMark.text(written, writtenBytes).encodeToByteArray().let { zip.write(it, 0, it.size) }
            zip.endEntry()
            zip.finish()
            // «Готово» is about the disk, not the system's cache; only a copy written to its end is waited for
            out.syncToDisk()
        } catch (e: Throwable) {
            zip.abandon()
            throw e
        } finally {
            out.close()
        }
        return done
    }
}

actual object BackupReader {
    actual fun manifest(input: ByteInput, knownFormat: Int, knownDatabase: Int): BackupManifest {
        try {
            ZipReader(input).use { zip ->
                val first = zip.next() ?: throw BackupFileException(BackupFileProblem.NotOurs)
                if (first.name != BackupManifest.ENTRY) throw BackupFileException(BackupFileProblem.NotOurs)
                // a first entry bigger than any passport is somebody else's file — and is not read whole into memory to find out
                val bytes = readUpTo(first, BackupManifest.MAX_BYTES + 1)
                if (bytes.size > BackupManifest.MAX_BYTES) throw BackupFileException(BackupFileProblem.NotOurs)
                val manifest = BackupManifestText.read(bytes.decodeToString()) ?: throw BackupFileException(BackupFileProblem.NotOurs)
                if (manifest.formatVersion > knownFormat || manifest.databaseVersion > knownDatabase) throw BackupFileException(BackupFileProblem.TooNew)
                return manifest
            }
        } catch (e: ZipFormatException) {
            throw BackupFileException(BackupFileProblem.NotOurs)
        } catch (e: okio.EOFException) {
            throw BackupFileException(BackupFileProblem.NotOurs)
        } catch (e: IllegalArgumentException) {
            // a size no archive can have (a negative ZIP64 field), which okio refuses by `require`: somebody else's file
            throw BackupFileException(BackupFileProblem.NotOurs)
        }
    }

    actual suspend fun verify(input: ByteInput, totalBytes: Long, limitBytes: Long, onProgress: (BackupProgress) -> Unit): BackupSeen =
        walk(input, null, emptySet(), totalBytes, limitBytes, onProgress)

    actual suspend fun extract(
        input: ByteInput,
        target: PlatformFile,
        totalBytes: Long,
        limitBytes: Long,
        parts: Set<BackupPart>,
        onProgress: (BackupProgress) -> Unit,
    ): BackupSeen = walk(input, target, parts, totalBytes, limitBytes, onProgress)

    private suspend fun walk(
        input: ByteInput,
        target: PlatformFile?,
        parts: Set<BackupPart>,
        totalBytes: Long,
        limitBytes: Long,
        onProgress: (BackupProgress) -> Unit,
    ): BackupSeen {
        val buffer = ByteArray(BUFFER)
        var done = 0L
        var files = 0
        var hasDatabase = false
        var mark: String? = null
        val seen = HashMap<BackupPart, Int>()
        try {
            ZipReader(input).use { zip ->
                while (true) {
                    coroutineContext.ensureActive()
                    val entry = zip.next() ?: break
                    val name = entry.name
                    // the rest of the mark, if there is any, is read by the next `next()`: its checksum is checked all the same
                    if (name == BackupPaths.COMPLETE_ENTRY) mark = readUpTo(entry, BackupEndMark.MAX_BYTES).decodeToString()
                    val isFile = !name.endsWith('/') && name != BackupManifest.ENTRY && name != BackupPaths.COMPLETE_ENTRY
                    val part = BackupPaths.partOf(name)
                    val index = if (isFile) (seen[part] ?: 0) + 1 else 0
                    if (isFile) {
                        seen[part] = index
                        files++
                        if (name == BackupPaths.DATABASE_ENTRY) hasDatabase = true
                    }
                    val out = if (isFile && target != null && part in parts) outputFor(target, name) else null
                    try {
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = entry.read(buffer, 0, buffer.size)
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
        } catch (e: ZipFormatException) {
            throw BackupFileException(BackupFileProblem.Damaged)
        } catch (e: okio.EOFException) {
            throw BackupFileException(BackupFileProblem.Damaged)
        } catch (e: IllegalArgumentException) {
            // a size no archive can have, refused by okio's `require`: the copy is damaged, not the app
            throw BackupFileException(BackupFileProblem.Damaged)
        }
        val result = BackupSeen(files, done, hasDatabase)
        // no mark — cut short; a mark that disagrees with what was read — not the archive it ended
        if (mark?.let { BackupEndMark.agrees(it, result) } != true) throw BackupFileException(BackupFileProblem.Damaged)
        return result
    }

    /** An archive is a file from outside: an entry may not climb out of the folder it is unpacked into. */
    private fun outputFor(root: PlatformFile, name: String): ByteOutput {
        val parts = name.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." }) throw BackupFileException(BackupFileProblem.Damaged)
        var folder = root
        parts.dropLast(1).forEach { folder = folder.child(it) }
        folder.makeDirectories()
        return folder.child(parts.last()).openOutput() ?: throw okio.IOException("cannot write $name")
    }

    /** Up to [limit] bytes of [entry]; what is past them stays to be read. */
    private fun readUpTo(entry: ZipEntryReader, limit: Int): ByteArray {
        val out = okio.Buffer()
        val buffer = ByteArray(MANIFEST_CHUNK)
        while (out.size < limit) {
            val read = entry.read(buffer, 0, minOf(buffer.size.toLong(), limit - out.size).toInt())
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.readByteArray()
    }

    private const val MANIFEST_CHUNK = 8 * 1024
}
