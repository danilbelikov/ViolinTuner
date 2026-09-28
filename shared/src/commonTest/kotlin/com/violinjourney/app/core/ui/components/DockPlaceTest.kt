package com.violinjourney.app.core.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Where the bottom zone is, for the message of iOS (spec 5.29): the zone that came last counts, only its own zone withdraws it;
 * and how high the message rises over it — or over the bottom inset, when there is no zone or the keyboard covers it.
 */
class DockPlaceTest {
    private val leaving = Any()
    private val coming = Any()

    @Test
    fun `no zone reported — no place`() {
        assertNull(DockPlace().rise)
    }

    @Test
    fun `one zone gives its place`() {
        val place = DockPlace()
        place.report(leaving, 174)
        assertEquals(174, place.rise)
    }

    @Test
    fun `the zone that came last counts`() {
        val place = DockPlace()
        place.report(leaving, 174)
        // under the crossfade the screen that comes in reports before the one that leaves withdraws
        place.report(coming, 240)
        assertEquals(240, place.rise)
    }

    @Test
    fun `an older zone that moves does not take the first place back`() {
        val place = DockPlace()
        place.report(leaving, 174)
        place.report(coming, 240)
        place.report(leaving, 180)
        assertEquals(240, place.rise)
    }

    @Test
    fun `when the last zone goes the one before it counts again with its latest place`() {
        val place = DockPlace()
        place.report(leaving, 174)
        place.report(coming, 240)
        place.report(leaving, 180)
        place.withdraw(coming)
        assertEquals(180, place.rise)
        place.withdraw(leaving)
        assertNull(place.rise, "no zone left")
    }

    @Test
    fun `a zone that never reported withdraws nothing`() {
        val place = DockPlace()
        place.report(coming, 240)
        place.withdraw(leaving)
        assertEquals(240, place.rise)
    }

    @Test
    fun `withdrawing twice does no harm`() {
        val place = DockPlace()
        place.report(leaving, 174)
        place.report(coming, 240)
        place.withdraw(coming)
        place.withdraw(coming)
        assertEquals(174, place.rise)
    }

    @Test
    fun `with no zone the message stands 96 over the bottom inset`() {
        assertEquals(34 + ABOVE_INSET, ToastLift.of(dockRise = null, insetBottom = 34, aboveDock = ABOVE_DOCK, aboveInset = ABOVE_INSET))
    }

    @Test
    fun `over the tabs the message stands 12 over the zone`() {
        assertEquals(186, ToastLift.of(dockRise = 174, insetBottom = 34, aboveDock = ABOVE_DOCK, aboveInset = ABOVE_INSET))
    }

    @Test
    fun `a zone under the keyboard does not count — the message stands over the keyboard`() {
        assertEquals(336 + ABOVE_INSET, ToastLift.of(dockRise = 174, insetBottom = 336, aboveDock = ABOVE_DOCK, aboveInset = ABOVE_INSET))
    }

    @Test
    fun `a zone lifted above the keyboard counts`() {
        assertEquals(426, ToastLift.of(dockRise = 414, insetBottom = 336, aboveDock = ABOVE_DOCK, aboveInset = ABOVE_INSET))
    }

    @Test
    fun `a zone level with the inset is taken as under the keyboard`() {
        assertEquals(336 + ABOVE_INSET, ToastLift.of(dockRise = 336, insetBottom = 336, aboveDock = ABOVE_DOCK, aboveInset = ABOVE_INSET))
    }

    private companion object {
        const val ABOVE_DOCK = 12
        const val ABOVE_INSET = 96
    }
}
