package com.violinjourney.app.feature.practice.components

import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_MINUTE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The still «Сегодня» the window of the home is fitted to (spec 3.36.2): its numbers are where the figures roll to, the same on every
 * frame of the roll — so its height, and the window's, never follow the roll.
 */
class SettledNumbersTest {
    private fun numbers(rolledTodayMs: Long, rolledWeekMs: Long, rolledStreakDays: Int, rolledMonthMs: Long) = ShownNumbers(
        hasHistory = true,
        todayMs = 130 * MS_PER_MINUTE,
        rolledTodayMs = rolledTodayMs,
        weekMs = 400 * MS_PER_MINUTE,
        rolledWeekMs = rolledWeekMs,
        weekDaysMs = List(7) { 30 * MS_PER_MINUTE },
        streakDays = 8,
        rolledStreakDays = rolledStreakDays,
        scope = "today",
        monthMs = 1_047 * MS_PER_MINUTE,
        rolledMonthMs = rolledMonthMs,
        monthDays = 24,
    )

    @Test
    fun `settled numbers are what they roll to`() {
        val rolling = numbers(rolledTodayMs = 110 * MS_PER_MINUTE, rolledWeekMs = 380 * MS_PER_MINUTE, rolledStreakDays = 6, rolledMonthMs = 1_027 * MS_PER_MINUTE)
        val settled = rolling.settled()
        assertEquals(130 * MS_PER_MINUTE, settled.rolledTodayMs)
        assertEquals(400 * MS_PER_MINUTE, settled.rolledWeekMs)
        assertEquals(8, settled.rolledStreakDays)
        assertEquals(1_047 * MS_PER_MINUTE, settled.rolledMonthMs)
    }

    @Test
    fun `every frame of a roll settles to the same numbers`() {
        val early = numbers(rolledTodayMs = 110 * MS_PER_MINUTE, rolledWeekMs = 380 * MS_PER_MINUTE, rolledStreakDays = 6, rolledMonthMs = 1_027 * MS_PER_MINUTE)
        val late = numbers(rolledTodayMs = 129 * MS_PER_MINUTE, rolledWeekMs = 399 * MS_PER_MINUTE, rolledStreakDays = 8, rolledMonthMs = 1_046 * MS_PER_MINUTE)
        assertNotEquals(early, late)
        assertEquals(early.settled(), late.settled())
    }
}
