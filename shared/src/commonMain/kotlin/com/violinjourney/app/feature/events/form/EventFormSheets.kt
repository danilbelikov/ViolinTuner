package com.violinjourney.app.feature.events.form

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import com.violinjourney.app.core.domain.events.ChangedField
import com.violinjourney.app.core.domain.events.EditScope
import com.violinjourney.app.core.domain.events.EventDraft
import com.violinjourney.app.core.domain.events.EventKind
import com.violinjourney.app.core.domain.events.Repeat
import com.violinjourney.app.core.domain.events.ScopeQuestion
import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_MINUTE
import com.violinjourney.app.core.ui.components.AppButtonStyle
import com.violinjourney.app.core.ui.components.AppChip
import com.violinjourney.app.core.ui.components.AppSheet
import com.violinjourney.app.core.ui.components.AppSheetButtons
import com.violinjourney.app.core.ui.components.AppSheetCard
import com.violinjourney.app.core.ui.components.AppSheetDefaults
import com.violinjourney.app.core.ui.components.AppSwitchMark
import com.violinjourney.app.core.ui.components.SectionLabel
import com.violinjourney.app.core.ui.components.Stepper
import com.violinjourney.app.core.ui.components.currentDockMetrics
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.events.EventsDimens
import com.violinjourney.app.feature.events.ScopeAnswer
import com.violinjourney.app.feature.events.ScopeAnswers
import com.violinjourney.app.feature.events.ScopeSheetContent
import com.violinjourney.app.feature.events.eventKindName
import com.violinjourney.app.feature.events.scopeAnswersPinned
import com.violinjourney.app.feature.events.screen.SeriesWord
import com.violinjourney.app.feature.practice.CalendarCell
import com.violinjourney.app.feature.practice.components.ArrowButton
import com.violinjourney.app.feature.practice.components.CalendarMetrics
import com.violinjourney.app.feature.practice.components.DaySpeech
import com.violinjourney.app.feature.practice.components.KindLegend
import com.violinjourney.app.feature.practice.components.MonthGrid
import com.violinjourney.app.feature.practice.components.Weekdays
import com.violinjourney.app.feature.practice.components.lowSheetWindow
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.dialog_cancel
import com.violinjourney.app.shared.resources.dot_separator
import com.violinjourney.app.shared.resources.event_all_day
import com.violinjourney.app.shared.resources.event_all_day_caption
import com.violinjourney.app.shared.resources.event_all_day_note
import com.violinjourney.app.shared.resources.event_all_day_title
import com.violinjourney.app.shared.resources.event_chip_today
import com.violinjourney.app.shared.resources.event_chip_tomorrow
import com.violinjourney.app.shared.resources.event_date_time
import com.violinjourney.app.shared.resources.event_dates_and_on
import com.violinjourney.app.shared.resources.event_duration_ceiling
import com.violinjourney.app.shared.resources.event_duration_hint
import com.violinjourney.app.shared.resources.event_duration_none
import com.violinjourney.app.shared.resources.event_duration_span
import com.violinjourney.app.shared.resources.event_end_line
import com.violinjourney.app.shared.resources.event_field_date
import com.violinjourney.app.shared.resources.event_field_duration
import com.violinjourney.app.shared.resources.event_field_place
import com.violinjourney.app.shared.resources.event_field_repeat
import com.violinjourney.app.shared.resources.event_field_teacher
import com.violinjourney.app.shared.resources.event_field_time
import com.violinjourney.app.shared.resources.event_field_title
import com.violinjourney.app.shared.resources.event_frequent
import com.violinjourney.app.shared.resources.event_kind_sheet_title
import com.violinjourney.app.shared.resources.event_new_from_on_1
import com.violinjourney.app.shared.resources.event_new_from_on_2
import com.violinjourney.app.shared.resources.event_new_from_on_3
import com.violinjourney.app.shared.resources.event_new_from_on_4
import com.violinjourney.app.shared.resources.event_new_from_on_5
import com.violinjourney.app.shared.resources.event_new_from_on_6
import com.violinjourney.app.shared.resources.event_new_from_on_7
import com.violinjourney.app.shared.resources.event_new_from_on_at_1
import com.violinjourney.app.shared.resources.event_new_from_on_at_2
import com.violinjourney.app.shared.resources.event_new_from_on_at_3
import com.violinjourney.app.shared.resources.event_new_from_on_at_4
import com.violinjourney.app.shared.resources.event_new_from_on_at_5
import com.violinjourney.app.shared.resources.event_new_from_on_at_6
import com.violinjourney.app.shared.resources.event_new_from_on_at_7
import com.violinjourney.app.shared.resources.event_no_end_biweekly_1
import com.violinjourney.app.shared.resources.event_no_end_biweekly_2
import com.violinjourney.app.shared.resources.event_no_end_biweekly_3
import com.violinjourney.app.shared.resources.event_no_end_biweekly_4
import com.violinjourney.app.shared.resources.event_no_end_biweekly_5
import com.violinjourney.app.shared.resources.event_no_end_biweekly_6
import com.violinjourney.app.shared.resources.event_no_end_biweekly_7
import com.violinjourney.app.shared.resources.event_no_end_weekly_1
import com.violinjourney.app.shared.resources.event_no_end_weekly_2
import com.violinjourney.app.shared.resources.event_no_end_weekly_3
import com.violinjourney.app.shared.resources.event_no_end_weekly_4
import com.violinjourney.app.shared.resources.event_no_end_weekly_5
import com.violinjourney.app.shared.resources.event_no_end_weekly_6
import com.violinjourney.app.shared.resources.event_no_end_weekly_7
import com.violinjourney.app.shared.resources.event_repeat_biweekly
import com.violinjourney.app.shared.resources.event_repeat_none
import com.violinjourney.app.shared.resources.event_repeat_weekly
import com.violinjourney.app.shared.resources.event_rest_on_at_1
import com.violinjourney.app.shared.resources.event_rest_on_at_2
import com.violinjourney.app.shared.resources.event_rest_on_at_3
import com.violinjourney.app.shared.resources.event_rest_on_at_4
import com.violinjourney.app.shared.resources.event_rest_on_at_5
import com.violinjourney.app.shared.resources.event_rest_on_at_6
import com.violinjourney.app.shared.resources.event_rest_on_at_7
import com.violinjourney.app.shared.resources.event_rest_on_weekdays
import com.violinjourney.app.shared.resources.event_series_edit_q_event
import com.violinjourney.app.shared.resources.event_series_edit_q_lesson
import com.violinjourney.app.shared.resources.event_series_edit_q_rehearsal
import com.violinjourney.app.shared.resources.event_series_following_event
import com.violinjourney.app.shared.resources.event_series_following_lesson
import com.violinjourney.app.shared.resources.event_series_following_rehearsal
import com.violinjourney.app.shared.resources.event_series_from_date
import com.violinjourney.app.shared.resources.event_series_label_event
import com.violinjourney.app.shared.resources.event_series_label_lesson
import com.violinjourney.app.shared.resources.event_series_label_rehearsal
import com.violinjourney.app.shared.resources.event_series_move_q_event
import com.violinjourney.app.shared.resources.event_series_move_q_lesson
import com.violinjourney.app.shared.resources.event_series_move_q_rehearsal
import com.violinjourney.app.shared.resources.event_series_only_date
import com.violinjourney.app.shared.resources.event_series_past_long_event
import com.violinjourney.app.shared.resources.event_series_past_long_lesson
import com.violinjourney.app.shared.resources.event_series_past_long_rehearsal
import com.violinjourney.app.shared.resources.event_series_past_short_event
import com.violinjourney.app.shared.resources.event_series_past_short_lesson
import com.violinjourney.app.shared.resources.event_series_past_short_rehearsal
import com.violinjourney.app.shared.resources.event_series_this_event
import com.violinjourney.app.shared.resources.event_series_this_lesson
import com.violinjourney.app.shared.resources.event_series_this_rehearsal
import com.violinjourney.app.shared.resources.event_sheet_until
import com.violinjourney.app.shared.resources.event_until_hint_event
import com.violinjourney.app.shared.resources.event_until_hint_lesson
import com.violinjourney.app.shared.resources.event_until_hint_rehearsal
import com.violinjourney.app.shared.resources.event_until_last_event
import com.violinjourney.app.shared.resources.event_until_last_lesson
import com.violinjourney.app.shared.resources.event_until_last_rehearsal
import com.violinjourney.app.shared.resources.event_until_none
import com.violinjourney.app.shared.resources.event_until_title
import com.violinjourney.app.shared.resources.event_wheel_down
import com.violinjourney.app.shared.resources.event_wheel_hours_few
import com.violinjourney.app.shared.resources.event_wheel_hours_many
import com.violinjourney.app.shared.resources.event_wheel_hours_one
import com.violinjourney.app.shared.resources.event_wheel_minutes_few
import com.violinjourney.app.shared.resources.event_wheel_minutes_many
import com.violinjourney.app.shared.resources.event_wheel_minutes_one
import com.violinjourney.app.shared.resources.event_wheel_up
import com.violinjourney.app.shared.resources.practice_month_back
import com.violinjourney.app.shared.resources.practice_month_forward
import com.violinjourney.app.shared.resources.practice_no_value
import com.violinjourney.app.shared.resources.practice_pair_description
import com.violinjourney.app.shared.resources.practice_step_down
import com.violinjourney.app.shared.resources.practice_step_up
import com.violinjourney.app.shared.resources.profile_done
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

private const val TABULAR_FIGURES = "tnum"
private const val MINUTES_PER_HOUR = 60

/** The values of the wheel of the hours: 00–23; the minutes are on the step of a start ([EventFormSheet.Time.step]). */
private val Hours = List(24) { it }

/**
 * The one frame of the sheets of the form (spec 3.36.9, plan D21): «Дата», «Время», «Длительность», «Повторять до», «Вид» and the sheet of
 * a repeat — faces of one [AppSheet]; a tap inside only chooses, «Готово» puts it into the form; a swipe, «назад» and a tap beside it only
 * hide the sheet ([EventFormIntent.SheetHidden]). The answers of a repeat are pinned at its bottom, but end what scrolls in a window no
 * higher than 360 dp ([scopeAnswersPinned]); the sheet «Вид» in a low window puts «Готово» under its field while the keyboard is up.
 */
@Composable
internal fun EventFormSheetHost(state: EventFormState, onIntent: (EventFormIntent) -> Unit) {
    val pinned = scopeAnswersPinned(currentDockMetrics().compact)
    val low = lowSheetWindow()
    AppSheet(
        value = state.sheet,
        onHide = { onIntent(EventFormIntent.SheetHidden) },
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = AppSheetDefaults.TopClearance),
        bottom = { sheet -> buttonsOf(sheet, onIntent, pinned) },
        buttonsInContentOverKeyboard = { sheet -> sheet is EventFormSheet.Kind && low },
    ) { sheet -> SheetFace(sheet, state, onIntent, pinned) }
}

/**
 * The face of [EventFormState.sheet] in the frame without its window ([AppSheetCard]) — for a preview, where a window does not draw, and
 * for a test: the same content and the same buttons at its bottom as [EventFormSheetHost] shows.
 */
@Composable
fun EventFormSheetCard(state: EventFormState, modifier: Modifier = Modifier, onIntent: (EventFormIntent) -> Unit = {}) {
    val sheet = state.sheet ?: return
    val pinned = scopeAnswersPinned(currentDockMetrics().compact)
    AppSheetCard(modifier = modifier, bottom = buttonsOf(sheet, onIntent, pinned)) { SheetFace(sheet, state, onIntent, pinned) }
}

@Composable
private fun ColumnScope.SheetFace(sheet: EventFormSheet, state: EventFormState, onIntent: (EventFormIntent) -> Unit, pinned: Boolean) {
    when (sheet) {
        is EventFormSheet.Date -> DateSheetContent(sheet, onIntent)
        is EventFormSheet.Time -> TimeSheetContent(sheet, onIntent)
        is EventFormSheet.Duration -> DurationSheetContent(sheet, onIntent)
        is EventFormSheet.Until -> UntilSheetContent(sheet, state.kind, onIntent)
        is EventFormSheet.Kind -> KindSheetContent(sheet, state, onIntent)
        is EventFormSheet.Scope -> {
            ScopeEditContent(sheet.ask)
            if (!pinned) ScopeEditAnswers(sheet.ask, onIntent)
        }
    }
}

/** «Готово» at the bottom of every face — asleep under its reason in «Вид» — and the answers of a repeat where they are pinned. */
private fun buttonsOf(sheet: EventFormSheet, onIntent: (EventFormIntent) -> Unit, pinned: Boolean): (@Composable ColumnScope.() -> Unit)? = when (sheet) {
    is EventFormSheet.Scope -> if (pinned) ({ ScopeEditAnswers(sheet.ask, onIntent) }) else null
    is EventFormSheet.Kind -> {
        { KindSheetButtons(sheet, onIntent) }
    }
    else -> {
        { AppSheetButtons(main = stringResource(Res.string.profile_done), onMain = { onIntent(EventFormIntent.SheetDone) }) }
    }
}

/** The label of a sheet and its value under it: «28 сентября, понедельник», «До 31 декабря» — 22 sp / 800 — and its quiet caption. */
@Composable
private fun SheetHead(label: String, value: String, caption: String? = null) {
    val colors = MaterialTheme.colorScheme
    SectionLabel(label)
    Text(
        text = value,
        modifier = Modifier.padding(top = EventsDimens.SheetValueTop).semantics { liveRegion = LiveRegionMode.Polite },
        color = colors.onSurface,
        style = MaterialTheme.typography.headlineSmall.copy(
            fontSize = EventsDimens.SheetValue, lineHeight = EventsDimens.SheetValueHeight, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = TABULAR_FIGURES,
        ),
    )
    if (caption != null) {
        Text(
            text = caption,
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = EventsDimens.SheetCaption, lineHeight = EventsDimens.SheetCaptionHeight, fontFeatureSettings = TABULAR_FIGURES),
        )
    }
}

/**
 * «Дата» (spec 3.36.9; events-form.html 2): the date chosen in words, as the sheet of the day says it; the chips «Сегодня · Завтра · пн 5
 * окт.» — the chosen date a week on; the month with its year and the arrows — forward up to the limit of the form, back without one; the
 * grid of «Занятия» on the colour of the sheet, without the fill of the time and without the dimmed days to come, the marks of every
 * event and the legend of the kinds of the month under it. A reader hears a day «суббота, 24 октября; оркестр в 11:00», chosen or not.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DateSheetContent(sheet: EventFormSheet.Date, onIntent: (EventFormIntent) -> Unit) {
    SheetHead(stringResource(Res.string.event_field_date), Formats.dayWithWeekday(sheet.picked))
    FlowRow(
        modifier = Modifier.padding(top = EventsDimens.SheetChipsTop).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(EventsDimens.ChoiceGap),
        verticalArrangement = Arrangement.spacedBy(EventsDimens.ChoiceGap),
    ) {
        val tomorrow = sheet.today.plus(1, DateTimeUnit.DAY)
        AppChip.Choice(stringResource(Res.string.event_chip_today), selected = sheet.picked == sheet.today, onClick = { onIntent(EventFormIntent.DatePicked(sheet.today)) }, inSheet = true)
        AppChip.Choice(stringResource(Res.string.event_chip_tomorrow), selected = sheet.picked == tomorrow, onClick = { onIntent(EventFormIntent.DatePicked(tomorrow)) }, inSheet = true)
        AppChip.Choice(Formats.weekdayDate(sheet.weekLater), selected = false, onClick = { onIntent(EventFormIntent.DatePicked(sheet.weekLater)) }, inSheet = true)
    }
    SheetMonth(
        month = sheet.month, canBack = true, canForward = sheet.canForward,
        onStep = { onIntent(EventFormIntent.DateMonthStep(it)) },
    )
    SheetGrid(sheet.cells) { onIntent(EventFormIntent.DatePicked(it)) }
    if (sheet.legend.isNotEmpty()) KindLegend(sheet.legend, column = false, Modifier.padding(top = EventsDimens.LegendTop))
}

/** The month of a sheet (5.29 R9): its name with its year, 16 sp / 800, between the arrows of 44 on the colour one step over the sheet. */
@Composable
private fun SheetMonth(month: YearMonth, canBack: Boolean, canForward: Boolean, onStep: (Int) -> Unit) {
    val container = MaterialTheme.colorScheme.surfaceContainerHigh
    val title = Formats.monthAndYear(month)
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = EventsDimens.SheetMonthTop, bottom = EventsDimens.SheetMonthBottom),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArrowButton(forward = false, enabled = canBack, onClick = { onStep(-1) }, description = stringResource(Res.string.practice_month_back), container = container)
        Text(
            text = title,
            modifier = Modifier.weight(1f).clearAndSetSemantics {
                heading()
                liveRegion = LiveRegionMode.Polite
                contentDescription = title
            },
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = EventsDimens.SheetMonth, lineHeight = EventsDimens.SheetMonthHeight, fontWeight = FontWeight.ExtraBold),
        )
        ArrowButton(forward = true, enabled = canForward, onClick = { onStep(1) }, description = stringResource(Res.string.practice_month_forward), container = container)
    }
}

/** The weekdays and the grid of the month on the colour of the sheet: no fill of the time, the days to come not dimmed. */
@Composable
private fun SheetGrid(cells: List<CalendarCell?>, onDay: (LocalDate) -> Unit) {
    Weekdays(Modifier)
    MonthGrid(
        cells = cells,
        metrics = CalendarMetrics.Portrait,
        onDaySelected = onDay,
        ground = MaterialTheme.colorScheme.surfaceContainer,
        timeFill = false,
        dimFuture = false,
        speech = DaySpeech.FORM,
    )
}

/**
 * «Время» (spec 3.36.9; events-form.html 3): «Весь день» with the switch of R1 and its caption; under it the wheels of the hours and the
 * minutes — the chosen row on its plate — the end under them, «до 20:00 · 1 ч 30 мин» (past midnight «до 01:00, вс 25 окт.»), and
 * «Частое»: the starts of the events by how often they are used, one tap each. «Весь день» on — the words in the place of the wheels.
 * Lying — the wheels at the left, «Весь день» and «Частое» at the right.
 */
@Composable
private fun TimeSheetContent(sheet: EventFormSheet.Time, onIntent: (EventFormIntent) -> Unit) {
    SectionLabel(stringResource(Res.string.event_field_time))
    val window = LocalWindowInfo.current.containerSize
    if (window.width > window.height) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(EventsDimens.AllDayGap)) {
            Column(Modifier.weight(1f)) { WheelsOrNote(sheet, onIntent) }
            Column(Modifier.weight(1f)) {
                AllDayRow(sheet.allDay) { onIntent(EventFormIntent.AllDayToggled) }
                if (!sheet.allDay) Frequent(sheet, onIntent)
            }
        }
    } else {
        AllDayRow(sheet.allDay) { onIntent(EventFormIntent.AllDayToggled) }
        WheelsOrNote(sheet, onIntent)
        if (!sheet.allDay) Frequent(sheet, onIntent)
    }
}

/** «Весь день»: a row of 56 that toggles as a whole — its words, its caption and the switch of R1 52 × 32. */
@Composable
private fun AllDayRow(on: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = EventsDimens.AllDayRowMin)
            .toggleable(value = on, role = Role.Switch, onValueChange = { onToggle() }),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EventsDimens.AllDayGap),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.event_all_day_title),
                color = colors.onSurface,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = EventsDimens.AllDayText, lineHeight = EventsDimens.AllDayTextHeight, fontWeight = FontWeight.Bold),
            )
            Text(
                text = stringResource(Res.string.event_all_day_caption),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = EventsDimens.AllDayCaption, lineHeight = EventsDimens.AllDayCaptionHeight, fontWeight = FontWeight.Medium),
            )
        }
        AppSwitchMark(on)
    }
}

/**
 * The wheels and the end under them — or, «Весь день» on, what it means. The end is 14 sp in the second level of text, the time in it in
 * the first, 700 (5.29 R9: «до **20:00** · 1 ч 30 мин»).
 */
@Composable
private fun WheelsOrNote(sheet: EventFormSheet.Time, onIntent: (EventFormIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    if (sheet.allDay) {
        Text(
            text = stringResource(Res.string.event_all_day_note),
            modifier = Modifier.padding(top = EventsDimens.AllDayNoteTop),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = EventsDimens.AllDayNote, lineHeight = EventsDimens.AllDayNoteHeight),
        )
        return
    }
    Wheels(sheet.minutes, sheet.step, onIntent)
    sheet.end?.let { end ->
        val clock = Formats.clockOf(end.minutes)
        val words = if (end.nextDay != null) {
            endWords(end)
        } else {
            stringResource(Res.string.event_end_line, clock, Formats.minutesInWords((sheet.duration ?: 0) * MS_PER_MINUTE))
        }
        val ink = colors.onSurface
        val text = remember(words, clock, ink) {
            buildAnnotatedString {
                append(words)
                val at = words.indexOf(clock)
                if (at >= 0) addStyle(SpanStyle(color = ink, fontWeight = FontWeight.Bold), at, at + clock.length)
            }
        }
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = EventsDimens.WheelEnd, lineHeight = EventsDimens.WheelEndHeight, fontFeatureSettings = TABULAR_FIGURES),
        )
    }
}

/**
 * The two wheels — the hours, the minutes by [step] — and the colon between them (5.29 R9): the plate of the chosen row across them, and
 * over them a fade to the colour of the sheet over a third of their height at each edge.
 */
@Composable
private fun Wheels(minutes: Int, step: Int, onIntent: (EventFormIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val plate = colors.surfaceContainerHigh
    val sheet = colors.surfaceContainer
    val minuteValues = List(MINUTES_PER_HOUR / step) { it * step }
    val hourWords = saidOf(Hours, Res.string.event_wheel_hours_one, Res.string.event_wheel_hours_few, Res.string.event_wheel_hours_many)
    val minuteWords = saidOf(minuteValues, Res.string.event_wheel_minutes_one, Res.string.event_wheel_minutes_few, Res.string.event_wheel_minutes_many)
    val up = stringResource(Res.string.event_wheel_up)
    val down = stringResource(Res.string.event_wheel_down)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = EventsDimens.WheelTop, bottom = EventsDimens.WheelBottom)
            .drawBehind {
                val row = EventsDimens.WheelRow.toPx()
                val top = row * (EventsDimens.WHEEL_ROWS / 2)
                drawRoundRect(plate, Offset(0f, top), Size(size.width, row), CornerRadius(EventsDimens.WheelPlateCorner.toPx()))
            }
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.verticalGradient(
                        0f to sheet, EventsDimens.WHEEL_FADE to Color.Transparent, 1f - EventsDimens.WHEEL_FADE to Color.Transparent, 1f to sheet,
                    ),
                )
            },
        horizontalArrangement = Arrangement.spacedBy(EventsDimens.WheelGap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TimeWheel(
            labels = Hours.map { it.toString().padStart(2, '0') },
            selected = EventFormReducer.hourOf(minutes),
            said = { hourWords[it] },
            up = up,
            down = down,
            onSettle = { onIntent(EventFormIntent.TimeHour(it)) },
        )
        Text(
            text = ":",
            color = colors.onSurface,
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = EventsDimens.WheelChosen, lineHeight = EventsDimens.WheelChosen, fontWeight = FontWeight.ExtraBold),
        )
        TimeWheel(
            labels = minuteValues.map { it.toString().padStart(2, '0') },
            selected = EventFormReducer.minuteOf(minutes) / step,
            said = { minuteWords[it] },
            up = up,
            down = down,
            onSettle = { onIntent(EventFormIntent.TimeMinute(minuteValues[it])) },
        )
    }
}

/** «18 часов», «30 минут» — the values of a wheel for TalkBack, in the forms of the language: one string of each value. */
@Composable
private fun saidOf(values: List<Int>, one: StringResource, few: StringResource, many: StringResource): List<String> =
    values.map { value -> stringResource(Formats.plural(value, one, few, many), value) }

/** «Частое» (spec 3.36.9): its caption 13 sp / 700 and up to four chips of starts, not narrower than 60. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Frequent(sheet: EventFormSheet.Time, onIntent: (EventFormIntent) -> Unit) {
    if (sheet.frequent.isEmpty()) return
    Text(
        text = stringResource(Res.string.event_frequent),
        modifier = Modifier.padding(top = EventsDimens.FrequentTop),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelLarge.copy(fontSize = EventsDimens.ChangeLabel, fontWeight = FontWeight.Bold),
    )
    FlowRow(
        modifier = Modifier.padding(top = EventsDimens.FrequentChipsTop).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(EventsDimens.ChoiceGap),
        verticalArrangement = Arrangement.spacedBy(EventsDimens.ChoiceGap),
    ) {
        sheet.frequent.forEach { start ->
            AppChip.Choice(
                text = Formats.clockOf(start),
                selected = sheet.minutes == start,
                onClick = { onIntent(EventFormIntent.FrequentPicked(start)) },
                modifier = Modifier.widthIn(min = EventsDimens.FrequentChipMin),
                inSheet = true,
            )
        }
    }
}

/**
 * «Длительность» (spec 3.36.9; events-form.html 4): the stepper of R3 with its number at 32 sp, by 15 minutes from 15 to 8 hours, «с 11:00 до
 * 13:30» under the number; the hint under it — «От 15 мин до 8 ч, шаг — 15 мин.», at the ceiling «8 ч — самое длинное. …» — its place kept
 * by the longer of the two.
 */
@Composable
private fun DurationSheetContent(sheet: EventFormSheet.Duration, onIntent: (EventFormIntent) -> Unit) {
    SectionLabel(stringResource(Res.string.event_field_duration))
    val value = Formats.minutesInWords(sheet.minutes * MS_PER_MINUTE)
    val span = stringResource(Res.string.event_duration_span, Formats.clockOf(sheet.start), Formats.clockOf(sheet.end.minutes))
    Stepper(
        value = value,
        onStep = { onIntent(EventFormIntent.DurationStepped(it)) },
        canStepDown = sheet.minutes > sheet.min,
        canStepUp = sheet.minutes < sheet.max,
        downDescription = stringResource(Res.string.practice_step_down, sheet.step),
        upDescription = stringResource(Res.string.practice_step_up, sheet.step),
        modifier = Modifier.padding(top = EventsDimens.DurationStepperTop),
        caption = span,
        valueDescription = stringResource(Res.string.practice_pair_description, value, span),
        valueSize = EventsDimens.DURATION_VALUE_SP,
    )
    val most = Formats.minutesInWords(sheet.max * MS_PER_MINUTE)
    val hint = stringResource(Res.string.event_duration_hint, Formats.minutesInWords(sheet.min * MS_PER_MINUTE), most, Formats.minutesInWords(sheet.step * MS_PER_MINUTE))
    val ceiling = stringResource(Res.string.event_duration_ceiling, most)
    val shown = if (sheet.minutes >= sheet.max) ceiling else hint
    Box(Modifier.fillMaxWidth().padding(top = EventsDimens.SheetHintTop), contentAlignment = Alignment.TopCenter) {
        // the place of the longer of the two, seen or not: the stepper does not move when one takes the place of the other
        listOf(hint, ceiling).filter { it != shown }.forEach { Hint(it, Modifier.alpha(0f).clearAndSetSemantics { }) }
        Hint(shown)
    }
}

/** A hint of a sheet: 13 sp in the second level of text, in the middle. */
@Composable
private fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = EventsDimens.SheetHint, lineHeight = EventsDimens.SheetHintHeight),
    )
}

/**
 * «Повторять до» (spec 3.36.9; events-form.html 5): the end in words — «До 31 декабря» and «последний урок — пн 28 декабря · всего 14», or
 * «Без конца» and «каждый понедельник с 28 сентября»; the chips «Без конца · До 31 дек. · До 31 мая»; the month with the marks of this
 * repeat up to its end, the end with the ring «выбран», the days before the first event asleep; under the grid, while there is no end, how
 * to choose one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UntilSheetContent(sheet: EventFormSheet.Until, kind: EventKind, onIntent: (EventFormIntent) -> Unit) {
    val picked = sheet.picked
    val value = picked?.let { stringResource(Res.string.event_until_title, Formats.dayAndMonth(it)) } ?: stringResource(Res.string.event_until_none)
    val caption = if (picked != null) {
        sheet.summary?.last?.let { last -> stringResource(byWord(sheet.word, Res.string.event_until_last_lesson, Res.string.event_until_last_rehearsal, Res.string.event_until_last_event), Formats.weekdayDayMonth(last), sheet.summary.count) }
    } else {
        stringResource(noEndOf(sheet.repeat, sheet.first.dayOfWeek), Formats.dayAndMonth(sheet.first))
    }
    SheetHead(stringResource(Res.string.event_sheet_until), value, caption)
    FlowRow(
        modifier = Modifier.padding(top = EventsDimens.SheetChipsTop).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(EventsDimens.ChoiceGap),
        verticalArrangement = Arrangement.spacedBy(EventsDimens.ChoiceGap),
    ) {
        AppChip.Choice(stringResource(Res.string.event_until_none), selected = picked == null, onClick = { onIntent(EventFormIntent.UntilPicked(null)) }, inSheet = true)
        sheet.chips.forEach { date ->
            AppChip.Choice(stringResource(Res.string.event_until_title, Formats.shortDate(date)), selected = picked == date, onClick = { onIntent(EventFormIntent.UntilPicked(date)) }, inSheet = true)
        }
    }
    SheetMonth(month = sheet.month, canBack = sheet.canBack, canForward = true, onStep = { onIntent(EventFormIntent.UntilMonthStep(it)) })
    SheetGrid(sheet.cells) { onIntent(EventFormIntent.UntilPicked(it)) }
    if (picked == null) {
        Hint(
            stringResource(byWord(sheet.word, Res.string.event_until_hint_lesson, Res.string.event_until_hint_rehearsal, Res.string.event_until_hint_event)),
            Modifier.padding(top = EventsDimens.SheetHintTop),
        )
    }
}

/** «каждый понедельник с …», «каждый второй понедельник с …» — whole sentences of each weekday (plan D32). */
private fun noEndOf(repeat: Repeat, weekday: DayOfWeek): StringResource {
    val weekly = listOf(
        Res.string.event_no_end_weekly_1, Res.string.event_no_end_weekly_2, Res.string.event_no_end_weekly_3, Res.string.event_no_end_weekly_4,
        Res.string.event_no_end_weekly_5, Res.string.event_no_end_weekly_6, Res.string.event_no_end_weekly_7,
    )
    val biweekly = listOf(
        Res.string.event_no_end_biweekly_1, Res.string.event_no_end_biweekly_2, Res.string.event_no_end_biweekly_3, Res.string.event_no_end_biweekly_4,
        Res.string.event_no_end_biweekly_5, Res.string.event_no_end_biweekly_6, Res.string.event_no_end_biweekly_7,
    )
    return (if (repeat == Repeat.BIWEEKLY) biweekly else weekly)[weekday.ordinal]
}

/**
 * The sheet of an edit of an event of a repeat (spec 3.36.9; events-form.html 6): the label «Урок повторяется», the question — «Изменить урок
 * 19 октября или все уроки с этого дня?», of a new date «Перенести урок 19 октября на вт 20 окт., 18:00?» — the plate of what changes and the
 * note: «Прошедшие уроки не изменятся — их заметки и записи остаются.», of a move the short one.
 */
@Composable
private fun ScopeEditContent(ask: ScopeAsk) {
    val word = ask.word
    val moved = ask.moved
    val question = if (moved == null) {
        stringResource(byWord(word, Res.string.event_series_edit_q_lesson, Res.string.event_series_edit_q_rehearsal, Res.string.event_series_edit_q_event), Formats.dayAndMonth(ask.date))
    } else {
        val to = moved.startMinutes?.let { stringResource(Res.string.event_date_time, Formats.weekdayDate(moved.date), Formats.clockOf(it)) } ?: Formats.weekdayDate(moved.date)
        stringResource(byWord(word, Res.string.event_series_move_q_lesson, Res.string.event_series_move_q_rehearsal, Res.string.event_series_move_q_event), Formats.dayAndMonth(ask.date), to)
    }
    val note = if (moved == null) {
        stringResource(byWord(word, Res.string.event_series_past_long_lesson, Res.string.event_series_past_long_rehearsal, Res.string.event_series_past_long_event))
    } else {
        stringResource(byWord(word, Res.string.event_series_past_short_lesson, Res.string.event_series_past_short_rehearsal, Res.string.event_series_past_short_event))
    }
    ScopeSheetContent(
        question = question,
        note = note,
        label = stringResource(byWord(word, Res.string.event_series_label_lesson, Res.string.event_series_label_rehearsal, Res.string.event_series_label_event)),
        change = ask.change?.let { { ChangePlate(it) } },
    )
}

/**
 * The answers of an edit (spec 3.36.9, plan D6, D31): the reasonable one filled and first — of a new date «Только этот урок · остальные — по
 * понедельникам в 17:00», else «Этот и следующие · 19, 26 окт. и дальше» — the other an outline; a new step or «Не повторять» — «Этот и
 * следующие» alone. «Отмена» quietly under them.
 */
@Composable
private fun ScopeEditAnswers(ask: ScopeAsk, onIntent: (EventFormIntent) -> Unit) {
    val word = ask.word
    val moved = ask.moved
    val dates = ask.following.dates
    val listed = if (dates.isEmpty()) {
        stringResource(Res.string.event_series_from_date, Formats.dayAndMonth(ask.date))
    } else {
        Formats.dateList(dates).let { list -> if (ask.following.andOn) stringResource(Res.string.event_dates_and_on, list) else list }
    }
    val restDays = stringArrayResource(Res.array.event_rest_on_weekdays)
    val thisCaption = if (moved == null) {
        stringResource(Res.string.event_series_only_date, Formats.dayAndMonth(ask.date))
    } else {
        moved.restStartMinutes?.let { stringResource(restAt(moved.restWeekday), Formats.clockOf(it)) } ?: restDays.getOrElse(moved.restWeekday.ordinal) { restDays.first() }
    }
    val followingCaption = if (moved == null || ask.question == ScopeQuestion.FollowingOnly) {
        listed
    } else {
        moved.startMinutes?.let { stringResource(newFromAt(moved.date.dayOfWeek), Formats.shortDate(moved.date), Formats.clockOf(it)) }
            ?: stringResource(newFrom(moved.date.dayOfWeek), Formats.shortDate(moved.date))
    }
    val following = ScopeAnswer(
        title = stringResource(byWord(word, Res.string.event_series_following_lesson, Res.string.event_series_following_rehearsal, Res.string.event_series_following_event)),
        caption = followingCaption,
        style = AppButtonStyle.Main,
        onClick = { onIntent(EventFormIntent.ScopeAnswered(EditScope.FOLLOWING)) },
    )
    val onlyThis = ScopeAnswer(
        title = stringResource(byWord(word, Res.string.event_series_this_lesson, Res.string.event_series_this_rehearsal, Res.string.event_series_this_event)),
        caption = thisCaption,
        style = AppButtonStyle.Main,
        onClick = { onIntent(EventFormIntent.ScopeAnswered(EditScope.ONLY_THIS)) },
    )
    val answers = when (val question = ask.question) {
        is ScopeQuestion.Both -> if (question.filled == EditScope.ONLY_THIS) {
            listOf(onlyThis, following.copy(style = AppButtonStyle.Outline))
        } else {
            listOf(following, onlyThis.copy(style = AppButtonStyle.Outline))
        }
        else -> listOf(following)
    }
    ScopeAnswers(answers = answers, cancel = stringResource(Res.string.dialog_cancel), onCancel = { onIntent(EventFormIntent.SheetHidden) })
}

/**
 * The plate «что меняется» (5.29 R9, «Листы повтора»): on the ground of the screen at a corner of 14, the caption of the field — none of a
 * date — the old value quietly, the arrow, the new value in the first level of text: «Время 17:00 → 17:30», «пн 19 · 17:00 → вт 20 · 18:00».
 * Each takes the room its words need ([ChangePlateMath]): «Преподаватель — → Анна Сергеевна» stands whole beside a short old value, and
 * where the row cannot hold them, they wrap by their words and the plate grows from its 48 — nothing is cut with an ellipsis.
 */
@Composable
private fun ChangePlate(line: ChangeLine) {
    val colors = MaterialTheme.colorScheme
    val label = when (line.field) {
        ChangedField.DATE -> null
        ChangedField.TIME -> stringResource(Res.string.event_field_time)
        ChangedField.DURATION -> stringResource(Res.string.event_field_duration)
        ChangedField.KIND -> stringResource(Res.string.event_kind_sheet_title)
        ChangedField.TITLE -> stringResource(Res.string.event_field_title)
        ChangedField.PLACE -> stringResource(if (EventFormReducer.isLesson(line.afterKind.ref)) Res.string.event_field_teacher else Res.string.event_field_place)
        ChangedField.REPEAT -> stringResource(Res.string.event_field_repeat)
    }
    val before = valueOf(line.field, line.before, line.beforeRepeat, line.beforeKind)
    val after = valueOf(line.field, line.after, line.afterRepeat, line.afterKind)
    val value = MaterialTheme.typography.titleSmall.copy(fontSize = EventsDimens.ChangeValue, lineHeight = EventsDimens.ChangeValueHeight, fontFeatureSettings = TABULAR_FIGURES)
    Box(
        modifier = Modifier
            .padding(top = EventsDimens.ChangeTop)
            .fillMaxWidth()
            .heightIn(min = EventsDimens.ChangeMin)
            .background(colors.surface, RoundedCornerShape(EventsDimens.ChangeCorner))
            .padding(horizontal = EventsDimens.ChangePaddingH, vertical = EventsDimens.ChangePaddingV),
        contentAlignment = Alignment.CenterStart,
    ) {
        PlateRow(captioned = label != null) {
            if (label != null) {
                Text(label, color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelLarge.copy(fontSize = EventsDimens.ChangeLabel, fontWeight = FontWeight.Bold))
            }
            Text(before, color = colors.onSurfaceVariant, style = value.copy(fontWeight = FontWeight.SemiBold))
            AppIcon(AppIcons.ArrowRight, contentDescription = null, size = EventsDimens.ChangeArrow, tint = ViolinTheme.textTertiary)
            Text(after, color = colors.onSurface, style = value.copy(fontWeight = FontWeight.Bold))
        }
    }
}

/**
 * The row of the plate: [content] is the caption (when [captioned]), the old value, the arrow and the new value, in this order; the texts
 * get the widths of [ChangePlateMath], [EventsDimens.ChangeGap] apart, each in the middle of the row's height; the caption over the values
 * where [ChangePlateMath] puts it there.
 */
@Composable
private fun PlateRow(captioned: Boolean, content: @Composable () -> Unit) {
    Layout(content) { measurables, constraints ->
        val gap = EventsDimens.ChangeGap.roundToPx()
        val arrowAt = if (captioned) 2 else 1
        val texts = measurables.filterIndexed { i, _ -> i != arrowAt }
        val arrow = measurables[arrowAt].measure(Constraints())
        val width = constraints.maxWidth
        val plan = ChangePlateMath.layout(
            needs = texts.map { it.maxIntrinsicWidth(Constraints.Infinity) },
            least = texts.map { it.minIntrinsicWidth(Constraints.Infinity) },
            width = width,
            arrow = arrow.width,
            gap = gap,
            captioned = captioned,
        )
        val placed = texts.mapIndexed { i, text -> text.measure(Constraints(maxWidth = plan.widths[i].coerceIn(0, width))) }
        val caption = if (captioned) placed.first() else null
        val values = if (captioned) placed.drop(1) else placed
        val row = listOf(values[0], arrow, values[1])
        val inRow = if (caption != null && !plan.captionAbove) listOf(caption) + row else row
        val rowHeight = inRow.maxOf { it.height }
        val top = if (caption != null && plan.captionAbove) caption.height + gap else 0
        layout(width, top + rowHeight) {
            if (caption != null && plan.captionAbove) caption.place(0, 0)
            var x = 0
            inRow.forEach { part ->
                part.place(x, top + (rowHeight - part.height) / 2)
                x += part.width + gap
            }
        }
    }
}

/** A value of the plate, as the form shows it. */
@Composable
private fun valueOf(field: ChangedField, draft: EventDraft, repeat: Repeat, kind: EventKind): String = when (field) {
    ChangedField.DATE -> {
        val day = Formats.weekdayDay(draft.date)
        draft.startMinutes?.let { day + stringResource(Res.string.dot_separator) + Formats.clockOf(it) } ?: day
    }
    ChangedField.TIME -> draft.startMinutes?.let { Formats.clockOf(it) } ?: stringResource(Res.string.event_all_day)
    ChangedField.DURATION -> draft.durationMinutes?.let { Formats.quickDuration(it) } ?: stringResource(Res.string.event_duration_none)
    ChangedField.KIND -> eventKindName(kind)
    ChangedField.TITLE -> draft.title.ifBlank { eventKindName(kind) }
    ChangedField.PLACE -> draft.place.ifBlank { stringResource(Res.string.practice_no_value) }
    ChangedField.REPEAT -> stringResource(
        when (repeat) {
            Repeat.NONE -> Res.string.event_repeat_none
            Repeat.WEEKLY -> Res.string.event_repeat_weekly
            Repeat.BIWEEKLY -> Res.string.event_repeat_biweekly
        },
    )
}

/** «остальные — по понедельникам в %1$s» of each weekday. */
private fun restAt(weekday: DayOfWeek): StringResource = listOf(
    Res.string.event_rest_on_at_1, Res.string.event_rest_on_at_2, Res.string.event_rest_on_at_3, Res.string.event_rest_on_at_4,
    Res.string.event_rest_on_at_5, Res.string.event_rest_on_at_6, Res.string.event_rest_on_at_7,
)[weekday.ordinal]

/** «с %1$s — по вторникам» of each weekday. */
private fun newFrom(weekday: DayOfWeek): StringResource = listOf(
    Res.string.event_new_from_on_1, Res.string.event_new_from_on_2, Res.string.event_new_from_on_3, Res.string.event_new_from_on_4,
    Res.string.event_new_from_on_5, Res.string.event_new_from_on_6, Res.string.event_new_from_on_7,
)[weekday.ordinal]

/** «с %1$s — по вторникам в %2$s» of each weekday. */
private fun newFromAt(weekday: DayOfWeek): StringResource = listOf(
    Res.string.event_new_from_on_at_1, Res.string.event_new_from_on_at_2, Res.string.event_new_from_on_at_3, Res.string.event_new_from_on_at_4,
    Res.string.event_new_from_on_at_5, Res.string.event_new_from_on_at_6, Res.string.event_new_from_on_at_7,
)[weekday.ordinal]

/** The string of the word of the kind: «урок», «репетиция», «событие». */
internal fun byWord(word: SeriesWord, lesson: StringResource, rehearsal: StringResource, event: StringResource): StringResource = when (word) {
    SeriesWord.LESSON -> lesson
    SeriesWord.REHEARSAL -> rehearsal
    SeriesWord.EVENT -> event
}
