package com.violinjourney.app.feature.events

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
import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.events.CalendarEvent
import com.violinjourney.app.core.domain.events.EventsConfig
import com.violinjourney.app.core.domain.events.KindRef
import com.violinjourney.app.core.domain.events.KindRules
import com.violinjourney.app.core.domain.events.PerformanceRow
import com.violinjourney.app.core.domain.events.Performances
import com.violinjourney.app.core.domain.repertoire.Piece
import com.violinjourney.app.core.domain.repertoire.PieceStatus
import com.violinjourney.app.core.domain.session.SessionSummary
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.events.performances.PerformanceCard
import com.violinjourney.app.feature.events.performances.PerformancesScreen
import com.violinjourney.app.feature.events.performances.PerformancesState
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

// «Выступления» (spec 3.36.9; events-views.html 7, practice-sheets.html 11) on the data of the mockups: Sunday 27 September 2026, 18:42 —
// «Осенний концерт» on 24 October in the small hall of the music school, the qualifying round on 21 November all day; gone by — the
// academic concert of 13 September with its video, the exam of May with two recordings, the New Year concert of 2025. The rows are what
// the model makes of them (`Performances.rows`). Over the tabs: the status bar over it, no bar of the tabs. The thumbnail of a video is a
// file of a phone: a preview shows its tile and its sign.

private object PerformancesSample {
    val zone: TimeZone = TimeZone.of("Europe/Moscow")
    val config = EventsConfig()
    val now = LocalDateTime(2026, 9, 27, 18, 42).toInstant(zone)
    val look = KindRules.lookOf(KindRef.BuiltIn(BuiltInKind.PERFORMANCE), emptyList(), config)
    private val performance = KindRef.BuiltIn(BuiltInKind.PERFORMANCE)

    private fun event(id: Long, date: LocalDate, start: Int?, duration: Int?, title: String, place: String) =
        CalendarEvent(id, performance, date, start, duration, title, place, notes = "", seriesId = null, detached = false, createdAtEpochMs = id)

    private fun piece(id: Long, title: String, composer: String) =
        Piece(id, title, composer, key = null, tempoBpm = null, status = PieceStatus.LEARNING, notes = "", createdAtEpochMs = id, updatedAtEpochMs = id)

    private fun recording(id: Long, eventId: Long, at: LocalDateTime, video: Boolean) = SessionSummary(
        id = id, title = null, startedAtEpochMs = at.toInstant(zone).toEpochMilliseconds(), durationMs = 220_000, a4Hz = 440.0, toleranceCents = 8.0,
        nearCents = 20.0, scorePercent = 80, nearPercent = 15, offPercent = 5, maeCents = 4.0, biasCents = 1.0, previewZones = listOf(Zone.IN_TUNE),
        audioPath = "$id.m4a", videoPath = if (video) "$id.mp4" else null, eventId = eventId,
    )

    val events = listOf(
        event(1, LocalDate(2026, 10, 24), 18 * 60 + 30, 90, "Осенний концерт", "Малый зал музыкальной школы"),
        event(2, LocalDate(2026, 11, 21), null, null, "Отборочный тур Международного конкурса юных скрипачей имени Л. Когана", "Москва, Малый зал консерватории"),
        event(3, LocalDate(2026, 9, 13), 15 * 60, 60, "Академический концерт", "Малый зал музыкальной школы"),
        event(4, LocalDate(2026, 5, 18), 11 * 60, 45, "Экзамен, 4 класс", "Концертный зал"),
        event(5, LocalDate(2025, 12, 27), 16 * 60, 90, "Новогодний концерт", "ДК «Строитель»"),
    )
    private val pieces = listOf(
        piece(11, "Концерт ля минор, 1 ч.", "Вивальди"),
        piece(12, "Мелодия", "Чайковский"),
        piece(13, "Концерт № 2, 3 ч.", "Зейтц"),
        piece(14, "Юмореска", "Дворжак"),
        piece(15, "Чардаш", "Монти"),
    )
    private val programs = mapOf(1L to listOf(11L, 12L), 2L to listOf(11L), 3L to listOf(11L, 12L), 4L to listOf(13L, 14L), 5L to listOf(15L))
    private val sessions = listOf(
        recording(21, 3, LocalDateTime(2026, 9, 13, 15, 40), video = true),
        recording(22, 4, LocalDateTime(2026, 5, 18, 11, 10), video = false),
        recording(23, 4, LocalDateTime(2026, 5, 18, 11, 20), video = false),
        // the concert of today, going on: two numbers recorded already
        recording(24, 6, LocalDateTime(2026, 9, 27, 17, 40), video = false),
        recording(25, 6, LocalDateTime(2026, 9, 27, 18, 5), video = false),
    )

    fun stateOf(events: List<CalendarEvent> = this.events): PerformancesState {
        val rows = Performances.rows(events, programs, pieces, sessions, now, zone, config)
        fun card(row: PerformanceRow) = PerformanceCard(row, thumbPath = row.lastVideo?.let { "$it-thumb.jpg" })
        return PerformancesState(loading = false, ahead = rows.ahead.map(::card), past = rows.past.map(::card), look = look)
    }

    /** A concert of today until it is over — from 17:30 till 19:30, two numbers recorded: «сегодня» on its chip, «2 записи» at its end. */
    val tonight = events + event(6, LocalDate(2026, 9, 27), 17 * 60 + 30, 120, "Отчётный концерт класса", "Большой зал")
}

/** The status bar of the phone over the screen of the preview. */
private val StatusBar = 24.dp

@Composable
private fun Screen(state: PerformancesState) {
    ViolinTheme {
        CompositionLocalProvider(LocalReduceMotion provides true) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(top = StatusBar)) {
                PerformancesScreen(state = state, onIntent = {})
            }
        }
    }
}

private val Listed = PerformancesSample.stateOf()
private val Empty = PerformancesState(loading = false, look = PerformancesSample.look)

@Preview(name = "Выступления: «Впереди» — через 27 дн. и весь день, «Прошли» — видео, «2 записи», прошлый год", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun ListPreview() = Screen(Listed)

@Preview(name = "Выступления: концерт сегодня — чип «сегодня» до конца концерта, «2 записи» справа", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun TonightPreview() = Screen(PerformancesSample.stateOf(PerformancesSample.tonight))

@Preview(name = "Выступления: пусто — плитка знака, слова и та же кнопка", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun EmptyPreview() = Screen(Empty)

@Preview(name = "Выступления: загрузка — шапка и пустая зона той же высоты", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun LoadingPreview() = Screen(PerformancesState(loading = true, look = PerformancesSample.look))

@Preview(name = "Выступления · landscape 892 × 412: колонка 560 по центру, зона той же ширины, строки 72", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun LandscapePreview() = Screen(Listed)

@Preview(name = "Выступления · landscape 640 × 360: кнопка 48", locale = "ru", device = "spec:width=640dp,height=360dp")
@Composable
private fun LandscapeSmallPreview() = Screen(Listed)

@Preview(name = "Выступления · landscape 892 × 412, пусто: плитка 64, заголовок 20", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun LandscapeEmptyPreview() = Screen(Empty)

@Preview(name = "Выступления · landscape 640 × 360, пусто", locale = "ru", device = "spec:width=640dp,height=360dp")
@Composable
private fun LandscapeSmallEmptyPreview() = Screen(Empty)

@Preview(name = "Выступления · 360 × 640, шрифт 1,3: чип и «2 записи» целы, название — многоточием", locale = "ru", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun SmallLargePreview() = Screen(Listed)

@Preview(name = "Выступления · de, 360, шрифт 1,3", locale = "de", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun GermanLargePreview() = Screen(Listed)

@Preview(name = "Выступления · fr, 360", locale = "fr", device = "spec:width=360dp,height=640dp")
@Composable
private fun FrenchPreview() = Screen(Listed)

@Preview(
    name = "Выступления · fr, 360, шрифт 1,3: концерт сегодня с записями — «2 enregistrements» под строками, чип «aujourd'hui» цел",
    locale = "fr", fontScale = 1.3f, device = "spec:width=360dp,height=640dp",
)
@Composable
private fun FrenchLargeTonightPreview() = Screen(PerformancesSample.stateOf(PerformancesSample.tonight))
