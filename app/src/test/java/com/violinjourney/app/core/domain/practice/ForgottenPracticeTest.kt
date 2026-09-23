package com.violinjourney.app.core.domain.practice

import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_HOUR
import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_MINUTE
import org.junit.Assert.assertEquals
import org.junit.Test

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
}
