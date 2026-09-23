package com.violinjourney.app.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteTest {
    @Test
    fun `names are latin with sharps`() {
        assertEquals("G3", Note(55).name)
        assertEquals("C4", Note(60).name)
        assertEquals("C#4", Note(61).name)
        assertEquals("A4", Note(69).name)
        assertEquals("A#4", Note(70).name)
        assertEquals("B4", Note(71).name)
        assertEquals("F#5", Note(78).name)
        assertEquals("E7", Note(100).name)
    }

    @Test
    fun `parts are exposed separately for the big letter and small octave`() {
        val note = Note(78)
        assertEquals('F', note.letter)
        assertTrue(note.isSharp)
        assertEquals(5, note.octave)
        assertFalse(Note(69).isSharp)
    }

    @Test
    fun `octave changes between B and C`() {
        assertEquals(3, Note(59).octave)
        assertEquals(4, Note(60).octave)
    }

    @Test
    fun `whole range has twelve distinct names per octave`() {
        val names = (55..100).map { Note(it).name }
        assertEquals(names.size, names.toSet().size)
    }
}
