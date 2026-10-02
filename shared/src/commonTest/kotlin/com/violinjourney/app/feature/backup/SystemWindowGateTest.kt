package com.violinjourney.app.feature.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * One system window of a copy for a press (spec 3.36.8, 5.29 R8): a picker holds every other window until it answers — a pick or a
 * cancel alike — a sheet holds them for the time of a double tap, a picker the platform sees gone frees them. Time only from the clock
 * the gate is given.
 */
class SystemWindowGateTest {
    private var clockMs = 1_000L
    private val launched = mutableListOf<String>()
    private val answers = mutableListOf<String?>()

    private fun gate(pickerGone: () -> Boolean = { false }) = SystemWindowGate(now = { clockMs }, pickerGone = pickerGone)

    @Test
    fun `a picker holds every other window until it answers`() {
        val gate = gate()
        val answer = gate.answering<String> { answers += it }
        assertTrue(gate.picker { launched += "place" }, "the first tap puts the picker up")
        assertFalse(gate.picker { launched += "place again" }, "the second tap of the same button puts up nothing")
        assertFalse(gate.picker { launched += "copy" }, "nor does another picker")
        assertFalse(gate.sheet { launched += "sheet" }, "nor a sheet")
        clockMs += 600_000
        assertFalse(gate.picker { launched += "ten minutes on" }, "a picker holds for as long as it is up: it is not timed out")
        answer("content://downloads/copy.zip")
        assertTrue(gate.picker { launched += "after the answer" }, "an answer frees the gate")
        assertEquals(listOf("place", "after the answer"), launched)
        assertEquals(listOf<String?>("content://downloads/copy.zip"), answers)
    }

    @Test
    fun `a picker closed without a pick answers as a pick does`() {
        val gate = gate()
        val answer = gate.answering<String> { answers += it }
        gate.picker { launched += "copy" }
        answer(null)
        assertEquals(listOf<String?>(null), answers, "the cancel is handed on")
        assertTrue(gate.picker { launched += "copy again" }, "and frees the gate as a pick does")
        assertEquals(listOf("copy", "copy again"), launched)
    }

    @Test
    fun `the gate is free by the time the answer is handed on`() {
        val gate = gate()
        var freeOnAnswer: Boolean? = null
        val answer = gate.answering<String> { freeOnAnswer = gate.sheet { launched += "sheet on the answer" } }
        gate.picker { launched += "place" }
        answer("content://downloads/copy.zip")
        assertEquals(true, freeOnAnswer, "whatever the answer starts may put up a window of its own")
        assertEquals(listOf("place", "sheet on the answer"), launched)
    }

    @Test
    fun `a sheet holds every window for the time of a double tap and no longer`() {
        val gate = gate()
        assertTrue(gate.sheet { launched += "chooser" })
        clockMs += 799
        assertFalse(gate.sheet { launched += "chooser at 799 ms" }, "the second tap of a double tap comes within 800 ms")
        assertFalse(gate.picker { launched += "picker at 799 ms" }, "a picker too waits for the hold")
        clockMs += 1
        assertTrue(gate.sheet { launched += "chooser at 800 ms" }, "the hold is 800 ms")
        clockMs += 800
        assertTrue(gate.picker { launched += "picker after the hold" })
        assertEquals(listOf("chooser", "chooser at 800 ms", "picker after the hold"), launched)
    }

    @Test
    fun `time comes only from the clock it is given`() {
        val gate = gate()
        gate.sheet { launched += "chooser" }
        repeat(1_000) { assertFalse(gate.sheet { launched += "tap $it" }, "a clock that stands holds the sheet however many times it is asked") }
        clockMs += SystemWindowGate.SHEET_HOLD_MS
        assertTrue(gate.sheet { launched += "chooser after the hold" })
        assertEquals(listOf("chooser", "chooser after the hold"), launched)
    }

    @Test
    fun `a launch that fails puts nothing up`() {
        val gate = gate()
        assertFailsWith<IllegalStateException> { gate.picker { error("no app to pick with") } }
        assertTrue(gate.picker { launched += "place" }, "the failure is the caller's to hear, and the gate is not left shut")
        assertEquals(listOf("place"), launched)
    }

    @Test
    fun `a picker the platform sees gone frees the gate without an answer`() {
        var gone = false
        val gate = gate(pickerGone = { gone })
        gate.picker { launched += "place" }
        assertFalse(gate.picker { launched += "place while it is up" }, "a picker on its way or up holds")
        gone = true
        assertTrue(gate.picker { launched += "place after it went" }, "one that went without a word is taken as answered")
        gone = false
        assertFalse(gate.sheet { launched += "sheet over the new one" }, "the new picker holds in its turn")
        gone = true
        assertTrue(gate.sheet { launched += "sheet after it went" }, "a sheet is not kept waiting by a picker that has gone either")
        assertEquals(listOf("place", "place after it went", "sheet after it went"), launched)
    }

    @Test
    fun `a sheet holds whatever the platform says of pickers`() {
        val gate = gate(pickerGone = { true })
        gate.sheet { launched += "chooser" }
        clockMs += 100
        assertFalse(gate.picker { launched += "place" }, "nothing of a picker frees the hold of a sheet")
        assertEquals(listOf("chooser"), launched)
    }
}
