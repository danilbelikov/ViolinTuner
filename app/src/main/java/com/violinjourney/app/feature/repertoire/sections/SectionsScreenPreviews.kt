package com.violinjourney.app.feature.repertoire.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.domain.repertoire.PieceSection
import com.violinjourney.app.core.domain.repertoire.SectionCount
import com.violinjourney.app.core.domain.repertoire.SectionRef
import com.violinjourney.app.core.ui.components.AppSheetCard
import com.violinjourney.app.core.ui.components.AppSheetDefaults
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.navigation.AppBottomBar
import com.violinjourney.app.navigation.TopLevelDestination

// The tab «Репертуар» of R4 (spec 3.36.4) on the data of the spec: «Произведения» 6 (1 / 3 / 2) with «Менуэт соль мажор» the sixth,
// «Гаммы» 3 (2 / 1 / 0), «Этюды» 2 (1 / 1 / 0), empty «Штрихи», «Двойные ноты» 4 (1 / 2 / 1) — «выучено 3 из 15». Each screen as a
// phone shows it: the status bar over it, the bar of the tabs under it (compact in landscape). Still frames: motion is removed.

private const val MIN = 60_000L

private val Time = PieceTimeCard(
    rows = listOf(
        PieceTimeRow(pieceId = 1, title = "Концерт ля минор, 1 ч.", totalMs = 540 * MIN, todayMs = 40 * MIN),
        PieceTimeRow(pieceId = 2, title = "G-dur · 3 октавы", totalMs = 270 * MIN, todayMs = 0),
        PieceTimeRow(pieceId = 3, title = "Чардаш", totalMs = 210 * MIN, todayMs = 8 * MIN),
        PieceTimeRow(pieceId = 4, title = "Менуэт соль мажор", totalMs = 130 * MIN, todayMs = 0),
        PieceTimeRow(pieceId = 5, title = "D-dur · 2 октавы", totalMs = 40 * MIN, todayMs = 0),
    ),
    days = 30,
    expanded = false,
)

private val Own = SectionCard(SectionRef.Custom(9), name = "Двойные ноты", count = SectionCount(reading = 1, learning = 2, learned = 1))

private val Cards = listOf(
    SectionCard(SectionRef.BuiltIn(PieceSection.PIECES), name = null, count = SectionCount(reading = 1, learning = 3, learned = 2)),
    SectionCard(SectionRef.BuiltIn(PieceSection.SCALES), name = null, count = SectionCount(reading = 2, learning = 1, learned = 0)),
    SectionCard(SectionRef.BuiltIn(PieceSection.ETUDES), name = null, count = SectionCount(reading = 1, learning = 1, learned = 0)),
    SectionCard(SectionRef.BuiltIn(PieceSection.STROKES), name = null, count = SectionCount.EMPTY),
    Own,
)

private val Filled = SectionsState(
    loading = false,
    cards = Cards,
    total = Cards.fold(SectionCount.EMPTY) { sum, card -> sum + card.count },
    maxNameLength = 24,
    time = Time,
)

/** Nothing in the repertoire, one section of one's own made already: the tiles say what the sections will be. */
private val Empty = SectionsState(
    loading = false,
    cards = Cards.map { it.copy(count = SectionCount.EMPTY) },
    total = SectionCount.EMPTY,
    maxNameLength = 24,
)

private val Loading = SectionsState(loading = true, cards = emptyList(), total = SectionCount.EMPTY, maxNameLength = 24)

/** The status bar of the phone over the screen of the preview. */
private val StatusBar = 24.dp

@Composable
private fun Tab(state: SectionsState) {
    ViolinTheme {
        CompositionLocalProvider(LocalReduceMotion provides true) {
            BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                val landscape = maxWidth > maxHeight
                Column(Modifier.fillMaxSize().padding(top = StatusBar)) {
                    SectionsScreen(state = state, onIntent = {}, modifier = Modifier.weight(1f))
                    AppBottomBar(TopLevelDestination.REPERTOIRE, onSelect = {}, compact = landscape)
                }
            }
        }
    }
}

/** «Что добавить?» over the tab: the sheet is a window, which a preview does not draw — its card at the bottom under the scrim. */
@Composable
private fun AddSheetOver(state: SectionsState) {
    ViolinTheme {
        CompositionLocalProvider(LocalReduceMotion provides true) {
            BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                val landscape = maxWidth > maxHeight
                val sheetMax = maxHeight - StatusBar - AppSheetDefaults.TopClearance
                Column(Modifier.fillMaxSize().padding(top = StatusBar)) {
                    SectionsScreen(state = state, onIntent = {}, modifier = Modifier.weight(1f))
                    AppBottomBar(TopLevelDestination.REPERTOIRE, onSelect = {}, compact = landscape)
                }
                Box(Modifier.fillMaxSize().background(ViolinTheme.sheetScrim), contentAlignment = Alignment.BottomCenter) {
                    AppSheetCard(Modifier.heightIn(max = sheetMax)) {
                        AddSheetContent(state.own, onPick = {})
                    }
                }
            }
        }
    }
}

@Preview(name = "Репертуар · обычная: плитки 2 × 2, пунктир «Штрихов», свой раздел, время под разделами", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun TabPreview() = Tab(Filled)

@Preview(name = "Репертуар · пусто: «Здесь живёт то, что вы играете», плитки с пунктиром", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun TabEmptyPreview() = Tab(Empty)

@Preview(name = "Репертуар · загрузка: заголовок и нижняя зона", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun TabLoadingPreview() = Tab(Loading)

@Preview(name = "Репертуар · лист «Что добавить?»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun AddSheetPreview() = AddSheetOver(Filled)

@Preview(name = "Репертуар · лист «Что добавить?», landscape 892 × 412: не шире 640", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun AddSheetLandscapePreview() = AddSheetOver(Filled)

@Preview(name = "Репертуар · landscape 892 × 412: разделы и зона слева, время справа", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun TabLandscapePreview() = Tab(Filled)

@Preview(name = "Репертуар · landscape 640 × 360: плитки в столбец, кнопка 48", locale = "ru", device = "spec:width=640dp,height=360dp")
@Composable
private fun TabLandscapeSmallPreview() = Tab(Filled)

@Preview(name = "Репертуар · landscape 740 × 360 (Galaxy S9): «Произведения» не встаёт в плитку двух столбцов — плитки в столбец", locale = "ru", device = "spec:width=740dp,height=360dp")
@Composable
private fun TabLandscapeGalaxyPreview() = Tab(Filled)

@Preview(name = "Репертуар · landscape 640 × 360, шрифт 1,3: счёт под заголовком, «Репертуар» целиком", locale = "ru", fontScale = 1.3f, device = "spec:width=640dp,height=360dp")
@Composable
private fun TabLandscapeLargeFontPreview() = Tab(Filled)

@Preview(name = "Репертуар · landscape 600 × 360 (640 с вырезом 40 сбоку): две колонки, плитки в столбец, полоса своего раздела короче", locale = "ru", device = "spec:width=600dp,height=360dp")
@Composable
private fun TabLandscapeCutoutPreview() = Tab(Filled)

@Preview(name = "Репертуар · 560 × 360 (разделённый экран): строке своего раздела не хватает — одна колонка, время под разделами", locale = "ru", device = "spec:width=560dp,height=360dp")
@Composable
private fun TabWideNarrowPreview() = Tab(Filled)

@Preview(name = "Репертуар · landscape, пусто: одна колонка по центру", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun TabLandscapeEmptyPreview() = Tab(Empty)

@Preview(name = "Репертуар · landscape, загрузка", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun TabLandscapeLoadingPreview() = Tab(Loading)

@Preview(name = "Репертуар · 360 × 640", locale = "ru", device = "spec:width=360dp,height=640dp")
@Composable
private fun TabSmallPreview() = Tab(Filled)

@Preview(name = "Репертуар · 360, шрифт 1,15: названия плиток 15 sp — «Произведения» в одну строку", locale = "ru", fontScale = 1.15f, device = "spec:width=360dp,height=640dp")
@Composable
private fun TabSmallLargerFontPreview() = Tab(Filled)

@Preview(name = "Репертуар · 360, шрифт 1,3: названия плиток 13 sp — «Произведения» в одну строку", locale = "ru", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun TabSmallLargeFontPreview() = Tab(Filled)

@Preview(name = "Репертуар · de, 360, шрифт 1,3: плитки растут по тексту", locale = "de", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun TabGermanPreview() = Tab(Filled)

@Preview(name = "Репертуар · fr, 360", locale = "fr", device = "spec:width=360dp,height=640dp")
@Composable
private fun TabFrenchPreview() = Tab(Filled)

@Preview(name = "Репертуар · лист «Что добавить?», fr, 360, шрифт 1,3", locale = "fr", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun AddSheetFrenchPreview() = AddSheetOver(Filled)
