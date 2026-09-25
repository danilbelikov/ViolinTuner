package com.violinjourney.app.core.audio.playback

import com.violinjourney.app.core.io.PlatformFile
import kotlinx.coroutines.flow.StateFlow

/** Where a picture is drawn: a `Surface` on Android, the layer of a player on iOS. */
expect class VideoSurfaceHandle

data class VideoState(
    /** As it is seen, the turn of the camera applied; zero until the file has been looked into. */
    val width: Int = 0,
    val height: Int = 0,
    /** There is a frame on the surface: the placeholder may go. */
    val showing: Boolean = false,
    /** No picture this device can decode. The sound and the analysis do not depend on it (spec 3.19). */
    val failed: Boolean = false,
)

/** The picture of one video take, as the screen's view model sees it; the real ones are the platform's. */
interface VideoPicture {
    val state: StateFlow<VideoState>

    /** Null takes the picture off the surface that is about to go. */
    fun setSurface(next: VideoSurfaceHandle?)

    /** Where the sound is, and whether it moves. */
    fun follow(positionMs: Long, playing: Boolean)

    fun release()
}

fun interface VideoPictureFactory {
    fun create(file: PlatformFile): VideoPicture
}
