package com.violinjourney.app.core.time

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The time and the zone the app lives in — what java.time.Clock was on Android, for code shared with iOS: a moment
 * and a zone to read dates in. Injected wherever «now» or «today» is read, so that tests fix both.
 */
interface WallClock {
    fun instant(): Instant

    val zone: TimeZone

    fun millis(): Long = instant().toEpochMilliseconds()
}

/** The local date of the clock's moment in its zone. */
fun WallClock.today(): LocalDate = instant().toLocalDateTime(zone).date

/** The device: its time, and its zone as it is at the moment asked — a trip across zones is followed. */
object SystemWallClock : WallClock {
    override fun instant(): Instant = Clock.System.now()

    override val zone: TimeZone get() = TimeZone.currentSystemDefault()
}

/** One moment that never moves, in [zone]: for tests and previews. */
class FixedWallClock(private val instant: Instant, override val zone: TimeZone = TimeZone.UTC) : WallClock {
    override fun instant(): Instant = instant
}

/** The device's time read in a zone of one's own — java's Clock.systemUTC(): code that must not depend on where it runs. */
class ZonedSystemWallClock(override val zone: TimeZone) : WallClock {
    override fun instant(): Instant = Clock.System.now()
}
