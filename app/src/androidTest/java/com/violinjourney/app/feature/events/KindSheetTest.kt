package com.violinjourney.app.feature.events

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.events.EventsConfig
import com.violinjourney.app.core.domain.events.KindNameProblem
import com.violinjourney.app.core.domain.events.KindRef
import com.violinjourney.app.core.domain.events.KindRules
import com.violinjourney.app.core.domain.events.KindSign
import com.violinjourney.app.core.domain.events.Repeat
import com.violinjourney.app.core.domain.events.StoredKind
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.events.form.EventFormIntent
import com.violinjourney.app.feature.events.form.EventFormReducer
import com.violinjourney.app.feature.events.form.EventFormSheetCard
import com.violinjourney.app.feature.events.form.FormDraft
import com.violinjourney.app.feature.events.form.FormSheet
import com.violinjourney.app.feature.events.form.KindDraft
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.event_color_blue
import com.violinjourney.app.shared.resources.event_color_ice
import com.violinjourney.app.shared.resources.event_color_lime
import com.violinjourney.app.shared.resources.event_color_orchid
import com.violinjourney.app.shared.resources.event_color_powder
import com.violinjourney.app.shared.resources.event_color_rose
import com.violinjourney.app.shared.resources.event_color_sea
import com.violinjourney.app.shared.resources.event_color_turquoise
import com.violinjourney.app.shared.resources.event_kind_color_line
import com.violinjourney.app.shared.resources.event_kind_delete
import com.violinjourney.app.shared.resources.event_kind_name_needed
import com.violinjourney.app.shared.resources.event_kind_name_taken
import com.violinjourney.app.shared.resources.event_kind_own
import com.violinjourney.app.shared.resources.event_sign_arc
import com.violinjourney.app.shared.resources.event_sign_bolt
import com.violinjourney.app.shared.resources.event_sign_book
import com.violinjourney.app.shared.resources.event_sign_bowtie
import com.violinjourney.app.shared.resources.event_sign_chat
import com.violinjourney.app.shared.resources.event_sign_hat
import com.violinjourney.app.shared.resources.event_sign_heart
import com.violinjourney.app.shared.resources.event_sign_keys
import com.violinjourney.app.shared.resources.event_sign_leaf
import com.violinjourney.app.shared.resources.event_sign_mask
import com.violinjourney.app.shared.resources.event_sign_moon
import com.violinjourney.app.shared.resources.event_sign_ticket
import com.violinjourney.app.shared.resources.profile_done
import com.violinjourney.app.testing.TestWindow
import com.violinjourney.app.testing.assertWordsWhole
import kotlin.math.roundToInt
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The sheet «Вид» of the form of an event (spec 3.36.9, 5.29 R9): the twelve signs of one's own — six in a row 8 apart in a sheet on
 * 412 (a row of 372), six 4 apart on 360 (320: cells of 50), four in three rows 8 apart on 320 (280: cells of 64) — each a radio button
 * of at least 48 × 48 that says its name; while the name cannot be saved, «Готово» sleeps under its reason, one line over it — «Нужно
 * имя», «Такой вид уже есть — „Урок“» — and wakes with a name. Laid out in a window of its own size ([TestWindow]); the words read in the
 * composition.
 */
@RunWith(AndroidJUnit4::class)
class KindSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val config = EventsConfig()
    private val kinds = KindRules.all(emptyList(), config)
    private val today = LocalDate(2026, 9, 27)
    private val intents = mutableListOf<EventFormIntent>()
    private val signs = mutableMapOf<KindSign, String>()

    /** The names of the eight colours, in their order. */
    private val colors = mutableListOf<String>()
    private val words = mutableMapOf<String, String>()

    private fun word(key: String): String = words.getValue(key)

    private val draft = FormDraft(KindRef.BuiltIn(BuiltInKind.LESSON), LocalDate(2026, 9, 28), 17 * 60, 45, Repeat.NONE, null, "", "", "")

    /** A new kind of one's own: the first colour and the first sign no kind has; [name] typed, [problem] why it cannot be saved. */
    private fun sheet(name: String = "", problem: KindNameProblem? = KindNameProblem.Empty): FormSheet.Kind {
        val new = EventFormReducer.newKindDraft(kinds, config)
        return FormSheet.Kind(KindDraft(ref = null, name = name, color = new.color, sign = new.sign), problem)
    }

    private fun show(sheet: FormSheet.Kind, width: Dp) {
        compose.setContent {
            KindSign.OWN.forEach { signs[it] = stringResource(signName(it)) }
            words[DONE] = stringResource(Res.string.profile_done)
            words[NEEDED] = stringResource(Res.string.event_kind_name_needed)
            words[TAKEN] = stringResource(Res.string.event_kind_name_taken, TAKEN_NAME)
            colors.clear()
            colors += COLORS.map { stringResource(it) }
            val state = EventFormReducer.stateOf(
                draft = draft, sheet = sheet, dialog = null, loading = false, isNew = true, edited = null, editedSeries = null,
                initialDate = draft.date, events = emptyList(), kinds = kinds, today = today, focusNotes = false, config = config,
            )
            ViolinTheme { TestWindow(DpSize(width, 892.dp)) { EventFormSheetCard(state, onIntent = { intents += it }) } }
        }
        compose.waitForIdle()
    }

    /** The name of a sign of one's own, as the sheet says it. */
    private fun signName(sign: KindSign): StringResource = when (sign) {
        KindSign.BOOK -> Res.string.event_sign_book
        KindSign.HAT -> Res.string.event_sign_hat
        KindSign.KEYS -> Res.string.event_sign_keys
        KindSign.MASK -> Res.string.event_sign_mask
        KindSign.TICKET -> Res.string.event_sign_ticket
        KindSign.CHAT -> Res.string.event_sign_chat
        KindSign.HEART -> Res.string.event_sign_heart
        KindSign.MOON -> Res.string.event_sign_moon
        KindSign.LEAF -> Res.string.event_sign_leaf
        KindSign.BOLT -> Res.string.event_sign_bolt
        KindSign.BOWTIE -> Res.string.event_sign_bowtie
        KindSign.ARC -> Res.string.event_sign_arc
        else -> error("$sign is a sign of a built-in kind")
    }

    private fun cellOf(sign: KindSign) = compose.onNode(hasContentDescription(signs.getValue(sign)) and isSelectable())

    /** The twelve cells in a sheet on [width]: [columns] in a row, [gap] between neighbours, each [cell] wide and 48 high. */
    private fun assertTheSigns(width: Dp, columns: Int, gap: Dp, cell: Dp) {
        show(sheet(), width)
        val cells: List<DpRect> = KindSign.OWN.map { cellOf(it).getUnclippedBoundsInRoot() }
        val rows = cells.groupBy { it.top.value.roundToInt() }.toSortedMap().values.map { row -> row.sortedBy { it.left } }
        assertEquals("on $width: rows of $columns", (KindSign.OWN.size + columns - 1) / columns, rows.size)
        rows.forEach { row -> assertEquals("on $width: $columns in a row", columns, row.size) }
        cells.forEach { box ->
            assertEquals("on $width: a cell $cell wide", cell.value, box.width.value, 0.6f)
            assertTrue("on $width: a target of 48: ${box.width} × ${box.height}", box.width >= 48.dp - 0.5.dp && box.height >= 48.dp - 0.5.dp)
        }
        val first = rows.first()
        assertEquals("on $width: $gap apart", gap.value, (first[1].left - first[0].right).value, 0.6f)
        assertEquals("on $width: the rows $gap apart", gap.value, (rows[1].first().top - first.first().bottom).value, 0.6f)
    }

    @Test
    fun onAPhoneOf412TheSignsStandSixInARow8Apart() = assertTheSigns(412.dp, columns = 6, gap = 8.dp, cell = (372.dp - 8.dp * 5) / 6)

    @Test
    fun onAPhoneOf360TheSignsStandSixInARow4Apart() = assertTheSigns(360.dp, columns = 6, gap = 4.dp, cell = 50.dp)

    @Test
    fun onAPhoneOf320TheSignsStandFourInARowInThreeRows() = assertTheSigns(320.dp, columns = 4, gap = 8.dp, cell = 64.dp)

    @Test
    fun aSignIsARadioButtonThatSaysItsNameAndAPressChoosesIt() {
        show(sheet(), 412.dp)
        val chosen = sheet().draft.sign
        cellOf(chosen).assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        val other = KindSign.OWN.last { it != chosen }
        cellOf(other).assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false)).performClick()
        compose.runOnIdle { assertEquals(EventFormIntent.KindSignPicked(other), intents.last()) }
    }

    /** Without a name «Готово» sleeps, «Нужно имя» one line over it; a press does nothing. */
    @Test
    fun withoutANameDoneSleepsUnderItsReason() {
        show(sheet(), 360.dp)
        assertTheReasonOverDone(word(NEEDED))
    }

    /** A name another kind has (case aside): «Такой вид уже есть — „Урок“», whole, over a sleeping «Готово». */
    @Test
    fun aNameAnotherKindHasSaysWhichOverASleepingDone() {
        show(sheet(name = "урок", problem = KindNameProblem.Taken(TAKEN_NAME)), 360.dp)
        assertTheReasonOverDone(word(TAKEN))
    }

    private fun assertTheReasonOverDone(reason: String) {
        val done = compose.onNode(hasText(word(DONE)) and hasClickAction()).assertIsNotEnabled()
        val line = compose.onNode(hasText(reason), useUnmergedTree = true)
        assertWordsWhole(line, reason)
        val over = line.getUnclippedBoundsInRoot()
        val button = done.getUnclippedBoundsInRoot()
        assertTrue("the reason over «${word(DONE)}»: ${over.bottom}, the button at ${button.top}", over.bottom <= button.top + 0.5.dp)
        done.performClick()
        compose.runOnIdle { assertTrue("a sleeping «${word(DONE)}» does nothing: $intents", intents.none { it == EventFormIntent.SheetDone }) }
    }

    /**
     * The eight colours stand 4 × 2 across the sheet, as the signs under them do (events-kinds.html 6, `.sw2`: four equal columns): the
     * first at the start of the row of signs, the last at its end, each cell 48 high, 8 apart. On the code before, four cells of 48 stood
     * at the left — 216 of 372.
     */
    @Test
    fun theColoursStandInFourColumnsAcrossTheSheet() {
        show(sheet(), 412.dp)
        val swatches = colors.map { compose.onNode(hasContentDescription(it) and isSelectable()).getUnclippedBoundsInRoot() }
        val rows = swatches.groupBy { it.top.value.roundToInt() }.toSortedMap().values.map { row -> row.sortedBy { it.left } }
        assertEquals("two rows", 2, rows.size)
        val signs = KindSign.OWN.map { cellOf(it).getUnclippedBoundsInRoot() }.groupBy { it.top.value.roundToInt() }.toSortedMap().values.first().sortedBy { it.left }
        rows.forEach { row ->
            assertEquals("four in a row", 4, row.size)
            assertEquals("from the start of the signs", signs.first().left.value, row.first().left.value, 0.6f)
            assertEquals("to the end of the signs", signs.last().right.value, row.last().right.value, 0.6f)
            assertEquals("8 apart", 8f, (row[1].left - row[0].right).value, 0.6f)
            row.forEach { cell ->
                assertEquals("a target 48 high", 48f, cell.height.value, 0.5f)
                assertEquals("four equal columns", row.first().width.value, cell.width.value, 0.6f)
            }
        }
    }

    /**
     * «Удалить вид…» (48) in the head of a kind of one's own does not make the head taller (events-kinds.html 6, `.sh-head`): the label and
     * what is under it stand where they stand in a new kind, which has no such button; the button stays 48 high over the label's middle.
     * On the code before, the head was 48 high and all under it stood 30 lower.
     */
    @Test
    fun theDeleteOfAKindOfOnesOwnDoesNotMakeItsHeadTaller() {
        val own = KindRules.all(listOf(StoredKind.Own(9, SOLFEGE, 1, KindSign.BOOK, 1)), config)
        val new = FormSheet.Kind(KindDraft(ref = null, name = SOLFEGE, color = 1, sign = KindSign.BOOK), problem = null)
        val edited = FormSheet.Kind(KindDraft(ref = KindRef.Custom(9), name = SOLFEGE, color = 1, sign = KindSign.BOOK), problem = null)
        var sheet by mutableStateOf(new)
        compose.setContent {
            words[OWN] = stringResource(Res.string.event_kind_own)
            words[COLOR_LINE] = stringResource(Res.string.event_kind_color_line, stringResource(Res.string.event_color_sea))
            words[DELETE] = stringResource(Res.string.event_kind_delete)
            val state = EventFormReducer.stateOf(
                draft = draft, sheet = sheet, dialog = null, loading = false, isNew = true, edited = null, editedSeries = null,
                initialDate = draft.date, events = emptyList(), kinds = own, today = today, focusNotes = false, config = config,
            )
            ViolinTheme { TestWindow(DpSize(412.dp, 892.dp)) { EventFormSheetCard(state, onIntent = { intents += it }) } }
        }
        compose.waitForIdle()
        // the label of a section is drawn in capitals
        fun top(key: String) = compose.onNode(hasText(word(key), ignoreCase = true), useUnmergedTree = true).getUnclippedBoundsInRoot().top.value
        val label = top(OWN)
        val colorLine = top(COLOR_LINE)
        compose.onNode(hasText(word(DELETE)), useUnmergedTree = true).assertDoesNotExist()

        compose.runOnIdle { sheet = edited }
        compose.waitForIdle()
        assertEquals("the label where it stood", label, top(OWN), 0.5f)
        assertEquals("what is under it where it stood", colorLine, top(COLOR_LINE), 0.5f)
        val button = compose.onNode(hasText(word(DELETE)) and hasClickAction()).getUnclippedBoundsInRoot()
        val head = compose.onNode(hasText(word(OWN), ignoreCase = true), useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("«${word(DELETE)}» 48 high: ${button.height}", button.height >= 48.dp - 0.5.dp)
        assertEquals("over the label's middle", ((head.top + head.bottom) / 2).value, ((button.top + button.bottom) / 2).value, 1f)
    }

    @Test
    fun aNameWakesDone() {
        show(sheet(name = "Сольфеджио", problem = null), 360.dp)
        compose.onNode(hasText(word(DONE)) and hasClickAction()).assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(EventFormIntent.SheetDone, intents.last()) }
        compose.onNode(hasText(word(NEEDED)), useUnmergedTree = true).assertDoesNotExist()
    }

    private companion object {
        const val DONE = "done"
        const val NEEDED = "needed"
        const val TAKEN = "taken"
        const val TAKEN_NAME = "Урок"
        const val OWN = "own"
        const val COLOR_LINE = "colorLine"
        const val DELETE = "delete"
        const val SOLFEGE = "Сольфеджио"
        val COLORS = listOf(
            Res.string.event_color_blue, Res.string.event_color_sea, Res.string.event_color_rose, Res.string.event_color_orchid,
            Res.string.event_color_ice, Res.string.event_color_turquoise, Res.string.event_color_powder, Res.string.event_color_lime,
        )
    }
}
