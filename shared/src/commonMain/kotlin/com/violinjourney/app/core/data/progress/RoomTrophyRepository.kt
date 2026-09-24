package com.violinjourney.app.core.data.progress

import com.violinjourney.app.core.domain.progress.Trophy
import com.violinjourney.app.core.domain.progress.TrophyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

class RoomTrophyRepository(private val dao: TrophyDao) : TrophyRepository {
    override val trophies: Flow<List<Trophy>> = dao.observeAll().map { rows ->
        rows.map { Trophy(hours = it.hours, awardedDate = LocalDate.parse(it.awardedDate), shown = it.shown) }
    }

    override suspend fun award(hours: Int, date: LocalDate) =
        dao.insertIfAbsent(TrophyEntity(hours = hours, awardedDate = date.toString(), shown = false))

    override suspend fun markShown(hours: Int) = dao.markShown(hours)
}
