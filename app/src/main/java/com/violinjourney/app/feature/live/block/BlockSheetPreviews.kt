package com.violinjourney.app.feature.live.block

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.violinjourney.app.core.domain.repertoire.PieceSection
import com.violinjourney.app.core.domain.repertoire.SectionRef
import com.violinjourney.app.core.ui.theme.ViolinTheme

// The sheets of blocks (handoff 30e, 30f) as their content: a modal sheet does not draw in a preview.

private const val MIN = 60_000L

private val lesson = listOf(
    PickerSection(
        SectionRef.BuiltIn(PieceSection.PIECES), null, doneToday = 0,
        listOf(
            PickerPiece(1, "Менуэт соль мажор", "И. С. Бах", TodayMark.Played(7 * MIN)),
            PickerPiece(2, "Концерт ля минор, I ч.", "А. Вивальди", TodayMark.Running),
            PickerPiece(3, "Мелодия", "К. В. Глюк", TodayMark.None),
            PickerPiece(4, "Концерт ми минор, соч. 64, I. Allegro molto appassionato", "Ф. Мендельсон", TodayMark.None),
        ),
    ),
    PickerSection(
        SectionRef.BuiltIn(PieceSection.SCALES), null, doneToday = 1,
        listOf(PickerPiece(5, "G-dur · 3 октавы", null, TodayMark.Done(10 * MIN)), PickerPiece(6, "a-moll гармонический · 2 октавы", null, TodayMark.None)),
    ),
    PickerSection(
        SectionRef.BuiltIn(PieceSection.ETUDES), null, doneToday = 1,
        listOf(PickerPiece(7, "Кайзер № 3", "Г. Кайзер", TodayMark.Done(15 * MIN)), PickerPiece(8, "Кайзер № 8", "Г. Кайзер", TodayMark.None)),
    ),
    PickerSection(SectionRef.Custom(1), "Двойные ноты", doneToday = 0, listOf(PickerPiece(9, "Терции", null, TodayMark.None))),
)

private fun picker(selected: Long? = null, sections: List<PickerSection> = lesson, now: NowLine? = NowLine("Концерт ля минор, I ч.", 7)) =
    BlockSheet.Picker(now, sections, selected, goalMinutes = 15, quickGoals = listOf(5, 10, 15, 20, 30), goalStep = 5, canGoalDown = true, canGoalUp = true)

@Composable
private fun SheetPreview(content: @Composable () -> Unit) {
    ViolinTheme {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh)) { content() }
    }
}

@Preview(name = "30f1 «Сначала — занятие»", widthDp = 412, heightDp = 372)
@Composable
private fun OfferPreview() = SheetPreview { OfferContent {} }

@Preview(name = "30e1 «Что играем» · a block runs", widthDp = 412, heightDp = 788)
@Composable
private fun PickerPreview() = SheetPreview { PickerPortrait(picker()) {} }

@Preview(name = "30e2 «Что играем» · Kaiser 8 picked, the goal panel", widthDp = 412, heightDp = 788)
@Composable
private fun PickerSelectedPreview() = SheetPreview { PickerPortrait(picker(selected = 8)) {} }

@Preview(name = "30e2 «Что играем» · 360 wide", widthDp = 360, heightDp = 640)
@Composable
private fun PickerNarrowPreview() = SheetPreview { PickerPortrait(picker(selected = 8)) {} }

@Preview(name = "30e4 «Что играем» · the repertoire is empty", widthDp = 412, heightDp = 788)
@Composable
private fun PickerEmptyPreview() = SheetPreview { PickerPortrait(picker(sections = emptyList(), now = null)) {} }

@Preview(name = "30e5 «Что играем» · landscape", widthDp = 892, heightDp = 412)
@Composable
private fun PickerLandscapePreview() = SheetPreview { PickerLandscape(picker(selected = 8)) {} }
