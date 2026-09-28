package com.violinjourney.app.core.domain.practice

import com.violinjourney.app.core.time.WallClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

private const val MS_PER_SECOND = 1_000L

/** A tick of the running practice: the practice as the store holds it at [nowEpochMs]. */
data class PracticeTick(val practice: RunningPractice, val nowEpochMs: Long) {
    val elapsedMs: Long get() = practice.elapsedMs(nowEpochMs)
}

/**
 * The running practice on every second of its own time — at its start and every whole second after it, not on the seconds of the
 * wall clock; null while none runs. A second of the timer of «Занятия» turns when it is due, and a minute of «Занятие не закончено»,
 * rounded at the start + k minutes + 30 s, changes on a tick rather than up to a second after it: the button saves what it says (spec
 * 3.36.3). A mark the store takes starts it over at once. Nothing accumulates in memory: the time is always the clock minus the stored
 * start, so a restart of the app or the phone changes nothing (spec 3.12).
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun RunningPracticeStore.practiceTicks(clock: WallClock): Flow<PracticeTick?> = running.flatMapLatest { running ->
    if (running == null) flowOf(null) else ticking(running, clock)
}

/** How long the running practice has been going, on every second of it ([practiceTicks]); null while none runs. */
fun RunningPracticeStore.elapsedTicker(clock: WallClock): Flow<Long?> = practiceTicks(clock).map { it?.elapsedMs }

private fun ticking(running: RunningPractice, clock: WallClock): Flow<PracticeTick> = flow {
    while (true) {
        emit(PracticeTick(running, clock.millis()))
        // to the next whole second of the practice, from the moment the tick is out
        delay(MS_PER_SECOND - running.elapsedMs(clock.millis()) % MS_PER_SECOND)
    }
}
