package com.violinjourney.app.feature.events

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.events.EventName
import com.violinjourney.app.core.domain.events.EventsConfig
import com.violinjourney.app.core.domain.events.KindRef
import com.violinjourney.app.core.domain.events.KindRules
import com.violinjourney.app.core.domain.events.PerformanceRow
import com.violinjourney.app.core.ui.components.DockMetrics
import com.violinjourney.app.core.ui.components.ScreenHeaderDefaults
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.events.performances.PerformanceCard
import com.violinjourney.app.feature.events.performances.PerformancesIntent
import com.violinjourney.app.feature.events.performances.PerformancesScreen
import com.violinjourney.app.feature.events.performances.PerformancesState
import com.violinjourney.app.shared.resources.Res
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
import com.violinjourney.app.shared.resources.practice_day_events_description
import com.violinjourney.app.shared.resources.practice_day_today
import com.violinjourney.app.shared.resources.practice_pair_description
import com.violinjourney.app.testing.TEST_WINDOW
import com.violinjourney.app.testing.TestWindow
import com.violinjourney.app.testing.assertWholeOnOneLine
import com.violinjourney.app.testing.numbers
import com.violinjourney.app.testing.textLayout
import java.util.Locale
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * «Выступления» (spec 3.36.9, 5.29 R9) where their layout is the risk: the chip of the term and «2 записи» stand whole at the font 1.3 —
 * in German too — and the long name gives way to them with an ellipsis; a row is one button that says it all, in the order it is seen;
 * «Впереди» and «Прошли» are headings; lying the column is 560 in the middle and the bottom zone as wide as it, its button 48 in a window
 * of 640 × 360; nothing yet — the words of the empty screen and the same button, smaller lying. Laid out in a window of its own size
 * ([TestWindow]); the words read in the composition, in the language the screen speaks.
 */
@RunWith(AndroidJUnit4::class)
class PerformancesScreenTest {
    @get:Rule
    val compose = createComposeRule()

    /** The language of the device, given back after every test. */
    private val deviceLanguage: Locale = Locale.getDefault()

    @After
    fun backToTheLanguageOfTheDevice() = Locale.setDefault(deviceLanguage)

    private val intents = mutableListOf<PerformancesIntent>()
    private val words = mutableMapOf<String, String>()
    private val look = KindRules.lookOf(KindRef.BuiltIn(BuiltInKind.PERFORMANCE), emptyList(), EventsConfig())

    private fun word(key: String): String = words.getValue(key)

    /** The autumn concert of the mockups: 24 October at 18:30 in the small hall, 27 days ahead, its programme of two. */
    private fun concert(title: String = CONCERT) = PerformanceRow(
        eventId = 1, date = LocalDate(2026, 10, 24), otherYear = false, name = EventName.Titled(title), place = HALL,
        startMinutes = 18 * 60 + 30, days = DAYS, program = listOf("А. Вивальди", "П. Чайковский"), records = 0, lastVideo = null,
    )

    /** The exam of May: over, two recordings without a video. */
    private val exam = PerformanceRow(
        eventId = 2, date = LocalDate(2026, 5, 18), otherYear = false, name = EventName.Titled(EXAM), place = "Концертный зал",
        startMinutes = 11 * 60, days = null, program = listOf("Зейтц"), records = RECORDS, lastVideo = null,
    )

    private fun state(ahead: List<PerformanceRow> = listOf(concert()), past: List<PerformanceRow> = listOf(exam)) = PerformancesState(
        loading = false, ahead = ahead.map { PerformanceCard(it, thumbPath = null) }, past = past.map { PerformanceCard(it, thumbPath = null) },
        look = look,
    )

    /** The screen is there: false — not yet, as «Записи» stand before their row «Выступления» opens it. */
    private var opened by mutableStateOf(true)

    private fun show(state: PerformancesState, size: DpSize, fontScale: Float = 1f, told: DpSize = size) {
        compose.setContent {
            words[CHIP] = stringResource(Formats.plural(DAYS, Res.string.performances_in_days_one, Res.string.performances_in_days_few, Res.string.performances_in_days_many), DAYS)
            words[TODAY_CHIP] = stringResource(Res.string.practice_day_today)
            words[COUNT] = stringResource(Formats.plural(RECORDS, Res.string.history_count_one, Res.string.history_count_few, Res.string.history_count_many), RECORDS)
            words[ADD] = stringResource(Res.string.performances_add)
            words[AHEAD] = stringResource(Res.string.performances_ahead)
            words[PAST] = stringResource(Res.string.performances_past)
            words[EMPTY_TITLE] = stringResource(Res.string.performances_empty_title)
            words[EMPTY_TEXT] = stringResource(Res.string.performances_empty_text)
            // «Осенний концерт, 24 октября, через 27 дней; Малый зал музыкальной школы, 18:30; программа: А. Вивальди, П. Чайковский»
            @Composable
            fun pair(a: String, b: String) = stringResource(Res.string.practice_pair_description, a, b)
            val term = stringResource(
                Formats.plural(DAYS, Res.string.performances_row_in_days_one, Res.string.performances_row_in_days_few, Res.string.performances_row_in_days_many),
                DAYS,
            )
            val head = pair(pair(CONCERT, Formats.recordDate(LocalDate(2026, 10, 24), withYear = false)), term)
            val program = stringResource(Res.string.performances_program_said, pair("А. Вивальди", "П. Чайковский"))
            words[SAID] = stringResource(
                Res.string.practice_day_events_description,
                stringResource(Res.string.practice_day_events_description, head, pair(HALL, Formats.clockOf(18 * 60 + 30))),
                program,
            )
            ViolinTheme { TestWindow(size, told = told, fontScale = fontScale) { if (opened) PerformancesScreen(state, onIntent = { intents += it }) } }
        }
        compose.waitForIdle()
    }

    private fun window() = compose.onNodeWithTag(TEST_WINDOW).getUnclippedBoundsInRoot()

    private fun addButton(): SemanticsNodeInteraction = compose.onNode(hasText(word(ADD)) and hasClickAction())

    private fun rowOf(name: String): SemanticsNodeInteraction = compose.onNode(
        SemanticsMatcher("the row of «$name»") { node -> node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.startsWith(name) } } and
            hasClickAction(),
    )

    /** Spec 3.36.9: on 360 at 1.3 the chip of the term and «2 записи» are whole; the long name gives way to them with an ellipsis. */
    @Test
    fun theChipOfTheTermAndTheCountStandWholeAtALargeFont() = assertTheChipAndTheCountWhole(language = null)

    /** The same in German: «in 27 Tagen» and «2 Aufnahmen». */
    @Test
    fun theChipOfTheTermAndTheCountStandWholeInGerman() = assertTheChipAndTheCountWhole(language = "de")

    private fun assertTheChipAndTheCountWhole(language: String?) {
        language?.let { Locale.setDefault(Locale.forLanguageTag(it)) }
        show(state(ahead = listOf(concert(LONG_NAME)), past = listOf(exam)), DpSize(360.dp, 640.dp), fontScale = 1.3f)
        assertWholeOnOneLine(compose.onNodeWithText(word(CHIP), useUnmergedTree = true), word(CHIP))
        assertWholeOnOneLine(compose.onNodeWithText(word(COUNT), useUnmergedTree = true), word(COUNT))
        val name = compose.onNodeWithText(LONG_NAME, useUnmergedTree = true)
        val layout = name.textLayout()
        assertTrue("the name gives way with an ellipsis — ${layout.numbers(name)}", layout.lineCount == 1 && layout.isLineEllipsized(0))
        val chip = compose.onNodeWithText(word(CHIP), useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("the chip stands after the name, in the window: $chip", chip.right <= window().right)
    }

    /**
     * Spec 3.36.9 («чип срока и «2 записи» не режутся»), review of stage 99: in French on 360 at 1.3 a concert of today with two recordings
     * cannot hold «aujourd'hui», «2 enregistrements» and its name on one line (the chip had 75 dp left, its word cut, the name none) —
     * «2 enregistrements» goes under the place, whole; the chip stands whole after the name, and the name keeps its room (three ems of its
     * 16 sp at 1.3 at the least), ending in an ellipsis.
     */
    @Test
    fun inFrenchAtALargeFontTheCountGoesUnderAndTheChipOfTodayStandsWhole() {
        Locale.setDefault(Locale.forLanguageTag("fr"))
        val tonight = concert().copy(days = 0, records = RECORDS)
        show(state(ahead = listOf(tonight), past = emptyList()), DpSize(360.dp, 640.dp), fontScale = 1.3f)
        assertWholeOnOneLine(compose.onNodeWithText(word(TODAY_CHIP), useUnmergedTree = true), word(TODAY_CHIP))
        assertWholeOnOneLine(compose.onNodeWithText(word(COUNT), useUnmergedTree = true), word(COUNT))
        val name = compose.onNodeWithText(CONCERT, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("the name keeps three ems of 16 sp at 1.3: $name", name.right - name.left >= NAME_LEAST_AT_LARGE_FONT)
        val chip = compose.onNodeWithText(word(TODAY_CHIP), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val row = rowOf(CONCERT).getUnclippedBoundsInRoot()
        assertTrue("the chip after the name, in the row: $chip, the name $name, the row $row", chip.left >= name.right && chip.right <= row.right)
        val place = compose.onNodeWithText(HALL, substring = true, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val count = compose.onNodeWithText(word(COUNT), useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("«${word(COUNT)}» under the place and the programme: $count, the place $place", count.top >= place.bottom)
        assertEquals("at the start of the lines of the middle", place.left.value, count.left.value, HALF_DP)
    }

    /**
     * Where there is room — upright on 412 at the font 1.0 — «2 записи» keeps the end of its row (spec 3.36.9: «справа»): 12 from its end,
     * in the middle of its height.
     */
    @Test
    fun uprightTheCountStandsAtTheEndOfItsRow() {
        show(state(), DpSize(412.dp, 892.dp))
        val row = rowOf(EXAM).getUnclippedBoundsInRoot()
        val count = compose.onNodeWithText(word(COUNT), useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals("12 from the end of the row", (row.right - ROW_PADDING).value, count.right.value, HALF_DP)
        assertEquals("in the middle of its height", ((row.top + row.bottom) / 2).value, ((count.top + count.bottom) / 2).value, 1f)
    }

    /**
     * The screen comes in under the finger that opened it (5.29 R9, review of stage 99): its first row stands where the row «Выступления»
     * of «Записи» stood, and the second tap of a double tap, a frame or two after the screen came, opens no event; once the time of a
     * double tap has passed, the row answers.
     */
    @Test
    fun theSecondTapOfTheFingerThatOpenedTheScreenOpensNoEvent() {
        opened = false
        show(state(), DpSize(412.dp, 892.dp))
        compose.mainClock.autoAdvance = false
        opened = true
        repeat(TAP_FRAMES) { compose.mainClock.advanceTimeByFrame() }
        rowOf(CONCERT).performTouchInput { click() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals("the second tap opened nothing", emptyList<PerformancesIntent>(), intents)
        rowOf(CONCERT).performClick()
        assertEquals(listOf<PerformancesIntent>(PerformancesIntent.RowClicked(1)), intents)
    }

    /**
     * Spec 3.36.9, «TalkBack»: a row is one button and one sentence — the name, the date and the term in words, then where and when,
     * then the programme; its words are not heard again. «Впереди» and «Прошли» are headings. A tap opens its event.
     */
    @Test
    fun aRowIsOneButtonThatSaysItAllAndOpensItsEvent() {
        show(state(), DpSize(412.dp, 892.dp))
        val said = rowOf(CONCERT).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
        assertEquals(word(SAID), said)
        compose.onNode(hasText(word(AHEAD).uppercase()) and isHeading()).assertExists()
        compose.onNode(hasText(word(PAST).uppercase()) and isHeading()).assertExists()
        // the words of a row are seen, not read: no node of the merged tree carries them
        compose.onAllNodes(hasText(HALL, substring = true)).assertCountEquals(0)
        rowOf(CONCERT).performClick()
        assertEquals(listOf<PerformancesIntent>(PerformancesIntent.RowClicked(1)), intents)
    }

    /** Spec 3.36.9: upright — rows of at least 80, the button 56 at the bottom. */
    @Test
    fun uprightARowIsAtLeast80AndTheButton56() {
        show(state(), DpSize(412.dp, 892.dp))
        val row = rowOf(CONCERT).getUnclippedBoundsInRoot()
        assertTrue("at least 80: $row", row.bottom - row.top >= 80.dp)
        assertEquals("the button", 56f, addButton().getUnclippedBoundsInRoot().let { it.bottom - it.top }.value, HALF_DP)
        addButton().performClick()
        assertEquals(listOf<PerformancesIntent>(PerformancesIntent.AddClicked), intents)
    }

    /** Spec 3.36.1 rule 3, 3.36.9: lying one column of 560 in the middle, the zone as wide; rows of at least 72. */
    @Test
    fun lyingIn892x412TheColumnIs560InTheMiddleAndTheZoneAsWide() = assertLying(DpSize(892.dp, 412.dp), button = 56.dp)

    /** The same in 640 × 360: the window no higher than 360 — the button of the zone is 48 (3.36.1 rule 5). */
    @Test
    fun lyingIn640x360TheButtonIs48() = assertLying(DpSize(640.dp, 360.dp), button = 48.dp)

    private fun assertLying(size: DpSize, button: Dp) {
        show(state(), size)
        val window = window()
        val column = minOf(COLUMN, size.width)
        val start = (size.width - column) / 2
        val zone = addButton().getUnclippedBoundsInRoot()
        assertEquals("the zone starts 16 into the column", (start + SIDE).value, (zone.left - window.left).value, HALF_DP)
        assertEquals("and ends 16 before its end", (start + column - SIDE).value, (zone.right - window.left).value, HALF_DP)
        assertEquals("its button", button.value, (zone.bottom - zone.top).value, HALF_DP)
        val row = rowOf(CONCERT).getUnclippedBoundsInRoot()
        assertEquals("the rows in the same column", (start + SIDE).value, (row.left - window.left).value, HALF_DP)
        assertEquals("as wide as the zone", (zone.right - zone.left).value, (row.right - row.left).value, HALF_DP)
        assertTrue("at least 72 lying: $row", row.bottom - row.top >= 72.dp)
        // the window of the test is wider than the phone held upright: the row is pressed by its action, not by a touch
        rowOf(CONCERT).performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf<PerformancesIntent>(PerformancesIntent.RowClicked(1)), intents)
    }

    /**
     * Spec 3.36.9, «Пусто», 5.29 R9: the plate of the sign 88, «Здесь будут ваши выступления» 22 sp 18 under it, the words 10 under that —
     * the block in the middle of what the zone leaves under the bar; the same button.
     */
    @Test
    fun anEmptyScreenSaysWhatItKeepsUpright() =
        assertEmpty(DpSize(412.dp, 892.dp), titleSp = 22f, plate = 88.dp, plateToTitle = 18.dp)

    /**
     * The same lying, smaller — the plate 64, the title 20 sp 12 under it — in the room a phone of 640 × 360 leaves under its bars (308,
     * the window told 640 × 360: the bar 48, the zone of a window no higher than 360), the block in the middle of what is left above the
     * zone and over it.
     */
    @Test
    fun anEmptyScreenIsSmallerLying() =
        assertEmpty(DpSize(640.dp, 308.dp), titleSp = 20f, plate = 64.dp, plateToTitle = 12.dp, told = DpSize(640.dp, 360.dp))

    private fun assertEmpty(size: DpSize, titleSp: Float, plate: Dp, plateToTitle: Dp, told: DpSize = size) {
        show(state(ahead = emptyList(), past = emptyList()), size, told = told)
        val window = window()
        val title = compose.onNodeWithText(word(EMPTY_TITLE))
        assertEquals("the title", titleSp.sp, title.textLayout().layoutInput.style.fontSize)
        val top = title.getUnclippedBoundsInRoot().top
        val text = compose.onNodeWithText(word(EMPTY_TEXT)).getUnclippedBoundsInRoot()
        val lying = told.width > told.height
        val bar = window.top + if (lying) ScreenHeaderDefaults.HeightLying else ScreenHeaderDefaults.Height
        val zoneTop = addButton().getUnclippedBoundsInRoot().top - DockMetrics.of(told.height).top
        // the block: the plate, the gap to the title, the title, the words — its top read back from the title by the sizes of the spec
        val blockTop = top - plateToTitle - plate
        assertEquals(
            "the block in the middle between the bar ($bar) and the zone ($zoneTop): $blockTop … ${text.bottom}",
            (blockTop - bar).value, (zoneTop - text.bottom).value, 1f,
        )
        assertTrue("the words over the zone: $text, the zone $zoneTop", text.bottom <= zoneTop)
        compose.onAllNodes(hasText(word(AHEAD).uppercase())).assertCountEquals(0)
    }

    private companion object {
        const val CONCERT = "Осенний концерт"
        const val EXAM = "Экзамен, 4 класс"
        const val HALL = "Малый зал музыкальной школы"
        const val LONG_NAME = "Отборочный тур Международного конкурса юных скрипачей имени Л. Когана"
        const val DAYS = 27
        const val RECORDS = 2
        val COLUMN = 560.dp
        val SIDE = 16.dp
        const val HALF_DP = 0.5f
        const val CHIP = "chip"
        const val COUNT = "count"
        const val ADD = "add"
        const val AHEAD = "ahead"
        const val PAST = "past"
        const val EMPTY_TITLE = "emptyTitle"
        const val EMPTY_TEXT = "emptyText"
        const val SAID = "said"
        const val TODAY_CHIP = "todayChip"

        /** The row's fields: 12 (5.29 R9). */
        val ROW_PADDING = 12.dp

        /** Three ems of the name of 16 sp at the font 1.3 — the least it keeps beside the chip (review of stage 99). */
        val NAME_LEAST_AT_LARGE_FONT = (3 * 16 * 1.3f).dp

        /** A double tap: the second tap a frame or two after the screen came. */
        const val TAP_FRAMES = 2
    }
}
