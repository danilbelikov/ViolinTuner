package com.example.violintuner.feature.session.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.violintuner.R
import com.example.violintuner.core.audio.playback.PlayerState
import com.example.violintuner.core.ui.format.Formats

private val ButtonSize = 48.dp
private val GlyphSize = 20.dp
private val TimeWidth = 40.dp
private const val TABULAR_FIGURES = "tnum"

/** Play / pause, position, slider, duration (spec 3.10, item 3; handoff 4a). */
@Composable
fun PlayerBar(
    player: PlayerState,
    onPlayPause: () -> Unit,
    onSeek: (positionMs: Long) -> Unit,
    modifier: Modifier = Modifier,
    onOriginal: (original: Boolean) -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    // While the thumb is dragged the slider shows the finger, not the playback position.
    var dragged by remember { mutableStateOf<Float?>(null) }
    val duration = player.durationMs.coerceAtLeast(1)
    val shownMs = dragged?.let { (it * duration).toLong() } ?: player.positionMs
    val timeStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontFeatureSettings = TABULAR_FIGURES)
    val positionDescription = stringResource(R.string.session_player_position)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(ButtonSize)
                .clip(CircleShape)
                .background(colors.primary)
                .clickable(
                    onClickLabel = stringResource(
                        if (player.playing) R.string.session_player_pause else R.string.session_player_play,
                    ),
                    role = Role.Button,
                    onClick = onPlayPause,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(GlyphSize)) {
                val unit = size.width / 20f
                if (player.playing) {
                    // two bars 5 x 18, 4 apart
                    val bar = Size(5 * unit, 18 * unit)
                    val radius = CornerRadius(2 * unit)
                    drawRoundRect(colors.onPrimary, Offset(3 * unit, unit), bar, radius)
                    drawRoundRect(colors.onPrimary, Offset(12 * unit, unit), bar, radius)
                } else {
                    // handoff path: M5 3 L17 10 L5 17 Z
                    val triangle = Path().apply {
                        moveTo(5 * unit, 3 * unit)
                        lineTo(17 * unit, 10 * unit)
                        lineTo(5 * unit, 17 * unit)
                        close()
                    }
                    drawPath(triangle, colors.onPrimary)
                }
            }
        }
        Text(Formats.duration(shownMs), Modifier.widthIn(min = TimeWidth), colors.onSurfaceVariant, style = timeStyle)
        Slider(
            value = dragged ?: (player.positionMs.toFloat() / duration),
            onValueChange = { dragged = it },
            onValueChangeFinished = {
                dragged?.let { onSeek((it * duration).toLong()) }
                dragged = null
            },
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = positionDescription },
            colors = SliderDefaults.colors(
                thumbColor = colors.primary,
                activeTrackColor = colors.primary,
                inactiveTrackColor = colors.surfaceContainerHigh,
            ),
        )
        Text(
            text = Formats.duration(player.durationMs),
            modifier = Modifier.widthIn(min = TimeWidth),
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.End,
            style = timeStyle,
        )
        // Only when there is something to compare: the processing does something to this recording.
        if (player.processed) AbSwitch(original = player.original, onOriginal = onOriginal)
    }
}

/**
 * «A | B» (spec 3.17): A — the recording as recorded, B — with its processing, which is what is
 * heard by default, for that is what would be sent. Switches while playing, without a click.
 */
@Composable
fun AbSwitch(original: Boolean, onOriginal: (Boolean) -> Unit, modifier: Modifier = Modifier, height: Dp = AbHeight) {
    val colors = MaterialTheme.colorScheme
    val labels = listOf(stringResource(R.string.sound_ab_original) to true, stringResource(R.string.sound_ab_processed) to false)
    Row(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(colors.surfaceContainerHigh)
            .selectableGroup(),
    ) {
        labels.forEachIndexed { index, (description, value) ->
            val selected = original == value
            Box(
                modifier = Modifier
                    .size(width = height + 2.dp, height = height)
                    .clip(RoundedCornerShape(height / 2))
                    .background(if (selected) colors.primaryContainer else Color.Transparent)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onOriginal(value) })
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (index == 0) "A" else "B",
                    color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

private val AbHeight = 32.dp
