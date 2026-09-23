package com.violinjourney.app.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CentsReadoutTest {
    private val config = IntonationConfig()
    private val a4 = 69

    @Test
    fun `the first value shows at once, rounded`() {
        assertEquals(12, CentsReadout(config).update(0, a4, 12.4))
        assertEquals(-7, CentsReadout(config).update(0, a4, -6.6))
        assertEquals(0, CentsReadout(config).update(0, a4, -0.3))
    }

    @Test
    fun `the digits change five times a second at most`() {
        val readout = CentsReadout(config)
        assertEquals(10, readout.update(0, a4, 10.0))
        assertEquals(10, readout.update(50, a4, 15.0))
        assertEquals(10, readout.update(199, a4, 18.0))
        assertEquals(18, readout.update(200, a4, 18.0))
        assertEquals(18, readout.update(390, a4, 3.0))
        assertEquals(3, readout.update(400, a4, 3.0))
    }

    @Test
    fun `a pitch hovering around a rounding edge does not toggle the digit`() {
        val readout = CentsReadout(config)
        assertEquals(4, readout.update(0, a4, 4.49))
        assertEquals(4, readout.update(300, a4, 4.51))
        assertEquals(4, readout.update(600, a4, 5.2))
        // A whole cent away from what was shown: now it moves.
        assertEquals(6, readout.update(900, a4, 5.6))
    }

    @Test
    fun `a new note shows at once, whatever the clock says`() {
        val readout = CentsReadout(config)
        assertEquals(10, readout.update(0, a4, 10.0))
        assertEquals(-20, readout.update(10, a4 + 2, -20.0))
    }

    @Test
    fun `the shown range is two digits, as in tuning mode where cents go far`() {
        assertEquals(99, CentsReadout(config).update(0, a4, 340.0))
        assertEquals(-99, CentsReadout(config).update(0, a4, -99.6))
    }

    @Test
    fun `after a reset the next note starts clean`() {
        val readout = CentsReadout(config)
        readout.update(0, a4, 10.0)
        readout.reset()
        assertEquals(-3, readout.update(10, a4, -3.0))
    }
}
