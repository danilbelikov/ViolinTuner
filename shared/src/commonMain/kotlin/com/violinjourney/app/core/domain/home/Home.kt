package com.violinjourney.app.core.domain.home

import com.violinjourney.app.core.domain.journey.JourneyProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/** The shelves of the shop (handoff 27b): what kind of thing it is, not where it stands. */
enum class HomeGroup { INSTRUMENT, MUSIC, ROOM, LIGHT, FURNITURE, PLANT, LIFE, PET, OUTSIDE }

/**
 * A thing of the catalogue (spec 3.24). It knows its place: [slot] holds one thing at a time, so a
 * room is always put together. [at] is where a pet actually lies — pets share the one slot «pet»,
 * a home has one. [from] is the stop of the journey the thing is brought from: until that city is
 * reached the shop shows its silhouette. Walls, floors and curtains are colours ([palette]) and,
 * some of them, a [pattern]; they are not [drawn] as things.
 */
data class HomeItem(
    val id: String,
    val group: HomeGroup,
    val slot: String,
    val at: String?,
    val price: Int,
    val from: String?,
    val z: Int,
    val outside: Boolean,
    val drawn: Boolean,
    val pattern: Boolean,
    val palette: Map<String, Long>,
)

/** A place of a home. [houses] null — every home has it; a fireplace needs a chimney. */
data class HomeSlot(val id: String, val outside: Boolean, val palette: Boolean, val houses: Set<String>?)

/** A home. Those not [drawn] yet stand in the row as silhouettes with a price — they come with updates, as the cities did. */
data class HomeHouse(val id: String, val price: Int, val drawn: Boolean)

object HomeCatalog {
    val items: List<HomeItem> = HomeCatalogData.items
    val slots: List<HomeSlot> = HomeCatalogData.slots
    val houses: List<HomeHouse> = HomeCatalogData.houses
    val byId: Map<String, HomeItem> = items.associateBy { it.id }
    val slotById: Map<String, HomeSlot> = slots.associateBy { it.id }
    val houseById: Map<String, HomeHouse> = houses.associateBy { it.id }

    /** The rented room everyone starts in. */
    const val START_HOUSE = "rent"

    /** The first present: a stand, and the violin leaves its case. It waits in the shop, free (handoff 27a3). */
    const val GIFT = "vln_student"

    /** What the rented room has from the start: everything that costs nothing, but the present. */
    val startItems: Set<String> = items.filter { it.price == 0 && it.id != GIFT }.map { it.id }.toSet()

    /** A tree that stands from the first of December to the middle of January and waits in the wardrobe the rest of the year. */
    const val SEASONAL = "xmas"
    /** A day of the year as month × 100 + day: the season runs over New Year, from the 1st of December to the 15th of January. */
    const val MONTH_DAY = 100
    const val SEASON_FROM = 12 * MONTH_DAY + 1
    const val SEASON_TO = 1 * MONTH_DAY + 15
}

/** Everything the home remembers. What stands where is [choices]: slot → item, an empty string — the place left bare on purpose. */
data class HomeState(
    val loaded: Boolean,
    val purchased: Set<String>,
    val houses: Set<String>,
    val choices: Map<String, String>,
    val movedInAtEpochMs: Long? = null,
) {
    companion object {
        val EMPTY = HomeState(loaded = false, purchased = emptySet(), houses = emptySet(), choices = emptyMap())

        /** The key of [choices] that holds the home lived in. */
        const val HOUSE_KEY = "@house"
    }
}

/** Pure rules of the home (spec 3.24, 5.18). */
object HomeRules {
    fun ownedItems(state: HomeState): Set<String> = HomeCatalog.startItems + state.purchased.filter { it in HomeCatalog.byId }

    fun ownedHouses(state: HomeState): Set<String> = state.houses.filter { HomeCatalog.houseById[it]?.drawn == true }.toSet() + HomeCatalog.START_HOUSE

    /** Where the player lives: the chosen home if it is theirs, the rented room otherwise. */
    fun house(state: HomeState): String = state.choices[HomeState.HOUSE_KEY]?.takeIf { it in ownedHouses(state) } ?: HomeCatalog.START_HOUSE

    fun slotIn(slotId: String, house: String): Boolean = HomeCatalog.slotById[slotId]?.let { it.houses == null || house in it.houses } ?: false

    /** Brought from a city: on the shelf once the city is reached. */
    fun unlocked(item: HomeItem, progress: JourneyProgress): Boolean = item.from == null || progress.arrivals.any { it.stopId == item.from }

    fun owned(item: HomeItem, state: HomeState): Boolean = item.id in ownedItems(state)

    fun canBuy(item: HomeItem, state: HomeState, progress: JourneyProgress): Boolean =
        !owned(item, state) && unlocked(item, progress) && progress.balance >= item.price

    fun canBuy(house: HomeHouse, state: HomeState, progress: JourneyProgress): Boolean =
        house.drawn && house.id !in ownedHouses(state) && progress.balance >= house.price

    /** What stands in every place: the choice if it is owned, otherwise what the room came with; an empty choice — nothing. */
    fun placed(state: HomeState): Map<String, HomeItem> {
        val owned = ownedItems(state)
        val result = LinkedHashMap<String, HomeItem>()
        HomeCatalog.items.filter { it.id in HomeCatalog.startItems }.forEach { result[it.slot] = it }
        state.choices.forEach { (slot, id) ->
            if (slot == HomeState.HOUSE_KEY) return@forEach
            if (id.isEmpty()) result.remove(slot) else HomeCatalog.byId[id]?.takeIf { it.id in owned && it.slot == slot }?.let { result[slot] = it }
        }
        return result
    }

    /** What is seen in [house] from [outside] or inside on [date], back to front. Things whose place this home lacks wait in the wardrobe. */
    fun standing(state: HomeState, house: String, outside: Boolean, date: LocalDate): List<HomeItem> =
        placed(state).values
            .filter { it.outside == outside && slotIn(it.slot, house) && (it.at == null || slotIn(it.at, house)) && inSeason(it, date) }
            .sortedBy { it.z }

    fun inSeason(item: HomeItem, date: LocalDate): Boolean {
        if (item.id != HomeCatalog.SEASONAL) return true
        val day = date.month.number * HomeCatalog.MONTH_DAY + date.day
        return day >= HomeCatalog.SEASON_FROM || day <= HomeCatalog.SEASON_TO
    }

    /** The cat is on the porch when the home is seen from outside and has a porch (handoff 27e3). */
    fun catOnPorch(state: HomeState, house: String): HomeItem? =
        placed(state)["pet"]?.takeIf { it.id.startsWith("cat_") && slotIn("oPorch", house) }

    fun giftWaiting(state: HomeState): Boolean = state.loaded && HomeCatalog.GIFT !in state.purchased

    /** The next home to save for: the cheapest one not owned — drawn or not, the row shows both. */
    fun nextHouse(state: HomeState): HomeHouse? = HomeCatalog.houses.filter { it.id !in ownedHouses(state) && it.price > 0 }.minByOrNull { it.price }

    /** Owned things of [slot] to choose from in «Обставить», the room's own first. */
    fun wardrobe(slot: String, state: HomeState): List<HomeItem> {
        val owned = ownedItems(state)
        return HomeCatalog.items.filter { it.slot == slot && it.id in owned }.sortedBy { it.price }
    }

    /** How many things the player has besides what the room came with, and how many of them came from the road. */
    fun counts(state: HomeState): Pair<Int, Int> {
        val bought = state.purchased.mapNotNull { HomeCatalog.byId[it] }
        return bought.size to bought.count { it.from != null }
    }
}

interface HomeRepository {
    val state: Flow<HomeState>

    /** Pays for [item] and puts it in its place, in one transaction; false when it is owned already or the takts are short. */
    suspend fun buy(item: HomeItem, nowEpochMs: Long): Boolean

    /** Pays for [house] and moves in. */
    suspend fun buy(house: HomeHouse, nowEpochMs: Long): Boolean

    /** Puts [itemId] into [slot]; an empty id leaves the place bare. */
    suspend fun place(slot: String, itemId: String)

    suspend fun liveIn(house: String)
}

object NoHome : HomeRepository {
    override val state: Flow<HomeState> = flowOf(HomeState.EMPTY.copy(loaded = true))

    override suspend fun buy(item: HomeItem, nowEpochMs: Long): Boolean = false

    override suspend fun buy(house: HomeHouse, nowEpochMs: Long): Boolean = false

    override suspend fun place(slot: String, itemId: String) = Unit

    override suspend fun liveIn(house: String) = Unit
}
