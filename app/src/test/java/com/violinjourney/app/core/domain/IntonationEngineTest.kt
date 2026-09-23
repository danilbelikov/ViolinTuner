package com.violinjourney.app.core.domain

import com.violinjourney.app.core.domain.IntonationReading.Active
import com.violinjourney.app.core.domain.IntonationReading.Silence
import com.violinjourney.app.core.domain.IntonationReading.TooNoisy
import com.violinjourney.app.core.domain.TestFrames.noise
import com.violinjourney.app.core.domain.TestFrames.played
import com.violinjourney.app.core.domain.TestFrames.quiet
import com.violinjourney.app.core.domain.TestFrames.times
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntonationEngineTest {
    private val engine = IntonationEngine()
    private var mode: TargetMode = TargetMode.Chromatic

    private fun feed(frame: PitchFrame): IntonationReading = engine.process(frame, mode)

    /** Plays [midi] for the time span and returns the last reading. */
    private fun play(fromMs: Long, untilMs: Long, midi: Int, cents: Double = 0.0): IntonationReading =
        times(fromMs, untilMs).map { feed(played(it, midi, cents)) }.last()

    private fun IntonationReading.active(): Active {
        assertTrue("expected Active but was $this", this is Active)
        return this as Active
    }

    @Test
    fun `steady A4 locks after 100 ms and fills the ring in 2 s`() {
        assertEquals(Silence, play(0, 100, midi = 69))

        val locked = feed(played(100, 69)).active()
        assertEquals("A4", locked.note.name)
        assertEquals(Zone.IN_TUNE, locked.zone)
        assertNull(locked.direction)
        assertEquals(0.0, locked.holdProgress, EPS)

        assertEquals(0.5, play(110, 1_110, midi = 69).active().holdProgress, EPS)
        assertEquals(1.0, play(1_110, 2_110, midi = 69).active().holdProgress, EPS)
    }

    @Test
    fun `sharp and flat readings carry zone and direction`() {
        val near = play(0, 500, midi = 69, cents = 15.0).active()
        assertEquals(Zone.NEAR, near.zone)
        assertEquals(Direction.SHARP, near.direction)
        assertEquals(15.0, near.cents, 0.01)
        assertEquals(0.0, near.holdProgress, EPS)

        engine.reset()
        val off = play(0, 500, midi = 69, cents = -30.0).active()
        assertEquals(Zone.OFF, off.zone)
        assertEquals(Direction.FLAT, off.direction)
    }

    @Test
    fun `slow drift walks through the zones with hysteresis`() {
        play(0, 300, midi = 69)
        val zones = times(300, 3_300).map { t ->
            val cents = (t - 300) * 0.01 // 0 → 30 cents over 3 s
            val reading = feed(played(t, 69, cents)).active()
            reading.zone to reading.cents
        }
        assertEquals(listOf(Zone.IN_TUNE, Zone.NEAR, Zone.OFF), zones.map { it.first }.distinct())
        val firstNear = zones.first { it.first == Zone.NEAR }.second
        val firstOff = zones.first { it.first == Zone.OFF }.second
        assertTrue("left in-tune at $firstNear", firstNear > 9.5 && firstNear < 9.8)
        assertTrue("left near at $firstOff", firstOff > 21.5 && firstOff < 21.8)
    }

    @Test
    fun `reading freezes through a pause and turns into silence after 300 ms`() {
        val playing = play(0, 1_000, midi = 69).active() // last pitched frame at 990
        assertEquals(playing.copy(held = true), feed(quiet(1_000))) // same reading, marked as held
        assertTrue(feed(quiet(1_290)) is Active)
        assertEquals(Silence, feed(quiet(1_300)))
        // a new note has to lock again
        assertEquals(Silence, play(1_310, 1_400, midi = 69))
        assertTrue(feed(played(1_410, 69)) is Active)
    }

    @Test
    fun `gap up to 100 ms keeps the hold ring, a longer one resets it`() {
        play(0, 1_100, midi = 69) // locked at 100, last frame 1090
        times(1_100, 1_190).forEach { feed(quiet(it)) }
        val resumed = feed(played(1_190, 69)).active()
        assertEquals(1_090.0 / 2_000, resumed.holdProgress, EPS)

        times(1_200, 1_300).forEach { feed(quiet(it)) }
        assertEquals(0.0, feed(quiet(1_300)).active().holdProgress, EPS)
        assertEquals(0.0, feed(played(1_310, 69)).active().holdProgress, EPS)
    }

    @Test
    fun `loud unclear signal is silence first and too noisy after one second`() {
        assertEquals(Silence, feed(noise(0)))
        assertEquals(Silence, times(10, 1_010).map { feed(noise(it)) }.last())
        assertEquals(TooNoisy, feed(noise(1_010)))
        assertEquals(Silence, feed(quiet(1_020)))
    }

    @Test
    fun `fast passage never paints the screen`() {
        var t = 0L
        val readings = listOf(69, 71, 73, 74, 76, 78, 76, 74).flatMap { midi ->
            (0 until 5).map { feed(played(t, midi)).also { t += 10 } }
        }
        assertTrue(readings.all { it == Silence })
    }

    @Test
    fun `note change keeps the old reading until the new note locks, then starts clean`() {
        val a4 = play(0, 1_000, midi = 69, cents = 5.0).active()
        val pending = play(1_000, 1_100, midi = 71, cents = -12.0).active()
        assertEquals("A4", pending.note.name)
        assertEquals(a4.cents, pending.cents, EPS)

        val b4 = feed(played(1_100, 71, -12.0)).active()
        assertEquals("B4", b4.note.name)
        assertEquals(-12.0, b4.cents, 0.01) // no glide from the previous note
        assertEquals(Zone.NEAR, b4.zone)
        assertEquals(Direction.FLAT, b4.direction)
        assertEquals(0.0, b4.holdProgress, EPS)
    }

    @Test
    fun `single octave error does not disturb the reading`() {
        play(0, 1_000, midi = 69, cents = 3.0)
        val glitch = feed(played(1_000, 81, 3.0)).active()
        assertEquals("A4", glitch.note.name)
        val after = feed(played(1_010, 69, 3.0)).active()
        assertEquals(3.0, after.cents, 0.01)
        assertEquals(Zone.IN_TUNE, after.zone)
        assertEquals(910.0 / 2_000, after.holdProgress, EPS)
    }

    @Test
    fun `moderate vibrato around the note stays in tune`() {
        play(0, 300, midi = 69)
        val readings = times(300, 2_300).map { t ->
            val cents = 10.0 * sin(2 * PI * 5.5 * t / 1_000.0)
            feed(played(t, 69, cents)).active()
        }
        assertTrue(readings.all { it.zone == Zone.IN_TUNE })
        assertTrue(readings.maxOf { abs(it.cents) } < 8.0)
        assertEquals(1.0, readings.last().holdProgress, EPS)
    }

    @Test
    fun `low clarity and out of range frames count as unclear`() {
        play(0, 500, midi = 69)
        val lowClarity = PitchFrame.pitched(500, 440.0, clarity = 0.5, rms = 0.2, a4Hz = 440.0)
        assertTrue(feed(lowClarity) is Active) // bridged as a gap
        val tooLow = times(510, 900).map { feed(played(it, 50)) }.last() // D3, below G3 − 350 c
        assertEquals(Silence, tooLow)
    }

    @Test
    fun `flat G string is still readable below 196 Hz`() {
        val reading = play(0, 500, midi = 55, cents = -60.0).active()
        assertEquals("F#3", reading.note.name)
        assertEquals(40.0, reading.cents, 0.01)
    }

    @Test
    fun `tuning mode measures against the nearest open string`() {
        mode = TargetMode.Strings()
        val reading = play(0, 500, midi = 55, cents = -60.0).active()
        assertEquals("G3", reading.note.name)
        assertEquals(-60.0, reading.cents, 0.01)
        assertEquals(Zone.OFF, reading.zone)
        assertEquals(Direction.FLAT, reading.direction)
    }

    @Test
    fun `tuning mode ignores pitch beyond 350 cents from every string`() {
        mode = TargetMode.Strings()
        assertEquals(Silence, play(0, 500, midi = 81)) // A5 is 500 cents above E5
    }

    @Test
    fun `locked string stays the target for any pitch`() {
        mode = TargetMode.Strings(locked = ViolinString.A4)
        val reading = play(0, 500, midi = 62).active() // playing D4
        assertEquals("A4", reading.note.name)
        assertEquals(-700.0, reading.cents, 0.01)
        assertEquals(Zone.OFF, reading.zone)
    }

    @Test
    fun `changing the mode starts from scratch`() {
        play(0, 1_000, midi = 69)
        mode = TargetMode.Strings()
        assertEquals(Silence, feed(played(1_000, 69)))
        assertEquals(0.0, play(1_010, 1_200, midi = 69).active().holdProgress, 0.06)
    }

    private companion object {
        const val EPS = 1e-9
    }
}
