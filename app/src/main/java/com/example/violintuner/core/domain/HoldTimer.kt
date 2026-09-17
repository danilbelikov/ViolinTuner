package com.example.violintuner.core.domain

/** Progress 0..1 of the hold ring: full after [IntonationConfig.holdFillMs] in tune (spec 3.3). */
class HoldTimer(private val config: IntonationConfig) {
    private var startMs: Long? = null

    fun update(tMs: Long, inTune: Boolean): Double {
        if (!inTune) {
            reset()
            return 0.0
        }
        val start = startMs ?: tMs.also { startMs = it }
        return ((tMs - start).toDouble() / config.holdFillMs).coerceIn(0.0, 1.0)
    }

    fun reset() {
        startMs = null
    }
}
