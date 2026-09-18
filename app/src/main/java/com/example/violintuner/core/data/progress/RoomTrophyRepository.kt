package com.example.violintuner.core.data.progress

import com.example.violintuner.core.domain.progress.Trophy
import com.example.violintuner.core.domain.progress.TrophyRepository
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomTrophyRepository @Inject constructor(private val dao: TrophyDao) : TrophyRepository {
    override val trophies: Flow<List<Trophy>> = dao.observeAll().map { rows ->
        rows.map { Trophy(hours = it.hours, awardedDate = LocalDate.parse(it.awardedDate), shown = it.shown) }
    }

    override suspend fun award(hours: Int, date: LocalDate) =
        dao.insertIfAbsent(TrophyEntity(hours = hours, awardedDate = date.toString(), shown = false))

    override suspend fun markShown(hours: Int) = dao.markShown(hours)
}
