package com.violinjourney.app.feature.practice.components

import com.violinjourney.app.core.domain.practice.PracticeRecap

/** Where the streak stands in «Занятие сохранено» (spec 3.36.3). */
enum class RecapStreak {
    /** A card beside the card of the level, 1 : 1.45. */
    Pair,

    /** A row of 48 under the framed card of a new level: one highlight on the sheet. */
    Row,

    /** A streak of 0 (an old forgotten practice saved days later) is not shown: the level stands the whole width. */
    None,
}

/** How «Занятие сохранено» lays out its streak and level (spec 3.36.3): pure, so the rule is tested apart from the sheet. */
data class RecapLook(val streak: RecapStreak, val levelUp: Boolean) {
    companion object {
        fun of(recap: PracticeRecap): RecapLook = RecapLook(
            streak = when {
                recap.streakDays == 0 -> RecapStreak.None
                recap.levelUp -> RecapStreak.Row
                else -> RecapStreak.Pair
            },
            levelUp = recap.levelUp,
        )
    }
}
