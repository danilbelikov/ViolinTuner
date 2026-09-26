package com.violinjourney.app.core.backup

import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.child
import com.violinjourney.app.core.io.exists
import com.violinjourney.app.core.io.listNames
import com.violinjourney.app.core.io.openInput
import com.violinjourney.app.core.io.openOutput
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID

/** A place for a copy on iOS is a folder of Files: a copy made earlier there is never written over, nor taken away. */
@OptIn(ExperimentalForeignApi::class)
class IosBackupDocumentsTest {
    private val folder = PlatformFile(NSTemporaryDirectory() + NSUUID().UUIDString).also {
        NSFileManager.defaultManager.createDirectoryAtPath(it.path, true, null, null)
    }
    private val documents = IosBackupDocuments()
    private val morning = byteArrayOf(1, 2, 3, 4, 5)

    @AfterTest
    fun cleanUp() {
        NSFileManager.defaultManager.removeItemAtPath(folder.path, null)
    }

    private fun place(fileName: String): String = assertNotNull(IosPickedPlaces.place(NSURL.fileURLWithPath(folder.path), fileName))

    private fun write(file: PlatformFile, bytes: ByteArray) {
        file.openOutput()!!.use { it.write(bytes, 0, bytes.size) }
    }

    private fun bytesOf(file: PlatformFile): ByteArray {
        val out = ArrayList<Byte>()
        val buffer = ByteArray(64)
        file.openInput()!!.use { input ->
            while (true) {
                val read = input.read(buffer, 0, buffer.size)
                if (read < 0) break
                for (i in 0 until read) out += buffer[i]
            }
        }
        return out.toByteArray()
    }

    @Test
    fun `a second copy of the day gets a name of its own and the first stays whole`() {
        write(folder.child("copy.zip"), morning)
        val uri = place("copy.zip")
        assertEquals("copy (1).zip", documents.nameOf(uri))
        val evening = byteArrayOf(9, 9)
        documents.openOutput(uri)!!.use { it.write(evening, 0, evening.size) }
        assertTrue(morning.contentEquals(bytesOf(folder.child("copy.zip"))))
        assertTrue(evening.contentEquals(bytesOf(folder.child("copy (1).zip"))))
    }

    @Test
    fun `names count up as Android names them`() {
        val taken = setOf("a.zip", "a (1).zip", "notes", "Kopie 20. September 2026.zip")
        assertEquals("a (2).zip", freeFileName("a.zip") { it in taken })
        assertEquals("b.zip", freeFileName("b.zip") { it in taken })
        assertEquals("notes (1)", freeFileName("notes") { it in taken })
        assertEquals("Kopie 20. September 2026 (1).zip", freeFileName("Kopie 20. September 2026.zip") { it in taken })
    }

    @Test
    fun `an iCloud file not downloaded yet takes its name too`() {
        write(folder.child(".copy.zip.icloud"), morning)
        assertEquals("copy (1).zip", documents.nameOf(place("copy.zip")))
    }

    @Test
    fun `a file that is there already is never opened for writing`() {
        val earlier = folder.child("copy.zip")
        write(earlier, morning)
        val uri = NSURL.fileURLWithPath(earlier.path).absoluteString!!
        assertNull(documents.openOutput(uri))
        assertTrue(morning.contentEquals(bytesOf(earlier)))
    }

    @Test
    fun `a copy picked where it lies is read in place`() {
        val picked = folder.child("Интонация · копия.zip")
        val bytes = ByteArray(70_000) { (it % 251).toByte() }
        write(picked, bytes)
        val uri = assertNotNull(IosPickedPlaces.copy(NSURL.fileURLWithPath(picked.path)))
        val read = ArrayList<Byte>()
        val buffer = ByteArray(8_192)
        assertNotNull(documents.openInput(uri)).use { input ->
            while (true) {
                val got = input.read(buffer, 0, buffer.size)
                if (got < 0) break
                for (i in 0 until got) read += buffer[i]
            }
        }
        assertTrue(bytes.contentEquals(read.toByteArray()))
        assertEquals("Интонация · копия.zip", documents.nameOf(uri))
        assertEquals(bytes.size.toLong(), documents.sizeOf(uri))
        assertEquals(listOf("Интонация · копия.zip"), folder.listNames(), "no second copy of it anywhere in the folder")
    }

    @Test
    fun `a copy that is not there is no stream`() {
        assertNull(documents.openInput(NSURL.fileURLWithPath(folder.child("gone.zip").path).absoluteString!!))
    }

    @Test
    fun `an unfinished copy removes only the file this app made`() {
        val earlier = folder.child("copy.zip")
        write(earlier, morning)
        documents.delete(NSURL.fileURLWithPath(earlier.path).absoluteString!!)
        assertTrue(earlier.exists(), "a copy that was there before is not the app's to remove")

        val uri = place("copy.zip")
        documents.openOutput(uri)!!.close()
        assertTrue(folder.child("copy (1).zip").exists())
        documents.delete(uri)
        assertFalse(folder.child("copy (1).zip").exists())
        assertTrue(morning.contentEquals(bytesOf(earlier)))
    }
}
