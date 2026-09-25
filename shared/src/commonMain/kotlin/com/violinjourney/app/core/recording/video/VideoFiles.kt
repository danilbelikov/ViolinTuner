package com.violinjourney.app.core.recording.video

import com.violinjourney.app.core.io.PlatformFile
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/** What a video says about itself before anyone listens to it. */
data class VideoInfo(
    val durationMs: Long,
    val width: Int,
    val height: Int,
    /** When it was shot, by its own metadata; null when the file does not say. */
    val createdAtEpochMs: Long?,
    val hasSound: Boolean,
)

/**
 * Videos of takes (spec 3.19). They live beside the sound of sessions — `files/sessions/<uuid>.mp4`
 * with `<uuid>-thumb.jpg` — because for a video take the file *is* the sound: the session's
 * `audioPath` names it. A shot comes through `cache/camera/`, where the system camera may write.
 */
interface VideoFiles {
    /** Where the system camera is to write a shot; the folder is open to the FileProvider. */
    fun newCameraFile(): PlatformFile

    /** Moves a finished shot out of the cache at once (the system may clear a cache when space runs short). Null when it cannot. */
    fun adopt(cameraFile: PlatformFile): PlatformFile?

    /** Size of what [uri] points at, when the provider tells. */
    fun sizeOf(uri: String): Long?

    fun freeBytes(): Long

    /** Copies the picked video in, whole and unchanged; null when it cannot be read or written. Cancellable. */
    suspend fun import(uri: String): PlatformFile?

    fun info(file: PlatformFile): VideoInfo?

    /** Writes the thumbnail beside [file]; false leaves the take without one. */
    fun makeThumb(file: PlatformFile): Boolean

    /** The thumbnail of the video stored under [name], if there is one. */
    fun thumbOf(name: String): PlatformFile?

    fun existing(name: String): PlatformFile?

    /** Removes the video and its thumbnail: a shot the player gave up, an import that failed. */
    fun discard(file: PlatformFile)
}

/** `METADATA_KEY_DATE` of a video: «20260918T101500.000Z», always UTC. Pure, for the sake of a test. */
object VideoDates {
    private val format = Regex("""(\d{4})(\d{2})(\d{2})T(\d{2})(\d{2})(\d{2})(?:\.(\d{3}))?Z""")

    // Files that do not know their date say 1904-01-01 (the zero of the QuickTime epoch) — that is "no date", not a date.
    private const val EARLIEST_BELIEVABLE_EPOCH_MS = 946_684_800_000L // 2000-01-01
    private const val NANOS_PER_MILLI = 1_000_000

    private operator fun <T> List<T>.component6(): T = this[5]

    fun parse(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        val parts = format.matchEntire(text.trim())?.groupValues?.drop(1) ?: return null
        val millis = try {
            val (year, month, day, hour, minute, second) = parts.take(6).map(String::toInt)
            LocalDateTime(year, month, day, hour, minute, second, (parts[6].ifEmpty { "0" }.toInt()) * NANOS_PER_MILLI)
                .toInstant(TimeZone.UTC).toEpochMilliseconds()
        } catch (_: IllegalArgumentException) {
            return null // a month 13, a day 31 of April: not a date
        }
        return millis.takeIf { it >= EARLIEST_BELIEVABLE_EPOCH_MS }
    }
}
