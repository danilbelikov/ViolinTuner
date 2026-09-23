package com.violinjourney.app.feature.repertoire

import com.violinjourney.app.feature.repertoire.piece.LevelHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelHistoryTest {
    @Test
    fun `the row is silent until its first bar closes`() {
        val history = LevelHistory(size = 4, periodMs = 50)
        assertEquals(listOf(0f, 0f, 0f, 0f), history.add(0, 0.8f))
        assertEquals(listOf(0f, 0f, 0f, 0f), history.add(40, 0.9f))
    }

    @Test
    fun `a bar is the loudest frame of its period and joins the row on the right`() {
        val history = LevelHistory(size = 4, periodMs = 50)
        history.add(0, 0.2f)
        history.add(20, 0.9f)
        history.add(40, 0.4f)
        assertEquals(listOf(0f, 0f, 0f, 0.9f), history.add(50, 0.1f))
        assertEquals(listOf(0f, 0f, 0.9f, 0.1f), history.add(100, 0.5f))
    }

    @Test
    fun `old bars leave on the left and the row keeps its length`() {
        val history = LevelHistory(size = 3, periodMs = 50)
        var row = emptyList<Float>()
        listOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f).forEachIndexed { index, level -> row = history.add(index * 50L, level) }
        assertEquals(listOf(0.3f, 0.4f, 0.5f), row)
    }

    @Test
    fun `between two bars the row is the very same list, so equal states stay equal`() {
        val history = LevelHistory(size = 3, periodMs = 50)
        history.add(0, 0.5f)
        val closed = history.add(50, 0.5f)
        assertSame(closed, history.add(60, 0.7f))
        assertSame(closed, history.add(99, 0.1f))
    }

    @Test
    fun `levels are kept within zero and one, and a reset empties the row`() {
        val history = LevelHistory(size = 2, periodMs = 50)
        history.add(0, 3f)
        assertEquals(listOf(0f, 1f), history.add(50, -2f))
        history.reset()
        assertTrue(history.add(0, 0.5f).all { it == 0f })
    }
}
