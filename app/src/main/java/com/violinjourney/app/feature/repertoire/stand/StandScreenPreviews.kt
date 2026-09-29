package com.violinjourney.app.feature.repertoire.stand

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview
import com.violinjourney.app.core.domain.repertoire.Accidental
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.domain.repertoire.Tonic
import com.violinjourney.app.core.domain.repertoire.scale.ScaleKind
import com.violinjourney.app.core.domain.repertoire.scale.ScaleSpec
import com.violinjourney.app.core.domain.repertoire.scale.Scales
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.repertoire.piece.TakeProblem

// The music stand of R4 (spec 3.36.4; repertoire.html 6): the panel with «Записать дубль» as the main button, a take running on
// the panel and in the capsule, the hint of the first visit. No photos in a preview — the pages are paper; the first is a drawn scale.

private val Config = RepertoireConfig()

private val Pages = listOf(
    StandPage(StandPage.DRAWN_ID, path = null, scale = Scales.build(ScaleSpec(Tonic.G, Accidental.NATURAL, ScaleKind.MAJOR, 2), Config.scaleLowestMidi, Config.scaleHighestMidi)),
    StandPage(2, path = null),
    StandPage(3, path = null),
    StandPage(4, path = null),
)

private fun stand(panel: Boolean, hint: Boolean = false, page: Int = 1) =
    StandState(loading = false, pages = Pages, initialPage = page, panelVisible = panel, deleteDialog = false, showHint = hint)

private val Idle = StandTake(recording = false, elapsedSeconds = 0, problem = null)
private val Running = StandTake(recording = true, elapsedSeconds = 72, problem = null)

@Composable
private fun Stand(state: StandState, take: StandTake) {
    ViolinTheme {
        CompositionLocalProvider(LocalReduceMotion provides true) {
            StandScreen(state = state, take = take, onIntent = {}, onRecordClick = {})
        }
    }
}

@Preview(name = "Пюпитр · панель: «2 / 4», корзина, «Записать дубль» главной во всю ширину", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun PanelPreview() = Stand(stand(panel = true), Idle)

@Preview(name = "Пюпитр · идёт дубль, панель: полоса записи без столбиков, «Слишком шумно»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun RecordingPanelPreview() = Stand(stand(panel = true), Running.copy(problem = TakeProblem.TOO_NOISY))

@Preview(name = "Пюпитр · идёт дубль, панель спрятана: капсула «● 1:12 · 2 / 4»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun CapsulePreview() = Stand(stand(panel = false), Running)

@Preview(name = "Пюпитр · первый вход: зоны пунктиром со стрелками и карточка, панели нет", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun FirstVisitPreview() = Stand(stand(panel = false, hint = true, page = 0), Idle)

@Preview(name = "Пюпитр · landscape 892 × 412: «Записать дубль» справа внизу по ширине слова", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun LandscapePreview() = Stand(stand(panel = true), Idle)

@Preview(name = "Пюпитр · landscape 892 × 412, идёт дубль: полоса на непрозрачной таблетке поверх листа", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun LandscapeRecordingPreview() = Stand(stand(panel = true), Running)

@Preview(name = "Пюпитр · первый вход во время дубля: подсказка и капсула «● 1:12 · 1 / 4» — знак дубля не прячется", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun FirstVisitRecordingPreview() = Stand(stand(panel = false, hint = true, page = 0), Running)

@Preview(name = "Пюпитр · первый вход, de, 360, шрифт 1,3", locale = "de", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun GermanFirstVisitPreview() = Stand(stand(panel = false, hint = true, page = 0), Idle)
