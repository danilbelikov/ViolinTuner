package com.example.violintuner.feature.live

import com.example.violintuner.core.domain.Direction
import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.IntonationReading
import com.example.violintuner.core.domain.Note
import com.example.violintuner.core.domain.TargetMode
import com.example.violintuner.core.domain.ViolinString
import com.example.violintuner.core.domain.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `targets map to engine modes`() {
        assertEquals(TargetMode.Chromatic, LiveReducer.targetModeOf(LiveTarget(LiveMode.PLAY)))
        assertEquals(TargetMode.Strings(locked = null), LiveReducer.targetModeOf(LiveTarget(LiveMode.TUNING)))
        assertEquals(
            TargetMode.Strings(locked = ViolinString.D4),
            LiveReducer.targetModeOf(LiveTarget(LiveMode.TUNING, ViolinString.D4)),
        )
    }

    @Test
    fun `tap pins a string, second tap on it returns to auto, tap on another moves the lock`() {
        val auto = LiveTarget(LiveMode.TUNING)
        val lockedD = LiveReducer.clickString(auto, ViolinString.D4)
        assertEquals(ViolinString.D4, lockedD.lockedString)
        assertEquals(ViolinString.E5, LiveReducer.clickString(lockedD, ViolinString.E5).lockedString)
        assertNull(LiveReducer.clickString(lockedD, ViolinString.D4).lockedString)
    }

    @Test
    fun `string taps do nothing in play mode`() {
        val play = LiveTarget(LiveMode.PLAY)
        assertEquals(play, LiveReducer.clickString(play, ViolinString.A4))
    }

    @Test
    fun `switching the mode drops the lock, reselecting the same mode keeps it`() {
        val lockedD = LiveTarget(LiveMode.TUNING, ViolinString.D4)
        assertEquals(LiveTarget(LiveMode.PLAY), LiveReducer.selectMode(lockedD, LiveMode.PLAY))
        assertEquals(lockedD, LiveReducer.selectMode(lockedD, LiveMode.TUNING))
        val backAgain = LiveReducer.selectMode(LiveReducer.selectMode(lockedD, LiveMode.PLAY), LiveMode.TUNING)
        assertNull(backAgain.lockedString)
    }

    @Test
    fun `auto target is the string nearest to the sound`() {
        val config = IntonationConfig()
        val tuning = LiveTarget(LiveMode.TUNING)
        val playingA = LiveSignal.Sounding(Note(69), -12.0, Zone.NEAR, Direction.FLAT, 0.0)
        assertEquals(ViolinString.A4, LiveReducer.tuningStateOf(tuning, playingA, config).targetString)
        assertNull(LiveReducer.tuningStateOf(tuning, LiveSignal.Silence, config).targetString)
        assertNull(LiveReducer.tuningStateOf(LiveTarget(LiveMode.PLAY), playingA, config).targetString)
    }

    @Test
    fun `locked string is the target whatever sounds`() {
        val config = IntonationConfig()
        val lockedG = LiveTarget(LiveMode.TUNING, ViolinString.G3)
        val state = LiveReducer.tuningStateOf(lockedG, LiveSignal.Silence, config)
        assertEquals(ViolinString.G3, state.targetString)
        assertEquals(ViolinString.G3, state.lockedString)
    }

    @Test
    fun `button captions are rounded open string frequencies`() {
        val hz = LiveReducer.tuningStateOf(LiveTarget(), LiveSignal.Silence, IntonationConfig()).stringHz
        assertEquals(listOf(196, 294, 440, 659), ViolinString.entries.map(hz::getValue))
        val at442 = LiveReducer.tuningStateOf(LiveTarget(), LiveSignal.Silence, IntonationConfig(a4Hz = 442.0)).stringHz
        assertEquals(442, at442.getValue(ViolinString.A4))
    }
}
