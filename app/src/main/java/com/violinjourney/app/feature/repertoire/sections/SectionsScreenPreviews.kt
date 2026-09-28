package com.violinjourney.app.feature.repertoire.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview
import com.violinjourney.app.core.domain.repertoire.PieceSection
import com.violinjourney.app.core.domain.repertoire.SectionCount
import com.violinjourney.app.core.domain.repertoire.SectionRef
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.ViolinTheme

// The tab «Репертуар» of stage 100 (spec 3.36.1): the title over the sections as they were under the switch of «Записи»;
// landscape keeps its columns — the time on the left, the sections on the right. Still frames: motion is removed.

private const val MIN = 60_000L

private val Time = PieceTimeCard(
    rows = listOf(
        PieceTimeRow(pieceId = 1, title = "Концерт ля минор, 1 ч.", totalMs = 185 * MIN, todayMs = 20 * MIN),
        PieceTimeRow(pieceId = 2, title = "G-dur · 3 октавы", totalMs = 120 * MIN, todayMs = 10 * MIN),
        PieceTimeRow(pieceId = 3, title = "Кайзер, этюд № 3", totalMs = 75 * MIN, todayMs = 0),
        PieceTimeRow(pieceId = 4, title = "Менуэт соль мажор", totalMs = 40 * MIN, todayMs = 0),
        PieceTimeRow(pieceId = 5, title = "Деташе на струне A", totalMs = 25 * MIN, todayMs = 0),
        PieceTimeRow(pieceId = 6, title = "Мартле", totalMs = 10 * MIN, todayMs = 0),
    ),
    days = 30,
    expanded = false,
)

private val Cards = listOf(
    SectionCard(SectionRef.BuiltIn(PieceSection.PIECES), name = null, count = SectionCount(reading = 2, learning = 3, learned = 4)),
    SectionCard(SectionRef.BuiltIn(PieceSection.SCALES), name = null, count = SectionCount(reading = 1, learning = 2, learned = 5)),
    SectionCard(SectionRef.BuiltIn(PieceSection.ETUDES), name = null, count = SectionCount(reading = 1, learning = 1, learned = 1)),
    SectionCard(SectionRef.BuiltIn(PieceSection.STROKES), name = null, count = SectionCount(reading = 0, learning = 0, learned = 3)),
    SectionCard(SectionRef.Custom(9), name = "К экзамену", count = SectionCount(reading = 1, learning = 1, learned = 0)),
)

private val Filled = SectionsState(
    loading = false,
    cards = Cards,
    total = Cards.fold(SectionCount.EMPTY) { sum, card -> sum + card.count },
    maxNameLength = 24,
    time = Time,
)

private val Empty = SectionsState(
    loading = false,
    cards = Cards.take(4).map { it.copy(count = SectionCount.EMPTY) },
    total = SectionCount.EMPTY,
    maxNameLength = 24,
)

@Composable
private fun SectionsPreview(state: SectionsState) {
    ViolinTheme {
        CompositionLocalProvider(LocalReduceMotion provides true) {
            SectionsScreen(state = state, onIntent = {})
        }
    }
}

@Preview(name = "Репертуар", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun SectionsFilledPreview() = SectionsPreview(Filled)

@Preview(name = "Репертуар · empty", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun SectionsEmptyPreview() = SectionsPreview(Empty)

@Preview(name = "Репертуар · landscape", widthDp = 892, heightDp = 412, locale = "ru")
@Composable
private fun SectionsLandscapePreview() = SectionsPreview(Filled)

@Preview(name = "Репертуар · 360 × 640", widthDp = 360, heightDp = 640, locale = "ru")
@Composable
private fun SectionsSmallPreview() = SectionsPreview(Filled)
