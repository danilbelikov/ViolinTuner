package com.violinjourney.app.core.di

/** Milliseconds that only go forward; the wall clock may jump. */
fun interface ElapsedClock {
    fun nowMs(): Long
}
