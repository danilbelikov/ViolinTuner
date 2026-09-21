package com.example.violintuner.feature.home

import com.example.violintuner.core.domain.home.HomeGroup
import com.example.violintuner.core.domain.home.HomeHouse
import com.example.violintuner.core.domain.home.HomeItem
import com.example.violintuner.core.domain.home.HomeState
import com.example.violintuner.core.domain.journey.JourneyProgress
import com.example.violintuner.feature.journey.art.SceneMode

/** The four screens of the home are views of one state (as the journey's map and passport are). */
enum class HomeView { MAIN, SHOP, ARRANGE, HOUSES }

data class HomeUi(
    val loading: Boolean,
    val home: HomeState,
    val progress: JourneyProgress,
    /** The home lived in. */
    val house: String,
    /** The room or the home from outside — on the main screen and in «Обставить». */
    val outside: Boolean = false,
    /** The shelf of the shop; null — all of them. */
    val category: HomeGroup? = null,
    /** The thing whose card is open. */
    val card: HomeItem? = null,
    /** The thing being tried on in the room, on the whole screen; [tryMode] — evening or day there, null — by the clock. */
    val tryOn: HomeItem? = null,
    val tryMode: SceneMode? = null,
    val houseCard: HomeHouse? = null,
    /** The little film of moving in (handoff 27e3). */
    val moving: HomeHouse? = null,
) {
    val balance: Long get() = progress.balance
}

sealed interface HomeIntent {
    data object BackClicked : HomeIntent
    data class SideSelected(val outside: Boolean) : HomeIntent
    data object ShopClicked : HomeIntent
    data object ArrangeClicked : HomeIntent
    data object HousesClicked : HomeIntent
    data object GiftTaken : HomeIntent
    data class CategorySelected(val group: HomeGroup?) : HomeIntent
    data class ItemClicked(val id: String) : HomeIntent
    data object CardClosed : HomeIntent
    data object TryClicked : HomeIntent
    data class TryModeSelected(val mode: SceneMode) : HomeIntent
    data object TryClosed : HomeIntent
    data object BuyClicked : HomeIntent
    data class Placed(val slot: String, val itemId: String) : HomeIntent
    data class HouseClicked(val id: String) : HomeIntent
    data object HouseCardClosed : HomeIntent
    data object MoveClicked : HomeIntent
    data class LiveHere(val id: String) : HomeIntent
    data class ReduceMotionChanged(val reduce: Boolean) : HomeIntent
}

sealed interface HomeEffect {
    data object Close : HomeEffect
    data object OpenShop : HomeEffect
    data object OpenArrange : HomeEffect
    data object OpenHouses : HomeEffect
    data class ShowBought(val itemId: String) : HomeEffect

    /** Moved in: back to the home itself, whatever screen the move was made from. */
    data object OpenHome : HomeEffect
}

object HomeMotion {
    const val MOVE_MS = 2_000L
    const val MOVE_REDUCED_MS = 800L
    const val MOVE_FADE_MS = 900
}
