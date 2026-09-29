package com.violinjourney.app.feature.session.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.domain.Note
import com.violinjourney.app.core.domain.ViolinString
import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.ui.components.SectionLabel
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.AppShapes
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.session.ProblemNoteUi
import com.violinjourney.app.feature.session.SessionContent
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.dot_separator
import com.violinjourney.app.shared.resources.session_advice_flat
import com.violinjourney.app.shared.resources.session_advice_line
import com.violinjourney.app.shared.resources.session_advice_line_short
import com.violinjourney.app.shared.resources.session_advice_none
import com.violinjourney.app.shared.resources.session_advice_sharp
import com.violinjourney.app.shared.resources.session_bias_flat_line
import com.violinjourney.app.shared.resources.session_bias_none_line
import com.violinjourney.app.shared.resources.session_bias_sharp_line
import com.violinjourney.app.shared.resources.session_card_per_string
import com.violinjourney.app.shared.resources.session_cents_value
import com.violinjourney.app.shared.resources.session_drift_open
import com.violinjourney.app.shared.resources.session_drift_title
import com.violinjourney.app.shared.resources.session_legend_near
import com.violinjourney.app.shared.resources.session_legend_off
import com.violinjourney.app.shared.resources.session_no_problem_notes
import com.violinjourney.app.shared.resources.session_notes_hint
import com.violinjourney.app.shared.resources.session_notes_hint_violin
import com.violinjourney.app.shared.resources.session_notes_label
import com.violinjourney.app.shared.resources.session_percent
import com.violinjourney.app.shared.resources.session_problem_note
import com.violinjourney.app.shared.resources.session_string_not_played
import com.violinjourney.app.shared.resources.session_summary_in_tune
import com.violinjourney.app.shared.resources.session_summary_tolerance
import kotlin.math.abs
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

// The cards of a recording (spec 3.36.5, 5.29 R5): surfaceContainer at a corner of 18, fields 16.
internal val CardPadding = 16.dp
private val CardShape = AppShapes.M
private const val TABULAR_FIGURES = "tnum"

// The summary: the score 60 sp, «%» 26 sp; lying, in the left column — 44 and 22.
private val ScoreStyle = TextStyle(fontSize = 60.sp, lineHeight = 60.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.04).em, fontFeatureSettings = TABULAR_FIGURES)
private val ScoreStyleCompact = ScoreStyle.copy(fontSize = 44.sp, lineHeight = 44.sp)
private val PercentStyle = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold)
private val PercentStyleCompact = PercentStyle.copy(fontSize = 22.sp)
private val ScoreGap = 16.dp
private val ScoreGapCompact = 14.dp
private val CompactVertical = 12.dp
private val SharesTop = 16.dp
private val SharesTopCompact = 10.dp
private val SharesHeight = 10.dp
private val SharesCorner = 6.dp
private val SharesGap = 2.dp
private val LegendTop = 8.dp
private val LegendGap = 14.dp
private val LegendRowGap = 4.dp
private val LegendDotGap = 5.dp
private val BiasTop = 14.dp
private val BiasTopCompact = 8.dp
private val BiasSign = 22.dp
private val BiasSignCompact = 20.dp
private val BiasGap = 12.dp

/** A dot where there is no direction to point — no bias, a note in tune: 8, in the middle of the room of an arrow. */
private val LevelDot = 8.dp

// «Что уходит»: rows of 56 at the least, a line of 1 between them, the arrow 20, the chevron 24.
private val DriftRowHeight = 56.dp
private val DriftTop = 6.dp
private val DriftBottom = 4.dp
private val DriftSign = 20.dp
private val DriftGap = 12.dp

// «По струнам»: four cells on the ground of the screen, a corner of 12, 8 apart.
private val StringsTop = 12.dp
private val StringCellVertical = 8.dp
private val StringsGap = 8.dp

/** The notes of the card «Ноты»: the roll inside a card of the fields of the others — its label 10 over the time line. */
internal val NotesCardPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)

/**
 * The summary of a recording in one card (spec 3.36.5, 5.29 R5), instead of the score over the roll and the cards «Распределение» and
 * «Средняя ошибка»: the score (5.5: the share «в строе») large, «в строе» and «2:05 · допуск ±8 ц» by its bottom edge; the three
 * shares as a bar in the colours of the zones and in words with their numbers; under a line the bias — an arrow in the colour of its
 * zone (a dot «в строе» without one), «В среднем ниже на 6 ц», and the advice with the mean error. [compact] — the left column lying:
 * a smaller score, no line, the advice and the error in one sentence. To TalkBack it is one paragraph, made of what is seen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SummaryCard(content: SessionContent, modifier: Modifier = Modifier, compact: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val zones = ViolinTheme.zoneColors
    val inTune = stringResource(Res.string.session_summary_in_tune)
    val duration = Formats.duration(content.durationMs)
    val tolerance = stringResource(Res.string.session_summary_tolerance, content.toleranceCents.roundToInt())
    val near = stringResource(Res.string.session_legend_near)
    val off = stringResource(Res.string.session_legend_off)
    val bias = biasWordsOf(content)
    val advice = stringResource(
        if (compact) Res.string.session_advice_line_short else Res.string.session_advice_line,
        bias.advice,
        Formats.oneDecimal(content.maeCents),
    )
    val shares = listOf(
        Triple(inTune, content.scorePercent, zones.inTune),
        Triple(near, content.nearPercent, zones.near),
        Triple(off, content.offPercent, zones.off),
    )
    val spoken = listOf(
        listOf(stringResource(Res.string.session_percent, content.scorePercent), inTune, duration, tolerance).joinToString(SPOKEN_PARTS),
        shares.joinToString(SPOKEN_PARTS) { (word, percent, _) -> "$word $percent" },
        listOf(bias.line, advice).joinToString(SPOKEN_PARTS),
    ).joinToString(SPOKEN_GROUPS)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer, CardShape)
            .clearAndSetSemantics { contentDescription = spoken }
            .padding(horizontal = CardPadding, vertical = if (compact) CompactVertical else CardPadding),
    ) {
        Row {
            Row(Modifier.alignBy(LastBaseline)) {
                Text(
                    text = content.scorePercent.toString(),
                    modifier = Modifier.alignByBaseline(),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.displayLarge.merge(if (compact) ScoreStyleCompact else ScoreStyle),
                )
                Text(
                    text = "%",
                    modifier = Modifier.alignByBaseline(),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.headlineMedium.merge(if (compact) PercentStyleCompact else PercentStyle),
                )
            }
            // «в строе» and «2:05 · допуск ±8 ц» by the bottom edge of the score: their last line on its baseline
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (compact) ScoreGapCompact else ScoreGap)
                    .alignBy(LastBaseline),
            ) {
                val small = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = TABULAR_FIGURES)
                Text(inTune, color = colors.onSurfaceVariant, style = small)
                Text(duration + stringResource(Res.string.dot_separator) + tolerance, color = colors.onSurfaceVariant, style = small)
            }
        }
        Row(
            modifier = Modifier
                .padding(top = if (compact) SharesTopCompact else SharesTop)
                .fillMaxWidth()
                .height(SharesHeight)
                .clip(RoundedCornerShape(SharesCorner)),
            horizontalArrangement = Arrangement.spacedBy(SharesGap),
        ) {
            shares.filter { it.second > 0 }.forEach { (_, percent, color) ->
                Box(Modifier.weight(percent.toFloat()).fillMaxHeight().background(color))
            }
        }
        FlowRow(
            modifier = Modifier.padding(top = LegendTop),
            horizontalArrangement = Arrangement.spacedBy(LegendGap),
            verticalArrangement = Arrangement.spacedBy(LegendRowGap),
        ) {
            shares.forEach { (word, percent, color) -> LegendItem(word, percent, color) }
        }
        if (!compact) {
            Box(
                Modifier
                    .padding(top = BiasTop)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.outlineVariant),
            )
        }
        Row(
            modifier = Modifier.padding(top = if (compact) BiasTopCompact else BiasTop),
            horizontalArrangement = Arrangement.spacedBy(BiasGap),
        ) {
            DeviationSign(
                cents = content.biasCents,
                level = content.biasZone == null,
                // the zone of the bias's size (5.5): −6 at a tolerance of ±8 is green; none at all — in tune
                color = zones.colorFor(content.biasZone ?: Zone.IN_TUNE),
                size = if (compact) BiasSignCompact else BiasSign,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = bias.line,
                    color = colors.onSurface,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR_FIGURES),
                )
                Text(
                    text = advice,
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = TABULAR_FIGURES),
                )
            }
        }
    }
}

/** How TalkBack's paragraph of the summary joins its parts and its groups. */
private const val SPOKEN_PARTS = ", "
private const val SPOKEN_GROUPS = "; "

/** The bias of a recording in words (spec 3.36.5): «В среднем ниже на 6 ц» / «…выше…» / «Без смещения», and what to do about it. */
private class BiasWords(val line: String, val advice: String)

@Composable
private fun biasWordsOf(content: SessionContent): BiasWords {
    val cents = abs(content.biasCents).roundToInt()
    return when {
        content.biasZone == null -> BiasWords(stringResource(Res.string.session_bias_none_line), stringResource(Res.string.session_advice_none))
        content.biasCents < 0 -> BiasWords(stringResource(Res.string.session_bias_flat_line, cents), stringResource(Res.string.session_advice_flat))
        else -> BiasWords(stringResource(Res.string.session_bias_sharp_line, cents), stringResource(Res.string.session_advice_sharp))
    }
}

@Composable
private fun LegendItem(word: String, percent: Int, color: Color) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LegendDotGap)) {
        Box(Modifier.size(LevelDot).background(color, CircleShape))
        Text(
            text = buildAnnotatedString {
                append(word)
                append(' ')
                withStyle(SpanStyle(color = colors.onSurface, fontWeight = FontWeight.Bold)) { append(percent.toString()) }
            },
            color = colors.onSurfaceVariant,
            maxLines = 1,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = TABULAR_FIGURES),
        )
    }
}

/**
 * The sign of a deviation (spec 3.36.5, 5.29 R5): the filled arrow of Live up or down by its sign ([cents]) in [color], or — [level] —
 * a dot of 8 in the middle of the same room: no bias, a note in tune. Colour is never the only sign (principle 5).
 */
@Composable
internal fun DeviationSign(cents: Double, level: Boolean, color: Color, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        if (level) {
            Box(Modifier.size(LevelDot).background(color, CircleShape))
        } else {
            AppIcon(if (cents < 0) AppIcons.ArrowDown else AppIcons.ArrowUp, contentDescription = null, tint = color, size = size)
        }
    }
}

/** The label of a card (the label of a section of R1) and, at its right, a caption of 13 sp that gives way before it does. */
@Composable
internal fun CardLabel(title: String, modifier: Modifier = Modifier, caption: String? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SectionLabel(title, Modifier.padding(end = LegendGap))
        if (caption != null) {
            Text(
                text = caption,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
            )
        }
    }
}

/**
 * «Ноты» (spec 3.36.5): the roll of 3.10, unchanged, in a card with its label and «тап — подробно» at the right — of a take under a
 * backing «только скрипка · тап — подробно»: the backing is not on the roll (3.32). [cursor] — where the sound is, read where the
 * cursor is drawn; null without a player.
 */
@Composable
internal fun NotesCard(
    content: SessionContent,
    selectedSegment: Int?,
    onSegment: (Int) -> Unit,
    cursor: (() -> Long)?,
    follow: Boolean,
    modifier: Modifier = Modifier,
) {
    PianoRoll(
        content = content,
        selectedSegment = selectedSegment,
        onSegmentClick = onSegment,
        modifier = modifier,
        cursorMs = cursor,
        followCursor = follow,
        contentPadding = NotesCardPadding,
        label = {
            CardLabel(
                title = stringResource(Res.string.session_notes_label),
                caption = stringResource(if (content.underBacking) Res.string.session_notes_hint_violin else Res.string.session_notes_hint),
            )
        },
    )
}

/**
 * «Что уходит» (spec 3.36.5) — the notes that drift steadily (5.5, up to three), each a row that opens the sheet of the note where it
 * drifts the most: the arrow of its mean in the colour of its zone, «F#5 на струне E», «−22 ц» in that colour and a chevron. None — one
 * line of praise that opens nothing. To TalkBack a row is a button «F#5 на струне E, −22 ц, подробно».
 */
@Composable
internal fun DriftCard(notes: List<ProblemNoteUi>, onNote: (Note) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(colors.surfaceContainer)
            .padding(top = CardPadding, bottom = DriftBottom),
    ) {
        CardLabel(stringResource(Res.string.session_drift_title), Modifier.padding(horizontal = CardPadding))
        Column(Modifier.padding(top = DriftTop)) {
            if (notes.isEmpty()) {
                Box(Modifier.fillMaxWidth().heightIn(min = DriftRowHeight).padding(horizontal = CardPadding), contentAlignment = Alignment.CenterStart) {
                    Text(
                        text = stringResource(Res.string.session_no_problem_notes),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                    )
                }
            }
            notes.forEachIndexed { index, note ->
                if (index > 0) {
                    Box(
                        Modifier
                            .padding(horizontal = CardPadding)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(colors.outlineVariant),
                    )
                }
                DriftRow(note, onNote)
            }
        }
    }
}

@Composable
private fun DriftRow(note: ProblemNoteUi, onNote: (Note) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val color = ViolinTheme.zoneColors.colorFor(note.zone)
    val line = stringResource(Res.string.session_problem_note, note.note.name, note.string.note.letter.toString())
    val cents = stringResource(Res.string.session_cents_value, Formats.signedCents(note.meanCents))
    val open = stringResource(Res.string.session_drift_open)
    val spoken = listOf(line, cents, open).joinToString(SPOKEN_PARTS)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = DriftRowHeight)
            // «подробно» ends the description; as the label of the action too it would be heard twice, and «чтобы подробно» (spec 3.36.5)
            .clickable(role = Role.Button) { onNote(note.note) }
            .semantics { contentDescription = spoken }
            .padding(horizontal = CardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // the words are said once, by the row
        Row(Modifier.weight(1f).clearAndSetSemantics { }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DriftGap)) {
            DeviationSign(note.meanCents, level = false, color = color, size = DriftSign)
            Text(
                text = line,
                modifier = Modifier.weight(1f),
                color = colors.onSurface,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = cents,
                color = color,
                maxLines = 1,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = TABULAR_FIGURES),
            )
            AppIcon(AppIcons.ChevronRight, contentDescription = null, tint = ViolinTheme.textTertiary)
        }
    }
}

/**
 * «По струнам» (spec 3.10, 3.36.5) — the last card: G · D · A · E, each a cell on the ground of the screen with the share «в строе» in
 * the colour of its thresholds — the number alone, as in the mockup: «100 %» of 16 sp would not stand in a cell of the narrow column
 * lying at a large font (≈ 55 dp) — «—» where nothing was played on it. To TalkBack a cell is «G, 91%», the sign said.
 */
@Composable
internal fun StringsCard(content: SessionContent, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val zones = ViolinTheme.zoneColors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer, CardShape)
            .padding(CardPadding),
    ) {
        CardLabel(stringResource(Res.string.session_card_per_string))
        Row(Modifier.padding(top = StringsTop), horizontalArrangement = Arrangement.spacedBy(StringsGap)) {
            ViolinString.entries.forEach { string ->
                val score = content.perString[string]
                val letter = string.note.letter.toString()
                val share = score?.let { stringResource(Res.string.session_percent, it.first) } ?: stringResource(Res.string.session_string_not_played)
                val spoken = listOf(letter, share).joinToString(SPOKEN_PARTS)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(colors.surface, AppShapes.S)
                        .padding(vertical = StringCellVertical)
                        .clearAndSetSemantics { contentDescription = spoken },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = letter,
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = score?.first?.toString() ?: stringResource(Res.string.session_string_not_played),
                        color = score?.let { zones.colorFor(it.second) } ?: colors.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = TABULAR_FIGURES),
                    )
                }
            }
        }
    }
}
