package com.violinjourney.app.core.io

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSBundle
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.timeIntervalSince1970

/**
 * The app's own copies of what the system pickers lend on iOS: photos of sheets and of the profile, videos, files of Files
 * (spec 3.13, 3.15, 3.19, 3.32). A lent file is gone once the picker's callback returns, so it is copied at once — each
 * pick into a folder of its own in `tmp/picked/`, dated by the pick, whatever date the copy keeps of its original.
 *
 * A copy is the app's to let go: the importer that took it releases it ([release]) whatever became of the import — taken
 * in, refused, failed or stopped. What a crash or a pick a screen dropped leaves behind is swept at the next start of the
 * app ([sweep]). Nothing outside `tmp/picked/` is ever deleted here, but the leftovers of the builds before 28.09.2026,
 * which copied into the temporary folder itself.
 */
@OptIn(ExperimentalForeignApi::class)
internal object PickedCopies {
    private val files get() = NSFileManager.defaultManager

    /** `tmp/picked`, where every copy lies; made with the first pick. */
    fun root(): String = "${temporary()}/$FOLDER"

    /** Copies [lent] as [name] into a folder of its own under [root]: the path of the copy, or null — and nothing left. */
    fun copy(lent: NSURL, name: String, root: String = root()): String? {
        val folder = newFolder(root) ?: return null
        val copy = "$folder/$name"
        if (files.copyItemAtURL(lent, NSURL.fileURLWithPath(copy), null)) return copy
        files.removeItemAtPath(folder, null)
        return null
    }

    /** A new folder for one pick under [root]; null when it cannot be made. */
    fun newFolder(root: String = root()): String? =
        "$root/${NSUUID().UUIDString}".takeIf { files.createDirectoryAtPath(it, withIntermediateDirectories = true, attributes = null, error = null) }

    /**
     * Lets the copy at [path] go, with the folder of its pick and whatever a move out of it left there. A path that is no
     * copy under [root] — a shot of the camera in Application Support, a file of a test — is left alone.
     */
    fun release(path: String, root: String = root()) {
        pickOf(path, root)?.let { files.removeItemAtPath(it, null) }
    }

    /** The folder of the pick that [path] is the copy in: the path is exactly `root/<pick>/<name>`, never a step up. */
    fun pickOf(path: String, root: String = root()): String? {
        val inside = path.removePrefix("$root/").takeIf { it != path } ?: return null
        val parts = inside.split('/')
        if (parts.size != 2 || parts.any { it.isEmpty() || it == "." || it == ".." }) return null
        return "$root/${parts.first()}"
    }

    /**
     * Deletes the picks older than a day by the date of their folders, and what the builds before 28.09.2026 left: copies
     * right in [temporary] under a name that is an NSUUID, with an extension or without, and the copies the picker of Files
     * put in its [inbox]. Those two keep the date of their originals, so a copy made a second ago may look years old: this
     * is called at the start of the app alone, when no picker is open and no import runs — never on a return to a screen.
     */
    fun sweep(nowEpochMs: Long, root: String = root(), temporary: String = temporary(), inbox: String = inbox(temporary)) {
        fun remove(folder: String, names: List<String>) =
            names.map { "$folder/$it" }.filter { nowEpochMs - modifiedMs(it) > MAX_AGE_MS }.forEach { files.removeItemAtPath(it, null) }
        remove(root, names(root))
        remove(inbox, names(inbox))
        remove(temporary, names(temporary).filter(LEFTOVER::matches))
    }

    private fun names(folder: String): List<String> = files.contentsOfDirectoryAtPath(folder, null)?.mapNotNull { it as? String }.orEmpty()

    private fun modifiedMs(path: String): Long =
        ((files.attributesOfItemAtPath(path, null)?.get(NSFileModificationDate) as? NSDate)?.timeIntervalSince1970 ?: 0.0).times(MS_PER_SECOND).toLong()

    private fun temporary(): String = NSTemporaryDirectory().trimEnd('/')

    /** Where the picker of Files puts what it copies for the app (`asCopy`). */
    private fun inbox(temporary: String): String = "$temporary/${NSBundle.mainBundle.bundleIdentifier}-Inbox"

    private const val FOLDER = "picked"
    private const val MAX_AGE_MS = 24 * 60 * 60_000L
    private const val MS_PER_SECOND = 1_000.0

    /** A name `NSUUID().UUIDString` gives, alone or with an extension: what the builds before 28.09.2026 copied into tmp. */
    private val LEFTOVER = Regex("""[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}(\.[A-Za-z0-9]{1,10})?""")
}
