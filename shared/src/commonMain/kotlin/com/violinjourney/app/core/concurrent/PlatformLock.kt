package com.violinjourney.app.core.concurrent

/** A lock for the small caches several threads read and fill (pictures parsed once): what @Synchronized was on the JVM. */
expect class PlatformLock() {
    fun <T> withLock(block: () -> T): T
}
