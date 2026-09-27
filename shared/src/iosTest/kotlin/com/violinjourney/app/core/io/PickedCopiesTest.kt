package com.violinjourney.app.core.io

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUUID
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile

/**
 * The copies of the pickers (`tmp/picked/`): each pick in a folder of its own, let go with it, and swept at the start of
 * the app once a day old — by the date of the pick, not of the original. Nothing outside the picked folder is released.
 * Every test works in a folder of its own that stands for tmp.
 */
@OptIn(ExperimentalForeignApi::class)
class PickedCopiesTest {
    private val files = NSFileManager.defaultManager
    private val temporary = NSTemporaryDirectory() + NSUUID().UUIDString
    private val root = "$temporary/picked"
    private val inbox = "$temporary/app-Inbox"
    // the real time: the folder of a pick made in the test is dated by the clock of the system
    private val now = (NSDate().timeIntervalSince1970 * MS_PER_SECOND).toLong()

    init {
        listOf(root, inbox).forEach { files.createDirectoryAtPath(it, withIntermediateDirectories = true, attributes = null, error = null) }
    }

    @AfterTest
    fun cleanUp() {
        files.removeItemAtPath(temporary, null)
    }

    @Test
    fun `a copy lies in a pick of its own and goes with it`() {
        val first = assertNotNull(PickedCopies.copy(url(file("$temporary/lent.jpg")), "a.jpg", root))
        val second = assertNotNull(PickedCopies.copy(url(file("$temporary/lent.jpg")), "a.jpg", root))
        assertTrue(first != second && exists(first) && exists(second), "two picks of one name stay apart")
        assertEquals(PickedCopies.pickOf(first, root), first.substringBeforeLast('/'))
        PickedCopies.release(first, root)
        assertFalse(exists(first.substringBeforeLast('/')), "the pick goes with its copy")
        assertTrue(exists(second), "and no other")
    }

    @Test
    fun `a copy that cannot be made leaves nothing`() {
        assertNull(PickedCopies.copy(url("$temporary/nothing here.jpg"), "a.jpg", root))
        assertTrue(names(root).isEmpty())
    }

    @Test
    fun `nothing outside the picked folder is released`() {
        val beside = file("$temporary/beside.jpg")
        val loose = file("$root/loose.jpg")
        val deeper = file("${assertNotNull(PickedCopies.newFolder(root))}/inner/deeper.jpg")
        val pick = assertNotNull(PickedCopies.newFolder(root))
        val kept = file("$pick/kept.jpg")
        for (path in listOf(beside, loose, deeper, "$pick/../../beside.jpg", "$pick/./kept.jpg", "$root/../beside.jpg")) {
            PickedCopies.release(path, root)
        }
        for (path in listOf(beside, loose, deeper, kept)) assertTrue(exists(path), path)
    }

    @Test
    fun `the sweep takes a pick a day old by the date of the pick`() {
        val old = assertNotNull(PickedCopies.newFolder(root)).also { dated(it, hoursAgo = 25) }
        val fresh = assertNotNull(PickedCopies.newFolder(root)).also { dated(it, hoursAgo = 23) }
        // a copy keeps the date of its original: a photo from last year picked a moment ago is still a fresh pick
        val lent = file("$temporary/last year.jpg").also { dated(it, hoursAgo = 24 * 365) }
        val justPicked = assertNotNull(PickedCopies.copy(url(lent), "last year.jpg", root))
        assertTrue(modifiedHoursAgo(justPicked) > 24, "the copy says it is a year old")
        PickedCopies.sweep(now, root, temporary, inbox)
        assertFalse(exists(old))
        assertTrue(exists(fresh))
        assertTrue(exists(justPicked))
    }

    @Test
    fun `the sweep takes what older builds left in tmp by its names alone`() {
        val uuid = NSUUID().UUIDString
        val copy = file("$temporary/$uuid.HEIC").also { dated(it, hoursAgo = 48) }
        val bare = "$temporary/${NSUUID().UUIDString}".also { files.createDirectoryAtPath(it, true, null, null); dated(it, hoursAgo = 48) }
        val fresh = file("$temporary/${NSUUID().UUIDString}.mov").also { dated(it, hoursAgo = 1) }
        val other = file("$temporary/notes.txt").also { dated(it, hoursAgo = 48) }
        val lowercase = file("$temporary/${uuid.lowercase()}.jpg").also { dated(it, hoursAgo = 48) }
        val inInbox = file("$inbox/Концерт.mp3").also { dated(it, hoursAgo = 48) }
        val freshInInbox = file("$inbox/Этюд.mp3").also { dated(it, hoursAgo = 1) }
        PickedCopies.sweep(now, root, temporary, inbox)
        assertFalse(exists(copy), "a copy of an older build")
        assertFalse(exists(bare), "a folder of an older build")
        assertFalse(exists(inInbox), "the old Inbox of the picker of Files")
        for (path in listOf(fresh, other, lowercase, freshInInbox, root)) assertTrue(exists(path), path)
    }

    private fun file(path: String): String {
        files.createDirectoryAtPath(path.substringBeforeLast('/'), withIntermediateDirectories = true, attributes = null, error = null)
        assertTrue(("a picture" as NSString).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null), path)
        return path
    }

    private fun dated(path: String, hoursAgo: Int) {
        val at = NSDate.dateWithTimeIntervalSince1970((now - hoursAgo * HOUR_MS) / MS_PER_SECOND)
        assertTrue(files.setAttributes(mapOf<Any?, Any?>(NSFileModificationDate to at), ofItemAtPath = path, error = null), path)
    }

    private fun modifiedHoursAgo(path: String): Long {
        val date = assertNotNull(files.attributesOfItemAtPath(path, null)?.get(NSFileModificationDate) as? NSDate)
        return (now - (date.timeIntervalSince1970 * MS_PER_SECOND).toLong()) / HOUR_MS
    }

    private fun url(path: String) = NSURL.fileURLWithPath(path)

    private fun exists(path: String) = files.fileExistsAtPath(path)

    private fun names(folder: String) = files.contentsOfDirectoryAtPath(folder, null).orEmpty()

    private companion object {
        const val HOUR_MS = 3_600_000L
        const val MS_PER_SECOND = 1_000.0
    }
}
