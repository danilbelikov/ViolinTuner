package com.example.violintuner.feature.repertoire.piece

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.violintuner.R
import com.example.violintuner.core.ui.format.Formats
import com.example.violintuner.core.ui.theme.ViolinTheme
import com.example.violintuner.feature.live.components.RecordButton
import com.example.violintuner.feature.repertoire.takesLabel
import java.time.ZoneId

private val CardCorner = 16.dp
private val RecDot = 10.dp
private val LevelBarWidth = 4.dp
private val LevelBarGap = 3.dp
private val LevelMinHeight = 4.dp
private val LevelMaxHeight = 20.dp
private val ChartWidth = 160.dp
private val ChartHeight = 44.dp
private val ScoreColumnWidth = 52.dp
private val PreviewBarWidth = 4.dp
private val PreviewHeight = 28.dp
private const val TABULAR_FIGURES = "tnum"
private const val REC_PULSE_MS = 1_200
private const val REC_PULSE_MIN_ALPHA = 0.35f
private const val LEVEL_MIN_ALPHA = 0.5f
private const val DISABLED_ALPHA = 0.4f
private const val CHART_MIN = 50f
private const val CHART_MAX = 100f
private const val CHART_GOOD = 75f
private const val NEW_TAKE_FADE_MS = 1_500

/**
 * «Записать дубль» (spec 3.15, handoff 13c1, 13d1, 13d2): the record button of Live with words
 * beside it — no heading, the button is loud enough. While a take runs the words give way to a
 * dot, a timer and a neutral row of loudness: the screen says "I hear you", never "you hit it".
 */
@Composable
fun RecordTakeRow(take: TakeState, onIntent: (PieceIntent) -> Unit, modifier: Modifier = Modifier, buttonSize: androidx.compose.ui.unit.Dp = 72.dp) {
    val colors = MaterialTheme.colorScheme
    val refused = take.micPermission == false
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        RecordButton(
            recording = take.recording,
            enabled = !refused,
            onClick = { onIntent(PieceIntent.RecordClicked) },
            modifier = Modifier
                .size(buttonSize)
                .alpha(if (refused) DISABLED_ALPHA else 1f),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when {
                take.recording -> RecordingWords(take)
                refused -> {
                    Text(stringResource(R.string.take_no_permission), color = colors.onSurface, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp))
                    TextButton(onClick = { onIntent(PieceIntent.GrantMicClicked) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        Text(stringResource(R.string.take_grant_permission))
                    }
                }
                else -> {
                    Text(stringResource(R.string.take_record), color = colors.onSurface, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                    Text(stringResource(R.string.take_record_hint), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp))
                }
            }
        }
    }
}

@Composable
private fun RecordingWords(take: TakeState) {
    val colors = MaterialTheme.colorScheme
    val pulse by rememberInfiniteTransition(label = "rec").animateFloat(
        initialValue = 1f,
        targetValue = REC_PULSE_MIN_ALPHA,
        animationSpec = InfiniteRepeatableSpec(tween(REC_PULSE_MS / 2), RepeatMode.Reverse),
        label = "recDot",
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .size(RecDot)
                .alpha(pulse)
                .background(ViolinTheme.zoneColors.off, CircleShape),
        )
        Text(
            text = stringResource(R.string.take_recording, Formats.timer(take.elapsedSeconds * MS_PER_SECOND)),
            color = colors.onSurface,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR_FIGURES),
        )
    }
    LevelBars(take.levels, quiet = take.problem != null)
    take.problem?.let { problem ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // The hollow "may not" dot of the Live status line: nothing like the pulsing red dot of the recording above it.
            Box(
                Modifier
                    .size(8.dp)
                    .border(2.dp, ViolinTheme.statusColors.blocked, CircleShape),
            )
            Text(
                text = stringResource(if (problem == TakeProblem.TOO_NOISY) R.string.live_too_noisy else R.string.live_mic_unavailable),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            )
        }
    }
}

private const val MS_PER_SECOND = 1_000L

/** Grey bars that follow the loudness and nothing else: no zone colors, no notes (handoff 13d1). */
@Composable
private fun LevelBars(levels: List<Float>, quiet: Boolean) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val description = stringResource(R.string.take_level_description)
    Canvas(
        modifier = Modifier
            .size(width = LevelBarWidth * levels.size + LevelBarGap * (levels.size - 1).coerceAtLeast(0), height = LevelMaxHeight)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        val barWidth = LevelBarWidth.toPx()
        val step = barWidth + LevelBarGap.toPx()
        levels.forEachIndexed { index, raw ->
            val level = if (quiet) raw * QUIET_SHARE else raw
            val barHeight = LevelMinHeight.toPx() + (LevelMaxHeight - LevelMinHeight).toPx() * level.coerceIn(0f, 1f)
            drawRoundRect(
                color = color,
                topLeft = Offset(index * step, (size.height - barHeight) / 2),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2),
                alpha = LEVEL_MIN_ALPHA + (1f - LEVEL_MIN_ALPHA) * level.coerceIn(0f, 1f),
            )
        }
    }
}

private const val QUIET_SHARE = 0.4f

/** «последний 82 % · лучший 88 %», «6 дублей» and the scores of the takes as a little line (handoff 13c1). */
@Composable
fun TakeProgressCard(progress: TakeProgress, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontFeatureSettings = TABULAR_FIGURES)
    // the summary gets the whole width: beside the chart it wrapped in the middle of «лучший 88 %»
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer, RoundedCornerShape(CardCorner))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.take_progress, progress.lastScore, progress.bestScore), color = colors.onSurface, style = style)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(takesLabel(progress.scores.size), color = colors.onSurfaceVariant, style = style, modifier = Modifier.weight(1f))
            ScoreChart(progress.scores)
        }
    }
}

/** Scores in the order of recording on a 50–100 scale with a dashed line at 75: the scale of the weekly chart of «Записи». */
@Composable
private fun ScoreChart(scores: List<Int>) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val description = stringResource(R.string.take_chart_description, scores.joinToString())
    Canvas(
        modifier = Modifier
            .size(ChartWidth, ChartHeight)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        val radius = 3.5.dp.toPx()
        fun yOf(score: Float) = radius + (size.height - radius * 2) * (1f - ((score - CHART_MIN) / (CHART_MAX - CHART_MIN)).coerceIn(0f, 1f))
        val good = yOf(CHART_GOOD)
        drawLine(grid, Offset(0f, good), Offset(size.width, good), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
        val points = scores.mapIndexed { index, score ->
            val x = if (scores.size == 1) size.width / 2 else radius + (size.width - radius * 2) * index / (scores.size - 1)
            Offset(x, yOf(score.toFloat()))
        }
        points.zipWithNext().forEach { (from, to) -> drawLine(line, from, to, 2.dp.toPx(), StrokeCap.Round) }
        points.forEach { drawCircle(line, radius, it) }
    }
}

/** The takes of the piece, newest first; the best one is marked, a fresh one glows for a moment (handoff 13c1b, 13d3). */
@Composable
fun TakesBlock(takes: List<TakeItem>, zone: ZoneId, onIntent: (PieceIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.takes_title), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp))
        if (takes.isEmpty()) {
            Text(
                text = stringResource(R.string.takes_empty),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Start,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
            )
        } else {
            takes.forEach { take -> TakeCard(take, zone) { onIntent(PieceIntent.TakeClicked(take.card.id)) } }
        }
    }
}

@Composable
private fun TakeCard(take: TakeItem, zone: ZoneId, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val zoneColors = ViolinTheme.zoneColors
    val repertoire = ViolinTheme.repertoireColors
    val card = take.card
    val shape = RoundedCornerShape(CardCorner)
    val background by animateColorAsState(if (take.isNew) repertoire.takeNew else colors.surfaceContainer, tween(NEW_TAKE_FADE_MS), label = "takeBackground")
    val ring by animateColorAsState(if (take.isNew) colors.primary else colors.primary.copy(alpha = 0f), tween(NEW_TAKE_FADE_MS), label = "takeRing")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(1.5.dp, ring, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.width(ScoreColumnWidth), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = card.scorePercent.toString(),
                color = zoneColors.colorFor(card.scoreZone),
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp, lineHeight = 26.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = TABULAR_FIGURES),
            )
            Text(stringResource(R.string.history_percent_sign), color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // A take is told from the others by its date: they all carry the name of the piece.
                Text(
                    text = card.title ?: Formats.dayAndMonth(card.startedAtEpochMs, zone),
                    modifier = Modifier.weight(1f, fill = false),
                    color = colors.onSurface,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                )
                if (take.best) {
                    Text(
                        text = stringResource(R.string.take_best),
                        modifier = Modifier
                            .border(1.dp, colors.primary, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                        color = colors.primary,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    )
                }
            }
            Text(
                text = stringResource(R.string.take_meta, Formats.duration(card.durationMs), Formats.signedCents(card.biasCents)),
                modifier = Modifier.padding(top = 2.dp),
                color = colors.onSurfaceVariant,
                maxLines = 1,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontFeatureSettings = TABULAR_FIGURES),
            )
        }
        Row(modifier = Modifier.height(PreviewHeight), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
            card.previewZones.forEach { previewZone ->
                val share = when (previewZone) {
                    com.example.violintuner.core.domain.Zone.IN_TUNE -> 1f
                    com.example.violintuner.core.domain.Zone.NEAR -> 0.57f
                    com.example.violintuner.core.domain.Zone.OFF -> 0.29f
                }
                Box(
                    Modifier
                        .width(PreviewBarWidth)
                        .height(PreviewHeight * share)
                        .background(zoneColors.colorFor(previewZone), RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}
