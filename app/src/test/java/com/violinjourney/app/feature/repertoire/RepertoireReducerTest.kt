package com.violinjourney.app.feature.repertoire

import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.domain.repertoire.Accidental
import com.violinjourney.app.core.domain.repertoire.KeyMode
import com.violinjourney.app.core.domain.repertoire.MusicalKey
import com.violinjourney.app.core.domain.repertoire.Piece
import com.violinjourney.app.core.domain.repertoire.PieceStatus
import com.violinjourney.app.core.domain.repertoire.SheetPage
import com.violinjourney.app.core.domain.repertoire.Tonic
import com.violinjourney.app.core.domain.session.SessionSummary
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepertoireReducerTest {
    private val zone: ZoneId = ZoneId.of("Europe/Moscow")
    private val today: LocalDate = LocalDate.of(2026, 9, 19)
    private val config = IntonationConfig()

    private fun at(date: String, hour: Int = 12): Long = LocalDate.parse(date).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun piece(id: Long, title: String, status: PieceStatus, updated: String, key: MusicalKey? = null, tempo: Int? = null) =
        Piece(id, title, "", key, tempo, status, "", createdAtEpochMs = at("2026-08-01"), updatedAtEpochMs = at(updated))

    private fun take(id: Long, pieceId: Long?, date: String, score: Int) = SessionSummary(
        id = id, title = null, startedAtEpochMs = at(date), durationMs = 120_000, a4Hz = 440.0, toleranceCents = 8.0, nearCents = 20.0,
        scorePercent = score, nearPercent = 0, offPercent = 0, maeCents = 0.0, biasCents = 0.0, previewZones = emptyList(),
        audioPath = null, pieceId = pieceId,
    )

    private val gMajor = MusicalKey(Tonic.G, Accidental.NATURAL, KeyMode.MAJOR)
    private val pieces = listOf(
        piece(1, "Менуэт", PieceStatus.LEARNING, "2026-09-01", gMajor, 96),
        piece(2, "Концерт", PieceStatus.READING, "2026-09-10"),
        piece(3, "Мелодия", PieceStatus.IN_REPERTOIRE, "2026-08-20"),
        piece(4, "Гавот", PieceStatus.LEARNING, "2026-09-15"),
    )
    private val sessions = listOf(
        take(1, 1, "2026-09-02", 58), take(2, 1, "2026-09-18", 82), take(3, 3, "2026-09-03", 91),
        take(4, 2, "2026-09-14", 48), take(5, null, "2026-09-19", 99),
    )
    private val pages = listOf(SheetPage(11, 1, position = 1, "b.jpg", "b-thumb.jpg"), SheetPage(10, 1, position = 0, "a.jpg", "a-thumb.jpg"))

    private fun state(filter: PieceStatus? = null) =
        RepertoireReducer.stateOf(pieces, pages, sessions, filter, today, zone) { "/sheets/$it" }

    @Test
    fun `pieces stand by their last activity - a take, an edit - freshest first`() {
        // Менуэт: take of the 18th; Гавот: edit of the 15th; Концерт: take of the 14th; Мелодия: take of the 3rd.
        assertEquals(listOf("Менуэт", "Гавот", "Концерт", "Мелодия"), state().cards.map { it.title })
    }

    @Test
    fun `a card shows the day of the latest take and the number of takes, and nothing about the score`() {
        val minuet = state().cards.first()
        assertEquals(LocalDate.of(2026, 9, 18), minuet.lastDate)
        assertEquals(false, minuet.lastDateOtherYear)
        assertEquals(false, minuet.hasBest)
        assertEquals(2, minuet.takes)
        assertEquals("G-dur", minuet.keyName)
        assertEquals(96, minuet.tempoBpm)

        val concerto = state().cards.single { it.title == "Концерт" }
        assertEquals(LocalDate.of(2026, 9, 14), concerto.lastDate)
    }

    @Test
    fun `a piece with a marked take says so, a mark left by a take that is gone does not count`() {
        fun cards(bestOfMinuet: Long?) = RepertoireReducer.stateOf(
            pieces.map { if (it.id == 1L) it.copy(bestTakeId = bestOfMinuet) else it }, pages, sessions, null, today, zone,
        ) { null }.cards
        assertEquals(listOf("Менуэт"), cards(bestOfMinuet = 1).filter { it.hasBest }.map { it.title })
        assertTrue(cards(bestOfMinuet = 404).none { it.hasBest })
        assertTrue("the take of another piece", cards(bestOfMinuet = 3).none { it.hasBest })
    }

    @Test
    fun `a piece without takes has no date and a piece without pages has no thumbnail`() {
        val gavotte = state().cards.single { it.title == "Гавот" }
        assertNull(gavotte.lastDate)
        assertEquals(0, gavotte.takes)
        assertNull(gavotte.thumbPath)
        assertNull(gavotte.keyName)
    }

    @Test
    fun `the thumbnail is the first page by position, whatever order the rows come in`() {
        assertEquals("/sheets/a-thumb.jpg", state().cards.first().thumbPath)
    }

    @Test
    fun `the filter narrows the cards and leaves the total alone`() {
        val learning = state(PieceStatus.LEARNING)
        assertEquals(listOf("Менуэт", "Гавот"), learning.cards.map { it.title })
        assertEquals(4, learning.totalCount)
        assertEquals(PieceStatus.LEARNING, learning.filter)
    }

    @Test
    fun `an empty repertoire and the loading state have no cards`() {
        val empty = RepertoireReducer.stateOf(emptyList(), emptyList(), sessions, null, today, zone) { null }
        assertEquals(0, empty.totalCount)
        assertTrue(empty.cards.isEmpty() && !empty.loading)
        assertTrue(RepertoireReducer.loading(null).loading)
    }
}
