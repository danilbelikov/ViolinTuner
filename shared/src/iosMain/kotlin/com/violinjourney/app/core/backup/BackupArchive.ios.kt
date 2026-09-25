package com.violinjourney.app.core.backup

import com.violinjourney.app.core.io.ByteInput
import com.violinjourney.app.core.io.ByteOutput
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.child
import com.violinjourney.app.core.io.makeDirectories
import com.violinjourney.app.core.io.openOutput
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

private const val BUFFER = 256 * 1024

actual object BackupWriter {
    actual suspend fun write(out: ByteOutput, manifest: BackupManifest, entries: List<BackupEntry>, onProgress: (BackupProgress) -> Unit): Long {
        val total = entries.sumOf { it.size }
        var done = 0L
        var written = 0
        val counts = entries.groupingBy { it.part }.eachCount()
        val seen = HashMap<BackupPart, Int>()
        val buffer = ByteArray(BUFFER)
        val zip = ZipWriter(out)
        zip.beginEntry(BackupManifest.ENTRY, compress = true)
        BackupManifestText.write(manifest).encodeToByteArray().let { zip.write(it, 0, it.size) }
        zip.endEntry()
        for (entry in entries) {
            coroutineContext.ensureActive()
            val index = (seen[entry.part] ?: 0) + 1
            seen[entry.part] = index
            val input = entry.open()
            if (input == null) {
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
                    onProgress(BackupProgress(entry.part, index, counts.getValue(entry.part), minOf(done, total), total))
                }
            }
            zip.endEntry()
            written++
        }
        zip.beginEntry(BackupPaths.COMPLETE_ENTRY, compress = true)
        "entries=$written\n".encodeToByteArray().let { zip.write(it, 0, it.size) }
        zip.endEntry()
        zip.finish()
        return done
    }
}

actual object BackupReader {
    actual fun manifest(input: ByteInput, knownFormat: Int, knownDatabase: Int): BackupManifest {
        try {
            ZipReader(input).use { zip ->
                val first = zip.next() ?: throw BackupFileException(BackupFileProblem.NotOurs)
                if (first.name != BackupManifest.ENTRY) throw BackupFileException(BackupFileProblem.NotOurs)
                val manifest = BackupManifestText.read(readAll(first).decodeToString()) ?: throw BackupFileException(BackupFileProblem.NotOurs)
                if (manifest.formatVersion > knownFormat || manifest.databaseVersion > knownDatabase) throw BackupFileException(BackupFileProblem.TooNew)
                return manifest
            }
        } catch (e: ZipFormatException) {
            throw BackupFileException(BackupFileProblem.NotOurs)
        } catch (e: okio.EOFException) {
            throw BackupFileException(BackupFileProblem.NotOurs)
        }
    }

    actual suspend fun verify(input: ByteInput, totalBytes: Long, onProgress: (BackupProgress) -> Unit) = walk(input, null, totalBytes, onProgress)

    actual suspend fun extract(input: ByteInput, target: PlatformFile, totalBytes: Long, onProgress: (BackupProgress) -> Unit) =
        walk(input, target, totalBytes, onProgress)

    private suspend fun walk(input: ByteInput, target: PlatformFile?, totalBytes: Long, onProgress: (BackupProgress) -> Unit) {
        val buffer = ByteArray(BUFFER)
        var done = 0L
        var complete = false
        val seen = HashMap<BackupPart, Int>()
        try {
            ZipReader(input).use { zip ->
                while (true) {
                    coroutineContext.ensureActive()
                    val entry = zip.next() ?: break
                    val name = entry.name
                    if (name == BackupPaths.COMPLETE_ENTRY) complete = true
                    val isFile = !name.endsWith('/') && name != BackupManifest.ENTRY && name != BackupPaths.COMPLETE_ENTRY
                    val part = BackupPaths.partOf(name)
                    val index = if (isFile) (seen[part] ?: 0) + 1 else 0
                    if (isFile) seen[part] = index
                    val out = if (isFile && target != null) outputFor(target, name) else null
                    try {
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = entry.read(buffer, 0, buffer.size)
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
        } catch (e: ZipFormatException) {
            throw BackupFileException(BackupFileProblem.Damaged)
        } catch (e: okio.EOFException) {
            throw BackupFileException(BackupFileProblem.Damaged)
        }
        if (!complete) throw BackupFileException(BackupFileProblem.Damaged)
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

    private fun readAll(entry: ZipEntryReader): ByteArray {
        val out = okio.Buffer()
        val buffer = ByteArray(MANIFEST_CHUNK)
        while (true) {
            val read = entry.read(buffer, 0, buffer.size)
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.readByteArray()
    }

    private const val MANIFEST_CHUNK = 8 * 1024
}
