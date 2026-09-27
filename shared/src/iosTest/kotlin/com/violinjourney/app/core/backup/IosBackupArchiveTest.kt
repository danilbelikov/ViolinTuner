package com.violinjourney.app.core.backup

import com.violinjourney.app.core.io.ByteOutput
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.child
import com.violinjourney.app.core.io.openInput
import com.violinjourney.app.core.io.openOutput
import com.violinjourney.app.core.io.sizeBytes
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.test.runTest
import okio.Deflater
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.dataWithContentsOfFile
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.posix.memcpy

/**
 * A copy on iOS: the archive written here is read back whole, an archive of Android's `ZipOutputStream` is read too, and
 * what is written here is the sample Android's `BackupArchiveTest` reads (ArchiveFixtures).
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalEncodingApi::class)
class IosBackupArchiveTest {
    private val folder = PlatformFile(NSTemporaryDirectory() + NSUUID().UUIDString).also {
        NSFileManager.defaultManager.createDirectoryAtPath(it.path, true, null, null)
    }

    @AfterTest
    fun cleanUp() {
        NSFileManager.defaultManager.removeItemAtPath(folder.path, null)
    }

    private val manifest = BackupManifest(
        formatVersion = 1, appVersion = "1.0", databaseVersion = 13, createdAtEpochMs = 1_790_000_000_000, device = "iPhone 15 Pro Max",
        parts = setOf(BackupPart.DATA, BackupPart.AUDIO), counts = BackupCounts(sessions = 2),
        bytes = mapOf(BackupPart.DATA to 11L, BackupPart.AUDIO to 300_000L),
    )

    @Test
    fun `an archive written here is read back whole`() = runTest {
        val audio = Random(3).nextBytes(300_000)
        val db = "hello world".encodeToByteArray()
        val entries = listOf(
            entryOf("db/violin.db", BackupPart.DATA, db),
            entryOf("sessions/a.m4a", BackupPart.AUDIO, audio),
        )
        val archive = folder.child("copy.zip")
        BackupWriter.write(archive.openOutput()!!, manifest, entries) {}
        assertEquals(manifest, BackupReader.manifest(archive.openInput()!!, knownDatabase = 13))
        BackupReader.verify(archive.openInput()!!, archive.sizeBytes()) {}
        val staging = folder.child("staging")
        BackupReader.extract(archive.openInput()!!, staging, 0) {}
        assertTrue(audio.contentEquals(bytesOf(staging.child("sessions").child("a.m4a"))))
        assertTrue(db.contentEquals(bytesOf(staging.child("db").child("violin.db"))))
    }

    @Test
    fun `a large compressible entry comes back as it was`() = runTest {
        val rows = buildString { repeat(40_000) { append("row $it of the practice log;") } }.encodeToByteArray() + Random(9).nextBytes(100_000)
        val archive = folder.child("copy.zip")
        BackupWriter.write(archive.openOutput()!!, manifest, listOf(entryOf("db/violin.db", BackupPart.DATA, rows))) {}
        val staging = folder.child("staging")
        BackupReader.extract(archive.openInput()!!, staging, 0) {}
        assertTrue(rows.contentEquals(bytesOf(staging.child("db").child("violin.db"))))
    }

    @Test
    fun `a damaged archive is told as such`() = runTest {
        val archive = folder.child("copy.zip")
        BackupWriter.write(archive.openOutput()!!, manifest, listOf(entryOf("sessions/a.m4a", BackupPart.AUDIO, Random(5).nextBytes(50_000)))) {}
        val bytes = bytesOf(archive)
        bytes[bytes.size / 2] = (bytes[bytes.size / 2] + 1).toByte()
        val broken = folder.child("broken.zip")
        writeBytes(broken, bytes)
        val failure = assertFailsWith<BackupFileException> { BackupReader.verify(broken.openInput()!!, bytes.size.toLong()) {} }
        assertEquals(BackupFileProblem.Damaged, failure.problem)
    }

    @Test
    fun `an archive of Android is read`() = runTest {
        val archive = folder.child("android.zip")
        writeBytes(archive, Base64.decode(ANDROID_ARCHIVE))
        val read = BackupReader.manifest(archive.openInput()!!, knownDatabase = 13)
        assertEquals("Google Pixel 10a", read.device)
        assertEquals(setOf(BackupPart.DATA, BackupPart.AUDIO), read.parts)
        val staging = folder.child("staging")
        BackupReader.extract(archive.openInput()!!, staging, 0) {}
        assertEquals("hello world", bytesOf(staging.child("db").child("violin.db")).decodeToString())
        assertEquals(4000, bytesOf(staging.child("sessions").child("a.m4a")).size)
    }

    /** The sample Android's test reads (ArchiveFixtures): a change of what Zip.kt writes is a new sample to check there. */
    @Test
    fun `the archive this phone writes is the one Android is tested on`() = runTest {
        val archive = folder.child("fixture.zip")
        val entries = listOf(
            entryOf("db/violin.db", BackupPart.DATA, ArchiveFixtures.database),
            entryOf("sessions/a.m4a", BackupPart.AUDIO, ArchiveFixtures.sound),
        )
        BackupWriter.write(archive.openOutput()!!, ArchiveFixtures.manifest, entries) {}
        val written = bytesOf(archive)
        if (!written.contentEquals(ArchiveFixtures.fromIos())) {
            fail("Zip.kt writes other bytes now; put this into ArchiveFixtures.FROM_IOS and run BackupArchiveTest of Android:\n" + Base64.encode(written))
        }
    }

    @Test
    fun `a copy from an older app is taken and one from a newer app is refused`() = runTest {
        // an older database is brought up by the migrations once restored (spec 5.14); a newer one no migration knows
        val older = folder.child("older.zip")
        BackupWriter.write(older.openOutput()!!, manifest.copy(databaseVersion = 12), emptyList()) {}
        assertEquals(12, BackupReader.manifest(older.openInput()!!, knownDatabase = 13).databaseVersion)
        val newer = folder.child("newer.zip")
        BackupWriter.write(newer.openOutput()!!, manifest.copy(databaseVersion = 14), emptyList()) {}
        val failure = assertFailsWith<BackupFileException> { BackupReader.manifest(newer.openInput()!!, knownDatabase = 13) }
        assertEquals(BackupFileProblem.TooNew, failure.problem)
    }

    @Test
    fun `a size no archive can have is damage and not a fall`() = runTest {
        // a local header whose ZIP64 field says «-1»: okio refuses to read a negative count with IllegalArgumentException
        val header = okio.Buffer().apply {
            writeIntLe(0x04034b50) // local header
            writeShortLe(45)
            writeShortLe(0) // flags: no descriptor
            writeShortLe(0) // stored
            writeIntLe(0) // time, date
            writeIntLe(0) // crc
            writeIntLe(-1) // compressed: see ZIP64
            writeIntLe(-1) // size: see ZIP64
            val name = BackupManifest.ENTRY.encodeToByteArray()
            writeShortLe(name.size)
            writeShortLe(20)
            write(name)
            writeShortLe(1) // ZIP64
            writeShortLe(16)
            writeLongLe(-1) // size
            writeLongLe(-1) // compressed
            write(ByteArray(100) { 7 })
        }.readByteArray()
        val archive = folder.child("strange.zip")
        writeBytes(archive, header)
        val notOurs = assertFailsWith<BackupFileException> { BackupReader.manifest(archive.openInput()!!, knownDatabase = 13) }
        assertEquals(BackupFileProblem.NotOurs, notOurs.problem)
        val damaged = assertFailsWith<BackupFileException> { BackupReader.verify(archive.openInput()!!, header.size.toLong()) {} }
        assertEquals(BackupFileProblem.Damaged, damaged.problem)
    }

    @Test
    fun `a copy whose database has gone fails instead of passing whole`() = runTest {
        val archive = folder.child("gone.zip")
        val gone = BackupEntry(BackupPaths.DATABASE_ENTRY, BackupPart.DATA, 11, required = true) { null }
        assertFailsWith<okio.IOException> { BackupWriter.write(archive.openOutput()!!, manifest, listOf(gone)) {} }
    }

    @Test
    fun `the end mark counts the entries and the bytes and the reader holds the archive to it`() = runTest {
        val audio = Random(3).nextBytes(300_000)
        val archive = folder.child("copy.zip")
        BackupWriter.write(archive.openOutput()!!, manifest, listOf(entryOf("db/violin.db", BackupPart.DATA, "hello world".encodeToByteArray()), entryOf("sessions/a.m4a", BackupPart.AUDIO, audio))) {}
        assertEquals(BackupSeen(entries = 2, bytes = 300_011, hasDatabase = true), BackupReader.verify(archive.openInput()!!, 300_011) {})
        // the same entries under a mark that says otherwise: not the archive the mark ended
        val odd = folder.child("odd.zip")
        val out = odd.openOutput()!!
        val zip = ZipWriter(out)
        zip.beginEntry(BackupManifest.ENTRY, compress = true)
        BackupManifestText.write(manifest).encodeToByteArray().let { zip.write(it, 0, it.size) }
        zip.endEntry()
        zip.beginEntry("db/violin.db", compress = true)
        "hello world".encodeToByteArray().let { zip.write(it, 0, it.size) }
        zip.endEntry()
        zip.beginEntry(BackupPaths.COMPLETE_ENTRY, compress = true)
        "entries=1\nbytes=5\n".encodeToByteArray().let { zip.write(it, 0, it.size) }
        zip.endEntry()
        zip.finish()
        out.close()
        assertEquals(BackupFileProblem.Damaged, assertFailsWith<BackupFileException> { BackupReader.verify(odd.openInput()!!, 11) {} }.problem)
    }

    @Test
    fun `a passport larger than any passport is not ours`() = runTest {
        val archive = folder.child("huge.zip")
        val out = archive.openOutput()!!
        val zip = ZipWriter(out)
        zip.beginEntry(BackupManifest.ENTRY, compress = true)
        val text = BackupManifestText.write(manifest) + buildString { repeat(3_000) { append("key$it=${"x".repeat(20)}\n") } }
        text.encodeToByteArray().let { zip.write(it, 0, it.size) }
        zip.endEntry()
        zip.finish()
        out.close()
        assertEquals(BackupFileProblem.NotOurs, assertFailsWith<BackupFileException> { BackupReader.manifest(archive.openInput()!!, knownDatabase = 13) }.problem)
    }

    @Test
    fun `an archive that unpacks past what it declared is damaged`() = runTest {
        val archive = folder.child("copy.zip")
        BackupWriter.write(archive.openOutput()!!, manifest, listOf(entryOf("sessions/a.m4a", BackupPart.AUDIO, Random(4).nextBytes(50_000)))) {}
        assertEquals(BackupFileProblem.Damaged, assertFailsWith<BackupFileException> { BackupReader.verify(archive.openInput()!!, 50_000, limitBytes = 1_000) {} }.problem)
        BackupReader.verify(archive.openInput()!!, 50_000, limitBytes = 50_000) {}
    }

    @Test
    fun `only the parts asked for are unpacked and synced`() = runTest {
        val audio = Random(6).nextBytes(100_000)
        val db = "hello world".encodeToByteArray()
        val archive = folder.child("copy.zip")
        BackupWriter.write(archive.openOutput()!!, manifest, listOf(entryOf("db/violin.db", BackupPart.DATA, db), entryOf("sessions/a.m4a", BackupPart.AUDIO, audio))) {}
        val staging = folder.child("staging")
        BackupReader.extract(archive.openInput()!!, staging, 100_011, parts = setOf(BackupPart.DATA)) {}
        assertTrue(db.contentEquals(bytesOf(staging.child("db").child("violin.db"))))
        assertEquals(0, bytesOf(staging.child("sessions").child("a.m4a")).size)
        BackupReader.extract(archive.openInput()!!, staging, 100_011, parts = setOf(BackupPart.AUDIO)) {}
        assertTrue(audio.contentEquals(bytesOf(staging.child("sessions").child("a.m4a"))))
    }

    @Test
    fun `an entry whose deflated size alone passes the limit is read with the descriptor it was written with`() {
        // media go deflated at level 0: every stored block adds five bytes, so the archive's bytes of an entry just under
        // the limit are past it — and the writer gives that entry eight-byte sizes, as ZipOutputStream does
        val limit = 100_000L
        val bytes = Random(8).nextBytes(99_996)
        val archive = folder.child("edge.zip")
        val out = archive.openOutput()!!
        val zip = ZipWriter(out, narrowUpTo = limit)
        zip.beginEntry("sessions/a.m4a", compress = false)
        zip.write(bytes, 0, bytes.size)
        zip.endEntry()
        zip.finish()
        out.close()
        ZipReader(archive.openInput()!!, narrowUpTo = limit).use { reader ->
            val entry = assertNotNull(reader.next())
            val read = okio.Buffer()
            val buffer = ByteArray(8_192)
            while (true) {
                val got = entry.read(buffer, 0, buffer.size)
                if (got < 0) break
                read.write(buffer, 0, got)
            }
            assertTrue(bytes.contentEquals(read.readByteArray()))
            // the descriptor is read at the next entry: a width other than the writer's would be damage here
            assertNull(reader.next())
        }
    }

    @Test
    fun `a copy that breaks while it is written lets go of its file`() = runTest {
        var closed = 0
        var writes = 0
        val breaking = object : ByteOutput() {
            override fun write(buffer: ByteArray, offset: Int, count: Int) {
                if (++writes == 3) throw okio.IOException("write failed: Input/output error")
            }

            override fun close() {
                closed++
            }
        }
        val entries = listOf(entryOf("db/violin.db", BackupPart.DATA, "hello world".encodeToByteArray()), entryOf("sessions/a.m4a", BackupPart.AUDIO, Random(2).nextBytes(600_000)))
        assertFailsWith<okio.IOException> { BackupWriter.write(breaking, manifest, entries) {} }
        assertEquals(1, closed)
    }

    @Test
    fun `a copy stopped halfway lets go of the zlib state of the entry it was writing`() {
        val made = mutableListOf<Deflater>()
        val out = folder.child("stopped.zip").openOutput()!!
        val zip = ZipWriter(out, deflaterOf = { level -> Deflater(level, true).also { made += it } })
        zip.beginEntry("sessions/a.m4a", compress = false)
        val bytes = Random(9).nextBytes(100_000)
        zip.write(bytes, 0, bytes.size)
        val deflater = made.single()
        assertTrue(deflater.getBytesRead() > 0, "the entry is being written")
        zip.abandon()
        // a Deflater that was ended says so, it no longer answers
        assertFailsWith<IllegalStateException> { deflater.getBytesRead() }
        zip.abandon()
        out.close()
    }

    @Test
    fun `names not in UTF-8 make somebody else's archive and a damaged copy and not a fall`() = runTest {
        // how an archiver of Windows writes Cyrillic: the name in its own code page — «É» as the one byte 0xC9, no UTF-8
        val foreign = folder.child("foreign.zip")
        writeBytes(foreign, spoilt(archiveOf("Noten/Xtude.pdf" to Random(5).nextBytes(100)), "Noten/Xtude.pdf"))
        assertEquals(BackupFileProblem.NotOurs, assertFailsWith<BackupFileException> { BackupReader.manifest(foreign.openInput()!!, knownDatabase = 13) }.problem)

        // a copy of ours whose name of an entry got spoilt halfway: names are not under the checksum
        val archive = folder.child("copy.zip")
        BackupWriter.write(archive.openOutput()!!, manifest, listOf(entryOf("sessions/Xtude.m4a", BackupPart.AUDIO, Random(6).nextBytes(100)))) {}
        val copy = folder.child("spoilt.zip")
        writeBytes(copy, spoilt(bytesOf(archive), "sessions/Xtude.m4a"))
        assertEquals(manifest, BackupReader.manifest(copy.openInput()!!, knownDatabase = 13))
        assertEquals(BackupFileProblem.Damaged, assertFailsWith<BackupFileException> { BackupReader.verify(copy.openInput()!!, 100) {} }.problem)
    }

    @Test
    fun `an entry may not climb out of the folder it is unpacked into`() = runTest {
        listOf("sessions/../../outside.txt", "sessions//a.m4a", "/outside.txt").forEach { path ->
            val archive = folder.child("sly.zip")
            NSFileManager.defaultManager.removeItemAtPath(archive.path, null)
            BackupWriter.write(archive.openOutput()!!, manifest, listOf(entryOf(path, BackupPart.AUDIO, Random(7).nextBytes(10)))) {}
            val failure = assertFailsWith<BackupFileException>(path) { BackupReader.extract(archive.openInput()!!, folder.child("staging").child("inner"), 10) {} }
            assertEquals(BackupFileProblem.Damaged, failure.problem, path)
            assertFalse(NSFileManager.defaultManager.fileExistsAtPath(folder.child("outside.txt").path), path)
            assertFalse(NSFileManager.defaultManager.fileExistsAtPath(folder.child("staging").child("outside.txt").path), path)
        }
    }

    /** An archive of entries as they are given, with no passport: somebody else's zip. */
    private fun archiveOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val file = folder.child("plain.zip")
        val out = file.openOutput()!!
        val zip = ZipWriter(out)
        entries.forEach { (name, bytes) ->
            zip.beginEntry(name, compress = true)
            zip.write(bytes, 0, bytes.size)
            zip.endEntry()
        }
        zip.finish()
        out.close()
        return bytesOf(file)
    }

    /** [bytes] with the «X» of [name] in its local header turned into 0xC9 — «É» of Latin-1, which no UTF-8 has alone. */
    private fun spoilt(bytes: ByteArray, name: String): ByteArray {
        val needle = name.encodeToByteArray()
        val at = (0..bytes.size - needle.size).first { start -> needle.indices.all { bytes[start + it] == needle[it] } }
        return bytes.copyOf().also { it[at + name.indexOf('X')] = 0xC9.toByte() }
    }

    private fun entryOf(path: String, part: BackupPart, bytes: ByteArray): BackupEntry {
        val file = folder.child(path.replace('/', '_'))
        writeBytes(file, bytes)
        return BackupEntry(path, part, bytes.size.toLong()) { file.openInput() }
    }

    private fun writeBytes(file: PlatformFile, bytes: ByteArray) {
        file.openOutput()!!.use { it.write(bytes, 0, bytes.size) }
    }

    private fun bytesOf(file: PlatformFile): ByteArray {
        val data = NSData.dataWithContentsOfFile(file.path) ?: return ByteArray(0)
        val out = ByteArray(data.length.toInt())
        if (out.isNotEmpty()) out.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
        return out
    }

    private companion object {
        /** Made by `java.util.zip.ZipOutputStream`, the way Android writes a copy: data descriptors, one entry at level 0. */
        const val ANDROID_ARCHIVE =
            "UEsDBBQACAgIADchOV0AAAAAAAAAAAAAAAAMAAAAbWFuaWZlc3QudHh0PY7BCoJAFEX37ysG2qbMTGYovIUgRUQUWB/w1FcMmTM4" +
            "k9TfZxGd5Vmce2frwYiKndBLIZNcZnmSin21E1rqFMg5VHEC9Suwj4tzuT1gIqX8ibI4FagUNANT4BbVKpN/oKVANXlGtYCWR9Mw" +
            "bqy9diyO5smdUJLgYoc7BVTgaAgeP8H5dwY8e29s71HDaGxn+sj0wfYUJhnV1Nwe0zV4A1BLBwgEPJ+yoAAAAL8AAABQSwMEFAAI" +
            "CAgANyE5XQAAAAAAAAAAAAAAAA4AAABzZXNzaW9ucy9hLm00YQGgD1/wmRcPuxg0d6NalMm/OQt3AgnTKlldSph9QfSz5ZJHGr4F" +
            "GUu1XcqzkQAWF1qhR+Hz6Y7oHt9/rkC/55PZ/O1SsgLjTRXlxMqvFm/C7c6k+xtl4kCk6USzHQcF5si3HPi9/JfGlmkwWLIu3RR5" +
            "8ZUyl+HIIUGmpG2ZNkz0YbMXyZ/uYUzFp0sCn4+BTjjZOZW2lKohUIR4kdddqN3dxGQ0r+tQeBrgQ2T5s6PzcH6reUcLo8iFtmxJ" +
            "mADaZBoipCDumYnsmFkB5BbJw4+/euIqAKQXTGS1FE3GmDHC1en4x9yxw6J22gPFxTR92HKLI0JzFJ626bsP9fQRGgIcmgFoqYEY" +
            "HJoyTwrsCTlaMhMBjr3iMrabRj3/6UxzupyZYMgLaiFMx7Bn+QKVsBgKmOuo/Om/ioGbAfVSnrwg76zU2VphZllarF5QXNY/zfdE" +
            "UqF+2vLOcUGaZRJQD8vBguHvMHIcjVf87oDu/QetQWVTVYW3YxVXgoJmJR3ppZIqCFdBODQMaX+pgv3y4eAx7vxjalkLlLPt5wv+" +
            "t8bo0piPSdhnuDMcljEOVs8gGU31HNbq7AlYudcuy1NBjhNKI92E3EN8slGliVP5ZCTnvTOi4GNuvbvB1Yb51tbJPiIKXQEQJhq6" +
            "XJdvfQeyu8Z5h4xi1DMoGcxDj9joAbJIR+r6jrv+QeG+WMmTb3BXAZhzbQCJY6fy8MAL43VDCKmpSSpY58xXkcdYIY9rigzVbZ14" +
            "/3J7+QtksKhLVJHB9YQPf43AJmIZnw4zrxecZolGUCRCTMiULbW5z98U04BiAQjWu9K1SMBuc4dmhHsKXPn5WQjV702MfEaMwAhm" +
            "4cRmYw2PSbhicGiDIW16rChvMqo2VapoTg+FB4CjWpuR5542TQOGgFXWacA6zMjrvyP/mlscbv5a7tNv541+Nj029PffRvDyQg3Y" +
            "rw/1XKaviRGizpg8UWpiojWuO+fvRwzRBJvOFQhIMhpYvDSVgJrPOcFqb7RUi2J705Ok5PRXMMMc117RUEt5ysooFzbOmNzHmuB+" +
            "Kc8bpzQKYFHLZrBGLuT4CnFrG51OlQWLKx27feLxRn/03U5tZ1wncwjXW0TnZIltjI/dxAlO2NjGqlzRZDI70zNvA7VikZ/uktIm" +
            "LWqH3pqDBsw4ww4Yv3roRdp96pEsvMjq8k0zhM09U2qmep5TkG2tRIAu81lFL/6eZCFLl+ZpAP9wWBELyMxLpijH3hEik3Ynw7qe" +
            "jRgnRrKmVK3fN36BSddewGjYTx7s2ZXdaKlx5oSI8LRJghjQfkeEB6idNY35lmUVZQo3JBlbmRm39aaXnCyuXShNjN0atVx6xiJP" +
            "tcoxpKz/+MMr4RHLceJBFrcrqITIMyjNZOb0EMcqZM5ju5eiZSSs+vOouY+qYDAMiAuDT6ViW7KQjVeX0+503FITFRfhb8j3sTBF" +
            "euvPDybeHwzkqSTfWD/buiYq9wNOELqKyAkEQ56NDlu4QR/qRqSVMF0O/yhuhVtDKnojZf+D3eq1iGwL4yP59pIcRyDzV3GMGhA5" +
            "Pwgnzrf0Ew9g6WHoMylMk12ZBuL5dNCRlAGJbY4CcZjlJiFp6LduYeaLtshjKQnFrYY44LKYFXSlr18pyfj1LfXhIevLp6OuEw5i" +
            "hh30tcVgsbTqqtSI9XrLFKfLwucxMfkJfjy88ecYPZgp+8QN9cFR5WI9/yipaNZ0GhvHk/Aq8nBpfjc6XPVlYfHFNQ+D6Ygsb8cH" +
            "2TVmt2IHKCvKxgYxIpX68oRIH+AX2g7wfrSEBV0leDPkbuDTUrBY1mEuNIGc29Z2efh9Aepz4T1iN7IKQoUL43X8ZCvP1ILJsdpq" +
            "U5EsVgzndlceXgmLRIWHZOEXQ/rlHJTqYPe/bgsZ5X6OZ38qTkLOEyrLFbBZ9mL8mAxi6KZNwbdy+qY9JHgJ3jC6lSI1Hdn3h9wL" +
            "KvCKTg0gx55cbOA9KkCeV83Cs/LHKNmN3UK1ojmdEWxnD1YqfirfqQqAkdrg4r42c7A1l5O5REVuN2ZXEMA9K0glNtZ3DmIyOk6B" +
            "9wlPxo6oqGqlk3w0ixiyM8WhMQhpsEdK5tO9UMAUdrOM2KQ5LoIDQwBsjBMjkjhCbO/qbnZLqHNn/UUdX+RSkMJhGytWDWO1S+qx" +
            "/5ZxY14fJSc+LDkS27N1zDMYjb1kGmVzyPjrvDmJs3lM2rRZdDTrv5uRn8ZqV2/SbT3LpN4mwW7LR8oqTsRY8v+7iVgWRZ6if9Ja" +
            "8lEEMaaegwCilNSW3weTh+XzzqRyUynCv/t+jt8zhkVEg5cyTQjtmd99Ql2A4nYssC0ONTA4QJ7+fYSoFtlF0+A3LSbe5YNYfXUq" +
            "aOzVWgjBh1avns8x1scZ3SlP4pL3DGhFXzbi5AYevNmivu0/kOiN2CaI7/QNJ/P1QpqYDZNvVfhJMp+ZZXF3uYESTd/YTlAdgo+j" +
            "yBxtT7LIOpX74LUBO+QUMENYXC5nbGGqGMah5RL0VaqG0msZjFduSh6fvLQza5aflJ3YTF0i/c2wSffmKNrM9sUkJV5Eil562ekc" +
            "eJ6uJmcdnMSjK9w1FWBL+O98kVUuze3k0SMmM7ypBiGcQbxL+uMNo+U/K5HyMBQiIhcVMStVp7dHy164/OqWsjVy1ak90JS5LD7a" +
            "VGsuVw2kNndpfhvLUzuUk4767uzImKjgLQfmgRRy6U8PmWIxsx2uh8/pRMPWS4EoY8ljfTviJMkHpU9ZtRqcNrUqJbuZPFA53+KP" +
            "lCm4vlxEUgqnoXCjfp5ZON2YZVBqYt+5BKsxbJhdE1esfOqyQLzPvEoWZyGHa9Z2qVD+OG/KDj+YOjFaZEvcY3wN6p4tAVDF7Uot" +
            "sVS4xgeqo2/p8uog7Npm9heeV9CQzlPnzmA0OYRgU+5e0YtEWPYHqQ4aoRSg55Q89Z1VMzz6x7JanFnCZiP4GCXTsmNg28+At5xY" +
            "+7+8PkVQglqDimWuQXbvbHYtBCvf8GmypY0eky0t8yl6ZdesVmIJDux4/LcvUgi+tIKtbrq2OZ+EfcO0ylsVY0vdlX04HChr0r+H" +
            "0jqMsYIx4SKf5JZSSeyH3uTZyo6kvHX7DbgS8KRLhQ03xC9dZHTKYo7cETnovxk+Jb4l6XIuna/wJ3uWJKLfpZ9URt+mWU2pVeVM" +
            "EHdQ3/QmQFSvWpdQUqHaN66d1idtkRL4uWiP3AwRh2YYPei+Ds4MI4uLAdp/i6v05gUYHH7STjydZxp1b/gM2/KSRaP6RzJ8Px0J" +
            "3x1OdlPj1VHSHXf5Gfyl0RT+bQP5XtEWFnNopi+BB2C/y9ydKPhYmXqDbCp42UjhgLinwWVgeoCq7+/AS/AzTUOQwoXH5vnB8eUK" +
            "mn4uB0hcuw7bsQEanDfp9b0FtPcqmh5SF1/kasojuQMohoyOW45GTK79JBD24nrwOzJ7sOOoTAFAZoA/bD5F9FeZ3FJA+blB160U" +
            "cRD7KbrBTeM2eqiUhnEYPA+QswEAy4ReWXWAAta7L8Zu3aHehTUViFaFFLyQHwLlf9YLXhnQd0mxR18xmADOAElGR1foZK49gq7p" +
            "+RlEnQSnEIniCtM0sHZczze0EpXalqjzoP19YDeJMjQ2adz99gnFmNLl5tGKbir/AJyfRz3YVGJeOJbcHChj7jzNaY56JK8WuzZA" +
            "CQiFfVhkFBtjZaIO7OeXJhZQ7yYv/v5w3MrFPV+WQ7sZiLn1FqoKPTSNZo9BjtmJq+Uddqg14fp0mMQSMnCiVTfjTYUunLSA+zXG" +
            "A/aJuIDeEb/fdRMFFNFvgoRks6qvi91g9YFdAqMhwaSM2WF3rhwMjgM7wPRPUmF3HmSLnpHG6nVhaYFWKnxLqr0j0rwcGKviGZLW" +
            "xUBP/IYeiQPtZD6jzrmIHc7aofy3H25+RbVvOy9pyivfATDvL/g4zpi2oIzkP/bPH0FPPOKvfvgKy+dSZtH2gS6umt6/0djdoroy" +
            "GMQLsfM9LNt/4V7i5UeQMqL1Q0z9Q+qvRZUEkDlTxaH4g4pnfXUuu6GOkFdOMJ5IB+FF0HbqV1dxIaHat13SYzGW+406uCAY9eue" +
            "CF1McFFi729XBt0hnOjM6euIK/+ngTrxxGwem3KHcFSWHiPmG+/I8NNdK3QZ1BjT4LoFDtcM7NSP+3CAV0LHc/IFSsUmJV0K9M+3" +
            "AXc2WIvCIXjlo2I+YFu2unEk3/cwCkL9NTlg5JIQHPt+9Qck7VhNBw6sSEAj1GB0Vdze+7NALhxBi9NXcWtZ4yMLhz0OzXY/OKjn" +
            "JAONg9zYC/gVmblnxFp+iUglvps0LRBQ6rQbkDW0LD6mt/U6cei9rN5zc1HN1eVxlEAYK0te+w322Vffzvj5+k1JoX5POYD63EUy" +
            "ERVo38CW6A1pgHsu1m0svc+GIN0glJrcmnDCd1auCDpP57E7IBdWNr28zVKB2Y+S4vVoIBaN3pTWWz6FZzUo2ULFr3Z1aVf6IvKh" +
            "RZMvmtzpD7OLAbMcjn/nrHkwAwbD1X1HQD7mzrIyVMgUhAAX4PbqB/WWMK/p1hHQWpwydzro08CUxSEwF8AjFwt8XoR0puMPYbX1" +
            "x/yXz6+3njpDDADoR2WzDgb8Z83ozNTWlCa1HpizeMjpdhPrFw1az6dZ7Dls0LiGb2Gn65iE8WquKgW7FPCgd2vYGLQVOv7G0zIo" +
            "XqPqWoqb0VV2Bx9A1mwfgIIaCnFN+TgoTnwEURttAQZyxRYe51EPnQobmHWXHqB+i+NFZ+QTXqbfZa0SjQ3D7fCM7pPZahTIvz1X" +
            "VyNFp58HzyhxiYwhDcKplbAivxM4zBq7Da9TYINu2pujXxjBOXDe+XxpDShSGGny97eECHCOc331YI+WaKnj+eoEAg7QWzjSiuKQ" +
            "5/3dh5G6GNHZBeL+hg+iM23v5k3mbM4hFTWLpj2fcuX9rJYqrpesDl68CgiiJtxp6x2GklNJb8lmRSgWaznpMNf1Qj7VWo4XVFvA" +
            "QMrkKu33kWgvXpkc/w5kwxrtLrJppOwk8dmiBb99cGA8ddp2kze+1SQJQRz/1yJYagoPS9qhvys42sK9SYopnPNq9nG/O3Q8dwqM" +
            "MLdcwIRdnm82pa0lk0hTD8Ht0JCeQi7wXOsAFjGxp+foSetlK3SJxQo8LplgHUYJNFnbtiZn9WYrJb0IuF+iS9iNN0/n0LGqjOdI" +
            "me32axkILcCSOIkyt9KO/kKFkjQkcozdRRu3k7MeQWhrm9Z6zWF+TaPWLqzITdwZEFtgHc/WBrQa4hNiNjO7EhciBXrJOlQDm63C" +
            "JVcm2OhiG2C9/Sjt7gbV8ral1hgRlqmOJt/bK96guheYdyLh2FFwMkOOHegRH28H1GDb7+RyAb9n/Sd28e+74PlbC1BLBwitQOSK" +
            "pQ8AAKAPAABQSwMEFAAICAgANyE5XQAAAAAAAAAAAAAAAAwAAABkYi92aW9saW4uZGLLSM3JyVcozy/KSQEAUEsHCIURSg0NAAAA" +
            "CwAAAFBLAwQUAAgICAA3ITldAAAAAAAAAAAAAAAADAAAAGNvbXBsZXRlLnR4dEvNKynKTC22NeICAFBLBwhtwb01DAAAAAoAAABQ" +
            "SwECFAAUAAgICAA3ITldBDyfsqAAAAC/AAAADAAAAAAAAAAAAAAAAAAAAAAAbWFuaWZlc3QudHh0UEsBAhQAFAAICAgANyE5Xa1A" +
            "5IqlDwAAoA8AAA4AAAAAAAAAAAAAAAAA2gAAAHNlc3Npb25zL2EubTRhUEsBAhQAFAAICAgANyE5XYURSg0NAAAACwAAAAwAAAAA" +
            "AAAAAAAAAAAAuxAAAGRiL3Zpb2xpbi5kYlBLAQIUABQACAgIADchOV1twb01DAAAAAoAAAAMAAAAAAAAAAAAAAAAAAIRAABjb21w" +
            "bGV0ZS50eHRQSwUGAAAAAAQABADqAAAASBEAAAAA"
    }
}
