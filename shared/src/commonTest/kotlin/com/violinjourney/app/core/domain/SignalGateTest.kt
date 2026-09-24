package com.violinjourney.app.core.domain

import kotlin.test.assertEquals
import kotlin.test.Test

class SignalGateTest {
    private val gate = SignalGate(IntonationConfig())

    @Test
    fun `starts silent`() {
        assertEquals(SignalState.SILENCE, gate.update(0, FrameKind.QUIET))
    }

    @Test
    fun `silence needs more than 300 ms without pitch`() {
        assertEquals(SignalState.PITCHED, gate.update(1_000, FrameKind.PITCHED))
        assertEquals(SignalState.GAP, gate.update(1_299, FrameKind.QUIET))
        assertEquals(SignalState.GAP, gate.update(1_300, FrameKind.QUIET))
        assertEquals(SignalState.SILENCE, gate.update(1_301, FrameKind.QUIET))
    }

    @Test
    fun `too noisy needs more than one second of unclear loud signal`() {
        gate.update(0, FrameKind.PITCHED)
        assertEquals(SignalState.GAP, gate.update(10, FrameKind.UNCLEAR))
        assertEquals(SignalState.SILENCE, gate.update(500, FrameKind.UNCLEAR))
        assertEquals(SignalState.SILENCE, gate.update(1_010, FrameKind.UNCLEAR))
        assertEquals(SignalState.TOO_NOISY, gate.update(1_011, FrameKind.UNCLEAR))
    }

    @Test
    fun `a quiet frame restarts the noise timer`() {
        gate.update(0, FrameKind.UNCLEAR)
        gate.update(900, FrameKind.UNCLEAR)
        gate.update(910, FrameKind.QUIET)
        assertEquals(SignalState.SILENCE, gate.update(1_500, FrameKind.UNCLEAR))
        assertEquals(SignalState.TOO_NOISY, gate.update(2_600, FrameKind.UNCLEAR))
    }

    @Test
    fun `pitch ends the noisy state`() {
        gate.update(0, FrameKind.UNCLEAR)
        assertEquals(SignalState.TOO_NOISY, gate.update(1_100, FrameKind.UNCLEAR))
        assertEquals(SignalState.PITCHED, gate.update(1_110, FrameKind.PITCHED))
        assertEquals(SignalState.GAP, gate.update(1_120, FrameKind.UNCLEAR))
    }
}
