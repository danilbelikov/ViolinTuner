package com.violinjourney.app.core.audio

import com.violinjourney.app.core.domain.IntonationConfig

/**
 * A blocking read of the microphone that gives no samples at all: it happens only when the recorder
 * was stopped or interrupted under the reader, and it does not block — a loop around it would spin
 * a core and never reach [DigitalSilenceWatchdog], which counts samples. So the reader waits one hop
 * ([waitMs]) after each empty read, and gives the input up after [IntonationConfig.digitalSilenceTimeoutMs]
 * of them, the same two seconds as a digitally silent input (spec 3.4): it is reopened, and Live says
 * «Микрофон недоступен» meanwhile.
 */
class EmptyReadWatch(config: IntonationConfig, sampleRateHz: Int) {
    /** How long to wait after an empty read: one hop, and never nothing. */
    val waitMs: Long = (config.hopSizeSamples * MS_PER_SECOND / sampleRateHz).coerceAtLeast(1)
    private val limitMs = config.digitalSilenceTimeoutMs
    private var emptyReads = 0L

    /** Takes the count a read returned; true once the reads have been empty for too long. */
    fun isDead(count: Int): Boolean {
        if (count > 0) {
            emptyReads = 0
            return false
        }
        emptyReads++
        return emptyReads * waitMs > limitMs
    }

    private companion object {
        const val MS_PER_SECOND = 1_000L
    }
}
