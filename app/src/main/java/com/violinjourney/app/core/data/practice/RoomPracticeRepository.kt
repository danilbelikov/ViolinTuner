package com.violinjourney.app.core.data.practice

import com.violinjourney.app.core.domain.practice.PracticeEntry
import com.violinjourney.app.core.domain.practice.PracticeRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

class RoomPracticeRepository @Inject constructor(private val dao: PracticeDao) : PracticeRepository {
    override val entries: Flow<List<PracticeEntry>> =
        dao.observeAll().map { rows -> rows.map(PracticeMapper::toDomain) }

    override suspend fun add(entry: PracticeEntry): Long = dao.insert(PracticeMapper.toEntity(entry))

    override suspend fun replaceDay(date: LocalDate, durationMs: Long, startedAtEpochMs: Long) {
        require(durationMs >= 0) { "negative duration $durationMs" }
        val replacement = if (durationMs == 0L) {
            null
        } else {
            PracticeEntity(date = date.toString(), startedAtEpochMs = startedAtEpochMs, durationMs = durationMs, manual = true)
        }
        dao.replaceDay(date.toString(), replacement)
    }
}
