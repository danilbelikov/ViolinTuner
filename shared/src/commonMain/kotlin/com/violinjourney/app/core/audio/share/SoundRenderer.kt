package com.violinjourney.app.core.audio.share

import com.violinjourney.app.core.domain.sound.SoundSettings
import com.violinjourney.app.core.io.PlatformFile

/** Renders a recording through the chain into a file to be sent (spec 3.17): what was heard in the app is what lands in it. */
interface SoundRenderer {
    /**
     * Renders [source] through the chain set to [settings] into [target]. True when the file is
     * whole; false when it could not be made — [target] is then gone. Cancellable: a cancelled
     * render leaves no file either. [onProgress] gets 0…1, from whatever thread renders.
     */
    suspend fun render(source: PlatformFile, settings: SoundSettings, target: PlatformFile, onProgress: (Float) -> Unit): Boolean

    /**
     * The same for a video take (spec 3.19): [target] is an `.mp4` with the picture of [source]
     * copied as it is — not re-encoded, its turn kept — beside the sound rendered through the chain.
     */
    suspend fun renderVideo(source: PlatformFile, settings: SoundSettings, target: PlatformFile, onProgress: (Float) -> Unit): Boolean

    /** [render] with the backing the take was made under mixed in (spec 3.32): a stereo `.m4a`. */
    suspend fun renderWithBacking(source: PlatformFile, settings: SoundSettings, backing: RenderBacking, target: PlatformFile, onProgress: (Float) -> Unit): Boolean = false

    /** [renderVideo] with the backing mixed into its sound. */
    suspend fun renderVideoWithBacking(source: PlatformFile, settings: SoundSettings, backing: RenderBacking, target: PlatformFile, onProgress: (Float) -> Unit): Boolean = false

    companion object {
        /** AAC-LC mono for the file that is sent (spec 5.11). */
        const val BIT_RATE = 128_000

        /** With the backing: stereo, and a bit rate to carry both sides (spec 5.25). */
        const val STEREO_BIT_RATE = 192_000
    }
}

/** A backing for the file that is sent: its sound prepared at the recording's rate, and how it is mixed. */
class RenderBacking(val pcm: (sampleRate: Int) -> PlatformFile?, val offsetMs: Int, val gainDb: Float)
