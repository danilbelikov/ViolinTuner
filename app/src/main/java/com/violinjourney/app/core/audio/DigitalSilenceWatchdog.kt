package com.violinjourney.app.core.audio

import com.violinjourney.app.core.domain.IntonationConfig

/**
 * A live microphone never delivers exact zeros for long, not even in a silent room. A long run
 * of them means the input is cut off upstream: silenced by the system while another app holds
 * the microphone, or dropped by the emulator's host-audio bridge. Reopening the recorder is the
 * only thing that brings such an input back.
 */
class DigitalSilenceWatchdog(config: IntonationConfig, sampleRateHz: Int) {
    private val limitSamples = config.digitalSilenceTimeoutMs * sampleRateHz / MS_PER_SECOND
    private var zeroSamples = 0L

    /** Feeds the first [count] samples of [hop]; true once the input has been dead too long. */
    fun isDead(hop: ShortArray, count: Int): Boolean {
        for (i in 0 until count) {
            if (hop[i] != ZERO) {
                zeroSamples = 0
                return false
            }
        }
        zeroSamples += count
        return zeroSamples > limitSamples
    }

    private companion object {
        const val MS_PER_SECOND = 1_000L
        const val ZERO: Short = 0
    }
}
