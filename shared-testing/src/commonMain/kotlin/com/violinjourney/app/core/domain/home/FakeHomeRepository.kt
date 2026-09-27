package com.violinjourney.app.core.domain.home

import com.violinjourney.app.core.domain.journey.FakeJourneyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** The home in memory, paid from the journey's purse as the real one is (IosDaoTest holds the real rules). */
class FakeHomeRepository(private val journey: FakeJourneyRepository) : HomeRepository {
    override val state = MutableStateFlow(HomeState.EMPTY.copy(loaded = true))

    private fun pay(price: Int): Boolean {
        val progress = journey.progress.value
        if (progress.balance < price) return false
        journey.progress.value = progress.copy(spent = progress.spent + price)
        return true
    }

    override suspend fun buy(item: HomeItem, nowEpochMs: Long): Boolean {
        if (item.id in state.value.purchased || !pay(item.price)) return false
        state.update { it.copy(purchased = it.purchased + item.id, choices = it.choices + (item.slot to item.id)) }
        return true
    }

    override suspend fun buy(house: HomeHouse, nowEpochMs: Long): Boolean {
        if (!house.drawn || house.id in state.value.houses || !pay(house.price)) return false
        state.update { it.copy(houses = it.houses + house.id, choices = it.choices + (HomeState.HOUSE_KEY to house.id)) }
        return true
    }

    override suspend fun place(slot: String, itemId: String) = state.update { it.copy(choices = it.choices + (slot to itemId)) }

    override suspend fun liveIn(house: String) = state.update { it.copy(choices = it.choices + (HomeState.HOUSE_KEY to house)) }
}
