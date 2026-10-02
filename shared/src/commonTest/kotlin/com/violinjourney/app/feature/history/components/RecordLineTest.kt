package com.violinjourney.app.feature.history.components

import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.events.KindRef
import com.violinjourney.app.core.domain.events.SessionEvent
import com.violinjourney.app.feature.history.HistoryCard
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The line of the card of a recording after «время · длительность» (spec 3.36.5) — its words in the order they stand — and the kind
 * TalkBack hears first.
 */
class RecordLineTest {
    private fun card(piece: Long? = null, audio: Boolean = true, video: Boolean = false, backing: Boolean = false, event: SessionEvent? = null) = HistoryCard(
        id = 1, title = null, startedAtEpochMs = 0, date = LocalDate(2026, 9, 27), durationMs = 125_000,
        pieceId = piece, hasAudio = audio, hasVideo = video, underBacking = backing, event = event,
    )

    private val concert = SessionEvent(4, "Осенний концерт", LocalDate(2026, 10, 24), KindRef.BuiltIn(BuiltInKind.PERFORMANCE), null)

    /** A kind of one's own: its word is its name as written — the composable says it; the line only knows it stands there. */
    private val orchestra = SessionEvent(5, "", LocalDate(2026, 10, 25), KindRef.Custom(9), "Оркестр ДК")

    private fun words(card: HistoryCard, place: RecordPlace = RecordPlace.Records) = RecordLine.wordsOf(card, place)

    @Test
    fun `a free recording with sound says nothing of itself`() {
        for (place in RecordPlace.entries) assertEquals(emptyList(), words(card(), place), "$place")
    }

    @Test
    fun `a take says «дубль» in «Записи» and on a sheet — not in the takes of its piece`() {
        val take = card(piece = 7)
        assertEquals(listOf(LineWord.TAKE), words(take))
        assertEquals(listOf(LineWord.TAKE), words(take, RecordPlace.Sheet))
        assertEquals(emptyList(), words(take, RecordPlace.Takes))
    }

    @Test
    fun `a video take says «видео» everywhere and never «дубль»`() {
        val video = card(piece = 7, video = true)
        for (place in RecordPlace.entries) assertEquals(listOf(LineWord.VIDEO), words(video, place), "$place")
        // bound to nothing, a video is still one
        assertEquals(listOf(LineWord.VIDEO), words(card(video = true)))
    }

    @Test
    fun `«без звука» comes after the kind`() {
        assertEquals(listOf(LineWord.NO_SOUND), words(card(audio = false)))
        assertEquals(listOf(LineWord.TAKE, LineWord.NO_SOUND), words(card(piece = 7, audio = false)))
        assertEquals(listOf(LineWord.NO_SOUND), words(card(piece = 7, audio = false), RecordPlace.Takes))
        assertEquals(listOf(LineWord.VIDEO, LineWord.NO_SOUND), words(card(piece = 7, video = true, audio = false)))
    }

    @Test
    fun `under a backing the sign stands in the place of «дубль» — last`() {
        assertEquals(listOf(LineWord.BACKING), words(card(piece = 7, backing = true)))
        assertEquals(listOf(LineWord.BACKING), words(card(piece = 7, backing = true), RecordPlace.Takes))
    }

    @Test
    fun `a video under a backing says «видео» first and the sign last`() {
        assertEquals(listOf(LineWord.VIDEO, LineWord.BACKING), words(card(piece = 7, video = true, backing = true)))
    }

    @Test
    fun `«без звука» of a recording under a backing stands before the sign`() {
        // a take under a backing whose sound is lost: «18:42 · 3:40 · без звука · [знак] под минусовку»
        assertEquals(listOf(LineWord.NO_SOUND, LineWord.BACKING), words(card(piece = 7, audio = false, backing = true)))
        assertEquals(listOf(LineWord.VIDEO, LineWord.NO_SOUND, LineWord.BACKING), words(card(piece = 7, video = true, audio = false, backing = true)))
    }

    @Test
    fun `a take of a deleted piece keeps the sign of its backing without «дубль»`() {
        assertEquals(listOf(LineWord.BACKING), words(card(piece = null, backing = true)))
    }

    @Test
    fun `a recording of an event says the kind of its event in the place of «видео» - in «Записи» and on the sheet of a day`() {
        // spec 3.36.9: «19:02 · 3:40 · выступление» — the tile tells a video from a sound
        for (place in listOf(RecordPlace.Records, RecordPlace.Sheet)) {
            assertEquals(listOf(LineWord.EVENT_KIND), words(card(event = concert), place), "$place")
            assertEquals(listOf(LineWord.EVENT_KIND), words(card(video = true, event = concert), place), "$place: a video too")
            assertEquals(listOf(LineWord.EVENT_KIND), words(card(event = orchestra), place), "$place: a kind of one's own")
        }
    }

    @Test
    fun `on the screen of its event a recording says no word of its kind - «видео» as in the takes of a piece`() {
        assertEquals(emptyList(), words(card(event = concert), RecordPlace.Event))
        assertEquals(listOf(LineWord.VIDEO), words(card(video = true, event = concert), RecordPlace.Event))
    }

    @Test
    fun `«без звука» of a recording of an event comes after the word of its kind`() {
        assertEquals(listOf(LineWord.EVENT_KIND, LineWord.NO_SOUND), words(card(video = true, audio = false, event = concert)))
        assertEquals(listOf(LineWord.VIDEO, LineWord.NO_SOUND), words(card(video = true, audio = false, event = concert), RecordPlace.Event))
    }

    @Test
    fun `TalkBack hears the tile of a recording of an event - «звук» or «видео»`() {
        assertEquals(RecordKind.SOUND, RecordLine.kindOf(card(event = concert)))
        assertEquals(RecordKind.VIDEO, RecordLine.kindOf(card(video = true, event = concert)))
        assertEquals(RecordKind.RECORD, RecordLine.kindOf(card()), "a recording of its own is still «запись»")
    }

    @Test
    fun `TalkBack hears the kind of every card — a video - a take - a recording`() {
        assertEquals(RecordKind.VIDEO, RecordLine.kindOf(card(piece = 7, video = true)))
        assertEquals(RecordKind.VIDEO, RecordLine.kindOf(card(video = true)))
        assertEquals(RecordKind.TAKE, RecordLine.kindOf(card(piece = 7)))
        assertEquals(RecordKind.TAKE, RecordLine.kindOf(card(piece = 7, backing = true)))
        assertEquals(RecordKind.RECORD, RecordLine.kindOf(card()))
        assertEquals(RecordKind.RECORD, RecordLine.kindOf(card(backing = true)))
    }
}
