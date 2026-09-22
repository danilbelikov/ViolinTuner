package com.example.violintuner.core.domain.practice

/**
 * Every tunable number of practice-time tracking, with starting values from docs/spec.md 5.6.
 * Kept apart from [com.example.violintuner.core.domain.IntonationConfig]: none of this is
 * about intonation.
 */
data class PracticeConfig(
    /** Shorter practices are dropped with a toast instead of being saved. */
    val minPracticeMs: Long = MS_PER_MINUTE,
    /**
     * A running practice with no violin sound for this long is "forgotten"; it is also how
     * long an expired practice that never heard the violin is credited with.
     */
    val forgottenAfterMs: Long = 60 * MS_PER_MINUTE,
    /** No practice runs longer: past this it ends by itself (spec 3.12). */
    val maxPracticeMs: Long = 12 * MS_PER_HOUR,
    /** Step of every duration control: the summary sheet and "change time". */
    val editStepMinutes: Int = 5,
    /** The summary sheet does not go below this; a practice cannot be edited into nothing. */
    val minEditableMinutes: Int = 5,
    /** Upper bound of a day's time when the user edits it. */
    val maxDayMinutes: Int = 12 * 60,
    /** The "violin sounded" mark is written at most this often. */
    val soundMarkIntervalMs: Long = MS_PER_MINUTE,
    /** Lower bounds, in minutes, of fill levels 2, 3 and 4 of the calendar; level 1 is any time. */
    val fillLevelMinutes: List<Int> = listOf(20, 45, 90),
    /** The goal of a block — «подход» (spec 5.21): from, to and by how much. */
    val blockGoalMinMinutes: Int = 5,
    val blockGoalMaxMinutes: Int = 60,
    val blockGoalStepMinutes: Int = 5,
    /** The quick goals of the panel in «Что играем». */
    val blockQuickGoalsMinutes: List<Int> = listOf(5, 10, 15, 20, 30),
    /** The goal offered before anything was ever played. */
    val blockDefaultGoalMinutes: Int = 10,
    /** A shorter block is not kept: a tap by mistake is not practice. */
    val blockMinSavedMs: Long = MS_PER_MINUTE,
    /** «Время по элементам» looks back this many days, today included. */
    val pieceTimeDays: Int = 30,
) {
    companion object {
        const val MS_PER_MINUTE = 60_000L
        const val MS_PER_HOUR = 60 * MS_PER_MINUTE
    }
}
