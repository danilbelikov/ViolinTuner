package com.violinjourney.app.feature.repertoire.piece

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.domain.backing.AudioRoute
import com.violinjourney.app.core.domain.backing.BackingOutput
import com.violinjourney.app.core.domain.repertoire.Accidental
import com.violinjourney.app.core.domain.repertoire.PieceStatus
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.domain.repertoire.Tonic
import com.violinjourney.app.core.domain.repertoire.scale.ScaleKind
import com.violinjourney.app.core.domain.repertoire.scale.ScaleSpec
import com.violinjourney.app.core.domain.repertoire.scale.Scales
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.history.HistoryCard
import com.violinjourney.app.feature.history.Selection
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

// The screen of an element of R4 (spec 3.36.4; repertoire.html 3, landscape.html 2) on the data of the spec: «Менуэт соль мажор»
// (И. С. Бах, G-dur, 100, «Учу»), four pages, the backing «Бах — клавесин» in Pixel Buds, a note, six takes with the best first.
// Over the tabs: the status bar over it, no bar of the tabs. No photos in a preview — the pages are the outline of a sheet.

private val Zone = TimeZone.UTC
private val Config = RepertoireConfig()
private val Buds = AudioRoute(BackingOutput.BLUETOOTH, "Pixel Buds")
private val Speaker = AudioRoute(BackingOutput.SPEAKER, null)

private fun take(id: Long, day: Int, hour: Int, minutes: Int, seconds: Int, best: Boolean = false, video: Boolean = false, underBacking: Boolean = false, new: Boolean = false) =
    TakeItem(
        card = HistoryCard(
            id = id, title = null, startedAtEpochMs = LocalDate(2026, 9, day).toEpochDays() * 86_400_000L + hour * 3_600_000L,
            date = LocalDate(2026, 9, day), durationMs = (minutes * 60 + seconds) * 1_000L, pieceTitle = "Менуэт соль мажор", pieceId = 1,
            hasAudio = true, hasVideo = video, best = best, underBacking = underBacking,
        ),
        best = best,
        isNew = new,
    )

private val Takes = listOf(
    take(1, 20, 18, 3, 52, best = true, underBacking = true),
    take(2, 27, 8, 3, 41, video = true),
    take(3, 25, 19, 2, 5),
    take(4, 22, 7, 2, 12),
    take(5, 18, 20, 1, 58),
    take(6, 15, 19, 2, 1),
)

private val Pages = (1..4).map { SheetTile(pageId = it.toLong(), number = it, thumbPath = null) }

private val Minuet = PieceState(
    loading = false,
    header = PieceHeader("Менуэт соль мажор", "И. С. Бах", "G-dur", 100, PieceStatus.LEARNING),
    pages = Pages,
    importing = 0,
    notes = "В 12-м такте — сразу в третью позицию. Легато в середине не рвать, смычок экономить до конца фразы.",
    takes = Takes,
    progress = TakeProgress(lastScore = 82, maxScore = 88, scores = listOf(64, 67, 79, 76, 88, 82)),
    notesCollapsedLines = Config.notesCollapsedLines,
)

private val NewPiece = Minuet.copy(
    header = PieceHeader("Юмореска", "А. Дворжак", "Ges-dur", null, PieceStatus.READING),
    pages = emptyList(), notes = "", takes = emptyList(), progress = null,
)

private val Scale = Minuet.copy(
    header = PieceHeader("G-dur · 3 октавы", "", "G-dur", 80, PieceStatus.LEARNING),
    pages = emptyList(), notes = "",
    takes = Takes.take(1).map { it.copy(best = false, card = it.card.copy(best = false, underBacking = false, pieceTitle = "G-dur · 3 октавы")) },
    progress = null,
    scale = Scales.build(ScaleSpec(Tonic.G, Accidental.NATURAL, ScaleKind.MAJOR, 3), Config.scaleLowestMidi, Config.scaleHighestMidi),
    exercise = true,
)

private val Backing = BackingUi(title = "Бах — клавесин", durationMs = 220_000, enabled = true, route = Buds)
private val NoBacking = BackingUi(title = null, durationMs = 0, enabled = false, route = Buds)

private val Idle = TakeState.idle(micPermission = true, bars = Config.levelBars)
private val Levels = listOf(0.3f, 0.55f, 0.8f, 0.62f, 0.4f, 0.7f, 0.92f, 0.66f, 0.38f, 0.52f, 0.74f, 0.48f, 0.28f, 0.44f)
private val Recording = TakeState(
    recording = true, elapsedSeconds = 72, levels = Levels, problem = null, micPermission = true, backingPlayedMs = 72_000, backingDurationMs = 220_000,
)

/** The status bar of the phone over the screen of the preview. */
private val StatusBar = 24.dp

@Composable
private fun Element(state: PieceState, backing: BackingUi? = Backing, take: TakeState = Idle) {
    ViolinTheme {
        CompositionLocalProvider(LocalReduceMotion provides true) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(top = StatusBar)) {
                PieceScreen(
                    state = state,
                    take = remember { mutableStateOf(take) },
                    onIntent = {},
                    addPhoto = AddPhotoActions({}, {}),
                    zone = Zone,
                    backing = backing,
                )
            }
        }
    }
}

@Preview(name = "Произведение · обычное: статус, лента 4 стр., минусовка, заметки, итог и дубли; «С минусовкой» и Pixel Buds", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun RegularPreview() = Element(Minuet)

@Preview(name = "Произведение · новое: две плитки нот, тихие строки, «Дублей пока нет»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun NewPreview() = Element(NewPiece, backing = NoBacking)

@Preview(name = "Произведение · идёт дубль под минусовкой: полоса записи, ход минусовки, приглушено то, что увело бы с экрана", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun RecordingPreview() = Element(Minuet, take = Recording)

@Preview(name = "Произведение · идёт дубль, шумно: строка «Слишком шумно», столбики тише", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun NoisyPreview() = Element(Minuet, take = Recording.copy(problem = TakeProblem.TOO_NOISY))

@Preview(name = "Произведение · без наушников: плашка «Подключите наушники… Или выключите «С минусовкой»», «наушников нет»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun NoHeadphonesPreview() = Element(Minuet, backing = Backing.copy(route = Speaker))

@Preview(name = "Произведение · минусовка готовится: плашка и крутилка в кнопке", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun PreparingPreview() = Element(Minuet, backing = Backing.copy(preparing = true))

@Preview(name = "Произведение · минусовка не подготовилась: плашка со вторым предложением", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun UnpreparedPreview() = Element(Minuet, backing = Backing.copy(unprepared = true))

@Preview(name = "Произведение · тумблер выключен: строка стоит, наушники видны", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun BackingOffPreview() = Element(Minuet, backing = Backing.copy(enabled = false, route = Speaker))

@Preview(name = "Произведение · нет разрешения: строка R1 над спящей «Записать дубль» без точки", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun NoMicPreview() = Element(NewPiece, backing = NoBacking, take = Idle.copy(micPermission = false))

@Preview(name = "Произведение · выбор дублей: всё, кроме списка, и нижняя зона — 0,38", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun SelectingPreview() = Element(Minuet.copy(selection = Selection(active = true, ids = setOf(2, 3))))

@Preview(name = "Гамма · нарисованные ноты, «G3 – G6», «Добавить фото нот» с подписью", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun ScalePreview() = Element(Scale, backing = NoBacking)

@Preview(name = "Произведение · загрузка: шапка и пустая нижняя зона", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun LoadingPreview() = Element(Minuet, backing = null)

@Preview(name = "Произведение · landscape 892 × 412: мета, статус и лента слева над зоной, минусовка и заметки рядом справа, «Записать дубль» в одну строку", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun LandscapePreview() = Element(Minuet)

@Preview(name = "Произведение · landscape 892 × 412, идёт дубль", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun LandscapeRecordingPreview() = Element(Minuet, take = Recording)

@Preview(name = "Гамма · landscape 892 × 412: «Ноты · G3 – G6» слева, нарисованные ноты справа", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun ScaleLandscapePreview() = Element(Scale, backing = NoBacking)

@Preview(name = "Произведение · landscape 640 × 360: кнопки 48, меты нет, минусовка над заметками (рядом название рвалось бы)", locale = "ru", device = "spec:width=640dp,height=360dp")
@Composable
private fun SmallLandscapePreview() = Element(Minuet)

@Preview(name = "Новое произведение · landscape 640 × 360: плитки нот по словам — «Сфотографировать» целиком", locale = "ru", device = "spec:width=640dp,height=360dp")
@Composable
private fun SmallLandscapeNewPreview() = Element(NewPiece, backing = NoBacking)

@Preview(name = "Произведение · 360 × 640", locale = "ru", device = "spec:width=360dp,height=640dp")
@Composable
private fun SmallPreview() = Element(Minuet)

@Preview(name = "Произведение · de, 360, шрифт 1,3: «Im Repertoire» в две строки, плашка до трёх строк", locale = "de", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun GermanPreview() = Element(Minuet.copy(header = Minuet.header!!.copy(status = PieceStatus.IN_REPERTOIRE)), backing = Backing.copy(route = Speaker))

@Preview(name = "Произведение · fr, 360, шрифт 1,3: «Au répertoire», тумблер «Avec accomp.»", locale = "fr", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun FrenchPreview() = Element(Minuet)

@Preview(name = "Произведение · 360, шрифт 1,3: ступени статуса по словам — «В репертуаре» не рвётся", locale = "ru", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun RussianLargePreview() = Element(Minuet)

@Preview(name = "Новое произведение · 360, шрифт 1,3: плитки шире 150 — «Сфотографировать» целиком", locale = "ru", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun RussianLargeNewPreview() = Element(NewPiece, backing = NoBacking)
