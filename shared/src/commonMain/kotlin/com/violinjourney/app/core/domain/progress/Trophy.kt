package com.violinjourney.app.core.domain.progress

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/** A trophy that has been given; it is never taken back (spec 3.13). */
data class Trophy(
    /** The mark, in total hours, it was given for. */
    val hours: Int,
    /** Local date of the moment the total first reached the mark. */
    val awardedDate: LocalDate,
    /** False until its gift sheet has been answered. */
    val shown: Boolean,
)

interface TrophyRepository {
    /** Every trophy given so far, lowest mark first. */
    val trophies: Flow<List<Trophy>>

    /** Gives the trophy of [hours]; a mark that already has one keeps its first date. */
    suspend fun award(hours: Int, date: LocalDate)

    suspend fun markShown(hours: Int)
}
