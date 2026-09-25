package com.violinjourney.app.core.backup

import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Writes a copy (spec 3.20, 5.14) straight into the stream it is given — the place the person
 * picked — without building it anywhere first: there may be gigabytes, and the phone may not
 * have as much again. A ZIP, with ZIP64 where sizes ask for it. The passport goes first, a
 * completion mark last: an archive without the mark was cut short. Pure Kotlin; cancellable
 * between buffers.
 */
actual object BackupWriter {
    private const val BUFFER = 256 * 1024

    /**
     * A file that has vanished since the list was made — a recording deleted in the meantime — is
     * skipped: its recording comes back without its sound, which the app knows how to show.
     * Answers with the bytes written into entries.
     */
    actual suspend fun write(out: OutputStream, manifest: BackupManifest, entries: List<BackupEntry>, onProgress: (BackupProgress) -> Unit): Long {
        val total = entries.sumOf { it.size }
        var done = 0L
        var written = 0
        val counts = entries.groupingBy { it.part }.eachCount()
        val seen = HashMap<BackupPart, Int>()
        val buffer = ByteArray(BUFFER)
        ZipOutputStream(out.buffered(BUFFER)).use { zip ->
            zip.putNextEntry(ZipEntry(BackupManifest.ENTRY))
            manifest.writeTo(zip)
            zip.closeEntry()
            for (entry in entries) {
                coroutineContext.ensureActive()
                val index = (seen[entry.part] ?: 0) + 1
                seen[entry.part] = index
                val input = entry.open()
                if (input == null) {
                    done += entry.size
                    continue
                }
                // Sound, video and photos are compressed already: deflating them again costs minutes and wins nothing.
                // Level 0 rather than STORED — a stored entry needs its checksum beforehand, that is, every file read twice.
                zip.setLevel(if (entry.part == BackupPart.DATA) Deflater.DEFAULT_COMPRESSION else Deflater.NO_COMPRESSION)
                zip.putNextEntry(ZipEntry(entry.path))
                input.use { from ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = from.read(buffer)
                        if (read < 0) break
                        zip.write(buffer, 0, read)
                        done += read
                        onProgress(BackupProgress(entry.part, index, counts.getValue(entry.part), minOf(done, total), total))
                    }
                }
                zip.closeEntry()
                written++
            }
            zip.setLevel(Deflater.DEFAULT_COMPRESSION)
            zip.putNextEntry(ZipEntry(BackupPaths.COMPLETE_ENTRY))
            zip.write("entries=$written\n".toByteArray())
            zip.closeEntry()
        }
        return done
    }
}
