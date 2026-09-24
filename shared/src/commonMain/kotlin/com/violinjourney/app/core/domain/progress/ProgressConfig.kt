package com.violinjourney.app.core.domain.progress

/**
 * Every number of levels and trophies, with starting values from docs/spec.md 5.7. Names of
 * levels and trophies are UI strings and live in the resources, in the same order.
 */
data class ProgressConfig(
    /** Total hours at which levels 1, 2, 3… begin; the first is always zero. */
    val levelThresholdHours: List<Int> = listOf(0, 2, 5, 10, 25, 50, 100, 200, 350, 500, 750, 1000, 2000, 5000, 10_000),
    /** Marks, in total hours, that give a trophy each. */
    val trophyHours: List<Int> = listOf(1, 10, 50, 100, 250, 500, 1000, 2500, 5000, 10_000),
    /**
     * Marks not yet taken from this one up are "far ahead": shown without what is left to
     * them, unless the mark is the very next one (spec 3.13).
     */
    val farTrophyHours: Int = 2500,
) {
    init {
        require(levelThresholdHours.firstOrNull() == 0) { "level 1 must start at zero" }
        require(levelThresholdHours.zipWithNext().all { (a, b) -> a < b }) { "level thresholds must grow" }
        require(trophyHours.isNotEmpty() && trophyHours.zipWithNext().all { (a, b) -> a < b }) { "trophy marks must grow" }
    }

    companion object {
        const val MS_PER_HOUR = 3_600_000L
    }
}
