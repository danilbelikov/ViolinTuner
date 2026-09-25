package com.violinjourney.app.core.time

/** `CLOCK_MONOTONIC`, what `AudioTimestamp.TIMEBASE_MONOTONIC` is counted on. */
actual fun monotonicNanos(): Long = System.nanoTime()
