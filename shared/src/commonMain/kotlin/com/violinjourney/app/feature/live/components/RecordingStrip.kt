package com.violinjourney.app.feature.live.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.domain.session.RecordingRibbon
import com.violinjourney.app.core.ui.components.glass
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.live.RecordingState
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.recording_elapsed_description
import com.violinjourney.app.shared.resources.recording_strip_label
import org.jetbrains.compose.resources.stringResource

private const val TABULAR_FIGURES = "tnum"

/** «запись» and the time on the strip (spec 5.29 R6): 15 sp, 700. */
private val StripWordSize = 15.sp

/**
 * The strip of a take over the bottom row of Live (spec 3.9, 3.36.6, 5.29 R6), on a capsule of glass of its own, 50 high: the
 * pulsing dot of recording with its halo, «запись», the time in tabular figures and the mini ribbon of the notes played so far in the
 * colours of their zones, filling up from the left — no track under it. The word tells it from the time of the practice, the ribbon
 * shows where a note slipped. The pulse and the notes ([ribbon]) are read while drawing: they move many times a second, the words
 * once. It stays whole in the dark — it is how the performance is going.
 */
@Composable
fun RecordingStrip(recording: RecordingState, ribbon: () -> RecordingRibbon, modifier: Modifier = Modifier) {
    val zoneColors = ViolinTheme.zoneColors
    val red = ViolinTheme.recording
    val onGlass = MaterialTheme.colorScheme.onSurface
    val elapsed = Formats.duration(recording.elapsedMs)
    val description = stringResource(Res.string.recording_elapsed_description, elapsed)
    val style = TextStyle(
        fontFamily = MaterialTheme.typography.bodyMedium.fontFamily,
        fontSize = StripWordSize,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp,
    )
    val pulse = rememberInfiniteTransition(label = "recordingPulse").animateFloat(
        initialValue = 1f,
        targetValue = LiveMotion.RECORDING_PULSE_MIN_ALPHA,
        animationSpec = infiniteRepeatable(tween(LiveMotion.RECORDING_PULSE_MS / 2), RepeatMode.Reverse),
        label = "recordingPulseAlpha",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(LiveDimens.RecordingStripHeight)
            .glass(RoundedCornerShape(LiveDimens.RecordingStripCorner), edge = true, ground = liveGlassGround())
            // before the padding: TalkBack and uiautomator see the whole capsule
            .clearAndSetSemantics { contentDescription = description }
            .padding(horizontal = LiveDimens.RecordingStripPadding),
        horizontalArrangement = Arrangement.spacedBy(LiveDimens.RecordingStripGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // the dot and its halo pulse together, read while drawing: no layer, no composing on every frame of the pulse
        Spacer(
            Modifier
                .size(LiveDimens.RecordingDotSize)
                .drawBehind {
                    val a = pulse.value
                    val halo = LiveDimens.RecordingDotHalo.toPx()
                    drawCircle(red, radius = size.minDimension / 2 + halo / 2, alpha = LiveDimens.RECORDING_HALO_ALPHA * a, style = Stroke(halo))
                    drawCircle(red, alpha = a)
                },
        )
        Text(text = stringResource(Res.string.recording_strip_label), color = onGlass, style = style, maxLines = 1, softWrap = false)
        Text(text = elapsed, color = onGlass, style = style.copy(fontFeatureSettings = TABULAR_FIGURES), maxLines = 1, softWrap = false)
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(LiveDimens.RecordingBarHeight),
        ) {
            val gap = LiveDimens.RecordingBarGap.toPx()
            val corner = CornerRadius(LiveDimens.RecordingBarCorner.toPx())
            val notes = ribbon()
            var x = 0f
            for (bar in notes.pieces) {
                val width = size.width * notes.share(bar)
                // very short notes still get a sliver, the gap is taken out of the note itself
                drawRoundRect(
                    color = zoneColors.colorFor(bar.zone),
                    topLeft = Offset(x, 0f),
                    size = Size((width - gap).coerceAtLeast(1f), size.height),
                    cornerRadius = corner,
                )
                x += width
                if (x >= size.width) break
            }
        }
    }
}
