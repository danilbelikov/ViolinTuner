package com.violinjourney.app.feature.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.domain.practice.ForgottenEndings
import com.violinjourney.app.core.domain.practice.ForgottenPractice
import com.violinjourney.app.core.domain.practice.PieceBlock
import com.violinjourney.app.core.domain.practice.PracticeBlocks
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_HOUR
import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_MINUTE
import com.violinjourney.app.core.domain.practice.RunningPractice
import com.violinjourney.app.core.ui.components.AppSheetCard
import com.violinjourney.app.core.ui.components.AppSheetDefaults
import com.violinjourney.app.core.ui.components.Stepper
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.practice.components.EditTimeButtons
import com.violinjourney.app.feature.practice.components.EditTimeSheetContent
import com.violinjourney.app.feature.practice.components.ForgottenButtons
import com.violinjourney.app.feature.practice.components.ForgottenSheetContent
import com.violinjourney.app.feature.practice.components.SummaryButtons
import com.violinjourney.app.feature.practice.components.SummarySheetContent
import kotlin.math.absoluteValue
import kotlin.math.sign
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant

/**
 * The sheets of time of R3 (spec 3.36.3; practice-sheets.html 1, 3, 4) on the data of the mockups: Sunday 27 September 2026; «Закончить
 * занятие» from 17:55 to 18:42 — «D-dur · 2 октавы» 15 of 15, «Кайзер № 3» 10 of 10, «Концерт ля минор, 1 ч.» from 18:35, 7 of 15;
 * «Занятие не закончено» running 3 h 12 min; «Время за день» of Wednesday 2 September, 1 h 15 min.
 */
private object SheetSample {
    val zone: TimeZone = TimeZone.of("Europe/Moscow")
    val config = PracticeConfig()
    private val day = LocalDate(2026, 9, 27)

    fun at(hour: Int, minute: Int, second: Int = 0): Long = day.atTime(hour, minute, second).toInstant(zone).toEpochMilliseconds()

    private val start = at(17, 55)
    private fun minutes(value: Int): Long = value * MS_PER_MINUTE

    private val titles = mapOf(
        1L to "D-dur · 2 октавы", 2L to "Кайзер № 3", 3L to "Концерт ля минор, 1 ч.",
        4L to "Менуэт соль мажор", 5L to "Этюд № 2", 6L to "Штрих деташе",
    )

    /** The three blocks of the mockup; the concerto is on the bookmark. */
    val blocks = PracticeBlocks(
        practiceStartedAtEpochMs = start,
        current = PieceBlock(3, start + minutes(40), minutes(15)),
        finished = listOf(
            PieceBlock(1, start, minutes(15), endedAtEpochMs = start + minutes(15)),
            PieceBlock(2, start + minutes(15), minutes(10), endedAtEpochMs = start + minutes(25)),
        ),
    )

    /** Six blocks of seven minutes: «Что играли» scrolls inside itself after four. */
    val sixBlocks = PracticeBlocks(
        practiceStartedAtEpochMs = start,
        current = null,
        finished = (0 until 6).map { i -> PieceBlock(i + 1L, start + minutes(7 * i), minutes(10), endedAtEpochMs = start + minutes(7 * i + 7)) },
    )

    /** «Закончить занятие» of [actualMinutes], the stepper moved by [steps]. */
    fun summary(actualMinutes: Int = 47, steps: Int = 0, blocks: PracticeBlocks? = this.blocks): PracticeSheet.Summary {
        var sheet = PracticeReducer.summarySheet(start, minutes(actualMinutes), config, blocks, titles)
        repeat(steps.absoluteValue) { sheet = PracticeReducer.step(sheet, steps.sign, config) }
        return sheet
    }

    /** «Занятие не закончено» of a practice begun at [startedAt], its last mark at [markAt], asked at [now]. */
    fun forgotten(startedAt: Long, markAt: Long?, answered: Boolean = false, now: Long): Pair<PracticePrompt.Forgotten, () -> ForgottenEndings?> {
        val practice = RunningPractice(startedAt, markAt, lastMarkByAnswer = answered)
        val endings = ForgottenPractice.endings(practice, now, config)
        return PracticePrompt.Forgotten(practice) to { endings }
    }

    private val wednesday = LocalDate(2026, 9, 2)

    /** «Время за день» of a day whose time is [totalMs], moved by [steps] of the stepper and [added] minutes of the chips. */
    fun edit(totalMs: Long, steps: Int = 0, added: Int = 0): PracticeSheet.EditTime {
        var sheet = PracticeReducer.editSheet(wednesday, totalMs, config)
        repeat(steps.absoluteValue) { sheet = PracticeReducer.step(sheet, steps.sign, config) }
        return if (added == 0) sheet else PracticeReducer.add(sheet, added)
    }

    const val STEP = 5
    const val HOUR = MS_PER_HOUR
    const val MINUTE = MS_PER_MINUTE
}

/** The status bar of a phone over the screen of the preview: the sheet never rises above it and 16 more (5.29 R3). */
private val StatusBar = 24.dp

/**
 * A sheet as it lies on a screen of the preview's size: under the scrim, at the bottom, never higher than the window allows. Shared
 * with the sheets of progress (`ProgressSheetPreviews.kt`).
 */
@Composable
internal fun OnScreen(sheet: @Composable (Modifier) -> Unit) = ViolinTheme {
    BoxWithConstraints(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).background(ViolinTheme.sheetScrim),
        contentAlignment = Alignment.BottomCenter,
    ) {
        sheet(Modifier.heightIn(max = maxHeight - StatusBar - AppSheetDefaults.TopClearance))
    }
}

@Composable
private fun SummaryPreview(sheet: PracticeSheet.Summary) = OnScreen { modifier ->
    AppSheetCard(
        modifier = modifier,
        bottom = { SummaryButtons(sheet, onSave = {}, onDiscard = {}) },
    ) {
        SummarySheetContent(sheet, SheetSample.STEP, SheetSample.zone, onStep = {})
    }
}

@Preview(name = "Закончить · как есть: 47 мин, 17:55 — 18:42, «Что играли» с латунной ✓", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun SummaryAsIsPreview() = SummaryPreview(SheetSample.summary())

@Preview(name = "Закончить · после степпера: 37 мин · было 47, «не вошёл», «Сохранить 37 мин»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun SummaryTrimmedPreview() = SummaryPreview(SheetSample.summary(steps = -2))

@Preview(name = "Закончить · без подходов", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun SummaryNoBlocksPreview() = SummaryPreview(SheetSample.summary(blocks = null))

@Preview(name = "Закончить · 4 мин: оба «−» и «+» приглушены, «Короче 5 минут…»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun SummaryTooShortPreview() = SummaryPreview(SheetSample.summary(actualMinutes = 4, blocks = null))

@Preview(name = "Закончить · шесть подходов: прокрутка внутри «Что играли»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun SummarySixBlocksPreview() = SummaryPreview(SheetSample.summary(blocks = SheetSample.sixBlocks))

@Preview(name = "Закончить · de, 360, шрифт 1,3", locale = "de", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun SummaryGermanPreview() = SummaryPreview(SheetSample.summary(steps = -2))

@Preview(name = "Закончить · fr, 360", locale = "fr", device = "spec:width=360dp,height=640dp")
@Composable
private fun SummaryFrenchPreview() = SummaryPreview(SheetSample.summary(steps = -2))

@Preview(name = "Закончить · 360, шрифт 1,3: «1 ч 35 мин» целиком, строка числа той же высоты", locale = "ru", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun SummaryLongLargePreview() = SummaryPreview(SheetSample.summary(actualMinutes = 95))

/**
 * The widest numbers of the stepper at 360 and the font of 1.3: German «1 Std. 35 Min.» and «11 Std. 55 Min.» — smaller down to 22 dp
 * but whole, each on a line of the height of «47 Min.». Written out: the formats of a preview speak Russian, whatever its locale.
 */
@Preview(name = "Степпер · de, 360, шрифт 1,3: «1 Std. 35 Min.» и «11 Std. 55 Min.» целиком", locale = "de", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun StepperGermanPreview() = OnScreen { modifier ->
    AppSheetCard(modifier) {
        listOf("47 Min.", "1 Std. 35 Min.", "11 Std. 55 Min.").forEach { value ->
            Stepper(
                value = value,
                onStep = {},
                canStepDown = true,
                canStepUp = true,
                downDescription = "",
                upDescription = "",
                caption = "17:55 — 18:42",
            )
        }
    }
}

@Preview(name = "Закончить · landscape 892 × 412: 640 по центру, кнопки у низа, затухание над ними", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun SummaryLandscapePreview() = SummaryPreview(SheetSample.summary())

@Composable
private fun ForgottenPreview(start: Long, mark: Long?, answered: Boolean = false, now: Long) {
    val (prompt, endings) = SheetSample.forgotten(start, mark, answered, now)
    OnScreen { modifier ->
        AppSheetCard(
            modifier = modifier,
            bottom = { ForgottenButtons(prompt, endings, onIntent = {}, zone = SheetSample.zone) },
        ) {
            ForgottenSheetContent(prompt, endings, SheetSample.zone)
        }
    }
}

@Preview(name = "Не закончено · звучала: «Идёт 3 ч 12 мин», «Закончить в 18:42 · 1 ч 4 мин»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun ForgottenSoundedPreview() = ForgottenPreview(SheetSample.at(17, 38), SheetSample.at(18, 42), now = SheetSample.at(20, 50))

@Preview(name = "Не закончено · без звука: «Указать, сколько играли» первым", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun ForgottenSilentPreview() = ForgottenPreview(SheetSample.at(17, 38), null, now = SheetSample.at(20, 50))

@Preview(name = "Не закончено · после ответа: «В 19:05 вы ответили…», «Закончить в 19:05 · 1 ч 10 мин»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun ForgottenAnsweredPreview() = ForgottenPreview(SheetSample.at(17, 55), SheetSample.at(19, 5), answered = true, now = SheetSample.at(21, 7))

@Preview(name = "Не закончено · отметка меньше минуты: «Закончить в 17:38» без числа", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun ForgottenShortMarkPreview() = ForgottenPreview(SheetSample.at(17, 38), SheetSample.at(17, 38, 40), now = SheetSample.at(20, 50))

@Preview(name = "Не закончено · de, 360, шрифт 1,3", locale = "de", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun ForgottenGermanPreview() = ForgottenPreview(SheetSample.at(17, 38), SheetSample.at(18, 42), now = SheetSample.at(20, 50))

@Preview(name = "Не закончено · 640 × 360: кнопки у низа, текст прокручивается над ними", locale = "ru", device = "spec:width=640dp,height=360dp")
@Composable
private fun ForgottenLowPreview() = ForgottenPreview(SheetSample.at(17, 38), SheetSample.at(18, 42), now = SheetSample.at(20, 50))

@Composable
private fun EditTimePreview(sheet: PracticeSheet.EditTime) = OnScreen { modifier ->
    AppSheetCard(
        modifier = modifier,
        bottom = { EditTimeButtons(sheet, onIntent = {}) },
    ) {
        EditTimeSheetContent(sheet, SheetSample.STEP, onIntent = {})
    }
}

@Preview(name = "Время за день · сдвинули: 1 ч 45 мин, «было 1 ч 15 мин»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun EditTimeMovedPreview() = EditTimePreview(SheetSample.edit(75 * SheetSample.MINUTE, added = 30))

@Preview(name = "Время за день · не трогали: подписи нет, место её держится", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun EditTimeUnmovedPreview() = EditTimePreview(SheetSample.edit(75 * SheetSample.MINUTE))

@Preview(name = "Время за день · больше 12 ч: открылся на 12 ч, сдвинули — «было 13 ч 10 мин»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun EditTimeOverTwelvePreview() = EditTimePreview(SheetSample.edit(13 * SheetSample.HOUR + 10 * SheetSample.MINUTE, steps = -1))

@Preview(name = "Время за день · пустой день на нуле: «Добавить» приглушена с причиной", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun EditTimeEmptyPreview() = EditTimePreview(SheetSample.edit(0))

@Preview(name = "Время за день · пустой день: «Добавить 30 мин», «занятие без телефона»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun EditTimeEmptyThirtyPreview() = EditTimePreview(SheetSample.edit(0, added = 30))

@Preview(name = "Время за день · de, 360", locale = "de", device = "spec:width=360dp,height=640dp")
@Composable
private fun EditTimeGermanPreview() = EditTimePreview(SheetSample.edit(75 * SheetSample.MINUTE, added = 30))

@Preview(name = "Время за день · 360, шрифт 1,3: «11 ч 55 мин» целиком", locale = "ru", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun EditTimeLongLargePreview() = EditTimePreview(SheetSample.edit(11 * SheetSample.HOUR + 55 * SheetSample.MINUTE))

@Preview(name = "Время за день · landscape 892 × 412", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun EditTimeLandscapePreview() = EditTimePreview(SheetSample.edit(75 * SheetSample.MINUTE, added = 30))
