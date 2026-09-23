package com.violinjourney.app.feature.history.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons

/** Sizes of the tile (handoff 22b3, `sizes`): the circle and the sign inside it. */
enum class RecordTileSize(val circle: Dp, val sign: Dp) {
    /** The card of a recording. */
    CARD(40.dp, 22.dp),

    /** The empty state of «Записи». */
    EMPTY(56.dp, 30.dp),
}

private val TileRing = 1.5.dp

/**
 * What stands at the left of every recording (spec 3.21, handoff 22b): one quiet circle of one
 * colour — it says what is inside, a note for sound and a camera for video, and nothing about how
 * it went. A recording without sound is the same sign in a ring without the fill: shape, not colour.
 * The words for TalkBack live on the card, which knows the rest.
 */
@Composable
fun RecordTile(hasAudio: Boolean, hasVideo: Boolean, modifier: Modifier = Modifier, size: RecordTileSize = RecordTileSize.CARD) {
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
