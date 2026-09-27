package com.violinjourney.app.feature.home.art

import com.violinjourney.app.feature.journey.art.SceneMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

class HomeTimeRulesTest {
    private val berlin = TimeZone.of("Europe/Berlin")

    private fun local(text: String) = LocalDateTime.parse(text)
    private fun at(text: String): Instant = local(text).toInstant(berlin)

    @Test
    fun `day is from seven to nineteen by the phone clock and the date is the local one`() {
        assertEquals(HomeTime(SceneMode.EVENING, LocalDate(2026, 9, 27)), HomeTimeRules.at(local("2026-09-27T06:59:59")))
        assertEquals(SceneMode.DAY, HomeTimeRules.at(local("2026-09-27T07:00")).mode)
        assertEquals(SceneMode.DAY, HomeTimeRules.at(local("2026-09-27T18:59:59")).mode)
        assertEquals(SceneMode.EVENING, HomeTimeRules.at(local("2026-09-27T19:00")).mode)
        assertEquals(HomeTime(SceneMode.EVENING, LocalDate(2026, 12, 1)), HomeTimeRules.at(local("2026-12-01T00:00")))
    }

    @Test
    fun `the next change is seven - nineteen - or midnight whichever comes first`() {
        assertEquals(at("2026-09-27T07:00"), HomeTimeRules.nextChange(at("2026-09-27T06:30"), berlin))
        assertEquals(at("2026-09-27T19:00"), HomeTimeRules.nextChange(at("2026-09-27T12:00"), berlin))
        assertEquals(at("2026-09-28T00:00"), HomeTimeRules.nextChange(at("2026-09-27T20:00"), berlin))
        assertEquals(at("2026-09-28T00:00"), HomeTimeRules.nextChange(at("2026-09-27T23:59:59"), berlin))
        // a boundary itself is not the next change: strictly later
        assertEquals(at("2026-09-27T07:00"), HomeTimeRules.nextChange(at("2026-09-27T00:00"), berlin))
        assertEquals(at("2026-09-27T19:00"), HomeTimeRules.nextChange(at("2026-09-27T07:00"), berlin))
        // the tree comes at midnight on the first of December
        assertEquals(at("2026-12-01T00:00"), HomeTimeRules.nextChange(at("2026-11-30T21:15"), berlin))
    }

    @Test
    fun `on the day the clocks go forward seven comes by the local clock`() {
        // 29 March 2026: 02:00 becomes 03:00 in Berlin — from 01:30 to 07:00 local is four and a half hours
        val night = at("2026-03-29T01:30")
        val next = HomeTimeRules.nextChange(night, berlin)
        assertEquals(at("2026-03-29T07:00"), next)
        assertEquals(4.hours + 30.minutes, next - night)
    }
}
