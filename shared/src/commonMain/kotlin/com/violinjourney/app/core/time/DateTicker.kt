package com.violinjourney.app.core.time

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * The local date, at once and anew at every local midnight while the flow is collected: a screen that stays open
 * overnight wakes up on the new day. It sleeps until midnight rather than polling; a zone or a clock changed meanwhile
 * is picked up by the next wake, or when the screen collects it again — it stops being collected in the background.
 */
fun WallClock.dates(): Flow<LocalDate> = flow {
    while (true) {
        val now = instant()
        val today = now.toLocalDateTime(zone).date
        emit(today)
        val midnight = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
        delay((midnight - now).inWholeMilliseconds.coerceAtLeast(1))
    }
}.distinctUntilChanged()

/**
 * «Now», at once and anew at the moment [next] names for it — the next midnight, the start or the end of an event the reminder of
 * «Занятия» holds (spec 3.36.9, plan D9): what is worked out from the moment is worked out again exactly when it can change, and
 * never in between. It sleeps rather than polls, like [dates]: [next] reads the zone it needs when it is asked, at each wake, so a
 * zone or a clock changed meanwhile is picked up by the next wake — or by the next collection, which asks the clock at once («Занятия»
 * collect it anew when the screen comes back). [next] gives a moment after the one it is asked of; one that is not lets a millisecond
 * pass.
 */
fun WallClock.ticksAt(next: (Instant) -> Instant): Flow<Instant> = flow {
    while (true) {
        val now = instant()
        emit(now)
        // rounded up to the millisecond: the wake is never before the moment, so what it brings has already come
        delay((next(now) - now).coerceAtLeast(1.milliseconds))
    }
}
