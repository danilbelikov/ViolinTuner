package com.violinjourney.app.feature.practice.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.components.AppButton
import com.violinjourney.app.core.ui.components.AppButtonStyle
import com.violinjourney.app.core.ui.components.SectionLabel
import com.violinjourney.app.core.ui.components.WeekBars
import com.violinjourney.app.core.ui.components.WeekBarsSize
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.icons.IconSizes
import com.violinjourney.app.core.ui.theme.AppShapes
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.practice.PracticeTimer
import com.violinjourney.app.feature.practice.RunningBlockLine
import com.violinjourney.app.feature.practice.TodayWeek
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.block_done
import com.violinjourney.app.shared.resources.block_left_few
import com.violinjourney.app.shared.resources.block_left_many
import com.violinjourney.app.shared.resources.block_left_one
import com.violinjourney.app.shared.resources.block_line
import com.violinjourney.app.shared.resources.practice_back_to_live
import com.violinjourney.app.shared.resources.practice_first_quoted
import com.violinjourney.app.shared.resources.practice_first_start
import com.violinjourney.app.shared.resources.practice_first_stop
import com.violinjourney.app.shared.resources.practice_first_stop_name
import com.violinjourney.app.shared.resources.practice_first_text
import com.violinjourney.app.shared.resources.practice_first_title
import com.violinjourney.app.shared.resources.practice_running
import com.violinjourney.app.shared.resources.practice_start
import com.violinjourney.app.shared.resources.practice_timer_description
import com.violinjourney.app.shared.resources.practice_today_label
import com.violinjourney.app.shared.resources.practice_today_not_yet
import com.violinjourney.app.shared.resources.practice_week
import com.violinjourney.app.shared.resources.practice_week_description
import com.violinjourney.app.shared.resources.practice_weekdays
import com.violinjourney.app.shared.resources.practice_with_running
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

private const val TABULAR_FIGURES = "tnum"
private val HeadGap = 6.dp
private val WeekLineTop = 12.dp
private val InlineGap = 8.dp
private val FirstPadding = 18.dp
private val StepCircle = 28.dp
private val StepsGap = 12.dp
private val StepsTop = 14.dp
private val CardBorder = 1.dp
private val CardCorner = 18.dp
private val BackToLiveTop = 12.dp
private val BlockRule = 1.dp
private const val RUNNING_FROM_DEGREES = 160.0
private const val RUNNING_GROUND_STOP = 0.7f
private const val RUNNING_BORDER_ALPHA = 0.3f

/** A mark no text holds: the place of the name of a button inside the words of a step of the first run. */
private const val NAME_MARK = "\u0001"

/** Sizes of the cards of «Сегодня» that differ between the layouts (spec 5.29 R2). */
@Immutable
internal data class TodayMetrics(
    val cardPadding: PaddingValues,
    /** The number of «Сегодня», and «Ещё не играли» in its place. */
    val todaySize: Int,
    val notYetSize: Int,
    val timerSize: Int,
    /** Above and below the timer. */
    val timerTop: Dp,
    val timerBottom: Dp,
    /** Above the rule over the line of the block, and between the rule and the line. */
    val blockTop: Dp,
    val blockRuleGap: Dp,
    val bars: WeekBarsSize,
    val barsTop: Dp,
    /** «Неделя» in the line of the number, when it fits (landscape): the column is short. */
    val weekInline: Boolean,
    /** «Вернуться к Live» in the card of the running practice; in landscape the tab Live of the compact bar is it. */
    val backToLive: Boolean,
) {
    companion object {
        val Portrait = TodayMetrics(
            cardPadding = PaddingValues(16.dp), todaySize = 40, notYetSize = 30, timerSize = 64, timerTop = 10.dp, timerBottom = 6.dp,
            blockTop = 10.dp, blockRuleGap = 10.dp, bars = WeekBarsSize.Regular, barsTop = 14.dp, weekInline = false, backToLive = true,
        )
        val Landscape = TodayMetrics(
            cardPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp), todaySize = 28, notYetSize = 22, timerSize = 48,
            timerTop = 4.dp, timerBottom = 2.dp, blockTop = 6.dp, blockRuleGap = 7.dp, bars = WeekBarsSize.Small, barsTop = 10.dp,
            weekInline = true, backToLive = false,
        )
    }
}

/**
 * «Сегодня» (spec 3.36.2): the label and the chip of the streak, today's time large — «Ещё не играли» while there is none — the
 * seven bars of the week and «Неделя» with its sum, only when the week has any. Not pressed: a day is opened in the calendar. The
 * chip goes under the label when they do not fit side by side; in landscape «Неделя» stands in the line of the number when it
 * fits beside it, else under the bars — the number is never made smaller for it.
 */
@Composable
internal fun TodayCard(
    numbers: ShownNumbers,
    today: LocalDate,
    floorMinutes: Int,
    metrics: TodayMetrics,
    onSway: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val played = numbers.todayMs > 0
    val weekLine = numbers.weekMs > 0
    Column(modifier.fillMaxWidth().background(colors.surfaceContainer, AppShapes.M).padding(metrics.cardPadding)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(HeadGap),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel(stringResource(Res.string.practice_today_label))
            StreakChip(days = numbers.streakDays, running = false, scope = numbers.scope, shownDays = numbers.rolledStreakDays, onSway = onSway)
        }
        val weekWords = stringResource(Res.string.practice_week)
        if (played && weekLine && metrics.weekInline) {
            // Beside the number only when both fit at their full size: the number is never made smaller for it. Measured is what
            // is drawn — the sum bold — and the wider of the figures rolled to and those on the screen while they roll: the line
            // chosen for «2 ч» must hold «1 ч 59 мин» on its way there.
            val number = numberStyle(metrics.todaySize)
            val week = weekStyle()
            val sumColor = colors.onSurface
            val measurer = rememberTextMeasurer()
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                fun width(text: AnnotatedString, style: TextStyle) = measurer.measure(text, style, softWrap = false, maxLines = 1).size.width
                val todayWidth = maxOf(
                    width(AnnotatedString(Formats.minutesInWords(numbers.todayMs)), number),
                    width(AnnotatedString(Formats.minutesInWords(numbers.rolledTodayMs)), number),
                )
                val weekWidth = maxOf(
                    width(weekWordsText(weekWords, numbers.weekMs, sumColor), week),
                    width(weekWordsText(weekWords, numbers.rolledWeekMs, sumColor), week),
                )
                val needed = todayWidth + weekWidth + with(LocalDensity.current) { InlineGap.roundToPx() }
                TodayBody(numbers, today, floorMinutes, metrics, weekWords, inline = needed <= constraints.maxWidth)
            }
        } else {
            TodayBody(numbers, today, floorMinutes, metrics, weekWords, inline = false)
        }
    }
}

/** Under the head of «Сегодня»: the number or «Ещё не играли», the bars and «Неделя» — [inline] in the line of the number. */
@Composable
private fun TodayBody(numbers: ShownNumbers, today: LocalDate, floorMinutes: Int, metrics: TodayMetrics, weekWords: String, inline: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        when {
            numbers.todayMs <= 0 -> Text(
                text = stringResource(Res.string.practice_today_not_yet),
                modifier = Modifier.padding(top = HeadGap),
                color = MaterialTheme.colorScheme.onSurface,
                style = numberStyle(metrics.notYetSize).copy(letterSpacing = (-0.01).em, fontFeatureSettings = null),
            )
            inline -> Row(Modifier.fillMaxWidth().padding(top = HeadGap), horizontalArrangement = Arrangement.SpaceBetween) {
                TodayNumber(numbers, metrics, Modifier.alignByBaseline())
                WeekWords(weekWords, numbers.rolledWeekMs, Modifier.alignByBaseline().padding(start = InlineGap))
            }
            else -> TodayNumber(numbers, metrics, Modifier.padding(top = HeadGap))
        }
        WeekBarsOf(numbers, today, floorMinutes, metrics, hatch = false, runningMinutes = 0, Modifier.padding(top = metrics.barsTop))
        if (numbers.weekMs > 0 && !inline) WeekLine(weekWords, numbers.rolledWeekMs, Modifier.padding(top = WeekLineTop))
    }
}

/** The number of «Сегодня»: smaller only when it does not fit the width (a large font, long words), never cut. */
@Composable
private fun TodayNumber(numbers: ShownNumbers, metrics: TodayMetrics, modifier: Modifier) {
    Text(
        text = Formats.minutesInWords(numbers.rolledTodayMs),
        modifier = modifier,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = MIN_NUMBER_SIZE.sp, maxFontSize = metrics.todaySize.sp, stepSize = 1.sp),
        style = numberStyle(metrics.todaySize),
    )
}

private const val MIN_NUMBER_SIZE = 24

/** «Неделя 4 ч 10 мин» in the line of the number: the words, and the sum bold in [sumColor] — what [TodayCard] measures, too. */
private fun weekWordsText(words: String, weekMs: Long, sumColor: Color): AnnotatedString = buildAnnotatedString {
    append(words)
    append(' ')
    withStyle(SpanStyle(color = sumColor, fontWeight = FontWeight.Bold)) { append(Formats.minutesInWords(weekMs)) }
}

@Composable
private fun WeekWords(words: String, weekMs: Long, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = weekWordsText(words, weekMs, colors.onSurface),
        modifier = modifier,
        color = colors.onSurfaceVariant,
        maxLines = 1,
        softWrap = false,
        // it is measured to fit; should a font still tell otherwise, the end is marked, not cut silently
        overflow = TextOverflow.Ellipsis,
        style = weekStyle(),
    )
}

/** «Неделя» on the left and its sum on the right, under the bars. */
@Composable
private fun WeekLine(words: String, weekMs: Long, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(words, color = colors.onSurfaceVariant, style = weekStyle(), modifier = Modifier.weight(1f, fill = false))
        Text(
            text = Formats.minutesInWords(weekMs),
            modifier = Modifier.padding(start = InlineGap),
            color = colors.onSurface,
            maxLines = 1,
            style = weekStyle().copy(fontWeight = FontWeight.Bold),
        )
    }
}

/** The seven bars (spec 5.29): one description for TalkBack — the week and today, saved only. */
@Composable
private fun WeekBarsOf(
    numbers: ShownNumbers,
    today: LocalDate,
    floorMinutes: Int,
    metrics: TodayMetrics,
    hatch: Boolean,
    runningMinutes: Int,
    modifier: Modifier,
) {
    val labels = stringArrayResource(Res.array.practice_weekdays)
    val bars = TodayWeek.days(numbers.weekDaysMs, today, labels, runningMinutes, hatch, floorMinutes)
    WeekBars(
        days = bars.days,
        running = hatch,
        scaleMinutes = bars.scaleMinutes,
        description = stringResource(Res.string.practice_week_description, Formats.minutesInWords(numbers.weekMs), Formats.minutesInWords(numbers.todayMs)),
        modifier = modifier,
        size = metrics.bars,
    )
}

/**
 * The first run (spec 3.36.2, «Здесь будет ваша неделя»): what the time at the violin turns into, the two steps with the names of
 * the buttons, and an empty week under them. Also while the very first practice has not been saved.
 */
@Composable
internal fun FirstWeekCard(
    numbers: ShownNumbers,
    today: LocalDate,
    floorMinutes: Int,
    metrics: TodayMetrics,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().background(colors.surfaceContainer, AppShapes.M).padding(FirstPadding)) {
        Text(
            text = stringResource(Res.string.practice_first_title),
            color = colors.onSurface,
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.01).em),
        )
        Text(
            text = stringResource(Res.string.practice_first_text),
            modifier = Modifier.padding(top = HeadGap),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 21.sp),
        )
        Column(Modifier.padding(top = StepsTop), verticalArrangement = Arrangement.spacedBy(StepsGap)) {
            Step(1, Res.string.practice_first_start, stringResource(Res.string.practice_start))
            Step(2, Res.string.practice_first_stop, stringResource(Res.string.practice_first_stop_name))
        }
        WeekBarsOf(numbers, today, floorMinutes, metrics, hatch = false, runningMinutes = 0, Modifier.padding(top = StepsTop))
    }
}

/** One step: its number on a plate of the soft accent, and the words with the name of the button bold, in the quotes of the language. */
@Composable
private fun Step(number: Int, words: StringResource, button: String) {
    val colors = MaterialTheme.colorScheme
    val quoted = stringResource(Res.string.practice_first_quoted, button)
    val parts = stringResource(words, NAME_MARK).split(NAME_MARK)
    val text = buildAnnotatedString {
        append(parts.first())
        withStyle(SpanStyle(color = colors.onSurface, fontWeight = FontWeight.Bold)) { append(quoted) }
        parts.drop(1).forEach { append(it) }
    }
    Row(Modifier.semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(StepsGap)) {
        Box(Modifier.size(StepCircle).background(ViolinTheme.accentSoft, CircleShape), contentAlignment = Alignment.Center) {
            Text(
                text = number.toString(),
                color = colors.primary,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, lineHeight = 13.sp, fontWeight = FontWeight.ExtraBold),
            )
        }
        Text(
            text = text,
            modifier = Modifier.weight(1f).padding(top = STEP_TEXT_TOP.dp),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
        )
    }
}

/** The first line of a step stands at the middle of its number: (28 − 20) / 2. */
private const val STEP_TEXT_TOP = 4

/**
 * «Занятие идёт» (spec 3.36.2): the living dot and the words, the chip of the streak with its flame still, the timer large — empty,
 * not «0:00», until the time is read — «Сегодня вместе с ним — …» when the practice began today and something is saved today, the
 * line of the block that runs, and «Вернуться к Live» ([onBackToLive]; none in landscape). The only readers of [timer] are its
 * parts that show the time, so a tick of the practice clock recomposes them and nothing else.
 */
@Composable
internal fun RunningCard(
    timer: () -> PracticeTimer?,
    numbers: ShownNumbers,
    /** Today's saved time as it is, to add the running time to. */
    todaySavedMs: Long,
    /** The practice began today and today already has saved time: «Сегодня вместе с ним». */
    withToday: Boolean,
    metrics: TodayMetrics,
    onBackToLive: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val soft = ViolinTheme.accentSoft
    val ground = colors.surfaceContainer
    val border = colors.primary.copy(alpha = RUNNING_BORDER_ALPHA)
    Column(
        modifier
            .fillMaxWidth()
            .drawWithCache {
                // linear-gradient(160deg, soft, ground 70%): the line of the gradient through the middle, as long as CSS makes it
                val angle = RUNNING_FROM_DEGREES * PI / 180.0
                val direction = Offset(sin(angle).toFloat(), -cos(angle).toFloat())
                val length = abs(size.width * direction.x) + abs(size.height * direction.y)
                val middle = Offset(size.width / 2, size.height / 2)
                val brush = Brush.linearGradient(
                    0f to soft,
                    RUNNING_GROUND_STOP to ground,
                    1f to ground,
                    start = middle - direction * (length / 2),
                    end = middle + direction * (length / 2),
                )
                val corner = CardCorner.toPx()
                val stroke = CardBorder.toPx()
                onDrawBehind {
                    drawRoundRect(brush, cornerRadius = CornerRadius(corner))
                    // the frame lies inside the edge, as an inset shadow does
                    drawRoundRect(
                        color = border,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(corner - stroke / 2),
                        style = Stroke(stroke),
                    )
                }
            }
            .padding(metrics.cardPadding),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(HeadGap),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LiveDot(timer)
                Text(
                    text = stringResource(Res.string.practice_running),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
                )
            }
            // the flame stands still while a practice runs (3.18)
            StreakChip(days = numbers.streakDays, running = true, scope = numbers.scope, shownDays = numbers.rolledStreakDays)
        }
        TimerText(timer, metrics, Modifier.padding(top = metrics.timerTop, bottom = metrics.timerBottom))
        if (withToday) WithTodayLine(timer, todaySavedMs)
        BlockSlot(timer, metrics)
        if (onBackToLive != null) {
            AppButton(
                text = stringResource(Res.string.practice_back_to_live),
                onClick = onBackToLive,
                modifier = Modifier.fillMaxWidth().padding(top = BackToLiveTop),
                style = AppButtonStyle.Soft,
                // the outline of the tab Live: it leads there
                icon = AppIcons.TabLive.normal,
            )
        }
    }
}

@Composable
private fun LiveDot(timer: () -> PracticeTimer?) {
    RunningDot(
        elapsedMs = timer()?.elapsedMs ?: 0L,
        color = MaterialTheme.colorScheme.primary,
        ringColor = ViolinTheme.practiceColors.timerRing,
    )
}

/** The digits do not move (3.16): the time only changes. Until it is read, an empty line of the same height. */
@Composable
private fun TimerText(timer: () -> PracticeTimer?, metrics: TodayMetrics, modifier: Modifier) {
    val time = timer()?.let { Formats.timer(it.elapsedMs) }.orEmpty()
    val description = stringResource(Res.string.practice_timer_description, time)
    Text(
        text = time,
        modifier = modifier.semantics { contentDescription = description },
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        softWrap = false,
        style = MaterialTheme.typography.displayLarge.copy(
            fontSize = metrics.timerSize.sp,
            lineHeight = metrics.timerSize.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.03).em,
            fontFeatureSettings = TABULAR_FIGURES,
        ),
    )
}

@Composable
private fun WithTodayLine(timer: () -> PracticeTimer?, todaySavedMs: Long) {
    val elapsed = timer()?.elapsedMs ?: return
    Text(
        text = stringResource(Res.string.practice_with_running, Formats.minutesInWords(todaySavedMs + elapsed)),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = weekStyle(),
    )
}

@Composable
private fun BlockSlot(timer: () -> PracticeTimer?, metrics: TodayMetrics) {
    val block = timer()?.block ?: return
    BlockLine(block, metrics)
}

/**
 * The block that runs (spec 3.28, 3.36.2): one line under a rule — the note, the name of the element with an ellipsis, and «· ещё
 * 6 мин» or «· готово», which is always seen. TalkBack reads it as one line.
 */
@Composable
private fun BlockLine(block: RunningBlockLine, metrics: TodayMetrics) {
    val colors = MaterialTheme.colorScheme
    val minutesLeft = block.minutesLeft
    val left = if (minutesLeft == null) {
        stringResource(Res.string.block_done)
    } else {
        stringResource(Formats.plural(minutesLeft, Res.string.block_left_one, Res.string.block_left_few, Res.string.block_left_many), minutesLeft)
    }
    val whole = stringResource(Res.string.block_line, block.title, left)
    // the words of the line after the name, as the language joins them: «· ещё 6 мин»
    val tail = stringResource(Res.string.block_line, "", left).trimStart()
    val style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp, fontFeatureSettings = TABULAR_FIGURES)
    Column(Modifier.fillMaxWidth().padding(top = metrics.blockTop)) {
        Box(Modifier.fillMaxWidth().height(BlockRule).background(colors.outlineVariant))
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = metrics.blockRuleGap).clearAndSetSemantics { contentDescription = whole },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppIcon(AppIcons.NoteOne, contentDescription = null, size = IconSizes.InButton, tint = colors.onSurfaceVariant)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(block.title, Modifier.weight(1f, fill = false), color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, style = style)
                Text(tail, color = colors.onSurfaceVariant, maxLines = 1, softWrap = false, style = style)
            }
        }
    }
}

/**
 * The week while a practice runs (spec 3.36.2): «НЕДЕЛЯ», its sum — saved only, it does not tick — and the bars; today's bar
 * hatched with the running minutes when the practice began today, worked out once a minute, without motion. In a card in
 * portrait, [plain] in the left column of landscape.
 */
@Composable
internal fun WeekCard(
    numbers: ShownNumbers,
    today: LocalDate,
    floorMinutes: Int,
    hatch: Boolean,
    timer: () -> PracticeTimer?,
    metrics: TodayMetrics,
    plain: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val currentTimer by rememberUpdatedState(timer)
    // whole minutes rounded up, as saved ones are: the bar is hatched from the first second, and worked out again once a minute
    val runningMinutes by remember { derivedStateOf { TodayWeek.minutesOf(currentTimer()?.elapsedMs ?: 0L) } }
    Column(
        if (plain) {
            modifier.fillMaxWidth().padding(horizontal = PLAIN_SIDE.dp)
        } else {
            modifier.fillMaxWidth().background(colors.surfaceContainer, AppShapes.M).padding(metrics.cardPadding)
        },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(stringResource(Res.string.practice_week), Modifier.weight(1f, fill = false))
            if (numbers.weekMs > 0) {
                Text(
                    text = Formats.minutesInWords(numbers.rolledWeekMs),
                    modifier = Modifier.padding(start = InlineGap),
                    color = colors.onSurface,
                    maxLines = 1,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR_FIGURES),
                )
            }
        }
        WeekBarsOf(numbers, today, floorMinutes, metrics, hatch, if (hatch) runningMinutes else 0, Modifier.padding(top = metrics.barsTop))
    }
}

/** The bars without a card in landscape stand a little in from the edges of the column (practice-extra.html, `.wk-plain`). */
private const val PLAIN_SIDE = 2

@Composable
private fun numberStyle(size: Int): TextStyle = MaterialTheme.typography.displaySmall.copy(
    fontSize = size.sp,
    lineHeight = 1.15.em,
    fontWeight = FontWeight.ExtraBold,
    letterSpacing = (-0.03).em,
    fontFeatureSettings = TABULAR_FIGURES,
)

@Composable
private fun weekStyle(): TextStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = TABULAR_FIGURES)
