package com.violinjourney.app.core.backup

import com.violinjourney.app.core.analytics.ErrorGroup
import com.violinjourney.app.core.analytics.FakeAnalytics
import com.violinjourney.app.core.analytics.NoOpAnalytics
import com.violinjourney.app.core.io.StorageException
import com.violinjourney.app.core.io.StorageFailure
import com.violinjourney.app.core.time.FixedWallClock
import com.violinjourney.app.core.time.WallClock
import com.violinjourney.app.core.time.ZonedSystemWallClock
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class BackupManagerTest {
    @get:Rule val folder = TemporaryFolder()
    private val now = Instant.parse("2026-09-20T11:32:00Z")
    private val counts = BackupCounts(sessions = 23, pieces = 5, practiceDays = 41, level = 4, withSound = 19, videos = 6)

    private val store by lazy { FakeBackupStore(folder.root, now, counts) }
    private val documents = FakeBackupDocuments()
    private val prefs = FakeBackupPrefs()
    private var keptAlive = 0
    private val all = BackupPart.entries.toSet()
    private val analytics = FakeAnalytics()

    private fun TestScope.manager(documents: BackupDocuments = this@BackupManagerTest.documents, config: BackupConfig = BackupConfig()) = BackupManager(
        store, documents, prefs, { keptAlive++ }, config,
        FixedWallClock(now, TimeZone.of("Europe/Moscow")), { testScheduler.currentTime }, StandardTestDispatcher(testScheduler), analytics,
    )

    @Test
    fun `a copy goes straight to the place picked, is read back, and the date is remembered`() = runTest {
        val manager = manager()
        manager.saveTo("content://downloads/1", all, "копия.zip")
        assertTrue(manager.running)
        advanceUntilIdle()
        val saved = manager.job.value as BackupJob.Saved
        assertEquals("Загрузки", saved.place)
        assertEquals(documents.written.getValue("content://downloads/1").size().toLong(), saved.bytes)
        assertEquals(counts, saved.manifest.counts)
        assertNull(saved.shareFile)
        assertEquals(now.toEpochMilliseconds(), prefs.lastBackupAtEpochMs.value)
        assertEquals(1 to 1, keptAlive to store.cleaned)
        assertTrue(documents.deleted.isEmpty())
        // and it really is a copy
        val read = BackupReader.manifest(documents.openInput("content://downloads/1")!!, knownDatabase = 6)
        assertEquals(all, read.parts)

        manager.dismiss()
        assertEquals(BackupJob.Idle, manager.job.value)
    }

    @Test
    fun `a copy without video does not carry it`() = runTest {
        val manager = manager()
        manager.saveTo("content://downloads/1", setOf(BackupPart.DATA), "копия.zip")
        advanceUntilIdle()
        assertTrue(documents.written.getValue("content://downloads/1").size() < 5_000)
        assertEquals(setOf(BackupPart.DATA), (manager.job.value as BackupJob.Saved).manifest.parts)
    }

    @Test
    fun `nothing blinks - a small copy shows no progress screen`() = runTest {
        val manager = manager()
        manager.saveTo("content://downloads/1", all, "копия.zip")
        runCurrent()
        assertTrue(manager.job.value.let { it is BackupJob.Saved || (it is BackupJob.Saving && !it.visible) })
    }

    @Test
    fun `a full card is told apart from a card that went away, and the unfinished file goes either way`() = runTest {
        val cases = listOf(
            IOException("write failed: ENOSPC (No space left on device)") to SaveFailure.NO_SPACE,
            IOException("write failed: EIO (I/O error)") to SaveFailure.UNAVAILABLE,
            IOException("something else") to SaveFailure.FAILED,
        )
        cases.forEachIndexed { index, (failure, reason) ->
            val manager = manager()
            documents.failWith = failure
            val uri = "content://usb/$index"
            manager.saveTo(uri, all, "копия.zip")
            advanceUntilIdle()
            assertEquals(reason, (manager.job.value as BackupJob.SaveFailed).reason)
            assertEquals(listOf(uri), documents.deleted.filter { it == uri })
            assertNull(prefs.lastBackupAtEpochMs.value)
        }
    }

    @Test
    fun `a place that goes away on iOS is told by its reason and not by its words`() = runTest {
        // what the streams of iOS throw: Darwin's words for an errno, which are not libcore's — the reason is a value
        val cases = listOf(
            StorageException(StorageFailure.GONE, "write failed: Input/output error") to SaveFailure.UNAVAILABLE,
            StorageException(StorageFailure.GONE, "write failed: Device not configured") to SaveFailure.UNAVAILABLE,
            StorageException(StorageFailure.NO_SPACE, "write failed: Disc quota exceeded") to SaveFailure.NO_SPACE,
            StorageException(StorageFailure.OTHER, "write failed: Permission denied") to SaveFailure.FAILED,
        )
        cases.forEachIndexed { index, (failure, reason) ->
            val manager = manager()
            documents.failWith = failure
            val uri = "file:///private/var/mobile/usb/$index"
            manager.saveTo(uri, all, "копия.zip")
            advanceUntilIdle()
            assertEquals(failure.message, reason, (manager.job.value as BackupJob.SaveFailed).reason)
            assertEquals(listOf(uri), documents.deleted.filter { it == uri })
        }
    }

    @Test
    fun `a snapshot the database refuses is said in words - the phone's own full memory as such, the rest as a failure`() = runTest {
        // what androidx.sqlite and android.database throw: RuntimeExceptions, not IOExceptions. The snapshot is taken in the
        // phone's own memory: no other card helps there, and no place that «went away» — it is not a stream into a place.
        val cases = listOf(
            RuntimeException("Error code: 13, message: database or disk is full") to SaveFailure.PHONE_FULL,
            IllegalStateException("database or disk is full (code 13 SQLITE_FULL)") to SaveFailure.PHONE_FULL,
            RuntimeException("Error code: 10, message: disk I/O error") to SaveFailure.FAILED,
            RuntimeException("database is locked") to SaveFailure.FAILED,
            IOException("copy failed: ENOSPC (No space left on device)") to SaveFailure.PHONE_FULL,
            // what the snapshot on a full emulator threw: the -shm of the copy could not grow
            RuntimeException("disk I/O error (code 4874 SQLITE_IOERR_SHMSIZE): , while compiling: PRAGMA journal_mode") to SaveFailure.PHONE_FULL,
            IOException("write failed: EIO (I/O error)") to SaveFailure.FAILED,
        )
        val manager = manager()
        cases.forEachIndexed { index, (failure, reason) ->
            store.prepareFails = failure
            manager.saveTo("content://downloads/$index", all, "копия.zip")
            advanceUntilIdle()
            assertEquals(failure.message, reason, (manager.job.value as BackupJob.SaveFailed).reason)
            assertEquals(index + 1, store.cleaned)
            manager.dismiss()
        }
        assertEquals(List(cases.size) { ErrorGroup.BACKUP }, analytics.errors.map { it.first })
        assertNull(prefs.lastBackupAtEpochMs.value)

        store.prepareFails = null
        manager.saveTo("content://downloads/next", all, "копия.zip")
        advanceUntilIdle()
        assertTrue("the next copy goes as if nothing had happened", manager.job.value is BackupJob.Saved)
    }

    @Test
    fun `a copy written whole stays when its date cannot be kept`() = runTest {
        val manager = manager()
        prefs.fails = IOException("write failed: ENOSPC (No space left on device)")
        manager.saveTo("content://downloads/1", all, "копия.zip")
        advanceUntilIdle()
        assertTrue(manager.job.value is BackupJob.Saved)
        assertTrue("the copy was checked whole: it is not taken away", documents.deleted.isEmpty())
        assertEquals(all, BackupReader.manifest(documents.openInput("content://downloads/1")!!, knownDatabase = 6).parts)
        assertEquals(listOf(ErrorGroup.BACKUP), analytics.errors.map { it.first })
    }

    @Test
    fun `a copy stopped while its write breaks stays stopped`() = runTest {
        lateinit var manager: BackupManager
        val breaking = object : BackupDocuments by documents {
            override fun openOutput(uri: String): OutputStream {
                documents.written[uri] = ByteArrayOutputStream()
                return object : OutputStream() {
                    override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
                    override fun write(b: ByteArray, off: Int, len: Int) {
                        // «Отмена» is pressed, and the write under way breaks as the place goes
                        manager.cancel()
                        throw IOException("write failed: EPIPE (Broken pipe)")
                    }
                }
            }
        }
        manager = manager(breaking)
        manager.saveTo("content://usb/1", all, "копия.zip")
        advanceUntilIdle()
        assertEquals(BackupJob.Idle, manager.job.value)
        assertEquals(listOf("content://usb/1"), documents.deleted)
        assertTrue("a stop is no failure to tell about", analytics.errors.isEmpty())
    }

    @Test
    fun `a place that cannot be opened is a place that went away`() = runTest {
        val manager = manager()
        val closed = object : BackupDocuments by documents {
            override fun openOutput(uri: String): OutputStream? = null
        }
        val other = BackupManager(
            store, closed, prefs, {}, BackupConfig(), ZonedSystemWallClock(TimeZone.UTC), { testScheduler.currentTime }, StandardTestDispatcher(testScheduler),
            analytics = NoOpAnalytics(),
        )
        other.saveTo("content://gone/1", all, "копия.zip")
        advanceUntilIdle()
        assertEquals(SaveFailure.UNAVAILABLE, (other.job.value as BackupJob.SaveFailed).reason)
        assertEquals(BackupJob.Idle, manager.job.value)
    }

    @Test
    fun `a provider that will not hand back what it took does not fail a copy written whole`() = runTest {
        val manager = manager()
        documents.unreadable = true
        manager.saveTo("content://cloud/1", all, "копия.zip")
        advanceUntilIdle()
        assertTrue(manager.job.value is BackupJob.Saved)
    }

    @Test
    fun `sharing builds the archive inside the app, and only when it fits`() = runTest {
        val manager = manager()
        manager.share(setOf(BackupPart.DATA), "копия.zip")
        advanceUntilIdle()
        val file = (manager.job.value as BackupJob.Saved).shareFile!!
        assertTrue(file.length() > 0)
        manager.dismiss()

        store.free = 10_000_000
        manager.share(all, "копия.zip")
        advanceUntilIdle()
        val failed = manager.job.value as BackupJob.SaveFailed
        assertEquals(SaveFailure.NO_SPACE, failed.reason)
        assertEquals(401_000L + 50L * 1024 * 1024 - 10_000_000, failed.missingBytes)
    }

    /** Documents that write down the job as it stands when the copy opens its stream — its files listed, its writing not begun. */
    private class WatchedDocuments(private val inner: FakeBackupDocuments) : BackupDocuments by inner {
        lateinit var manager: BackupManager
        val seen = mutableListOf<BackupJob>()

        override fun openOutput(uri: String): OutputStream? {
            seen += manager.job.value
            return inner.openOutput(uri)
        }
    }

    @Test
    fun `the job knows the parts of its copy and those with files`() = runTest {
        // spec 3.36.8: a screen opened anew on a copy shows its choice from the job, and its line of phases names the parts with files
        val watched = WatchedDocuments(documents)
        val manager = manager(watched).also { watched.manager = it }
        manager.saveTo("content://downloads/1", setOf(BackupPart.DATA, BackupPart.AUDIO), "копия.zip")
        assertEquals(setOf(BackupPart.DATA, BackupPart.AUDIO), (manager.job.value as BackupJob.Saving).parts)
        assertTrue("nothing is listed yet", (manager.job.value as BackupJob.Saving).filled.isEmpty())
        advanceUntilIdle()
        // the fake app has files of the data and of the video only: the sound switched on has none
        val listed = watched.seen.single() as BackupJob.Saving
        assertEquals(setOf(BackupPart.DATA, BackupPart.AUDIO), listed.parts)
        assertEquals(setOf(BackupPart.DATA), listed.filled)
        manager.dismiss()

        // the data always go, even when the choice leaves them out
        manager.saveTo("content://downloads/2", setOf(BackupPart.VIDEO), "копия.zip")
        assertEquals(setOf(BackupPart.DATA, BackupPart.VIDEO), (manager.job.value as BackupJob.Saving).parts)
        advanceUntilIdle()
        assertEquals(setOf(BackupPart.DATA, BackupPart.VIDEO), (watched.seen.last() as BackupJob.Saving).filled)
    }

    @Test
    fun `a failed copy keeps the parts it was made of`() = runTest {
        // a full card (as above): «Ещё раз» on a screen opened anew takes the same copy, and «Без видео…» knows the video was in it.
        // Not all of them: the parts of every copy were all of them once, so a failure that forgot its own would look the same
        // (review of stage 122); the video is in it, for the fake card fills up past its first 100 000 bytes.
        val manager = manager()
        documents.failWith = IOException("write failed: ENOSPC (No space left on device)")
        val withVideo = setOf(BackupPart.DATA, BackupPart.VIDEO)
        manager.saveTo("content://usb/1", withVideo, "копия.zip")
        advanceUntilIdle()
        assertEquals(BackupJob.SaveFailed(SaveFailure.NO_SPACE, parts = withVideo), manager.job.value)
        manager.dismiss()

        // «Отправить…» without the room in the phone for its archive
        documents.failWith = null
        store.free = 10_000_000
        val sent = setOf(BackupPart.DATA, BackupPart.VIDEO)
        manager.share(sent, "копия.zip")
        advanceUntilIdle()
        val failed = manager.job.value as BackupJob.SaveFailed
        assertEquals(SaveFailure.NO_SPACE, failed.reason)
        assertTrue(failed.missingBytes > 0)
        assertEquals(sent, failed.parts)
        manager.dismiss()

        // a snapshot the phone has no room for, and a place that cannot be opened: the parts go with every failure
        store.free = Long.MAX_VALUE
        store.prepareFails = RuntimeException("database or disk is full (code 13 SQLITE_FULL)")
        manager.saveTo("content://downloads/3", setOf(BackupPart.SHEETS), "копия.zip")
        advanceUntilIdle()
        assertEquals(BackupJob.SaveFailed(SaveFailure.PHONE_FULL, parts = setOf(BackupPart.DATA, BackupPart.SHEETS)), manager.job.value)
        store.prepareFails = null
        val closed = object : BackupDocuments by documents {
            override fun openOutput(uri: String): OutputStream? = null
        }
        val gone = manager(closed)
        gone.saveTo("content://gone/1", setOf(BackupPart.AUDIO), "копия.zip")
        advanceUntilIdle()
        assertEquals(BackupJob.SaveFailed(SaveFailure.UNAVAILABLE, parts = setOf(BackupPart.DATA, BackupPart.AUDIO)), gone.job.value)
    }

    /** Documents that hand back only the first half of what was written: a provider that cut the file short behind the copy's back. */
    private class HalfReadDocuments(private val inner: FakeBackupDocuments) : BackupDocuments by inner {
        override fun openInput(uri: String): InputStream? = inner.written[uri]?.toByteArray()?.let { ByteArrayInputStream(it, 0, it.size / 2) }
    }

    @Test
    fun `a copy found damaged on reading back keeps the parts it was made of`() = runTest {
        // the archive went out whole, and «Проверяем файл» reads back half of it: the copy failed, and «Ещё раз» takes the same one —
        // the sheets without the video, not everything (spec 3.36.8; the one failure the tests above never reach, review of stage 122)
        val manager = manager(HalfReadDocuments(documents))
        val chosen = setOf(BackupPart.DATA, BackupPart.SHEETS)
        manager.saveTo("content://cloud/1", chosen, "копия.zip")
        advanceUntilIdle()
        assertEquals(BackupJob.SaveFailed(SaveFailure.FAILED, parts = chosen), manager.job.value)
        assertEquals("the damaged file goes", listOf("content://cloud/1"), documents.deleted)
        assertNull("a copy that failed is not the last copy", prefs.lastBackupAtEpochMs.value)
    }

    @Test
    fun `one job at a time`() = runTest {
        val manager = manager()
        manager.saveTo("content://downloads/1", all, "копия.zip")
        manager.saveTo("content://downloads/2", all, "копия.zip")
        advanceUntilIdle()
        assertEquals(setOf("content://downloads/1"), documents.written.keys)
    }

    private suspend fun TestScope.savedCopy(manager: BackupManager, uri: String = "content://downloads/1"): BackupCandidate.Copy {
        manager.saveTo(uri, all, "копия.zip")
        advanceUntilIdle()
        manager.dismiss()
        return manager.inspect(uri) as BackupCandidate.Copy
    }

    @Test
    fun `a picked file says what it is before anything is unpacked`() = runTest {
        val manager = manager()
        val copy = savedCopy(manager)
        assertEquals(counts, copy.manifest.counts)
        assertEquals(0L, copy.missingBytes)

        store.free = 100_000
        assertEquals(401_000L + 50L * 1024 * 1024 - 100_000, (manager.inspect("content://downloads/1") as BackupCandidate.Copy).missingBytes)

        documents.written["content://downloads/photo"] = ByteArrayOutputStream().also { it.write(ByteArray(3_000) { 1 }) }
        assertEquals(BackupCandidate.Unfit(BackupFileProblem.NotOurs), manager.inspect("content://downloads/photo"))
        assertEquals(BackupCandidate.Unfit(BackupFileProblem.Damaged), manager.inspect("content://downloads/nothing"))
    }

    @Test
    fun `a picked archive with names not in UTF-8 is not ours - and the app stays`() = runTest {
        val manager = manager()
        documents.written["content://downloads/notes"] = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out, StandardCharsets.ISO_8859_1).use { it.putNextEntry(ZipEntry("Noten/Étude.pdf")); it.write(ByteArray(100) { 3 }); it.closeEntry() }
        }
        assertEquals(BackupCandidate.Unfit(BackupFileProblem.NotOurs), manager.inspect("content://downloads/notes"))
    }

    @Test
    fun `a picked file the platform cannot weigh against the phone is told as damaged - and the app stays`() = runTest {
        val manager = manager()
        val copy = savedCopy(manager)
        // StatFs throws IllegalArgumentException, a database that will not open throws SQLiteException
        store.freeFails = IllegalArgumentException("Invalid path: /data/user/0")
        assertEquals(BackupCandidate.Unfit(BackupFileProblem.Damaged), manager.inspect(copy.uri))
        assertEquals(listOf(ErrorGroup.BACKUP), analytics.errors.map { it.first })
    }

    @Test
    fun `a restore that breaks on something other than the file changes nothing`() = runTest {
        val manager = manager()
        val copy = savedCopy(manager)
        store.stagingFails = IllegalStateException("cannot make restore-staging")
        manager.restore(copy, unsafe = false)
        advanceUntilIdle()
        val failed = manager.job.value as BackupJob.RestoreFailed
        assertTrue(failed.dataIntact)
        assertEquals(1, store.stagingDiscarded)
        assertFalse(store.readyMarked)
        assertTrue("backup_restored {ok=false}" in analytics.sent())
        assertEquals(listOf(ErrorGroup.BACKUP), analytics.errors.map { it.first })
    }

    @Test
    fun `the safe way unpacks beside the data and leaves the swap to the next start`() = runTest {
        val manager = manager()
        val copy = savedCopy(manager)
        manager.restore(copy, unsafe = false)
        assertEquals(RestorePhase.EXTRACTING, (manager.job.value as BackupJob.Restoring).phase)
        assertFalse((manager.job.value as BackupJob.Restoring).checked)
        advanceUntilIdle()
        assertTrue(manager.job.value is BackupJob.Restored)
        assertTrue(store.readyMarked)
        assertFalse(store.mediaDeleted)
        assertEquals(store.video.toList(), File(folder.root, "staging/sessions/a.mp4").readBytes().toList())
    }

    @Test
    fun `a damaged copy on the safe way changes nothing`() = runTest {
        val manager = manager()
        val copy = savedCopy(manager)
        val bytes = documents.written.getValue(copy.uri).toByteArray()
        documents.written[copy.uri] = ByteArrayOutputStream().also { it.write(bytes, 0, bytes.size / 2) }
        manager.restore(copy, unsafe = false)
        advanceUntilIdle()
        val failed = manager.job.value as BackupJob.RestoreFailed
        assertTrue(failed.dataIntact)
        assertFalse(store.readyMarked)
        assertFalse(File(folder.root, "staging").exists())
    }

    @Test
    fun `the way without a net checks the whole copy before it deletes anything`() = runTest {
        val manager = manager()
        val copy = savedCopy(manager)
        val bytes = documents.written.getValue(copy.uri).toByteArray()
        documents.written[copy.uri] = ByteArrayOutputStream().also { it.write(bytes, 0, bytes.size / 2) }
        manager.restore(copy, unsafe = true)
        advanceUntilIdle()
        assertTrue("the check failed, so the data are still there", (manager.job.value as BackupJob.RestoreFailed).dataIntact)
        assertFalse(store.mediaDeleted)
    }

    @Test
    fun `the way without a net makes room first, and cannot be stopped once it has`() = runTest {
        val manager = manager()
        val copy = savedCopy(manager)
        manager.restore(copy, unsafe = true)
        assertTrue((manager.job.value as BackupJob.Restoring).checked)
        assertTrue("while it is only checking, it can be stopped", manager.cancellable)
        advanceUntilIdle()
        assertTrue(store.mediaDeleted && store.readyMarked)
        assertTrue(manager.job.value is BackupJob.Restored)
        assertFalse(manager.cancellable)
    }

    @Test
    fun `cancelling a copy removes its file, cancelling a safe restore removes what was unpacked`() = runTest {
        val manager = manager()
        val copy = savedCopy(manager)

        manager.saveTo("content://downloads/2", all, "копия.zip")
        manager.cancel()
        advanceUntilIdle()
        assertEquals(BackupJob.Idle, manager.job.value)
        assertTrue("content://downloads/2" in documents.deleted || "content://downloads/2" !in documents.written)

        manager.restore(copy, unsafe = false)
        manager.cancel()
        advanceUntilIdle()
        assertEquals(BackupJob.Idle, manager.job.value)
        assertFalse(store.readyMarked)
    }

    @Test
    fun `starting clean only leaves the mark - the next start does the wiping`() = runTest {
        assertTrue(manager().startClean())
        assertTrue(store.wipeMarked)
    }

    @Test
    fun `a mark to start clean that cannot be left is no restart, and no fall`() = runTest {
        store.wipeFails = IOException("open failed: EACCES (Permission denied)")
        assertFalse(manager().startClean())
        assertFalse(store.wipeMarked)
        assertEquals(listOf(ErrorGroup.BACKUP), analytics.errors.map { it.first })
    }

    /**
     * A copy of [reads] reads of 4 KiB, each taking [msPerRead] on the manager's clock — the clock is moved by the reading
     * itself — and every state of progress it published, with the moment it came. Saved in the end.
     */
    private fun TestScope.timedCopy(reads: Int, msPerRead: Long): List<Pair<Long, BackupJob.Saving>> {
        var nowMs = 0L
        val seen = mutableListOf<Pair<Long, BackupJob.Saving>>()
        lateinit var manager: BackupManager
        // the state a read finds is the one the read before it published, at the moment the clock still shows
        fun look() {
            val state = manager.job.value as? BackupJob.Saving ?: return
            if (state.progress != null && state.progress != seen.lastOrNull()?.second?.progress) seen += nowMs to state
        }
        val video = object : InputStream() {
            private var left = reads
            override fun read(): Int = error("read in blocks")
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                look()
                if (left == 0) return -1
                left--
                nowMs += msPerRead
                val count = minOf(len, BLOCK)
                b.fill(0, off, off + count)
                return count
            }
        }
        val slow = object : BackupStore by store {
            override suspend fun prepare(parts: Set<BackupPart>) = PreparedBackup(
                store.manifest(parts),
                listOf(
                    BackupEntry(BackupPaths.DATABASE_ENTRY, BackupPart.DATA, 1_000, required = true) { ByteArrayInputStream(ByteArray(1_000) { 7 }) },
                    BackupEntry("sessions/big.mp4", BackupPart.VIDEO, reads.toLong() * BLOCK) { video },
                ),
            )
        }
        manager = BackupManager(
            slow, documents, prefs, {}, BackupConfig(), FixedWallClock(now, TimeZone.UTC), { nowMs }, StandardTestDispatcher(testScheduler),
            analytics = NoOpAnalytics(),
        )
        manager.saveTo("content://slow/1", all, "копия.zip")
        advanceUntilIdle()
        assertTrue("${manager.job.value}", manager.job.value is BackupJob.Saved)
        return seen
    }

    @Test
    fun `the time left waits for five percent and then counts down`() = runTest {
        // forty seconds of writing, evenly
        val seen = timedCopy(reads = 2_000, msPerRead = 20)
        val (early, late) = seen.partition { it.second.progress!!.fraction < 0.05f }
        assertTrue("the start is published too", early.isNotEmpty())
        assertTrue("no word of the time left before 5 % and 10 s", early.all { it.second.remainingSec == null })
        // at 5 % two seconds have gone of forty: about 38 are left
        assertTrue("${late.first()}", late.first().second.remainingSec in 37..39)
        val left = late.map { it.second.remainingSec!! }
        assertTrue("the time left only goes down: $left", left.zipWithNext().all { (a, b) -> b <= a })
        assertTrue("and never below a second: $left", left.all { it >= 1 })
        // ten a second at most, but the end is always told
        val moments = seen.map { it.first }
        assertTrue("$moments", moments.dropLast(1).zipWithNext().all { (a, b) -> b - a >= 100 })
        assertEquals(1f, seen.last().second.progress!!.fraction, 0f)
    }

    @Test
    fun `a slow start says the time left after ten seconds even below five percent`() = runTest {
        // five minutes of writing: 5 % would be a quarter of a minute away
        val seen = timedCopy(reads = 1_000, msPerRead = 300)
        assertTrue(seen.filter { it.first < 10_000 }.all { it.second.remainingSec == null })
        val first = seen.first { it.first >= 10_000 }
        assertTrue("$first", first.second.progress!!.fraction < 0.05f)
        // about 290 s are left of 300 after 10 s: the guess counts what was written so far, the database too
        assertTrue("$first", first.second.remainingSec in 280..292)
    }

    // — «Остановить» and the point of no return (spec 3.20) —

    @Test
    fun `what can still be stopped is one rule`() {
        assertTrue(BackupJob.Saving("a", visible = true, parts = all).stoppable)
        assertFalse(BackupJob.Saving("a", visible = true, verifying = true, parts = all).stoppable)
        assertTrue(BackupJob.Restoring(RestorePhase.VERIFYING, checked = true).stoppable)
        assertFalse(BackupJob.Restoring(RestorePhase.EXTRACTING, checked = true).stoppable)
        assertTrue(BackupJob.Restoring(RestorePhase.EXTRACTING, checked = false).stoppable)
        assertFalse(BackupJob.Restoring(RestorePhase.FINISHING, checked = false).stoppable)
        assertFalse(BackupJob.Restoring(RestorePhase.FINISHING, checked = true).stoppable)
        val manifest = store.manifest(all)
        listOf(
            BackupJob.Idle, BackupJob.Saved("a", 1, null, manifest), BackupJob.SaveFailed(SaveFailure.FAILED, parts = all),
            BackupJob.Restored(manifest), BackupJob.RestoreFailed(true, "u", manifest, checked = false),
        ).forEach { assertFalse("$it", it.stoppable) }
    }

    @Test
    fun `a stop pressed while the media go is refused and the restore goes on`() = runTest {
        val manager = manager()
        val copy = savedCopy(manager)
        var stopWasOffered = true
        store.onDeleteMedia = {
            stopWasOffered = manager.cancellable
            manager.cancel()
        }
        manager.restore(copy, unsafe = true)
        advanceUntilIdle()
        assertFalse("once the media go, «Остановить» is gone", stopWasOffered)
        assertTrue(manager.job.value is BackupJob.Restored)
        assertTrue(store.mediaDeleted && store.readyMarked)
        assertTrue(analytics.errors.isEmpty())
    }

    @Test
    fun `a stop pressed during the check keeps the media`() = runTest {
        val manager = manager()
        val copy = savedCopy(manager)
        store.onSettle = { manager.cancel() }
        manager.restore(copy, unsafe = true)
        advanceUntilIdle()
        assertEquals(BackupJob.Idle, manager.job.value)
        assertFalse(store.mediaDeleted)
        assertFalse(store.readyMarked)
        assertEquals(1, store.stagingDiscarded)
    }

    @Test
    fun `a new job waits until the stopped one has tidied up after itself`() = runTest {
        val manager = manager()
        val gate = CompletableDeferred<Unit>()
        store.prepareGate = gate
        manager.saveTo("content://downloads/1", all, "копия.zip")
        runCurrent()
        manager.cancel()
        assertEquals(BackupJob.Idle, manager.job.value)
        manager.share(all, "копия.zip")
        runCurrent()
        assertEquals("the new job has not taken its snapshot yet", 1, store.prepareCalls)
        // the stopped job leaves its blocking step, and only then removes its snapshot — which is this job's snapshot too
        gate.complete(Unit)
        advanceUntilIdle()
        val saved = manager.job.value as BackupJob.Saved
        assertEquals(2, store.cleaned)
        assertTrue(BackupReader.verify(saved.shareFile!!.inputStream(), 401_000) {}.hasDatabase)
        assertEquals(listOf("content://downloads/1"), documents.deleted)
    }

    @Test
    fun `a job stopped while it waits still waits - no two jobs ever work in the same folders`() = runTest {
        val manager = manager()
        val gate = CompletableDeferred<Unit>()
        store.prepareGate = gate
        // a copy stuck in a blocking step, stopped
        manager.saveTo("content://downloads/1", all, "копия.zip")
        runCurrent()
        manager.cancel()
        // the next waits for it, shows its screen and its «Остановить» as a copy that drags on does, and is stopped too
        manager.saveTo("content://downloads/2", all, "копия.zip")
        runCurrent()
        assertFalse((manager.job.value as BackupJob.Saving).visible)
        advanceTimeBy(701)
        runCurrent()
        assertTrue((manager.job.value as BackupJob.Saving).visible)
        assertTrue(manager.cancellable)
        manager.cancel()
        assertEquals(BackupJob.Idle, manager.job.value)
        // the third waits for the first as well: the second has not ended, it is still waiting
        manager.share(all, "копия.zip")
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals("nothing took a snapshot beside the stuck one", 1, store.prepareCalls)
        gate.complete(Unit)
        advanceUntilIdle()
        val saved = manager.job.value as BackupJob.Saved
        assertEquals("the stopped waiter took no snapshot of its own", 2, store.prepareCalls)
        assertTrue(BackupReader.verify(saved.shareFile!!.inputStream(), 401_000) {}.hasDatabase)
        // the file the picker made for the waiter goes too, and after the file of the job it waited for
        assertEquals(listOf("content://downloads/1", "content://downloads/2"), documents.deleted)
    }

    @Test
    fun `a restore stopped while it waits leaves the unpacking folder of the next alone`() = runTest {
        val manager = manager()
        val copy = savedCopy(manager)
        val gate = CompletableDeferred<Unit>()
        store.settleGate = gate
        // a restore stuck in a blocking step, stopped: when it leaves the step, it removes the unpacking folder
        manager.restore(copy, unsafe = false)
        runCurrent()
        manager.cancel()
        // the next waits for it and is stopped while it waits; the one after waits for both
        manager.restore(copy, unsafe = false)
        runCurrent()
        manager.cancel()
        manager.restore(copy, unsafe = false)
        runCurrent()
        assertEquals("nothing unpacked beside the stuck restore", listOf("settle"), store.calls.filter { it == "settle" })
        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(manager.job.value is BackupJob.Restored)
        assertTrue(store.readyMarked)
        assertEquals(1_000, File(folder.root, "staging/db/violin.db").length())
        assertEquals(store.video.toList(), File(folder.root, "staging/sessions/a.mp4").readBytes().toList())
    }

    // — outcomes that have been read, and «Ещё раз» —

    @Test
    fun `a job starts over an outcome that has been read`() = runTest {
        val manager = manager()
        manager.saveTo("content://downloads/1", all, "копия.zip")
        advanceUntilIdle()
        assertTrue(manager.job.value is BackupJob.Saved)
        val copy = manager.inspect("content://downloads/1") as BackupCandidate.Copy
        // «Сначала сохранить текущие данные», the system «Назад» that did not say «Готово», and «Восстановить»
        manager.restore(copy, unsafe = false)
        assertTrue(manager.job.value is BackupJob.Restoring)
        advanceUntilIdle()
        assertTrue(manager.job.value is BackupJob.Restored)
    }

    @Test
    fun `a failed copy or restore does not stand in the way of the next`() = runTest {
        val manager = manager()
        val copy = savedCopy(manager)
        store.prepareFails = IllegalStateException("database is locked")
        manager.saveTo("content://downloads/2", all, "копия.zip")
        advanceUntilIdle()
        assertTrue(manager.job.value is BackupJob.SaveFailed)
        store.prepareFails = null
        manager.saveTo("content://downloads/3", all, "копия.zip")
        assertTrue(manager.job.value is BackupJob.Saving)
        advanceUntilIdle()
        manager.dismiss()

        store.stagingFails = IllegalStateException("cannot make restore-staging")
        manager.restore(copy, unsafe = false)
        advanceUntilIdle()
        assertTrue(manager.job.value is BackupJob.RestoreFailed)
        store.stagingFails = null
        manager.restore(copy, unsafe = false)
        assertTrue(manager.job.value is BackupJob.Restoring)
    }

    @Test
    fun `a restore waiting for its restart takes nothing else`() = runTest {
        val manager = manager()
        val copy = savedCopy(manager)
        manager.restore(copy, unsafe = false)
        advanceUntilIdle()
        val restored = manager.job.value as BackupJob.Restored
        manager.saveTo("content://downloads/2", all, "копия.zip")
        manager.restore(copy, unsafe = false)
        manager.dismiss()
        advanceUntilIdle()
        assertEquals(restored, manager.job.value)
        assertFalse("content://downloads/2" in documents.written)
    }

    @Test
    fun `a retry needs no passport - the failure knows its file and its way`() = runTest {
        val manager = manager()
        val copy = savedCopy(manager)
        val bytes = documents.written.getValue(copy.uri).toByteArray()
        documents.written[copy.uri] = ByteArrayOutputStream().also { it.write(bytes, 0, bytes.size / 2) }
        manager.restore(copy, unsafe = false)
        advanceUntilIdle()
        assertTrue((manager.job.value as BackupJob.RestoreFailed).dataIntact)
        documents.written[copy.uri] = ByteArrayOutputStream().also { it.write(bytes) }
        manager.retry()
        assertEquals(BackupJob.Restoring(RestorePhase.EXTRACTING, checked = false), manager.job.value)
        advanceUntilIdle()
        assertTrue(manager.job.value is BackupJob.Restored)
    }

    @Test
    fun `a retry of the way without a net takes that way again`() = runTest {
        val manager = manager()
        val copy = savedCopy(manager)
        val bytes = documents.written.getValue(copy.uri).toByteArray()
        documents.written[copy.uri] = ByteArrayOutputStream().also { it.write(bytes, 0, bytes.size / 2) }
        manager.restore(copy, unsafe = true)
        advanceUntilIdle()
        val failed = manager.job.value as BackupJob.RestoreFailed
        assertTrue("the check failed: nothing has gone", failed.dataIntact)
        // a phone short of room is short of it still: the safe way would run out of room after a whole unpacking
        manager.retry()
        assertEquals(BackupJob.Restoring(RestorePhase.VERIFYING, checked = true), manager.job.value)
    }

    @Test
    fun `a retry after the worst case that fails again still says the media are gone`() = runTest {
        val manager = manager()
        val copy = savedCopy(manager)
        val bytes = documents.written.getValue(copy.uri).toByteArray()
        // the copy breaks after the media have gone: the worst case
        store.onDeleteMedia = {
            store.mediaBytes = 0
            documents.written[copy.uri] = ByteArrayOutputStream().also { it.write(bytes, 0, bytes.size / 2) }
        }
        manager.restore(copy, unsafe = true)
        advanceUntilIdle()
        assertFalse((manager.job.value as BackupJob.RestoreFailed).dataIntact)
        // «Ещё раз с этим файлом» on a phone that has even less room now
        store.onDeleteMedia = {}
        documents.written[copy.uri] = ByteArrayOutputStream().also { it.write(bytes) }
        store.free = 10_000
        manager.retry()
        advanceUntilIdle()
        val again = manager.job.value as BackupJob.RestoreFailed
        assertFalse("the media went in the try before: «Ваши данные на месте» would not be true", again.dataIntact)
        assertTrue(again.checked)
    }

    // — what a copy holds, and what a restore opens —

    @Test
    fun `a copy whose snapshot has gone is not called whole`() = runTest {
        val manager = manager()
        val lost = object : BackupStore by store {
            override suspend fun prepare(parts: Set<BackupPart>) = store.prepare(parts).let { prepared ->
                PreparedBackup(prepared.manifest, prepared.entries.map { if (it.part == BackupPart.DATA) BackupEntry(it.path, it.part, it.size, required = true) { null } else it })
            }
        }
        val other = BackupManager(lost, documents, prefs, {}, BackupConfig(), FixedWallClock(now, TimeZone.UTC), { testScheduler.currentTime }, StandardTestDispatcher(testScheduler), analytics)
        other.saveTo("content://downloads/1", all, "копия.zip")
        advanceUntilIdle()
        assertEquals(SaveFailure.FAILED, (other.job.value as BackupJob.SaveFailed).reason)
        assertEquals(listOf("content://downloads/1"), documents.deleted)
        assertNull(prefs.lastBackupAtEpochMs.value)
        assertEquals(BackupJob.Idle, manager.job.value)
    }

    @Test
    fun `a picked copy without its database is damaged and the media stay`() = runTest {
        val manager = manager()
        val manifest = store.manifest(all)
        documents.written["content://downloads/media"] = ByteArrayOutputStream().also { out ->
            BackupWriter.write(out, manifest, listOf(BackupEntry("sessions/a.mp4", BackupPart.VIDEO, store.video.size.toLong()) { ByteArrayInputStream(store.video) })) {}
        }
        val copy = manager.inspect("content://downloads/media") as BackupCandidate.Copy
        manager.restore(copy, unsafe = true)
        advanceUntilIdle()
        assertTrue((manager.job.value as BackupJob.RestoreFailed).dataIntact)
        assertFalse(store.mediaDeleted)
        assertFalse(store.readyMarked)
    }

    @Test
    fun `the way without a net stops at the check when the copy is bigger than its passport`() = runTest {
        val manager = manager(config = BackupConfig(freeSpaceMarginBytes = 1_000))
        val understated = store.manifest(all).copy(bytes = mapOf(BackupPart.DATA to 1_000L, BackupPart.VIDEO to 0L))
        documents.written["content://downloads/big"] = ByteArrayOutputStream().also { out ->
            BackupWriter.write(
                out, understated,
                listOf(
                    BackupEntry(BackupPaths.DATABASE_ENTRY, BackupPart.DATA, 1_000) { ByteArrayInputStream(ByteArray(1_000) { 7 }) },
                    BackupEntry("sessions/a.mp4", BackupPart.VIDEO, store.video.size.toLong()) { ByteArrayInputStream(store.video) },
                ),
            ) {}
        }
        val copy = manager.inspect("content://downloads/big") as BackupCandidate.Copy
        manager.restore(copy, unsafe = true)
        advanceUntilIdle()
        assertTrue((manager.job.value as BackupJob.RestoreFailed).dataIntact)
        assertFalse(store.mediaDeleted)
    }

    @Test
    fun `the way without a net leaves the media when they would not make the room`() = runTest {
        val manager = manager(config = BackupConfig(freeSpaceMarginBytes = 1_000))
        val copy = savedCopy(manager)
        // the check took minutes, and the phone has less room than when the screen offered the way
        store.free = 10_000
        store.mediaBytes = 1_000
        store.calls.clear()
        manager.restore(copy, unsafe = true)
        advanceUntilIdle()
        val failed = manager.job.value as BackupJob.RestoreFailed
        assertTrue(failed.dataIntact)
        assertEquals("the data fitted and were opened; the media would not make room for the rest", listOf("settle"), store.calls)
        assertFalse(store.mediaDeleted)
        assertFalse(store.readyMarked)
    }

    @Test
    fun `the way without a net needs room for the data of the copy before it unpacks them`() = runTest {
        val manager = manager(config = BackupConfig(freeSpaceMarginBytes = 1_000))
        val copy = savedCopy(manager)
        // the media would free plenty, but the data of the copy are unpacked while they are still in place
        store.free = 1_500
        store.mediaBytes = 1L shl 30
        store.calls.clear()
        manager.restore(copy, unsafe = true)
        advanceUntilIdle()
        val failed = manager.job.value as BackupJob.RestoreFailed
        assertTrue(failed.dataIntact && failed.checked)
        assertEquals(emptyList<String>(), store.calls)
        assertFalse(File(folder.root, "staging/db/violin.db").exists())
    }

    @Test
    fun `the unpacked data are opened before the point of no return`() = runTest {
        val manager = manager()
        val copy = savedCopy(manager)
        store.settleFails = IllegalStateException("A migration from 9 to 10 was required but not found")
        manager.restore(copy, unsafe = false)
        advanceUntilIdle()
        assertTrue((manager.job.value as BackupJob.RestoreFailed).dataIntact)
        assertFalse(store.readyMarked)
        assertEquals(1, store.stagingDiscarded)
        assertEquals(listOf(ErrorGroup.BACKUP), analytics.errors.map { it.first })

        // without a net the database is opened while the media are still there: a copy the app would not open costs nothing
        manager.restore(copy, unsafe = true)
        advanceUntilIdle()
        val failed = manager.job.value as BackupJob.RestoreFailed
        assertTrue(failed.dataIntact && failed.checked)
        assertFalse(store.mediaDeleted)
        assertFalse(store.readyMarked)
    }

    @Test
    fun `settling comes before the media go and before the mark`() = runTest {
        val manager = manager()
        val copy = savedCopy(manager)
        store.calls.clear()
        manager.restore(copy, unsafe = false)
        advanceUntilIdle()
        assertEquals(listOf("settle", "mark"), store.calls)
        store.calls.clear()
        store.readyMarked = false
        manager.dismiss()
        val other = manager()
        other.restore(copy, unsafe = true)
        advanceUntilIdle()
        assertEquals(listOf("settle", "deleteMedia", "mark"), store.calls)
        assertEquals(store.video.toList(), File(folder.root, "staging/sessions/a.mp4").readBytes().toList())
        assertEquals(1_000, File(folder.root, "staging/db/violin.db").length())
    }

    @Test
    fun `the unpacked settings are told the date of their copy`() = runTest {
        val manager = manager()
        val copy = savedCopy(manager)
        manager.restore(copy, unsafe = false)
        advanceUntilIdle()
        assertEquals(copy.manifest.createdAtEpochMs, store.settled)
    }

    private companion object {
        /** What one read of [timedCopy] gives. */
        const val BLOCK = 4_096
    }
}
