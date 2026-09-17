package com.example.violintuner.feature.live.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.violintuner.R
import com.example.violintuner.core.ui.format.Formats
import com.example.violintuner.core.ui.theme.ViolinTheme
import com.example.violintuner.feature.live.RecordingState

private const val TABULAR_FIGURES = "tnum"
/**
 * Strip above the record button while recording (spec 3.9): pulsing red dot, timer and the mini
 * bar of the notes played so far, colored by zone and filling up from the left.
 */
@Composable
fun RecordingStrip(recording: RecordingState, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val zoneColors = ViolinTheme.zoneColors
    val elapsed = Formats.duration(recording.elapsedMs)
    val description = stringResource(R.string.recording_elapsed_description, elapsed)

    val pulse by rememberInfiniteTransition(label = "recordingPulse").animateFloat(
        initialValue = 1f,
        targetValue = LiveMotion.RECORDING_PULSE_MIN_ALPHA,
        animationSpec = infiniteRepeatable(tween(LiveMotion.RECORDING_PULSE_MS / 2), RepeatMode.Reverse),
        label = "recordingPulseAlpha",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(LiveDimens.RecordingStripGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(LiveDimens.RecordingDotSize)
                .alpha(pulse)
                .background(zoneColors.off, CircleShape),
        )
        Text(
            text = elapsed,
            color = colors.onSurface,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = TABULAR_FIGURES,
            ),
        )
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(LiveDimens.RecordingBarHeight)
                .clip(CircleShape)
                .background(colors.surfaceContainerHigh),
        ) {
            val gap = LiveDimens.RecordingBarGap.toPx()
            var x = 0f
            for (bar in recording.bars) {
                val width = size.width * bar.fraction
                // very short notes still get a sliver, the gap is taken out of the note itself
                drawRect(
                    color = zoneColors.colorFor(bar.zone),
                    topLeft = Offset(x, 0f),
                    size = Size((width - gap).coerceAtLeast(1f), size.height),
                )
                x += width
                if (x >= size.width) break
            }
        }
    }
}
