package com.violinjourney.app.feature.session.components

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.violinjourney.app.R
import com.violinjourney.app.core.ui.components.PlayPauseGlyph
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.session.VideoUi
import kotlinx.coroutines.delay

/** Where a video's surface goes: to the view model, which hands it to the decoder. */
class VideoSurfaceCallbacks(val onSurface: (Surface) -> Unit, val onSurfaceGone: (Surface) -> Unit)

private val CornerButton = 40.dp
private val PauseGlyphCircle = 64.dp
private const val SPINNER_AFTER_MS = 300L
private const val GLYPH_IN_MS = 120
private const val GLYPH_HOLD_MS = 300L
private const val GLYPH_OUT_MS = 180

/**
 * The one View of the app (spec 3.19): a decoder draws onto a surface, and a surface that lives
 * inside a scrolling, shrinking Compose layout is a `TextureView`. It is sized to the proportions
 * of the video from outside, so the picture is never stretched.
 */
@Composable
fun VideoSurface(callbacks: VideoSurfaceCallbacks, modifier: Modifier = Modifier) {
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

/**
 * The picture with the two things that lie on it (handoff 20d1, 20d5, 20d6): a tap — pause or go
 * on, a glyph shows for a moment — and «на весь экран» in the corner. No slider here: there is
 * one, in the player. Until the first frame a placeholder of the same proportions stands in; a
 * spinner joins it only if that takes long.
 */
@Composable
fun VideoFrame(
    video: VideoUi,
    playing: Boolean,
    callbacks: VideoSurfaceCallbacks,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    corner: Dp = 16.dp,
    /** Null inside the full screen, where the panel has «свернуть». */
    onFullscreen: (() -> Unit)? = null,
    cornerButtonAlpha: Float = 1f,
    /**
     * The sound cannot be played yet — a take under a backing whose sound is being made (spec 5.25): the picture is
     * greyed, says why, and takes no tap; «на весь экран» waits too.
     */
    waiting: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val videoColors = ViolinTheme.videoColors
    var taps by remember { mutableIntStateOf(0) }
    val tapWords = stringResource(if (playing) R.string.video_state_playing else R.string.video_state_paused)
    val description = stringResource(R.string.video_description)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(colors.surfaceContainer)
            .clickable(enabled = !waiting, interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                taps++
                onTap()
            }
            .semantics {
                contentDescription = description
                stateDescription = tapWords
            },
        contentAlignment = Alignment.Center,
    ) {
        VideoSurface(callbacks, Modifier.fillMaxSize())
        if (!video.showing) {
            var slow by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(SPINNER_AFTER_MS)
                slow = true
            }
            Box(Modifier.fillMaxSize().background(colors.surfaceContainer), contentAlignment = Alignment.Center) {
                if (slow) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = colors.onSurfaceVariant, strokeWidth = 2.dp)
            }
        }
        TapGlyph(taps, playing)
        if (waiting) {
            Column(
                modifier = Modifier.fillMaxSize().background(videoColors.scrim),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), color = Color.White, strokeWidth = 2.5.dp)
                Text(
                    stringResource(R.string.backing_preparing),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        if (onFullscreen != null && !waiting) {
            val label = stringResource(R.string.video_fullscreen)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .graphicsLayer { alpha = cornerButtonAlpha }
                    .size(CornerButton)
                    .clip(CircleShape)
                    .background(videoColors.scrim)
                    .clickable(role = Role.Button, onClick = onFullscreen)
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) { AppIcon(AppIcons.Fullscreen, contentDescription = null, tint = Color.White, size = 22.dp) }
        }
    }
}

/** What the tap did, for a moment: it comes in, holds and fades — 600 ms in all, never a blink. */
@Composable
private fun TapGlyph(taps: Int, playing: Boolean) {
    val still = LocalReduceMotion.current
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    LaunchedEffect(taps) {
        if (taps == 0) return@LaunchedEffect
        if (still) {
            alpha.snapTo(1f)
        } else {
            scale.snapTo(GLYPH_SCALE_FROM)
            alpha.animateTo(1f, tween(GLYPH_IN_MS))
        }
        scale.snapTo(1f)
        delay(GLYPH_HOLD_MS)
        alpha.animateTo(0f, tween(GLYPH_OUT_MS))
    }
    Box(
        modifier = Modifier
            .graphicsLayer {
                this.alpha = alpha.value
                scaleX = scale.value
                scaleY = scale.value
            }
            .size(PauseGlyphCircle)
            .background(ViolinTheme.videoColors.scrim, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        // what the tap has led to: playing shows the play sign going, paused the bars
        PlayPauseGlyph(playing = !playing, tint = Color.White, size = 28.dp)
    }
}

private const val GLYPH_SCALE_FROM = 0.8f

/** «Видео не найдено — остался разбор» and «Это видео здесь не показать»: a line where the picture would be (handoff 20d7). */
@Composable
fun VideoMissingRow(title: String, text: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(AppIcons.VideoOff, contentDescription = null, tint = colors.onSurfaceVariant)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(title, color = colors.onSurface, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
            Text(text, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp))
        }
    }
}
