package com.violinjourney.app.core.time

import com.violinjourney.app.core.audio.backing.HostClock

/** The host clock, what the blocks of the microphone are stamped on. */
actual fun monotonicNanos(): Long = HostClock.nowNanos()
