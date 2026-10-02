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

    /**
     * The same name for a video sent as it was shot: it keeps the container it was made in (spec 3.19) — a QuickTime movie
     * from the camera of an iPhone goes as «….mov», never as an `.mp4` it is not.
     */
    fun originalVideoFileName(title: String, videoName: String): String = fileName(title).removeSuffix(EXTENSION) + videoExtensionOf(videoName)

    /** «.mov» of «/…/sessions/A1.MOV»; [VIDEO_EXTENSION] when the last part of the path has none. */
    fun videoExtensionOf(name: String): String =
        name.substringAfterLast('/').substringAfterLast('.', "").lowercase().takeIf { it.isNotEmpty() && it.all(Char::isLetterOrDigit) }
            ?.let { ".$it" } ?: VIDEO_EXTENSION

    const val VIDEO_EXTENSION = ".mp4"

    /**
     * The name of a sound brought in from a file (spec 3.35, 5.28), sent as it was recorded (plan D48): it keeps the extension of its own
     * file — «Осенний концерт · 24 октября.mp3» of `<uuid>.sound.mp3`; a container that may hold a picture as well is named by its sound,
     * `.3gp` and `.3gpp` → `.3ga`, `.mp4` → `.m4a`, so the receiver takes it for sound. The sound of a take is `.m4a` and keeps it.
     */
    fun originalAudioFileName(title: String, audioName: String): String = fileName(title).removeSuffix(EXTENSION) + audioExtensionOf(audioName)

    /** «.mp3» of «/…/sessions/A1.sound.MP3»; [EXTENSION] when the last part of the path has none. */
    private fun audioExtensionOf(name: String): String {
        val extension = extensionOf(name) ?: return EXTENSION
        return "." + (SOUND_OF_CONTAINER[extension] ?: extension)
    }

    /** The extension of the last part of [name], in lowercase; null when it has none worth the name. */
    private fun extensionOf(name: String): String? =
        name.substringAfterLast('/').substringAfterLast('.', "").lowercase().takeIf { it.isNotEmpty() && it.all(Char::isLetterOrDigit) }

    /** Containers of sound that may hold a picture too, by the extension that says «sound» of them. */
    private val SOUND_OF_CONTAINER = mapOf("3gp" to "3ga", "3gpp" to "3ga", "mp4" to "m4a")

    /**
     * The type other apps are told a file is (Android's `Intent.type`), by the extension of the name it is sent under. Sound: the sound of
     * a take, and what «Звук из файла» brought in (plan D48) — mp3, wav and the rest; sent as any video, an mp3 would reach only the apps
     * that play pictures. Video: an `.mp4` made here, or a video shot on an iPhone and brought over by a copy (spec 3.20) — a QuickTime
     * `.mov`, which sent as `audio/mp4` would reach only the apps that play sound; any other extension — a video as it was shot. The words
     * of the name before its extension say nothing: a recording named «Final.sound.check» is still a video. What a recording without a
     * picture sends is sound whatever its extension — [soundTypeOf].
     */
    fun mimeTypeOf(name: String): String {
        val extension = extensionOf(name)
        extension?.let(AUDIO_TYPES::get)?.let { return it }
        return when (extension) {
            VIDEO_EXTENSION.removePrefix(".") -> VIDEO_TYPE
            QUICKTIME_EXTENSION -> QUICKTIME_TYPE
            else -> ANY_VIDEO_TYPE
        }
    }

    /**
     * The type of a file of sound sent under [name] (plan D48): by its extension where it is known here, else any sound ([ANY_AUDIO_TYPE]) — a recording
     * without a picture sends sound whatever its own file is, «….webm» or «….audio» of a sound brought in from a file, and never a video's
     * type. Decided by what the recording is, not by its name: the name is made of its title, which says nothing of it.
     */
    fun soundTypeOf(name: String): String = extensionOf(name)?.let(AUDIO_TYPES::get) ?: ANY_AUDIO_TYPE

    /** The sounds a recording may be: a take's own `.m4a`, and what «Звук из файла» brings in (spec 5.28). */
    private val AUDIO_TYPES = mapOf(
        EXTENSION.removePrefix(".") to "audio/mp4",
        "mp3" to "audio/mpeg",
        "wav" to "audio/wav",
        "aac" to "audio/aac",
        "ogg" to "audio/ogg",
        "opus" to "audio/opus",
        "flac" to "audio/flac",
        "3ga" to "audio/3gpp",
        "amr" to "audio/amr",
    )
    private const val ANY_AUDIO_TYPE = "audio/*"
    private const val VIDEO_TYPE = "video/mp4"
    private const val QUICKTIME_EXTENSION = "mov"
    private const val QUICKTIME_TYPE = "video/quicktime"

    /** Any other extension here is a video's as it was shot. */
    private const val ANY_VIDEO_TYPE = "video/*"
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
