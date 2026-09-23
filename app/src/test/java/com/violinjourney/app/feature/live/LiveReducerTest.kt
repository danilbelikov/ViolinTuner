package com.violinjourney.app.feature.live

import com.violinjourney.app.core.domain.Direction
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.IntonationReading
import com.violinjourney.app.core.domain.Note
import com.violinjourney.app.core.domain.TargetMode
import com.violinjourney.app.core.domain.ViolinString
import com.violinjourney.app.core.domain.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveReducerTest {
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

    private val config = IntonationConfig()

    private fun sounding(zone: Zone, hold: Double = 0.0) =
        LiveSignal.Sounding(Note(69), 0.0, zone, direction = null, holdProgress = hold)

    @Test
    fun `nothing glows without a note`() {
        listOf(LiveSignal.Silence, LiveSignal.TooNoisy, LiveSignal.MicUnavailable, LiveSignal.NoMicPermission)
            .forEach { assertEquals(0f, LiveReducer.glowTargetOf(it, config), 0f) }
    }

    @Test
    fun `a miss glows less than a near miss, and both less than a hit`() {
        assertEquals(0.25f, LiveReducer.glowTargetOf(sounding(Zone.OFF), config), 0f)
        assertEquals(0.4f, LiveReducer.glowTargetOf(sounding(Zone.NEAR), config), 0f)
        assertEquals(0.6f, LiveReducer.glowTargetOf(sounding(Zone.IN_TUNE), config), 0f)
    }

    @Test
    fun `in tune the glow grows to full with the hold`() {
        assertEquals(0.8f, LiveReducer.glowTargetOf(sounding(Zone.IN_TUNE, hold = 0.5), config), 1e-6f)
        assertEquals(1f, LiveReducer.glowTargetOf(sounding(Zone.IN_TUNE, hold = 1.0), config), 1e-6f)
        assertEquals(1f, LiveReducer.glowTargetOf(sounding(Zone.IN_TUNE, hold = 1.3), config), 1e-6f)
    }

    @Test
    fun `the status line says whether one may play, and hides while a note sounds`() {
        val play = LiveTarget(LiveMode.PLAY)
        assertEquals(StatusLine(StatusDot.READY, StatusMessage.PLAY), LiveReducer.statusLineOf(play, LiveSignal.Silence))
        assertEquals(StatusLine(StatusDot.BLOCKED, StatusMessage.TOO_NOISY), LiveReducer.statusLineOf(play, LiveSignal.TooNoisy))
        assertEquals(
            StatusLine(StatusDot.BLOCKED, StatusMessage.MIC_UNAVAILABLE),
            LiveReducer.statusLineOf(play, LiveSignal.MicUnavailable),
        )
        assertNull(LiveReducer.statusLineOf(play, sounding(Zone.NEAR)))
        assertNull("the permission prompt speaks for itself", LiveReducer.statusLineOf(play, LiveSignal.NoMicPermission))
    }

    @Test
    fun `in tuning mode the line is the hint, and trouble replaces the hint`() {
        val auto = LiveTarget(LiveMode.TUNING)
        val locked = LiveTarget(LiveMode.TUNING, lockedString = ViolinString.D4)
        assertEquals(StatusLine(StatusDot.READY, StatusMessage.TUNE_AUTO), LiveReducer.statusLineOf(auto, LiveSignal.Silence))
        assertEquals(StatusLine(StatusDot.READY, StatusMessage.TUNE_LOCKED), LiveReducer.statusLineOf(locked, LiveSignal.Silence))
        assertEquals(StatusLine(StatusDot.BLOCKED, StatusMessage.TOO_NOISY), LiveReducer.statusLineOf(locked, LiveSignal.TooNoisy))
        assertNull(LiveReducer.statusLineOf(locked, sounding(Zone.IN_TUNE)))
    }
}
