package com.violinjourney.app.feature.history.components

import com.violinjourney.app.feature.history.HistoryCard
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The line of the card of a recording after «время · длительность» (spec 3.36.5) — its words in the order they stand — and the kind
 * TalkBack hears first.
 */
class RecordLineTest {
    private fun card(piece: Long? = null, audio: Boolean = true, video: Boolean = false, backing: Boolean = false) = HistoryCard(
        id = 1, title = null, startedAtEpochMs = 0, date = LocalDate(2026, 9, 27), durationMs = 125_000,
        pieceId = piece, hasAudio = audio, hasVideo = video, underBacking = backing,
    )

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
    fun `TalkBack hears the kind of every card — a video - a take - a recording`() {
        assertEquals(RecordKind.VIDEO, RecordLine.kindOf(card(piece = 7, video = true)))
        assertEquals(RecordKind.VIDEO, RecordLine.kindOf(card(video = true)))
        assertEquals(RecordKind.TAKE, RecordLine.kindOf(card(piece = 7)))
        assertEquals(RecordKind.TAKE, RecordLine.kindOf(card(piece = 7, backing = true)))
        assertEquals(RecordKind.RECORD, RecordLine.kindOf(card()))
        assertEquals(RecordKind.RECORD, RecordLine.kindOf(card(backing = true)))
    }
}
