package com.violinjourney.app.feature.sound.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.audio.fx.SoundMeters
import com.violinjourney.app.core.audio.playback.PlayerState
import com.violinjourney.app.core.ui.components.PlayPauseGlyph
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.sound.SoundFormats
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.session_player_pause
import com.violinjourney.app.shared.resources.session_player_play
import com.violinjourney.app.shared.resources.session_player_position
import com.violinjourney.app.shared.resources.sound_ab_original
import com.violinjourney.app.shared.resources.sound_ab_processed
import com.violinjourney.app.shared.resources.sound_meter_limiter
import com.violinjourney.app.shared.resources.sound_meter_none
import com.violinjourney.app.shared.resources.sound_meter_output
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.stringResource

private val MeterHeight = 4.dp
private val LimiterMark = 8.dp
private val PlainTrack = 4.dp
private const val BAR_STEP_DP = 3f
private const val BAR_WIDTH_DP = 2f
private const val MIN_BAR = 0.08f
private const val METER_FLOOR_DB = -60.0
/** The whole meter in a second: fast enough to follow a phrase, slow enough for the eye to read a peak. */
private const val METER_FALL_DB_PER_SECOND = 60.0
private const val LIMITER_LIT_MS = 600L
private const val NUMBER_EVERY_MS = 100L
private const val DISABLED_ALPHA = 0.38f
private const val TABULAR_FIGURES = "tnum"

/** Sizes of the mini player: the usual one and the one of a 360 × 640 screen (handoff `sizes`). */
data class MiniPlayerMetrics(val button: Dp, val wave: Dp, val ab: Dp) {
    companion object {
        val Regular = MiniPlayerMetrics(button = 48.dp, wave = 36.dp, ab = 32.dp)
        val Compact = MiniPlayerMetrics(button = 40.dp, wave = 24.dp, ab = 28.dp)
    }
}

/**
 * Always in sight on the «Звук» screen: everything here is set by ear. Play and pause, the
 * waveform that is also the seek bar, the time, A/B and the level that leaves the chain.
 * [meters] is read where it is drawn — thirty readings a second recompose nothing but a number.
 */
@Composable
fun MiniPlayer(
    player: PlayerState,
    waveform: List<Float>?,
    meters: State<SoundMeters?>,
    metrics: MiniPlayerMetrics,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onOriginal: (original: Boolean, held: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(metrics.button)
                    .clip(CircleShape)
                    .background(colors.primary)
                    .clickable(
                        onClickLabel = stringResource(if (player.playing) Res.string.session_player_pause else Res.string.session_player_play),
                        role = Role.Button,
                        onClick = onPlayPause,
                    ),
                contentAlignment = Alignment.Center,
            ) { PlayPauseGlyph(playing = player.playing, tint = colors.onPrimary) }
            SeekWave(player, waveform, metrics.wave, onSeek, Modifier.weight(1f))
            HoldableAb(original = player.original, enabled = player.processed, height = metrics.ab, onOriginal = onOriginal)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "${Formats.duration(player.positionMs)} / ${Formats.duration(player.durationMs)}",
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontFeatureSettings = TABULAR_FIGURES),
                modifier = Modifier.widthIn(min = 76.dp),
            )
            Text(stringResource(Res.string.sound_meter_output), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp))
            OutputMeter(meters, Modifier.weight(1f))
        }
    }
}

/** The waveform as the seek bar: the played part is primary, the finger goes anywhere along it. Until reckoned — a plain track. */
@Composable
private fun SeekWave(player: PlayerState, waveform: List<Float>?, height: Dp, onSeek: (Long) -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val rest = ViolinTheme.soundColors.waveRest
    val duration = player.durationMs.coerceAtLeast(1)
    var dragged by remember { mutableStateOf<Float?>(null) }
    val currentOnSeek by rememberUpdatedState(onSeek)
    val description = stringResource(Res.string.session_player_position)
    val played = dragged ?: (player.positionMs.toFloat() / duration)
    Box(
        modifier = modifier
            .height(height)
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(played, 0f..1f)
                setProgress { fraction -> currentOnSeek((fraction.coerceIn(0f, 1f) * duration).toLong()); true }
            }
            .pointerInput(duration) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun at(x: Float) = (x / size.width).coerceIn(0f, 1f)
                    dragged = at(down.position.x)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        dragged = at(change.position.x)
                    }
                    dragged?.let { currentOnSeek((it * duration).toLong()) }
                    dragged = null
                }
            }
            .drawBehind {
                val bars = waveform
                if (bars == null || size.height < 20.dp.toPx()) {
                    val track = PlainTrack.toPx()
                    val y = (size.height - track) / 2
                    drawRoundRect(rest, Offset(0f, y), Size(size.width, track), CornerRadius(track / 2))
                    drawRoundRect(colors.primary, Offset(0f, y), Size(size.width * played, track), CornerRadius(track / 2))
                    drawCircle(colors.primary, 7.dp.toPx(), Offset(size.width * played, size.height / 2))
                } else {
                    val step = BAR_STEP_DP.dp.toPx()
                    val width = BAR_WIDTH_DP.dp.toPx()
                    val count = (size.width / step).toInt().coerceAtLeast(1)
                    for (index in 0 until count) {
                        // as many columns as fit: the 120 reckoned ones are spread over them
                        val level = bars[(index * bars.size / count).coerceIn(0, bars.lastIndex)].coerceAtLeast(MIN_BAR)
                        val barHeight = size.height * level
                        val x = index * step
                        drawRoundRect(
                            color = if (x / size.width <= played) colors.primary else rest,
                            topLeft = Offset(x, (size.height - barHeight) / 2),
                            size = Size(width, barHeight),
                            cornerRadius = CornerRadius(width / 2),
                        )
                    }
                }
            },
    )
}

/** «A | B»; a finger held on A plays the original only for as long as it stays there (handoff `anims`). */
@Composable
private fun HoldableAb(original: Boolean, enabled: Boolean, height: Dp, onOriginal: (Boolean, Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val currentOnOriginal by rememberUpdatedState(onOriginal)
    val labels = listOf(stringResource(Res.string.sound_ab_original), stringResource(Res.string.sound_ab_processed))
    Row(
        modifier = Modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(colors.surfaceContainerHigh),
    ) {
        listOf(true, false).forEachIndexed { index, value ->
            val chosen = enabled && original == value
            Box(
                modifier = Modifier
                    .size(width = height + 2.dp, height = height)
                    .clip(RoundedCornerShape(height / 2))
                    .background(if (chosen) colors.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                    .semantics {
                        contentDescription = labels[index]
                        role = Role.RadioButton
                        selected = chosen
                    }
                    .pointerInput(enabled, value) {
                        if (!enabled) return@pointerInput
                        detectTapGestures(
                            onTap = { currentOnOriginal(value, false) },
                            onLongPress = { if (value) currentOnOriginal(true, true) },
                            onPress = {
                                tryAwaitRelease()
                                if (value) currentOnOriginal(false, true) // lets go only what a hold took
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (value) "A" else "B",
                    color = (if (chosen) colors.onPrimaryContainer else colors.onSurfaceVariant).copy(alpha = if (enabled) 1f else DISABLED_ALPHA),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

/**
 * The level at the output: up at once, down at a readable pace, stepped by frames only while
 * there is something to show. The mark at its end lights up for a moment when the limiter has
 * had to work, and the number gives way to the word — «ограничитель», not "overload": there
 * will be none in the file, but too much was asked for.
 */
@Composable
private fun OutputMeter(meters: State<SoundMeters?>, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val sound = ViolinTheme.soundColors
    val level = remember { mutableFloatStateOf(0f) }
    var limitedAt by remember { mutableLongStateOf(Long.MIN_VALUE) }
    var lit by remember { mutableStateOf(false) }
    var number by remember { mutableStateOf<Double?>(null) }

    LaunchedEffect(Unit) {
        var lastFrame = 0L
        var lastNumberAt = 0L
        while (true) {
            if (meters.value == null && level.floatValue <= 0f && !lit) {
                number = null
                snapshotFlow { meters.value }.first { it != null } // asleep until sound plays through the chain
                lastFrame = 0
            }
            withFrameMillis { now ->
                val reading = meters.value
                val target = reading?.let { ((it.outputPeakDb - METER_FLOOR_DB) / -METER_FLOOR_DB).toFloat().coerceIn(0f, 1f) } ?: 0f
                val elapsed = if (lastFrame == 0L) 0L else now - lastFrame
                lastFrame = now
                val fallen = level.floatValue - (METER_FALL_DB_PER_SECOND / -METER_FLOOR_DB * elapsed / 1_000.0).toFloat()
                level.floatValue = maxOf(target, fallen, 0f)
                if (reading?.limiting == true) limitedAt = now
                lit = now - limitedAt < LIMITER_LIT_MS
                if (now - lastNumberAt >= NUMBER_EVERY_MS) {
                    lastNumberAt = now
                    number = reading?.outputPeakDb
                }
            }
        }
    }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .weight(1f)
                .height(MeterHeight)
                .clearAndSetSemantics { }
                .drawBehind {
                    drawRoundRect(colors.surfaceContainerHigh, size = size, cornerRadius = CornerRadius(size.height / 2))
                    drawRoundRect(sound.meterLevel, size = Size(size.width * level.floatValue, size.height), cornerRadius = CornerRadius(size.height / 2))
                },
        )
        Box(
            Modifier
                .size(LimiterMark)
                .background(if (lit) sound.meterLimit else colors.surfaceContainerHigh, RoundedCornerShape(2.dp)),
        )
        val shown by remember { derivedStateOf { number } }
        Text(
            text = when {
                lit -> stringResource(Res.string.sound_meter_limiter)
                else -> shown?.takeIf { it > METER_FLOOR_DB }?.let { SoundFormats.decibels(it, signed = true) } ?: stringResource(Res.string.sound_meter_none)
            },
            color = if (lit) sound.meterLimit else colors.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 1,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR_FIGURES),
            // wide enough for «ограничитель» in 12 sp semibold — 80 dp cut its last letter
            modifier = Modifier.width(96.dp),
        )
    }
}
