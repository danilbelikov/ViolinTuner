package com.violinjourney.app.feature.session.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.violinjourney.app.core.audio.playback.VideoSurfaceHandle

/** Where a video's surface goes: to the view model, which hands it to the decoder. */
class VideoSurfaceCallbacks(val onSurface: (VideoSurfaceHandle) -> Unit, val onSurfaceGone: (VideoSurfaceHandle) -> Unit)

/** The place of the picture in the layout (spec 3.19): the platform's view, sized to the proportions of the video from outside. */
@Composable
expect fun VideoSurface(callbacks: VideoSurfaceCallbacks, modifier: Modifier = Modifier)
