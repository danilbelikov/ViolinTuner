package com.example.violintuner.core.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.Arrays
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupArchiveTest {
    @get:Rule val folder = TemporaryFolder()

    private val counts = BackupCounts(sessions = 23, takes = 6, pieces = 5, pages = 31, practiceDays = 41, trophies = 2, level = 4, withSound = 19, videos = 6)

    private fun manifest(parts: Set<BackupPart> = BackupPart.entries.toSet(), format: Int = 1, database: Int = 6) = BackupManifest(
        formatVersion = format, appVersion = "1.0", databaseVersion = database, createdAtEpochMs = 1_790_000_000_000, device = "Pixel 7 · тест",
        parts = parts, counts = counts, bytes = mapOf(BackupPart.DATA to 1_200L, BackupPart.SHEETS to 48_000L, BackupPart.AUDIO to 96_000L, BackupPart.VIDEO to 3_200_000L),
    )

    private fun bytes(size: Int, seed: Int) = ByteArray(size) { ((it * 31 + seed) % 251).toByte() }

    private fun entry(path: String, part: BackupPart, content: ByteArray?) = BackupEntry(path, part, content?.size?.toLong() ?: 1_000) { content?.let(::ByteArrayInputStream) }

    private val database = bytes(40_000, 1)
    private val sound = bytes(300_000, 2)
    private val video = bytes(900_000, 3)
    private val sheet = bytes(120_000, 4)

    private fun entries() = listOf(
        entry("db/violin.db", BackupPart.DATA, database),
        entry("repertoire/a.jpg", BackupPart.SHEETS, sheet),
        entry("sessions/one.m4a", BackupPart.AUDIO, sound),
        entry("sessions/two.mp4", BackupPart.VIDEO, video),
    )

    private fun archive(entries: List<BackupEntry> = entries(), manifest: BackupManifest = manifest()): ByteArray = runBlocking {
        ByteArrayOutputStream().also { BackupWriter.write(it, manifest, entries) {} }.toByteArray()
    }

    private fun problemOf(block: suspend () -> Unit): BackupFileProblem? = try {
        runBlocking { block() }
        null
    } catch (e: BackupFileException) {
        e.problem
    }

    @Test
    fun `the passport comes back as it went in`() {
        val read = BackupReader.manifest(ByteArrayInputStream(archive()), knownDatabase = 6)
        assertEquals(manifest(), read)
        assertEquals(1_200L + 48_000 + 96_000 + 3_200_000, read.totalBytes)
    }

    @Test
    fun `a copy without video weighs what is in it`() {
        val light = manifest(parts = setOf(BackupPart.DATA, BackupPart.SHEETS, BackupPart.AUDIO))
        assertEquals(1_200L + 48_000 + 96_000, BackupReader.manifest(ByteArrayInputStream(archive(manifest = light)), knownDatabase = 6).totalBytes)
    }

    @Test
    fun `what was written is unpacked where its path says, byte for byte`() = runTest {
        val target = folder.newFolder("staging")
        val seen = mutableListOf<BackupProgress>()
        BackupReader.extract(ByteArrayInputStream(archive()), target, totalBytes = 1_360_000) { seen += it }
        assertArrayEquals(database, File(target, "db/violin.db").readBytes())
        assertArrayEquals(sheet, File(target, "repertoire/a.jpg").readBytes())
        assertArrayEquals(sound, File(target, "sessions/one.m4a").readBytes())
        assertArrayEquals(video, File(target, "sessions/two.mp4").readBytes())
        assertFalse(File(target, BackupManifest.ENTRY).exists())
        assertTrue(seen.zipWithNext().all { (a, b) -> b.doneBytes >= a.doneBytes })
        assertEquals(1_360_000L, seen.last().doneBytes)
        assertEquals(listOf(BackupPart.DATA, BackupPart.SHEETS, BackupPart.AUDIO, BackupPart.VIDEO), seen.map { it.part }.distinct())
    }

    @Test
    fun `media are not squeezed again, the data are`() {
        val zeros = ByteArray(500_000)
        val asData = archive(listOf(entry("db/violin.db", BackupPart.DATA, zeros)))
        val asVideo = archive(listOf(entry("sessions/two.mp4", BackupPart.VIDEO, zeros)))
        assertTrue(asData.size < 10_000)
        assertTrue(asVideo.size > 500_000)
    }

    @Test
    fun `progress names the file of its part and ends at the whole`() = runTest {
        val seen = mutableListOf<BackupProgress>()
        val many = listOf(entry("sessions/a.mp4", BackupPart.VIDEO, video), entry("sessions/b.mp4", BackupPart.VIDEO, video))
        BackupWriter.write(ByteArrayOutputStream(), manifest(), many) { seen += it }
        assertEquals(listOf(1, 2), seen.map { it.index }.distinct())
        assertTrue(seen.all { it.count == 2 })
        assertEquals(1f, seen.last().fraction, 1e-6f)
    }

    @Test
    fun `a file deleted while the copy was being made is skipped, and the copy is whole`() = runTest {
        val withGone = entries() + entry("sessions/gone.m4a", BackupPart.AUDIO, null)
        val target = folder.newFolder("staging")
        BackupReader.extract(ByteArrayInputStream(archive(withGone)), target, 1_361_000) {}
        assertFalse(File(target, "sessions/gone.m4a").exists())
        assertTrue(File(target, "sessions/two.mp4").exists())
    }

    @Test
    fun `an archive cut short in its data is damaged`() {
        val whole = archive()
        listOf(whole.size / 3, whole.size - 400).forEach { cut ->
            val problem = problemOf { BackupReader.verify(ByteArrayInputStream(whole.copyOf(cut)), 1_360_000) {} }
            assertEquals("cut at $cut", BackupFileProblem.Damaged, problem)
        }
    }

    @Test
    fun `an archive that lost only the tail of its directory still holds everything, and is taken`() {
        // The reader goes through the entries as a stream and never looks at the directory at the end:
        // every entry and the completion mark are there, so nothing of the person's is missing.
        val whole = archive()
        assertEquals(null, problemOf { BackupReader.verify(ByteArrayInputStream(whole.copyOf(whole.size - 30)), 1_360_000) {} })
    }

    @Test
    fun `a flipped byte does not pass the check`() {
        val bad = archive()
        bad[bad.size / 2] = (bad[bad.size / 2] + 1).toByte()
        assertEquals(BackupFileProblem.Damaged, problemOf { BackupReader.verify(ByteArrayInputStream(bad), 1_360_000) {} })
    }

    @Test
    fun `a whole archive passes the check and leaves nothing behind`() = runTest {
        BackupReader.verify(ByteArrayInputStream(archive()), 1_360_000) {}
    }

    @Test
    fun `somebody else's archive, and what is no archive at all, are not ours`() {
        val foreign = ByteArrayOutputStream().also { out -> ZipOutputStream(out).use { it.putNextEntry(ZipEntry("photo.jpg")); it.write(bytes(100, 5)); it.closeEntry() } }.toByteArray()
        assertEquals(BackupFileProblem.NotOurs, problemOf { BackupReader.manifest(ByteArrayInputStream(foreign), knownDatabase = 6) })
        assertEquals(BackupFileProblem.NotOurs, problemOf { BackupReader.manifest(ByteArrayInputStream(bytes(5_000, 6)), knownDatabase = 6) })
        assertEquals(BackupFileProblem.NotOurs, problemOf { BackupReader.manifest(ByteArrayInputStream(ByteArray(0)), knownDatabase = 6) })
    }

    @Test
    fun `a copy from a newer app is refused, from an older one taken`() {
        assertEquals(BackupFileProblem.TooNew, problemOf { BackupReader.manifest(ByteArrayInputStream(archive(manifest = manifest(database = 7))), knownDatabase = 6) })
        assertEquals(BackupFileProblem.TooNew, problemOf { BackupReader.manifest(ByteArrayInputStream(archive(manifest = manifest(format = 2))), knownDatabase = 6) })
        assertEquals(3, BackupReader.manifest(ByteArrayInputStream(archive(manifest = manifest(database = 3))), knownDatabase = 6).databaseVersion)
    }

    @Test
    fun `an entry may not climb out of the folder it is unpacked into`() {
        val target = folder.newFolder("staging")
        val sly = archive(listOf(entry("sessions/../../outside.txt", BackupPart.AUDIO, bytes(10, 7))))
        assertEquals(BackupFileProblem.Damaged, problemOf { BackupReader.extract(ByteArrayInputStream(sly), target, 10) {} })
        assertFalse(File(folder.root, "outside.txt").exists())
    }

    @Test
    fun `the top folder of a path says which part it is`() {
        assertEquals(BackupPart.AUDIO, BackupReader.partOf("sessions/a.m4a"))
        assertEquals(BackupPart.VIDEO, BackupReader.partOf("sessions/a.mp4"))
        assertEquals(BackupPart.VIDEO, BackupReader.partOf("sessions/a-thumb.jpg"))
        assertEquals(BackupPart.SHEETS, BackupReader.partOf("repertoire/p.jpg"))
        assertEquals(BackupPart.DATA, BackupReader.partOf("db/violin.db"))
        assertEquals(BackupPart.DATA, BackupReader.partOf("profile/avatar-1.jpg"))
    }

    @Test
    fun `writing can be cancelled between buffers`() = runTest {
        var job: Job? = null
        var steps = 0
        val endless = BackupEntry("sessions/big.mp4", BackupPart.VIDEO, Long.MAX_VALUE / 4) {
            object : InputStream() {
                override fun read(): Int = 0
                override fun read(b: ByteArray, off: Int, len: Int): Int = len
            }
        }
        job = launch {
            BackupWriter.write(ByteArrayOutputStream(), manifest(), listOf(endless)) { if (++steps == 5) job!!.cancel() }
            fail("must not finish")
        }
        job.join()
        assertTrue(job.isCancelled)
    }

    @Test
    fun `sizes past four gigabytes go through`() = runTest {
        // ZIP64: a video a megabyte past four gigabytes, as a big file of a real copy is. Media go in unsqueezed, so the
        // archive grows past four gigabytes too, and where its last entry starts no longer fits in 32 bits either.
        // Unsqueezed is also the cheap way through the zip: deflating the same zeros costs about twice as much.
        val size = (4L shl 30) + (1 shl 20)
        val zeros = BackupEntry("sessions/huge.mp4", BackupPart.VIDEO, size) {
            object : InputStream() {
                var left = size
                override fun read(): Int = if (left-- > 0) 0 else -1
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    if (left <= 0) return -1
                    val count = minOf(len.toLong(), left).toInt()
                    Arrays.fill(b, off, off + count, 0)
                    left -= count
                    return count
                }
            }
        }
        var last = 0L
        val archive = MostlyZeros()
        BackupWriter.write(archive, manifest(), listOf(zeros)) { last = it.doneBytes }
        assertEquals(size, last)
        assertTrue(archive.size > size)
        var read = 0L
        BackupReader.verify(archive.input(), size) { read = it.doneBytes }
        assertEquals(size, read)
    }
}

/**
 * An archive of gigabytes kept in memory: its runs of zeros are counted, not stored, and the rest — headers, the
 * directory — lies in [kept] in its order. [runs] alternate: zeros, other bytes, zeros…
 */
private class MostlyZeros : OutputStream() {
    private val kept = ByteArrayOutputStream()
    private var runs = LongArray(1024)
    private var count = 1 // the first run counts zeros, and may count none

    var size = 0L
        private set

    override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

    override fun write(b: ByteArray, off: Int, len: Int) {
        size += len
        val end = off + len
        var i = off
        while (i < end) {
            val bytesFrom = firstNonZero(b, i, end)
            if (bytesFrom > i) add(zeros = true, bytesFrom - i)
            var zerosFrom = bytesFrom
            while (zerosFrom < end && b[zerosFrom] != ZERO) zerosFrom++
            if (zerosFrom > bytesFrom) {
                add(zeros = false, zerosFrom - bytesFrom)
                kept.write(b, bytesFrom, zerosFrom - bytesFrom)
            }
            i = zerosFrom
        }
    }

    private fun add(zeros: Boolean, n: Int) {
        val lastIsZeros = count % 2 == 1
        if (lastIsZeros != zeros) {
            if (count == runs.size) runs = runs.copyOf(count * 2)
            count++
        }
        runs[count - 1] += n.toLong()
    }

    fun input(): InputStream = object : InputStream() {
        private val bytes = kept.toByteArray()
        private var run = 0
        private var left = runs[0]
        private var at = 0

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            while (left == 0L) {
                if (++run == count) return -1
                left = runs[run]
            }
            val n = minOf(len.toLong(), left).toInt()
            if (run % 2 == 0) {
                Arrays.fill(b, off, off + n, ZERO)
            } else {
                System.arraycopy(bytes, at, b, off, n)
                at += n
            }
            left -= n
            return n
        }
    }

    private companion object {
        const val ZERO: Byte = 0
        val ZEROS = ByteArray(64 * 1024)

        /** The first byte from [from] that is not zero, or [to]; compared a block at a time, which is fast. */
        fun firstNonZero(b: ByteArray, from: Int, to: Int): Int {
            var i = from
            while (i < to) {
                val n = minOf(ZEROS.size, to - i)
                val at = Arrays.mismatch(b, i, i + n, ZEROS, 0, n)
                if (at >= 0) return i + at
                i += n
            }
            return to
        }
    }
}
