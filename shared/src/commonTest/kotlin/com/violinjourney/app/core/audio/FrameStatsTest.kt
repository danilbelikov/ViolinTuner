package com.violinjourney.app.core.audio

import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.PitchFrame
import kotlin.test.Test
import kotlin.test.assertEquals

class FrameStatsTest {
    private val config = IntonationConfig()

    @Test
    fun `numbers are written as the old logcat line wrote them`() {
        assertEquals("-12.3", FrameStats.fixed(-12.34, 1))
        assertEquals("0.97", FrameStats.fixed(0.9701, 2))
        assertEquals("440.0", FrameStats.fixed(440.0, 1))
        assertEquals("0.05", FrameStats.fixed(0.049, 2))
        assertEquals("-Infinity", FrameStats.fixed(Double.NEGATIVE_INFINITY, 1))
        assertEquals("0.0", FrameStats.fixed(-0.01, 1))
    }

    @Test
    fun `one line a second — with the confident notes in order`() {
        val lines = ArrayList<String>()
        val stats = FrameStats(config, "src=9 rate=48000") { lines += it }
        for (i in 0..100) {
            val midi = if (i < 50) 69 else 67
            stats.add(PitchFrame.pitched(tMs = i * 10L, freqHz = if (midi == 69) 440.0 else 392.0, clarity = 0.97, rms = 0.1, a4Hz = 440.0))
        }
        assertEquals(1, lines.size)
        val line = lines.single()
        assertEquals(true, line.startsWith("src=9 rate=48000 t=1s frames=101 confident=101 peakRms=-20.0 dBFS peakClarity=0.97 lastHz=392.0"), line)
        assertEquals(true, line.contains("midi={67=51, 69=50}"), line)
    }
}
