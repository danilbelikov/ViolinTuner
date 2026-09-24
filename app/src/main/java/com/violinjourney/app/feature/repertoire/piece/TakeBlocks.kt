package com.violinjourney.app.feature.repertoire.piece

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.R
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.icons.IconSizes
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.history.Selection
import com.violinjourney.app.feature.history.SelectionIntent
import com.violinjourney.app.feature.history.components.CardActions
import com.violinjourney.app.feature.history.components.RecordCard
import com.violinjourney.app.feature.history.components.SelectAction
import com.violinjourney.app.feature.live.components.RecordButton
import com.violinjourney.app.feature.repertoire.takesLabel
import kotlinx.datetime.TimeZone

private val CardCorner = 16.dp
private val RecDot = 10.dp
private val LevelBarWidth = 4.dp
private val LevelBarGap = 3.dp
private val LevelMinHeight = 4.dp
private val LevelMaxHeight = 20.dp
private val ChartWidth = 160.dp
private val TakesTitleHeight = 32.dp
private val ChartHeight = 44.dp
private const val TABULAR_FIGURES = "tnum"
private const val REC_PULSE_MS = 1_200
private const val REC_PULSE_MIN_ALPHA = 0.35f
private const val LEVEL_MIN_ALPHA = 0.5f
private const val DISABLED_ALPHA = 0.4f
private const val CHART_MIN = 50f
private const val CHART_MAX = 100f
private const val CHART_GOOD = 75f

/**
 * «Записать дубль» (spec 3.15, handoff 13c1, 13d1, 13d2): the record button of Live with words
 * beside it — no heading, the button is loud enough. While a take runs the words give way to a
 * dot, a timer and a neutral row of loudness: the screen says "I hear you", never "you hit it".
 */
@Composable
fun RecordTakeRow(
    take: TakeState,
    onIntent: (PieceIntent) -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: androidx.compose.ui.unit.Dp = 72.dp,
    /** Under the backing without headphones (spec 3.32): the button sleeps, the chip above says why. */
    blocked: Boolean = false,
    /** Stands under the words, in their column: the quiet second way to a take (spec 3.19). */
    below: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val refused = take.micPermission == false || (blocked && !take.recording)
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
                blocked && take.micPermission != false -> {
                    Text(stringResource(R.string.take_record), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                }
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
            if (below != null) Box(Modifier.padding(top = 6.dp)) { below() }
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
    take.backingPlayedMs?.let { played -> take.backingDurationMs?.let { duration -> BackingProgressLine(played, duration) } }
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
        Text(stringResource(R.string.take_progress, progress.lastScore, progress.maxScore), color = colors.onSurface, style = style)
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

/** The takes of the piece: the one marked as the best first, the rest newest first; a fresh one glows for a moment (handoff 22e1). */
@Composable
fun TakesBlock(
    takes: List<TakeItem>,
    zone: TimeZone,
    onIntent: (PieceIntent) -> Unit,
    modifier: Modifier = Modifier,
    actions: CardActions? = null,
    selection: Selection = Selection(),
    /** No «Выбрать» while a take is being recorded (spec 3.18). */
    canSelect: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.heightIn(min = TakesTitleHeight), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.takes_title),
                modifier = Modifier.weight(1f),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            )
            when {
                // the number takes the place of the action, so the line does not go empty (handoff 19f1)
                selection.active -> Text(
                    text = takes.size.toString(),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontFeatureSettings = TABULAR_FIGURES),
                )
                takes.isNotEmpty() && canSelect -> SelectAction(
                    onClick = { onIntent(PieceIntent.Select(SelectionIntent.SelectClicked)) },
                    modifier = Modifier.offset(x = 10.dp),
                )
            }
        }
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
            takes.forEach { take ->
                val id = take.card.id
                TakeCard(
                    take, zone, actions,
                    selected = if (selection.active) id in selection.ids else null,
                    onLongClick = { onIntent(PieceIntent.Select(SelectionIntent.CardLongPressed(id))) }.takeIf { canSelect },
                ) { onIntent(PieceIntent.TakeClicked(id)) }
            }
        }
    }
}

/**
 * A take under the name of its piece (handoff 22e): six lines of «Менуэт соль мажор» below that
 * very heading would say nothing, so the card is called by its date and its line gives the time;
 * a take with a name of its own keeps the name, and the date moves into the line. Once, either way.
 */
@Composable
private fun TakeCard(take: TakeItem, zone: TimeZone, actions: CardActions?, selected: Boolean?, onLongClick: (() -> Unit)?, onClick: () -> Unit) {
    val card = take.card
    val date = Formats.recordDate(card.date, card.otherYear)
    val duration = Formats.duration(card.durationMs)
    val meta = stringResource(R.string.record_meta, if (card.title == null) Formats.timeOfDay(card.startedAtEpochMs, zone) else date, duration)
    RecordCard(
        card = card,
        title = card.title ?: date,
        // made under the backing: said in words beside the time — the card already carries its note (spec 3.32)
        meta = if (take.underBacking) stringResource(R.string.backing_take_meta, meta) else meta,
        onClick = onClick,
        actions = actions,
        highlighted = take.isNew,
        selected = selected,
        onLongClick = onLongClick,
    )
}
