package com.example.violintuner.feature.live

import com.example.violintuner.core.domain.Direction
import com.example.violintuner.core.domain.IntonationReading
import com.example.violintuner.core.domain.Note
import com.example.violintuner.core.domain.TargetMode
import com.example.violintuner.core.domain.Zone
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveReducerTest {
    @Test
    fun `silence and noise map one to one`() {
        assertEquals(LiveSignal.Silence, LiveReducer.signalOf(IntonationReading.Silence))
        assertEquals(LiveSignal.TooNoisy, LiveReducer.signalOf(IntonationReading.TooNoisy))
    }

    @Test
    fun `active reading keeps every field`() {
        val reading = IntonationReading.Active(Note(78), 14.5, Zone.NEAR, Direction.SHARP, 0.0)
        assertEquals(
            LiveSignal.Sounding(Note(78), 14.5, Zone.NEAR, Direction.SHARP, 0.0),
            LiveReducer.signalOf(reading),
        )
    }

    @Test
    fun `modes map to engine targets`() {
        assertEquals(TargetMode.Chromatic, LiveReducer.targetModeOf(LiveMode.PLAY))
        assertEquals(TargetMode.Strings(locked = null), LiveReducer.targetModeOf(LiveMode.TUNING))
    }
}
