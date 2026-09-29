package com.violinjourney.app.feature.repertoire.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.live_too_noisy
import com.violinjourney.app.shared.resources.record_stop
import com.violinjourney.app.shared.resources.stand_recording_description
import com.violinjourney.app.shared.resources.take_level_description
import com.violinjourney.app.shared.resources.take_recording_label
import org.jetbrains.compose.resources.stringResource

// The bar of a running take (spec 3.36.4, 5.29 R4, «Нижняя зона записи»; repertoire.html 3, «Идёт дубль»).
private val Dot = 12.dp
private val DotHalo = 4.dp
private const val HALO_ALPHA = 0.2f
private val BarGap = 12.dp
private val LevelsHeight = 32.dp
private val LevelGap = 3.dp
private val LevelMin = 4.dp
private val LevelCorner = 2.dp
private const val LEVEL_ALPHA = 0.7f
private const val QUIET_SHARE = 0.4f
private val StopSize = 56.dp
private val StopSizeCompact = 48.dp
private val StopBorder = 1.5.dp
private val StopSquare = 18.dp
private val StopCorner = 4.dp
private val ProgressTop = 10.dp
private val ProgressIcon = 16.dp
private val ProgressTrack = 4.dp
private val ProgressGap = 10.dp
private val NoiseTop = 8.dp
private val NoiseDot = 8.dp
private val NoiseRing = 2.dp
private val RecordDotRing = 3.dp
private const val RECORD_DOT_RING_ALPHA = 0.55f
private const val TABULAR_FIGURES = "tnum"
private const val MS_PER_SECOND = 1_000L

/**
 * A take recorded «вслепую» (spec 3.15, 3.36.4): the red dot of recording with its halo, «запись» over the timer, a row of grey
 * [levels] — «the microphone hears», never «in tune» — and «стоп», a circle on the place of the record button. Under the bar the
 * backing's progress ([backingPlayedMs] of [backingDurationMs]) and, while it is [noisy], «Слишком шумно» with a hollow dot; the
 * bars go quieter then. The stand has neither the levels nor the backing ([levels] null): the dot, the timer, the noise and «стоп».
 * The dot does not pulse — R4 has no motion of its own (5.29 R4). [compact] — a window no higher than 360 dp: «стоп» of 48.
 * TalkBack hears «Идёт запись дубля, 1:12», «Микрофон слышит» and «Остановить запись».
 */
@Composable
fun RecordingBar(
    elapsedSeconds: Long,
    noisy: Boolean,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    levels: List<Float>? = null,
    backingPlayedMs: Long? = null,
    backingDurationMs: Long? = null,
    compact: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BarGap)) {
            val time = Formats.timer(elapsedSeconds * MS_PER_SECOND)
            val said = stringResource(Res.string.stand_recording_description, time)
            Row(
                modifier = Modifier.clearAndSetSemantics { contentDescription = said },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(BarGap),
            ) {
                HaloDot()
                Column {
                    Text(
                        stringResource(Res.string.take_recording_label),
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold),
                    )
                    Text(
                        time,
                        color = colors.onSurface,
                        maxLines = 1,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = TABULAR_FIGURES,
                        ),
                    )
                }
            }
            if (levels != null) LevelBars(levels, quiet = noisy, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
            StopCircle(onStop, if (compact) StopSizeCompact else StopSize)
        }
        if (backingPlayedMs != null && backingDurationMs != null && backingDurationMs > 0) {
            BackingProgressLine(backingPlayedMs, backingDurationMs, Modifier.padding(top = ProgressTop))
        }
        if (noisy) NoiseLine(Modifier.padding(top = NoiseTop))
    }
}

/** The dot of recording with a halo of 4 at 0.2 around it — drawn outside its 12 dp, taking no room. */
@Composable
private fun HaloDot() {
    val red = ViolinTheme.recording
    Box(
        Modifier
            .size(Dot)
            .drawBehind {
                drawCircle(red.copy(alpha = HALO_ALPHA), radius = size.minDimension / 2 + DotHalo.toPx())
                drawCircle(red)
            },
    )
}

/** Grey bars that follow the loudness and nothing else (handoff 13d1): no zone colours, no notes. */
@Composable
private fun LevelBars(levels: List<Float>, quiet: Boolean, modifier: Modifier) {
    val color = ViolinTheme.textTertiary
    val description = stringResource(Res.string.take_level_description)
    Canvas(
        modifier = modifier
            .height(LevelsHeight)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        if (levels.isEmpty()) return@Canvas
        val gap = LevelGap.toPx()
        val width = (size.width - gap * (levels.size - 1)) / levels.size
        if (width <= 0f) return@Canvas
        val least = LevelMin.toPx()
        levels.forEachIndexed { index, raw ->
            val level = (if (quiet) raw * QUIET_SHARE else raw).coerceIn(0f, 1f)
            val height = least + (size.height - least) * level
            drawRoundRect(
                color = color,
                topLeft = Offset(index * (width + gap), (size.height - height) / 2),
                size = Size(width, height),
                cornerRadius = CornerRadius(LevelCorner.toPx()),
                alpha = LEVEL_ALPHA,
            )
        }
    }
}

@Composable
private fun StopCircle(onStop: () -> Unit, size: Dp) {
    val label = stringResource(Res.string.record_stop)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .border(StopBorder, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable(role = Role.Button, onClick = onStop)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(StopSquare).background(ViolinTheme.recording, RoundedCornerShape(StopCorner)))
    }
}

/**
 * How far the backing of a take has played: its sign, a thin track and «1:12 / 3:40» (5.29 R4) — under the recording bar of the
 * piece, and over the shutter of the own camera, where [color] is the light text of a picture and [track] its glass.
 */
@Composable
fun BackingProgressLine(
    playedMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    track: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    val fraction = if (durationMs > 0) (playedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ProgressGap)) {
        AppIcon(AppIcons.Backing, contentDescription = null, tint = color, size = ProgressIcon)
        Box(
            Modifier
                .weight(1f)
                .height(ProgressTrack)
                .clip(RoundedCornerShape(ProgressTrack / 2))
                .background(track),
        ) {
            Box(Modifier.fillMaxWidth(fraction).height(ProgressTrack).background(color))
        }
        Text(
            "${Formats.duration(playedMs.coerceAtMost(durationMs))} / ${Formats.duration(durationMs)}",
            color = color,
            maxLines = 1,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR_FIGURES),
        )
    }
}

/** «Слишком шумно» under the bar: the hollow «may not» dot of the status line of Live, nothing like the red dot above it. */
@Composable
private fun NoiseLine(modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(NoiseDot).border(NoiseRing, ViolinTheme.statusColors.blocked, CircleShape))
        Text(
            stringResource(Res.string.live_too_noisy),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
        )
    }
}

/**
 * The red dot of recording inside «Записать дубль» (spec 3.36.1, 5.29 R4): [size] 14 with a ring of 3 of white at 0.55 around it —
 * 12 in a zone of 48. The sign of recording, not a zone.
 */
@Composable
fun RecordDot(size: Dp = 14.dp, modifier: Modifier = Modifier) {
    val red = ViolinTheme.recording
    Box(
        modifier
            .size(size)
            .drawBehind {
                drawCircle(Color.White.copy(alpha = RECORD_DOT_RING_ALPHA), radius = size.toPx() / 2 + RecordDotRing.toPx())
                drawCircle(red)
            },
    )
}
