package com.violinjourney.app.feature.home

import com.violinjourney.app.core.domain.home.HomeCatalog
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The line under the name of a home not owned (spec 3.36.7, 5.29 R7): a home not drawn says «скоро» and its price whatever the purse;
 * a drawn one — what is saved of its price with a bar while the takts are short, «хватает» without a bar once they are enough.
 */
class NextHouseLineTest {
    private val wood = HomeCatalog.houseById.getValue("wood")
    private val flat = HomeCatalog.houseById.getValue("flat")

    @Test
    fun `the wooden house at 1 200 of 3 000 is short by four tenths of a bar`() {
        assertEquals(NextHouseLine.Short(balance = 1_200, price = 3_000, fraction = 0.4f), NextHouseLine.of(wood, 1_200))
    }

    @Test
    fun `the wooden house at its price and over it is enough`() {
        assertEquals(NextHouseLine.Enough(3_000), NextHouseLine.of(wood, 3_000))
        assertEquals(NextHouseLine.Enough(3_000), NextHouseLine.of(wood, 47_884))
    }

    @Test
    fun `a home not drawn yet is soon whatever the purse`() {
        assertEquals(NextHouseLine.Soon(6_000), NextHouseLine.of(flat, 0))
        assertEquals(NextHouseLine.Soon(6_000), NextHouseLine.of(flat, 47_884), "no «хватает» for what cannot be bought")
    }

    @Test
    fun `an empty purse is an empty bar`() {
        assertEquals(NextHouseLine.Short(balance = 0, price = 3_000, fraction = 0f), NextHouseLine.of(wood, 0))
    }
}
