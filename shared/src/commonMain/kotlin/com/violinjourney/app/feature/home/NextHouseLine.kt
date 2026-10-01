package com.violinjourney.app.feature.home

import com.violinjourney.app.core.domain.home.HomeHouse

/**
 * What the row of a home not owned yet says under its name (spec 3.36.7, 5.29 R7): the next home on «Дом» and a home to buy in «Дома».
 * Pure, with a test.
 *
 * - [Soon] — not drawn yet (it comes with an update): «скоро · 6 000», no bar — saving for what cannot be bought is not offered (3.24);
 * - [Short] — drawn, the takts are short: «1 200 / 3 000» and the bar to the price, [fraction] of it filled;
 * - [Enough] — drawn, the takts are enough: «хватает · 3 000» in the accent, no bar — a full bar says nothing.
 */
sealed interface NextHouseLine {
    val price: Int

    data class Soon(override val price: Int) : NextHouseLine

    data class Short(val balance: Long, override val price: Int, val fraction: Float) : NextHouseLine

    data class Enough(override val price: Int) : NextHouseLine

    companion object {
        fun of(house: HomeHouse, balance: Long): NextHouseLine = when {
            !house.drawn -> Soon(house.price)
            balance >= house.price -> Enough(house.price)
            else -> Short(balance, house.price, (balance.toFloat() / house.price).coerceIn(0f, 1f))
        }
    }
}
