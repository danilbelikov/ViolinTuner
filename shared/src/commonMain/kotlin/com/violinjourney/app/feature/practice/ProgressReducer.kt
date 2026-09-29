package com.violinjourney.app.feature.practice

import com.violinjourney.app.core.domain.progress.Progress
import com.violinjourney.app.core.domain.progress.ProgressConfig
import com.violinjourney.app.core.domain.progress.Trophy

/** Total time, trophies and profile → the path row, «Мой путь» and the trophies list (spec 3.13, 3.36.2). Pure. */
object ProgressReducer {
    /** «Мой путь» shows this many given trophies at most, the latest ones, before the next mark. */
    const val HEADER_GIVEN_TROPHIES = 2

    fun headerOf(totalMs: Long, trophies: List<Trophy>, name: String, avatarPath: String?, config: ProgressConfig): ProfileHeader {
        val level = Progress.levelOf(totalMs, config)
        val given = trophies.filter { it.shown }.map { it.hours }.sorted()
        val next = Progress.nextTrophyHours(given.toSet(), config)
        return ProfileHeader(
            name = name,
            avatarPath = avatarPath,
            totalMs = totalMs,
            level = level.level,
            nextLevel = level.nextLevel,
            levelFraction = level.fraction,
            toNextLevelMs = level.toNextMs,
            trophyRow = given.takeLast(HEADER_GIVEN_TROPHIES).map { TrophyBadge(it, given = true, index = config.trophyHours.indexOf(it)) } +
                listOfNotNull(next?.let { TrophyBadge(it, given = false, index = config.trophyHours.indexOf(it)) }),
            givenTrophies = given.size,
            nextTrophyHours = next,
        )
    }

    /**
     * The trophy whose gift sheet is due: the lowest one not seen yet (spec 3.13: one sheet after another, lowest first), with the
     * card of the trophy after it (spec 3.36.3): the lowest mark not given at all — an unseen one waiting in the chain counts as given
     * — and what is left to it from [totalMs]; none after the last mark.
     */
    fun giftOf(trophies: List<Trophy>, config: ProgressConfig, totalMs: Long): Gift? =
        trophies.filter { !it.shown }.minByOrNull { it.hours }?.let {
            val next = Progress.nextTrophyHours(trophies.map { trophy -> trophy.hours }.toSet(), config)
            Gift(
                hours = it.hours,
                index = config.trophyHours.indexOf(it.hours),
                awardedDate = it.awardedDate,
                next = next?.let { hours -> NextTrophy(hours, config.trophyHours.indexOf(hours), Progress.remainingMs(totalMs, hours)) },
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
