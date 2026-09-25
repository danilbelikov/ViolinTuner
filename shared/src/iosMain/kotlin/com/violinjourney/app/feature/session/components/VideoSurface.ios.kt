package com.violinjourney.app.feature.session.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import com.violinjourney.app.core.audio.playback.PictureView
import com.violinjourney.app.core.audio.playback.VideoSurfaceHandle

@Composable
actual fun VideoSurface(callbacks: VideoSurfaceCallbacks, modifier: Modifier) {
    val current by rememberUpdatedState(callbacks)
    val handle = remember { VideoSurfaceHandle(PictureView()) }
    UIKitView(
        factory = { handle.view.also { current.onSurface(handle) } },
        modifier = modifier,
        onRelease = { current.onSurfaceGone(handle) },
    )
}
