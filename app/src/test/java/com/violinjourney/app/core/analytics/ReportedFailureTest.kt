package com.violinjourney.app.core.analytics

import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A handled exception as it goes to the statistics: its class and frames, its messages without names of files (spec 3.34). */
class ReportedFailureTest {
    @Test
    fun `the class and the frames stay and the names of files go`() {
        val original = IOException("/data/user/0/com.violinjourney.app/files/sessions/a.m4a: open failed: ENOENT", IllegalStateException("“Соната.zip” is gone"))
        val reported = original.reported()
        assertEquals("java.io.IOException: <path>: open failed: ENOENT", reported.message)
        assertArrayEquals(original.stackTrace, reported.stackTrace)
        assertEquals("java.lang.IllegalStateException: <name> is gone", reported.cause!!.message)
        assertArrayEquals(original.cause!!.stackTrace, reported.cause!!.stackTrace)
    }

    @Test
    fun `an exception without a message is its class`() {
        assertEquals("java.lang.IllegalStateException", IllegalStateException().reported().message)
    }

    @Test
    fun `a chain of causes that loops is cut`() {
        val first = IllegalStateException("a")
        val second = IllegalArgumentException("b", first)
        first.initCause(second)
        var depth = 0
        var at: Throwable? = first.reported()
        while (at != null) {
            depth++
            at = at.cause
        }
        assertTrue("was $depth", depth in 2..10)
    }
}
