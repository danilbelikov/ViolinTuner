package com.example.violintuner.core.audio

import com.example.violintuner.core.domain.Direction
import com.example.violintuner.core.domain.IntonationEngine
import com.example.violintuner.core.domain.IntonationReading
import com.example.violintuner.core.domain.IntonationReading.Active
import com.example.violintuner.core.domain.TargetMode
import com.example.violintuner.core.domain.Zone
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.testTimeSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakePitchSourceTest {

    /** Three seconds of the scenario run through the real engine. */
    private fun readings(scenario: FakeScenario): List<IntonationReading> {
        val source = FakePitchSource(scenario)
        val engine = IntonationEngine()
        return (0L until FRAMES).map { engine.process(source.frameAt(it), TargetMode.Chromatic) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `flow emits frames at the hop rate`() = runTest {
        val frames = FakePitchSource(FakeScenario.IN_TUNE, timeSource = testTimeSource).frames.take(5).toList()
        assertEquals(listOf(0L, 11L, 23L, 34L, 46L), frames.map { it.tMs })
        assertEquals(46L, currentTime)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `flow catches up with the clock when the collector is slow`() = runTest {
        val frames = FakePitchSource(FakeScenario.IN_TUNE, timeSource = testTimeSource).frames
            .onEach { delay(5) } // slower than nothing, faster than the hop
            .take(100)
            .toList()
        assertEquals(frames.last().tMs, currentTime - 5)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `flow and frameAt agree`() = runTest {
        val source = FakePitchSource(FakeScenario.VIBRATO, timeSource = testTimeSource)
        val fromFlow = source.frames.take(20).toList()
        assertEquals((0L until 20).map(source::frameAt), fromFlow)
    }

    @Test
    fun `pitched frames carry note and cents, unpitched carry nulls`() {
        val played = FakePitchSource(FakeScenario.IN_TUNE).frameAt(10)
        assertEquals(69, played.midi)
        assertEquals(2.0, played.cents!!, 1e-6)

        val silent = FakePitchSource(FakeScenario.SILENCE).frameAt(10)
        assertNull(silent.freqHz)
        assertNull(silent.cents)
        assertNull(silent.midi)
    }

    @Test
    fun `in tune scenario ends in tune with a growing ring`() {
        val last = readings(FakeScenario.IN_TUNE).last() as Active
        assertEquals("A4", last.note.name)
        assertEquals(Zone.IN_TUNE, last.zone)
        assertEquals(1.0, last.holdProgress, 1e-9)
    }

    @Test
    fun `drift sharp passes near and ends off, sharp`() {
        val active = readings(FakeScenario.DRIFT_SHARP).filterIsInstance<Active>()
        assertEquals(listOf(Zone.IN_TUNE, Zone.NEAR, Zone.OFF), active.map { it.zone }.distinct())
        assertEquals(Direction.SHARP, active.last().direction)
    }

    @Test
    fun `drift flat mirrors it`() {
        val active = readings(FakeScenario.DRIFT_FLAT).filterIsInstance<Active>()
        assertEquals(listOf(Zone.IN_TUNE, Zone.NEAR, Zone.OFF), active.map { it.zone }.distinct())
        assertEquals(Direction.FLAT, active.last().direction)
    }

    @Test
    fun `vibrato scenario stays in tune while the reading moves`() {
        val active = readings(FakeScenario.VIBRATO).filterIsInstance<Active>()
        assertTrue(active.all { it.zone == Zone.IN_TUNE })
        assertTrue(active.maxOf { it.cents } - active.minOf { it.cents } > 8.0)
    }

    @Test
    fun `silence scenario is always silence`() {
        assertTrue(readings(FakeScenario.SILENCE).all { it == IntonationReading.Silence })
    }

    @Test
    fun `noise scenario becomes too noisy after a second`() {
        val all = readings(FakeScenario.NOISE)
        assertEquals(IntonationReading.Silence, all.first())
        assertEquals(IntonationReading.TooNoisy, all.last())
    }

    @Test
    fun `demo loops through every state of the screen`() {
        val source = FakePitchSource(FakeScenario.DEMO)
        val engine = IntonationEngine()
        val oneLoop = 6 * 4_000 * 44_100L / 512 / 1_000
        val readings = (0L until oneLoop + 100).map { engine.process(source.frameAt(it), TargetMode.Chromatic) }
        val zones = readings.filterIsInstance<Active>().map { it.zone }.toSet()
        assertEquals(setOf(Zone.IN_TUNE, Zone.NEAR, Zone.OFF), zones)
        assertTrue(IntonationReading.Silence in readings)
        assertTrue(IntonationReading.TooNoisy in readings)
        assertTrue("second loop starts in tune again", readings.last() is Active)
    }

    private companion object {
        const val FRAMES = 300L
    }
}
