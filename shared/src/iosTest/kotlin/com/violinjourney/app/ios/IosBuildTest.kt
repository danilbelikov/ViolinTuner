package com.violinjourney.app.ios

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The owner's app is told by its bundle id, whatever its Kotlin binary; statistics follow it (spec 5.27). */
class IosBuildTest {
    @Test
    fun `the debug bundle is the owner app`() {
        assertTrue(IosBuild.isDevBundle("com.violinjourney.app.debug"))
    }

    @Test
    fun `the store bundle is not the owner app`() {
        assertFalse(IosBuild.isDevBundle("com.violinjourney.app"))
        // a test executable without a bundle
        assertFalse(IosBuild.isDevBundle(null))
    }

    @Test
    fun `the owner app is silent without the flag`() {
        assertFalse(IosBuild.sendsStatistics("key", devApp = true, analyticsInDebug = false))
    }

    @Test
    fun `the owner app sends when asked`() {
        assertTrue(IosBuild.sendsStatistics("key", devApp = true, analyticsInDebug = true))
    }

    @Test
    fun `the store app sends with a key`() {
        assertTrue(IosBuild.sendsStatistics("key", devApp = false, analyticsInDebug = false))
    }

    @Test
    fun `no key sends nothing`() {
        assertFalse(IosBuild.sendsStatistics("", devApp = false, analyticsInDebug = true))
        assertFalse(IosBuild.sendsStatistics(" ", devApp = false, analyticsInDebug = false))
        assertFalse(IosBuild.sendsStatistics("", devApp = true, analyticsInDebug = true))
    }
}
