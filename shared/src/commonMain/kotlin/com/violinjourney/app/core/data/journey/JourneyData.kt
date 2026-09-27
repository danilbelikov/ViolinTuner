package com.violinjourney.app.core.data.journey

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import com.violinjourney.app.core.domain.home.HomeCatalog
import com.violinjourney.app.core.domain.home.HomeHouse
import com.violinjourney.app.core.domain.home.HomeItem
import com.violinjourney.app.core.domain.home.HomeRepository
import com.violinjourney.app.core.domain.home.HomeRules
import com.violinjourney.app.core.domain.home.HomeState
import com.violinjourney.app.core.domain.journey.Arrival
import com.violinjourney.app.core.domain.journey.BoughtExtra
import com.violinjourney.app.core.domain.journey.JourneyExtra
import com.violinjourney.app.core.domain.journey.JourneyProgress
import com.violinjourney.app.core.domain.journey.JourneyRepository
import com.violinjourney.app.core.domain.journey.JourneyRoute
import com.violinjourney.app.core.domain.journey.JourneyStop
import com.violinjourney.app.core.domain.journey.TaktEarning
import com.violinjourney.app.core.analytics.Analytics
import com.violinjourney.app.core.analytics.CityReached
import com.violinjourney.app.core.analytics.ItemBought
import com.violinjourney.app.core.analytics.NoOpAnalytics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** What one saved practice earned (spec 5.17). The balance is never a column: it is the earnings minus what the two other tables cost. */
@Entity(tableName = "journey_earnings")
data class EarningEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val atEpochMs: Long,
    val notesPlayed: Int,
    val notesInTune: Int,
    val durationMs: Long,
    val takts: Int,
    /** Elements whose blocks were paid for (spec 3.28); earnings older than blocks paid for none. */
    @ColumnInfo(defaultValue = "0") val piecesPaid: Int = 0,
)

/** A stop that has been reached, with what the leg cost then: a later change of prices does not rewrite the balance. */
@Entity(tableName = "journey_arrivals")
data class ArrivalEntity(@PrimaryKey val stopId: String, val arrivedAtEpochMs: Long, val price: Int)

@Entity(tableName = "journey_extras", primaryKeys = ["stopId", "extra"])
data class ExtraEntity(val stopId: String, val extra: String, val price: Int, val boughtAtEpochMs: Long)

/** A thing or a home bought for the home (spec 3.24), with what it cost then. One purse with the road: the balance subtracts this table as well. */
@Entity(tableName = "home_purchases")
data class HomePurchaseEntity(@PrimaryKey val id: String, val kind: String, val price: Int, val boughtAtEpochMs: Long)

/**
 * What stands where: slot → item id, an empty id — the place left bare; the key `@house` — the home lived in, `@season` —
 * what stood in the place of the tree when it was put there (spec 3.24).
 */
@Entity(tableName = "home_choices")
data class HomeChoiceEntity(@PrimaryKey val slot: String, val itemId: String)

@Dao
abstract class JourneyDao {
    @Query("SELECT * FROM home_purchases")
    abstract fun observeHomePurchases(): Flow<List<HomePurchaseEntity>>

    @Query("SELECT * FROM home_choices")
    abstract fun observeHomeChoices(): Flow<List<HomeChoiceEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertHomePurchase(purchase: HomePurchaseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putHomeChoice(choice: HomeChoiceEntity)

    @Query("SELECT itemId FROM home_choices WHERE slot = :slot")
    protected abstract suspend fun choiceOf(slot: String): String?

    @Query("DELETE FROM home_choices WHERE slot = :slot")
    protected abstract suspend fun clearChoice(slot: String)

    /**
     * Puts [id] into [slot]. A thing that stands only in its season remembers under [underKey] what stood in its place,
     * so that the place shows it the rest of the year (spec 3.24): no row — the room's own, an empty id — nothing. Put
     * again where it already stands, it keeps what it remembers. [underKey] null — a thing like any other.
     */
    @Transaction
    open suspend fun putOnPlace(slot: String, id: String, underKey: String?) {
        if (underKey != null) {
            val before = choiceOf(slot)
            if (before != id) {
                if (before == null) clearChoice(underKey) else putHomeChoice(HomeChoiceEntity(underKey, before))
            }
        }
        putHomeChoice(HomeChoiceEntity(slot, id))
    }

    /** Pays for a thing of the home and puts it where it belongs ([putOnPlace]), or does neither. */
    @Transaction
    open suspend fun buyForHome(id: String, kind: String, price: Int, slot: String, now: Long, underKey: String? = null): Boolean {
        if (balance() < price) return false
        if (insertHomePurchase(HomePurchaseEntity(id, kind, price, now)) == -1L) return false
        putOnPlace(slot, id, underKey)
        return true
    }

    @Query("SELECT * FROM journey_earnings ORDER BY id")
    abstract fun observeEarnings(): Flow<List<EarningEntity>>

    @Query("SELECT * FROM journey_arrivals")
    abstract fun observeArrivals(): Flow<List<ArrivalEntity>>

    @Query("SELECT * FROM journey_extras")
    abstract fun observeExtras(): Flow<List<ExtraEntity>>

    @Insert
    abstract suspend fun insertEarning(earning: EarningEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertArrival(arrival: ArrivalEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertExtra(extra: ExtraEntity): Long

    @Query("SELECT stopId FROM journey_arrivals")
    protected abstract suspend fun arrivedIds(): List<String>

    @Query(
        "SELECT COALESCE((SELECT SUM(takts) FROM journey_earnings), 0) - COALESCE((SELECT SUM(price) FROM journey_arrivals), 0) " +
            "- COALESCE((SELECT SUM(price) FROM journey_extras), 0) - COALESCE((SELECT SUM(price) FROM home_purchases), 0)",
    )
    protected abstract suspend fun balance(): Long

    suspend fun begin(homeId: String, now: Long) {
        insertArrival(ArrivalEntity(homeId, now, price = 0))
    }

    /** Pays and arrives in one transaction; [previousId] must have been reached — the route has no shortcuts. */
    @Transaction
    open suspend fun arrive(stopId: String, previousId: String, price: Int, now: Long): Boolean {
        val arrived = arrivedIds()
        if (previousId !in arrived || stopId in arrived || balance() < price) return false
        return insertArrival(ArrivalEntity(stopId, now, price)) != -1L
    }

    @Transaction
    open suspend fun buyExtra(stopId: String, extra: String, price: Int, now: Long): Boolean {
        if (stopId !in arrivedIds() || balance() < price) return false
        return insertExtra(ExtraEntity(stopId, extra, price, now)) != -1L
    }
}

class RoomJourneyRepository(
    private val dao: JourneyDao,
    private val analytics: Analytics = NoOpAnalytics(),
) : JourneyRepository {
    override val progress: Flow<JourneyProgress> =
        combine(dao.observeEarnings(), dao.observeArrivals(), dao.observeExtras(), dao.observeHomePurchases()) { earnings, arrivals, extras, home ->
            JourneyProgress(
                earned = earnings.sumOf { it.takts.toLong() },
                // one purse: the road, the extras of the stops and the home (spec 3.24)
                spent = arrivals.sumOf { it.price.toLong() } + extras.sumOf { it.price.toLong() } + home.sumOf { it.price.toLong() },
                arrivals = arrivals.map { Arrival(it.stopId, it.arrivedAtEpochMs) },
                // an extra this build does not know (a row written by a newer one) is simply not shown
                extras = extras.mapNotNull { row -> JourneyExtra.entries.firstOrNull { it.name == row.extra }?.let { BoughtExtra(row.stopId, it) } }.toSet(),
            )
        }

    override suspend fun start(nowEpochMs: Long) = dao.begin(JourneyRoute.HOME, nowEpochMs)

    override suspend fun depart(stop: JourneyStop, nowEpochMs: Long): Boolean {
        val index = JourneyRoute.indexOf(stop.id)
        if (index <= 0 || !stop.available) return false
        return dao.arrive(stop.id, JourneyRoute.stops[index - 1].id, stop.price, nowEpochMs)
            .also { arrived -> if (arrived) analytics.track(CityReached(index)) }
    }

    override suspend fun buy(stop: JourneyStop, extra: JourneyExtra, price: Int, nowEpochMs: Long): Boolean =
        dao.buyExtra(stop.id, extra.name, price, nowEpochMs)

    override suspend fun earn(earning: TaktEarning) {
        if (earning.takts <= 0) return
        dao.insertEarning(EarningEntity(0, earning.atEpochMs, earning.notesPlayed, earning.notesInTune, earning.durationMs, earning.takts, earning.piecesPaid))
    }
}

class RoomHomeRepository(
    private val dao: JourneyDao,
    private val analytics: Analytics = NoOpAnalytics(),
) : HomeRepository {
    override val state: Flow<HomeState> = combine(dao.observeHomePurchases(), dao.observeHomeChoices()) { purchases, choices ->
        HomeState(
            loaded = true,
            purchased = purchases.filter { it.kind == ITEM }.map { it.id }.toSet(),
            houses = purchases.filter { it.kind == HOUSE }.map { it.id }.toSet(),
            choices = choices.associate { it.slot to it.itemId },
            movedInAtEpochMs = purchases.filter { it.kind == HOUSE }.maxOfOrNull { it.boughtAtEpochMs },
        )
    }

    override suspend fun buy(item: HomeItem, nowEpochMs: Long): Boolean =
        dao.buyForHome(item.id, ITEM, item.price, item.slot, nowEpochMs, HomeRules.underKeyOf(item))
            .also { bought -> if (bought) analytics.track(ItemBought(item.id, house = false)) }

    override suspend fun buy(house: HomeHouse, nowEpochMs: Long): Boolean =
        (house.drawn && dao.buyForHome(house.id, HOUSE, house.price, HomeState.HOUSE_KEY, nowEpochMs))
            .also { bought -> if (bought) analytics.track(ItemBought(house.id, house = true)) }

    override suspend fun place(slot: String, itemId: String) = dao.putOnPlace(slot, itemId, HomeCatalog.byId[itemId]?.let(HomeRules::underKeyOf))

    override suspend fun liveIn(house: String) = dao.putHomeChoice(HomeChoiceEntity(HomeState.HOUSE_KEY, house))

    private companion object {
        const val ITEM = "ITEM"
        const val HOUSE = "HOUSE"
    }
}
