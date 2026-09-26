package com.violinjourney.app.core.analytics

import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.exists
import com.violinjourney.app.core.io.openOutput
import com.violinjourney.app.core.io.writeBytes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

/**
 * A Kotlin crash on iOS is kept at the moment of the fall and told once at the next start (spec 3.34). The hook itself is
 * not installed here: it would end the test process on any stray exception.
 */
@OptIn(ExperimentalForeignApi::class)
class KotlinCrashesTest {
    private val folder = NSTemporaryDirectory() + NSUUID().UUIDString
    private val record = PlatformFile("$folder/last-kotlin-crash.txt")

    init {
        NSFileManager.defaultManager.createDirectoryAtPath(folder, true, null, null)
    }

    @AfterTest
    fun cleanUp() {
        NSFileManager.defaultManager.removeItemAtPath(folder, null)
    }

    private fun thrownHere(): Throwable = IllegalStateException("boom\tat\nnight \\ here", IllegalArgumentException("inner"))

    @Test
    fun `a crash kept on disk is told once at the next start`() {
        assertTrue(KotlinCrashes.keep(record, thrownHere()))
        val crash = assertNotNull(KotlinCrashes.takeKept(record))
        assertTrue(crash.type.endsWith("IllegalStateException"), crash.type)
        assertEquals("boom\tat\nnight \\ here", crash.message, "the message comes back as it was")
        assertEquals("kotlin.IllegalArgumentException: inner", crash.causes)
        assertTrue(crash.frames.isNotEmpty())
        // the test binary keeps its names: the frame where it was thrown is a Kotlin class and function
        assertTrue(crash.frames.any { it.className.endsWith("KotlinCrashesTest") && it.methodName == "thrownHere" }, "${crash.frames.take(8)}")
        assertNull(KotlinCrashes.takeKept(record), "told once")
        assertFalse(record.exists())
    }

    @Test
    fun `no crash kept tells nothing`() {
        assertNull(KotlinCrashes.takeKept(record))
    }

    @Test
    fun `a crash that cannot be kept is no second fall`() {
        val file = PlatformFile("$folder/file")
        val output = assertNotNull(file.openOutput())
        output.writeBytes("not a folder".encodeToByteArray())
        output.close()
        assertFalse(KotlinCrashes.keep(PlatformFile("$folder/file/last-kotlin-crash.txt"), thrownHere()))
    }

    @Test
    fun `a record that is not one of ours is dropped`() {
        val output = assertNotNull(record.openOutput())
        output.writeBytes("something else".encodeToByteArray())
        output.close()
        assertNull(KotlinCrashes.takeKept(record))
        assertFalse(record.exists())
    }

    @Test
    fun `a frame is named by its Kotlin class and function or by its place in the image`() {
        assertEquals("com.example.Foo" to "bar", KotlinCrashes.namesOf("kfun:com.example.Foo#bar(kotlin.Int){}", "App", 0x10))
        assertEquals("com.example.Foo" to "bar", KotlinCrashes.namesOf("kfun:com.example.Foo.bar#internal", "App", 0x10))
        assertEquals("App" to "main", KotlinCrashes.namesOf("kfun:main(){}", "App", 0x10))
        assertEquals("libsystem_kernel.dylib" to "__pthread_kill", KotlinCrashes.namesOf("__pthread_kill", "libsystem_kernel.dylib", 8))
        assertEquals("App" to "0x1f", KotlinCrashes.namesOf(null, "App", 0x1f))
    }

    @Test
    fun `the statistics get the crash as a crash of its own`() {
        val service = RecordingService()
        val frames = listOf(KotlinCrashFrame("com.example.Foo", "bar", "Foo.kt", 12))
        IosAppMetricaAnalytics(service).crashed(KotlinCrash("kotlin.IllegalStateException", "boom", "kotlin.IllegalArgumentException: inner", frames))
        val told = service.crashes.single()
        assertEquals("kotlin.IllegalStateException", told.type)
        assertEquals("boom", told.message)
        assertEquals(frames, told.frames)
        assertEquals("kotlin.IllegalArgumentException: inner", told.environment["caused by"])
        assertEquals(KotlinVersion.CURRENT.toString(), told.environment["kotlin"])
    }

    private class Told(val type: String, val message: String?, val frames: List<KotlinCrashFrame>, val environment: Map<String, String>)

    private class RecordingService : AnalyticsService {
        val crashes = mutableListOf<Told>()

        override fun activate(apiKey: String, logs: Boolean) = Unit

        override fun setDataSendingEnabled(enabled: Boolean) = Unit

        override fun reportEvent(name: String, params: Map<String, Any>) = Unit

        override fun reportError(group: String, message: String, cause: String?) = Unit

        override fun reportUnhandledException(type: String, message: String?, frames: List<KotlinCrashFrame>, environment: Map<String, String>) {
            crashes += Told(type, message, frames, environment)
        }
    }
}
