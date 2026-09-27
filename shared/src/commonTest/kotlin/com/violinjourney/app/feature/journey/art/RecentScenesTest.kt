package com.violinjourney.app.feature.journey.art

import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

class RecentScenesTest {
    private fun cache() = RecentScenes<String, String>(budget = 10) { it.length }

    @Test
    fun `the eldest goes when the whole is over the budget - a look keeps it`() {
        val scenes = cache()
        scenes.put("a", "aaaa")
        scenes.put("b", "bbbb")
        scenes.put("c", "cccc")
        assertNull(scenes.peek("a"), "twelve of ten: the first put goes")
        assertEquals("bbbb", scenes.peek("b"))

        val seen = cache()
        seen.put("a", "aaaa")
        seen.put("b", "bbbb")
        seen.get("a")
        seen.put("c", "cccc")
        assertEquals("aaaa", seen.peek("a"), "the one looked at stays")
        assertNull(seen.peek("b"), "the one not looked at since goes instead")
        // a peek is only a look: it does not save the eldest
        seen.peek("a")
        seen.put("d", "dddd")
        assertNull(seen.peek("a"))
    }

    @Test
    fun `a picture heavier than the budget is kept alone - one put again weighs once`() {
        val scenes = cache()
        scenes.put("a", "aa")
        scenes.put("big", "b".repeat(15))
        assertNull(scenes.peek("a"))
        assertEquals(15, scenes.peek("big")?.length)
        val again = cache()
        repeat(5) { again.put("a", "aaaa") }
        again.put("b", "bbbb")
        assertEquals("aaaa", again.peek("a"), "a key put five times weighs four, not twenty")
    }

    @Test
    fun `two callers of one key at once make it once and get the same`() = runTest {
        val scenes = cache()
        var made = 0
        val make: suspend () -> String = {
            delay(100)
            made++
            "v$made"
        }
        val first = async { scenes.obtain("k", EmptyCoroutineContext, make) }
        val second = async { scenes.obtain("k", EmptyCoroutineContext, make) }
        assertEquals("v1", first.await())
        assertEquals("v1", second.await())
        assertEquals(1, made)
        assertEquals("v1", scenes.obtain("k", EmptyCoroutineContext, make))
        assertEquals(1, made)
    }

    @Test
    fun `nothing made is not kept - the next caller tries again`() = runTest {
        val scenes = cache()
        var asked = 0
        assertNull(scenes.obtain("k", EmptyCoroutineContext) { asked++; null })
        assertEquals("v", scenes.obtain("k", EmptyCoroutineContext) { asked++; "v" })
        assertEquals(2, asked)
    }

    @Test
    fun `a first maker cancelled before it made anything lets the next one make it`() = runTest {
        val scenes = cache()
        val started = CompletableDeferred<Unit>()
        val first = launch {
            scenes.obtain("k", EmptyCoroutineContext) {
                started.complete(Unit)
                delay(1_000)
                "never"
            }
        }
        started.await()
        first.cancel()
        assertEquals("v", scenes.obtain("k", EmptyCoroutineContext) { "v" })
        assertEquals("v", scenes.peek("k"))
    }

    @Test
    fun `what is made is kept even when its caller has gone meanwhile`() = runTest {
        val scenes = cache()
        // the caller is cancelled while the work runs on (a fling of the ribbon mid-parse): it gets no answer, but the
        // work ends and what it made stays for the next one
        assertFailsWith<CancellationException> {
            scenes.obtain("k", EmptyCoroutineContext) {
                currentCoroutineContext().cancel()
                "v"
            }
        }
        assertEquals("v", scenes.peek("k"))
    }
}
