package com.example.violintuner.feature.repertoire.sections

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.violintuner.R
import com.example.violintuner.core.domain.repertoire.PieceSection
import com.example.violintuner.core.domain.repertoire.SectionCount
import com.example.violintuner.core.domain.repertoire.SectionRef
import com.example.violintuner.core.ui.icons.AppIcons
import com.example.violintuner.core.ui.icons.IconLabel
import com.example.violintuner.core.ui.theme.ViolinTheme

private val CardCorner = 16.dp
private val CardSpacing = 8.dp
private val BarHeight = 6.dp
private val BarGap = 2.dp
private val AddHeight = 44.dp
private const val BAR_MS = 400
private const val TABULAR_FIGURES = "tnum"
/** Rows of «Время по элементам» before «Все N»: three upright keep every section card above the fold (handoff 30h1), five in the landscape column. */
private const val TIME_ROWS_UPRIGHT = 3
const val TIME_ROWS_LANDSCAPE = 5

/**
 * The way into the repertoire (spec 3.22, handoff 24a1): its sections as cards, then «Добавить раздел». Items of the
 * tab's one lazy list. Above the sections, [showTime] — «Время по элементам» (spec 3.28, handoff 30h1); in landscape
 * it stands in the left column instead.
 */
fun LazyListScope.sectionItems(state: SectionsState, onIntent: (SectionsIntent) -> Unit, showTime: Boolean = true) {
    if (state.loading) return
    item(key = "sectionsTotal") {
        // in the place of the former «5 произведений»: everything there is, and how much of it is learnt
        Text(
            text = stringResource(R.string.section_learned, state.total.learned, state.total.total),
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontFeatureSettings = TABULAR_FIGURES),
        )
    }
    if (showTime) {
        state.time?.let { time ->
            item(key = "pieceTime") { PieceTimeCardView(time, visibleRows = TIME_ROWS_UPRIGHT, onIntent = onIntent, modifier = Modifier.padding(top = CardSpacing)) }
        }
    }
    items(state.cards.size, key = { "section-" + com.example.violintuner.feature.repertoire.SectionKeys.keyOf(state.cards[it].ref) }) { index ->
        val card = state.cards[index]
        SectionCardRow(card, onClick = { onIntent(SectionsIntent.SectionClicked(card.ref)) }, modifier = Modifier.padding(top = CardSpacing))
    }
    item(key = "sectionsAdd") {
        TextButton(
            onClick = { onIntent(SectionsIntent.AddClicked) },
            modifier = Modifier
                .padding(top = CardSpacing)
                .fillMaxWidth()
                .height(AddHeight),
        ) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                IconLabel(
                    icon = AppIcons.Plus,
                    text = stringResource(R.string.section_add),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}

/** The name of a section: a word of the interface for the four built-in ones, the player's own otherwise. */
@Composable
fun sectionName(ref: SectionRef, name: String?): String = when (ref) {
    is SectionRef.Custom -> name.orEmpty()
    is SectionRef.BuiltIn -> stringResource(
        when (ref.section) {
            PieceSection.PIECES -> R.string.section_pieces
            PieceSection.SCALES -> R.string.section_scales
            PieceSection.ETUDES -> R.string.section_etudes
            PieceSection.STROKES -> R.string.section_strokes
        },
    )
}

/** «выучено 4 из 12», or «пока пусто». */
@Composable
fun sectionCountLabel(count: SectionCount): String =
    if (count.total == 0) stringResource(R.string.section_empty_count) else stringResource(R.string.section_learned, count.learned, count.total)

@Composable
private fun SectionCardRow(card: SectionCard, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(CardCorner)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = sectionName(card.ref, card.name),
                modifier = Modifier.weight(1f),
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = sectionCountLabel(card.count),
                // when all of it is learnt the count steps forward: there is nothing left to the right of it
                color = if (card.count.allLearned) colors.onSurface else colors.onSurfaceVariant,
                maxLines = 1,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontFeatureSettings = TABULAR_FIGURES),
            )
        }
        SectionBar(card.count)
    }
}

/**
 * The shares of a section (handoff 24b): dark «разбираю», then «учу», then light «выучено» — the
 * bar grows lighter from the left as the section is learnt. No legend: the order never changes.
 * An empty section is an empty track. The shares settle over 400 ms when they change.
 */
@Composable
fun SectionBar(count: SectionCount, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val exercise = ViolinTheme.exerciseColors
    val total = count.total.coerceAtLeast(1).toFloat()
    val reading by animateFloatAsState(count.reading / total, tween(BAR_MS), label = "sectionReading")
    val learning by animateFloatAsState(count.learning / total, tween(BAR_MS), label = "sectionLearning")
    val learned by animateFloatAsState(count.learned / total, tween(BAR_MS), label = "sectionLearned")
    val description = stringResource(R.string.section_bar_description, count.reading, count.learning, count.learned)
    val track = colors.surfaceContainerHigh
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(BarHeight)
            .semantics { contentDescription = description }
            .drawBehind {
                val radius = CornerRadius(size.height / 2)
                drawRoundRect(track, cornerRadius = radius)
                val gap = BarGap.toPx()
                val shares = listOf(reading to exercise.reading, learning to exercise.learning, learned to exercise.learned).filter { it.first > 0.001f }
                val room = size.width - gap * (shares.size - 1).coerceAtLeast(0)
                var x = 0f
                shares.forEach { (share, color) ->
                    val width = room * share
                    drawRoundRect(color, topLeft = Offset(x, 0f), size = Size(width, size.height), cornerRadius = radius)
                    x += width + gap
                }
            },
    )
}

/** «Новый раздел» and «Переименовать раздел»: one field, the limit shown as it is approached. */
@Composable
fun SectionNameDialog(
    title: String,
    confirm: String,
    name: String,
    maxLength: Int,
    canConfirm: Boolean,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    // The field holds its own text, like every field of the app: echoing the state back makes the cursor jump.
    var text by remember { mutableStateOf(name) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it.take(maxLength)
                    onNameChange(text)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
                label = { Text(stringResource(R.string.section_name_label)) },
                supportingText = { Text("${text.length} / $maxLength", style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TABULAR_FIGURES)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canConfirm) onConfirm() }),
                shape = RoundedCornerShape(16.dp),
            )
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = canConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
    )
}
