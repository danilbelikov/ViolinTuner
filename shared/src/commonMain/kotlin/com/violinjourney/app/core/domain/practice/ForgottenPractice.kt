package com.violinjourney.app.core.domain.practice

import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_MINUTE

/** What the app finds when it looks at a running practice (spec 3.12, "Забытое занятие"). */
sealed interface PracticeCheck {
    /** Nothing to ask: the violin sounded recently enough. */
    data object Running : PracticeCheck

    /** Ask the user; [lastSoundEpochMs] is null when the violin never sounded during it. */
    data class Forgotten(val elapsedMs: Long, val lastSoundEpochMs: Long?) : PracticeCheck

    /** Over the limit: it has ended by itself at [endEpochMs], the summary sheet reports it. */
    data class Expired(val endEpochMs: Long) : PracticeCheck
}

/**
 * What «Занятие не закончено» says at a moment (spec 3.36.3), each rounded to the minute as 5.6 shows a length — so the numbers
 * change once a minute, not every second. [runningMs] — «Идёт 3 ч 12 мин», never past the limit; [endNowMs] — what «Закончить
 * сейчас» would save, by the same rule the save uses ([ForgottenPractice.lengthAt]); [endAtMarkMs] — what «Закончить в 18:42» would
 * save, null without a mark. A length under a minute is null too: the practice would not be kept («Слишком коротко»), and its
 * button has no number. The saves themselves take the exact time of the answer.
 */
data class ForgottenEndings(val runningMs: Long, val endNowMs: Long?, val endAtMarkMs: Long?)

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
     * How long [running] counts when it is ended at [endEpochMs] — «Закончить занятие», «Закончить сейчас», «Указать,
     * сколько играли»: up to that end, as long as it is within the limit. A practice does not run longer (spec 3.12): one ended
     * past the limit had ended by itself at [expiredEndOf], so it counts up to its last sound, not twelve hours. Never
     * negative: a clock moved backwards counts nothing.
     */
    fun lengthAt(running: RunningPractice, endEpochMs: Long, config: PracticeConfig): Long {
        val length = running.elapsedMs(endEpochMs)
        return if (length > config.maxPracticeMs) expiredEndOf(running, config) - running.startedAtEpochMs else length
    }

    /**
     * The numbers of «Занятие не закончено» about [running] at [nowEpochMs] ([ForgottenEndings]), each by what its answer saves:
     * «Идёт» and «Закончить сейчас» follow [running] as the store holds it now — a sound Live marks under the sheet moves where a
     * practice past the limit ends, and «Закончить сейчас» saves by that; «Закончить в 18:42» ends at the mark of [asked], the
     * practice as the question saw it, whatever was marked since.
     */
    fun endings(running: RunningPractice, nowEpochMs: Long, config: PracticeConfig, asked: RunningPractice = running): ForgottenEndings {
        fun kept(lengthMs: Long): Long? = lengthMs.takeIf { it >= config.minPracticeMs }?.let(::toMinute)
        return ForgottenEndings(
            runningMs = toMinute(running.elapsedMs(nowEpochMs).coerceAtMost(config.maxPracticeMs)),
            endNowMs = kept(lengthAt(running, nowEpochMs, config)),
            endAtMarkMs = asked.lastSoundEpochMs?.let { kept(lengthAt(running, it, config)) },
        )
    }

    /** To the nearest minute, as «47 мин» rounds it (5.6). */
    private fun toMinute(ms: Long): Long = (ms + MS_PER_MINUTE / 2) / MS_PER_MINUTE * MS_PER_MINUTE

    /**
     * Whether a practice begun at [startedAtEpochMs] still runs at [atEpochMs]. Past the limit it has ended by itself
     * (spec 3.12): a sound or a note heard then is not of it, and must not move its end.
     */
    fun runsAt(startedAtEpochMs: Long, atEpochMs: Long, config: PracticeConfig): Boolean =
        atEpochMs - startedAtEpochMs <= config.maxPracticeMs
}
