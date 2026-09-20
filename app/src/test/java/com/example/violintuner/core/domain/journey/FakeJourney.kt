package com.example.violintuner.core.domain.journey

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory journey with the rules of the real one: nothing is paid for that cannot be afforded, no stop is skipped. */
class FakeJourneyRepository : JourneyRepository {
    override val progress = MutableStateFlow(JourneyProgress.EMPTY)
    val earnings = mutableListOf<TaktEarning>()

    override suspend fun start(nowEpochMs: Long) {
        progress.update { if (it.started) it else it.copy(arrivals = listOf(Arrival(JourneyRoute.HOME, nowEpochMs))) }
    }

    override suspend fun depart(stop: JourneyStop, nowEpochMs: Long): Boolean {
        val current = progress.value
        if (JourneyRules.next(current)?.id != stop.id || current.balance < stop.price) return false
        progress.value = current.copy(spent = current.spent + stop.price, arrivals = current.arrivals + Arrival(stop.id, nowEpochMs))
        return true
    }

    override suspend fun buy(stop: JourneyStop, extra: JourneyExtra, price: Int, nowEpochMs: Long): Boolean {
        val current = progress.value
        val bought = BoughtExtra(stop.id, extra)
        if (bought in current.extras || current.balance < price || current.arrivals.none { it.stopId == stop.id }) return false
        progress.value = current.copy(spent = current.spent + price, extras = current.extras + bought)
        return true
    }

    override suspend fun earn(earning: TaktEarning) {
        if (earning.takts <= 0) return
        earnings += earning
        progress.update { it.copy(earned = it.earned + earning.takts, lastEarning = earning) }
    }
}

class FakePracticeNotesStore : PracticeNotesStore {
    var forPractice: Long? = null
    var count = NoteCount.ZERO

    override suspend fun add(practiceStartedAtEpochMs: Long, count: NoteCount) {
        if (forPractice != practiceStartedAtEpochMs) this.count = NoteCount.ZERO
        forPractice = practiceStartedAtEpochMs
        this.count += count
    }

    override suspend fun countFor(practiceStartedAtEpochMs: Long): NoteCount = if (forPractice == practiceStartedAtEpochMs) count else NoteCount.ZERO

    override suspend fun clear() {
        forPractice = null
        count = NoteCount.ZERO
    }
}
