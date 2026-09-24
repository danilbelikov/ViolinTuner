package com.violinjourney.app.core.concurrent

/** A reentrant lock for state several threads share (pictures parsed once, the take of the audio thread): what `synchronized` was on the JVM. */
expect class PlatformLock() {
    fun lock()

    fun unlock()
}

/** Runs [block] under the lock; inline, so a `return` inside it returns from the caller, as in `synchronized`. */
inline fun <T> PlatformLock.withLock(block: () -> T): T {
    lock()
    try {
        return block()
    } finally {
        unlock()
    }
}
