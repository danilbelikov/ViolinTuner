package com.violinjourney.app.core.analytics

import com.violinjourney.app.core.domain.IntonationReading
import com.violinjourney.app.core.domain.Note
import com.violinjourney.app.core.domain.PitchFrame
import com.violinjourney.app.core.domain.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FramePictureTest {
    private val active = IntonationReading.Active(Note(69), 0.0, Zone.IN_TUNE, null, 0.0)

    private fun frame(tMs: Long, clarity: Double = 0.9, rms: Double = 0.1) =
        PitchFrame(tMs = tMs, freqHz = null, cents = null, midi = null, clarity = clarity, rms = rms)

    @Test
    fun `a visit too short to learn from holds no picture`() {
        val picture = FramePicture(toleranceCents = 8, a4Hz = 440)
        repeat(1_000) { picture.add(frame(it * 10L), IntonationReading.Silence) }
        assertNull("29 seconds is not a picture", picture.finish())
    }

    @Test
    fun `a visit with no frames at all holds no picture`() {
        assertNull(FramePicture(toleranceCents = 8, a4Hz = 440).finish())
    }

    @Test
    fun `the picture is what the microphone heard, in shares`() {
        val picture = FramePicture(toleranceCents = 3, a4Hz = 442)
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
                "rms_peak_dbfs" to -20,
                "tolerance" to 3,
                "a4" to 442,
            ),
            event.params,
        )
    }

    @Test
    fun `the loudest frame is the peak, and exact zeros have a floor instead of minus infinity`() {
        val picture = FramePicture(toleranceCents = 8, a4Hz = 440)
        repeat(4_000) { picture.add(frame(it * 15L, rms = 0.0), IntonationReading.Silence) }
        assertEquals(-120, requireNotNull(picture.finish()).params["rms_peak_dbfs"])

        val louder = FramePicture(toleranceCents = 8, a4Hz = 440)
        repeat(4_000) { index -> louder.add(frame(index * 15L, rms = if (index == 7) 0.5 else 0.001), IntonationReading.Silence) }
        assertEquals(-6, requireNotNull(louder.finish()).params["rms_peak_dbfs"])
    }
}
