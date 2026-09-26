package com.violinjourney.app.core.backup

import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.child
import com.violinjourney.app.core.io.deleteAll
import com.violinjourney.app.core.io.exists
import com.violinjourney.app.core.io.listNames
import com.violinjourney.app.core.io.makeDirectories
import com.violinjourney.app.core.io.moveTo
import com.violinjourney.app.core.io.openInput
import com.violinjourney.app.core.io.openOutput
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.posix.chmod

/**
 * The swap of a copy on iOS, over the layout of the iOS app — the database and the settings beside the folders of media in
 * Application Support — as `RestoreSwapTest` checks it on Android: whole, finished after a cut, and never losing what it
 * has not put in place yet.
 */
@OptIn(ExperimentalForeignApi::class)
class IosRestoreSwapTest {
    private val root = PlatformFile(NSTemporaryDirectory() + NSUUID().UUIDString)
    private val data = root.child("data")
    private val staging = data.child(IosRestoreSwap.STAGING)

    @AfterTest
    fun cleanUp() {
        // a folder a test locked is opened again first: a locked folder is not deleted
        listOf(data.child("backings").child("locked"), data.child("violin.db-wal").child("locked")).forEach { chmod(it.path, OPEN.convert()) }
        NSFileManager.defaultManager.removeItemAtPath(root.path, null)
    }

    private fun write(file: PlatformFile, text: String) {
        file.path.substringBeforeLast('/').let { PlatformFile(it).makeDirectories() }
        file.openOutput()!!.use { out -> text.encodeToByteArray().let { out.write(it, 0, it.size) } }
    }

    private fun read(file: PlatformFile): String {
        val buffer = ByteArray(256)
        val read = file.openInput()!!.use { it.read(buffer, 0, buffer.size) }
        return buffer.decodeToString(0, maxOf(read, 0))
    }

    private fun layOut() {
        root.deleteAll()
        // what is in the app now
        write(data.child("violin.db"), "old database")
        write(data.child("violin.db-wal"), "old journal")
        write(data.child(IosRestoreSwap.SETTINGS_FILE), "old settings")
        write(data.child("sessions/old.m4a"), "old sound")
        write(data.child("repertoire/old.jpg"), "old sheet")
        write(data.child("profile/avatar-1.jpg"), "old face")
        write(data.child("backings/old.m4a"), "old backing")
        write(data.child("waveforms/old.m4a.wave"), "old wave")
        // what the copy brought, unpacked and marked
        write(staging.child("db/violin.db"), "new database")
        write(staging.child("settings/${IosRestoreSwap.SETTINGS_FILE}"), "new settings")
        write(staging.child("sessions/new.m4a"), "new sound")
        write(staging.child("sessions/new.mov"), "new video")
        write(staging.child("repertoire/new.jpg"), "new sheet")
        write(staging.child("backings/new.mp3"), "new backing")
        staging.child("profile").makeDirectories() // the copy had no photo: the folder is there all the same, and empty
        write(data.child(IosRestoreSwap.READY_MARK), "")
    }

    private fun assertRestored() {
        assertEquals("new database", read(data.child("violin.db")))
        assertFalse(data.child("violin.db-wal").exists(), "the journal of the old database must not be played over the new one")
        assertEquals("new settings", read(data.child(IosRestoreSwap.SETTINGS_FILE)))
        assertEquals(listOf("new.m4a", "new.mov"), data.child("sessions").listNames().sorted())
        assertEquals(listOf("new.jpg"), data.child("repertoire").listNames())
        assertEquals(listOf("new.mp3"), data.child("backings").listNames())
        assertEquals(emptyList(), data.child("profile").listNames())
        assertFalse(data.child("waveforms").exists())
        assertFalse(staging.exists())
        assertFalse(data.child(IosRestoreSwap.READY_MARK).exists())
    }

    @Test
    fun `a marked copy takes the place of everything`() {
        layOut()
        assertEquals(IosRestoreSwap.Outcome.RESTORED, IosRestoreSwap.applyIfPending(data))
        assertRestored()
        assertEquals(IosRestoreSwap.Outcome.NOTHING, IosRestoreSwap.applyIfPending(data), "the next start finds nothing to do")
    }

    @Test
    fun `a swap cut short at any step is finished by the next start`() {
        fun move(from: String, to: String) = assertTrue(staging.child(from).moveTo(data.child(to)), "$from → $to")
        // every prefix of the steps a dying process may have got through, in the order the swap takes them
        val steps: List<() -> Unit> = listOf(
            { data.child("violin.db").deleteAll(); data.child("violin.db-wal").deleteAll() },
            { move("db/violin.db", "violin.db") },
            { data.child(IosRestoreSwap.SETTINGS_FILE).deleteAll(); move("settings/${IosRestoreSwap.SETTINGS_FILE}", IosRestoreSwap.SETTINGS_FILE) },
            { data.child("profile").deleteAll(); move("profile", "profile") },
            { data.child("repertoire").deleteAll() },
            { move("repertoire", "repertoire") },
            { data.child("sessions").deleteAll(); move("sessions", "sessions") },
            { data.child("backings").deleteAll() },
            { move("backings", "backings") },
            { data.child("waveforms").deleteAll() },
        )
        for (done in 0..steps.size) {
            layOut()
            steps.take(done).forEach { it() }
            assertEquals(IosRestoreSwap.Outcome.RESTORED, IosRestoreSwap.applyIfPending(data), "after $done steps")
            assertRestored()
        }
    }

    @Test
    fun `a folder that cannot be put in place keeps the unpacked copy and its mark`() {
        layOut()
        // a folder inside the old backings that cannot be emptied: they do not go, and the new ones cannot take their place
        write(data.child("backings/locked/old.m4a"), "old backing")
        chmod(data.child("backings/locked").path, LOCKED.convert())
        assertFailsWith<okio.IOException> { IosRestoreSwap.applyIfPending(data) }
        assertEquals(listOf("new.mp3"), staging.child("backings").listNames(), "what did not move is still in the staging folder")
        assertTrue(data.child(IosRestoreSwap.READY_MARK).exists(), "the mark stays for the next start")

        // the next start, with the folder open again, finishes the swap
        chmod(data.child("backings/locked").path, OPEN.convert())
        assertEquals(IosRestoreSwap.Outcome.RESTORED, IosRestoreSwap.applyIfPending(data))
        assertRestored()
    }

    @Test
    fun `a journal of the old database that will not go stops the swap before the new database comes`() {
        layOut()
        data.child("violin.db-wal").deleteAll()
        write(data.child("violin.db-wal/locked/x"), "old journal")
        chmod(data.child("violin.db-wal/locked").path, LOCKED.convert())
        assertFailsWith<okio.IOException> { IosRestoreSwap.applyIfPending(data) }
        assertEquals("new database", read(staging.child("db/violin.db")), "the new database waits in the staging folder")
        assertTrue(data.child(IosRestoreSwap.READY_MARK).exists())

        chmod(data.child("violin.db-wal/locked").path, OPEN.convert())
        assertEquals(IosRestoreSwap.Outcome.RESTORED, IosRestoreSwap.applyIfPending(data))
        assertRestored()
    }

    @Test
    fun `unpacking that never got its mark is thrown away and the data stay`() {
        layOut()
        data.child(IosRestoreSwap.READY_MARK).deleteAll()
        assertEquals(IosRestoreSwap.Outcome.NOTHING, IosRestoreSwap.applyIfPending(data))
        assertFalse(staging.exists())
        assertEquals("old database", read(data.child("violin.db")))
        assertEquals("old sheet", read(data.child("repertoire/old.jpg")))
    }

    @Test
    fun `starting clean wipes the data the staging folder and the marks`() {
        layOut()
        write(data.child(IosRestoreSwap.WIPE_MARK), "")
        assertEquals(IosRestoreSwap.Outcome.WIPED, IosRestoreSwap.applyIfPending(data))
        listOf("violin.db", "violin.db-wal", IosRestoreSwap.SETTINGS_FILE, "sessions", "repertoire", "profile", "backings", "waveforms").forEach {
            assertFalse(data.child(it).exists(), it)
        }
        assertFalse(staging.exists())
        assertFalse(data.child(IosRestoreSwap.READY_MARK).exists())
        assertFalse(data.child(IosRestoreSwap.WIPE_MARK).exists())
        assertEquals(IosRestoreSwap.Outcome.NOTHING, IosRestoreSwap.applyIfPending(data))
    }

    private companion object {
        /** r-x for everyone: nothing inside can be removed. */
        const val LOCKED = 0x16D // 0555
        const val OPEN = 0x1ED // 0755
    }
}
