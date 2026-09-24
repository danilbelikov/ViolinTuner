package com.violinjourney.app.core.domain.practice

import com.violinjourney.app.core.time.WallClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

private const val MS_PER_SECOND = 1_000L

/**
 * How long the running practice has been going, refreshed on every second of the wall clock;
 * null while none runs. Nothing accumulates in memory: the time is always the clock minus the
 * stored start, so a restart of the app or the phone changes nothing (spec 3.12).
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun RunningPracticeStore.elapsedTicker(clock: WallClock): Flow<Long?> = running.flatMapLatest { running ->
    if (running == null) flowOf(null) else ticking(running, clock)
}

private fun ticking(running: RunningPractice, clock: WallClock): Flow<Long> = flow {
    while (true) {
        val now = clock.millis()
        emit(running.elapsedMs(now))
        delay(MS_PER_SECOND - now % MS_PER_SECOND)
    }
}
