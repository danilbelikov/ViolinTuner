package com.violinjourney.app.feature.repertoire.piece

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.components.AppButton
import com.violinjourney.app.core.ui.components.AppButtonStyle
import com.violinjourney.app.core.ui.components.dimmedWhen
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.AppShapes
import com.violinjourney.app.feature.history.Selection
import com.violinjourney.app.feature.history.SelectionIntent
import com.violinjourney.app.feature.history.components.CardActions
import com.violinjourney.app.feature.history.components.RecordCard
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.record_meta
import com.violinjourney.app.shared.resources.selection_select
import com.violinjourney.app.shared.resources.take_chart_description
import com.violinjourney.app.shared.resources.take_last
import com.violinjourney.app.shared.resources.take_max
import com.violinjourney.app.shared.resources.take_progress_description
import com.violinjourney.app.shared.resources.takes_empty_text
import com.violinjourney.app.shared.resources.takes_empty_title
import com.violinjourney.app.shared.resources.takes_title
import org.jetbrains.compose.resources.stringResource
import kotlinx.datetime.TimeZone

// «Дубли» of an element (spec 3.36.4, 5.29 R4, «Произведение»; repertoire.html 3).
private val CardGap = 8.dp
private val ChartHeight = 44.dp
private val SummaryGap = 14.dp
private val EmptyTitleGap = 6.dp
private const val TABULAR_FIGURES = "tnum"
private const val CHART_MIN = 50f
private const val CHART_MAX = 100f
private const val CHART_GOOD = 75f

/**
 * «Дубли» and their number by the title, «Выбрать» at the right edge (spec 3.18, 3.36.4) — gone in the selection mode, while the
 * number stays; from two takes on the summary «последний 82 %» · «максимум 88 %» with the little chart of every score (5.9); then
 * the cards of the takes as they are until R5, the best one first, a fresh one glowing for a moment (3.21). Without takes — «Дублей
 * пока нет» in the middle, without the title and the summary. While a take is [recording] the summary, the cards and «Выбрать»
 * sleep: a card opens the recording's screen, «Звук» or «Поделиться» — each would end the take (spec 3.15). While takes are
 * picked, all but the list steps aside.
 */
@Composable
internal fun TakesSection(
    takes: List<TakeItem>,
    progress: TakeProgress?,
    zone: TimeZone,
    onIntent: (PieceIntent) -> Unit,
    recording: Boolean,
    modifier: Modifier = Modifier,
    actions: CardActions? = null,
    selection: Selection = Selection(),
) {
    if (takes.isEmpty()) {
        EmptyTakes(modifier)
        return
    }
    Column(modifier) {
        BlockTitle(
            stringResource(Res.string.takes_title),
            beside = takes.size.toString(),
            action = if (selection.active) {
                null
            } else {
                {
                    AppButton(
                        text = stringResource(Res.string.selection_select),
                        onClick = { onIntent(PieceIntent.Select(SelectionIntent.SelectClicked)) },
                        modifier = Modifier.dimmedWhen(recording),
                        style = AppButtonStyle.Text,
                        icon = AppIcons.Select,
                    )
                }
            },
        )
        Column(verticalArrangement = Arrangement.spacedBy(CardGap)) {
            if (progress != null) TakeProgressCard(progress, Modifier.dimmedWhen(recording || selection.active))
            takes.forEach { take ->
                // A card keeps its own highlight, press and star when a new take lands on top or the best one moves first.
                key(take.card.id) {
                    val id = take.card.id
                    TakeCard(
                        take, zone, actions,
                        selected = if (selection.active) id in selection.ids else null,
                        onLongClick = { onIntent(PieceIntent.Select(SelectionIntent.CardLongPressed(id))) }.takeIf { !recording },
                        modifier = Modifier.dimmedWhen(recording),
                    ) { onIntent(PieceIntent.TakeClicked(id)) }
                }
            }
        }
    }
}

/** «Дублей пока нет» and «Запишите первый — увидите, как меняется произведение.» in the middle: one first step (spec 3.36.4). */
@Composable
private fun EmptyTakes(modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(Res.string.takes_empty_title),
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.ExtraBold),
        )
        Text(
            stringResource(Res.string.takes_empty_text),
            modifier = Modifier.padding(top = EmptyTitleGap),
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 21.sp),
        )
    }
}

/**
 * «последний 82 %» and «максимум 88 %» on two lines, the figures larger, and beside them the scores of the takes as a line on the
 * scale of 50–100 with a dash at 75 (spec 5.9, 5.29 R4). One description: «последний 82 %, максимум 88 %» and the chart's own.
 */
@Composable
fun TakeProgressCard(progress: TakeProgress, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val said = stringResource(Res.string.take_progress_description, progress.lastScore, progress.maxScore) + ", " +
        stringResource(Res.string.take_chart_description, progress.scores.joinToString())
    val words = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 20.sp, fontFeatureSettings = TABULAR_FIGURES)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer, AppShapes.M)
            .clearAndSetSemantics { contentDescription = said }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SummaryGap),
    ) {
        Column {
            ScoreLine(stringResource(Res.string.take_last, progress.lastScore), progress.lastScore, words)
            ScoreLine(stringResource(Res.string.take_max, progress.maxScore), progress.maxScore, words)
        }
        ScoreChart(progress.scores, Modifier.weight(1f).height(ChartHeight))
    }
}

/** «последний 82 %»: the word in the second level at 13 sp, the figure and its sign in the colour of the text at 15 sp. */
@Composable
private fun ScoreLine(line: String, score: Int, style: TextStyle) {
    val colors = MaterialTheme.colorScheme
    val at = line.lastIndexOf(score.toString()).takeIf { it >= 0 } ?: line.length
    Text(
        buildAnnotatedString {
            append(line.substring(0, at))
            withStyle(SpanStyle(color = colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)) { append(line.substring(at)) }
        },
        color = colors.onSurfaceVariant,
        maxLines = 1,
        style = style,
    )
}

/** Scores in the order of recording on a 50–100 scale with a dashed line at 75: the scale of the weekly chart of «Записи». */
@Composable
private fun ScoreChart(scores: List<Int>, modifier: Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
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

/**
 * A take under the name of its piece (handoff 22e): six lines of «Менуэт соль мажор» below that
 * very heading would say nothing, so the card is called by its date and its line gives the time;
 * a take with a name of its own keeps the name, and the date moves into the line. Once, either way.
 */
@Composable
private fun TakeCard(
    take: TakeItem,
    zone: TimeZone,
    actions: CardActions?,
    selected: Boolean?,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val card = take.card
    val date = Formats.recordDate(card.date, card.otherYear)
    val duration = Formats.duration(card.durationMs)
    val meta = stringResource(Res.string.record_meta, if (card.title == null) Formats.timeOfDay(card.startedAtEpochMs, zone) else date, duration)
    RecordCard(
        card = card,
        title = card.title ?: date,
        // a take made under the backing gets its sign and «под минусовку» from the card itself, as in every list (spec 3.32)
        meta = meta,
        onClick = onClick,
        modifier = modifier,
        actions = actions,
        highlighted = take.isNew,
        selected = selected,
        onLongClick = onLongClick,
    )
}
