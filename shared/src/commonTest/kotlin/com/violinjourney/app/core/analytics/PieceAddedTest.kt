package com.violinjourney.app.core.analytics

import com.violinjourney.app.core.domain.repertoire.PieceSection
import kotlin.test.assertEquals
import kotlin.test.Test

class PieceAddedTest {
    @Test
    fun `a built-in section goes by its key from the spec`() {
        assertEquals("pieces", PieceAdded(PieceSection.PIECES, ownSection = false, scale = false).params["section"])
        assertEquals("scales", PieceAdded(PieceSection.SCALES, ownSection = false, scale = true).params["section"])
        assertEquals("etudes", PieceAdded(PieceSection.ETUDES, ownSection = false, scale = false).params["section"])
        assertEquals("strokes", PieceAdded(PieceSection.STROKES, ownSection = false, scale = false).params["section"])
    }

    @Test
    fun `a section of the player's own is custom whatever it is kept under`() {
        assertEquals("custom", PieceAdded(PieceSection.PIECES, ownSection = true, scale = false).params["section"])
    }
}
