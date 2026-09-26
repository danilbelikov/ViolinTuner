package com.violinjourney.app.core.concurrent

/**
 * A lock per key: work on one key waits for another thread's work on the same key, work on different keys does not wait.
 * An entry lives only while someone holds it or waits for it, so the map does not grow with every key ever used.
 * Reentrant, as [PlatformLock] is: a thread already holding a key passes again.
 */
internal class KeyedLock<K : Any> {
    private class Entry {
        val lock = PlatformLock()
        var users = 0
    }

    private val guard = PlatformLock()
    private val entries = HashMap<K, Entry>()

    /** Runs [block] under the lock of [key]; both locks are let go when it throws. */
    fun <T> withLock(key: K, block: () -> T): T {
        val entry = guard.withLock { entries.getOrPut(key, ::Entry).also { it.users++ } }
        try {
            return entry.lock.withLock(block)
        } finally {
            guard.withLock { if (--entry.users == 0) entries.remove(key) }
        }
    }
}
