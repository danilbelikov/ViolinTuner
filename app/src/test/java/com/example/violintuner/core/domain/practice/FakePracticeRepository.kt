package com.example.violintuner.core.domain.practice

import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory repository for view model tests; ids count up from 1. */
class FakePracticeRepository : PracticeRepository {
    override val entries = MutableStateFlow<List<PracticeEntry>>(emptyList())
    val replacedDays = mutableListOf<Triple<LocalDate, Long, Long>>()

    override suspend fun add(entry: PracticeEntry): Long {
        val id = (entries.value.maxOfOrNull { it.id } ?: 0L) + 1
        entries.update { (it + entry.copy(id = id)).sortedWith(compareBy({ it.date }, { it.startedAtEpochMs })) }
        return id
    }

    override suspend fun replaceDay(date: LocalDate, durationMs: Long, startedAtEpochMs: Long) {
        replacedDays += Triple(date, durationMs, startedAtEpochMs)
        entries.update { list ->
            val kept = list.filterNot { it.date == date }
            if (durationMs == 0L) kept else kept + PracticeEntry(date, startedAtEpochMs, durationMs, manual = true, id = 1_000L + list.size)
        }
    }
}

class FakeRunningPracticeStore : RunningPracticeStore {
    override val running = MutableStateFlow<RunningPractice?>(null)

    override suspend fun start(startedAtEpochMs: Long) {
        running.value = RunningPractice(startedAtEpochMs, lastSoundEpochMs = null)
    }

    override suspend fun markSound(epochMs: Long) {
        running.update { it?.copy(lastSoundEpochMs = epochMs) }
    }

    override suspend fun clear() {
        running.value = null
    }
}
