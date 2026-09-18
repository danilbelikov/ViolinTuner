package com.example.violintuner.feature.practice

import com.example.violintuner.core.domain.progress.Progress
import com.example.violintuner.core.domain.progress.ProgressConfig
import com.example.violintuner.core.domain.progress.Trophy

/** Total time, trophies and profile → the header and the trophies list (spec 3.13). Pure. */
object ProgressReducer {
    /** The header shows this many given trophies at most, the latest ones, before the next mark. */
    const val HEADER_GIVEN_TROPHIES = 2

    fun headerOf(totalMs: Long, trophies: List<Trophy>, name: String, avatarPath: String?, config: ProgressConfig): ProfileHeader {
        val level = Progress.levelOf(totalMs, config)
        val given = trophies.map { it.hours }.sorted()
        val next = Progress.nextTrophyHours(given.toSet(), config)
        return ProfileHeader(
            name = name,
            avatarPath = avatarPath,
            totalMs = totalMs,
            level = level.level,
            nextLevel = level.nextLevel,
            levelFraction = level.fraction,
            toNextLevelMs = level.toNextMs,
            trophyRow = given.takeLast(HEADER_GIVEN_TROPHIES).map { TrophyBadge(it, given = true) } +
                listOfNotNull(next?.let { TrophyBadge(it, given = false) }),
            givenTrophies = given.size,
            nextTrophyHours = next,
        )
    }

    /** Every mark of the config, lowest first: the date of the given ones, what is left to the others. */
    fun trophyLines(totalMs: Long, trophies: List<Trophy>, config: ProgressConfig): List<TrophyLine> {
        val byHours = trophies.associateBy { it.hours }
        val next = Progress.nextTrophyHours(byHours.keys, config)
        return config.trophyHours.mapIndexed { index, hours ->
            val far = Progress.isFar(hours, byHours.keys, config)
            val trophy = byHours[hours]
            TrophyLine(
                hours = hours,
                index = index,
                awardedDate = trophy?.awardedDate,
                remainingMs = if (trophy == null && !far) Progress.remainingMs(totalMs, hours) else null,
                isNext = hours == next,
                isFar = far,
            )
        }
    }
}
