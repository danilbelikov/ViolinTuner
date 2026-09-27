package com.violinjourney.app.core.time

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
