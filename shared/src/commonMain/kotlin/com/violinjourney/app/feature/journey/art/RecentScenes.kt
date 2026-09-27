package com.violinjourney.app.feature.journey.art

import com.violinjourney.app.core.concurrent.PlatformLock
import com.violinjourney.app.core.concurrent.withLock
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Pictures made ready to be drawn, kept while there is room for them (docs/notes/journey.md): at most [budget] of
 * [weigh] in all — layers — the one seen longest ago going first; a picture heavier than the whole budget is still
 * kept, alone. Two callers that ask for one key at once make it once: the second waits for the first and finds it
 * here. What is made is kept even when the one who asked for it has gone meanwhile (a fling of the ribbon, a screen
 * left): it is put here inside the work, before the caller would have resumed; a first maker cancelled before it made
 * anything lets the next one make it.
 */
internal class RecentScenes<K : Any, V : Any>(private val budget: Int, private val weigh: (V) -> Int) {
    private class Gate {
        val mutex = Mutex()
        var users = 0
    }

    private val lock = PlatformLock()

    /** In the order they were last seen, the eldest first. */
    private val entries = LinkedHashMap<K, V>()
    private var weight = 0
    private val gates = HashMap<K, Gate>()

    /** What is kept for [key], without making it the latest seen: for the first frame of a picture. */
    fun peek(key: K): V? = lock.withLock { entries[key] }

    /** What is kept for [key], now the latest seen. */
    fun get(key: K): V? = lock.withLock {
        val value = entries.remove(key) ?: return@withLock null
        entries[key] = value
        value
    }

    /** Keeps [value] for [key] as the latest seen, and lets the eldest go while the whole is over the budget. */
    fun put(key: K, value: V) = lock.withLock {
        entries.remove(key)?.let { weight -= weigh(it) }
        entries[key] = value
        weight += weigh(value)
        val eldest = entries.entries.iterator()
        while (weight > budget) {
            val entry = eldest.next()
            if (entry.key == key) break
            weight -= weigh(entry.value)
            eldest.remove()
        }
    }

    /**
     * What is kept for [key], or made by [make] in [context] and kept; null when [make] has nothing (such an answer is
     * not kept: the next caller tries again). The platform lock is never held across a suspension: a key's own [Mutex]
     * lets one maker at a time through.
     */
    suspend fun obtain(key: K, context: CoroutineContext, make: suspend () -> V?): V? {
        get(key)?.let { return it }
        val gate = lock.withLock { gates.getOrPut(key) { Gate() }.also { it.users++ } }
        try {
            return gate.mutex.withLock {
                get(key) ?: withContext(context) { get(key) ?: make()?.also { put(key, it) } }
            }
        } finally {
            lock.withLock { if (--gate.users == 0) gates.remove(key) }
        }
    }
}
