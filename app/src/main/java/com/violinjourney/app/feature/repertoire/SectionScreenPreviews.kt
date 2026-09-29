package com.violinjourney.app.feature.repertoire

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.domain.repertoire.Accidental
import com.violinjourney.app.core.domain.repertoire.PieceSection
import com.violinjourney.app.core.domain.repertoire.PieceStatus
import com.violinjourney.app.core.domain.repertoire.SectionCount
import com.violinjourney.app.core.domain.repertoire.SectionRef
import com.violinjourney.app.core.domain.repertoire.Tonic
import com.violinjourney.app.core.domain.repertoire.scale.ScaleKind
import com.violinjourney.app.core.domain.repertoire.scale.ScaleSpec
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.ViolinTheme
import kotlinx.datetime.LocalDate

// The list of a section of R4 (spec 3.36.4; repertoire.html 2) on the data of the spec: «Менуэт соль мажор» (И. С. Бах, G-dur, 100,
// «Учу», 2 дубля, лучший) is the sixth of «Произведения» — «выучено 2 из 6», chips «Все 6 · Разбираю 1 · Учу 3 · В репертуаре 2».
// Over the tabs: the status bar over it, no bar of the tabs. No photos of music in a preview — every card has the tile of its kind.

private val Today = LocalDate(2026, 9, 27)

private fun card(
    id: Long,
    title: String,
    composer: String = "",
    key: String? = null,
    tempo: Int? = null,
    status: PieceStatus,
    last: LocalDate? = null,
    takes: Int = 0,
    best: Boolean = false,
    scale: ScaleSpec? = null,
    stroke: Boolean = false,
    etude: Boolean = false,
) = PieceCard(
    id = id, title = title, composer = composer, keyName = key, tempoBpm = tempo, status = status, lastDate = last, takes = takes,
    hasBest = best, thumbPath = null, scale = scale, stroke = stroke, etude = etude,
)

private val Pieces = listOf(
    card(1, "Менуэт соль мажор", "И. С. Бах", "G-dur", 100, PieceStatus.LEARNING, Today, takes = 2, best = true),
    card(2, "Концерт ля минор, 1 ч.", "А. Вивальди", "a-moll", status = PieceStatus.IN_REPERTOIRE, last = LocalDate(2026, 9, 23), takes = 3, best = true),
    card(3, "Чардаш", "В. Монти", "d-moll", 120, PieceStatus.LEARNING, LocalDate(2026, 9, 12), takes = 1),
    card(4, "Концерт № 2, 3 ч.", "Ф. Зейтц", "G-dur", status = PieceStatus.IN_REPERTOIRE),
    card(5, "Юмореска", "А. Дворжак", "Ges-dur", status = PieceStatus.LEARNING),
    card(6, "Мелодия", "П. Чайковский", "Es-dur", status = PieceStatus.READING),
)

private val PiecesState = RepertoireState(
    loading = false, totalCount = 6, filter = null, cards = Pieces,
    section = SectionRef.BuiltIn(PieceSection.PIECES), count = SectionCount(reading = 1, learning = 3, learned = 2), maxNameLength = 24,
)

private val ScalesState = RepertoireState(
    loading = false, totalCount = 3, filter = null,
    cards = listOf(
        card(11, "G-dur · 3 октавы", key = "G-dur", tempo = 80, status = PieceStatus.LEARNING, last = LocalDate(2026, 9, 21), takes = 2, scale = ScaleSpec(Tonic.G, Accidental.NATURAL, ScaleKind.MAJOR, 3)),
        card(12, "a-moll гармонический · 2 октавы", key = "a-moll", status = PieceStatus.READING, scale = ScaleSpec(Tonic.A, Accidental.NATURAL, ScaleKind.HARMONIC_MINOR, 2)),
        card(13, "D-dur · 2 октавы", key = "D-dur", status = PieceStatus.READING, scale = ScaleSpec(Tonic.D, Accidental.NATURAL, ScaleKind.MAJOR, 2)),
    ),
    section = SectionRef.BuiltIn(PieceSection.SCALES), count = SectionCount(reading = 2, learning = 1, learned = 0), maxNameLength = 24,
)

private val OwnState = RepertoireState(
    loading = false, totalCount = 4, filter = null,
    cards = listOf(
        card(21, "Шрадик, упражнения для двойных нот — тетрадь первая, № 1–12", "Г. Шрадик", status = PieceStatus.LEARNING, last = Today, takes = 5),
        card(22, "Терции", tempo = 60, status = PieceStatus.LEARNING),
        card(23, "Сексты", status = PieceStatus.READING),
        card(24, "Октавы", status = PieceStatus.IN_REPERTOIRE, last = LocalDate(2025, 12, 30), takes = 1),
    ).map { it.copy(lastDateOtherYear = it.lastDate?.year == 2025) },
    section = SectionRef.Custom(9), sectionName = "Двойные ноты", count = SectionCount(reading = 1, learning = 2, learned = 1), maxNameLength = 24,
)

private val EmptyStrokes = RepertoireState(
    loading = false, totalCount = 0, filter = null, cards = emptyList(), section = SectionRef.BuiltIn(PieceSection.STROKES), maxNameLength = 24,
)

private val EtudesFiltered = RepertoireState(
    loading = false, totalCount = 2, filter = PieceStatus.IN_REPERTOIRE, cards = emptyList(),
    section = SectionRef.BuiltIn(PieceSection.ETUDES), count = SectionCount(reading = 1, learning = 1, learned = 0), maxNameLength = 24,
)

private val EtudesState = RepertoireState(
    loading = false, totalCount = 2, filter = null,
    cards = listOf(
        card(31, "Кайзер, этюд № 3", "Г. Кайзер", "C-dur", 88, PieceStatus.LEARNING, Today, takes = 1, etude = true),
        card(32, "Вольфарт, этюд № 12", "Ф. Вольфарт", status = PieceStatus.READING, etude = true),
    ),
    section = SectionRef.BuiltIn(PieceSection.ETUDES), count = SectionCount(reading = 1, learning = 1, learned = 0), maxNameLength = 24,
)

private val StrokesState = RepertoireState(
    loading = false, totalCount = 2, filter = null,
    cards = listOf(
        card(41, "Деташе на струне A", tempo = 72, status = PieceStatus.LEARNING, last = Today, takes = 3, stroke = true),
        card(42, "Спиккато", status = PieceStatus.IN_REPERTOIRE, stroke = true),
    ),
    section = SectionRef.BuiltIn(PieceSection.STROKES), count = SectionCount(reading = 0, learning = 1, learned = 1), maxNameLength = 24,
)

private val Loading = RepertoireReducer.loading(filter = null).copy(section = SectionRef.BuiltIn(PieceSection.PIECES), maxNameLength = 24)

/** The status bar of the phone over the screen of the preview. */
private val StatusBar = 24.dp

@Composable
private fun Section(state: RepertoireState) {
    ViolinTheme {
        CompositionLocalProvider(LocalReduceMotion provides true) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(top = StatusBar)) {
                SectionScreen(state = state, onIntent = {})
            }
        }
    }
}

@Preview(name = "Раздел · «Произведения»: счёт полосой, чипы с числами, «Менуэт соль мажор»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun PiecesPreview() = Section(PiecesState)

@Preview(name = "Раздел · «Гаммы»: плитка ключа, вид и темп, «Выучено 0»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun ScalesPreview() = Section(ScalesState)

@Preview(name = "Раздел · свой: «⋯», название в две строки, дата прошлого года", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun OwnPreview() = Section(OwnState)

@Preview(name = "Раздел · «Этюды»: плитка этюда, автор · тональность · темп", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun EtudesPreview() = Section(EtudesState)

@Preview(name = "Раздел · «Штрихи»: плитка смычка, только темп", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun StrokesPreview() = Section(StrokesState)

@Preview(name = "Раздел · пустой: пунктир, «пока пусто», текст раздела, без чипов", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun EmptyPreview() = Section(EmptyStrokes)

@Preview(name = "Раздел · пусто под фильтром: «Ничего со статусом «Выучено».», «Показать все»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun EmptyFilterPreview() = Section(EtudesFiltered)

@Preview(name = "Раздел · загрузка: шапка и нижняя зона", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun LoadingPreview() = Section(Loading)

@Preview(name = "Раздел · landscape 892 × 412: колонка 560, шапка 48", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun LandscapePreview() = Section(PiecesState)

@Preview(name = "Раздел · landscape 640 × 360: кнопка 48", locale = "ru", device = "spec:width=640dp,height=360dp")
@Composable
private fun LandscapeSmallPreview() = Section(PiecesState)

@Preview(name = "Раздел · 360 × 640", locale = "ru", device = "spec:width=360dp,height=640dp")
@Composable
private fun SmallPreview() = Section(PiecesState)

@Preview(name = "Раздел · de, 360, шрифт 1,3: чипы листаются вбок", locale = "de", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun GermanPreview() = Section(PiecesState)

@Preview(name = "Раздел · fr, 360", locale = "fr", device = "spec:width=360dp,height=640dp")
@Composable
private fun FrenchPreview() = Section(PiecesState)
