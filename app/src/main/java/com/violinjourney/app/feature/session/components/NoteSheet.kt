package com.violinjourney.app.feature.session.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.violinjourney.app.R
import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.domain.session.Finger
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.icons.IconLabel
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.session.RollSegment
import kotlin.math.roundToInt

private const val TABULAR_FIGURES = "tnum"
private const val MS_PER_SECOND = 1_000.0
private val TargetStyle = TextStyle(fontSize = 56.sp, lineHeight = 56.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.035).em)
private val MeanStyle = TextStyle(fontSize = 40.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR_FIGURES)
private val TileValue = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR_FIGURES)

/**
 * The way from a note to its place in the recording (spec 3.19): «Смотреть это место» of a video
 * take is the one action of the sheet and a filled button; «Слушать это место» of a sound
 * recording is the same thing, worth less, and outlined. A recording without sound has neither.
 */
class NotePlace(val video: Boolean, val onPlay: () -> Unit)

/** Details of one played note (spec 3.10, item 7). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteSheet(segment: RollSegment, onDismiss: () -> Unit, place: NotePlace? = null) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        NoteSheetContent(segment, place = place)
    }
}

@Composable
internal fun NoteSheetContent(segment: RollSegment, modifier: Modifier = Modifier, place: NotePlace? = null) {
    val colors = MaterialTheme.colorScheme
    val meanColor = ViolinTheme.zoneColors.colorFor(segment.zone)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.session_note_target), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp))
                Row {
                    Text(
                        text = if (segment.note.isSharp) "${segment.note.letter}#" else segment.note.letter.toString(),
                        modifier = Modifier.alignByBaseline(),
                        color = colors.onSurface,
                        style = MaterialTheme.typography.displayMedium.merge(TargetStyle),
                    )
                    Text(
                        text = segment.note.octave.toString(),
                        modifier = Modifier.alignByBaseline(),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.session_note_mean), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp))
                Text(
                    text = Formats.signedCents(segment.meanCents),
                    color = meanColor,
                    style = MaterialTheme.typography.displaySmall.merge(MeanStyle),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(
                label = stringResource(R.string.session_note_min_max),
                value = stringResource(
                    R.string.session_note_min_max_value,
                    Formats.signedCents(segment.minCents), Formats.signedCents(segment.maxCents),
                ),
                modifier = Modifier.weight(1f),
            )
            Tile(
                label = stringResource(R.string.session_note_duration),
                value = stringResource(
                    R.string.session_note_duration_value,
                    Formats.oneDecimal((segment.endMs - segment.startMs) / MS_PER_SECOND),
                ),
                modifier = Modifier.weight(1f),
            )
            Tile(
                label = stringResource(R.string.session_note_range),
                value = stringResource(
                    R.string.session_note_range_value,
                    ((segment.maxCents - segment.minCents) / 2).roundToInt(),
                ),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(colors.primaryContainer, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = segment.position.string.note.letter.toString(),
                    color = colors.onPrimaryContainer,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
            }
            Column {
                Text(
                    text = stringResource(
                        R.string.session_note_position,
                        segment.position.string.note.letter.toString(),
                        stringResource(fingerRes(segment.position.finger)),
                    ),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text = stringResource(
                        when {
                            segment.zone == Zone.IN_TUNE && !segment.steady -> R.string.session_tip_wandering
                            segment.zone == Zone.IN_TUNE -> R.string.session_tip_stable
                            segment.meanCents < 0 -> R.string.session_tip_flat
                            else -> R.string.session_tip_sharp
                        },
                    ),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                )
            }
        }
        if (place != null) {
            val label = stringResource(if (place.video) R.string.video_watch_place else R.string.video_listen_place)
            if (place.video) {
                Button(onClick = place.onPlay, modifier = Modifier.fillMaxWidth().height(52.dp)) { IconLabel(AppIcons.PlayCircle, label, iconSize = 20.dp) }
            } else {
                OutlinedButton(onClick = place.onPlay, modifier = Modifier.fillMaxWidth().height(52.dp)) { IconLabel(AppIcons.PlayCircle, label, iconSize = 20.dp) }
            }
        }
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .background(colors.surfaceContainer, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp))
        Text(value, color = colors.onSurface, style = MaterialTheme.typography.titleMedium.merge(TileValue), maxLines = 1)
    }
}

private fun fingerRes(finger: Finger): Int = when (finger) {
    Finger.OPEN -> R.string.finger_open
    Finger.FIRST -> R.string.finger_first
    Finger.SECOND -> R.string.finger_second
    Finger.THIRD -> R.string.finger_third
    Finger.FOURTH -> R.string.finger_fourth
    Finger.HIGHER_POSITION -> R.string.finger_higher_position
}
