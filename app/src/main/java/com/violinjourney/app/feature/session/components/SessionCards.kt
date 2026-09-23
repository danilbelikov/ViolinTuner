package com.violinjourney.app.feature.session.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.R
import com.violinjourney.app.core.domain.ViolinString
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.session.ProblemNoteUi
import com.violinjourney.app.feature.session.SessionContent

private val CardCorner = 16.dp
private val CardPadding = 14.dp
private val CardGap = 10.dp
private val DistributionBarHeight = 8.dp
private val ProblemCorner = 14.dp
private val ProblemDot = 10.dp
private const val TABULAR_FIGURES = "tnum"

private val CardTitle = TextStyle(fontSize = 12.sp)
private val NumberLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR_FIGURES)
private val NumberSmall = TextStyle(fontSize = 12.sp, fontFeatureSettings = TABULAR_FIGURES)

@Composable
private fun StatCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .background(colors.surfaceContainer, RoundedCornerShape(CardCorner))
            .padding(CardPadding),
    ) {
        Text(title, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.merge(CardTitle))
        content()
    }
}

/** "Распределение", "Средняя ошибка" side by side, "По струнам" under them (spec 3.10). */
@Composable
fun SessionStatCards(content: SessionContent, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val zoneColors = ViolinTheme.zoneColors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(CardGap)) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(CardGap),
        ) {
            StatCard(
                title = stringResource(R.string.session_card_distribution),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                val shares = listOf(
                    content.scorePercent to zoneColors.inTune,
                    content.nearPercent to zoneColors.near,
                    content.offPercent to zoneColors.off,
                )
                Row(
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 8.dp)
                        .fillMaxWidth()
                        .height(DistributionBarHeight)
                        .clip(CircleShape),
                ) {
                    shares.filter { it.first > 0 }.forEach { (percent, color) ->
                        Box(
                            Modifier
                                .weight(percent.toFloat())
                                .fillMaxHeight()
                                .background(color),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    shares.forEach { (percent, color) ->
                        Text(
                            text = stringResource(R.string.session_percent, percent),
                            color = color,
                            style = MaterialTheme.typography.bodySmall.merge(NumberSmall),
                        )
                    }
                }
            }
            StatCard(
                title = stringResource(R.string.session_card_mean_error),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                Row(modifier = Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = Formats.oneDecimal(content.maeCents),
                        modifier = Modifier.alignByBaseline(),
                        color = colors.onSurface,
                        style = MaterialTheme.typography.headlineMedium.merge(NumberLarge),
                    )
                    Text(
                        text = " " + stringResource(R.string.session_cents_unit),
                        modifier = Modifier.alignByBaseline(),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    text = when {
                        content.biasZone == null -> stringResource(R.string.session_bias_small_none)
                        content.biasCents < 0 ->
                            stringResource(R.string.session_bias_small_down, Formats.signedCents(content.biasCents))
                        else -> stringResource(R.string.session_bias_small_up, Formats.signedCents(content.biasCents))
                    },
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall.merge(CardTitle),
                )
            }
        }
        StatCard(title = stringResource(R.string.session_card_per_string), modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(top = 10.dp)) {
                ViolinString.entries.forEach { string ->
                    val score = content.perString[string]
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = string.note.letter.toString(),
                            color = colors.onSurface,
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                        )
                        Text(
                            text = score?.let { stringResource(R.string.session_percent, it.first) }
                                ?: stringResource(R.string.session_string_not_played),
                            color = score?.let { zoneColors.colorFor(it.second) } ?: colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontFeatureSettings = TABULAR_FIGURES),
                        )
                    }
                }
            }
        }
    }
}

/** Up to three notes that are off on average, or a word of praise (spec 3.10). */
@Composable
fun ProblemNotes(notes: List<ProblemNoteUi>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.session_problem_notes),
            modifier = Modifier.padding(bottom = 2.dp),
            color = colors.onSurface,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        )
        if (notes.isEmpty()) {
            Text(
                text = stringResource(R.string.session_no_problem_notes),
                modifier = Modifier.padding(vertical = 6.dp),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        notes.forEach { ProblemNoteRow(it, ViolinTheme.zoneColors.colorFor(it.zone)) }
    }
}

@Composable
private fun ProblemNoteRow(note: ProblemNoteUi, color: Color) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer, RoundedCornerShape(ProblemCorner))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(ProblemDot)
                .background(color, CircleShape),
        )
        Text(
            text = stringResource(R.string.session_problem_note, note.note.name, note.string.note.letter.toString()),
            modifier = Modifier.weight(1f),
            color = colors.onSurface,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
        )
        Text(
            text = stringResource(R.string.session_cents_value, Formats.signedCents(note.meanCents)),
            color = color,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR_FIGURES,
            ),
        )
    }
}
