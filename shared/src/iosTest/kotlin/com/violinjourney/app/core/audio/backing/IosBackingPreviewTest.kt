package com.violinjourney.app.core.audio.backing

import com.violinjourney.app.core.audio.IosAudioSession
import com.violinjourney.app.core.audio.recording.IosAacEncoder
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.io.openOutput
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

/**
 * The backing listened to on the piece screen on iOS (spec 3.32) sounds in the category for playing whatever the session
 * was before — a fresh process has one the silent switch mutes — and holds the app's session only while it sounds. The
 * process of the tests has no sound output: whether it is heard is for a device.
 */
@OptIn(ExperimentalForeignApi::class)
class IosBackingPreviewTest {
    private val path = NSTemporaryDirectory() + NSUUID().UUIDString + ".m4a"

    @AfterTest
    fun cleanUp() {
        NSFileManager.defaultManager.removeItemAtPath(path, null)
    }

    @Test
    fun `the backing listened to sounds in the playback category and lets the session go when it stops`() {
        writeTone()
        val session = AVAudioSession.sharedInstance()
        // what a fresh process has, not what an earlier test left: the silent switch mutes it
        assertTrue(session.setCategory(AVAudioSessionCategoryAmbient, null))
        assertEquals(AVAudioSessionCategoryAmbient, session.category)
        val before = IosAudioSession.count

        val preview = IosBackingPreview()
        preview.toggle(PlatformFile(path))
        assertEquals(AVAudioSessionCategoryPlayback, session.category, "set for playing before it plays")
        if (preview.playing.value) assertEquals(before + 1, IosAudioSession.count, "held while it sounds")
        preview.stop()
        assertFalse(preview.playing.value)
        assertEquals(before, IosAudioSession.count, "let go when it stops")
    }

    @Test
    fun `a file that is no sound starts nothing and holds no session`() {
        val junk = ByteArray(4_096) { 7 }
        PlatformFile(path).openOutput()!!.use { it.write(junk, 0, junk.size) }
        val before = IosAudioSession.count

        val preview = IosBackingPreview()
        preview.toggle(PlatformFile(path))
        assertFalse(preview.playing.value)
        assertEquals(before, IosAudioSession.count)
    }

    private fun writeTone() {
        val rate = 48_000
        val encoder = IosAacEncoder(PlatformFile(path), rate)
        val hop = ShortArray(512)
        var n = 0
        repeat(rate / hop.size) {
            for (i in hop.indices) hop[i] = (sin(2 * PI * 440 * (n++) / rate) * 8_000).roundToInt().toShort()
            encoder.offer(hop, hop.size)
        }
        assertTrue(encoder.finish())
    }
}
