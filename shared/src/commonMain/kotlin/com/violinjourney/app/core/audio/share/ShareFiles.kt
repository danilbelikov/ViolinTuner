package com.violinjourney.app.core.audio.share

import com.violinjourney.app.core.domain.sound.SoundSettings
import com.violinjourney.app.core.io.PlatformFile

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
    fun processed(audioName: String, settings: SoundSettings, fileName: String): PlatformFile

    /** A copy of [audio] under [fileName]; null when it cannot be made. */
    suspend fun original(audio: PlatformFile, fileName: String): PlatformFile?

    /** Throws out what nobody will read any more; what that is, [ShareSweep] decides. */
    suspend fun sweep(nowEpochMs: Long)
}
