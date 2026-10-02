package com.violinjourney.app.feature.practice.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.domain.events.Reminder
import com.violinjourney.app.core.domain.events.ReminderDay
import com.violinjourney.app.core.domain.events.ReminderRow
import com.violinjourney.app.core.domain.events.Running
import com.violinjourney.app.core.ui.components.DockMetrics
import com.violinjourney.app.core.ui.components.WholeWordsFit
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.events.EventsDimens
import com.violinjourney.app.feature.events.EventsMotion
import com.violinjourney.app.feature.events.KindSignPlate
import com.violinjourney.app.feature.events.MiniSign
import com.violinjourney.app.feature.events.eventKindWord
import com.violinjourney.app.feature.events.eventWordOf
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.event_all_day
import com.violinjourney.app.shared.resources.event_reminder_more
import com.violinjourney.app.shared.resources.event_reminder_more_description_few
import com.violinjourney.app.shared.resources.event_reminder_more_description_many
import com.violinjourney.app.shared.resources.event_reminder_more_description_one
import com.violinjourney.app.shared.resources.event_reminder_today
import com.violinjourney.app.shared.resources.event_reminder_today_at
import com.violinjourney.app.shared.resources.event_reminder_today_at_said
import com.violinjourney.app.shared.resources.event_reminder_today_said
import com.violinjourney.app.shared.resources.event_reminder_tomorrow
import com.violinjourney.app.shared.resources.event_reminder_tomorrow_at
import com.violinjourney.app.shared.resources.event_reminder_tomorrow_at_said
import com.violinjourney.app.shared.resources.event_reminder_tomorrow_said
import com.violinjourney.app.shared.resources.event_reminder_yesterday_at
import com.violinjourney.app.shared.resources.event_reminder_yesterday_at_said
import com.violinjourney.app.shared.resources.event_running
import com.violinjourney.app.shared.resources.event_running_until
import com.violinjourney.app.shared.resources.event_running_until_said
import com.violinjourney.app.shared.resources.practice_pair_description
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/** Between the parts of the second line of a row, as they are seen (« · »); TalkBack hears them one by one. */
private const val PART_SEPARATOR = " · "

/** The first line of a row: up to two lines, then an ellipsis (spec 3.36.9); the second — one. */
private const val FIRST_LINES = 2

/** «ещё N» of the compact card: its words may go on two lines at a space, smaller down to this, never broken inside a word. */
private const val MORE_MIN_SP = 10f

/** The numbers of the card that differ lying (spec 5.29 R9): its fields, the gap of a row, the width of the target of «ещё N». */
@Immutable
private data class ReminderMetrics(val paddingV: Dp, val paddingH: Dp, val gap: Dp, val target: Dp) {
    companion object {
        val Upright = ReminderMetrics(EventsDimens.ReminderPaddingV, EventsDimens.ReminderPaddingH, EventsDimens.ReminderGap, EventsDimens.CompactTarget)
        val Lying = ReminderMetrics(
            EventsDimens.ReminderPaddingVLying, EventsDimens.ReminderPaddingHLying, EventsDimens.ReminderGapLying, EventsDimens.CompactTargetLying,
        )
    }
}

/**
 * The reminder on «Занятия» (spec 3.35, 3.36.9, 5.29 R9; events-views.html, 1–2): a card of the colour of a card — quieter than the
 * living button, it tells and does not call — with a row an event: the sign of its kind on its plate, «Завтра в 17:00 — урок» (up to
 * two lines) and under it, quietly, the teacher or the place, «весь день · …» for an event of the whole day, «· идёт, до 17:45» or
 * «· идёт» for one going on; a rule between the rows. Two rows are seen; the third and further are «ещё 2» with the mini signs of their
 * kinds — it opens the sheet of the day of the first of them ([onMore]). [compact] — lying and in a window lower than 700 (360 × 640,
 * [ReminderFit]): one event, its first line alone, and «ещё N» a target of its own on the right behind a rule, the gap of the row
 * between the words and the rule. No button «закрыть»: the card goes by
 * itself when its event is over. The rows open nothing until the screen of an event is there (stage 98). [lying] — the fields and the
 * gaps of the left column of landscape.
 */
@Composable
fun ReminderCard(reminder: Reminder, compact: Boolean, lying: Boolean, onMore: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val metrics = if (lying) ReminderMetrics.Lying else ReminderMetrics.Upright
    val shape = RoundedCornerShape(EventsDimens.ReminderCorner)
    val card = modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainer)
    val hidden = reminder.hidden(compact)
    if (!compact) {
        Column(card.padding(horizontal = metrics.paddingH, vertical = metrics.paddingV)) {
            reminder.visible(compact = false).forEachIndexed { index, row ->
                if (index > 0) Rule(Modifier.fillMaxWidth().height(EventsDimens.ReminderRule))
                ReminderRowView(row, compact = false, gap = metrics.gap)
            }
            if (hidden.isNotEmpty()) {
                Rule(Modifier.fillMaxWidth().height(EventsDimens.ReminderRule))
                MoreRow(hidden, onClick = { onMore(hidden.first().date) })
            }
        }
    } else {
        val first = reminder.visible(compact = true).first()
        Row(
            // the target of «ещё N» runs to the right edge of the card and the whole height of the row
            card
                .height(IntrinsicSize.Min)
                .padding(start = metrics.paddingH, top = metrics.paddingV, bottom = metrics.paddingV, end = if (hidden.isEmpty()) metrics.paddingH else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReminderRowView(first, compact = true, gap = metrics.gap, modifier = Modifier.weight(1f))
            if (hidden.isNotEmpty()) {
                // the words keep the gap of the row from the rule, as from the plate (events-views.html, 2: 12, lying 10)
                Spacer(Modifier.width(metrics.gap))
                Rule(Modifier.fillMaxHeight().width(EventsDimens.ReminderRule))
                MoreTarget(hidden, width = metrics.target, onClick = { onMore(hidden.first().date) })
            }
        }
    }
}

/** A rule of 1 in the colour of the borders: between the rows, and before the target of «ещё N». */
@Composable
private fun Rule(modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.outlineVariant))
}

/**
 * One event of the card: the sign of its kind on its plate of 32, the first line — the day, the start and the name — and in the full
 * card the second one. A reader hears one sentence: «Завтра в 17:00 урок, Анна Сергеевна», «…, идёт до 17:45». The sentence is the
 * whole row, its fields of 8 too — a node set inside them would be the words alone, 40 high where the row is 56: the frame of the
 * reader would stand off the row, and the touch of stage 98 would be less than 48.
 */
@Composable
private fun ReminderRowView(row: ReminderRow, compact: Boolean, gap: Dp, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val name = eventWordOf(row.name)
    val first = firstLineOf(row, name)
    val parts = secondLineOf(row)
    val description = spokenOf(row, name)
    Row(
        modifier = modifier
            .heightIn(min = EventsDimens.ReminderRowMin)
            .clearAndSetSemantics { contentDescription = description }
            .padding(vertical = EventsDimens.ReminderRowPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KindSignPlate(
            look = row.look, plate = EventsDimens.ReminderPlate, corner = EventsDimens.ReminderPlateCorner, sign = EventsDimens.ReminderSign,
            ground = colors.surfaceContainer,
        )
        Spacer(Modifier.width(gap))
        Column(Modifier.weight(1f)) {
            Text(
                text = first,
                color = colors.onSurface,
                maxLines = FIRST_LINES,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = if (compact) EventsDimens.ReminderFirstCompact else EventsDimens.ReminderFirst,
                    lineHeight = if (compact) EventsDimens.ReminderFirstCompactHeight else EventsDimens.ReminderFirstHeight,
                    fontWeight = FontWeight.Bold,
                ),
            )
            if (!compact && parts.isNotEmpty()) {
                Text(
                    text = parts.joinToString(PART_SEPARATOR),
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = EventsDimens.ReminderSecond, lineHeight = EventsDimens.ReminderSecondHeight),
                )
            }
        }
    }
}

/**
 * «Завтра в 17:00 — урок», «Сегодня в 18:30 — Осенний концерт», «Вчера в 23:00 — …» of an event of yesterday going past midnight (plan
 * D7); «Завтра — Отборочный тур» of one of the whole day: the day, the start and the name — the title, or the word of the kind.
 */
@Composable
private fun firstLineOf(row: ReminderRow, name: String): String {
    val start = row.startMinutes ?: return when (row.day) {
        ReminderDay.TOMORROW -> stringResource(Res.string.event_reminder_tomorrow, name)
        // an event of the whole day of yesterday is never in the card: it ends at midnight
        ReminderDay.TODAY, ReminderDay.YESTERDAY -> stringResource(Res.string.event_reminder_today, name)
    }
    val time = Formats.clockOf(start)
    return when (row.day) {
        ReminderDay.YESTERDAY -> stringResource(Res.string.event_reminder_yesterday_at, time, name)
        ReminderDay.TODAY -> stringResource(Res.string.event_reminder_today_at, time, name)
        ReminderDay.TOMORROW -> stringResource(Res.string.event_reminder_tomorrow_at, time, name)
    }
}

/**
 * The parts of the second line: «весь день» first for an event of the whole day, the teacher or the place, and for an event going on
 * «идёт, до 17:45» — or «идёт» without a length: its end of start and an hour (5.28) is a number of the domain, not a time a person set.
 */
@Composable
private fun secondLineOf(row: ReminderRow): List<String> = listOfNotNull(
    stringResource(Res.string.event_all_day).takeIf { row.startMinutes == null },
    row.place.takeIf { it.isNotBlank() },
    when (val running = row.running) {
        is Running.Until -> stringResource(Res.string.event_running_until, Formats.clockOf(running.end.hour * MINUTES_PER_HOUR + running.end.minute))
        Running.Open -> stringResource(Res.string.event_running)
        null -> null
    },
)

/** What a reader hears of a row (spec 3.36.9): «Завтра в 17:00 урок, Анна Сергеевна», «Сегодня в 17:00 урок, Анна Сергеевна, идёт до 17:45». */
@Composable
private fun spokenOf(row: ReminderRow, name: String): String {
    val head = row.startMinutes?.let { start ->
        val time = Formats.clockOf(start)
        when (row.day) {
            ReminderDay.YESTERDAY -> stringResource(Res.string.event_reminder_yesterday_at_said, time, name)
            ReminderDay.TODAY -> stringResource(Res.string.event_reminder_today_at_said, time, name)
            ReminderDay.TOMORROW -> stringResource(Res.string.event_reminder_tomorrow_at_said, time, name)
        }
    } ?: when (row.day) {
        ReminderDay.TOMORROW -> stringResource(Res.string.event_reminder_tomorrow_said, name)
        ReminderDay.TODAY, ReminderDay.YESTERDAY -> stringResource(Res.string.event_reminder_today_said, name)
    }
    val tail = listOfNotNull(
        stringResource(Res.string.event_all_day).takeIf { row.startMinutes == null },
        row.place.takeIf { it.isNotBlank() },
        when (val running = row.running) {
            is Running.Until -> stringResource(Res.string.event_running_until_said, Formats.clockOf(running.end.hour * MINUTES_PER_HOUR + running.end.minute))
            Running.Open -> stringResource(Res.string.event_running)
            null -> null
        },
    )
    var said = head
    for (part in tail) said = stringResource(Res.string.practice_pair_description, said, part)
    return said
}

/** «Ещё 2 события: мастер-класс, урок» — the number of the events «ещё N» stands for and the word of the kind of each, in their order (plan D8). */
@Composable
private fun moreDescriptionOf(hidden: List<ReminderRow>): String {
    var kinds: String? = null
    for (row in hidden) {
        val word = eventKindWord(row.kind, row.ownName)
        kinds = kinds?.let { stringResource(Res.string.practice_pair_description, it, word) } ?: word
    }
    val count = hidden.size
    val description = Formats.plural(
        count,
        Res.string.event_reminder_more_description_one,
        Res.string.event_reminder_more_description_few,
        Res.string.event_reminder_more_description_many,
    )
    return stringResource(description, count, kinds.orEmpty())
}

/** «ещё N» of the full card: a row of 48 under the words of the rows, the mini signs of the first three of its events, the chevron. */
@Composable
private fun MoreRow(hidden: List<ReminderRow>, onClick: () -> Unit) {
    val description = moreDescriptionOf(hidden)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = EventsDimens.MoreRowMin)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = description }
            .padding(start = EventsDimens.MoreIndent),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EventsDimens.MoreGap),
    ) {
        Text(
            text = stringResource(Res.string.event_reminder_more, hidden.size),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = EventsDimens.MoreText, lineHeight = EventsDimens.MoreTextHeight, fontWeight = FontWeight.Bold),
        )
        MoreSigns(hidden)
        Spacer(Modifier.weight(1f))
        AppIcon(AppIcons.ChevronRight, contentDescription = null, size = EventsDimens.MoreChevron, tint = ViolinTheme.textTertiary)
    }
}

/**
 * «ещё N» of the compact card: a target of its own on the right, [width] wide and as high as the row, «ещё 3» over the mini signs of
 * its events. Its words never break inside a word: where they do not stand whole in the target — «3 weitere» at a large font — they go
 * on two lines at the space, smaller down to [MORE_MIN_SP].
 */
@Composable
private fun MoreTarget(hidden: List<ReminderRow>, width: Dp, onClick: () -> Unit) {
    val description = moreDescriptionOf(hidden)
    Column(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(EventsDimens.CompactTargetGap, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.event_reminder_more, hidden.size),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = FIRST_LINES,
            autoSize = remember { WholeWordsFit(EventsDimens.MoreText.value, MORE_MIN_SP) },
            style = MaterialTheme.typography.labelMedium.copy(fontSize = EventsDimens.MoreText, lineHeight = EventsDimens.MoreTextHeight, fontWeight = FontWeight.Bold),
        )
        MoreSigns(hidden)
    }
}

/** The mini signs of the first three events «ещё N» stands for, in their order (plan D8): 10, 3 apart. */
@Composable
private fun MoreSigns(hidden: List<ReminderRow>) {
    Row(horizontalArrangement = Arrangement.spacedBy(EventsDimens.MoreSignGap), verticalAlignment = Alignment.CenterVertically) {
        hidden.take(MORE_SIGNS).forEach { MiniSign(it.look, EventsDimens.MoreSign) }
    }
}

private const val MORE_SIGNS = 3
private const val MINUTES_PER_HOUR = 60

/**
 * Which card stands (spec 3.36.9, 5.29 R9; plan D10): the compact one lying, and upright in a window lower than 700 dp — 360 × 640 —
 * by the height of the window, as the bottom zone reads it ([DockMetrics.LowBelow]); the full one in every other window.
 */
object ReminderFit {
    fun compact(windowHeight: Dp, lying: Boolean): Boolean = lying || windowHeight < DockMetrics.LowBelow
}

/** The fade of the reminder (spec 3.36.9, «Движение»): the last one the state gave, kept while it fades out, and the strength it is drawn at. */
@Stable
private class ReminderFade(initial: Reminder?) {
    var last: Reminder? by mutableStateOf(initial)

    /** The last one has faded out: nothing is shown until the state gives another. */
    var gone: Boolean by mutableStateOf(initial == null)
    val alpha = Animatable(if (initial == null) 0f else 1f)
}

/**
 * The reminder as the screen shows it (spec 3.36.9, «Движение»): [value] — the one the state gives, or the last one while it fades
 * out once it has gone; [alpha] — read where the card is drawn, never in composition. Come or gone while the screen is open — a fade
 * of 300 ms ([EventsMotion.REMINDER_FADE_MS]); there at the first frame of the screen — no fade, it comes with «Сегодня» («Загрузка»).
 * The window of the home is fitted to [value], so it moves once: when the card has faded out and leaves its place, not before.
 */
@Immutable
class ShownReminder(val value: Reminder?, val alpha: () -> Float)

/**
 * The reminder of the state, [reminder], as the screen shows it ([ShownReminder]). The card is there at once, without a fade, when
 * the data is new to the screen: at the end of [loading], and when [unseen] — the count of the changes of the reminder the screen did
 * not see (`PracticeState.reminderEpoch`: one made while it was away, or by the reckoning at its opening) — has grown. «Убрать
 * анимации» — it comes and goes at once.
 */
@Composable
fun rememberShownReminder(reminder: Reminder?, loading: Boolean, unseen: Int): ShownReminder {
    val fade = remember(loading, unseen) { ReminderFade(reminder) }
    SideEffect {
        if (reminder != null) {
            fade.last = reminder
            fade.gone = false
        }
    }
    val reduceMotion = LocalReduceMotion.current
    LaunchedEffect(fade, reminder == null) {
        if (reminder != null) {
            if (reduceMotion) fade.alpha.snapTo(1f) else fade.alpha.animateTo(1f, tween(EventsMotion.REMINDER_FADE_MS))
        } else if (!fade.gone) {
            if (reduceMotion) fade.alpha.snapTo(0f) else fade.alpha.animateTo(0f, tween(EventsMotion.REMINDER_FADE_MS))
            fade.gone = true
        }
    }
    val value = reminder ?: fade.last.takeUnless { fade.gone }
    return remember(value, fade) { ShownReminder(value) { fade.alpha.value } }
}
