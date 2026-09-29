package com.violinjourney.app.feature.repertoire

import com.violinjourney.app.core.domain.repertoire.scale.ScaleKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The line under the name of an element (spec 3.36.4): only what is set, in one order. */
class PieceMetaTest {
    @Test
    fun `a piece says its composer its key and its tempo in that order`() {
        assertEquals(
            listOf(MetaPart.Composer("И. С. Бах"), MetaPart.Key("G-dur"), MetaPart.Tempo(100)),
            PieceMeta.parts(composer = "И. С. Бах", keyName = "G-dur", tempoBpm = 100),
        )
    }

    @Test
    fun `only what is set - no composer no key or no tempo leave no part and no separator behind`() {
        assertEquals(listOf(MetaPart.Key("a-moll"), MetaPart.Tempo(96)), PieceMeta.parts(composer = "", keyName = "a-moll", tempoBpm = 96))
        assertEquals(listOf(MetaPart.Composer("Ф. Зейтц")), PieceMeta.parts(composer = "Ф. Зейтц", keyName = null, tempoBpm = null))
        assertEquals(listOf(MetaPart.Tempo(60)), PieceMeta.parts(composer = "  ", keyName = null, tempoBpm = 60))
        assertTrue(PieceMeta.parts(composer = "", keyName = null, tempoBpm = null).isEmpty(), "nothing set — no line at all")
    }

    @Test
    fun `a scale says its kind and its tempo - its key and octaves are its title`() {
        assertEquals(
            listOf(MetaPart.Kind(ScaleKind.MAJOR), MetaPart.Tempo(80)),
            PieceMeta.parts(composer = "", keyName = "G-dur", tempoBpm = 80, scaleKind = ScaleKind.MAJOR),
        )
        assertEquals(listOf(MetaPart.Kind(ScaleKind.HARMONIC_MINOR)), PieceMeta.parts(composer = "", keyName = "a-moll", tempoBpm = null, scaleKind = ScaleKind.HARMONIC_MINOR))
    }

    @Test
    fun `a bow stroke says only its tempo`() {
        assertEquals(listOf(MetaPart.Tempo(72)), PieceMeta.parts(composer = "Шевчик", keyName = "D-dur", tempoBpm = 72, stroke = true))
        assertTrue(PieceMeta.parts(composer = "", keyName = null, tempoBpm = null, stroke = true).isEmpty())
    }
}
