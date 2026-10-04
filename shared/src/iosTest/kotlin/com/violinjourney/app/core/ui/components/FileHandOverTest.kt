package com.violinjourney.app.core.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.IOException

/**
 * The hand-over of a file the library lends (spec 3.19, 0.94) in every order a stop can come in: the copy is made only while somebody
 * waits, and the one nobody takes goes — made while the wait was stopped, or finished a moment before the stopped wait would take it.
 */
class FileHandOverTest {
    private val letGo = mutableListOf<String>()
    private val failure = { IOException("no file") }

    @Test
    fun `the copy handed over is the one the wait gets`() = runTest {
        val handOver = FileHandOver(letGo::add)
        val wait = async { handOver.await(stop = { fail("nothing stops") }) }
        runCurrent()
        handOver.hand(copy = { "copy-a" }, failure = failure)
        assertEquals("copy-a", wait.await())
        assertTrue(letGo.isEmpty())
    }

    @Test
    fun `a copy finished a moment before the stopped wait would take it goes`() = runTest {
        val handOver = FileHandOver(letGo::add)
        var stopped = 0
        val wait = async(start = CoroutineStart.UNDISPATCHED) { handOver.await(stop = { stopped++ }) }
        // the wait is stopped while the copy is made: the copy is in before the stopped wait gets to run
        handOver.hand(copy = { wait.cancel(); "copy-b" }, failure = failure)
        runCurrent()
        assertTrue(wait.isCancelled)
        assertEquals(1, stopped, "the library is told to stop")
        assertEquals(listOf("copy-b"), letGo)
    }

    @Test
    fun `a copy finished after the stopped wait has gone goes`() = runTest {
        val handOver = FileHandOver(letGo::add)
        val wait = async(start = CoroutineStart.UNDISPATCHED) { handOver.await(stop = {}) }
        handOver.hand(
            copy = {
                // the stop is through before the copy is: nobody will take it
                wait.cancel()
                testScheduler.runCurrent()
                "copy-c"
            },
            failure = failure,
        )
        assertTrue(wait.isCancelled)
        assertEquals(listOf("copy-c"), letGo)
    }

    @Test
    fun `nothing is copied for a wait that has stopped`() = runTest {
        val handOver = FileHandOver(letGo::add)
        val wait = async(start = CoroutineStart.UNDISPATCHED) { handOver.await(stop = {}) }
        wait.cancel()
        runCurrent()
        handOver.hand(copy = { fail("no copy for nobody") }, failure = failure)
        assertTrue(letGo.isEmpty())
    }

    @Test
    fun `a library that gives nothing is the failure it says`() = runTest {
        val handOver = FileHandOver(letGo::add)
        handOver.hand(copy = { null }, failure = failure)
        assertFailsWith<IOException> { handOver.await(stop = {}) }
        assertTrue(letGo.isEmpty())
    }
}
