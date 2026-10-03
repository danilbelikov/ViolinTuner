package com.violinjourney.app.feature.practice.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.components.AppButton
import com.violinjourney.app.core.ui.components.AppButtonStyle
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.feature.events.EventsDimens
import com.violinjourney.app.feature.history.components.RecordPlace
import com.violinjourney.app.feature.history.components.SessionCard
import com.violinjourney.app.feature.practice.PracticeIntent
import com.violinjourney.app.feature.practice.SelectedDay
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.practice_day_add
import com.violinjourney.app.shared.resources.practice_day_edit
import com.violinjourney.app.shared.resources.practice_day_events
import com.violinjourney.app.shared.resources.practice_day_no_events
import com.violinjourney.app.shared.resources.practice_day_no_events_text
import com.violinjourney.app.shared.resources.practice_day_none
import com.violinjourney.app.shared.resources.practice_day_records
import com.violinjourney.app.shared.resources.practice_day_time_later
import com.violinjourney.app.shared.resources.practice_day_today
import com.violinjourney.app.shared.resources.practice_day_tomorrow
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource

private val DateGap = 8.dp
private val TimeTop = 4.dp
private val TimeGap = 12.dp
private val RecordsTop = 20.dp
private val RecordsGap = 8.dp
private const val TABULAR_FIGURES = "tnum"
private const val TIME_SIZE = 34
private const val MIN_TIME_SIZE = 22

/**
 * What the sheet of a day holds (spec 3.36.2, 3.36.9, 5.29): the date — «24 сентября, четверг», with the chip «сегодня» for today and
 * «завтра» for tomorrow. Today and a day gone by: the time of the day large and «Изменить» with the pencil beside it, or «Не
 * занимались» and «Добавить» with the plus — both open «Время за день» in its place — then «События» of the day, if it has any, and
 * «Записи этого дня» with their cards, if there are any. A day to come: no time and no «Изменить» — its time is not edited (3.35) —
 * but «Время появится, когда день наступит.» and its events right under it, without a title; without events — «Событий нет» and what
 * a day holds. TalkBack names the sheet by its date. A face of the frame of «Занятия» ([PracticeSheetHost]): a swipe only hides it
 * (DayHidden); many events and records scroll inside the frame. [onAddEvent] — «Событие в этот день» dashed at the bottom: the form of
 * an event with the date of the sheet; the main button of a day to come without events is the frame's, under the content
 * ([AddEventButtons]).
 */
@Composable
fun DaySheetContent(
    day: SelectedDay,
    onIntent: (PracticeIntent) -> Unit,
    zone: TimeZone,
    modifier: Modifier = Modifier,
    onAddEvent: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val date = Formats.dayWithWeekday(day.date)
    Column(modifier.fillMaxWidth().semantics { paneTitle = date }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DateGap)) {
            Text(
                text = date,
                modifier = Modifier.weight(1f, fill = false),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
            )
            when {
                day.isToday -> DayChip(stringResource(Res.string.practice_day_today))
                day.isTomorrow -> DayChip(stringResource(Res.string.practice_day_tomorrow))
            }
        }
        if (day.isFuture) {
            FutureDay(day, onIntent)
            if (day.events.isNotEmpty()) onAddEvent?.let { AddEventRow(it) }
        } else {
            DayTime(day, onIntent)
            DayEventRows(day, onIntent)
            DayRecords(day, onIntent, zone)
            onAddEvent?.let { AddEventRow(it) }
        }
    }
}

/** The time of today or of a day gone by, large, with «Изменить» — or «Не занимались» with «Добавить» (spec 3.36.2). */
@Composable
private fun DayTime(day: SelectedDay, onIntent: (PracticeIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val played = day.totalMs > 0
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = TimeTop),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TimeGap),
    ) {
        if (played) {
            // «12 ч 45 мин», «1 Std. 25 Min.» beside «Изменить» on 360: smaller rather than on two lines, like the numbers of «Сегодня»
            Text(
                text = Formats.minutesInWords(day.totalMs),
                modifier = Modifier.weight(1f),
                color = colors.onSurface,
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = MIN_TIME_SIZE.sp, maxFontSize = TIME_SIZE.sp, stepSize = 1.sp),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = TIME_SIZE.sp, lineHeight = 1.2.em, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.03).em,
                    fontFeatureSettings = TABULAR_FIGURES,
                ),
            )
        } else {
            Text(
                text = stringResource(Res.string.practice_day_none),
                modifier = Modifier.weight(1f),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
            )
        }
        AppButton(
            text = stringResource(if (played) Res.string.practice_day_edit else Res.string.practice_day_add),
            onClick = { onIntent(PracticeIntent.EditTimeClicked) },
            style = AppButtonStyle.Soft,
            icon = if (played) AppIcons.Pencil else AppIcons.Plus,
        )
    }
}

/** «События» of today or of a day gone by (spec 3.36.9): a heading and the rows, between the time and the records; nothing without events. */
@Composable
private fun DayEventRows(day: SelectedDay, onIntent: (PracticeIntent) -> Unit) {
    if (day.events.isEmpty()) return
    Text(
        text = stringResource(Res.string.practice_day_events),
        modifier = Modifier.padding(top = EventsDimens.DayEventsTop).semantics { heading() },
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.titleSmall.copy(
            fontSize = EventsDimens.DayEventsTitle, lineHeight = EventsDimens.DayEventsTitleHeight, fontWeight = FontWeight.ExtraBold,
        ),
    )
    Column(Modifier.padding(top = EventsDimens.DayEventsGap), verticalArrangement = Arrangement.spacedBy(EventsDimens.DayEventsGap)) {
        day.events.forEach { event -> DayEventRow(event, onClick = { onIntent(PracticeIntent.DayEventClicked(event.eventId)) }) }
    }
}

/** «Записи этого дня» and their cards; nothing without records. */
@Composable
private fun DayRecords(day: SelectedDay, onIntent: (PracticeIntent) -> Unit, zone: TimeZone) {
    if (day.sessions.isEmpty()) return
    Text(
        text = stringResource(Res.string.practice_day_records),
        modifier = Modifier.padding(top = RecordsTop).semantics { heading() },
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.ExtraBold),
    )
    Column(Modifier.padding(top = RecordsGap), verticalArrangement = Arrangement.spacedBy(RecordsGap)) {
        day.sessions.forEach { card ->
            // the cards of R5 without «⋯» — here a recording is only opened — on the ground of the screen, as in every sheet
            SessionCard(card = card, zone = zone, onClick = { onIntent(PracticeIntent.SessionClicked(card.id)) }, place = RecordPlace.Sheet)
        }
    }
}

/**
 * A day to come (spec 3.36.9): «Время появится, когда день наступит.» and its events right under it, without a title; without events
 * no such line — «Событий нет» and «Урок, репетиция, выступление — всё, что стоит в этот день.».
 */
@Composable
private fun FutureDay(day: SelectedDay, onIntent: (PracticeIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    if (day.events.isEmpty()) {
        Text(
            text = stringResource(Res.string.practice_day_no_events),
            modifier = Modifier.padding(top = EventsDimens.NoEventsTop),
            color = colors.onSurface,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = EventsDimens.NoEventsTitle, lineHeight = EventsDimens.NoEventsTitleHeight, fontWeight = FontWeight.ExtraBold,
            ),
        )
        Text(
            text = stringResource(Res.string.practice_day_no_events_text),
            modifier = Modifier.padding(top = EventsDimens.NoEventsTextTop),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = EventsDimens.NoEventsText, lineHeight = EventsDimens.NoEventsTextHeight),
        )
    } else {
        Text(
            text = stringResource(Res.string.practice_day_time_later),
            modifier = Modifier.padding(top = EventsDimens.TimeLaterTop),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = EventsDimens.TimeLaterText, lineHeight = EventsDimens.TimeLaterHeight),
        )
        Column(Modifier.padding(top = EventsDimens.TimeLaterRowsTop), verticalArrangement = Arrangement.spacedBy(EventsDimens.DayEventsGap)) {
            day.events.forEach { event -> DayEventRow(event, onClick = { onIntent(PracticeIntent.DayEventClicked(event.eventId)) }) }
        }
    }
}
