package com.violinjourney.app.feature.practice.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.domain.events.Repeat
import com.violinjourney.app.core.ui.components.AppSheetButtons
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.icons.IconSizes
import com.violinjourney.app.feature.events.EventsDimens
import com.violinjourney.app.feature.events.KindSignPlate
import com.violinjourney.app.feature.events.eventNameOf
import com.violinjourney.app.feature.practice.DayEvent
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.event_all_day
import com.violinjourney.app.shared.resources.event_repeat_biweekly_short
import com.violinjourney.app.shared.resources.event_repeat_weekly_short
import com.violinjourney.app.shared.resources.event_time_range
import com.violinjourney.app.shared.resources.practice_day_add_event
import com.violinjourney.app.shared.resources.practice_pair_description
import org.jetbrains.compose.resources.stringResource

/** Between the parts of a line of an event, as they are seen (« · »); TalkBack hears them one by one. */
private const val PART_SEPARATOR = " · "
private const val TABULAR_FIGURES = "tnum"
private val DayChipHeight = 22.dp
private val DayChipBorder = 1.5.dp
private val DayChipPadding = 8.dp
private val Capsule = RoundedCornerShape(percent = 50)

/**
 * A row of «События» of the sheet of the day (spec 3.36.9, 5.29 R9 «Лист дня»): the sign of the kind on its plate, the first line —
 * «17:00–17:45 · Урок», only the start without a length («19:00 · Филармония»), only the name for «весь день» — and under it, quietly,
 * the teacher or the place and the repeat: «Анна Сергеевна · каждую неделю», «весь день · Москва, Малый зал консерватории»; no empty
 * part. Both lines are one line each with an ellipsis: the whole of it is on the screen of the event. A reader hears the parts in the
 * order they are seen. It opens nothing until the screen of the event is there (stage 98).
 */
@Composable
fun DayEventRow(event: DayEvent, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val name = eventNameOf(event.name)
    val time = event.startMinutes?.let { start ->
        event.endMinutes?.let { end -> stringResource(Res.string.event_time_range, Formats.clockOf(start), Formats.clockOf(end)) } ?: Formats.clockOf(start)
    }
    val first = listOfNotNull(time, name)
    val second = listOfNotNull(
        stringResource(Res.string.event_all_day).takeIf { event.allDay },
        event.place.takeIf { it.isNotBlank() },
        repeatWord(event.repeat),
    )
    var said: String? = null
    for (part in first + second) said = said?.let { stringResource(Res.string.practice_pair_description, it, part) } ?: part
    val description = said.orEmpty()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = EventsDimens.DayEventMin)
            .background(colors.surface, RoundedCornerShape(EventsDimens.DayEventCorner))
            // the description is the whole row, its fields too: set inside them it would be a node of the words alone, 44 of 64
            .clearAndSetSemantics { contentDescription = description }
            .padding(horizontal = EventsDimens.DayEventPaddingH, vertical = EventsDimens.DayEventPaddingV),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KindSignPlate(
            look = event.look, plate = EventsDimens.DayEventPlate, corner = EventsDimens.DayEventPlateCorner, sign = EventsDimens.DayEventSign,
            ground = colors.surface,
        )
        Spacer(Modifier.width(EventsDimens.DayEventGap))
        Column(Modifier.weight(1f)) {
            Text(
                text = first.joinToString(PART_SEPARATOR),
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = EventsDimens.DayEventFirst, lineHeight = EventsDimens.DayEventFirstHeight, fontWeight = FontWeight.Bold,
                    fontFeatureSettings = TABULAR_FIGURES,
                ),
            )
            if (second.isNotEmpty()) {
                Text(
                    text = second.joinToString(PART_SEPARATOR),
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = EventsDimens.DayEventSecond, lineHeight = EventsDimens.DayEventSecondHeight),
                )
            }
        }
    }
}

/** «каждую неделю», «раз в две недели»; nothing for a single event. */
@Composable
private fun repeatWord(repeat: Repeat): String? = when (repeat) {
    Repeat.NONE -> null
    Repeat.WEEKLY -> stringResource(Res.string.event_repeat_weekly_short)
    Repeat.BIWEEKLY -> stringResource(Res.string.event_repeat_biweekly_short)
}

/**
 * «Событие в этот день» (spec 3.36.9): the last row of the sheet of any day, dashed, with the plus — the way in is everywhere, and it
 * does not argue with «Изменить» and «Добавить». Up to two lines, never cut. It opens the form of an event, which comes with stage 98:
 * until then only the previews show it.
 */
@Composable
fun AddEventRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(EventsDimens.AddEventCorner)
    Row(
        modifier = modifier
            .padding(top = EventsDimens.AddEventTop)
            .fillMaxWidth()
            .heightIn(min = EventsDimens.AddEventHeight)
            .clip(shape)
            .dashedFrame(colors.outlineVariant, EventsDimens.AddEventCorner)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = EventsDimens.AddEventPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(IconSizes.ButtonGap, Alignment.CenterHorizontally),
    ) {
        AppIcon(AppIcons.Plus, contentDescription = null, size = EventsDimens.AddEventPlus, tint = colors.onSurface)
        Text(
            text = stringResource(Res.string.practice_day_add_event),
            color = colors.onSurface,
            maxLines = 2,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = EventsDimens.AddEventText, lineHeight = EventsDimens.AddEventTextHeight, fontWeight = FontWeight.Bold,
            ),
        )
    }
}

/**
 * The main «Событие в этот день» of a day to come without events (spec 3.36.9, 5.29 R9): 56 with the plus, at the bottom of the frame
 * under «Событий нет» — making an event is all there is to do on such a day. The frame of «Занятия» gives it to the face of the day with
 * the form of an event (stage 98); until then only the previews show it.
 */
@Composable
fun AddEventButtons(onClick: () -> Unit) {
    AppSheetButtons(main = stringResource(Res.string.practice_day_add_event), onMain = onClick, mainIcon = AppIcons.Plus)
}

/** «сегодня» or «завтра» by the date of the sheet of the day: a capsule of 22 outlined in the accent (5.29 R2, R9) — the two look the same. */
@Composable
internal fun DayChip(text: String) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .heightIn(min = DayChipHeight)
            .border(DayChipBorder, accent, Capsule)
            .padding(horizontal = DayChipPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = accent,
            maxLines = 1,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold),
        )
    }
}
