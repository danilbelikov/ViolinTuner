package com.violinjourney.app.core.analytics

import com.violinjourney.app.core.concurrent.PlatformLock
import com.violinjourney.app.core.concurrent.withLock

/**
 * The consent to the statistics as the app last read it from its settings (spec 3.34, rule 2), held by the app itself and
 * not only by the flag of the library: whether a muted library keeps what it is given and sends it once unmuted is its own
 * business, so it is given nothing it may not send. Until the stored choice has been read, what comes — the first screen,
 * a crash of the last run told at this start — waits for it; while the choice is off, nothing reaches the library at all.
 */
class ConsentGate {
    private val lock = PlatformLock()

    /** Null until the settings have been read. */
    private var consent: Boolean? = null
    private val waiting = ArrayList<() -> Unit>()

    /** True only when the consent has been read and is on. */
    val open: Boolean get() = lock.withLock { consent == true }

    /** Runs [send] now when the consent is on; once it has been read, if it has not been yet and turns out on; never while it is off. */
    fun pass(send: () -> Unit) {
        val now = lock.withLock {
            when (consent) {
                true -> true
                false -> false
                null -> {
                    if (waiting.size < MAX_WAITING) waiting += send
                    false
                }
            }
        }
        if (now) send()
    }

    /** The stored consent, read anew: switched off, the gate closes before the library is told; switched on, it opens after. */
    fun follow(enabled: Boolean, apply: (Boolean) -> Unit) {
        if (!enabled) {
            lock.withLock {
                consent = false
                waiting.clear()
            }
            apply(false)
            return
        }
        apply(true)
        val released = lock.withLock {
            consent = true
            waiting.toList().also { waiting.clear() }
        }
        released.forEach { it() }
    }

    private companion object {
        /** The settings are read within the first moments of a start: more than this waiting is not a start that went well. */
        const val MAX_WAITING = 64
    }
}
