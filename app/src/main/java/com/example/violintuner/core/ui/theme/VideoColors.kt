package com.example.violintuner.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colors of video takes (spec 3.19, handoff `Видео`, `tokens`). [field] is the hex of the music
 * stand's background and [sizeWarn] that of "near" — but tokens of their own: a stand is about
 * sheets, and green, amber and red mean intonation everywhere in this app, not "a big file".
 */
@Immutable
data class VideoColors(
    /** The margins beside a tall video, and the full screen around it. */
    val field: Color,
    /** Under what lies on the picture itself: the full-screen button, the pause glyph, the note strip. */
    val scrim: Color,
    /** The darkest end of the gradients behind the full-screen panel. */
    val panel: Color,
    /** The full screen around a wide video. */
    val fullBackground: Color,
    /** The camera badge on the tile of a video take. */
    val badge: Color,
    /** A file large enough for a messenger to squeeze or refuse. */
    val sizeWarn: Color,
)

internal val DarkVideoColors = VideoColors(
    field = VideoField,
    scrim = VideoScrim,
    panel = VideoPanel,
    fullBackground = Color.Black,
    badge = SurfaceContainerHigh,
    sizeWarn = VideoSizeWarn,
)

internal val LocalVideoColors = staticCompositionLocalOf<VideoColors> {
    error("VideoColors not provided: wrap content in ViolinTheme")
}
