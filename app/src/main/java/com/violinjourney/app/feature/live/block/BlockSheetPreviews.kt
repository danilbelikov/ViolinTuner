package com.violinjourney.app.feature.live.block

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.domain.repertoire.PieceSection
import com.violinjourney.app.core.domain.repertoire.SectionRef
import com.violinjourney.app.core.ui.components.AppSheetCard
import com.violinjourney.app.core.ui.theme.ViolinTheme

// «Что играем» and «Сначала — занятие» (spec 3.36.6; live.html, 4 and 5) in the frame of the sheet of R1 (AppSheetCard: a modal
// sheet does not draw in a preview), over the scrim.

private const val MIN = 60_000L

private val lesson = listOf(
    PickerSection(
        SectionRef.BuiltIn(PieceSection.PIECES), null, doneToday = 1,
        listOf(
            PickerPiece(1, "Концерт ля минор, 1 ч.", "А. Вивальди", TodayMark.Done(15 * MIN)),
            PickerPiece(2, "Юмореска", "А. Дворжак", TodayMark.Played(7 * MIN)),
            PickerPiece(3, "Чардаш", "В. Монти", TodayMark.None),
            PickerPiece(4, "Концерт ми минор, соч. 64, I. Allegro molto appassionato", "Ф. Мендельсон", TodayMark.None),
        ),
    ),
    PickerSection(
        SectionRef.BuiltIn(PieceSection.SCALES), null, doneToday = 0,
        listOf(
            PickerPiece(5, "G-dur · 3 октавы", null, TodayMark.None),
            PickerPiece(6, "a-moll гармонический · 2 октавы", null, TodayMark.None),
            PickerPiece(7, "D-dur · 2 октавы", null, TodayMark.Running),
        ),
    ),
    PickerSection(
        SectionRef.BuiltIn(PieceSection.ETUDES), null, doneToday = 1,
        listOf(PickerPiece(8, "Кайзер № 3", "Г. Кайзер", TodayMark.Done(10 * MIN)), PickerPiece(9, "Кайзер № 8", "Г. Кайзер", TodayMark.None)),
    ),
    PickerSection(SectionRef.Custom(1), "Двойные ноты", doneToday = 0, listOf(PickerPiece(10, "Терции", null, TodayMark.None))),
)

private fun picker(
    selected: Long? = null,
    sections: List<PickerSection> = lesson,
    now: NowLine? = NowLine("D-dur · 2 октавы", 7),
    practiceMs: Long = 24 * MIN + 18_000,
) = BlockSheet.Picker(
    now = now,
    sections = sections,
    selectedId = selected,
    goalMinutes = 15,
    quickGoals = listOf(5, 10, 15, 20, 30),
    goalStep = 5,
    canGoalDown = true,
    canGoalUp = true,
    practiceMs = practiceMs,
    goalMinMinutes = 5,
    goalMaxMinutes = 60,
)

/** The frame of the sheet at the bottom of the screen, over the scrim, as the sheet stands over Live. */
@Composable
private fun SheetPreview(content: @Composable ColumnScope.() -> Unit) {
    ViolinTheme {
        Box(Modifier.fillMaxSize().background(ViolinTheme.sheetScrim), contentAlignment = Alignment.BottomCenter) {
            AppSheetCard(contentPadding = PaddingValues(0.dp), content = content)
        }
    }
}

@Preview(name = "«Что играем» · Концерт picked: the goal panel, one row of seven", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun PickerSelectedPreview() = SheetPreview { PickerPortrait(picker(selected = 3, now = null), onIntent = {}) }

@Preview(name = "«Что играем» · a block runs: «Сейчас» with «Остановить» 48", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun PickerRunningPreview() = SheetPreview { PickerPortrait(picker(), onIntent = {}) }

@Preview(name = "«Сначала — занятие» · the living «Начать занятие», «Не сейчас» quietly", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun OfferPreview() = SheetPreview { OfferContent(onIntent = {}, calm = { false }) }

@Preview(name = "«Что играем» · the repertoire is empty: «Открыть репертуар» pinned", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun PickerEmptyPreview() = SheetPreview { PickerPortrait(picker(sections = emptyList(), now = null, practiceMs = 42_000), onIntent = {}) }

@Preview(name = "«Что играем» · 360 x 640: the goal in two rows of four and three", widthDp = 360, heightDp = 640, locale = "ru")
@Composable
private fun PickerNarrowPreview() = SheetPreview { PickerPortrait(picker(selected = 9), onIntent = {}) }

@Preview(name = "«Что играем» · fr, 360 x 640 at the font 1.3", widthDp = 360, heightDp = 640, fontScale = 1.3f, locale = "fr")
@Composable
private fun PickerFrenchPreview() = SheetPreview { PickerPortrait(picker(selected = 9), onIntent = {}) }

@Preview(name = "«Что играем» · de, 360 x 640 at the font 1.3", widthDp = 360, heightDp = 640, fontScale = 1.3f, locale = "de")
@Composable
private fun PickerGermanPreview() = SheetPreview { PickerPortrait(picker(selected = 9), onIntent = {}) }

@Preview(name = "«Что играем» · 320 x 500 at the font 1.5: the header and «Сейчас» scroll with the list, the goal pinned", widthDp = 320, heightDp = 500, fontScale = 1.5f, locale = "ru")
@Composable
private fun PickerSmallLargeFontPreview() = SheetPreview { PickerPortrait(picker(selected = 9, now = NowLine("D-dur · 2 октавы", 12)), onIntent = {}) }

@Preview(name = "«Что играем» · 411 x 892 (1080 px at 420 dpi): the seven goals in one row, the gaps give way", widthDp = 411, heightDp = 892, locale = "ru")
@Composable
private fun PickerPhone411Preview() = SheetPreview { PickerPortrait(picker(selected = 3, now = null), onIntent = {}) }

@Preview(name = "«Что играем» · landscape 892 x 412: not wider than 640, the column of 300", widthDp = 892, heightDp = 412, locale = "ru")
@Composable
private fun PickerLandscapePreview() = SheetPreview { PickerLandscape(picker(selected = 9), onIntent = {}) }

@Preview(name = "«Что играем» · landscape 640 x 360 behind a cutout, nothing picked", widthDp = 603, heightDp = 360, locale = "ru")
@Composable
private fun PickerLandscapeLowPreview() = SheetPreview { PickerLandscape(picker(), onIntent = {}) }

@Preview(name = "«Что играем» · landscape 603 x 360 at the font 1.5: «Остановить» under the lines of «Сейчас»", widthDp = 603, heightDp = 360, fontScale = 1.5f, locale = "ru")
@Composable
private fun PickerLandscapeLargeFontPreview() = SheetPreview { PickerLandscape(picker(selected = 9, now = NowLine("D-dur · 2 октавы", 12)), onIntent = {}) }

@Preview(name = "«Сначала — занятие» · landscape: one column, not wider than 640", widthDp = 892, heightDp = 412, locale = "ru")
@Composable
private fun OfferLandscapePreview() = SheetPreview { OfferContent(onIntent = {}, calm = { false }) }
