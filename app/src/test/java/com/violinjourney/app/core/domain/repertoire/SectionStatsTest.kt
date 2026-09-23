package com.violinjourney.app.core.domain.repertoire

import com.violinjourney.app.core.domain.repertoire.scale.ScaleKind
import com.violinjourney.app.core.domain.repertoire.scale.ScaleSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SectionStatsTest {
    private val config = RepertoireConfig()
    private val gMajor = ScaleSpec(Tonic.G, Accidental.NATURAL, ScaleKind.MAJOR, 3)

    private fun piece(id: Long, status: PieceStatus, section: PieceSection = PieceSection.PIECES, groupId: Long? = null, scale: ScaleSpec? = null) =
        Piece(id, "p$id", "", null, null, status, "", 1, 1, section = section, groupId = groupId, scale = scale)

    private val groups = listOf(PieceGroup(2, "оркестр", 10), PieceGroup(1, "Двойные ноты", 20))
    private val pieces = listOf(
        piece(1, PieceStatus.READING), piece(2, PieceStatus.LEARNING), piece(3, PieceStatus.IN_REPERTOIRE),
        piece(4, PieceStatus.IN_REPERTOIRE, PieceSection.SCALES, scale = gMajor),
        piece(5, PieceStatus.LEARNING, PieceSection.ETUDES),
        piece(6, PieceStatus.READING, groupId = 1), piece(7, PieceStatus.IN_REPERTOIRE, PieceSection.ETUDES, groupId = 1),
        piece(8, PieceStatus.LEARNING, PieceSection.STROKES, groupId = 404), // its group is gone: back in its built-in section
    )

    @Test
    fun `the four built-in sections come first, the player's own after them by name`() {
        val summaries = SectionStats.summaries(pieces, groups)
        assertEquals(
            listOf<SectionRef>(
                SectionRef.BuiltIn(PieceSection.PIECES), SectionRef.BuiltIn(PieceSection.SCALES), SectionRef.BuiltIn(PieceSection.ETUDES),
                SectionRef.BuiltIn(PieceSection.STROKES), SectionRef.Custom(1), SectionRef.Custom(2),
            ),
            summaries.map { it.ref },
        )
        assertEquals(listOf(null, null, null, null, "Двойные ноты", "оркестр"), summaries.map { it.name })
    }

    @Test
    fun `a section counts what it holds by status, and a group wins over the built-in section`() {
        val counts = SectionStats.summaries(pieces, groups).associate { it.ref to it.count }
        assertEquals(SectionCount(1, 1, 1), counts[SectionRef.BuiltIn(PieceSection.PIECES)])
        assertEquals(SectionCount(0, 0, 1), counts[SectionRef.BuiltIn(PieceSection.SCALES)])
        assertEquals(SectionCount(0, 1, 0), counts[SectionRef.BuiltIn(PieceSection.ETUDES)])
        assertEquals(SectionCount(0, 1, 0), counts[SectionRef.BuiltIn(PieceSection.STROKES)])
        assertEquals(SectionCount(1, 0, 1), counts[SectionRef.Custom(1)])
        assertEquals(SectionCount.EMPTY, counts[SectionRef.Custom(2)])
        assertEquals(pieces.size, counts.values.fold(SectionCount.EMPTY, SectionCount::plus).total)
    }

    @Test
    fun `all learnt is said of a section that has something in it`() {
        assertTrue(SectionCount(0, 0, 3).allLearned)
        assertTrue(!SectionCount.EMPTY.allLearned)
        assertTrue(!SectionCount(1, 0, 3).allLearned)
    }

    @Test
    fun `the same scale is found again, another number of octaves is another scale`() {
        val draft = PieceDraft(title = "G-dur", section = PieceSection.SCALES, scale = gMajor)
        assertEquals(4L, SectionStats.sameScale(pieces, draft)!!.id)
        assertNull(SectionStats.sameScale(pieces, draft, exceptId = 4))
        assertNull(SectionStats.sameScale(pieces, draft.copy(scale = gMajor.copy(octaves = 2))))
        assertNull(SectionStats.sameScale(pieces, PieceDraft(title = "Менуэт")))
    }

    @Test
    fun `a scale lives in its section and nothing else does`() {
        assertNotNull(PieceRules.clean(PieceDraft(title = "G-dur", section = PieceSection.SCALES, scale = gMajor), config))
        assertNull(PieceRules.clean(PieceDraft(title = "G-dur", section = PieceSection.ETUDES, scale = gMajor), config))
        assertNull(PieceRules.clean(PieceDraft(title = "G-dur", section = PieceSection.SCALES, groupId = 1, scale = gMajor), config))
        assertNull(PieceRules.clean(PieceDraft(title = "Не гамма", section = PieceSection.SCALES), config))
        assertNotNull(PieceRules.clean(PieceDraft(title = "Кайзер № 3", section = PieceSection.ETUDES), config))
    }

    @Test
    fun `the name of a section is trimmed and cut, an empty one is no name`() {
        assertEquals("Двойные ноты", PieceRules.cleanGroupName("  Двойные ноты ", config))
        assertEquals(24, PieceRules.cleanGroupName("а".repeat(40), config)!!.length)
        assertNull(PieceRules.cleanGroupName("   ", config))
    }
}
