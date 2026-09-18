package com.example.violintuner.core.domain.practice

/** What the app finds when it looks at a running practice (spec 3.12, "Забытое занятие"). */
sealed interface PracticeCheck {
    /** Nothing to ask: the violin sounded recently enough. */
    data object Running : PracticeCheck

    /** Ask the user; [lastSoundEpochMs] is null when the violin never sounded during it. */
    data class Forgotten(val elapsedMs: Long, val lastSoundEpochMs: Long?) : PracticeCheck

    /** Over the limit: it has ended by itself at [endEpochMs], the summary sheet reports it. */
    data class Expired(val endEpochMs: Long) : PracticeCheck
}

object ForgottenPractice {
    fun check(running: RunningPractice, nowEpochMs: Long, config: PracticeConfig): PracticeCheck {
        val elapsed = running.elapsedMs(nowEpochMs)
        if (elapsed > config.maxPracticeMs) {
            val end = running.lastSoundEpochMs ?: (running.startedAtEpochMs + config.forgottenAfterMs)
            return PracticeCheck.Expired(end.coerceIn(running.startedAtEpochMs, running.startedAtEpochMs + config.maxPracticeMs))
        }
        val quietSince = running.lastSoundEpochMs ?: running.startedAtEpochMs
        if (nowEpochMs - quietSince > config.forgottenAfterMs) {
            return PracticeCheck.Forgotten(elapsed, running.lastSoundEpochMs)
        }
        return PracticeCheck.Running
    }
}
