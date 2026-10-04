package com.violinjourney.app.core.ui.components

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * A file the system lends on a queue of its own, handed to the one coroutine that waits for it (spec 3.19): the app's copy is made only
 * while somebody waits, and a copy nobody takes is let go by [letGo] — one made while the wait was being stopped, or one finished a
 * moment before the stopped wait would have taken it. Whoever comes second lets it go.
 */
internal class FileHandOver(private val letGo: (copy: String) -> Unit) {
    private val handed = CompletableDeferred<String>()

    /** On the system's queue, while the lent file lives: [copy] makes the app's copy — not at all when nobody waits; null is a failure, [failure] says which. */
    fun hand(copy: () -> String?, failure: () -> Throwable) {
        // stopped meanwhile: nothing to keep
        if (handed.isCompleted) return
        val made = copy()
        when {
            made == null -> handed.completeExceptionally(failure())
            !handed.complete(made) -> letGo(made)
        }
    }

    /** The copy, once handed; throws what [hand] failed with. Cancelled, it tells the system by [stop]. */
    suspend fun await(stop: () -> Unit): String = try {
        handed.await()
    } catch (e: CancellationException) {
        // closed before the system is told: a hand-over on its way finds it closed and copies nothing
        val late = !handed.completeExceptionally(e)
        stop()
        if (late) copyOrNull()?.let(letGo)
        throw e
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun copyOrNull(): String? = if (handed.isCompleted) runCatching { handed.getCompleted() }.getOrNull() else null
}
