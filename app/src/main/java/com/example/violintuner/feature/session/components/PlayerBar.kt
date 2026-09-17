package com.example.violintuner.feature.session.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.violintuner.R
import com.example.violintuner.core.ui.format.Formats
import com.example.violintuner.feature.session.player.PlayerState

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
    }
}
