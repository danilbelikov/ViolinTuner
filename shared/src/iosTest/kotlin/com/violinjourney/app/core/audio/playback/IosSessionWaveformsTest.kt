package com.violinjourney.app.core.audio.playback

import com.violinjourney.app.core.audio.recording.IosAacEncoder
import com.violinjourney.app.core.io.PlatformFile
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

/** The waveform of a take on iOS: quiet first half, loud second half — and the second look reads what the first one kept. */
@OptIn(ExperimentalForeignApi::class)
class IosSessionWaveformsTest {
    private val folder = NSTemporaryDirectory() + NSUUID().UUIDString
    private val audio = "$folder/take.m4a"

    init {
        NSFileManager.defaultManager.createDirectoryAtPath(folder, true, null, null)
    }

    @AfterTest
    fun cleanUp() {
        NSFileManager.defaultManager.removeItemAtPath(folder, null)
    }

    @Test
    fun `the loud half is loud and the waveform is kept`() = runTest {
        val rate = 48_000
        val encoder = IosAacEncoder(PlatformFile(audio), rate)
        val hop = ShortArray(512)
        var n = 0
        repeat(rate / hop.size) { index ->
            val level = if (index < rate / hop.size / 2) 1_000.0 else 16_000.0
            for (i in hop.indices) hop[i] = (sin(2 * PI * 440 * (n++) / rate) * level).roundToInt().toShort()
            encoder.offer(hop, hop.size)
        }
        assertTrue(encoder.finish())

        val waveforms = IosSessionWaveforms({ folder }, Dispatchers.Default)
        val first = assertNotNull(waveforms.of(PlatformFile(audio)))
        assertEquals(SessionWaveforms.BARS, first.size)
        assertTrue(first[SessionWaveforms.BARS / 4] < 0.2f, "quiet: ${first[SessionWaveforms.BARS / 4]}")
        assertTrue(first[SessionWaveforms.BARS * 3 / 4] > 0.8f, "loud: ${first[SessionWaveforms.BARS * 3 / 4]}")
        assertTrue(NSFileManager.defaultManager.fileExistsAtPath("$folder/take.m4a.wave"))
        assertEquals(first.toList(), assertNotNull(waveforms.of(PlatformFile(audio))).toList())
    }
}
