package com.violinjourney.app.feature.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.audio.playback.PlayerState
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.events.KindRef
import com.violinjourney.app.core.domain.events.SessionEvent
import com.violinjourney.app.core.domain.session.SessionAnalyzer
import com.violinjourney.app.core.domain.session.SessionDetails
import com.violinjourney.app.core.domain.session.SessionSample
import com.violinjourney.app.core.domain.session.SessionSummary
import com.violinjourney.app.core.domain.sound.BuiltInPreset
import com.violinjourney.app.core.ui.components.AppSheetCard
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.session.components.NotePlace
import com.violinjourney.app.feature.session.components.NoteSheetButtons
import com.violinjourney.app.feature.session.components.NoteSheetContent
import com.violinjourney.app.feature.session.components.StickyVideo
import com.violinjourney.app.feature.session.components.VideoSurfaceCallbacks
import com.violinjourney.app.feature.sound.SoundCaption
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

// The recording of R5 (spec 3.36.5; records.html 2 and 3, landscape.html 3) on the data of the mockup: «Менуэт соль мажор», a take of
// 2:05 recorded on Sunday 27 September 2026 at 18:42, a D-major scale up and down with F#5 and C#5 drifting flat; the player at the
// bottom with its waveform at 0:43. Each screen as a phone shows it: the status bar over it. Still frames: motion is removed.

private val Moscow = TimeZone.of("Europe/Moscow")
private val Started = LocalDateTime.parse("2026-09-27T18:42:00").toInstant(Moscow).toEpochMilliseconds()
private const val MENUET = "Менуэт соль мажор"
private const val DURATION_MS = 125_000L
private const val POSITION_MS = 43_000L
private const val VIDEO_BYTES = 214L * 1024 * 1024
private const val BIG_VIDEO_BYTES = 612L * 1024 * 1024

/** A D major scale up and down, some notes flat, each with a little vibrato. */
private fun scaleSamples(repeats: Int): List<SessionSample?> {
    val notes = listOf(62 to -3.0, 64 to 2.0, 66 to -14.0, 67 to 1.0, 69 to 4.0, 71 to -2.0, 73 to -11.0, 74 to 0.0, 78 to -22.0)
    val samples = ArrayList<SessionSample?>()
    repeat(repeats) { round ->
        (if (round % 2 == 0) notes else notes.reversed()).forEach { (midi, bias) ->
            repeat(14) { samples += SessionSample(midi, bias + 4 * sin(it * 0.9)) }
            samples += null
        }
    }
    return samples
}

private fun contentOf(samples: List<SessionSample?> = scaleSamples(repeats = 2), title: String? = null, piece: Boolean = true): SessionContent {
    val config = IntonationConfig()
    val analysis = SessionAnalyzer.analyze(samples, config)
    val metrics = analysis.metrics
    val summary = SessionSummary(
        id = 1, title = title, startedAtEpochMs = Started, durationMs = samples.size * config.sessionBucketMs,
        a4Hz = 440.0, toleranceCents = config.toleranceCents, nearCents = config.nearCents,
        scorePercent = metrics?.scorePercent ?: 0, nearPercent = metrics?.nearPercent ?: 0,
        offPercent = metrics?.offPercent ?: 0, maeCents = metrics?.maeCents ?: 0.0, biasCents = metrics?.biasCents ?: 0.0,
        previewZones = emptyList(), audioPath = null,
    )
    return SessionContentMapper.contentOf(SessionDetails(summary, samples, analysis), config)
        .copy(pieceTitle = if (piece) MENUET else null, pieceId = if (piece) 1L else null, hasAudio = true)
}

private val Take = contentOf()
private val Wave = List(120) { abs(sin(it * 0.21)).toFloat() * 0.8f + 0.2f * abs(sin(it * 1.7)).toFloat() }
private val Player = PlayerState(ready = true, positionMs = POSITION_MS, durationMs = DURATION_MS, processed = true)
private val Hall = SoundRow(SoundCaption.BuiltIn(BuiltInPreset.CHAMBER_HALL), own = false, processed = true)
private val Off = SoundRow(SoundCaption.BuiltIn(BuiltInPreset.OFF), own = false, processed = false)

/** Settings of its own that do nothing (spec 3.17): the line says «выключен». */
private val OwnOff = SoundRow(SoundCaption.BuiltIn(BuiltInPreset.OFF), own = true, processed = false)
private val Video = VideoUi(width = 1920, height = 1080, showing = true, sizeBytes = VIDEO_BYTES)

private val Sound = SessionState.Loaded(Take, player = Player, sound = Hall, waveform = Wave)

/** The status bar of the phone over the screen of the preview. */
private val StatusBar = 24.dp

@Composable
private fun Screen(state: SessionState) {
    ViolinTheme {
        CompositionLocalProvider(LocalReduceMotion provides true) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(top = StatusBar)) {
                SessionScreen(state = state, onIntent = {}, zone = Moscow)
            }
        }
    }
}

@Preview(name = "Запись · звуковой дубль: итог, ноты, плеер внизу с A/B и «Звук»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun TakePreview() = Screen(Sound)

@Preview(name = "Запись · свободная запись: «09:15 · 0:36» без слова, звезды нет, «Звук · как у всех · без обработки» во всю ширину", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun FreePreview() = Screen(
    SessionState.Loaded(contentOf(piece = false), player = Player.copy(processed = false), sound = Off, waveform = Wave),
)

/** «Осенний концерт» of 24 October (spec 3.36.9): its recordings are named by it and say the word of its kind under the name. */
private val Concert = SessionEvent(4, "Осенний концерт", LocalDate(2026, 10, 24), KindRef.BuiltIn(BuiltInKind.PERFORMANCE), ownName = null)

@Preview(name = "Запись события: «Осенний концерт · 24 октября», «выступление · 18:42 · 2:05», без «К произведению»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun EventRecordPreview() = Screen(
    SessionState.Loaded(contentOf(piece = false).copy(event = Concert), player = Player, sound = Hall, waveform = Wave),
)

@Preview(name = "Видео события: слово вида и у видео — «выступление · 18:42 · 2:05»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun EventVideoPreview() = Screen(
    SessionState.Loaded(contentOf(piece = false).copy(event = Concert, hasVideo = true), player = Player, sound = Hall, waveform = Wave, video = Video),
)

@Preview(name = "Запись события своего вида: имя вида, как написано — «Оркестр ДК · 18:42 · 2:05»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun EventOwnKindPreview() = Screen(
    SessionState.Loaded(
        contentOf(piece = false).copy(event = SessionEvent(5, "Сводная", LocalDate(2026, 9, 27), KindRef.Custom(9), ownName = "Оркестр ДК")),
        player = Player, sound = Hall, waveform = Wave,
    ),
)

@Preview(name = "Запись · свои настройки, которые ничего не делают: «Звук · выключен» во всю ширину, без A/B", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun OwnOffPreview() = Screen(Sound.copy(player = Player.copy(processed = false), sound = OwnOff))

@Preview(name = "Запись · волна ещё не посчитана: обычный ползунок", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun NoWavePreview() = Screen(Sound.copy(waveform = null))

@Preview(name = "Запись · видео-дубль: картинка ≤ 40 %, «на весь экран» на стекле", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun VideoPreview() = Screen(Sound.copy(content = Take.copy(hasVideo = true, best = true), video = Video))

@Preview(name = "Запись · под минусовку: знак в подзаголовке, «только скрипка» над лентой, третий ряд плеера", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun BackingPreview() = Screen(
    Sound.copy(content = Take.copy(underBacking = true), player = Player.copy(hasBacking = true)),
)

@Preview(name = "Запись · «Готовим минусовку…»: крутилка в плеере, A/B и минусовка 0,38, видео затемнено", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun PreparingPreview() = Screen(
    Sound.copy(content = Take.copy(underBacking = true, hasVideo = true), player = null, preparingBacking = true, video = Video),
)

@Preview(name = "Запись · без звука: пунктирная строка первой, ни плеера, ни «Поделиться»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun SilentPreview() = Screen(SessionState.Loaded(contentOf(piece = false).copy(hasAudio = false)))

@Preview(name = "Запись · видео потеряно: плашка на месте картинки", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun LostPreview() = Screen(
    SessionState.Loaded(Take.copy(hasAudio = false, hasVideo = true), video = VideoUi(lost = true)),
)

@Preview(name = "Запись · картинку не показать: плашка, плеер работает", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun UndecodablePreview() = Screen(Sound.copy(content = Take.copy(hasVideo = true), video = VideoUi(undecodable = true, sizeBytes = VIDEO_BYTES)))

@Preview(name = "Запись · «Что уходит» пусто: одна строка без шеврона", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun CleanPreview() = Screen(
    Sound.copy(content = contentOf(List(80) { SessionSample(69, 1.5 * sin(it * 0.5)) })),
)

@Preview(name = "Запись · 360 × 640: компактный плеер одним рядом", locale = "ru", device = "spec:width=360dp,height=640dp")
@Composable
private fun SmallPreview() = Screen(Sound)

@Preview(name = "Запись · 360 × 640 под минусовку: второй ряд — компактная минусовка", locale = "ru", device = "spec:width=360dp,height=640dp")
@Composable
private fun SmallBackingPreview() = Screen(
    Sound.copy(content = Take.copy(underBacking = true), player = Player.copy(hasBacking = true)),
)

// «Готовим минусовку…» in the compact panel with A/B (the processing does something): beside A/B and «Звук» where its words stand
// whole in two lines, else alone in its row with A/B and «Звук» a row lower, at the end (PreparingFit)
private val Preparing = Sound.copy(content = Take.copy(underBacking = true), player = null, preparingBacking = true)

@Preview(name = "Запись · 360 × 640, «Готовим минусовку…» с A/B: рядом с A/B и «Звук»", locale = "ru", device = "spec:width=360dp,height=640dp")
@Composable
private fun SmallPreparingPreview() = Screen(Preparing)

@Preview(name = "Запись · 360 × 640, «Готовим минусовку…» с A/B, fr, шрифт 1,3: надпись одна в ряду, A/B и «Звук» рядом ниже", locale = "fr", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun SmallPreparingFrenchPreview() = Screen(Preparing)

@Preview(name = "Запись · 360 × 640, «Готовим минусовку…» с A/B, de, шрифт 1,3", locale = "de", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun SmallPreparingGermanPreview() = Screen(Preparing)

@Preview(name = "Запись · 640 × 360 с вырезом (≈ 603), «Готовим минусовку…» с A/B, es, шрифт 1,3", locale = "es", fontScale = 1.3f, device = "spec:width=603dp,height=360dp")
@Composable
private fun LandscapeSmallPreparingPreview() = Screen(Preparing)

@Preview(name = "Запись · de, 360, шрифт 1,3", locale = "de", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun GermanLargePreview() = Screen(
    Sound.copy(content = Take.copy(underBacking = true), player = Player.copy(hasBacking = true)),
)

@Preview(name = "Запись · fr, 360", locale = "fr", device = "spec:width=360dp,height=640dp")
@Composable
private fun FrenchPreview() = Screen(Sound)

@Preview(name = "Запись · landscape 892 × 412: сжатый итог над плеером слева", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun LandscapePreview() = Screen(Sound)

@Preview(name = "Запись · landscape 892 × 412, видео: картинка над плеером, итог справа первым", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun LandscapeVideoPreview() = Screen(Sound.copy(content = Take.copy(hasVideo = true), video = Video))

@Preview(name = "Запись · 640 × 360 с вырезом (≈ 603) под минусовку, шрифт 1,3: итог прокручивается над плеером", locale = "ru", fontScale = 1.3f, device = "spec:width=603dp,height=360dp")
@Composable
private fun LandscapeSmallPreview() = Screen(
    Sound.copy(content = Take.copy(underBacking = true), player = Player.copy(hasBacking = true)),
)

@Preview(name = "Запись · landscape без звука: одна колонка", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun LandscapeSilentPreview() = Screen(SessionState.Loaded(contentOf(piece = false).copy(hasAudio = false)))

@Preview(name = "Запись · не найдена", locale = "ru", device = "spec:width=412dp,height=500dp")
@Composable
private fun NotFoundPreview() = Screen(SessionState.NotFound)

@Composable
private fun StuckRow(waiting: Boolean) = ViolinTheme {
    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp)) {
        StickyVideo(
            video = Video,
            content = Take,
            player = if (waiting) null else Player,
            // scrolled well past the picture: the row
            scrolledPx = { 10_000 },
            availableWidth = 380f,
            screenHeight = 800f,
            callbacks = VideoSurfaceCallbacks(onSurface = {}, onSurfaceGone = {}),
            onTap = {},
            onFullscreen = {},
            waiting = waiting,
        )
    }
}

@Preview(name = "Видео при прокрутке: строка под шапкой — «82 %», «в строе · ниже на 6 ц»", locale = "ru", widthDp = 412, heightDp = 120)
@Composable
private fun StuckRowPreview() = StuckRow(waiting = false)

@Preview(name = "Видео при прокрутке, пока готовится минусовка: мини-кадр затемнён, «на весь экран» нет", locale = "ru", widthDp = 412, heightDp = 120)
@Composable
private fun StuckRowWaitingPreview() = StuckRow(waiting = true)

// The sheet of a note (spec 3.36.5; records.html 3) in its frame without a window.

@Composable
private fun NoteSheet(zone: Zone? = Zone.OFF, steady: Boolean = true, place: NotePlace?, content: SessionContent = Take) {
    val segment = content.segments.first { zone == null || it.zone == zone }.let { if (steady) it else it.copy(steady = false) }
    ViolinTheme {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.BottomCenter) {
            AppSheetCard(bottom = if (place == null) null else ({ NoteSheetButtons(place, 0) })) {
                NoteSheetContent(segment, nearCents = content.nearCents)
            }
        }
    }
}

private val Listen = NotePlace(video = false, ready = true) {}

@Preview(name = "Лист ноты · звук: «Слушать это место» главной", locale = "ru", widthDp = 412, heightDp = 520)
@Composable
private fun NoteSheetPreview() = NoteSheet(place = Listen)

@Preview(name = "Лист ноты · видео: в строе, но плавает — «Смотреть это место»", locale = "ru", widthDp = 412, heightDp = 520)
@Composable
private fun NoteSheetVideoPreview() = NoteSheet(zone = Zone.IN_TUNE, steady = false, place = NotePlace(video = true, ready = true) {})

@Preview(name = "Лист ноты · без звука: без кнопки", locale = "ru", widthDp = 412, heightDp = 420)
@Composable
private fun NoteSheetSilentPreview() = NoteSheet(zone = Zone.NEAR, place = null)

@Preview(name = "Лист ноты · пока готовится минусовка: кнопка 0,38 и причина над ней", locale = "ru", widthDp = 412, heightDp = 560)
@Composable
private fun NoteSheetPreparingPreview() = NoteSheet(place = NotePlace(video = false, ready = false) {})

@Preview(name = "Лист ноты · de, 360, шрифт 1,3", locale = "de", fontScale = 1.3f, widthDp = 360, heightDp = 640)
@Composable
private fun NoteSheetGermanPreview() = NoteSheet(place = Listen)

@Preview(name = "Лист ноты · ru, 360, шрифт 1,3: подписи плиток мельче вместе, «длительность» целиком", locale = "ru", fontScale = 1.3f, widthDp = 360, heightDp = 640)
@Composable
private fun NoteSheetRussianLargePreview() = NoteSheet(place = Listen)

@Preview(name = "Лист ноты · ru, 360: «длительность» — 11,5 sp, в одну строку", locale = "ru", widthDp = 360, heightDp = 560)
@Composable
private fun NoteSheetRussianSmallPreview() = NoteSheet(place = Listen)

@Preview(name = "Лист ноты · landscape: не шире 640", locale = "ru", widthDp = 892, heightDp = 412)
@Composable
private fun NoteSheetLandscapePreview() = NoteSheet(place = Listen)
