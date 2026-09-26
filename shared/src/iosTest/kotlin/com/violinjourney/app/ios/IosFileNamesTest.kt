package com.violinjourney.app.ios

import com.violinjourney.app.core.audio.backing.IosBackingFiles
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.child
import com.violinjourney.app.core.io.exists
import com.violinjourney.app.core.io.makeDirectories
import com.violinjourney.app.core.io.openOutput
import com.violinjourney.app.core.time.SystemWallClock
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

/**
 * Names from the database stay inside their folder on iOS — a copy picked by hand may bring any name (spec 3.20). The
 * stores over the app's own folders are only asked, never told to delete: their folder is the data of the test process.
 */
@OptIn(ExperimentalForeignApi::class)
class IosFileNamesTest {
    private val folder = PlatformFile(NSTemporaryDirectory() + NSUUID().UUIDString).also { it.makeDirectories() }
    private val outside = listOf("", ".", "..", "../x", "a/b")

    @AfterTest
    fun cleanUp() {
        NSFileManager.defaultManager.removeItemAtPath(folder.path, null)
    }

    @Test
    fun `names that leave the folder are not found`() {
        val config = RepertoireConfig()
        val sessions = IosSessionAudioFiles(config)
        val sheets = IosSheetFiles(Dispatchers.Default, config)
        val avatars = IosAvatarFiles(Dispatchers.Default, SystemWallClock)
        val videos = IosVideoFiles(config, Dispatchers.Default)
        val backings = IosBackingFiles(folder, SystemWallClock)
        outside.forEach { name ->
            assertNull(sessions.existing(name), "sessions «$name»")
            assertNull(sheets.existing(name), "sheets «$name»")
            assertNull(avatars.existing(name), "avatars «$name»")
            assertNull(videos.existing(name), "videos «$name»")
            assertNull(videos.thumbOf(name), "thumbnails «$name»")
            assertNull(backings.existing(name), "backings «$name»")
        }
    }

    @Test
    fun `a folder under a plain name is no backing and is not deleted by its name`() {
        val backings = IosBackingFiles(folder, SystemWallClock)
        val inner = folder.child("backings").child("x").also { it.makeDirectories() }
        val kept = inner.child("kept.m4a")
        kept.openOutput()!!.close()
        assertNull(backings.existing("x"))
        backings.delete("x")
        assertTrue(kept.exists())
        val real = folder.child("backings").child("a.m4a")
        real.openOutput()!!.close()
        assertEquals(real.path, backings.existing("a.m4a")?.path)
        backings.delete("a.m4a")
        assertFalse(real.exists())
    }

    @Test
    fun `a delete by a name that leaves the folder deletes nothing`() {
        val backings = IosBackingFiles(folder, SystemWallClock)
        val victim = folder.child("victim")
        victim.openOutput()!!.close()
        // «..» of `backings/` is the whole folder of the data, and it would go with everything in it
        outside.forEach { backings.delete(it) }
        assertTrue(folder.exists())
        assertTrue(victim.exists())
    }
}
