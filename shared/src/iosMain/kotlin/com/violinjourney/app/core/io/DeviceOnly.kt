package com.violinjourney.app.core.io

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.numberWithBool

/**
 * What stays on this iPhone alone — the counterpart of Android's `backup_rules.xml` (spec 5.14). The backup of the phone
 * (iCloud or a computer) takes all of Application Support, and the person's data belong in it: it has no limit per app and
 * is what brings them to a new iPhone. What the app makes again or holds only for a while is marked out of it — the
 * waveforms, the shots of the camera on their way in, the unpacked copy of a restore and its marks.
 *
 * A mark on a folder keeps everything in it out; what is moved out of the folder — the folders of a restored copy — is
 * backed up as usual, the mark stays with the folder. A mark that cannot be set leaves the item in the backup, as it was
 * before there were marks: nothing is lost by it.
 */
@OptIn(ExperimentalForeignApi::class)
internal object DeviceOnly {
    /** Keeps the file or folder at [path] out of the backup of the phone; false when the system would not. */
    fun mark(path: String): Boolean =
        NSURL.fileURLWithPath(path).setResourceValue(NSNumber.numberWithBool(true), forKey = NSURLIsExcludedFromBackupKey, error = null)

    fun isMarked(path: String): Boolean =
        (NSURL.fileURLWithPath(path).resourceValuesForKeys(listOf(NSURLIsExcludedFromBackupKey), error = null)
            ?.get(NSURLIsExcludedFromBackupKey) as? NSNumber)?.boolValue == true
}
