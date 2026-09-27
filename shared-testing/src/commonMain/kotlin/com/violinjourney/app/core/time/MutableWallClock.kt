package com.violinjourney.app.core.time

import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

/** A clock the test moves by hand ([nowMs]); the delays of what reads it run on the test scheduler. */
@OptIn(ExperimentalTime::class)
class MutableWallClock(var nowMs: Long, override val zone: TimeZone = TimeZone.UTC) : WallClock {
    override fun instant(): Instant = Instant.fromEpochMilliseconds(nowMs)
}
