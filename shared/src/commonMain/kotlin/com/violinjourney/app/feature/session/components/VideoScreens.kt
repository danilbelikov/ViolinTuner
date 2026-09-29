package com.violinjourney.app.feature.session.components

import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrain
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.offset
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.session_back
import com.violinjourney.app.shared.resources.video_fullscreen
import com.violinjourney.app.shared.resources.video_fullscreen_exit
import com.violinjourney.app.shared.resources.video_row_flat
import com.violinjourney.app.shared.resources.video_row_none
import com.violinjourney.app.shared.resources.video_row_sharp
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.audio.playback.PlayerState
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.session.SessionContent
import com.violinjourney.app.feature.session.VideoLayoutMath
import com.violinjourney.app.feature.session.VideoUi
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private const val TABULAR_FIGURES = "tnum"
private const val PANEL_HIDE_MS = 3_000L

/** «На весь экран» in the row the picture shrinks into: an icon of 24 in a target of 48. */
private val RowButton = 48.dp

/** The corner of the mini frame of that row (5.13): the picture's own corner shrinks to it with the frame. */
private val MiniFrameCorner = 10.dp
private const val PANEL_FADE_MS = 300
private val StripHeight = 40.dp
private val StripBar = 6.dp
private val PanelButton = 48.dp

/**
 * The picture on top of the session screen (handoff 20d1–20d3). Scrolling towards the note roll
 * does not take it away: by the offset of the scroll — and by nothing else — it shrinks into a
 * row under the top bar, with the score and the time beside a mini frame. Watching the hands and
 * the roll at once is what this screen is opened for. [scrolledPx] is read in the layout phase:
 * the size and place of the frame, the height of the block, the rounding and the fades are worked
 * out where they are laid out and drawn, and composition sees only whether the frame is still open
 * and whether the words of the row show — not every pixel of the scroll.
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
    /** The sound is not ready (the backing is being made): the picture waits with it, and neither it nor its row has «на весь экран». */
    waiting: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val aspect = VideoLayoutMath.aspectOf(video.width, video.height)
    val block = VideoLayoutMath.blockHeight(aspect, availableWidth, screenHeight)
    val density = LocalDensity.current.density
    val scrolled by rememberUpdatedState(scrolledPx)
    // Only ever called where things are laid out or drawn: what they read of the scroll moves that phase alone.
    val geometry = remember(aspect, availableWidth, block, density) {
        StickyGeometry(
            collapse = { VideoLayoutMath.collapse(block, scrolled() / density) },
            frame = { collapse -> VideoLayoutMath.frameAt(aspect, availableWidth, block, collapse) },
        )
    }
    val open by remember(geometry) { derivedStateOf { geometry.collapse() < 1f } }
    val words by remember(geometry) { derivedStateOf { geometry.frame().wordsAlpha > 0f } }
    val line = colors.outlineVariant
    Box(
        modifier = modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val height = geometry.frame().blockHeight.dp.roundToPx().coerceIn(constraints.minHeight, constraints.maxHeight)
                val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
                layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
            }
            .background(colors.surface)
            // the line under the row comes with the row
            .drawBehind { drawLine(line.copy(alpha = geometry.frame().wordsAlpha), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) },
    ) {
        // the margins of a tall video are a field of their own, not the screen showing through
        if (open) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 1f - geometry.collapse() }
                    .background(ViolinTheme.videoColors.field, RoundedCornerShape(VideoCorner)),
            )
        }
        VideoFrame(
            video = video,
            playing = player?.playing == true,
            callbacks = callbacks,
            onTap = onTap,
            modifier = Modifier
                .layout { measurable, constraints ->
                    val frame = geometry.frame()
                    val placeable = measurable.measure(constraints.constrain(Constraints.fixed(frame.width.dp.roundToPx(), frame.height.dp.roundToPx())))
                    layout(placeable.width, placeable.height) { placeable.placeRelative(frame.x.dp.roundToPx(), 0) }
                }
                // the rounding shrinks with the frame, from that of a card to that of the mini frame: the clip of VideoFrame itself is
                // left square
                .graphicsLayer {
                    shape = RoundedCornerShape(VideoCorner - (VideoCorner - MiniFrameCorner) * geometry.collapse())
                    clip = true
                },
            corner = 0.dp,
            // in the row the button stands beside the words, not on a frame a finger wide
            onFullscreen = onFullscreen.takeIf { open },
            cornerButtonAlpha = { 1f - geometry.frame().wordsAlpha },
            waiting = waiting,
        )
        if (words) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = geometry.frame().wordsAlpha }
                    // beside the mini frame, however wide it is at the moment
                    .layout { measurable, constraints ->
                        val start = (geometry.frame().width + 14f).dp.roundToPx()
                        val placeable = measurable.measure(constraints.offset(horizontal = -start))
                        layout(constraints.constrainWidth(placeable.width + start), constraints.constrainHeight(placeable.height)) {
                            placeable.placeRelative(start, 0)
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // «82 %» and «в строе · ниже на 6 ц» (spec 3.36.5): the time is in the player; the summary under the video says the same
                // to TalkBack, so these words stay quiet
                Column(modifier = Modifier.weight(1f).clearAndSetSemantics { }) {
                    Row {
                        Text(
                            text = content.scorePercent.toString(),
                            modifier = Modifier.alignByBaseline(),
                            color = colors.onSurface,
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = TABULAR_FIGURES),
                        )
                        Text(
                            text = "%",
                            modifier = Modifier.alignByBaseline().padding(start = 2.dp),
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        )
                    }
                    Text(
                        text = rowWordsOf(content),
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR_FIGURES),
                    )
                }
                // while the backing is made there is no «на весь экран» here either (spec 3.36.5, 5.25): its room stays, so the words
                // do not move when it comes
                if (waiting) {
                    Spacer(Modifier.size(RowButton))
                } else {
                    val label = stringResource(Res.string.video_fullscreen)
                    Box(
                        modifier = Modifier
                            .size(RowButton)
                            .clip(CircleShape)
                            .clickable(enabled = !open, role = Role.Button, onClick = onFullscreen)
                            .semantics { contentDescription = label },
                        contentAlignment = Alignment.Center,
                    ) { AppIcon(AppIcons.Fullscreen, contentDescription = null, tint = colors.onSurface) }
                }
            }
        }
    }
}

/** «в строе · ниже на 6 ц» of the row the picture shrinks into (spec 3.36.5): the bias in words, as the summary says it. */
@Composable
private fun rowWordsOf(content: SessionContent): String {
    val cents = abs(content.biasCents).roundToInt()
    return when {
        content.biasZone == null -> stringResource(Res.string.video_row_none)
        content.biasCents < 0 -> stringResource(Res.string.video_row_flat, cents)
        else -> stringResource(Res.string.video_row_sharp, cents)
    }
}

/** How far the sticky video has shrunk, and its frame at that — read where laid out and drawn, never in composition. */
private class StickyGeometry(val collapse: () -> Float, private val frame: (Float) -> VideoLayoutMath.Frame) {
    fun frame(): VideoLayoutMath.Frame = frame(collapse())
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
    /** Where the player is, exactly; [player] keeps it to the whole second. Read where the slider and the strip are drawn. */
    position: () -> Long,
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
                // a tap pauses or resumes, as on the video in the screen and as the handoff has it (spec 3.19), and brings
                // the panel back for its seconds
                onPlayPause()
                panel = true
                touches++
            },
            modifier = Modifier.size(frame.width.dp, frame.height.dp),
            corner = 0.dp,
        )
        // the panel lies on the glass of R1 (spec 3.36.5), its words light on it
        val glass = ViolinTheme.glass
        val words = MaterialTheme.colorScheme.onSurface
        AnimatedVisibility(visible = panel, enter = fadeIn(tween(PANEL_FADE_MS)), exit = fadeOut(tween(PANEL_FADE_MS)), modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(glass)
                    // what of the bars the screen's host has not taken already (not systemBarsPadding(): on iOS that one
                    // does not see the host's share and put the status bar here a second time)
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PanelIcon(AppIcons.Back, stringResource(Res.string.session_back), onExit)
                Text(
                    text = title,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    color = words,
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
                NoteStrip(content, position, Modifier.padding(horizontal = 16.dp).padding(bottom = if (panel) 0.dp else 24.dp))
            }
            AnimatedVisibility(visible = panel, enter = fadeIn(tween(PANEL_FADE_MS)), exit = fadeOut(tween(PANEL_FADE_MS))) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(glass)
                        .windowInsetsPadding(WindowInsets.systemBars)
                        .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (player != null) {
                        PlayerBar(
                            player = player.copy(processed = false), // A/B lives above here
                            position = position,
                            onPlayPause = { onPlayPause(); touches++ },
                            onSeek = { onSeek(it); touches++ },
                            modifier = Modifier.weight(1f),
                            // light on the glass, as the name and the icons
                            timeColor = words,
                        )
                    }
                    PanelIcon(AppIcons.FullscreenExit, stringResource(Res.string.video_fullscreen_exit), onExit)
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
    ) { AppIcon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }
}

/**
 * What is played around the cursor, ±4 s (handoff 20e): the whole roll of a take in forty dp is
 * coloured noise. Pieces in the colour of their zone, higher notes higher; the one under the
 * cursor is outlined. Decoration for the eye — TalkBack has the roll itself on the screen below.
 * [positionMs] is read where the strip is drawn: while the sound plays it redraws, nothing recomposes.
 */
@Composable
fun NoteStrip(content: SessionContent, positionMs: () -> Long, modifier: Modifier = Modifier) {
    val zoneColors = ViolinTheme.zoneColors
    val cursor = MaterialTheme.colorScheme.primary
    val outline = Color.White
    val spans = remember(content.segments) { content.segments.map { it.startMs to it.endMs } }
    val rowOf = remember(content.rollNotes) { content.rollNotes.withIndex().associate { (row, note) -> note to row } }
    val rows = content.rollNotes.size.coerceAtLeast(1)
    Box(
        modifier
            .fillMaxWidth()
            .height(StripHeight)
            .clip(RoundedCornerShape(10.dp))
            .background(ViolinTheme.videoColors.scrim)
            .drawBehind {
                val at = positionMs()
                val bar = StripBar.toPx()
                val room = size.height - bar - 8.dp.toPx()
                VideoLayoutMath.strip(spans, at).forEach { piece ->
                    val segment = content.segments[piece.index]
                    val row = rowOf[segment.note] ?: 0
                    val top = 4.dp.toPx() + if (rows > 1) room * row / (rows - 1) else room / 2
                    val left = piece.from * size.width
                    val width = ((piece.to - piece.from) * size.width - 1.dp.toPx()).coerceAtLeast(2f)
                    drawRoundRect(zoneColors.colorFor(segment.zone), Offset(left, top), Size(width, bar), CornerRadius(bar / 2))
                    if (at in segment.startMs..segment.endMs) {
                        drawRoundRect(outline, Offset(left, top), Size(width, bar), CornerRadius(bar / 2), style = Stroke(1.5.dp.toPx()))
                    }
                }
                drawRect(cursor, Offset(size.width / 2 - 1.dp.toPx(), 0f), Size(2.dp.toPx(), size.height))
            },
    )
}
