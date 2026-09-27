package com.violinjourney.app.feature.home

import com.violinjourney.app.core.domain.home.HomeItem
import com.violinjourney.app.core.domain.home.HomeRules
import com.violinjourney.app.core.domain.home.HomeState
import com.violinjourney.app.core.domain.journey.JourneyProgress
import kotlinx.datetime.LocalDate

/** What a thing on a shelf says under its name (spec 3.24, 3.29): its price, a gift, «в комнате» / «у дома», «куплено», «привезут». */
internal enum class ShelfTag { PRICE, GIFT, STANDING, OWNED, LOCKED }

/**
 * The tags of the shop for one state of the home, worked out once for all the shelves rather than per tile. «В комнате»
 * and «у дома» are for what is seen now in [house], the home lived in, on [date]: a thing whose place this home lacks,
 * and the tree out of its season, are bought and wait — «куплено». Pure.
 */
internal class ShelfTags(state: HomeState, private val progress: JourneyProgress, house: String, date: LocalDate) {
    private val owned = HomeRules.ownedItems(state)
    private val placed = HomeRules.placed(state)
    private val seen: Set<String> =
        (HomeRules.standing(state, house, outside = false, date = date) + HomeRules.standing(state, house, outside = true, date = date)).mapTo(HashSet()) { it.id }

    fun of(item: HomeItem): ShelfTag = when {
        item.id in owned -> if (item.id in seen) ShelfTag.STANDING else ShelfTag.OWNED
        !HomeRules.unlocked(item, progress) -> ShelfTag.LOCKED
        item.price == 0 -> ShelfTag.GIFT
        else -> ShelfTag.PRICE
    }

    /** It is the choice of its place: seen, or waiting for a home with that place or for its season — nothing to «Поставить». */
    fun chosen(item: HomeItem): Boolean = placed[item.slot]?.id == item.id

    fun owned(item: HomeItem): Boolean = item.id in owned
}
