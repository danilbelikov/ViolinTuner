package com.example.violintuner.feature.history.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.violintuner.R
import com.example.violintuner.core.domain.Zone
import com.example.violintuner.core.ui.icons.AppIcon
import com.example.violintuner.core.ui.icons.AppIcons
import com.example.violintuner.core.ui.theme.ViolinTheme

/** Sizes of the note tile (handoff 19b, `sizes`): the circle and the note inside it. */
enum class RecordTileSize(val circle: Dp, val note: Dp) {
    /** The card of a recording. */
    CARD(44.dp, 26.dp),

    /** The card of a piece in the repertoire list. */
    PIECE(36.dp, 20.dp),
}

private const val TILE_FILL_ALPHA = 0.16f
private val TileRing = 1.5.dp
private val EmptyDash = 4.dp

/**
 * The note that stands where the score used to (spec 3.18, handoff 19b): a single quaver for a
 * free session, a beamed pair for a take. The note and the soft fill carry the zone of the score,
 * so the list says "it went well" without lining numbers up into a report card. A recording
 * without sound is a ring without the fill. TalkBack hears the zone in words, never the number.
 */
@Composable
fun RecordTile(zone: Zone, take: Boolean, hasAudio: Boolean, modifier: Modifier = Modifier, size: RecordTileSize = RecordTileSize.CARD) {
    val color = ViolinTheme.zoneColors.colorFor(zone)
    val words = listOfNotNull(
        stringResource(R.string.record_tile_take).takeIf { take },
        stringResource(
            when (zone) {
                Zone.IN_TUNE -> R.string.record_zone_good
                Zone.NEAR -> R.string.record_zone_fair
                Zone.OFF -> R.string.record_zone_off
            },
        ),
        stringResource(R.string.record_tile_no_sound).takeIf { !hasAudio },
    ).joinToString()
    Box(
        modifier = modifier
            .size(size.circle)
            .semantics { contentDescription = words }
            .drawBehind {
                if (hasAudio) {
                    drawCircle(color.copy(alpha = TILE_FILL_ALPHA))
                } else {
                    val stroke = TileRing.toPx()
                    drawCircle(color, radius = (this.size.minDimension - stroke) / 2f, style = Stroke(stroke))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(if (take) AppIcons.NotePair else AppIcons.NoteOne, contentDescription = null, tint = color, size = size.note)
    }
}

/** The tile of a piece that has no takes yet (handoff 19c2): a dashed ring — neither a hole nor a grade. */
@Composable
fun EmptyRecordTile(ring: Color, note: Color, modifier: Modifier = Modifier, size: RecordTileSize = RecordTileSize.PIECE) {
    Box(
        modifier = modifier
            .size(size.circle)
            .drawBehind {
                val stroke = TileRing.toPx()
                val dash = EmptyDash.toPx()
                drawCircle(
                    color = ring,
                    radius = (this.size.minDimension - stroke) / 2f,
                    center = Offset(this.size.width / 2f, this.size.height / 2f),
                    style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(AppIcons.NotePair, contentDescription = null, tint = note, size = size.note)
    }
}
