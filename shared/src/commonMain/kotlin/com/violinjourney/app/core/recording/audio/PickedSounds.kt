package com.violinjourney.app.core.recording.audio

import com.violinjourney.app.core.io.PlatformFile

/** What a picked sound file says about itself before anyone listens to it (spec 5.28; plan D15, D18). */
data class SoundProbe(
    val durationMs: Long,
    /** A track of sound this phone can decode: a file without one is said so, not analysed. */
    val hasSound: Boolean,
    /** When it was recorded, by its own metadata; null when the file does not say. */
    val createdAtEpochMs: Long?,
    /** When the file was last changed, as its provider tells it; null when it does not. */
    val modifiedAtEpochMs: Long?,
)

/**
 * Sound files picked for a recording of an event («Звук из файла», spec 3.35, 5.28) and what the platform holds of them. A file comes in
 * whole and unchanged, beside the sound of the recordings, as `<uuid>.sound.<its extension>` — the name tells a copy of the data that
 * it is sound, whatever its extension (plan D17). On Android the picker lends a content uri; on iOS the picker of Files hands over the
 * app's own copy in `tmp/picked/` (`PickedCopies`), which goes once it is in or will not come in.
 */
interface PickedSounds {
    /** Size of what [uri] points at, when the provider tells. Blocking. */
    fun sizeOf(uri: String): Long?

    /** The room for recordings. Blocking, never on the main thread: iOS counts in it what it would free on demand. */
    fun freeBytes(): Long

    /** Looks into the picked file with the decoder of the platform; null when it cannot be opened at all. Blocking. */
    fun probe(uri: String): SoundProbe?

    /** Copies the picked file in, whole and unchanged; null when it cannot be read or written. Cancellable. */
    suspend fun import(uri: String): PlatformFile?

    /** [uri] was picked but will not come in — refused, failed, stopped, or not wanted now: what the platform holds of it goes. */
    fun release(uri: String)

    /** Removes the copy of a file that did not become a recording. */
    fun discard(file: PlatformFile)

    companion object {
        /** In the name of a copy: `<uuid>.sound.mp3` is sound, whatever its extension (plan D17). */
        const val MARK = ".sound."
    }
}
