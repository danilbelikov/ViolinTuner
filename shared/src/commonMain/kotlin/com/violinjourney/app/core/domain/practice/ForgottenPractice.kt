package com.violinjourney.app.core.domain.practice

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
        if (elapsed > config.maxPracticeMs) return PracticeCheck.Expired(expiredEndOf(running, config))
        val quietSince = running.lastSoundEpochMs ?: running.startedAtEpochMs
        if (nowEpochMs - quietSince > config.forgottenAfterMs) {
            return PracticeCheck.Forgotten(elapsed, running.lastSoundEpochMs)
        }
        return PracticeCheck.Running
    }

    /**
     * Where a practice past the limit ended by itself (spec 3.12): at its last sound, or an hour after its start when
     * the violin never sounded — never beyond the limit.
     */
    fun expiredEndOf(running: RunningPractice, config: PracticeConfig): Long =
        (running.lastSoundEpochMs ?: (running.startedAtEpochMs + config.forgottenAfterMs))
            .coerceIn(running.startedAtEpochMs, running.startedAtEpochMs + config.maxPracticeMs)

    /**
     * How long [running] counts when it is ended at [endEpochMs] — «Закончить занятие», «Закончить сейчас», «Изменить
     * время»: up to that end, as long as it is within the limit. A practice does not run longer (spec 3.12): one ended
     * past the limit had ended by itself at [expiredEndOf], so it counts up to its last sound, not twelve hours. Never
     * negative: a clock moved backwards counts nothing.
     */
    fun lengthAt(running: RunningPractice, endEpochMs: Long, config: PracticeConfig): Long {
        val length = running.elapsedMs(endEpochMs)
        return if (length > config.maxPracticeMs) expiredEndOf(running, config) - running.startedAtEpochMs else length
    }

    /**
     * Whether a practice begun at [startedAtEpochMs] still runs at [atEpochMs]. Past the limit it has ended by itself
     * (spec 3.12): a sound or a note heard then is not of it, and must not move its end.
     */
    fun runsAt(startedAtEpochMs: Long, atEpochMs: Long, config: PracticeConfig): Boolean =
        atEpochMs - startedAtEpochMs <= config.maxPracticeMs
}
