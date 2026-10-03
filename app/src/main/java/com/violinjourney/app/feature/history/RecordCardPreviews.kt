package com.violinjourney.app.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.events.KindRef
import com.violinjourney.app.core.domain.events.SessionEvent
import com.violinjourney.app.core.ui.components.AppMenuCard
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.history.components.CardActions
import com.violinjourney.app.feature.history.components.CardMenuItems
import com.violinjourney.app.feature.history.components.RecordCard
import com.violinjourney.app.feature.history.components.RecordPlace
import com.violinjourney.app.feature.history.components.SessionCard
import com.violinjourney.app.feature.history.components.cardMenuDanger
import com.violinjourney.app.feature.history.components.recordCardTitle
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

// The card of a recording of R5 (spec 3.36.5, 5.29 R5; records.html 1) in every look it takes, on the three lists it stands in,
// and its «⋯». Still frames: motion is removed.

private val Moscow = TimeZone.of("Europe/Moscow")

private fun card(
    id: Long,
    at: String,
    seconds: Long,
    title: String? = null,
    piece: String? = null,
    audio: Boolean = true,
    video: Boolean = false,
    best: Boolean = false,
    backing: Boolean = false,
    unbound: Boolean = false,
): HistoryCard {
    val start = LocalDateTime.parse(at)
    return HistoryCard(
        id = id, title = title, startedAtEpochMs = start.toInstant(Moscow).toEpochMilliseconds(), date = start.date,
        durationMs = seconds * 1_000, pieceTitle = piece, pieceId = if (piece != null && !unbound) id else null,
        hasAudio = audio, hasVideo = video, best = best, videoBytes = if (video) 214_000_000 else 0, underBacking = backing,
    )
}

private const val MENUET = "Менуэт соль мажор"
private const val CONCERTO = "Концерт ля минор, 1 ч."

private val Free = card(1, "2026-09-27T18:10:00", 32)
private val Take = card(2, "2026-09-24T08:05:00", 192, piece = CONCERTO)
private val BestTake = card(3, "2026-09-23T08:05:00", 220, piece = CONCERTO, best = true)
private val VideoTake = card(4, "2026-09-27T18:42:00", 125, piece = MENUET, video = true, best = true)
private val Silent = card(5, "2026-09-24T09:15:00", 36, audio = false)
private val SilentTake = card(6, "2026-09-22T19:30:00", 95, piece = CONCERTO, audio = false)
private val BackingTake = card(7, "2026-09-23T08:05:00", 220, piece = CONCERTO, backing = true)
private val BackingVideo = card(8, "2026-09-21T20:14:00", 220, piece = CONCERTO, video = true, backing = true)

/** A take under a backing whose piece was deleted: a recording of its own now, «под минусовку» still (spec 3.32, 3.36.5). */
private val UnboundBacking = card(9, "2026-09-20T09:15:00", 220, piece = CONCERTO, backing = true, unbound = true)
private val Renamed = card(10, "2026-09-19T17:40:00", 305, title = "Для Анны Сергеевны: Концерт ля минор, первая часть, с повторением", piece = CONCERTO)

private val Actions = CardActions(onShare = {}, onSound = {}, onDelete = {}, onBest = {})

@Composable
private fun Cards(content: @Composable () -> Unit) {
    ViolinTheme {
        CompositionLocalProvider(LocalReduceMotion provides true) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) { content() }
        }
    }
}

@Composable
private fun InRecords(card: HistoryCard, selected: Boolean? = null) =
    SessionCard(card, Moscow, onClick = {}, actions = Actions, selected = selected, onLongClick = {})

@Preview(name = "Card · «Записи»: of its own, a take, the best, a video, without sound", widthDp = 412, heightDp = 420, locale = "ru")
@Composable
private fun KindsPreview() = Cards {
    InRecords(Free)
    InRecords(Take)
    InRecords(BestTake)
    InRecords(VideoTake)
    InRecords(Silent)
    InRecords(SilentTake)
}

// The frame of a video (spec 3.38; thumbs.html): a preview has no file to read, so the square stands as it does while its frame is
// read — with the camera; under it a video without a thumbnail keeps its circle, a lost one its ring.
private val FramedVideo = VideoTake.copy(thumbPath = "/preview/menuet-thumb.jpg")
private val LostVideo = card(15, "2026-09-26T07:12:00", 58, piece = "Гамма ре мажор", video = true, audio = false)

@Preview(name = "Card · a video with its frame (the square while it is read), one without a thumbnail, a lost one", widthDp = 412, heightDp = 240, locale = "ru")
@Composable
private fun FramePreview() = Cards {
    InRecords(FramedVideo)
    InRecords(VideoTake)
    InRecords(LostVideo)
}

// The recordings of events (spec 3.36.9; events-views.html 8): named by their event and its date, the word of the kind of the event in the
// place of «дубль» / «видео» — the tile tells a video from a sound; on the screen of the event — no word, a chevron, no «⋯».
private val Concert = SessionEvent(4, "Осенний концерт", LocalDate(2026, 10, 24), KindRef.BuiltIn(BuiltInKind.PERFORMANCE), ownName = null)
private val Lesson = SessionEvent(5, "", LocalDate(2026, 9, 21), KindRef.BuiltIn(BuiltInKind.LESSON), ownName = null)
private val Orchestra = SessionEvent(6, "Сводная", LocalDate(2026, 9, 26), KindRef.Custom(9), ownName = "Оркестр ДК")
private val ConcertSound = card(11, "2026-10-24T19:02:00", 220).copy(event = Concert)
private val ConcertVideo = card(12, "2026-10-24T18:58:00", 212, video = true).copy(event = Concert)
private val LessonSilent = card(13, "2026-09-21T17:20:00", 95, audio = false).copy(event = Lesson)
private val OrchestraSound = card(14, "2026-09-26T11:40:00", 310).copy(event = Orchestra)

@Preview(name = "Card · records of events in «Записи»: «· выступление», a video, a lesson without sound, a kind of one's own", widthDp = 412, heightDp = 340, locale = "ru")
@Composable
private fun EventRecordsPreview() = Cards {
    listOf(ConcertSound, ConcertVideo, LessonSilent, OrchestraSound).forEach { InRecords(it) }
}

@Preview(name = "Card · records on the screen of their event: «· видео», «· без звука», no word of the kind, a chevron", widthDp = 412, heightDp = 260, locale = "ru")
@Composable
private fun EventScreenRecordsPreview() = Cards {
    listOf(ConcertSound, ConcertVideo, LessonSilent).forEach { record ->
        RecordCard(record, title = recordCardTitle(record), start = Formats.timeOfDay(record.startedAtEpochMs, Moscow), onClick = {}, place = RecordPlace.Event)
    }
}

@Preview(name = "Card · records of events, 360, de, font 1.3: the name ends in an ellipsis, the length stays", widthDp = 360, heightDp = 340, locale = "de", fontScale = 1.3f)
@Composable
private fun EventRecordsGermanPreview() = Cards {
    listOf(ConcertSound, ConcertVideo, LessonSilent).forEach { InRecords(it) }
}

@Preview(name = "Card · under a backing: a take, a video, a take of a deleted piece", widthDp = 412, heightDp = 260, locale = "ru")
@Composable
private fun BackingPreview() = Cards {
    InRecords(BackingTake)
    InRecords(BackingVideo)
    InRecords(UnboundBacking)
}

@Preview(name = "Card · picked, not picked, and a take recorded a moment ago", widthDp = 412, heightDp = 260, locale = "ru")
@Composable
private fun PickedPreview() = Cards {
    InRecords(Free, selected = true)
    InRecords(Take, selected = false)
    RecordCard(
        Take, title = Formats.recordDate(Take.date, withYear = false), start = Formats.timeOfDay(Take.startedAtEpochMs, Moscow), onClick = {},
        place = RecordPlace.Takes, actions = Actions, highlighted = true,
    )
}

/** The takes of a piece (spec 3.36.5): named by their date, without «дубль» — «видео», «без звука» and the backing stay. */
@Preview(name = "Card · the takes of a piece: no «дубль» in the line", widthDp = 412, heightDp = 340, locale = "ru")
@Composable
private fun TakesPreview() = Cards {
    listOf(BestTake, Take, VideoTake, SilentTake, BackingTake).forEach { take ->
        val date = Formats.recordDate(take.date, withYear = false)
        RecordCard(
            take, title = take.title ?: date, start = if (take.title == null) Formats.timeOfDay(take.startedAtEpochMs, Moscow) else date,
            onClick = {}, place = RecordPlace.Takes, actions = Actions, onLongClick = {},
        )
    }
}

/** «Записи этого дня» of the sheet of a day (spec 3.36.2, 3.36.5): the cards on the ground of the screen, without «⋯». */
@Preview(name = "Card · the sheet of a day: on #131318, no «⋯»", widthDp = 412, heightDp = 240, locale = "ru")
@Composable
private fun SheetPreview() {
    ViolinTheme {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer).padding(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SessionCard(VideoTake, Moscow, onClick = {}, place = RecordPlace.Sheet)
                SessionCard(Take, Moscow, onClick = {}, place = RecordPlace.Sheet)
                SessionCard(Silent, Moscow, onClick = {}, place = RecordPlace.Sheet)
            }
        }
    }
}

@Preview(name = "Card · 360: a long name and a long line end in an ellipsis, the time and the length stay", widthDp = 360, heightDp = 240, locale = "ru")
@Composable
private fun NarrowPreview() = Cards {
    InRecords(Renamed)
    InRecords(BackingVideo)
    InRecords(SilentTake)
}

@Preview(name = "Card · de, 360, font 1.3", widthDp = 360, heightDp = 300, locale = "de", fontScale = 1.3f)
@Composable
private fun GermanPreview() = Cards {
    InRecords(Renamed)
    InRecords(BackingVideo)
    InRecords(SilentTake)
}

/** A take of last year with a name of its own in the takes of its piece: the date with its year gives way, the length never (5.29 R5). */
private val LastYear = card(11, "2025-09-27T17:40:00", 220, title = "Прогон перед концертом", piece = CONCERTO, audio = false, backing = true)
    .copy(otherYear = true)

@Preview(name = "Card · takes, de, 360, font 1.3: a long date gives way, the length stays", widthDp = 360, heightDp = 200, locale = "de", fontScale = 1.3f)
@Composable
private fun LongDatePreview() = Cards {
    RecordCard(
        LastYear, title = LastYear.title.orEmpty(), start = Formats.recordDate(LastYear.date, withYear = true), onClick = {},
        place = RecordPlace.Takes, actions = Actions,
    )
    RecordCard(
        Take, title = Formats.recordDate(Take.date, withYear = false), start = Formats.timeOfDay(Take.startedAtEpochMs, Moscow), onClick = {},
        place = RecordPlace.Takes, actions = Actions,
    )
}

@Preview(name = "Card · fr, 360", widthDp = 360, heightDp = 240, locale = "fr")
@Composable
private fun FrenchPreview() = Cards {
    InRecords(BackingTake)
    InRecords(BackingVideo)
    InRecords(Silent)
}

@Preview(name = "Card · ja, 360: the sign of the backing in the line", widthDp = 360, heightDp = 200, locale = "ja")
@Composable
private fun JapanesePreview() = Cards {
    InRecords(BackingVideo)
    InRecords(SilentTake)
}

/** A popup does not draw in a preview: the card of the menu at the right, where «⋯» opens it. */
@Composable
private fun Menu(card: HistoryCard) {
    ViolinTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            contentAlignment = Alignment.TopEnd,
        ) {
            AppMenuCard(danger = cardMenuDanger(card, Actions) {}) {
                CardMenuItems(card, Actions) {}
            }
        }
    }
}

@Preview(name = "Menu · «⋯» of a take: the best, share, sound, a line, «Удалить…»", widthDp = 412, heightDp = 300, locale = "ru")
@Composable
private fun TakeMenuPreview() = Menu(Take)

@Preview(name = "Menu · «⋯» of a recording with sound: share, sound, a line, «Удалить…»", widthDp = 412, heightDp = 240, locale = "ru")
@Composable
private fun RecordMenuPreview() = Menu(Free)

@Preview(name = "Menu · «⋯» of a recording without sound: «Удалить…» alone, no line", widthDp = 412, heightDp = 140, locale = "ru")
@Composable
private fun SilentMenuPreview() = Menu(Silent)
