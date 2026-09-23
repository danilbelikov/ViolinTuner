package com.violinjourney.app.core.audio.share

import android.content.Context
import android.system.ErrnoException
import android.system.Os
import android.util.Log
import com.violinjourney.app.core.data.sound.SoundMapper
import com.violinjourney.app.core.di.IoDispatcher
import com.violinjourney.app.core.domain.sound.SoundSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Names of files as other apps will see them. Pure. */
object ShareNames {
    const val EXTENSION = ".m4a"
    private const val MAX_LENGTH = 80
    private val FORBIDDEN = Regex("""[\\/:*?"<>|\p{Cntrl}]""")
    private val SPACES = Regex("""\s+""")

    /** [title] made fit to be a file name anywhere: «Соната № 1: Allegro» → «Соната № 1 Allegro.m4a». */
    fun fileName(title: String): String {
        val clean = title.replace(FORBIDDEN, " ").replace(SPACES, " ").trim().trim('.').take(MAX_LENGTH).trim()
        return (clean.ifEmpty { "recording" }) + EXTENSION
    }

    /** The same name for the video of a take (spec 3.19): «Менуэт соль мажор · 18 сентября.mp4». */
    fun videoFileName(title: String): String = fileName(title).removeSuffix(EXTENSION) + VIDEO_EXTENSION

    const val VIDEO_EXTENSION = ".mp4"
}

/**
 * Where files wait to be handed to other apps: `cache/share/<key>/<name as the receiver sees it>`.
 * The receiver is shown the name of the file on disk, hence the folder per file. A processed file
 * is kept under a key of its recording and its settings, so that the same sound is not rendered
 * twice; everything here is temporary and swept out by age and by weight ([ShareSweep]).
 */
interface ShareFiles {
    /** Where the processed file of [audioName] with [settings] lives — or will. */
    fun processed(audioName: String, settings: SoundSettings, fileName: String): File

    /** A copy of [audio] under [fileName]; null when it cannot be made. */
    suspend fun original(audio: File, fileName: String): File?

    /** Throws out what nobody will read any more; what that is, [ShareSweep] decides. */
    suspend fun sweep(nowEpochMs: Long)
}

class AppShareFiles @Inject constructor(
    @ApplicationContext context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ShareFiles {
    private val directory = File(context.cacheDir, DIRECTORY)

    override fun processed(audioName: String, settings: SoundSettings, fileName: String): File {
        // Columns, not the settings themselves: the hash of an enum differs from run to run, that of its name does not.
        val key = "${audioName.removeSuffix(ShareNames.EXTENSION)}-${SoundMapper.columnsOf(settings).hashCode().toUInt().toString(HEX)}"
        return File(File(directory, key), fileName)
    }

    override suspend fun original(audio: File, fileName: String): File? = withContext(io) {
        val target = File(File(directory, "${audio.name.removeSuffix(ShareNames.EXTENSION)}-original"), fileName)
        try {
            if (!target.isFile || target.length() != audio.length()) {
                target.parentFile?.mkdirs()
                target.delete()
                // A second name for the same bytes: a video of two hundred megabytes is not copied for the
                // sake of a pretty name. Cache and files are one volume; where they are not, it is a copy after all.
                try {
                    Os.link(audio.path, target.path)
                } catch (e: ErrnoException) {
                    Log.i(TAG, "no hard link (${e.message}), copying")
                    audio.copyTo(target, overwrite = true)
                }
            }
            target
        } catch (e: IOException) {
            target.delete()
            null
        }
    }

    override suspend fun sweep(nowEpochMs: Long) = withContext(io) {
        val folders = directory.listFiles().orEmpty().associateBy { it.name }
        ShareSweep.toDelete(folders.values.map(::look), nowEpochMs).forEach { folders.getValue(it).deleteRecursively() }
    }

    /** What the sweep is told about a folder. A folder gone under our feet looks ancient and is deleted — that is, nothing happens. */
    private fun look(folder: File): ShareFolder {
        val entries = folder.walkTopDown().toList()
        return ShareFolder(
            name = folder.name,
            bytes = entries.filter { it.isFile }.sumOf(::weightOf),
            touchedAtEpochMs = entries.maxOf { it.lastModified() },
            heavy = entries.any { entry -> HEAVY.any { entry.name.endsWith(it, ignoreCase = true) } },
        )
    }

    /** A hard link (see [original]) costs nothing: those very bytes are already counted under `files/`. */
    private fun weightOf(file: File): Long = try {
        if (Os.stat(file.path).st_nlink > 1) 0 else file.length()
    } catch (e: ErrnoException) {
        Log.i(TAG, "cannot weigh ${file.name} (${e.message})")
        file.length()
    }

    private companion object {
        const val TAG = "ShareFiles"
        const val DIRECTORY = "share"
        const val HEX = 16

        /** What is as heavy as the thing it was made from: the picture of a take, all the data at once. */
        val HEAVY = listOf(ShareNames.VIDEO_EXTENSION, ".zip")
    }
}
