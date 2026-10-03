package com.violinjourney.app.feature.events.performances

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.violinjourney.app.core.domain.events.KindLook
import com.violinjourney.app.core.domain.events.PerformanceRow
import com.violinjourney.app.core.ui.components.AppButton
import com.violinjourney.app.core.ui.components.AppDock
import com.violinjourney.app.core.ui.components.LocalDockInset
import com.violinjourney.app.core.ui.components.ScreenHeader
import com.violinjourney.app.core.ui.components.SectionLabel
import com.violinjourney.app.core.ui.components.arrivalPresses
import com.violinjourney.app.core.ui.components.currentDockMetrics
import com.violinjourney.app.core.ui.components.rememberSmallFileImage
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.events.EventsDimens
import com.violinjourney.app.feature.events.KindSignPlate
import com.violinjourney.app.feature.events.eventNameOf
import com.violinjourney.app.feature.events.kindColors
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.dot_separator
import com.violinjourney.app.shared.resources.event_all_day
import com.violinjourney.app.shared.resources.history_count_few
import com.violinjourney.app.shared.resources.history_count_many
import com.violinjourney.app.shared.resources.history_count_one
import com.violinjourney.app.shared.resources.performances_add
import com.violinjourney.app.shared.resources.performances_ahead
import com.violinjourney.app.shared.resources.performances_empty_text
import com.violinjourney.app.shared.resources.performances_empty_title
import com.violinjourney.app.shared.resources.performances_in_days_few
import com.violinjourney.app.shared.resources.performances_in_days_many
import com.violinjourney.app.shared.resources.performances_in_days_one
import com.violinjourney.app.shared.resources.performances_past
import com.violinjourney.app.shared.resources.performances_program_said
import com.violinjourney.app.shared.resources.performances_row_in_days_few
import com.violinjourney.app.shared.resources.performances_row_in_days_many
import com.violinjourney.app.shared.resources.performances_row_in_days_one
import com.violinjourney.app.shared.resources.performances_title
import com.violinjourney.app.shared.resources.practice_day_events_description
import com.violinjourney.app.shared.resources.practice_day_today
import com.violinjourney.app.shared.resources.practice_day_tomorrow
import com.violinjourney.app.shared.resources.practice_pair_description
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

/**
 * «Выступления» (spec 3.35, 3.36.9; 5.29 R9; events-views.html 7, practice-sheets.html 11): above the tabs, without the bottom bar. The
 * bar of R8 — «назад» and «Выступления» — and one column in the middle, no wider than 560, upright and lying alike (3.36.1 rule 3):
 * «Впереди» and «Прошли» — labels of R1, a group with nothing in it is not shown — and their rows; at the bottom «Добавить выступление»,
 * a zone as wide as the column, its button 48 in a window no higher than 360. Nothing yet — the plate of the sign of the kind, «Здесь
 * будут ваши выступления» and its words in the middle of what the zone leaves, the same button under them; smaller lying. While the
 * events are read — the bar and an empty zone of the height of its button, nothing else. No motion of its own (5.29 R9). Its presses —
 * a row, «Добавить выступление», «назад» — are not heard for the time of a double tap from its first frame ([arrivalPresses]): it comes
 * in under the finger that opened it. Stateless.
 */
@Composable
fun PerformancesScreen(state: PerformancesState, onIntent: (PerformancesIntent) -> Unit, modifier: Modifier = Modifier) {
    // the screen comes in under the finger that opened it: its first row stands where the row «Выступления» of «Записи» stood, and the
    // second tap of a double tap would open the nearest performance over the list; back from a screen above, «назад» of the bar stands
    // where its «назад» stood — not heard for the time of a double tap from the first frame (5.29 R9, review of stage 99)
    val onPress = arrivalPresses(onIntent)
    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        val lying = maxWidth > maxHeight
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = stringResource(Res.string.performances_title),
                onBack = { onPress(PerformancesIntent.BackClicked) },
                titleSize = EventsDimens.PerformancesBarTitle,
            )
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
                AppDock(
                    dock = {
                        if (state.loading) {
                            // nothing blinks while the events are read: the zone keeps the height of its button
                            Spacer(Modifier.height(buttonHeight))
                        } else {
                            AppButton(
                                text = stringResource(Res.string.performances_add),
                                onClick = { onPress(PerformancesIntent.AddClicked) },
                                modifier = Modifier.fillMaxWidth(),
                                icon = AppIcons.Plus,
                                compact = compact,
                            )
                        }
                    },
                    modifier = Modifier.widthIn(max = EventsDimens.ColumnMax).fillMaxSize(),
                    metrics = currentDockMetrics().copy(side = EventsDimens.ScreenSide),
                ) {
                    when {
                        state.loading -> Unit
                        state.empty -> NoPerformances(state.look, lying)
                        else -> PerformanceList(state, lying, onPress)
                    }
                }
            }
        }
    }
}

/** «Впереди», then «Прошли»: a label and the rows of each group that has any, 8 apart; the end clear of the zone and its fade. */
@Composable
private fun PerformanceList(state: PerformancesState, lying: Boolean, onIntent: (PerformancesIntent) -> Unit) {
    val inset = LocalDockInset.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = EventsDimens.ScreenSide,
            end = EventsDimens.ScreenSide,
            top = EventsDimens.PerformancesFirstLabelTop,
            bottom = inset + EventsDimens.ScrollEndGap,
        ),
    ) {
        group(AHEAD, state.ahead, first = true, state.look, lying, onIntent)
        group(PAST, state.past, first = state.ahead.isEmpty(), state.look, lying, onIntent)
    }
}

private const val AHEAD = "ahead"
private const val PAST = "past"

/** A group of the list: its label — a heading for TalkBack — and its rows; nothing at all when it has no row. */
private fun LazyListScope.group(
    key: String,
    cards: List<PerformanceCard>,
    first: Boolean,
    look: KindLook,
    lying: Boolean,
    onIntent: (PerformancesIntent) -> Unit,
) {
    if (cards.isEmpty()) return
    item(key = "label-$key") {
        SectionLabel(
            text = stringResource(if (key == AHEAD) Res.string.performances_ahead else Res.string.performances_past),
            modifier = Modifier.padding(top = if (first) 0.dp else EventsDimens.PerformancesLabelTop, bottom = EventsDimens.PerformancesLabelBottom),
        )
    }
    items(cards, key = { it.row.eventId }) { card ->
        PerformanceRowCard(
            card = card,
            look = look,
            lying = lying,
            onClick = { onIntent(PerformancesIntent.RowClicked(card.row.eventId)) },
            modifier = Modifier.padding(top = EventsDimens.PerformanceRowTop),
        )
    }
}

/**
 * A performance (spec 3.36.9, 5.29 R9): the plate of its date in the colour of the kind — «24» and «ОКТ», the year under them when it is
 * not this one — grey once it is over; the name in one line with an ellipsis and, ahead, the chip of its term, never cut: the name gives
 * it its room; under them the place and the time — «весь день · место» of a day — and the programme in one line; at the end the
 * thumbnail of the newest video, the number of the recordings, or nothing — no zero, no dash. Laid out by [PerformanceRowPolicy]: «2
 * записи» keeps the right while the name keeps a little room beside the chip, and goes under the programme where it would not. One
 * button for TalkBack, one sentence: «Осенний концерт, 24 октября, через 27 дней; Малый зал музыкальной школы, 18:30; программа: А.
 * Вивальди, П. Чайковский; 2 записи».
 */
@Composable
internal fun PerformanceRowCard(card: PerformanceCard, look: KindLook, lying: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val row = card.row
    val ground = colors.surfaceContainer
    val name = eventNameOf(row.name)
    val time = row.startMinutes?.let(Formats::clockOf)
    val allDay = stringResource(Res.string.event_all_day).takeIf { row.startMinutes == null }
    // «весь день · место» of a day, «место · 18:30» of a time — no empty part (spec 3.36.9)
    val where = if (time == null) listOfNotNull(allDay, row.place.ifEmpty { null }) else listOfNotNull(row.place.ifEmpty { null }, time)
    val dot = stringResource(Res.string.dot_separator)
    val records = row.records.takeIf { it > 0 }?.let { count ->
        stringResource(Formats.plural(count, Res.string.history_count_one, Res.string.history_count_few, Res.string.history_count_many), count)
    }
    val said = performanceSaid(row, name, where, records)
    val shape = RoundedCornerShape(EventsDimens.PerformanceRowCorner)
    Layout(
        content = {
            DatePlate(row, look, ground, lying, Modifier.layoutId(RowPart.PLATE))
            Text(
                text = name,
                modifier = Modifier.layoutId(RowPart.NAME),
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = EventsDimens.PerformanceName,
                    lineHeight = EventsDimens.PerformanceNameHeight,
                    fontWeight = FontWeight.ExtraBold,
                ),
            )
            row.days?.let { days -> TermChip(termOf(days), look, ground, Modifier.layoutId(RowPart.TERM)) }
            Text(
                text = where.joinToString(dot),
                modifier = Modifier.layoutId(RowPart.WHERE),
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = lineStyle(),
            )
            if (row.program.isNotEmpty()) {
                Text(
                    text = row.program.joinToString(dot),
                    modifier = Modifier.layoutId(RowPart.PROGRAM),
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = lineStyle(),
                )
            }
            when {
                card.thumbPath != null -> Thumbnail(card.thumbPath, Modifier.layoutId(RowPart.THUMB))
                // never cut: measured before the name is given its room
                records != null -> Text(
                    text = records,
                    modifier = Modifier.layoutId(RowPart.COUNT),
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    style = lineStyle(),
                )
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (lying) EventsDimens.PerformanceRowMinLying else EventsDimens.PerformanceRowMin)
            .clip(shape)
            .background(ground, shape)
            .clickable(role = Role.Button, onClick = onClick)
            // the whole row, its fields too: one button, one sentence
            .clearAndSetSemantics { contentDescription = said }
            .padding(
                horizontal = EventsDimens.PerformanceRowPadding,
                vertical = if (lying) EventsDimens.PerformanceRowPaddingVLying else EventsDimens.PerformanceRowPadding,
            ),
        measurePolicy = PerformanceRowPolicy,
    )
}

/** The parts of a row of «Выступления», as [PerformanceRowPolicy] finds them; the term, the programme and the end may be missing. */
private enum class RowPart { PLATE, NAME, TERM, WHERE, PROGRAM, THUMB, COUNT }

/**
 * A row of «Выступления» laid out by what must stay whole (spec 3.36.9: «чип срока и «2 записи» не режутся», «место отдаёт название»):
 * the plate on the left and the thumbnail or «2 записи» on the right, each in the middle of the height, 12 from the middle; in the middle
 * the name and, 6 after it, the chip of the term on the first line — the chip measured first, the name ending in an ellipsis in what is
 * left — and under them the place with the time and the programme, a line each. «2 записи» keeps the right while the name keeps at least
 * [EventsDimens.PERFORMANCE_NAME_LEAST_EM] of its size beside the chip; where it would not, «2 записи» is the last line of the middle,
 * under the programme, whole as the chip is — fr «2 enregistrements» beside «aujourd'hui» on 360 at 1.3 left the chip 75 dp before (5.29
 * R9, review of stage 99). The thumbnail keeps the right always: it is no words that could go under, the name gives way to it.
 */
private object PerformanceRowPolicy : MeasurePolicy {
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        fun part(part: RowPart): Measurable? = measurables.firstOrNull { it.layoutId == part }
        val gap = EventsDimens.PerformanceRowGap.roundToPx()
        val chipGap = EventsDimens.TermChipGap.roundToPx()
        val plate = part(RowPart.PLATE)?.measure(Constraints())
        val start = (plate?.width ?: 0) + gap
        val name = part(RowPart.NAME)
        val term = part(RowPart.TERM)
        val where = part(RowPart.WHERE)
        val program = part(RowPart.PROGRAM)
        val thumb = part(RowPart.THUMB)
        val count = part(RowPart.COUNT)
        // what stays whole, asked before anything is measured: the chip of the term and the words at the end
        val termWidth = term?.maxIntrinsicWidth(Constraints.Infinity) ?: 0
        val taken = if (term != null) termWidth + chipGap else 0
        val endWidth = (thumb ?: count)?.maxIntrinsicWidth(Constraints.Infinity) ?: 0
        val width = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            val lines = listOfNotNull(where, program).maxOfOrNull { it.maxIntrinsicWidth(Constraints.Infinity) } ?: 0
            start + maxOf((name?.maxIntrinsicWidth(Constraints.Infinity) ?: 0) + taken, lines) + if (endWidth > 0) gap + endWidth else 0
        }
        val nameLeast = (EventsDimens.PerformanceName.toPx() * EventsDimens.PERFORMANCE_NAME_LEAST_EM).roundToInt()
        val countUnder = thumb == null && count != null && start + taken + nameLeast + gap + endWidth > width
        val right = if (thumb != null || (count != null && !countUnder)) gap + endWidth else 0
        val middle = (width - start - right).coerceAtLeast(0)
        val chip = term?.measure(Constraints(maxWidth = middle))
        val title = name?.measure(Constraints(maxWidth = (middle - (chip?.let { it.width + chipGap } ?: 0)).coerceAtLeast(0)))
        val place = where?.measure(Constraints(maxWidth = middle))
        val pieces = program?.measure(Constraints(maxWidth = middle))
        val end = thumb?.measure(Constraints()) ?: count?.measure(Constraints(maxWidth = if (countUnder) middle else endWidth))
        val under = end.takeIf { countUnder }
        val beside = end.takeUnless { countUnder }
        val firstLine = maxOf(title?.height ?: 0, chip?.height ?: 0)
        val text = firstLine + (place?.height ?: 0) + (pieces?.height ?: 0) + (under?.height ?: 0)
        val height = constraints.constrainHeight(maxOf(plate?.height ?: 0, text, beside?.height ?: 0))
        return layout(constraints.constrainWidth(width), height) {
            plate?.placeRelative(0, (height - plate.height) / 2)
            var y = (height - text) / 2
            title?.placeRelative(start, y + (firstLine - title.height) / 2)
            chip?.placeRelative(start + (title?.width?.plus(chipGap) ?: 0), y + (firstLine - chip.height) / 2)
            y += firstLine
            for (line in listOfNotNull(place, pieces, under)) {
                line.placeRelative(start, y)
                y += line.height
            }
            beside?.placeRelative(width - beside.width, (height - beside.height) / 2)
        }
    }
}

@Composable
private fun lineStyle() = MaterialTheme.typography.bodySmall.copy(
    fontSize = EventsDimens.PerformanceLine,
    lineHeight = EventsDimens.PerformanceLineHeight,
    fontFeatureSettings = TABULAR_FIGURES,
)

private const val TABULAR_FIGURES = "tnum"

/**
 * What TalkBack hears of a row (spec 3.36.9), in the order it is seen: the name, the date with its year when not this one, the term
 * ahead — in words, «через 27 дней»; then where and when; the programme; the number of the recordings.
 */
@Composable
private fun performanceSaid(row: PerformanceRow, name: String, where: List<String>, records: String?): String {
    val head = listOfNotNull(name, Formats.recordDate(row.date, withYear = row.otherYear), row.days?.let { termSaidOf(it) })
    val program = row.program.takeIf { it.isNotEmpty() }?.let { names -> stringResource(Res.string.performances_program_said, pairs(names)) }
    var said = pairs(head)
    for (part in listOfNotNull(pairs(where).ifEmpty { null }, program, records)) {
        said = stringResource(Res.string.practice_day_events_description, said, part)
    }
    return said
}

/** «А, Б, В» in the joint of the language («、» in Japanese). */
@Composable
private fun pairs(parts: List<String>): String {
    var said = parts.firstOrNull().orEmpty()
    for (part in parts.drop(1)) said = stringResource(Res.string.practice_pair_description, said, part)
    return said
}

/** The chip of the term: «сегодня», «завтра», «через 27 дн.». */
@Composable
private fun termOf(days: Int): String = when (days) {
    0 -> stringResource(Res.string.practice_day_today)
    1 -> stringResource(Res.string.practice_day_tomorrow)
    else -> stringResource(Formats.plural(days, Res.string.performances_in_days_one, Res.string.performances_in_days_few, Res.string.performances_in_days_many), days)
}

/** The term as TalkBack says it — in whole words: «через 27 дней». */
@Composable
private fun termSaidOf(days: Int): String = when (days) {
    0 -> stringResource(Res.string.practice_day_today)
    1 -> stringResource(Res.string.practice_day_tomorrow)
    else -> stringResource(
        Formats.plural(days, Res.string.performances_row_in_days_one, Res.string.performances_row_in_days_few, Res.string.performances_row_in_days_many),
        days,
    )
}

/**
 * The plate of the date: 52 × 58 (52 × 52 lying), in the colour of the kind at 18 % with the figures in the colour of its sign — grey
 * once the performance is over. It grows with a large font rather than cut a figure.
 */
@Composable
private fun DatePlate(row: PerformanceRow, look: KindLook, ground: Color, lying: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val over = row.days == null
    val ink = if (over) colors.onSurfaceVariant else kindColors(look).onPlate
    val plate = if (over) colors.surfaceContainerHigh else ViolinTheme.eventsColors.plate(look.color, ground)
    Column(
        modifier = modifier
            .widthIn(min = EventsDimens.DatePlateWidth)
            .heightIn(min = if (lying) EventsDimens.DatePlateHeightLying else EventsDimens.DatePlateHeight)
            .background(plate, RoundedCornerShape(EventsDimens.DatePlateCorner)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val figures = MaterialTheme.typography.labelLarge
        Text(
            text = row.date.day.toString(),
            color = ink,
            maxLines = 1,
            style = figures.copy(
                fontSize = EventsDimens.DatePlateDay,
                lineHeight = EventsDimens.DatePlateDayHeight,
                fontWeight = FontWeight.ExtraBold,
                fontFeatureSettings = TABULAR_FIGURES,
            ),
        )
        Text(
            text = Formats.shortMonth(row.date).uppercase(),
            color = ink,
            maxLines = 1,
            style = figures.copy(
                fontSize = EventsDimens.DatePlateMonth,
                lineHeight = EventsDimens.DatePlateMonthHeight,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = EventsDimens.DATE_PLATE_MONTH_TRACKING.em,
            ),
        )
        if (row.otherYear) {
            Text(
                text = row.date.year.toString(),
                color = ink,
                maxLines = 1,
                style = figures.copy(
                    fontSize = EventsDimens.DatePlateYear,
                    lineHeight = EventsDimens.DatePlateYearHeight,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = EventsDimens.DATE_PLATE_YEAR_TRACKING.em,
                    fontFeatureSettings = TABULAR_FIGURES,
                ),
            )
        }
    }
}

/** «через 27 дн.», «сегодня», «завтра»: a capsule of 22 on the kind at 18 %, the words in the colour of its sign, one line. */
@Composable
private fun TermChip(text: String, look: KindLook, ground: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .heightIn(min = EventsDimens.TermChipHeight)
            .background(ViolinTheme.eventsColors.plate(look.color, ground), RoundedCornerShape(percent = 50))
            .padding(horizontal = EventsDimens.TermChipPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = kindColors(look).onPlate,
            maxLines = 1,
            softWrap = false,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = EventsDimens.TermChipText,
                lineHeight = EventsDimens.TermChipTextHeight,
                fontWeight = FontWeight.ExtraBold,
                fontFeatureSettings = TABULAR_FIGURES,
            ),
        )
    }
}

/**
 * The thumbnail of the newest video of the performance: 64 × 44 at a corner of 10, the picture cropped to it, the sign of a video in its
 * middle — the form says «video» (principle 5) — white on the dark circle of what lies on a picture ([VideoColors.scrim], as the sign of
 * the player): a light picture — a white wall, a lit stage — would swallow a sign drawn on it bare (review of stage 99). Until the picture
 * is decoded, and for one that is not a picture, the tone of a tile under it. Silent: the row says what it has.
 */
@Composable
private fun Thumbnail(path: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val image = rememberSmallFileImage(path)
    Box(
        modifier = modifier
            .size(EventsDimens.ThumbWidth, EventsDimens.ThumbHeight)
            .clip(RoundedCornerShape(EventsDimens.ThumbCorner))
            .background(colors.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(bitmap = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(
            modifier = Modifier.size(EventsDimens.ThumbPlayPlate).background(ViolinTheme.videoColors.scrim, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(AppIcons.Play, contentDescription = null, size = EventsDimens.ThumbPlay, tint = Color.White)
        }
    }
}

/**
 * No performance yet (spec 3.36.9, «Пусто»): the plate of the sign of «Выступление» in the colour of the kind, «Здесь будут ваши
 * выступления» and what they keep — «Прошедшие тоже можно добавить.» — in the middle of what the zone leaves; it scrolls where it is more
 * (a large font lying). Smaller lying: the plate 64, the title 20 sp.
 */
@Composable
private fun NoPerformances(look: KindLook, lying: Boolean) {
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val inset = LocalDockInset.current
        val room = (maxHeight - inset).coerceAtLeast(0.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = room)
                    .padding(horizontal = EventsDimens.ScreenSide + EventsDimens.PerformancesEmptySide),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                KindSignPlate(
                    look = look,
                    plate = if (lying) EventsDimens.PerformancesEmptyPlateLying else EventsDimens.PerformancesEmptyPlate,
                    corner = if (lying) EventsDimens.PerformancesEmptyPlateCornerLying else EventsDimens.PerformancesEmptyPlateCorner,
                    sign = if (lying) EventsDimens.PerformancesEmptySignLying else EventsDimens.PerformancesEmptySign,
                    ground = colors.surface,
                )
                Text(
                    text = stringResource(Res.string.performances_empty_title),
                    modifier = Modifier.padding(top = if (lying) EventsDimens.PerformancesEmptyTitleTopLying else EventsDimens.PerformancesEmptyTitleTop),
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = if (lying) EventsDimens.PerformancesEmptyTitleLying else EventsDimens.PerformancesEmptyTitle,
                        lineHeight = if (lying) EventsDimens.PerformancesEmptyTitleHeightLying else EventsDimens.PerformancesEmptyTitleHeight,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                )
                Text(
                    text = stringResource(Res.string.performances_empty_text),
                    modifier = Modifier
                        .padding(top = if (lying) EventsDimens.PerformancesEmptyWordsTopLying else EventsDimens.PerformancesEmptyWordsTop)
                        .widthIn(max = EventsDimens.PerformancesEmptyWordsMax),
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = EventsDimens.PerformancesEmptyWords,
                        lineHeight = EventsDimens.PerformancesEmptyWordsHeight,
                    ),
                )
            }
            // what the zone covers at the bottom: the words end over it
            Spacer(Modifier.height(inset))
        }
    }
}
