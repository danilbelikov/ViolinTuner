package com.violinjourney.app.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.events.CalendarEvent
import com.violinjourney.app.core.domain.events.EventsConfig
import com.violinjourney.app.core.domain.events.KindRef
import com.violinjourney.app.core.domain.events.KindRules
import com.violinjourney.app.core.domain.events.PerformancesLine
import com.violinjourney.app.core.domain.session.SessionSummary
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.history.components.CardActions
import com.violinjourney.app.navigation.AppBottomBar
import com.violinjourney.app.navigation.TopLevelDestination
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

// The tab «Записи» of R5 (spec 3.36.5; records.html 1) on the data of the mockup — Sunday 27 September 2026, twelve recordings in
// two weeks: a video take of «Менуэт соль мажор» marked the best, free recordings (one without sound), takes of «Концерт ля минор»
// (one under the backing), a take of a deleted piece under the backing. Each screen as a phone shows it: the status bar over it, the
// bar of the tabs under it (compact in landscape). Still frames: motion is removed. The row «Выступления» of R9 (spec 3.36.9;
// events-views.html 8, practice-sheets.html 11) stands first in every tab read — «Осенний концерт» in 27 days, three over — and in
// its other two captions below.

private val Moscow = TimeZone.of("Europe/Moscow")
private val Today = LocalDate(2026, 9, 27)
private const val MENUET = 1L
private const val CONCERTO = 2L

private fun session(id: Long, at: String, seconds: Long, piece: Long? = null, audio: Boolean = true, video: Boolean = false) = SessionSummary(
    id = id, title = null,
    startedAtEpochMs = LocalDateTime.parse(at).toInstant(Moscow).toEpochMilliseconds(),
    durationMs = seconds * 1_000, a4Hz = 440.0, toleranceCents = 8.0, nearCents = 20.0,
    scorePercent = 82, nearPercent = 12, offPercent = 6, maeCents = 7.4, biasCents = -6.0,
    previewZones = listOf(Zone.IN_TUNE, Zone.NEAR), audioPath = if (audio) "take-$id.m4a" else null, pieceId = piece,
    videoPath = if (video) "take-$id.mp4" else null,
)

private val Sessions = listOf(
    session(1, "2026-09-27T18:42:00", 125, piece = MENUET, video = true),
    session(2, "2026-09-27T18:10:00", 32),
    session(3, "2026-09-25T20:30:00", 244, piece = CONCERTO),
    session(4, "2026-09-24T09:15:00", 36, audio = false),
    session(5, "2026-09-24T08:05:00", 192, piece = CONCERTO),
    session(6, "2026-09-23T08:05:00", 220, piece = CONCERTO),
    session(7, "2026-09-21T19:00:00", 95),
    session(8, "2026-09-21T18:20:00", 160, piece = MENUET),
    session(9, "2026-09-19T09:15:00", 220),
    session(10, "2026-09-17T17:00:00", 60),
    session(11, "2026-09-14T18:00:00", 120, piece = MENUET),
    session(12, "2026-09-14T17:30:00", 45),
    session(13, "2025-11-02T12:00:00", 300, piece = MENUET),
)

private val Titles = mapOf(MENUET to "Менуэт соль мажор", CONCERTO to "Концерт ля минор, 1 ч.")

/** «Концерт» under the backing (6); the 9th — a take under the backing of a piece deleted since, a recording of its own now. */
private val UnderBacking = setOf(6L, 9L)
private val Best = setOf(1L, 6L)

private val Concert = CalendarEvent(
    id = 1, kind = KindRef.BuiltIn(BuiltInKind.PERFORMANCE), date = LocalDate(2026, 10, 24), startMinutes = 18 * 60 + 30, durationMinutes = 90,
    title = "Осенний концерт", place = "Малый зал музыкальной школы", notes = "", seriesId = null, detached = false, createdAtEpochMs = 1,
)
private val PerformanceLook = KindRules.lookOf(KindRef.BuiltIn(BuiltInKind.PERFORMANCE), emptyList(), EventsConfig())

/** The row «Выступления» saying [line] (spec 3.36.9). */
private fun rowOf(line: PerformancesLine) = HistoryPerformances(line, PerformanceLook, Today)

/** «Осенний концерт» / «через 27 дней · 3 прошло». */
private val AheadRow = rowOf(PerformancesLine.Ahead(Concert, days = 27, pastCount = 3))

private fun stateOf(sessions: List<SessionSummary> = Sessions, filter: HistoryFilter = HistoryFilter.ALL, row: HistoryPerformances = AheadRow) =
    HistoryReducer.stateOf(sessions, filter, Today, Moscow, IntonationConfig(), pieceTitles = Titles, bestTakeIds = Best, underBackingIds = UnderBacking)
        .let { state -> state.copy(cards = state.cards.map { if (it.hasVideo) it.copy(videoBytes = 214_000_000) else it }, performances = row) }

private val Actions = CardActions(onShare = {}, onSound = {}, onDelete = {}, onBest = {})

/** The status bar of the phone over the screen of the preview. */
private val StatusBar = 24.dp

@Composable
private fun Tab(state: HistoryState) {
    ViolinTheme {
        CompositionLocalProvider(LocalReduceMotion provides true) {
            BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                val landscape = maxWidth > maxHeight
                Column(Modifier.fillMaxSize().padding(top = StatusBar)) {
                    HistoryScreen(state = state, onIntent = {}, modifier = Modifier.weight(1f), zone = Moscow, cardActions = Actions)
                    AppBottomBar(TopLevelDestination.HISTORY, onSelect = {}, compact = landscape)
                }
            }
        }
    }
}

private val Filled = stateOf()

/** Picking (spec 3.18, 3.36.5): the bar over the title, the strip and the chips dimmed, «⋯» gone from every card. */
private val Picking = Filled.copy(selection = Selection(active = true, ids = setOf(2L, 4L)))
private val Loading = HistoryReducer.loading(HistoryFilter.ALL)
private val NoRecords = stateOf(emptyList(), row = rowOf(PerformancesLine.None))

@Preview(name = "Записи · обычное: «Записи · Выбрать», полоска, чипы, дни", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun TabPreview() = Tab(Filled)

@Preview(name = "Записи · выбор: панель на месте заголовка, полоска и чипы 0,38", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun PickingPreview() = Tab(Picking)

@Preview(name = "Записи · загрузка: один заголовок", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun LoadingPreview() = Tab(Loading)

@Preview(name = "Записи · пусто совсем: «Открыть Live» в нижней зоне", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun NothingPreview() = Tab(NoRecords)

@Preview(name = "Записи · пусто под «Дубли»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun NoTakesPreview() = Tab(stateOf(Sessions.filter { it.pieceId == null }, HistoryFilter.TAKES))

@Preview(name = "Записи · пусто под «Видео»: знак видео", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun NoVideoPreview() = Tab(stateOf(Sessions.filter { it.videoPath == null }, HistoryFilter.VIDEO))

@Preview(name = "Записи · пусто под «С Live»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun NoLivePreview() = Tab(stateOf(Sessions.filter { it.pieceId != null }, HistoryFilter.LIVE))

@Preview(name = "Записи · под «Дубли»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun TakesPreview() = Tab(stateOf(filter = HistoryFilter.TAKES))

@Preview(name = "Записи · 360 × 640", locale = "ru", device = "spec:width=360dp,height=640dp")
@Composable
private fun SmallPreview() = Tab(Filled)

@Preview(name = "Записи · 360 × 640, пусто совсем: кнопка и панель видны", locale = "ru", device = "spec:width=360dp,height=640dp")
@Composable
private fun SmallNothingPreview() = Tab(NoRecords)

@Preview(name = "Записи · fr, 360, шрифт 1,3: чипы переносятся, заголовок с многоточием, «Sélectionner» целиком", locale = "fr", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun FrenchLargePreview() = Tab(Filled)

@Preview(name = "Записи · de, 360, шрифт 1,3", locale = "de", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun GermanLargePreview() = Tab(Filled)

@Preview(name = "Записи · fr, 360, выбор: «Sélectionner tout» не сжимается", locale = "fr", device = "spec:width=360dp,height=640dp")
@Composable
private fun FrenchPickingPreview() = Tab(Picking)

@Preview(name = "Записи · landscape 892 × 412: слева 360 — заголовок, полоска, чипы; справа список", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun LandscapePreview() = Tab(Filled)

@Preview(name = "Записи · landscape, выбор: панель во всю ширину", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun LandscapePickingPreview() = Tab(Picking)

@Preview(name = "Записи · landscape, пусто: «Открыть Live» по центру правой колонки", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun LandscapeNothingPreview() = Tab(NoRecords)

@Preview(name = "Записи · landscape, пусто под «Видео»", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun LandscapeNoVideoPreview() = Tab(stateOf(Sessions.filter { it.videoPath == null }, HistoryFilter.VIDEO))

@Preview(name = "Записи · landscape 640 × 360: левая колонка — половина", locale = "ru", device = "spec:width=640dp,height=360dp")
@Composable
private fun LandscapeSmallPreview() = Tab(Filled)

@Preview(name = "Записи · landscape 600 × 360 (640 с вырезом сбоку): время и длительность видны", locale = "ru", device = "spec:width=600dp,height=360dp")
@Composable
private fun LandscapeCutoutPreview() = Tab(Filled)

@Preview(name = "Записи · landscape 640 × 360, пусто: кнопка 48", locale = "ru", device = "spec:width=640dp,height=360dp")
@Composable
private fun LandscapeSmallNothingPreview() = Tab(NoRecords)

@Preview(name = "Записи · строка «Выступления»: только прошедшие — «3 прошло · последнее 24 октября»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun PerformancesOnlyPastPreview() = Tab(stateOf(row = rowOf(PerformancesLine.OnlyPast(count = 3, lastDate = LocalDate(2026, 9, 13)))))

@Preview(name = "Записи · строка «Выступления»: ни одного — «Концерты, экзамены — с записями»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun PerformancesNonePreview() = Tab(stateOf(row = rowOf(PerformancesLine.None)))

@Preview(name = "Записи · строка «Выступления»: концерт сегодня, без прошедших — «сегодня»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun PerformancesTodayPreview() = Tab(stateOf(row = rowOf(PerformancesLine.Ahead(Concert.copy(date = Today), days = 0, pastCount = 0))))

@Preview(name = "Записи · строка «Выступления», de, 360, шрифт 1,3: длинное название — многоточием, срок — целиком", locale = "de", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun PerformancesGermanLargePreview() = Tab(
    stateOf(
        row = rowOf(PerformancesLine.Ahead(Concert.copy(title = "Отборочный тур Международного конкурса юных скрипачей имени Л. Когана"), days = 55, pastCount = 4)),
    ),
)

@Preview(name = "Записи · строка «Выступления», fr, 360, шрифт 1,3", locale = "fr", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun PerformancesFrenchLargePreview() = Tab(stateOf())
