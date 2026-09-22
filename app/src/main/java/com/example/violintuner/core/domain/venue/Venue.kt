package com.example.violintuner.core.domain.venue

import com.example.violintuner.core.domain.journey.JourneyProgress
import com.example.violintuner.core.domain.journey.JourneyRepository
import com.example.violintuner.core.domain.journey.JourneyRoute
import com.example.violintuner.core.domain.journey.JourneyRules
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Where the player is (spec 3.27): at home, or in a city the road has reached. Live takes place
 * there — in the room, or on the stage of the city's hall — and the window on «Занятия» shows it.
 */
sealed interface Venue {
    /** The index of the stop on the route; home is the stop 0. */
    val stopIndex: Int

    data object Home : Venue {
        override val stopIndex: Int = 0
    }

    data class Hall(val stopId: String) : Venue {
        override val stopIndex: Int get() = JourneyRoute.indexOf(stopId)
    }
}

/**
 * How «where we are» is kept: null — wherever the road stands; [VenueRules.HOME]; or the id of a
 * reached stop. Nothing is stored until the player moves for the first time.
 */
interface VenueStore {
    val stored: Flow<String?>

    suspend fun store(value: String?)
}

/** For code that keeps no choice (tests of other screens, previews): the player is always where the road stands. */
object FollowTheRoad : VenueStore {
    override val stored: Flow<String?> = kotlinx.coroutines.flow.flowOf(null)

    override suspend fun store(value: String?) = Unit
}

/** What a place looks like in the list «Где играть»: open to play in, the next one on the road, or still ahead. */
enum class VenueAccess { OPEN, NEXT, AHEAD }

data class VenueEntry(val venue: Venue, val access: VenueAccess)

/** Pure: the stored choice and the journey → where the player is and where they may go. */
object VenueRules {
    const val HOME = JourneyRoute.HOME

    /** Where the road stands: the farthest stop reached; home before the journey has begun. */
    fun road(progress: JourneyProgress): Venue {
        val index = JourneyRules.currentIndex(progress)
        return if (index == 0) Venue.Home else Venue.Hall(JourneyRoute.stops[index].id)
    }

    /** Every place one may be: the room, then the reached stops in the order of the route. */
    fun reached(progress: JourneyProgress): List<Venue> {
        val arrived = progress.arrivals.map { it.stopId }.toSet()
        return listOf(Venue.Home) + JourneyRoute.stops.drop(1).filter { it.id in arrived }.map { Venue.Hall(it.id) }
    }

    /** Nothing stored follows the road; a hall that is not reached (a copy from before the trip) gives the room. */
    fun resolve(stored: String?, progress: JourneyProgress): Venue = when (stored) {
        null -> road(progress)
        HOME -> Venue.Home
        else -> Venue.Hall(stored).takeIf { it in reached(progress) } ?: Venue.Home
    }

    /**
     * What a choice is stored as. The place where the road stands is stored as «follow the road»:
     * the next city the player travels to then takes over by itself.
     */
    fun storedFor(choice: Venue, progress: JourneyProgress): String? = when {
        choice == road(progress) -> null
        choice is Venue.Hall -> choice.stopId
        else -> HOME
    }

    /** The list «Где играть»: home and the reached stops open, the next drawn stop, the rest of the drawn route ahead. */
    fun menu(progress: JourneyProgress): List<VenueEntry> {
        val open = reached(progress)
        val next = JourneyRules.next(progress)
        val ahead = JourneyRoute.stops.drop(1).filter { stop -> stop.available && open.none { it is Venue.Hall && it.stopId == stop.id } && stop.id != next?.id }
        return open.map { VenueEntry(it, VenueAccess.OPEN) } +
            listOfNotNull(next?.let { VenueEntry(Venue.Hall(it.id), VenueAccess.NEXT) }) +
            ahead.map { VenueEntry(Venue.Hall(it.id), VenueAccess.AHEAD) }
    }
}

/** Where the player is, and the three ways of moving (spec 3.27): home, back on the road, to a place of one's choice. */
class Venues @Inject constructor(
    private val store: VenueStore,
    private val journey: JourneyRepository,
) {
    val current: Flow<Venue> = combine(store.stored, journey.progress, VenueRules::resolve).distinctUntilChanged()

    /** The list «Где играть». */
    val menu: Flow<List<VenueEntry>> = journey.progress.map(VenueRules::menu).distinctUntilChanged()

    /** «Войти в дом», the door home of a stop. */
    suspend fun goHome() = store.store(VenueRules.HOME)

    /** «В дорогу», an arrival: wherever the road stands, now and after the next leg. */
    suspend fun followRoad() = store.store(null)

    /** «Играть здесь», the list «Где играть». A place that is not reached is not a choice: nothing changes. */
    suspend fun choose(venue: Venue) {
        val progress = journey.progress.first()
        if (venue in VenueRules.reached(progress)) store.store(VenueRules.storedFor(venue, progress))
    }
}
