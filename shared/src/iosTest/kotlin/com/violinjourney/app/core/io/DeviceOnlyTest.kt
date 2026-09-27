package com.violinjourney.app.core.io

import com.violinjourney.app.core.backup.DataLayout
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.ios.IosFolders
import com.violinjourney.app.ios.IosSheetFiles
import com.violinjourney.app.ios.IosStorage
import com.violinjourney.app.ios.IosVideoFiles
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

/**
 * What the backup of the phone leaves out (spec 5.14): the mark holds on a folder and on a file, and does not follow what
 * is moved out of a marked folder — the way the swap of a restore puts the unpacked folders in place.
 */
@OptIn(ExperimentalForeignApi::class)
class DeviceOnlyTest {
    private val files = NSFileManager.defaultManager
    private val folder = NSTemporaryDirectory() + NSUUID().UUIDString

    init {
        files.createDirectoryAtPath(folder, withIntermediateDirectories = true, attributes = null, error = null)
    }

    @AfterTest
    fun cleanUp() {
        files.removeItemAtPath(folder, null)
    }

    @Test
    fun `a marked folder stays out of the backup and an unmarked one does not`() {
        val marked = folderAt("staging")
        val plain = folderAt("sessions")
        assertTrue(DeviceOnly.mark(marked), "the system takes the mark")
        assertTrue(DeviceOnly.isMarked(marked))
        assertFalse(DeviceOnly.isMarked(plain), "data are backed up")
    }

    @Test
    fun `a file is marked as a folder is`() {
        val mark = "$folder/restore-wipe"
        assertTrue(PlatformFile(mark).openOutput()!!.let { it.close(); true })
        assertTrue(DeviceOnly.mark(mark))
        assertTrue(DeviceOnly.isMarked(mark))
    }

    @Test
    fun `a folder moved out of a marked one is backed up again`() {
        val staging = folderAt("staging")
        DeviceOnly.mark(staging)
        val unpacked = folderAt("staging/sessions")
        assertTrue(PlatformFile("$unpacked/take.m4a").openOutput()!!.let { it.close(); true })
        val data = folderAt("data")
        assertTrue(files.moveItemAtPath(unpacked, "$data/sessions", null))
        assertFalse(DeviceOnly.isMarked("$data/sessions"), "the mark stays with the staging folder")
        assertTrue(DeviceOnly.isMarked(staging))
    }

    @Test
    fun `the camera folders and the waveforms stay out of the backup and the data go into it`() {
        // made anew here: a mark an earlier run left on them would prove nothing
        for (name in listOf(DataLayout.CAMERA, DataLayout.WAVEFORMS)) assertTrue(PlatformFile("${IosStorage.dataDirectory()}/$name").deleteAll(), name)
        val sheetShots = IosSheetFiles(Dispatchers.Default, RepertoireConfig()).newCameraFile().path.substringBeforeLast('/')
        val videoShots = IosVideoFiles(RepertoireConfig(), Dispatchers.Default).newCameraFile().path.substringBeforeLast('/')
        assertTrue(DeviceOnly.isMarked(sheetShots), sheetShots)
        assertTrue(DeviceOnly.isMarked(videoShots), videoShots)
        assertTrue(DeviceOnly.isMarked(IosFolders.deviceOnlyFolder(DataLayout.WAVEFORMS)))
        for (name in DataLayout.MEDIA_DIRS) assertFalse(DeviceOnly.isMarked(IosFolders.folder(name)), name)
    }

    @Test
    fun `a helper folder deleted and made again is marked again`() {
        val waveforms = IosFolders.deviceOnlyFolder(DataLayout.WAVEFORMS)
        // what a restore does to the waveforms (IosRestoreSwap)
        assertTrue(PlatformFile(waveforms).deleteAll())
        assertTrue(DeviceOnly.isMarked(IosFolders.deviceOnlyFolder(DataLayout.WAVEFORMS)))
    }

    private fun folderAt(name: String): String = "$folder/$name".also {
        files.createDirectoryAtPath(it, withIntermediateDirectories = true, attributes = null, error = null)
    }
}
