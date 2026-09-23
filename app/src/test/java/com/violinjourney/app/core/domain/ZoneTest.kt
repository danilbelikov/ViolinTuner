package com.violinjourney.app.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZoneTest {
    private val config = IntonationConfig()

    @Test
    fun `boundaries are inclusive on the inner side`() {
        assertEquals(Zone.IN_TUNE, ZoneClassifier.classify(0.0, config))
        assertEquals(Zone.IN_TUNE, ZoneClassifier.classify(8.0, config))
        assertEquals(Zone.NEAR, ZoneClassifier.classify(8.01, config))
        assertEquals(Zone.NEAR, ZoneClassifier.classify(20.0, config))
        assertEquals(Zone.OFF, ZoneClassifier.classify(20.01, config))
    }

    @Test
    fun `zones are symmetric around zero`() {
        assertEquals(Zone.IN_TUNE, ZoneClassifier.classify(-8.0, config))
        assertEquals(Zone.NEAR, ZoneClassifier.classify(-15.0, config))
        assertEquals(Zone.OFF, ZoneClassifier.classify(-35.0, config))
    }

    @Test
    fun `tolerance comes from config`() {
        val strict = config.copy(toleranceCents = 3.0)
        assertEquals(Zone.NEAR, ZoneClassifier.classify(5.0, strict))
    }

    @Test
    fun `direction follows the sign`() {
        assertEquals(Direction.SHARP, Direction.of(0.1))
        assertEquals(Direction.FLAT, Direction.of(-0.1))
        assertNull(Direction.of(0.0))
    }

    @Test
    fun `leaving in-tune needs 1_5 cents past the boundary, returning 1_5 inside`() {
        val zones = ZoneHysteresis(config)
        assertEquals(Zone.IN_TUNE, zones.update(0.0))
        assertEquals(Zone.IN_TUNE, zones.update(9.5))
        assertEquals(Zone.NEAR, zones.update(9.6))
        assertEquals(Zone.NEAR, zones.update(7.0))
        assertEquals(Zone.NEAR, zones.update(6.6))
        assertEquals(Zone.IN_TUNE, zones.update(6.5))
    }

    @Test
    fun `near-off boundary has the same hysteresis`() {
        val zones = ZoneHysteresis(config)
        assertEquals(Zone.NEAR, zones.update(-15.0))
        assertEquals(Zone.NEAR, zones.update(-21.5))
        assertEquals(Zone.OFF, zones.update(-21.6))
        assertEquals(Zone.OFF, zones.update(-19.0))
        assertEquals(Zone.NEAR, zones.update(-18.5))
    }

    @Test
    fun `wobble around a boundary does not flicker`() {
        val zones = ZoneHysteresis(config)
        zones.update(7.0)
        val seen = listOf(9.0, 7.2, 8.9, 7.1, 9.4, 6.9).map(zones::update).toSet()
        assertEquals(setOf(Zone.IN_TUNE), seen)
    }

    @Test
    fun `a big jump crosses several zones at once`() {
        val zones = ZoneHysteresis(config)
        zones.update(0.0)
        assertEquals(Zone.OFF, zones.update(30.0))
        assertEquals(Zone.IN_TUNE, zones.update(2.0))
    }

    @Test
    fun `reset forgets the current zone`() {
        val zones = ZoneHysteresis(config)
        zones.update(0.0)
        zones.reset()
        assertEquals(Zone.NEAR, zones.update(9.0))
    }
}
