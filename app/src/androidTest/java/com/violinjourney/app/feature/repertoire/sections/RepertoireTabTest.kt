package com.violinjourney.app.feature.repertoire.sections

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.domain.repertoire.PieceSection
import com.violinjourney.app.core.domain.repertoire.PieceStatus
import com.violinjourney.app.core.domain.repertoire.SectionCount
import com.violinjourney.app.core.domain.repertoire.SectionRef
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.repertoire.PieceCard
import com.violinjourney.app.feature.repertoire.RepertoireIntent
import com.violinjourney.app.feature.repertoire.RepertoireState
import com.violinjourney.app.feature.repertoire.SectionScreen
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.add_kind_etude
import com.violinjourney.app.shared.resources.add_kind_own
import com.violinjourney.app.shared.resources.add_kind_piece
import com.violinjourney.app.shared.resources.add_kind_scale
import com.violinjourney.app.shared.resources.add_kind_stroke
import com.violinjourney.app.shared.resources.history_filter_all
import com.violinjourney.app.shared.resources.nav_repertoire
import com.violinjourney.app.shared.resources.piece_status_learned
import com.violinjourney.app.shared.resources.piece_status_learning
import com.violinjourney.app.shared.resources.piece_status_reading
import com.violinjourney.app.shared.resources.piece_tempo_description
import com.violinjourney.app.shared.resources.piece_time_title
import com.violinjourney.app.shared.resources.repertoire_add_any
import com.violinjourney.app.shared.resources.repertoire_empty_filter
import com.violinjourney.app.shared.resources.repertoire_empty_filter_hint
import com.violinjourney.app.shared.resources.repertoire_has_best_description
import com.violinjourney.app.shared.resources.repertoire_last_take_description
import com.violinjourney.app.shared.resources.repertoire_show_all
import com.violinjourney.app.shared.resources.repertoire_welcome_title
import com.violinjourney.app.shared.resources.section_bar_description
import com.violinjourney.app.shared.resources.section_bar_description_pieces
import com.violinjourney.app.shared.resources.section_empty_count
import com.violinjourney.app.shared.resources.section_empty_strokes
import com.violinjourney.app.shared.resources.section_learned
import com.violinjourney.app.shared.resources.section_own_add
import com.violinjourney.app.shared.resources.section_pieces
import com.violinjourney.app.shared.resources.section_scales
import com.violinjourney.app.shared.resources.section_strokes
import com.violinjourney.app.shared.resources.takes_few
import com.violinjourney.app.shared.resources.takes_many
import com.violinjourney.app.shared.resources.takes_one
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The tab «Репертуар» and the list of a section of R4 (spec 3.36.4) by their phrases, their answers and their layout: a tile and a row
 * of a section are one button with one phrase — the name, the count and the shares, the third step said as the section says it; an
 * empty section says «пока пусто» and no shares; «Свой раздел» and «Добавить в репертуар» answer, the latter before the data is
 * read, when there is no count, no tile and no welcome yet; «Что добавить?» gives the kind picked; upright the time stands under the
 * sections; in landscape the tiles stand two by two beside the time card of 360, one under another in a narrow column (640 with a
 * cutout too), the title is never cut for the count, and a window whose left column would not hold its rows is one column; a card of the list is one phrase, the chips carry their
 * numbers — a chip of nought too — and nothing under the filter says so and offers everything back.
 *
 * Every screen is laid out in a window of its own size, whatever the device's: a [Box] of that size, the window the bottom zone reads
 * ([LocalWindowInfo]) and a font of 1.0 (a device left in landscape or with a large font changes nothing). The words are read where
 * the screens read them, in the composition, in the language they speak (the lesson of stage 107).
 */
@RunWith(AndroidJUnit4::class)
class RepertoireTabTest {
    @get:Rule
    val compose = createComposeRule()

    private val tabIntents = mutableListOf<SectionsIntent>()
    private val listIntents = mutableListOf<RepertoireIntent>()
    private val words = mutableMapOf<String, String>()

    private fun word(key: String): String = words.getValue(key)

    /** A window of [width] × [height]: what the bottom zone asks to know its height. */
    private class Window(private val size: IntSize) : WindowInfo {
        override val isWindowFocused: Boolean = true
        override val containerSize: IntSize get() = size
    }

    /** [content] laid out whole in a window of [width] × [height] with the font at [fontScale]. */
    @Composable
    private fun InWindow(width: Dp, height: Dp, fontScale: Float, content: @Composable () -> Unit) {
        val density = LocalDensity.current
        val window = with(density) { Window(IntSize(width.roundToPx(), height.roundToPx())) }
        CompositionLocalProvider(LocalWindowInfo provides window, LocalDensity provides Density(density.density, fontScale)) {
            Box(Modifier.requiredSize(width, height)) { content() }
        }
    }

    private fun showTab(state: SectionsState, width: Dp = PORTRAIT_WIDTH, height: Dp = PORTRAIT_HEIGHT, fontScale: Float = 1f) {
        compose.setContent {
            ReadTabWords()
            ViolinTheme { InWindow(width, height, fontScale) { SectionsScreen(state, onIntent = { tabIntents += it }) } }
        }
        compose.waitForIdle()
    }

    @Composable
    private fun ReadTabWords() {
        val pieces = stringResource(Res.string.section_pieces)
        val strokes = stringResource(Res.string.section_strokes)
        words[PIECES_NAME] = pieces
        words[PIECES_TILE] = "$pieces, " + stringResource(Res.string.section_learned, 2, 6) + ": " +
            stringResource(Res.string.section_bar_description_pieces, 1, 3, 2)
        words[SCALES_TILE] = stringResource(Res.string.section_scales) + ", " + stringResource(Res.string.section_learned, 0, 3) + ": " +
            stringResource(Res.string.section_bar_description, 2, 1, 0)
        words[STROKES_TILE] = "$strokes, " + stringResource(Res.string.section_empty_count)
        words[OWN_ROW] = "$OWN_NAME, " + stringResource(Res.string.section_learned, 1, 4) + ": " +
            stringResource(Res.string.section_bar_description_pieces, 1, 2, 1)
        words[OWN_ADD] = stringResource(Res.string.section_own_add)
        words[ADD_ANY] = stringResource(Res.string.repertoire_add_any)
        words[KIND_PIECE] = stringResource(Res.string.add_kind_piece)
        words[KIND_SCALE] = stringResource(Res.string.add_kind_scale)
        words[KIND_ETUDE] = stringResource(Res.string.add_kind_etude)
        words[KIND_STROKE] = stringResource(Res.string.add_kind_stroke)
        words[KIND_OWN] = stringResource(Res.string.add_kind_own, OWN_NAME)
        words[TIME] = stringResource(Res.string.piece_time_title).uppercase()
        words[TITLE] = stringResource(Res.string.nav_repertoire)
        words[TOTAL] = stringResource(Res.string.section_learned, 3, 15)
        words[EMPTY_COUNT] = stringResource(Res.string.section_empty_count)
        words[WELCOME] = stringResource(Res.string.repertoire_welcome_title)
    }

    private fun bounds(description: String): DpRect = compose.onNodeWithContentDescription(description).getUnclippedBoundsInRoot()

    private fun boundsOfText(text: String): DpRect = compose.onNodeWithText(text).getUnclippedBoundsInRoot()

    /** The time row of «Менуэт соль мажор» — one button with its phrase, the full width of the card within its fields of 16. */
    private fun timeRow(): DpRect = compose.onNode(hasContentDescription(MINUET_TITLE, substring = true)).getUnclippedBoundsInRoot()

    @Test
    fun aTileIsOneButtonThatSaysItsNameCountAndSharesAndOpensItsSection() {
        showTab(filled)
        val tile = compose.onNodeWithContentDescription(word(PIECES_TILE))
        tile.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        tile.assertHeightIsAtLeast(TILE_MIN)
        tile.performClick()
        // «выучено» in «Гаммы»: the third step as the scales say it
        compose.onNodeWithContentDescription(word(SCALES_TILE)).assertExists()
        assertEquals(listOf<SectionsIntent>(SectionsIntent.SectionClicked(SectionRef.BuiltIn(PieceSection.PIECES))), tabIntents)
    }

    @Test
    fun anEmptySectionSaysItIsEmptyAndHasNoShares() {
        showTab(filled)
        compose.onNodeWithContentDescription(word(STROKES_TILE)).assertExists()
    }

    /** 360 × 640: the tiles still two in a row, not lower than 112; the row of one's own section and «Свой раздел» above the zone answer. */
    @Test
    fun onA360ScreenASectionOfOnesOwnIsARowAndSvoiRazdelAsksForANewOne() {
        showTab(filled, width = SMALL_WIDTH, height = SMALL_HEIGHT)
        val pieces = bounds(word(PIECES_TILE))
        val scales = bounds(word(SCALES_TILE))
        assertEquals("two in a row", pieces.top.value, scales.top.value, 1f)
        assertTrue("«Гаммы» to the right of «Произведения»", scales.left > pieces.right)
        compose.onNodeWithContentDescription(word(PIECES_TILE)).assertHeightIsAtLeast(TILE_MIN)
        compose.onNodeWithContentDescription(word(OWN_ROW)).performClick()
        compose.onNodeWithText(word(OWN_ADD)).performClick()
        assertEquals(listOf(SectionsIntent.SectionClicked(OWN), SectionsIntent.NewSectionClicked), tabIntents)
    }

    /** Upright «Время по элементам» stands under the sections, under «Свой раздел» (3.36.4 undoes 3.28, п. 1). */
    @Test
    fun uprightTheTimeStandsUnderTheSections() {
        showTab(filled)
        val ownAdd = boundsOfText(word(OWN_ADD))
        val time = boundsOfText(word(TIME))
        val pieces = bounds(word(PIECES_TILE))
        assertTrue("the time under «Свой раздел»: ${time.top} against ${ownAdd.bottom}", time.top >= ownAdd.bottom)
        assertEquals("in the one column", pieces.left.value, time.left.value, 1f)
    }

    /** While the data is read: the title and «Добавить в репертуар» only — no count, no tile, no welcome, no «Свой раздел» (3.36.4). */
    private fun assertOnlyTheTitleAndAddWhileTheDataIsRead() {
        compose.onNode(hasText(word(TITLE)) and isHeading()).assertExists()
        // what a count, a tile or the empty state would say if they came before the data: «пока пусто» is all of them
        compose.onAllNodesWithText(word(EMPTY_COUNT)).assertCountEquals(0)
        compose.onAllNodes(hasContentDescription(word(PIECES_NAME), substring = true)).assertCountEquals(0)
        compose.onAllNodesWithText(word(WELCOME)).assertCountEquals(0)
        compose.onAllNodesWithText(word(OWN_ADD)).assertCountEquals(0)
        compose.onNodeWithText(word(ADD_ANY)).performClick()
        assertEquals(listOf<SectionsIntent>(SectionsIntent.AddToRepertoireClicked), tabIntents)
    }

    @Test
    fun whileTheDataIsReadThereIsOnlyTheTitleAndAddAnswers() {
        showTab(loading)
        assertOnlyTheTitleAndAddWhileTheDataIsRead()
    }

    @Test
    fun whileTheDataIsReadInLandscapeThereIsOnlyTheTitleAndAddAnswers() {
        showTab(loading, width = LANDSCAPE_WIDTH, height = LANDSCAPE_HEIGHT)
        assertOnlyTheTitleAndAddWhileTheDataIsRead()
    }

    @Test
    fun theSheetGivesTheKindPickedAndTheSectionsOfOnesOwn() {
        showTab(filled.copy(adding = true))
        for (kind in listOf(KIND_PIECE, KIND_SCALE, KIND_ETUDE, KIND_STROKE, KIND_OWN)) compose.onNodeWithText(word(kind)).performClick()
        assertEquals(
            listOf(PieceSection.PIECES, PieceSection.SCALES, PieceSection.ETUDES, PieceSection.STROKES)
                .map { SectionsIntent.KindPicked(SectionRef.BuiltIn(it)) } + SectionsIntent.KindPicked(OWN),
            tabIntents,
        )
    }

    /**
     * Landscape 892 × 412: the tiles two by two at the left, 12 under the line of the title (5.29 R4), lower than upright;
     * «Время по элементам» to the right of them, its card 360 (a row of it 328 inside its fields of 16) and 16 from the edge; «Добавить
     * в репертуар» as wide as the left column inside its fields: 892 − 376 − 32 = 484.
     */
    @Test
    fun inLandscapeTheTilesStandTwoByTwoOnTheLeftAndTheTimeCardOf360OnTheRight() {
        showTab(filled, width = LANDSCAPE_WIDTH, height = LANDSCAPE_HEIGHT)
        val pieces = bounds(word(PIECES_TILE))
        val scales = bounds(word(SCALES_TILE))
        val time = boundsOfText(word(TIME))
        val title = compose.onNode(hasText(word(TITLE)) and isHeading()).getUnclippedBoundsInRoot()
        assertEquals("two in a row", pieces.top.value, scales.top.value, 1f)
        assertTrue("«Гаммы» to the right of «Произведения»", scales.left > pieces.right)
        assertTrue("the time to the right of the tiles", time.left > scales.right)
        assertTrue("a tile of landscape is lower than one upright: ${pieces.bottom - pieces.top}", pieces.bottom - pieces.top < TILE_MIN)
        assertEquals("the line of the title 32, then 12 to the tiles", 44f, (pieces.top - title.top).value, 1f)
        val row = timeRow()
        assertEquals("a row of the card of 360 within its fields", 328f, (row.right - row.left).value, 1f)
        assertEquals("the card 16 from the edge: its row 32", (pieces.left - 16.dp + LANDSCAPE_WIDTH - 32.dp).value, row.right.value, 1f)
        val add = boundsOfText(word(ADD_ANY))
        assertEquals("«Добавить в репертуар» as wide as the left column", 484f, (add.right - add.left).value, 1f)
    }

    /** 640 × 360: the left column is narrower than 320 — one tile under another. */
    @Test
    fun inANarrowLeftColumnTheTilesStandOneUnderAnother() {
        showTab(filled, width = 640.dp, height = 360.dp)
        val pieces = bounds(word(PIECES_TILE))
        val scales = bounds(word(SCALES_TILE))
        assertTrue("«Гаммы» under «Произведения»", scales.top >= pieces.bottom)
        assertEquals("in one column", pieces.left.value, scales.left.value, 1f)
    }

    /**
     * 640 × 360 with the font at 1.3: «Репертуар» and «выучено 3 из 15» (≈ 163 + 12 + 141) do not fit the left column of 232 on one
     * line — the count goes under the title, and the title is whole, not cut for the count (5.29 R4). Where both fit (the two
     * signs of «曲库»), the count stands beside it; either way it lies clear of the title.
     */
    @Test
    fun inLandscapeTheTitleIsNeverCutForTheCount() {
        showTab(filled, width = 640.dp, height = 360.dp, fontScale = LARGE_FONT)
        val titleNode = compose.onNode(hasText(word(TITLE)) and isHeading())
        val layouts = mutableListOf<TextLayoutResult>()
        titleNode.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        assertFalse("«${word(TITLE)}» is whole", layouts.single().isLineEllipsized(0))
        val title = titleNode.getUnclippedBoundsInRoot()
        val count = boundsOfText(word(TOTAL))
        val beside = count.left >= title.right
        val under = count.top >= title.bottom && (count.left - title.left).value in -1f..1f
        assertTrue("the count beside the title or under it from its start: $count against $title", beside || under)
    }

    /**
     * 610 × 360 — 640 × 360 with a cutout of 30 at its side: the left column has 202 inside, enough in every language for a tile
     * alone in its row, the row of one's own (Russian 181, Portuguese 196) and the button (Japanese, measured whole, 196) — two
     * columns, the tiles one under another, the time to the right.
     */
    @Test
    fun withACutoutAtItsSide640KeepsItsTwoColumns() {
        showTab(filled, width = 610.dp, height = 360.dp)
        val pieces = bounds(word(PIECES_TILE))
        val scales = bounds(word(SCALES_TILE))
        val time = boundsOfText(word(TIME))
        assertTrue("«Гаммы» under «Произведения»", scales.top >= pieces.bottom)
        assertTrue("the time to the right of the tiles", time.left > pieces.right)
    }

    /**
     * 560 × 360 — wider than tall, but the left column beside the time card would have 152 inside, less than the row of one's own
     * needs in any language (at the least 88 + 36 + a word of its count): one column in the middle, the tiles of landscape two in a
     * row (528 inside the fields) and «Время по элементам» under the sections, as upright.
     */
    @Test
    fun aWindowWhoseLeftColumnWouldNotHoldItsRowsHasOneColumnWithTheTimeUnderTheSections() {
        showTab(filled, width = 560.dp, height = 360.dp)
        val pieces = bounds(word(PIECES_TILE))
        val scales = bounds(word(SCALES_TILE))
        val ownAdd = boundsOfText(word(OWN_ADD))
        val time = boundsOfText(word(TIME))
        assertEquals("two in a row", pieces.top.value, scales.top.value, 1f)
        assertTrue("a tile of landscape: ${pieces.bottom - pieces.top}", pieces.bottom - pieces.top < TILE_MIN)
        assertTrue("the time under «Свой раздел»: ${time.top} against ${ownAdd.bottom}", time.top >= ownAdd.bottom)
        assertEquals("in the one column", pieces.left.value, time.left.value, 1f)
    }

    private fun showList(state: RepertoireState) {
        compose.setContent {
            ReadListWords(state)
            ViolinTheme { InWindow(PORTRAIT_WIDTH, PORTRAIT_HEIGHT, fontScale = 1f) { SectionScreen(state, onIntent = { listIntents += it }) } }
        }
        compose.waitForIdle()
    }

    @Composable
    private fun ReadListWords(state: RepertoireState) {
        val learning = stringResource(Res.string.piece_status_learning)
        val takes = stringResource(Formats.plural(2, Res.string.takes_one, Res.string.takes_few, Res.string.takes_many), 2)
        words[MINUET] = listOf(
            MINUET_TITLE, "И. С. Бах", "G-dur", stringResource(Res.string.piece_tempo_description, 100), learning,
            stringResource(Res.string.repertoire_last_take_description, Formats.recordDate(TODAY, false)), takes,
            stringResource(Res.string.repertoire_has_best_description),
        ).joinToString(", ")
        words[ALL] = stringResource(Res.string.history_filter_all)
        words[LEARNING] = learning
        words[LEARNED] = stringResource(Res.string.piece_status_learned)
        words[SHOW_ALL] = stringResource(Res.string.repertoire_show_all)
        words[COUNT] = stringResource(Res.string.section_learned, state.count.learned, state.count.total)
        words[EMPTY_FILTER] = stringResource(Res.string.repertoire_empty_filter, stringResource(Res.string.piece_status_reading))
        words[EMPTY_FILTER_HINT] = stringResource(Res.string.repertoire_empty_filter_hint)
        words[EMPTY_STROKES] = stringResource(Res.string.section_empty_strokes)
    }

    @Test
    fun aCardIsOneButtonWithTheWholePhraseOfItsElement() {
        showList(pieces)
        val card = compose.onNodeWithContentDescription(word(MINUET))
        card.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        card.performClick()
        assertEquals(listOf<RepertoireIntent>(RepertoireIntent.PieceClicked(1)), listIntents)
    }

    @Test
    fun theChipsCarryTheirNumbersAChipOfNoughtToo() {
        showList(pieces.copy(section = SectionRef.BuiltIn(PieceSection.ETUDES), count = SectionCount(reading = 1, learning = 5, learned = 0)))
        compose.onNode(hasText(word(ALL)) and hasText("6")).assertExists()
        // «Выучено» in «Этюды», with nought
        compose.onNode(hasText(word(LEARNED)) and hasText("0")).assertExists()
        compose.onNode(hasText(word(LEARNING)) and hasText("5")).performClick()
        assertEquals(listOf<RepertoireIntent>(RepertoireIntent.FilterSelected(PieceStatus.LEARNING)), listIntents)
    }

    /** An empty section: its own words in the middle and no chips. */
    @Test
    fun anEmptySectionHasItsWordsAndNoChips() {
        showList(RepertoireState(loading = false, totalCount = 0, filter = null, cards = emptyList(), section = SectionRef.BuiltIn(PieceSection.STROKES), maxNameLength = 24))
        compose.onNodeWithText(word(EMPTY_STROKES)).assertExists()
        compose.onAllNodes(hasText(word(ALL))).assertCountEquals(0)
    }

    /** Nothing under the filter: «Ничего со статусом «Разбираю».», «Статус меняется на экране элемента.» and «Показать все». */
    @Test
    fun nothingUnderTheFilterSaysSoAndGivesEverythingBack() {
        showList(pieces.copy(filter = PieceStatus.READING, cards = emptyList()))
        compose.onNodeWithContentDescription(word(COUNT), substring = true).assertExists()
        compose.onNodeWithText(word(EMPTY_FILTER)).assertExists()
        compose.onNodeWithText(word(EMPTY_FILTER_HINT)).assertExists()
        compose.onNodeWithText(word(SHOW_ALL)).performClick()
        assertEquals(listOf<RepertoireIntent>(RepertoireIntent.FilterSelected(null)), listIntents)
    }

    private companion object {
        const val PIECES_NAME = "piecesName"
        const val PIECES_TILE = "piecesTile"
        const val SCALES_TILE = "scalesTile"
        const val STROKES_TILE = "strokesTile"
        const val OWN_ROW = "ownRow"
        const val OWN_ADD = "ownAdd"
        const val ADD_ANY = "addAny"
        const val KIND_PIECE = "kindPiece"
        const val KIND_SCALE = "kindScale"
        const val KIND_ETUDE = "kindEtude"
        const val KIND_STROKE = "kindStroke"
        const val KIND_OWN = "kindOwn"
        const val TIME = "time"
        const val TITLE = "title"
        const val TOTAL = "total"
        const val EMPTY_COUNT = "emptyCount"
        const val WELCOME = "welcome"
        const val MINUET = "minuet"
        const val ALL = "all"
        const val LEARNING = "learning"
        const val LEARNED = "learned"
        const val SHOW_ALL = "showAll"
        const val COUNT = "count"
        const val EMPTY_FILTER = "emptyFilter"
        const val EMPTY_FILTER_HINT = "emptyFilterHint"
        const val EMPTY_STROKES = "emptyStrokes"
        const val OWN_NAME = "Двойные ноты"
        const val MINUET_TITLE = "Менуэт соль мажор"
        const val LARGE_FONT = 1.3f
        val TILE_MIN = 112.dp
        val PORTRAIT_WIDTH = 412.dp
        val PORTRAIT_HEIGHT = 892.dp
        val SMALL_WIDTH = 360.dp
        val SMALL_HEIGHT = 640.dp
        val LANDSCAPE_WIDTH = 892.dp
        val LANDSCAPE_HEIGHT = 412.dp
        val TODAY = LocalDate(2026, 9, 27)
        val OWN = SectionRef.Custom(9)

        val filled: SectionsState = run {
            val cards = listOf(
                SectionCard(SectionRef.BuiltIn(PieceSection.PIECES), null, SectionCount(reading = 1, learning = 3, learned = 2)),
                SectionCard(SectionRef.BuiltIn(PieceSection.SCALES), null, SectionCount(reading = 2, learning = 1, learned = 0)),
                SectionCard(SectionRef.BuiltIn(PieceSection.ETUDES), null, SectionCount(reading = 1, learning = 1, learned = 0)),
                SectionCard(SectionRef.BuiltIn(PieceSection.STROKES), null, SectionCount.EMPTY),
                SectionCard(OWN, OWN_NAME, SectionCount(reading = 1, learning = 2, learned = 1)),
            )
            SectionsState(
                loading = false,
                cards = cards,
                total = cards.fold(SectionCount.EMPTY) { sum, card -> sum + card.count },
                maxNameLength = 24,
                time = PieceTimeCard(listOf(PieceTimeRow(1, MINUET_TITLE, 40 * 60_000L, 0), PieceTimeRow(2, "Чардаш", 20 * 60_000L, 0)), days = 30, expanded = false),
            )
        }

        val loading = SectionsState(loading = true, cards = emptyList(), total = SectionCount.EMPTY, maxNameLength = 24)

        val pieces = RepertoireState(
            loading = false,
            totalCount = 6,
            filter = null,
            cards = listOf(
                PieceCard(
                    id = 1, title = MINUET_TITLE, composer = "И. С. Бах", keyName = "G-dur", tempoBpm = 100, status = PieceStatus.LEARNING,
                    lastDate = TODAY, takes = 2, hasBest = true, thumbPath = null,
                ),
            ),
            section = SectionRef.BuiltIn(PieceSection.PIECES),
            count = SectionCount(reading = 1, learning = 3, learned = 2),
            maxNameLength = 24,
        )
    }
}
