package com.violinjourney.app.ios

import com.violinjourney.app.core.audio.share.ShareFiles
import com.violinjourney.app.core.audio.share.ShareFolder
import com.violinjourney.app.core.audio.share.ShareNames
import com.violinjourney.app.core.audio.share.ShareSweep
import com.violinjourney.app.core.data.sound.SoundMapper
import com.violinjourney.app.core.domain.sound.SoundSettings
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.fileName
import com.violinjourney.app.core.io.sizeBytes
import com.violinjourney.app.core.time.WallClock
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.feature.share.ShareTexts
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.session_default_title
import com.violinjourney.app.shared.resources.session_take_title
import com.violinjourney.app.shared.resources.share_message
import com.violinjourney.app.shared.resources.share_message_session
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileReferenceCount
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/**
 * Where files wait to be handed to other apps on iOS, as on Android: `Caches/share/<key>/<name the receiver sees>`,
 * a processed file kept under a key of its recording and its settings, swept by [ShareSweep].
 */
@OptIn(ExperimentalForeignApi::class)
internal class IosShareFiles(private val io: CoroutineDispatcher) : ShareFiles {
    private val files = NSFileManager.defaultManager
    private val directory by lazy {
        val caches = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first() as String
        "$caches/$DIRECTORY".also { files.createDirectoryAtPath(it, true, null, null) }
    }

    override fun processed(audioName: String, settings: SoundSettings, fileName: String): PlatformFile {
        // columns, not the settings themselves: the hash of an enum differs from run to run, that of its name does not
        val key = "${audioName.removeSuffix(ShareNames.EXTENSION)}-${SoundMapper.columnsOf(settings).hashCode().toUInt().toString(HEX)}"
        files.createDirectoryAtPath("$directory/$key", true, null, null)
        return PlatformFile("$directory/$key/$fileName")
    }

    override suspend fun original(audio: PlatformFile, fileName: String): PlatformFile? = withContext(io) {
        val folder = "$directory/${audio.fileName.removeSuffix(ShareNames.EXTENSION)}-original"
        val target = PlatformFile("$folder/$fileName")
        if (target.sizeBytes() != audio.sizeBytes()) {
            files.createDirectoryAtPath(folder, true, null, null)
            files.removeItemAtPath(target.path, null)
            // a second name for the same bytes: a video is not copied for the sake of a pretty name
            val done = files.linkItemAtPath(audio.path, target.path, null) || files.copyItemAtPath(audio.path, target.path, null)
            if (!done) return@withContext null
        }
        target
    }

    override suspend fun sweep(nowEpochMs: Long) = withContext(io) {
        val names = files.contentsOfDirectoryAtPath(directory, null).orEmpty().mapNotNull { it as? String }
        ShareSweep.toDelete(names.map(::look), nowEpochMs).forEach { files.removeItemAtPath("$directory/$it", null) }
    }

    private fun look(name: String): ShareFolder {
        val folder = "$directory/$name"
        val inside = files.contentsOfDirectoryAtPath(folder, null).orEmpty().mapNotNull { it as? String }.map { "$folder/$it" }
        val entries = inside + folder
        return ShareFolder(
            name = name,
            // a hard link costs nothing: those very bytes are already counted with the sessions
            bytes = inside.sumOf { path ->
                val links = (files.attributesOfItemAtPath(path, null)?.get(NSFileReferenceCount) as? NSNumber)?.longValue ?: 1
                if (links > 1) 0L else PlatformFile(path).sizeBytes()
            },
            touchedAtEpochMs = entries.maxOf { IosFolders.modifiedMs(it) },
            heavy = inside.any { path -> HEAVY.any { path.endsWith(it, ignoreCase = true) } },
        )
    }

    private companion object {
        const val DIRECTORY = "share"
        const val HEX = 16
        val HEAVY = listOf(ShareNames.VIDEO_EXTENSION, ".mov", ".zip")
    }
}

/**
 * The names and the message of a shared recording on iOS, the words of the screens (as `AppShareTexts` on Android).
 * The view model asks without suspending: the templates are read once, in the language of the interface.
 */
internal class IosShareTexts private constructor(private val templates: Map<String, String>, private val clock: WallClock) : ShareTexts {
    override fun title(title: String?, pieceTitle: String?, startedAtEpochMs: Long): String {
        val date = Formats.dayAndMonth(startedAtEpochMs, clock.zone)
        return title
            ?: pieceTitle?.let { IosScaleTexts.format(templates.getValue(TAKE), arrayOf(it, date)) }
            ?: IosScaleTexts.format(templates.getValue(DEFAULT), arrayOf(date))
    }

    override fun message(title: String?, pieceTitle: String?, scorePercent: Int, startedAtEpochMs: Long): String = IosScaleTexts.format(
        templates.getValue(MESSAGE),
        arrayOf(title ?: pieceTitle ?: templates.getValue(SESSION), scorePercent, Formats.dayAndMonth(startedAtEpochMs, clock.zone)),
    )

    companion object {
        private const val TAKE = "take"
        private const val DEFAULT = "default"
        private const val MESSAGE = "message"
        private const val SESSION = "session"

        suspend fun load(clock: WallClock) = IosShareTexts(
            mapOf(
                TAKE to getString(Res.string.session_take_title),
                DEFAULT to getString(Res.string.session_default_title),
                MESSAGE to getString(Res.string.share_message),
                SESSION to getString(Res.string.share_message_session),
            ),
            clock,
        )
    }
}
