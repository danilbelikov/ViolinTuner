package com.violinjourney.app.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

/** The light of the tab bar (spec 3.36.1): lent by a screen, taken back only by the one who lent it. */
class TabBarLightTest {
    private val liveOne = Any()
    private val liveTwo = Any()

    @Test
    fun `nobody lends and the bar is in full light`() {
        assertEquals(1f, TabBarLight().alpha())
    }

    @Test
    fun `a lent light is read and follows its lender`() {
        val light = TabBarLight()
        var shown = 0.38f
        light.lend(liveOne) { shown }
        assertEquals(0.38f, light.alpha())
        shown = 0.7f
        assertEquals(0.7f, light.alpha(), "read at every draw, not kept")
    }

    @Test
    fun `a late taking back of the one who left does not put out the newcomer`() {
        val light = TabBarLight()
        light.lend(liveOne) { 0.5f }
        // under the crossfade the screen that comes in lends before the one that leaves takes back
        light.lend(liveTwo) { 0.38f }
        light.takeBack(liveOne)
        assertEquals(0.38f, light.alpha())
    }

    @Test
    fun `the lender taking back returns the full light`() {
        val light = TabBarLight()
        light.lend(liveOne) { 0.38f }
        light.takeBack(liveOne)
        assertEquals(1f, light.alpha())
    }
}
