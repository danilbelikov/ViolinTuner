package com.violinjourney.app.feature.history.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.ui.components.rememberSmallFileImage
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.motion.LocalReduceMotion

/** Sizes of the tile (spec 5.15, 5.29 R5): the circle and the sign inside it. */
enum class RecordTileSize(val circle: Dp, val sign: Dp) {
    /** The card of a recording. */
    CARD(40.dp, 22.dp),

    /** The empty states of «Записи» — nothing at all, and nothing under a chip. */
    EMPTY(72.dp, 32.dp),
}

private val TileRing = 1.5.dp

/** The corner of the square of a frame (spec 5.31). */
private val FrameCorner = 10.dp

/**
 * Where a frame is cut to its square (spec 5.31): in the middle across; of an upright one more from below than from above — the face
 * and the violin are in the upper half of such a frame. A frame lying on its side overflows only across, so the height says nothing.
 */
private val FrameFocus = BiasAlignment(horizontalBias = 0f, verticalBias = -0.5f)

/** How long a frame just read takes to come in over its square (spec 5.31). */
private const val FRAME_IN_MS = 150

/**
 * What stands at the left of every recording (spec 3.21, handoff 22b): one quiet circle of one colour — it says what is inside, a note
 * for sound and a camera for video, and nothing about how it went. A recording without sound is the same sign in a ring without the
 * fill: shape, not colour. A video with its file and a thumbnail ([thumbPath]) shows a frame of itself instead, as wide as the circle
 * (spec 3.38). The words for TalkBack live on the card, which knows the rest.
 */
@Composable
fun RecordTile(
    hasAudio: Boolean,
    hasVideo: Boolean,
    modifier: Modifier = Modifier,
    size: RecordTileSize = RecordTileSize.CARD,
    thumbPath: String? = null,
) {
    if (hasAudio && hasVideo && thumbPath != null) {
        FrameTile(thumbPath, modifier, size)
        return
    }
    val colors = MaterialTheme.colorScheme
    val fill = colors.surfaceContainerHigh
    val ring = colors.outlineVariant
    Box(
        modifier = modifier
            .size(size.circle)
            .drawBehind {
                if (hasAudio) {
                    drawCircle(fill)
                } else {
                    val stroke = TileRing.toPx()
                    drawCircle(ring, radius = (this.size.minDimension - stroke) / 2f, style = Stroke(stroke))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(if (hasVideo) AppIcons.Video else AppIcons.NoteOne, contentDescription = null, tint = colors.onSurfaceVariant, size = size.sign)
    }
}

/**
 * The frame of a video in its tile (spec 3.38, 5.31): a rounded square as wide as the circle, so the words of every card begin on
 * one line and the mark of the selection takes its place without a jump. Nothing lies over the picture — it is the sign itself. While
 * the file is read the square holds the camera, and the frame comes in over it; one read a moment ago is there from the first frame,
 * so a list scrolled back and forth does not blink.
 */
@Composable
private fun FrameTile(path: String, modifier: Modifier, size: RecordTileSize) {
    val colors = MaterialTheme.colorScheme
    val reduce = LocalReduceMotion.current
    val image = rememberSmallFileImage(path)
    val shown = remember(path) { Animatable(if (image != null || reduce) 1f else 0f) }
    LaunchedEffect(image != null) { if (image != null && shown.value < 1f) shown.animateTo(1f, tween(FRAME_IN_MS)) }
    Box(
        modifier = modifier
            .size(size.circle)
            .clip(RoundedCornerShape(FrameCorner))
            .background(colors.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(AppIcons.Video, contentDescription = null, tint = colors.onSurfaceVariant, size = size.sign)
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = shown.value },
                alignment = FrameFocus,
                contentScale = ContentScale.Crop,
            )
        }
    }
}

private val MarkRing = 2.dp
private val MarkCheck = 20.dp

/**
 * What the tile turns into while recordings are being picked (handoff 19b3): a circle of the same
 * size, so the list does not jump — an empty ring, or filled with a tick. Shape, not only colour.
 */
@Composable
fun SelectionMark(selected: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val fill = colors.primary
    val ring = colors.outline
    Box(
        modifier = modifier
            .size(RecordTileSize.CARD.circle)
            .drawBehind {
                if (selected) {
                    drawCircle(fill)
                } else {
                    val stroke = MarkRing.toPx()
                    drawCircle(ring, radius = (size.minDimension - stroke) / 2f, style = Stroke(stroke))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) AppIcon(AppIcons.Check, contentDescription = null, tint = colors.onPrimary, size = MarkCheck)
    }
}
