package com.violinjourney.app.feature.session.components

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * The one View of the app (spec 3.19): a decoder draws onto a surface, and a surface that lives
 * inside a scrolling, shrinking Compose layout is a `TextureView`. It is sized to the proportions
 * of the video from outside, so the picture is never stretched.
 */
@Composable
actual fun VideoSurface(callbacks: VideoSurfaceCallbacks, modifier: Modifier) {
    val current by rememberUpdatedState(callbacks)
    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextureView(context).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    private var surface: Surface? = null

                    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                        surface = Surface(texture).also { current.onSurface(it) }
                    }

                    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                        // the decoder lets go first, then the surface may die
                        surface?.let {
                            current.onSurfaceGone(it)
                            it.release()
                        }
                        surface = null
                        return true
                    }

                    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit

                    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                }
            }
        },
    )
}
