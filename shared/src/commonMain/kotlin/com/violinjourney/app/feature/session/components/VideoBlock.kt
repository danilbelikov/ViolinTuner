package com.violinjourney.app.feature.session.components

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.video_description
import com.violinjourney.app.shared.resources.video_fullscreen
import com.violinjourney.app.shared.resources.video_state_paused
import com.violinjourney.app.shared.resources.video_state_playing
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.components.PlayPauseGlyph
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.AppShapes
import com.violinjourney.app.feature.practice.components.dashedFrame
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.session.VideoUi
import kotlinx.coroutines.delay


/** «На весь экран» on the picture (5.29 R5): an icon of 24 in a square of 48 on the glass, 8 from the corner. */
private val CornerButton = 48.dp
private val CornerButtonIcon = 24.dp
private val CornerButtonInset = 8.dp
private val PauseGlyphCircle = 64.dp

/** The picture's corner (5.29 R5): that of a card. */
internal val VideoCorner = 18.dp

// The dashed plate of what is not there (5.29 R5): a dashed frame of 1.5 at a corner of 14, fields 12 / 14, an icon of 24.
private val PlateCorner = 14.dp
private val PlatePaddingVertical = 12.dp
private val PlatePaddingHorizontal = 14.dp
private val PlateGap = 12.dp
private const val SPINNER_AFTER_MS = 300L
private const val GLYPH_IN_MS = 120
private const val GLYPH_HOLD_MS = 300L
private const val GLYPH_OUT_MS = 180


/**
 * The picture with the two things that lie on it (handoff 20d1, 20d5, 20d6; spec 3.36.5): a tap — pause or go on, a glyph shows for
 * a moment — and «на весь экран» in the corner, on the glass. No slider here: there is one, in the player. Until the first frame a
 * placeholder of the same proportions stands in; a spinner joins it only if that takes long.
 */
@Composable
fun VideoFrame(
    video: VideoUi,
    playing: Boolean,
    callbacks: VideoSurfaceCallbacks,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    corner: Dp = VideoCorner,
    /** Null inside the full screen, where the panel has «свернуть». */
    onFullscreen: (() -> Unit)? = null,
    /** Read where the button is drawn: the sticky video fades it with the scroll without being composed anew. */
    cornerButtonAlpha: () -> Float = { 1f },
    /**
     * The sound cannot be played yet — a take under a backing whose sound is being made (spec 5.25): the picture is dimmed and takes
     * no tap, «на весь экран» waits too; why it waits, the player at the bottom says — the spinner of the screen is there (3.36.5).
     */
    waiting: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val videoColors = ViolinTheme.videoColors
    var taps by remember { mutableIntStateOf(0) }
    val tapWords = stringResource(if (playing) Res.string.video_state_playing else Res.string.video_state_paused)
    val description = stringResource(Res.string.video_description)
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
        if (waiting) Box(Modifier.fillMaxSize().background(videoColors.scrim))
        if (onFullscreen != null && !waiting) {
            val label = stringResource(Res.string.video_fullscreen)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(CornerButtonInset)
                    .graphicsLayer { alpha = cornerButtonAlpha() }
                    .size(CornerButton)
                    .clip(AppShapes.S)
                    .background(ViolinTheme.glass)
                    .clickable(role = Role.Button, onClick = onFullscreen)
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) { AppIcon(AppIcons.Fullscreen, contentDescription = null, tint = colors.onSurface, size = CornerButtonIcon) }
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

/**
 * What is not there, said quietly where it would be (spec 3.36.5, 5.29 R5): a dashed plate first under the bar — «Запись без звука —
 * только разбор интонации» ([title] null: one line of 14 sp, the icon «инфо»), «Видео не найдено — остался разбор» and «Это видео
 * здесь не показать» with what that means under them (the icon of a video crossed out). Not a card: nothing broken, nothing to press.
 */
@Composable
fun DashedPlate(icon: ImageVector, text: String, modifier: Modifier = Modifier, title: String? = null) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .dashedFrame(colors.outlineVariant, PlateCorner)
            .padding(horizontal = PlatePaddingHorizontal, vertical = PlatePaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PlateGap),
    ) {
        AppIcon(icon, contentDescription = null, tint = colors.onSurfaceVariant)
        Column(modifier = Modifier.weight(1f)) {
            if (title == null) {
                Text(text, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp))
            } else {
                Text(title, color = colors.onSurface, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold))
                Text(text, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp))
            }
        }
    }
}
