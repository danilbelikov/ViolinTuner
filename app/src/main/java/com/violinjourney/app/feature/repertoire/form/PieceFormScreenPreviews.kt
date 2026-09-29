package com.violinjourney.app.feature.repertoire.form

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import com.violinjourney.app.core.domain.repertoire.Accidental
import com.violinjourney.app.core.domain.repertoire.KeyMode
import com.violinjourney.app.core.domain.repertoire.MusicalKey
import com.violinjourney.app.core.domain.repertoire.PieceDraft
import com.violinjourney.app.core.domain.repertoire.PieceSection
import com.violinjourney.app.core.domain.repertoire.PieceStatus
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.domain.repertoire.SectionRef
import com.violinjourney.app.core.domain.repertoire.Tonic
import com.violinjourney.app.core.ui.components.AppSheetCard
import com.violinjourney.app.core.ui.components.AppSheetDefaults
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.ViolinTheme

// The form of a piece of R4 (spec 3.36.4; repertoire.html 4, landscape.html 5) on the data of the spec: «Менуэт соль мажор»,
// И. С. Бах, G-dur, 100, «Учу». Over the tabs: the status bar over it, no bar of the tabs. A preview has no keyboard: the zone stands
// at the bottom of the window; the line «причина · кнопка 48» is shown in a window as low as the room over a keyboard lying.

private val Config = RepertoireConfig()

/** The status bar of the phone over the screen of the preview. */
private val StatusBar = 24.dp

private val Sections = listOf(
    SectionOption(SectionRef.BuiltIn(PieceSection.PIECES), null, enabled = true),
    SectionOption(SectionRef.BuiltIn(PieceSection.SCALES), null, enabled = false),
    SectionOption(SectionRef.BuiltIn(PieceSection.ETUDES), null, enabled = true),
    SectionOption(SectionRef.BuiltIn(PieceSection.STROKES), null, enabled = true),
    SectionOption(SectionRef.Custom(7), "Двойные ноты", enabled = true),
)

private val Minuet = PieceDraft(
    title = "Менуэт соль мажор",
    composer = "И. С. Бах",
    key = MusicalKey(Tonic.G, Accidental.NATURAL, KeyMode.MAJOR),
    tempoBpm = 100,
    status = PieceStatus.LEARNING,
    notes = "В 12-м такте — сразу в третью позицию.",
)

private fun state(
    draft: PieceDraft = Minuet,
    isNew: Boolean = true,
    loading: Boolean = false,
    section: SectionRef = SectionRef.BuiltIn(draft.section),
) = PieceFormState(
    loading = loading,
    isNew = isNew,
    draft = draft,
    canSave = draft.title.isNotBlank(),
    dialog = null,
    maxTitleLength = Config.maxTitleLength,
    maxComposerLength = Config.maxComposerLength,
    maxNotesLength = Config.maxNotesLength,
    focusNotes = false,
    savedTitle = if (isNew) "" else draft.title,
    section = section,
    sections = Sections,
)

@Composable
private fun Form(state: PieceFormState) {
    ViolinTheme {
        CompositionLocalProvider(LocalReduceMotion provides true) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(top = StatusBar)) {
                PieceFormScreen(state = state, onIntent = {})
            }
        }
    }
}

/** A sheet over the form: the sheet is a window, which a preview does not draw — its card at the bottom under the scrim. */
@Composable
private fun SheetOver(state: PieceFormState, sheet: PieceFormSheet) {
    ViolinTheme {
        CompositionLocalProvider(LocalReduceMotion provides true) {
            BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                val sheetMax = maxHeight - StatusBar - AppSheetDefaults.TopClearance
                Box(Modifier.fillMaxSize().padding(top = StatusBar)) { PieceFormScreen(state = state, onIntent = {}) }
                Box(Modifier.fillMaxSize().background(ViolinTheme.sheetScrim), contentAlignment = Alignment.BottomCenter) {
                    when (sheet) {
                        PieceFormSheet.KEY -> AppSheetCard(Modifier.heightIn(max = sheetMax), bottom = { KeySheetButtons(onIntent = {}) }) {
                            KeySheetContent(state.draft.key, onIntent = {})
                        }
                        PieceFormSheet.SECTION -> AppSheetCard(Modifier.heightIn(max = sheetMax)) { SectionSheetContent(state, onIntent = {}) }
                    }
                }
            }
        }
    }
}

@Preview(name = "Форма · новое, заполнено: подписи над полями, «Раздел» и «Тональность» строками, темп с «—», «Сохранить» внизу", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun FilledPreview() = Form(state())

@Preview(name = "Форма · новое без названия: заглушки в полях, «Без названия не сохранить» над «Сохранить» 0,38 — с первого кадра", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun UntitledPreview() = Form(state(PieceDraft()))

@Preview(name = "Форма · мало места над клавиатурой (< 200 dp): нижняя зона строкой «причина · кнопка 48»", locale = "ru", device = "spec:width=892dp,height=260dp")
@Composable
private fun RowDockPreview() = Form(state(PieceDraft()))

@Preview(name = "Форма · лист «Тональность»: итог a-moll крупно, тоники в ряд (и на 411: зазоры уже 6), знак и лад на фоне экрана, «Без тональности» · «Готово»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun KeySheetPreview() = SheetOver(state(Minuet.copy(key = MusicalKey(Tonic.A, Accidental.NATURAL, KeyMode.MINOR))), PieceFormSheet.KEY)

@Preview(name = "Форма · лист «Тональность» без тоники: знак и лад 0,38, «Сначала выберите тонику»; 360 — тоники в два ряда", locale = "ru", device = "spec:width=360dp,height=640dp")
@Composable
private fun KeySheetEmptyPreview() = SheetOver(state(Minuet.copy(key = null)), PieceFormSheet.KEY)

@Preview(name = "Форма · лист «Тональность», 360 × 640, шрифт 1,3: «Без / тональности» и «Готово» вместе 14 sp — слово не рвётся", locale = "ru", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun KeySheetLargeFontPreview() = SheetOver(state(Minuet.copy(key = null)), PieceFormSheet.KEY)

@Preview(name = "Форма · лист «Тональность» лёжа 892 × 412 без тоники: знак и лад в один ряд, «Сначала выберите тонику» видна без прокрутки", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun KeySheetLandscapePreview() = SheetOver(state(Minuet.copy(key = null)), PieceFormSheet.KEY)

@Preview(name = "Форма · лист «Раздел»: плашки значков, галочка у текущего, «Гаммы» 0,38 «только для гамм», свой раздел", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun SectionSheetPreview() = SheetOver(state(), PieceFormSheet.SECTION)

@Preview(name = "Форма · правка: «Произведение», «Удалить произведение» последней строкой, «Сохранить» внизу", locale = "ru", device = "spec:width=412dp,height=1180dp")
@Composable
private fun EditPreview() = Form(state(isNew = false))

@Preview(name = "Форма · новый этюд: «Автор · необязательно», третья ступень «Выучено»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun EtudePreview() = Form(state(Minuet.copy(title = "Этюд № 2", composer = "Р. Крейцер", key = null, section = PieceSection.ETUDES)))

@Preview(name = "Форма · правка штриха: подсказки чипами над названием, без композитора и тональности, «Удалить штрих»", locale = "ru", device = "spec:width=412dp,height=1000dp")
@Composable
private fun StrokePreview() = Form(state(PieceDraft(title = "Спиккато", section = PieceSection.STROKES, tempoBpm = 80), isNew = false))

@Preview(name = "Форма · загрузка правки: шапка и пустая нижняя зона, ни полей, ни причины", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun LoadingPreview() = Form(state(PieceDraft(), isNew = false, loading = true))

@Preview(name = "Форма · landscape 892 × 412: шапка 48, одна колонка 560 по центру, нижняя зона её ширины", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun LandscapePreview() = Form(state())

@Preview(name = "Форма · 360 × 640, шрифт 1,3: подписи по словам, чипы темпа под степпером", locale = "ru", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun SmallLargePreview() = Form(state())

@Preview(name = "Форма · de, 360: «Komponist · optional», «Im Repertoire» целиком", locale = "de", device = "spec:width=360dp,height=640dp")
@Composable
private fun GermanPreview() = Form(state(Minuet.copy(status = PieceStatus.IN_REPERTOIRE)))

@Preview(name = "Форма · fr, 360: «Compositeur · facultatif», «Au répertoire»", locale = "fr", device = "spec:width=360dp,height=640dp")
@Composable
private fun FrenchPreview() = Form(state(Minuet.copy(status = PieceStatus.IN_REPERTOIRE)))
