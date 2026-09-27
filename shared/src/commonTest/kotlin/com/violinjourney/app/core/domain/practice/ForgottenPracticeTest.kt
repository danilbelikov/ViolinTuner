package com.violinjourney.app.core.domain.practice

import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_HOUR
import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_MINUTE
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class ForgottenPracticeTest {
    private val config = PracticeConfig()
    private val start = 1_000_000_000_000L

    private fun check(elapsedMs: Long, lastSoundAgoMs: Long?): PracticeCheck {
        val now = start + elapsedMs
        val running = RunningPractice(start, lastSoundAgoMs?.let { now - it })
        return ForgottenPractice.check(running, now, config)
    }

    @Test
    fun `recent sound means the practice simply runs`() {
        assertEquals(PracticeCheck.Running, check(3 * MS_PER_HOUR, lastSoundAgoMs = 10 * MS_PER_MINUTE))
        assertEquals(PracticeCheck.Running, check(3 * MS_PER_HOUR, lastSoundAgoMs = 60 * MS_PER_MINUTE))
    }

    @Test
    fun `no sound yet within the first hour is not forgotten`() {
        assertEquals(PracticeCheck.Running, check(59 * MS_PER_MINUTE, lastSoundAgoMs = null))
        assertEquals(PracticeCheck.Running, check(60 * MS_PER_MINUTE, lastSoundAgoMs = null))
    }

    @Test
    fun `an hour of silence after the last sound is forgotten with that time`() {
        val now = start + 3 * MS_PER_HOUR + 12 * MS_PER_MINUTE
        val lastSound = now - 61 * MS_PER_MINUTE
        val result = ForgottenPractice.check(RunningPractice(start, lastSound), now, config)
        assertEquals(PracticeCheck.Forgotten(elapsedMs = now - start, lastSoundEpochMs = lastSound), result)
    }

    @Test
    fun `an hour without any sound is forgotten without a time`() {
        val elapsed = 61 * MS_PER_MINUTE
        assertEquals(PracticeCheck.Forgotten(elapsed, lastSoundEpochMs = null), check(elapsed, lastSoundAgoMs = null))
    }

    @Test
    fun `past twelve hours the practice has ended at the last sound`() {
        val now = start + 13 * MS_PER_HOUR
        val lastSound = start + 5 * MS_PER_HOUR
        val result = ForgottenPractice.check(RunningPractice(start, lastSound), now, config)
        assertEquals(PracticeCheck.Expired(endEpochMs = lastSound), result)
    }

    @Test
    fun `past twelve hours without sound it ended an hour after the start`() {
        val result = check(13 * MS_PER_HOUR, lastSoundAgoMs = null)
        assertEquals(PracticeCheck.Expired(endEpochMs = start + config.forgottenAfterMs), result)
    }

    @Test
    fun `an expired practice is never credited beyond the limit`() {
        val now = start + 14 * MS_PER_HOUR
        val lastSound = start + 13 * MS_PER_HOUR
        val result = ForgottenPractice.check(RunningPractice(start, lastSound), now, config)
        assertEquals(PracticeCheck.Expired(endEpochMs = start + config.maxPracticeMs), result)
    }

    @Test
    fun `a clock moved backwards reads as no time elapsed`() {
        assertEquals(0L, RunningPractice(start, null).elapsedMs(start - MS_PER_HOUR))
        assertEquals(PracticeCheck.Running, check(-MS_PER_HOUR, lastSoundAgoMs = null))
    }

    @Test
    fun `ended within the limit a practice counts up to the end`() {
        val running = RunningPractice(start, start + 40 * MS_PER_MINUTE)
        assertEquals(3 * MS_PER_HOUR, ForgottenPractice.lengthAt(running, start + 3 * MS_PER_HOUR, config))
        assertEquals(config.maxPracticeMs, ForgottenPractice.lengthAt(running, start + config.maxPracticeMs, config))
    }

    @Test
    fun `ended past the limit it counts up to its last sound`() {
        val running = RunningPractice(start, start + 40 * MS_PER_MINUTE)
        assertEquals(40 * MS_PER_MINUTE, ForgottenPractice.lengthAt(running, start + 13 * MS_PER_HOUR, config))
    }

    @Test
    fun `ended past the limit without a sound it counts an hour`() {
        assertEquals(60 * MS_PER_MINUTE, ForgottenPractice.lengthAt(RunningPractice(start, null), start + 13 * MS_PER_HOUR, config))
    }

    @Test
    fun `ended before its start by a clock moved back it counts nothing`() {
        assertEquals(0L, ForgottenPractice.lengthAt(RunningPractice(start, null), start - MS_PER_HOUR, config))
    }

    @Test
    fun `a sound belongs to the practice up to the limit and not after it`() {
        assertTrue(ForgottenPractice.runsAt(start, start + config.maxPracticeMs, config))
        assertFalse(ForgottenPractice.runsAt(start, start + config.maxPracticeMs + 1, config))
    }
}
