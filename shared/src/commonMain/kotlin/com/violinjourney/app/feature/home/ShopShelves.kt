package com.violinjourney.app.feature.home

import com.violinjourney.app.core.domain.home.HomeCatalog
import com.violinjourney.app.core.domain.home.HomeGroup
import com.violinjourney.app.core.domain.home.HomeItem

/**
 * A shelf of the shop: its row ([group]), the [things] on it in the order of the catalogue, and whether the line «Полка ваша — всё
 * куплено» stands under it ([yours]).
 */
internal data class ShopShelf(val group: HomeGroup, val things: List<HomeItem>, val yours: Boolean)

/**
 * The shelves the shop shows (spec 3.24, 3.36.7). Pure, with a test.
 *
 * - The whole shop: the rows of the catalogue, [category] alone or all of them; what the rented room came with is not for sale.
 * - The shop by place ([slot], from «в лавке N →» of «Обставить»): on every row only the things of that place — those bought too, so a
 *   bought thing does not vanish from its shelf — and a row with none of them is not shown; the place is the one filter, the row of
 *   [category] does not narrow it further. «Полка ваша» is not said there: it is about a whole shelf, and here a shelf holds one place.
 *
 * [owned] — whether the player has a thing ([ShelfTags.owned]): a shelf of the whole shop all of which is owned is [ShopShelf.yours].
 */
internal object ShopShelves {
    fun of(category: HomeGroup?, slot: String?, owned: (HomeItem) -> Boolean = { false }): List<ShopShelf> =
        HomeGroup.entries
            .filter { group -> slot != null || category == null || category == group }
            .map { group ->
                val things = HomeCatalog.items.filter { it.group == group && it.id !in HomeCatalog.startItems && (slot == null || it.slot == slot) }
                ShopShelf(group, things, yours = slot == null && things.isNotEmpty() && things.all(owned))
            }
            .filter { it.things.isNotEmpty() }
}
