package com.violinjourney.app.core.audio.playback

/**
 * «Оригинал ↔ обработка» while the sound plays (spec 3.17): a crossfade between the recording as
 * it is and what the chain made of it — no click, no jump of the position. The chain hands its
 * sound over [latencySamples] late (its limiter looks ahead), so the original is delayed by as
 * much here: otherwise the two would be a comb filter for the length of the fade.
 *
 * The original goes through the delay always, faded or not — the line has to hold the right
 * samples the moment a fade begins. Delayed is still bit for bit.
 */
class AbMixer(latencySamples: Int, private val fadeSamples: Int) {
    private val delay = FloatArray(latencySamples.coerceAtLeast(1))
    private val delayed = latencySamples > 0
    private var index = 0

    /** Share of the processed sound: 0 — the original, 1 — the processing. */
    var processedShare = 0f
        private set

    /** Where the share is going; it gets there in [fadeSamples]. */
    var target = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    /** True while the original alone is heard and is meant to be: the chain need not run at all. */
    val originalOnly: Boolean get() = processedShare == 0f && target == 0f

    /** Without a fade: the state a recording starts to play in. */
    fun jumpTo(share: Float) {
        target = share
        processedShare = target
    }

    fun reset() {
        delay.fill(0f)
        index = 0
    }

    /** The original alone, through the delay; [dry] is changed in place. */
    fun passOriginal(dry: FloatArray, count: Int) {
        if (!delayed) return
        for (i in 0 until count) dry[i] = swap(dry[i])
    }

    /** Mixes into [wet] in place: what the chain made of these very samples of [dry]. */
    fun mix(dry: FloatArray, wet: FloatArray, count: Int) {
        val step = 1f / fadeSamples.coerceAtLeast(1)
        for (i in 0 until count) {
            val original = if (delayed) swap(dry[i]) else dry[i]
            if (processedShare < target) {
                processedShare = (processedShare + step).coerceAtMost(target)
            } else if (processedShare > target) {
                processedShare = (processedShare - step).coerceAtLeast(target)
            }
            wet[i] = if (processedShare >= 1f) wet[i] else original * (1f - processedShare) + wet[i] * processedShare
        }
    }

    private fun swap(sample: Float): Float {
        val out = delay[index]
        delay[index] = sample
        if (++index == delay.size) index = 0
        return out
    }
}
