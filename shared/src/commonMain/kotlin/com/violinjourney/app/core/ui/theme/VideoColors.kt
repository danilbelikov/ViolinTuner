package com.violinjourney.app.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colors of video takes (spec 3.19, handoff `Видео`, `tokens`). [field] is the hex of the music
 * stand's background — but a token of its own: a stand is about sheets. A file large enough for a
 * messenger to squeeze is said in the colour of danger of the redesign (`ViolinTheme.dangerSoft`,
 * spec 3.36.5), no longer in a token here with the hex of "near": green, amber and red mean
 * intonation everywhere in this app, not "a big file".
 */
@Immutable
data class VideoColors(
    /** The margins beside a tall video, and the full screen around it. */
    val field: Color,
    /** Under what lies on the picture itself: the full-screen button, the pause glyph, the note strip. */
    val scrim: Color,
    /** The full screen around a wide video. */
    val fullBackground: Color,
    /** The camera badge on the tile of a video take. */
    val badge: Color,
)

internal val DarkVideoColors = VideoColors(
    field = VideoField,
    scrim = VideoScrim,
    fullBackground = Color.Black,
    badge = SurfaceContainerHigh,
)

internal val LocalVideoColors = staticCompositionLocalOf<VideoColors> {
    error("VideoColors not provided: wrap content in ViolinTheme")
}
