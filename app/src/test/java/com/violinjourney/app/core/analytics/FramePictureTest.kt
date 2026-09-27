package com.violinjourney.app.core.analytics

import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.IntonationReading
import com.violinjourney.app.core.domain.Note
import com.violinjourney.app.core.domain.PitchFrame
import com.violinjourney.app.core.domain.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FramePictureTest {
    private val silenceRms = IntonationConfig().silenceRms
    private val active = IntonationReading.Active(Note(69), 0.0, Zone.IN_TUNE, null, 0.0)

    private fun frame(tMs: Long, clarity: Double = 0.9, rms: Double = 0.1) =
        PitchFrame(tMs = tMs, freqHz = null, cents = null, midi = null, clarity = clarity, rms = rms)

    @Test
    fun `a visit too short to learn from holds no picture`() {
        val picture = FramePicture(toleranceCents = 8, a4Hz = 440, silenceRms = silenceRms)
        repeat(1_000) { picture.add(frame(it * 10L), IntonationReading.Silence) }
        assertNull("29 seconds is not a picture", picture.finish())
    }

    @Test
    fun `a visit with no frames at all holds no picture`() {
        assertNull(FramePicture(toleranceCents = 8, a4Hz = 440, silenceRms = silenceRms).finish())
    }

    @Test
    fun `the picture is what the microphone heard, in shares`() {
        val picture = FramePicture(toleranceCents = 3, a4Hz = 442, silenceRms = silenceRms)
        repeat(4_000) { index ->
            val reading = when {
                index < 2_000 -> IntonationReading.Silence
                index < 3_000 -> IntonationReading.TooNoisy
                else -> active
            }
            picture.add(frame(index * 15L), reading)
        }
        val event = requireNotNull(picture.finish())
        assertEquals("live_frames", event.name)
        assertEquals(
            mapOf(
                "seconds" to 59,
                "silence_pct" to 50,
                "noisy_pct" to 25,
                "active_pct" to 25,
                "clarity_median" to 0.905,
                "rms_floor_dbfs" to -20,
                "rms_peak_dbfs" to -20,
                "tolerance" to 3,
                "a4" to 442,
            ),
            event.params,
        )
    }

    @Test
    fun `quiet frames stay out of the clarity median`() {
        val picture = FramePicture(toleranceCents = 8, a4Hz = 440, silenceRms = silenceRms)
        repeat(4_000) { index ->
            val quiet = index < 3_000
            picture.add(frame(index * 15L, clarity = if (quiet) 0.1 else 0.9, rms = if (quiet) silenceRms / 2 else 0.1), IntonationReading.Silence)
        }
        assertEquals(0.905, requireNotNull(picture.finish()).params["clarity_median"])
    }

    @Test
    fun `a visit without a loud frame reports a median of zero`() {
        val picture = FramePicture(toleranceCents = 8, a4Hz = 440, silenceRms = silenceRms)
        repeat(4_000) { picture.add(frame(it * 15L, clarity = 0.0, rms = silenceRms / 2), IntonationReading.Silence) }
        assertEquals(0.0, requireNotNull(picture.finish()).params["clarity_median"])
    }

    @Test
    fun `the loudest frame is the peak, and exact zeros have a floor instead of minus infinity`() {
        val picture = FramePicture(toleranceCents = 8, a4Hz = 440, silenceRms = silenceRms)
        repeat(4_000) { picture.add(frame(it * 15L, rms = 0.0), IntonationReading.Silence) }
        assertEquals(-120, requireNotNull(picture.finish()).params["rms_peak_dbfs"])

        val louder = FramePicture(toleranceCents = 8, a4Hz = 440, silenceRms = silenceRms)
        repeat(4_000) { index -> louder.add(frame(index * 15L, rms = if (index == 7) 0.5 else 0.001), IntonationReading.Silence) }
        assertEquals(-6, requireNotNull(louder.finish()).params["rms_peak_dbfs"])
    }

    @Test
    fun `the floor is the loudness a tenth of the frames stay under`() {
        val picture = FramePicture(toleranceCents = 8, a4Hz = 440, silenceRms = silenceRms)
        repeat(4_000) { index -> picture.add(frame(index * 15L, rms = if (index % 5 == 0) 0.001 else 0.1), IntonationReading.Silence) }
        assertEquals(-60, requireNotNull(picture.finish()).params["rms_floor_dbfs"])

        val dead = FramePicture(toleranceCents = 8, a4Hz = 440, silenceRms = silenceRms)
        repeat(4_000) { dead.add(frame(it * 15L, rms = 0.0), IntonationReading.Silence) }
        assertEquals(-120, requireNotNull(dead.finish()).params["rms_floor_dbfs"])
    }

    @Test
    fun `a reopened microphone starts a stretch of its own, and the seconds add up`() {
        val picture = FramePicture(toleranceCents = 8, a4Hz = 440, silenceRms = silenceRms)
        (0..2_800).forEach { picture.add(frame(it * 10L), IntonationReading.Silence) } // 28 s
        picture.newStretch() // the input failed and was opened again 3 s later, on a clock that went on meanwhile
        (0..500).forEach { picture.add(frame(31_000 + it * 10L), IntonationReading.Silence) } // 5 s more
        assertEquals("the 3 s of the reopening are not heard", 33, requireNotNull(picture.finish()).params["seconds"])
    }

    @Test
    fun `a clock that jumps back counts nothing for the jump even when nobody said it was a new stretch`() {
        val picture = FramePicture(toleranceCents = 8, a4Hz = 440, silenceRms = silenceRms)
        (0..2_000).forEach { picture.add(frame(it * 10L), IntonationReading.Silence) } // 20 s
        (0..1_500).forEach { picture.add(frame(46 + it * 10L), IntonationReading.Silence) } // 15 s
        assertEquals(35, requireNotNull(picture.finish()).params["seconds"])
    }
}
