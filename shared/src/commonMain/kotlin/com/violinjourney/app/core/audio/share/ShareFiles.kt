package com.violinjourney.app.core.audio.share

import com.violinjourney.app.core.domain.sound.SoundSettings
import com.violinjourney.app.core.io.PlatformFile

/** Names of files as other apps will see them. Pure. */
object ShareNames {
    const val EXTENSION = ".m4a"

    /** Characters — whole ones: an emoji is two UTF-16 units and is never cut in half. */
    private const val MAX_LENGTH = 80

    /**
     * Bytes of UTF-8: a name on ext4, f2fs and APFS is at most 255 of them, and the longest name made of this one is a
     * video's sound on its way, `<name>.mp4.part.sound.m4a` — 80 characters of Chinese alone would be 240 bytes.
     */
    private const val MAX_BYTES = 200
    private val FORBIDDEN = Regex("""[\\/:*?"<>|\p{Cntrl}]""")
    private val SPACES = Regex("""\s+""")

    /** [title] made fit to be a file name anywhere: «Соната № 1: Allegro» → «Соната № 1 Allegro.m4a». */
    fun fileName(title: String): String {
        val clean = fitted(title.replace(FORBIDDEN, " ").replace(SPACES, " ").trim().trim('.')).trim()
        return (clean.ifEmpty { "recording" }) + EXTENSION
    }

    /** The head of [text] that fits [MAX_LENGTH] characters and [MAX_BYTES] bytes of UTF-8, cut between characters. */
    private fun fitted(text: String): String {
        var end = 0
        var characters = 0
        var bytes = 0
        while (end < text.length && characters < MAX_LENGTH) {
            val pair = text[end].isHighSurrogate() && end + 1 < text.length && text[end + 1].isLowSurrogate()
            val width = if (pair) 2 else 1
            bytes += if (pair) UTF8_OF_PAIR else utf8Bytes(text[end])
            if (bytes > MAX_BYTES) break
            end += width
            characters++
        }
        return text.substring(0, end)
    }

    /** A lone surrogate is written as the replacement character, three bytes. */
    private fun utf8Bytes(char: Char): Int = when {
        char.code < 0x80 -> 1
        char.code < 0x800 -> 2
        else -> 3
    }

    private const val UTF8_OF_PAIR = 4

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

    /** A copy of [audio] under [fileName]; null when it cannot be made. The copy counts as handed over ([handedOver]). */
    suspend fun original(audio: PlatformFile, fileName: String): PlatformFile?

    /**
     * [file] goes to a receiver now: its folder is the newest one, which [ShareSweep] never takes by weight, and its age
     * starts again — a file prepared long ago and sent once more is not swept from under the receiver. The folder, not
     * the file: a hard link shares its time with the take itself.
     */
    suspend fun handedOver(file: PlatformFile)

    /** Throws out what nobody will read any more; what that is, [ShareSweep] decides. */
    suspend fun sweep(nowEpochMs: Long)
}
