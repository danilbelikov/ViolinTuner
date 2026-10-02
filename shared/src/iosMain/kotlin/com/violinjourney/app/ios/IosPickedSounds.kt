package com.violinjourney.app.ios

import com.violinjourney.app.core.backup.DataLayout
import com.violinjourney.app.core.io.PickedCopies
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.availableBytes
import com.violinjourney.app.core.io.pathOfFileUri
import com.violinjourney.app.core.recording.audio.PickedSounds
import com.violinjourney.app.core.recording.audio.SoundProbe
import kotlin.math.roundToLong
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.creationDate
import platform.AVFoundation.dateValue
import platform.AVFoundation.duration
import platform.AVFoundation.tracksWithMediaType
import platform.CoreMedia.CMTimeGetSeconds
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.timeIntervalSince1970

/**
 * Sound files picked in Files for a recording of an event on iOS (spec 3.35, 5.28), where Android keeps them: `sessions/<uuid>.sound.<ext>`
 * beside the sound of the recordings. The picker hands over the app's own copy in `tmp/picked/` under the file's own name ([PickedCopies],
 * `copyKeepingName`); it is moved in, and its folder goes once it is in or will not come in. Looked into by AVFoundation — the analysis
 * reads it with `AVAssetReader` (`IosPcmFileOpener`): a file the one does not open the other does not either. Its date: the one it says
 * of itself (`creationDate`), else that of the copy, which keeps its original's.
 */
@OptIn(ExperimentalForeignApi::class)
internal class IosPickedSounds(private val io: CoroutineDispatcher) : PickedSounds {
    private val directory by lazy { IosFolders.folder(DataLayout.SESSIONS) }
    private val files = NSFileManager.defaultManager

    override fun sizeOf(uri: String): Long? = pathOfFileUri(uri)?.let { path -> (files.attributesOfItemAtPath(path, null)?.get(NSFileSize) as? NSNumber)?.longLongValue }

    // what iOS gives a write the person asked for, what it frees on demand included
    override fun freeBytes(): Long = PlatformFile(directory).availableBytes()

    override fun probe(uri: String): SoundProbe? {
        val path = pathOfFileUri(uri) ?: return null
        val asset = AVURLAsset(uRL = NSURL.fileURLWithPath(path), options = null)
        val seconds = CMTimeGetSeconds(asset.duration)
        // a duration that is not a number: nothing AVFoundation can read
        if (seconds.isNaN() || seconds < 0) return null
        val modified = (files.attributesOfItemAtPath(path, null)?.get(NSFileModificationDate) as? NSDate)?.timeIntervalSince1970
        return SoundProbe(
            durationMs = (seconds * MS_PER_SECOND).roundToLong(),
            hasSound = asset.tracksWithMediaType(AVMediaTypeAudio).isNotEmpty(),
            createdAtEpochMs = asset.creationDate?.dateValue?.timeIntervalSince1970?.let { (it * MS_PER_SECOND).roundToLong() },
            modifiedAtEpochMs = modified?.let { (it * MS_PER_SECOND).roundToLong() },
        )
    }

    override suspend fun import(uri: String): PlatformFile? = withContext(io) {
        val source = pathOfFileUri(uri) ?: return@withContext null
        try {
            val target = "$directory/${NSUUID().UUIDString}${PickedSounds.MARK}${extensionOf(source)}"
            // the picker's copy is ours already: it moves in; anything else is copied whole
            val done = IosFolders.move(source, target) || files.copyItemAtPath(source, target, null)
            if (done) PlatformFile(target) else null
        } finally {
            // moved in, the folder of the pick is left empty; copied or not taken at all, the copy goes with it
            PickedCopies.release(source)
        }
    }

    // a copy of the picker that will not come in: maybe hundreds of megabytes
    override fun release(uri: String) {
        pathOfFileUri(uri)?.let { PickedCopies.release(it) }
    }

    override fun discard(file: PlatformFile) = IosFolders.delete(file.path)

    // of the name, not of the path: `…/<uuid>.debug-Inbox/clip` has a dot only in its folder
    private fun extensionOf(path: String) =
        path.substringAfterLast('/').substringAfterLast('.', "").lowercase().filter(Char::isLetterOrDigit).take(MAX_EXTENSION).ifEmpty { FALLBACK_EXTENSION }

    private companion object {
        const val MS_PER_SECOND = 1_000.0
        const val MAX_EXTENSION = 5
        const val FALLBACK_EXTENSION = "audio"
    }
}
