package com.violinjourney.app.core.domain.session

import com.violinjourney.app.core.domain.Direction
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.IntonationReading
import com.violinjourney.app.core.domain.Note
import com.violinjourney.app.core.domain.Zone
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class SessionSamplerTest {
    private val sampler = SessionSampler(IntonationConfig())

    private fun active(midi: Int, cents: Double, held: Boolean = false) =
        IntonationReading.Active(Note(midi), cents, Zone.IN_TUNE, null as Direction?, 0.0, held)

    @Test
    fun `readings are averaged per 50 ms bucket — time starts at the first reading`() {
        sampler.add(1_000, active(69, 2.0))
        sampler.add(1_020, active(69, 4.0))
        sampler.add(1_040, active(69, 6.0))
        sampler.add(1_050, active(69, 10.0))
        assertEquals(listOf(SessionSample(69, 4.0), SessionSample(69, 10.0)), sampler.snapshot())
        assertEquals(50, sampler.durationMs)
    }

    @Test
    fun `silence — noise and skipped buckets are no note`() {
        sampler.add(0, active(69, 1.0))
        sampler.add(60, IntonationReading.Silence)
        sampler.add(110, IntonationReading.TooNoisy)
        sampler.add(260, active(71, -3.0)) // buckets 3 and 4 had no frames at all
        assertEquals(
            listOf(SessionSample(69, 1.0), null, null, null, null, SessionSample(71, -3.0)),
            sampler.snapshot(),
        )
    }

    @Test
    fun `held readings are not measurements`() {
        sampler.add(0, active(69, 5.0))
        sampler.add(50, active(69, 5.0, held = true))
        sampler.add(60, active(69, 5.0, held = true))
        sampler.add(100, active(69, 7.0))
        sampler.add(110, active(69, 5.0, held = true))
        val samples = sampler.snapshot()
        assertNull(samples[1])
        assertEquals(SessionSample(69, 7.0), samples[2])
    }

    @Test
    fun `the note with more readings wins a mixed bucket`() {
        sampler.add(0, active(69, 30.0))
        sampler.add(10, active(71, -2.0))
        sampler.add(20, active(71, -4.0))
        assertEquals(listOf(SessionSample(71, -3.0)), sampler.snapshot())
    }

    @Test
    fun `empty sampler`() {
        assertEquals(listOf<SessionSample?>(null), sampler.snapshot())
        assertEquals(0, sampler.durationMs)
    }
}
