package com.violinjourney.app.feature.history

import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.events.KindRef
import com.violinjourney.app.core.domain.events.SessionEvent
import com.violinjourney.app.core.domain.session.SessionSummary
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

class HistoryReducerTest {
    private val moscow = TimeZone.of("Europe/Moscow")
    private val config = IntonationConfig()

    // Thursday 17 September 2026.
    private val today = LocalDate(2026, 9, 17)

    private fun session(id: Long, dateTime: String, score: Int, title: String? = null, bias: Double = -4.0) = SessionSummary(
        id = id, title = title,
        startedAtEpochMs = LocalDateTime.parse(dateTime).toInstant(moscow).toEpochMilliseconds(),
        durationMs = 495_000, a4Hz = 440.0, toleranceCents = 8.0, nearCents = 20.0,
        scorePercent = score, nearPercent = 0, offPercent = 100 - score, maeCents = 5.0, biasCents = bias,
        previewZones = listOf(Zone.IN_TUNE, Zone.NEAR), audioPath = null,
    )

    private val sessions = listOf(
        session(1, "2026-08-12T10:00:00", 62),
        session(2, "2026-08-18T09:40:00", 71),
        session(3, "2026-08-19T09:40:00", 66),
        session(4, "2026-09-13T21:02:00", 79, title = "Этюд Кайзера №3"),
        session(5, "2026-09-16T08:15:00", 84, title = "Гаммы D-dur"),
        session(6, "2026-09-17T07:00:00", 54, bias = 9.0),
    )

    private fun state(filter: HistoryFilter = HistoryFilter.ALL, list: List<SessionSummary> = sessions) =
        HistoryReducer.stateOf(list.shuffled(), filter, today, moscow, config)

    @Test
    fun `cards are newest first with their day and duration — and nothing about the score`() {
        val cards = state().cards
        assertEquals(listOf(6L, 5L, 4L, 3L, 2L, 1L), cards.map { it.id })
        assertEquals(today, cards[0].date)
        assertEquals(LocalDate(2026, 9, 13), cards[2].date)
        assertEquals("Гаммы D-dur", cards[1].title)
        assertNull(cards[0].title)
        assertEquals(495_000, cards[0].durationMs)
        assertFalse(cards[0].otherYear)
    }

    @Test
    fun `the list is grouped by day — newest day first — and today is told apart`() {
        val twoToday = sessions + session(7, "2026-09-17T19:30:00", 70)
        val groups = state(list = twoToday).groups
        assertEquals(listOf("2026-09-17", "2026-09-16", "2026-09-13", "2026-08-19", "2026-08-18", "2026-08-12"), groups.map { it.date.toString() })
        assertEquals(listOf(7L, 6L), groups[0].cards.map { it.id })
        assertEquals(listOf(true, false), groups.take(2).map { it.today })
    }

    @Test
    fun `the days are those of the cards the chip shows`() {
        // «Дубли» leaves out the recording of the 17th: its day gets no header, the two takes keep theirs
        val takes = state(HistoryFilter.TAKES, list = kinds)
        assertEquals(listOf(LocalDate(2026, 9, 16), LocalDate(2026, 9, 15)), takes.groups.map { it.date })
        assertEquals(listOf(12L, 13L), takes.groups.flatMap { g -> g.cards.map { it.id } })
    }

    @Test
    fun `a recording of another year says so`() {
        val old = state(list = listOf(session(1, "2025-09-20T10:00:00", 60))).cards.single()
        assertEquals(true, old.otherYear)
    }

    @Test
    fun `the best take of a piece is marked — wherever it stands`() {
        val list = listOf(session(1, "2026-09-16T10:00:00", 60).copy(pieceId = 7), session(2, "2026-09-17T10:00:00", 90).copy(pieceId = 7))
        val cards = HistoryReducer.stateOf(list, HistoryFilter.ALL, today, moscow, config, bestTakeIds = setOf(1L)).cards
        assertEquals(listOf(2L to false, 1L to true), cards.map { it.id to it.best })
        assertEquals(listOf(true, true), cards.map { it.take })
    }

    @Test
    fun `a take made under a backing carries its sign — the others do not`() {
        val list = listOf(session(1, "2026-09-16T10:00:00", 60).copy(pieceId = 7), session(2, "2026-09-17T10:00:00", 90).copy(pieceId = 7))
        val cards = HistoryReducer.stateOf(list, HistoryFilter.ALL, today, moscow, config, underBackingIds = setOf(2L)).cards
        assertEquals(listOf(2L to true, 1L to false), cards.map { it.id to it.underBacking })
    }

    // The chips by kind (spec 3.36.5): from what a recording stores — its piece and its video.
    private val free = session(11, "2026-09-17T08:00:00", 70).copy(audioPath = "free.m4a")
    private val soundTake = session(12, "2026-09-16T08:00:00", 70).copy(audioPath = "take.m4a", pieceId = 7)
    private val videoTake = session(13, "2026-09-15T08:00:00", 70).copy(audioPath = "video.m4a", pieceId = 7, videoPath = "video.mp4")
    private val kinds = listOf(free, soundTake, videoTake)

    @Test
    fun `the takes are the recordings bound to a piece — the video takes too`() {
        assertEquals(listOf(12L, 13L), state(HistoryFilter.TAKES, list = kinds).cards.map { it.id })
        assertEquals(listOf(11L, 12L, 13L), state(HistoryFilter.ALL, list = kinds).cards.map { it.id })
    }

    @Test
    fun `video is every recording with a video — its file lost too`() {
        // a lost file keeps its name in the row: the recording stays a video, the file may come back with a copy (spec 3.20)
        val lost = session(14, "2026-09-14T08:00:00", 70).copy(pieceId = 7, videoPath = "gone.mp4", audioPath = null)
        val cards = state(HistoryFilter.VIDEO, list = kinds + lost).cards
        assertEquals(listOf(13L, 14L), cards.map { it.id })
        assertEquals(listOf(true, false), cards.map { it.hasAudio })
    }

    @Test
    fun `from Live is what is bound to no piece and has no video`() {
        assertEquals(listOf(11L), state(HistoryFilter.LIVE, list = kinds).cards.map { it.id })
    }

    @Test
    fun `a sound take of a deleted piece stands under «С Live» — its video take only under «Видео»`() {
        // deleting a piece unbinds its takes (spec 3.15): where they were made is not stored
        val unboundSound = soundTake.copy(pieceId = null)
        val unboundVideo = videoTake.copy(pieceId = null)
        val list = listOf(free, unboundSound, unboundVideo)
        assertEquals(listOf(11L, 12L), state(HistoryFilter.LIVE, list = list).cards.map { it.id })
        assertEquals(listOf(13L), state(HistoryFilter.VIDEO, list = list).cards.map { it.id })
        assertEquals(emptyList(), state(HistoryFilter.TAKES, list = list).cards.map { it.id })
    }

    @Test
    fun `an unbound take made under a backing stands under «С Live» and keeps its sign`() {
        // the line of the backing is stored with the recording, not with the piece (spec 3.32)
        val unbound = soundTake.copy(pieceId = null)
        val cards = HistoryReducer.stateOf(listOf(free, unbound), HistoryFilter.LIVE, today, moscow, config, underBackingIds = setOf(12L)).cards
        assertEquals(listOf(11L to false, 12L to true), cards.map { it.id to it.underBacking })
        assertEquals(listOf(false, false), cards.map { it.take })
    }

    @Test
    fun `the strip and the count ignore the filter`() {
        val filtered = state(HistoryFilter.TAKES)
        assertEquals(emptyList(), filtered.cards)
        assertEquals(6, filtered.totalCount)
        // fourteen days ending today: 13, 16 and 17 September have one recording each
        assertEquals(14, filtered.days.size)
        assertEquals(today, filtered.days.last().date)
        assertEquals(listOf("2026-09-13", "2026-09-16", "2026-09-17"), filtered.days.filter { it.count == 1 }.map { it.date.toString() })
        assertEquals(3, filtered.stripTotal)
        assertEquals(config.historyChartMinTop, filtered.chartTop)
        assertEquals(HistoryFilter.TAKES, filtered.filter)
        assertFalse(filtered.loading)
    }

    @Test
    fun `no sessions — and no sessions under the filter — are different states`() {
        val none = state(list = emptyList())
        assertEquals(0, none.totalCount)
        assertEquals(List(14) { 0 }, none.days.map { it.count })

        val noVideo = state(HistoryFilter.VIDEO)
        assertEquals(6, noVideo.totalCount)
        assertEquals(emptyList<HistoryCard>(), noVideo.cards)
    }

    @Test
    fun `select all under a chip takes only what the chip shows`() {
        val live = state(HistoryFilter.LIVE, list = kinds)
        val all = SelectionRules.reduce(Selection(active = true), SelectionIntent.SelectAllClicked, live.cards.map { it.id })
        assertEquals(setOf(11L), all.ids)
        assertTrue(live.copy(selection = all).allSelected)
    }

    @Test
    fun `sessions of the same moment keep a stable order`() {
        val twins = listOf(session(7, "2026-09-17T07:00:00", 50), session(8, "2026-09-17T07:00:00", 60))
        assertEquals(listOf(8L, 7L), state(list = twins).cards.map { it.id })
    }

    @Test
    fun `a take carries the title of its piece — a free session and a take of a deleted piece do not`() {
        val take = session(1, "2026-09-16T18:00:00", 80)
        val sessions = listOf(take.copy(pieceId = 7), take.copy(id = 2), take.copy(id = 3, pieceId = 404))
        val state = HistoryReducer.stateOf(sessions, HistoryFilter.ALL, today, moscow, config, pieceTitles = mapOf(7L to "Менуэт"))
        assertEquals(mapOf(1L to "Менуэт", 2L to null, 3L to null), state.cards.associate { it.id to it.pieceTitle })
    }

    // The recordings of events (spec 3.36.9, plan D36): the sound of a lesson only under «Все», its video under «Видео» too.
    private val lessonSound = session(21, "2026-09-16T17:50:00", 70).copy(audioPath = "lesson.m4a", eventId = 3)
    private val lessonVideo = session(22, "2026-09-16T17:40:00", 70).copy(audioPath = "lesson.mp4", videoPath = "lesson.mp4", eventId = 3)
    private val lesson = SessionEvent(3, title = "", date = LocalDate(2026, 9, 16), kind = KindRef.BuiltIn(BuiltInKind.LESSON), ownName = null)

    @Test
    fun `the sound of an event is not «С Live» - only «Все» shows it - and its video is a video`() {
        val list = kinds + lessonSound + lessonVideo
        assertEquals(listOf(11L), state(HistoryFilter.LIVE, list = list).cards.map { it.id })
        assertEquals(listOf(22L, 13L), state(HistoryFilter.VIDEO, list = list).cards.map { it.id })
        assertEquals(listOf(12L, 13L), state(HistoryFilter.TAKES, list = list).cards.map { it.id }, "a recording of an event is no take")
        assertEquals(listOf(11L, 21L, 22L, 12L, 13L), state(HistoryFilter.ALL, list = list).cards.map { it.id })
    }

    @Test
    fun `a recording of an event carries its event - one with a name of its own or of an event gone keeps its title`() {
        val renamed = lessonSound.copy(id = 23, title = "Этюд на уроке")
        // the event deleted: the name it wore is written into the recording and the link is gone (spec 3.35)
        val orphan = lessonSound.copy(id = 24, title = "Урок · 9 сентября", eventId = null)
        val cards = HistoryReducer.stateOf(listOf(lessonSound, renamed, orphan), HistoryFilter.ALL, today, moscow, config, recordEvents = mapOf(3L to lesson)).cards
        assertEquals(mapOf(21L to lesson, 23L to lesson, 24L to null), cards.associate { it.id to it.event })
        assertEquals(mapOf(21L to null, 23L to "Этюд на уроке", 24L to "Урок · 9 сентября"), cards.associate { it.id to it.title })
    }
}
