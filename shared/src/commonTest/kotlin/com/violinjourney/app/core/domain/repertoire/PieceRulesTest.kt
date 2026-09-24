package com.violinjourney.app.core.domain.repertoire

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class PieceRulesTest {
    private val config = RepertoireConfig()

    @Test
    fun `a piece without a title cannot be saved`() {
        assertNull(PieceRules.clean(PieceDraft(title = ""), config))
        assertNull(PieceRules.clean(PieceDraft(title = "  \n "), config))
    }

    @Test
    fun `texts lose their edges and are cut to their lengths`() {
        val clean = PieceRules.clean(
            PieceDraft(title = "  Менуэт  ", composer = " И. С. Бах ", notes = "\n Такты 9–12:\nне спешить \n\n"),
            config,
        )!!
        assertEquals("Менуэт", clean.title)
        assertEquals("И. С. Бах", clean.composer)
        assertEquals("Такты 9–12:\nне спешить", clean.notes, "the line break inside stays")

        val long = PieceRules.clean(PieceDraft(title = "а".repeat(200), composer = "б".repeat(200), notes = "в".repeat(5_000)), config)!!
        assertEquals(80, long.title.length)
        assertEquals(60, long.composer.length)
        assertEquals(2_000, long.notes.length)
    }

    @Test
    fun `a cut never leaves a space at the end`() {
        val title = "а".repeat(79) + " хвост"
        assertEquals("а".repeat(79), PieceRules.clean(PieceDraft(title = title), config)!!.title)
    }

    @Test
    fun `tempo is optional and kept within its range`() {
        assertNull(PieceRules.clean(PieceDraft(title = "x"), config)!!.tempoBpm)
        assertEquals(20, PieceRules.clean(PieceDraft(title = "x", tempoBpm = 5), config)!!.tempoBpm)
        assertEquals(300, PieceRules.clean(PieceDraft(title = "x", tempoBpm = 999), config)!!.tempoBpm)
        assertEquals(96, PieceRules.clean(PieceDraft(title = "x", tempoBpm = 96), config)!!.tempoBpm)
    }

    @Test
    fun `the first step of an empty tempo lands on a walking pace — then steps go by their size`() {
        assertEquals(96, PieceRules.stepTempo(null, +1, config))
        assertEquals(96, PieceRules.stepTempo(null, -5, config))
        assertEquals(97, PieceRules.stepTempo(96, +1, config))
        assertEquals(91, PieceRules.stepTempo(96, -5, config))
        assertEquals(300, PieceRules.stepTempo(299, +5, config))
        assertEquals(20, PieceRules.stepTempo(20, -1, config))
    }

    @Test
    fun `a new piece is being read — and a draft of a piece is that piece`() {
        assertEquals(PieceStatus.READING, PieceDraft().status)
        val key = MusicalKey(Tonic.G, Accidental.NATURAL, KeyMode.MAJOR)
        val piece = Piece(7, "Менуэт", "Бах", key, 96, PieceStatus.LEARNING, "ноты", 1, 2)
        assertEquals(PieceDraft("Менуэт", "Бах", key, 96, PieceStatus.LEARNING, "ноты"), PieceRules.draftOf(piece))
    }
}
