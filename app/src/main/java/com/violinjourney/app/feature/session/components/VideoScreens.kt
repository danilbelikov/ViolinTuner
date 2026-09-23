package com.violinjourney.app.feature.session.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.R
import com.violinjourney.app.core.audio.playback.PlayerState
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.session.SessionContent
import com.violinjourney.app.feature.session.VideoLayoutMath
import com.violinjourney.app.feature.session.VideoUi
import kotlinx.coroutines.delay

private const val TABULAR_FIGURES = "tnum"
private const val PANEL_HIDE_MS = 3_000L
private const val PANEL_FADE_MS = 300
private val StripHeight = 40.dp
private val StripBar = 6.dp
private val PanelButton = 48.dp

/**
 * The picture on top of the session screen (handoff 20d1–20d3). Scrolling towards the note roll
 * does not take it away: by the offset of the scroll — and by nothing else — it shrinks into a
 * row under the top bar, with the score and the time beside a mini frame. Watching the hands and
 * the roll at once is what this screen is opened for. [scrolledPx] is read in the layout phase.
 */
@Composable
fun StickyVideo(
    video: VideoUi,
    content: SessionContent,
    player: PlayerState?,
    scrolledPx: () -> Int,
    availableWidth: Float,
    screenHeight: Float,
    callbacks: VideoSurfaceCallbacks,
    onTap: () -> Unit,
    onFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
    /** The sound is not ready (the backing is being made): the picture waits with it. */
    waiting: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val aspect = VideoLayoutMath.aspectOf(video.width, video.height)
    val block = VideoLayoutMath.blockHeight(aspect, availableWidth, screenHeight)
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val collapse = VideoLayoutMath.collapse(block, scrolledPx() / density)
    val frame = VideoLayoutMath.frameAt(aspect, availableWidth, block, collapse)
    val line = colors.surfaceContainerHigh
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(frame.blockHeight.dp)
            .background(colors.surface)
            // the line under the row comes with the row
            .drawBehind { drawLine(line.copy(alpha = frame.wordsAlpha), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) },
    ) {
        // the margins of a tall video are a field of their own, not the screen showing through
        if (collapse < 1f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 1f - collapse }
                    .background(ViolinTheme.videoColors.field, RoundedCornerShape(16.dp)),
            )
        }
        VideoFrame(
            video = video,
            playing = player?.playing == true,
            callbacks = callbacks,
            onTap = onTap,
            modifier = Modifier
                .offset(x = frame.x.dp)
                .size(frame.width.dp, frame.height.dp),
            corner = (16f - 6f * collapse).dp,
            // in the row the button stands beside the words, not on a frame a finger wide
            onFullscreen = onFullscreen.takeIf { collapse < 1f },
            cornerButtonAlpha = 1f - frame.wordsAlpha,
            waiting = waiting,
        )
        if (frame.wordsAlpha > 0f) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = frame.wordsAlpha }
                    .padding(start = (frame.width + 14f).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = content.scorePercent.toString(),
                            color = colors.onSurface,
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = TABULAR_FIGURES),
                        )
                        Text(
                            text = "%",
                            modifier = Modifier.padding(start = 2.dp, bottom = 3.dp),
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.video_row_time,
                            Formats.duration(player?.positionMs ?: 0), Formats.duration(content.durationMs), Formats.signedCents(content.biasCents),
                        ),
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontFeatureSettings = TABULAR_FIGURES),
                    )
                }
                val label = stringResource(R.string.video_fullscreen)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(enabled = collapse >= 1f, role = Role.Button, onClick = onFullscreen)
                        .semantics { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) { AppIcon(AppIcons.Fullscreen, contentDescription = null, tint = colors.onSurface, size = 22.dp) }
            }
        }
    }
}

/**
 * The video over the whole screen, in whatever way the phone is held (handoff 20e). The panel —
 * back, the name and A/B above; play, time, the slider and «свернуть» below — hides by itself
 * and comes back by a tap, as on the music stand. The note strip shows what is played around the
 * cursor; it lies on the picture only together with the panel.
 */
@Composable
fun FullscreenVideo(
    video: VideoUi,
    content: SessionContent,
    title: String,
    player: PlayerState?,
    callbacks: VideoSurfaceCallbacks,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onOriginal: (Boolean) -> Unit,
    onExit: () -> Unit,
) {
    val videoColors = ViolinTheme.videoColors
    var touches by remember { mutableIntStateOf(0) }
    var panel by remember { mutableStateOf(true) }
    val playing = player?.playing == true
    // Hides only while the video plays: a paused picture is being looked at, controls and all.
    LaunchedEffect(touches, playing) {
        if (playing) {
            delay(PANEL_HIDE_MS)
            panel = false
        }
    }
    BackHandler(onBack = onExit)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(videoColors.fullBackground),
        contentAlignment = Alignment.Center,
    ) {
        val aspect = VideoLayoutMath.aspectOf(video.width, video.height)
        val frame = VideoLayoutMath.fit(aspect, maxWidth.value, maxHeight.value)
        // Room under the picture (a wide video held upright): the strip lives there and stays when the panel goes.
        val roomBelow = (maxHeight.value - frame.height) / 2f
        val stripStays = roomBelow >= StripHeight.value + PanelButton.value + 32f
        VideoFrame(
            video = video,
            playing = playing,
            callbacks = callbacks,
            onTap = {
                // a tap with the panel away brings it back and pauses, as the handoff has it; with the panel there it is the panel's business
                if (panel) onPlayPause() else onPlayPause()
                panel = true
                touches++
            },
            modifier = Modifier.size(frame.width.dp, frame.height.dp),
            corner = 0.dp,
        )
        val scrimTop = Brush.verticalGradient(listOf(videoColors.panel, Color.Transparent))
        val scrimBottom = Brush.verticalGradient(listOf(Color.Transparent, videoColors.panel))
        AnimatedVisibility(visible = panel, enter = fadeIn(tween(PANEL_FADE_MS)), exit = fadeOut(tween(PANEL_FADE_MS)), modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(scrimTop)
                    .systemBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PanelIcon(AppIcons.Back, stringResource(R.string.session_back), onExit)
                Text(
                    text = title,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                )
                if (player?.processed == true) {
                    AbSwitch(original = player.original, onOriginal = { onOriginal(it); touches++ }, modifier = Modifier.padding(end = 12.dp))
                }
            }
        }
        Column(modifier = Modifier.align(Alignment.BottomCenter)) {
            AnimatedVisibility(visible = panel || stripStays, enter = fadeIn(tween(PANEL_FADE_MS)), exit = fadeOut(tween(PANEL_FADE_MS))) {
                NoteStrip(content, player?.positionMs ?: 0, Modifier.padding(horizontal = 16.dp).padding(bottom = if (panel) 0.dp else 24.dp))
            }
            AnimatedVisibility(visible = panel, enter = fadeIn(tween(PANEL_FADE_MS)), exit = fadeOut(tween(PANEL_FADE_MS))) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(scrimBottom)
                        .systemBarsPadding()
                        .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (player != null) {
                        PlayerBar(
                            player = player.copy(processed = false), // A/B lives above here
                            onPlayPause = { onPlayPause(); touches++ },
                            onSeek = { onSeek(it); touches++ },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    PanelIcon(AppIcons.FullscreenExit, stringResource(R.string.video_fullscreen_exit), onExit)
                }
            }
        }
    }
}

@Composable
private fun PanelIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(PanelButton)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { AppIcon(icon, contentDescription = null, tint = Color.White) }
}

/**
 * What is played around the cursor, ±4 s (handoff 20e): the whole roll of a take in forty dp is
 * coloured noise. Pieces in the colour of their zone, higher notes higher; the one under the
 * cursor is outlined. Decoration for the eye — TalkBack has the roll itself on the screen below.
 */
@Composable
fun NoteStrip(content: SessionContent, positionMs: Long, modifier: Modifier = Modifier) {
    val zoneColors = ViolinTheme.zoneColors
    val cursor = MaterialTheme.colorScheme.primary
    val outline = Color.White
    val pieces = VideoLayoutMath.strip(content.segments.map { it.startMs to it.endMs }, positionMs)
    val rows = content.rollNotes.size.coerceAtLeast(1)
    Box(
        modifier
            .fillMaxWidth()
            .height(StripHeight)
            .clip(RoundedCornerShape(10.dp))
            .background(ViolinTheme.videoColors.scrim)
            .drawBehind {
                val bar = StripBar.toPx()
                val room = size.height - bar - 8.dp.toPx()
                pieces.forEach { piece ->
                    val segment = content.segments[piece.index]
                    val row = content.rollNotes.indexOf(segment.note).coerceAtLeast(0)
                    val top = 4.dp.toPx() + if (rows > 1) room * row / (rows - 1) else room / 2
                    val left = piece.from * size.width
                    val width = ((piece.to - piece.from) * size.width - 1.dp.toPx()).coerceAtLeast(2f)
                    drawRoundRect(zoneColors.colorFor(segment.zone), Offset(left, top), Size(width, bar), CornerRadius(bar / 2))
                    if (positionMs in segment.startMs..segment.endMs) {
                        drawRoundRect(outline, Offset(left, top), Size(width, bar), CornerRadius(bar / 2), style = Stroke(1.5.dp.toPx()))
                    }
                }
                drawRect(cursor, Offset(size.width / 2 - 1.dp.toPx(), 0f), Size(2.dp.toPx(), size.height))
            },
    )
}
