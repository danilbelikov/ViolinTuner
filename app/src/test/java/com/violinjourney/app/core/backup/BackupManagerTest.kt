package com.violinjourney.app.core.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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

    private inner class Store : BackupStore {
        var free = Long.MAX_VALUE
        var cleaned = 0
        var readyMarked = false
        var wipeMarked = false
        var mediaDeleted = false
        var stagingDiscarded = 0
        val video = ByteArray(400_000) { (it % 97).toByte() }
        override val databaseVersion = 6
        override suspend fun contents() = BackupContents(counts, mapOf(BackupPart.DATA to 1_000L, BackupPart.VIDEO to video.size.toLong()))
        override suspend fun prepare(parts: Set<BackupPart>) = PreparedBackup(
            BackupManifest(1, "1.0", 6, now.toEpochMilli(), "Pixel 7", parts + BackupPart.DATA, counts, mapOf(BackupPart.DATA to 1_000L, BackupPart.VIDEO to video.size.toLong())),
            listOfNotNull(
                BackupEntry("db/violin.db", BackupPart.DATA, 1_000) { ByteArrayInputStream(ByteArray(1_000) { 7 }) },
                BackupEntry("sessions/a.mp4", BackupPart.VIDEO, video.size.toLong()) { ByteArrayInputStream(video) }.takeIf { BackupPart.VIDEO in parts },
            ),
        )
        override fun cleanUp() { cleaned++ }
        override fun freeBytes() = free
        override fun newStaging(): File = File(folder.root, "staging").also { it.deleteRecursively(); it.mkdirs() }
        override fun discardStaging() { stagingDiscarded++; File(folder.root, "staging").deleteRecursively() }
        override fun markStagingReady() { readyMarked = true }
        override fun deleteMedia() { mediaDeleted = true }
        override fun markWipe() { wipeMarked = true }
        override fun shareFile(fileName: String) = File(folder.root, "share/$fileName").also { it.parentFile!!.mkdirs() }
    }

    /** Documents that live in memory; [failWith] breaks the writing half way. */
    private inner class Documents : BackupDocuments {
        val written = HashMap<String, ByteArrayOutputStream>()
        val deleted = mutableListOf<String>()
        var failWith: IOException? = null
        var unreadable = false
        override fun openOutput(uri: String): OutputStream? {
            val sink = ByteArrayOutputStream().also { written[uri] = it }
            val failure = failWith ?: return sink
            return object : OutputStream() {
                override fun write(b: Int) = sink.write(b)
                override fun write(b: ByteArray, off: Int, len: Int) {
                    if (sink.size() > 100_000) throw failure
                    sink.write(b, off, len)
                }
            }
        }
        override fun openInput(uri: String): InputStream? = written[uri]?.takeIf { !unreadable }?.let { ByteArrayInputStream(it.toByteArray()) }
        override fun delete(uri: String) { deleted += uri; written.remove(uri) }
        override fun placeOf(uri: String) = "Загрузки"
        override fun nameOf(uri: String) = "Интонация · копия.zip"
        override fun sizeOf(uri: String) = written[uri]?.size()?.toLong()
    }

    private class Prefs : BackupPrefs {
        override val lastBackupAtEpochMs = MutableStateFlow<Long?>(null)
        override suspend fun setLastBackupAt(epochMs: Long) { lastBackupAtEpochMs.value = epochMs }
    }

    private val store = Store()
    private val documents = Documents()
    private val prefs = Prefs()
    private var keptAlive = 0
    private val all = BackupPart.entries.toSet()

    private fun TestScope.manager() = BackupManager(
        store, documents, prefs, { keptAlive++ }, BackupConfig(), BackupSpeed(BackupConfig()),
        Clock.fixed(now, ZoneId.of("Europe/Moscow")), { testScheduler.currentTime }, StandardTestDispatcher(testScheduler),
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
        assertEquals(now.toEpochMilli(), prefs.lastBackupAtEpochMs.value)
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
    fun `a place that cannot be opened is a place that went away`() = runTest {
        val manager = manager()
        val closed = object : BackupDocuments by documents {
            override fun openOutput(uri: String): OutputStream? = null
        }
        val other = BackupManager(store, closed, prefs, {}, BackupConfig(), BackupSpeed(BackupConfig()), Clock.systemUTC(), { testScheduler.currentTime }, StandardTestDispatcher(testScheduler))
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
        manager().startClean()
        assertTrue(store.wipeMarked)
    }

    @Test
    fun `remaining time waits until the speed is worth a word`() = runTest {
        // a slow place: every write takes its time
        val manager = manager()
        val slow = object : BackupDocuments by documents {
            override fun openOutput(uri: String): OutputStream = object : OutputStream() {
                override fun write(b: Int) = Unit
                override fun write(b: ByteArray, off: Int, len: Int) = Unit
            }
        }
        val other = BackupManager(store, slow, prefs, {}, BackupConfig(), BackupSpeed(BackupConfig()), Clock.systemUTC(), { testScheduler.currentTime }, StandardTestDispatcher(testScheduler))
        other.saveTo("content://slow/1", all, "копия.zip")
        advanceTimeBy(800)
        runCurrent()
        assertTrue(other.job.value.let { it !is BackupJob.Saving || it.visible })
        advanceUntilIdle()
        assertTrue(other.job.value is BackupJob.Saved)
        assertEquals(BackupJob.Idle, manager.job.value)
    }
}
