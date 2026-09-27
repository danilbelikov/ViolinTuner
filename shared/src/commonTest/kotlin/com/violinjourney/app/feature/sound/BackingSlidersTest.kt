package com.violinjourney.app.feature.sound

import com.violinjourney.app.core.domain.backing.BackingConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class BackingSlidersTest {
    private val config = BackingConfig(minOffsetMs = -500, maxOffsetMs = 500, minGainDb = -24f, maxGainDb = 6f)

    @Test
    fun `a fraction and its value come back to each other`() {
        listOf(-500, -250, 0, 250, 500).forEach { offsetMs ->
            assertEquals(offsetMs, BackingSliders.offsetAt(BackingSliders.offsetFraction(offsetMs, config), config), "the shift $offsetMs")
        }
        listOf(-24f, -6f, 0f, 6f).forEach { gainDb ->
            assertEquals(gainDb, BackingSliders.gainAt(BackingSliders.gainFraction(gainDb, config), config), 1e-4f, "the level $gainDb")
        }
        assertEquals(0f, BackingSliders.offsetFraction(config.minOffsetMs, config))
        assertEquals(1f, BackingSliders.gainFraction(config.maxGainDb, config))
    }

    @Test
    fun `a value past its range stands the thumb at the end`() {
        assertEquals(1f, BackingSliders.offsetFraction(2_000, config))
        assertEquals(0f, BackingSliders.gainFraction(-40f, config))
    }

    @Test
    fun `a dragged shift lands on a whole step`() {
        // 0.6372 of −500…+500 is +137.2 ms
        assertEquals(135, BackingSliders.offsetAt(0.6372f, config))
    }
}
