package com.example.violintuner.core.domain.journey

import kotlinx.coroutines.flow.Flow

/** Every number of the journey (spec 5.17). None of it is about intonation. */
data class JourneyConfig(
    /** A note played in tune is one takt; a minute of practice is two — a day of slow scales must not be poorer than a day of fast runs. */
    val taktsPerNoteInTune: Int = 1,
    val taktsPerMinute: Int = 2,
    /** A note counts from this length on: the same threshold the analysis of a recording uses (spec 5.5). */
    val minNoteMs: Long = 200,
    /** Counted notes reach the disk this often, so a killed process loses seconds, not the whole practice. */
    val notesFlushMs: Long = 10_000,
    val secondTimePrice: Int = 200,
    val secondViewPrice: Int = 400,
    val souvenirPrice: Int = 150,
)

enum class Transport { NONE, TRAIN, SHIP, PLANE }

/** What can be bought inside a stop (handoff 26g2): cheap goals for when the next city is far. */
enum class JourneyExtra { SECOND_TIME, SECOND_VIEW, SOUVENIR }

/** A view of a stop as it is drawn: the key of its scene in the assets. */
data class StopView(val scene: String, val inside: Boolean)

/** One stop of the route. Names, places and facts are words of the interface, by [id]; here are the rules. */
data class JourneyStop(
    val id: String,
    val price: Int,
    /** How one gets here from the stop before. */
    val transport: Transport,
    val mapX: Float,
    val mapY: Float,
    /** Drawn and open for travel. The rest of the route is on the map as «скоро»: takts saved for it wait. */
    val available: Boolean,
    /** The main view first; empty for a stop that has only its silhouette yet. */
    val views: List<StopView> = emptyList(),
) {
    val hasSecondView: Boolean get() = views.size > 1
}

/** The route of the first version (handoff `LOCATIONS`): linear — forks would break the line on the map and the story of the road. */
object JourneyRoute {
    val stops: List<JourneyStop> = listOf(
        JourneyStop("home", 0, Transport.NONE, 70f, 380f, true, listOf(StopView("home", inside = true))),
        JourneyStop("cremona", 300, Transport.TRAIN, 150f, 372f, true, listOf(StopView("cremona", inside = true), StopView("cremonaOut", inside = false))),
        JourneyStop("milan", 500, Transport.TRAIN, 132f, 344f, true, listOf(StopView("milan", inside = true), StopView("milanOut", inside = false))),
        // The handoff's generic alpine postcard (mountains, a lake, an onion-domed church): kept there as a spare, it is Salzburg here.
        JourneyStop("salzburg", 800, Transport.TRAIN, 212f, 318f, true, listOf(StopView("austria", inside = false), StopView("salzburgInt", inside = true))),
        JourneyStop("vienna", 1_200, Transport.TRAIN, 262f, 326f, true, listOf(StopView("vienna", inside = false), StopView("viennaInt", inside = true))),
        JourneyStop("prague", 1_600, Transport.TRAIN, 238f, 282f, true, listOf(StopView("prague", inside = false), StopView("pragueInt", inside = true))),
        JourneyStop("leipzig", 2_000, Transport.TRAIN, 206f, 250f, true, listOf(StopView("leipzig", inside = false), StopView("leipzigInt", inside = true))),
        JourneyStop("berlin", 2_500, Transport.TRAIN, 244f, 218f, true, listOf(StopView("berlin", inside = false), StopView("berlinInt", inside = true))),
        JourneyStop("amsterdam", 3_000, Transport.TRAIN, 166f, 214f, true, listOf(StopView("amsterdam", inside = false), StopView("amsterdamInt", inside = true))),
        JourneyStop("paris", 4_000, Transport.TRAIN, 118f, 270f, true, listOf(StopView("paris", inside = false), StopView("parisInt", inside = true))),
        JourneyStop("london", 5_000, Transport.TRAIN, 110f, 186f, true, listOf(StopView("london", inside = false), StopView("londonInt", inside = true))),
        JourneyStop("spb", 6_000, Transport.SHIP, 340f, 150f, true, listOf(StopView("spb", inside = false), StopView("spbInt", inside = true))),
        JourneyStop("moscow", 7_000, Transport.TRAIN, 388f, 208f, true, listOf(StopView("moscow", inside = false), StopView("moscowInt", inside = true))),
        JourneyStop("newyork", 9_000, Transport.PLANE, 40f, 480f, true, listOf(StopView("newyork", inside = false), StopView("newyorkInt", inside = true))),
        JourneyStop("buenosaires", 11_000, Transport.SHIP, 86f, 640f, true, listOf(StopView("buenosaires", inside = false), StopView("buenosairesInt", inside = true))),
        JourneyStop("tokyo", 13_000, Transport.PLANE, 392f, 420f, true, listOf(StopView("tokyo", inside = false), StopView("tokyoInt", inside = true))),
        JourneyStop("sydney", 15_000, Transport.PLANE, 372f, 650f, true, listOf(StopView("sydney", inside = false), StopView("sydneyInt", inside = true))),
    )

    const val HOME = "home"

    fun indexOf(id: String): Int = stops.indexOfFirst { it.id == id }
}

data class Arrival(val stopId: String, val arrivedAtEpochMs: Long)

data class BoughtExtra(val stopId: String, val extra: JourneyExtra)

/** What one practice earned: kept as it was counted, so the summary can say «264 ноты в строе + 76 за время». */
data class TaktEarning(val atEpochMs: Long, val notesPlayed: Int, val notesInTune: Int, val durationMs: Long, val takts: Int)

/** Everything the journey remembers. The balance is never stored: it is what was earned minus what was spent. */
data class JourneyProgress(
    val earned: Long,
    val spent: Long,
    val arrivals: List<Arrival>,
    val extras: Set<BoughtExtra>,
    val lastEarning: TaktEarning? = null,
) {
    val balance: Long get() = (earned - spent).coerceAtLeast(0)

    /** The journey has begun: the player has packed the case at home (the intro has been seen). */
    val started: Boolean get() = arrivals.isNotEmpty()

    companion object {
        val EMPTY = JourneyProgress(earned = 0, spent = 0, arrivals = emptyList(), extras = emptySet())
    }
}

/** Where the player stands on the route and what the next leg costs. Pure. */
object JourneyRules {
    fun taktsFor(notesInTune: Int, durationMs: Long, config: JourneyConfig): Int =
        notesInTune.coerceAtLeast(0) * config.taktsPerNoteInTune + (durationMs.coerceAtLeast(0) / MS_PER_MINUTE).toInt() * config.taktsPerMinute

    /** The farthest stop reached; home before anything else. */
    fun currentIndex(progress: JourneyProgress): Int =
        progress.arrivals.maxOfOrNull { JourneyRoute.indexOf(it.stopId) }?.coerceAtLeast(0) ?: 0

    /** The stop the road leads to; null at the end of what is drawn — the takts wait for the next batch of cities. */
    fun next(progress: JourneyProgress): JourneyStop? =
        JourneyRoute.stops.getOrNull(currentIndex(progress) + 1)?.takeIf { it.available }

    /** How much is missing for the next leg; zero when the player can set off. */
    fun missing(progress: JourneyProgress): Long = next(progress)?.let { (it.price - progress.balance).coerceAtLeast(0) } ?: 0

    fun canDepart(progress: JourneyProgress): Boolean = progress.started && next(progress)?.let { progress.balance >= it.price } == true

    fun priceOf(extra: JourneyExtra, config: JourneyConfig): Int = when (extra) {
        JourneyExtra.SECOND_TIME -> config.secondTimePrice
        JourneyExtra.SECOND_VIEW -> config.secondViewPrice
        JourneyExtra.SOUVENIR -> config.souvenirPrice
    }

    /** Extras belong to stops that have been reached and drawn; a second view — to those that have one. */
    fun offers(stop: JourneyStop, progress: JourneyProgress): List<JourneyExtra> {
        // home is a section of its own (spec 3.24): the clock gives its time of day, the shop gives the rest
        if (stop.id == JourneyRoute.HOME || progress.arrivals.none { it.stopId == stop.id }) return emptyList()
        return JourneyExtra.entries.filter { extra ->
            when (extra) {
                JourneyExtra.SECOND_TIME -> stop.views.isNotEmpty()
                JourneyExtra.SECOND_VIEW -> stop.hasSecondView
                JourneyExtra.SOUVENIR -> true
            }
        }
    }

    fun canBuy(stop: JourneyStop, extra: JourneyExtra, progress: JourneyProgress, config: JourneyConfig): Boolean =
        extra in offers(stop, progress) && BoughtExtra(stop.id, extra) !in progress.extras && progress.balance >= priceOf(extra, config)

    private const val MS_PER_MINUTE = 60_000L
}

interface JourneyRepository {
    val progress: Flow<JourneyProgress>

    /** «Собрать футляр»: the journey begins at home. No-op when it already has. */
    suspend fun start(nowEpochMs: Long)

    /** Pays for the next leg and arrives; false when there is not enough, or the stop is not the next one — nothing changes then. */
    suspend fun depart(stop: JourneyStop, nowEpochMs: Long): Boolean

    suspend fun buy(stop: JourneyStop, extra: JourneyExtra, price: Int, nowEpochMs: Long): Boolean

    suspend fun earn(earning: TaktEarning)
}

/** For code that earns nothing: tests of the practice, builds before the journey. */
object NoJourney : JourneyRepository {
    override val progress: Flow<JourneyProgress> = kotlinx.coroutines.flow.flowOf(JourneyProgress.EMPTY)

    override suspend fun start(nowEpochMs: Long) = Unit

    override suspend fun depart(stop: JourneyStop, nowEpochMs: Long): Boolean = false

    override suspend fun buy(stop: JourneyStop, extra: JourneyExtra, price: Int, nowEpochMs: Long): Boolean = false

    override suspend fun earn(earning: TaktEarning) = Unit
}
