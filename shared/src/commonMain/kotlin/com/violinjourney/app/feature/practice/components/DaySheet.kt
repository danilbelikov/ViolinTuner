package com.violinjourney.app.feature.practice.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.violinjourney.app.feature.history.components.SessionCard
import com.violinjourney.app.feature.practice.PracticeIntent
import com.violinjourney.app.feature.practice.SelectedDay
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.practice_day_add
import com.violinjourney.app.shared.resources.practice_day_edit
import com.violinjourney.app.shared.resources.practice_day_none
import com.violinjourney.app.shared.resources.practice_day_records
import com.violinjourney.app.shared.resources.practice_day_today
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource

private val DateGap = 8.dp
private val TimeTop = 4.dp
private val TimeGap = 12.dp
private val RecordsTop = 20.dp
private val RecordsGap = 8.dp
private val TodayChipHeight = 22.dp
private val TodayChipBorder = 1.5.dp
private val TodayChipPadding = 8.dp
private val Capsule = RoundedCornerShape(percent = 50)
private const val TABULAR_FIGURES = "tnum"
private const val TIME_SIZE = 34
private const val MIN_TIME_SIZE = 22

/**
 * What the sheet of a day holds (spec 3.36.2, 5.29): the date — «24 сентября, четверг», with the chip «сегодня» for today; the time of
 * the day large and «Изменить» with the pencil beside it, or «Не занимались» and «Добавить» with the plus — both open «Время за день»
 * in its place; and «Записи этого дня» with their cards, if there are any. TalkBack names the sheet by its date. A face of the frame of
 * «Занятия» ([PracticeSheetHost]): a swipe only hides it (DayHidden); many records scroll inside the frame.
 */
@Composable
fun DaySheetContent(day: SelectedDay, onIntent: (PracticeIntent) -> Unit, zone: TimeZone, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val date = Formats.dayWithWeekday(day.date)
    val played = day.totalMs > 0
    Column(modifier.fillMaxWidth().semantics { paneTitle = date }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DateGap)) {
            Text(
                text = date,
                modifier = Modifier.weight(1f, fill = false),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
            )
            if (day.isToday) TodayChip()
        }
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
        if (day.sessions.isNotEmpty()) {
            Text(
                text = stringResource(Res.string.practice_day_records),
                modifier = Modifier.padding(top = RecordsTop).semantics { heading() },
                color = colors.onSurface,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.ExtraBold),
            )
            Column(Modifier.padding(top = RecordsGap), verticalArrangement = Arrangement.spacedBy(RecordsGap)) {
                day.sessions.forEach { card ->
                    // the old cards (their new look is R5) on the ground of the screen: on the sheet the card colour is the sheet's own
                    SessionCard(card = card, zone = zone, onClick = { onIntent(PracticeIntent.SessionClicked(card.id)) }, container = colors.surface)
                }
            }
        }
    }
}

/** «сегодня» by the date: a capsule of 22 outlined in the accent (5.29 R2), as «завтра» of the events will be. */
@Composable
private fun TodayChip() {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .heightIn(min = TodayChipHeight)
            .border(TodayChipBorder, accent, Capsule)
            .padding(horizontal = TodayChipPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.practice_day_today),
            color = accent,
            maxLines = 1,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold),
        )
    }
}
