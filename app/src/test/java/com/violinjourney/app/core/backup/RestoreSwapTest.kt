package com.violinjourney.app.core.backup

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RestoreSwapTest {
    @get:Rule val folder = TemporaryFolder()
    private lateinit var files: File
    private lateinit var databases: File

    @Before
    fun setUp() {
        files = folder.newFolder("files")
        databases = folder.newFolder("databases")
        // what is in the app now
        write(File(databases, "violin.db"), "old database")
        write(File(databases, "violin.db-wal"), "old journal")
        write(File(files, "datastore/user_settings.preferences_pb"), "old settings")
        write(File(files, "sessions/old.m4a"), "old sound")
        write(File(files, "repertoire/old.jpg"), "old sheet")
        write(File(files, "profile/avatar-1.jpg"), "old face")
        write(File(files, "waveforms/old.m4a.wave"), "old wave")
    }

    private fun write(file: File, text: String) {
        file.parentFile!!.mkdirs()
        file.writeText(text)
    }

    private fun stage(withVideo: Boolean = true) {
        val staging = File(files, RestoreSwap.STAGING)
        write(File(staging, "db/violin.db"), "new database")
        write(File(staging, "settings/user_settings.preferences_pb"), "new settings")
        write(File(staging, "sessions/new.m4a"), "new sound")
        if (withVideo) write(File(staging, "sessions/new.mp4"), "new video")
        write(File(staging, "repertoire/new.jpg"), "new sheet")
        File(staging, "profile").mkdirs() // the copy had no photo: the folder is there all the same, and empty
    }

    private fun apply() = RestoreSwap.applyIfPending(files, databases)

    private fun assertRestored() {
        assertEquals("new database", File(databases, "violin.db").readText())
        assertFalse("the journal of the old database must not be replayed over the new one", File(databases, "violin.db-wal").exists())
        assertEquals("new settings", File(files, "datastore/user_settings.preferences_pb").readText())
        assertEquals(listOf("new.m4a", "new.mp4"), File(files, "sessions").list()!!.sorted())
        assertEquals(listOf("new.jpg"), File(files, "repertoire").list()!!.toList())
        assertEquals(emptyList<String>(), File(files, "profile").list()!!.toList())
        assertFalse(File(files, "waveforms").exists())
        assertFalse(File(files, RestoreSwap.STAGING).exists())
        assertFalse(File(files, RestoreSwap.READY_MARK).exists())
    }

    @Test
    fun `nothing pending changes nothing`() {
        assertEquals(RestoreSwap.Outcome.NOTHING, apply())
        assertEquals("old database", File(databases, "violin.db").readText())
        assertEquals("old sound", File(files, "sessions/old.m4a").readText())
    }

    @Test
    fun `unpacking that never got its mark is thrown away, and the data stay`() {
        stage()
        assertEquals(RestoreSwap.Outcome.NOTHING, apply())
        assertFalse(File(files, RestoreSwap.STAGING).exists())
        assertEquals("old database", File(databases, "violin.db").readText())
        assertEquals("old sheet", File(files, "repertoire/old.jpg").readText())
    }

    @Test
    fun `a marked copy takes the place of everything`() {
        stage()
        File(files, RestoreSwap.READY_MARK).createNewFile()
        assertEquals(RestoreSwap.Outcome.RESTORED, apply())
        assertRestored()
        assertEquals("the next start finds nothing to do", RestoreSwap.Outcome.NOTHING, apply())
    }

    @Test
    fun `a swap cut short at any step is finished by the next start`() {
        // every prefix of the steps a dying process may have got through
        val steps: List<() -> Unit> = listOf(
            { File(databases, "violin.db").delete(); File(databases, "violin.db-wal").delete() },
            { File(files, "restore-staging/db/violin.db").renameTo(File(databases, "violin.db")) },
            { File(files, "datastore/user_settings.preferences_pb").delete(); File(files, "restore-staging/settings/user_settings.preferences_pb").renameTo(File(files, "datastore/user_settings.preferences_pb")) },
            { File(files, "profile").deleteRecursively(); File(files, "restore-staging/profile").renameTo(File(files, "profile")) },
            { File(files, "repertoire").deleteRecursively() },
            { File(files, "restore-staging/repertoire").renameTo(File(files, "repertoire")) },
            { File(files, "sessions").deleteRecursively(); File(files, "restore-staging/sessions").renameTo(File(files, "sessions")) },
        )
        for (done in 0..steps.size) {
            folder.root.listFiles()!!.forEach { it.deleteRecursively() }
            setUp()
            stage()
            File(files, RestoreSwap.READY_MARK).createNewFile()
            steps.take(done).forEach { it() }
            assertEquals("after $done steps", RestoreSwap.Outcome.RESTORED, apply())
            assertRestored()
        }
    }

    @Test
    fun `starting clean wipes the data, the staging folder and the marks`() {
        stage()
        File(files, RestoreSwap.READY_MARK).createNewFile()
        File(files, RestoreSwap.WIPE_MARK).createNewFile()
        assertEquals(RestoreSwap.Outcome.WIPED, apply())
        assertFalse(File(databases, "violin.db").exists())
        assertFalse(File(files, "datastore/user_settings.preferences_pb").exists())
        assertFalse(File(files, "sessions").exists())
        assertFalse(File(files, RestoreSwap.STAGING).exists())
        assertEquals(RestoreSwap.Outcome.NOTHING, apply())
    }
}
