package com.violinjourney.app.feature.session.components

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.audio.playback.PlayerState
import com.violinjourney.app.core.ui.components.PlayPauseGlyph
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.session_player_pause
import com.violinjourney.app.shared.resources.session_player_play
import com.violinjourney.app.shared.resources.session_player_position
import com.violinjourney.app.shared.resources.sound_ab_original
import com.violinjourney.app.shared.resources.sound_ab_processed
import org.jetbrains.compose.resources.stringResource

private val ButtonSize = 48.dp
private val GlyphSize = 20.dp
private val TimeWidth = 40.dp
private const val TABULAR_FIGURES = "tnum"

/**
 * Play / pause, position, slider, duration (spec 3.10, item 3; handoff 4a). The words go by [player], whose
 * position may be to the whole second; the slider follows [position], the exact one, in a slider and a layer
 * of its own — a chunk of sound moves the thumb and redraws nothing around it. [timeColor] — the colour of the
 * two times: the second level of text by default; on the glass of the video over the whole screen the first
 * (spec 3.36.1, 5.29 R1: no grey on the glass).
 */
@Composable
fun PlayerBar(
    player: PlayerState,
    onPlayPause: () -> Unit,
    onSeek: (positionMs: Long) -> Unit,
    modifier: Modifier = Modifier,
    onOriginal: (original: Boolean) -> Unit = {},
    position: () -> Long = { player.positionMs },
    timeColor: Color = Color.Unspecified,
) {
    val colors = MaterialTheme.colorScheme
    val times = if (timeColor.isSpecified) timeColor else colors.onSurfaceVariant
    // While the thumb is dragged the slider shows the finger, not the playback position.
    var dragged by remember { mutableStateOf<Float?>(null) }
    val duration = player.durationMs.coerceAtLeast(1)
    val shownMs = dragged?.let { (it * duration).toLong() } ?: player.positionMs
    val timeStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontFeatureSettings = TABULAR_FIGURES)
    val positionDescription = stringResource(Res.string.session_player_position)

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
                        if (player.playing) Res.string.session_player_pause else Res.string.session_player_play,
                    ),
                    role = Role.Button,
                    onClick = onPlayPause,
                ),
            contentAlignment = Alignment.Center,
        ) {
            PlayPauseGlyph(playing = player.playing, tint = colors.onPrimary, size = GlyphSize)
        }
        Text(Formats.duration(shownMs), Modifier.widthIn(min = TimeWidth), times, style = timeStyle)
        SeekSlider(
            fraction = { dragged ?: (position().toFloat() / duration) },
            onValueChange = { dragged = it },
            onValueChangeFinished = {
                dragged?.let { onSeek((it * duration).toLong()) }
                dragged = null
            },
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = positionDescription },
        )
        Text(
            text = Formats.duration(player.durationMs),
            modifier = Modifier.widthIn(min = TimeWidth),
            color = times,
            textAlign = TextAlign.End,
            style = timeStyle,
        )
        // Only when there is something to compare: the processing does something to this recording.
        if (player.processed) AbSwitch(original = player.original, onOriginal = onOriginal)
    }
}

/** The only part of the bar that reads the exact position: it recomposes with every chunk, in a layer of its own. */
@Composable
private fun SeekSlider(fraction: () -> Float, onValueChange: (Float) -> Unit, onValueChangeFinished: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Slider(
        value = fraction(),
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        modifier = modifier.graphicsLayer(),
        colors = SliderDefaults.colors(
            thumbColor = colors.primary,
            activeTrackColor = colors.primary,
            inactiveTrackColor = colors.surfaceContainerHigh,
        ),
    )
}

/**
 * «A | B» (spec 3.17): A — the recording as recorded, B — with its processing, which is what is
 * heard by default, for that is what would be sent. Switches while playing, without a click.
 */
@Composable
fun AbSwitch(original: Boolean, onOriginal: (Boolean) -> Unit, modifier: Modifier = Modifier, height: Dp = AbHeight) {
    val colors = MaterialTheme.colorScheme
    val labels = listOf(stringResource(Res.string.sound_ab_original) to true, stringResource(Res.string.sound_ab_processed) to false)
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
