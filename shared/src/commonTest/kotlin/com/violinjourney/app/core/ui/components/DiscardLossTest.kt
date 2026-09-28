package com.violinjourney.app.core.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

/** What «Не сохранять?» says is lost (spec 3.36.1): the changes to what has a name, or what was typed into a new element. */
class DiscardLossTest {
    @Test
    fun `a new element speaks of what was typed even when it has a name`() {
        assertEquals(DiscardLoss.Typed, DiscardLoss.of(isNew = true, savedName = "Менуэт соль мажор"))
        assertEquals(DiscardLoss.Typed, DiscardLoss.of(isNew = true, savedName = null))
    }

    @Test
    fun `an edit names the element as it was saved`() {
        assertEquals(DiscardLoss.Changes("Менуэт соль мажор"), DiscardLoss.of(isNew = false, savedName = "Менуэт соль мажор"))
    }

    @Test
    fun `the saved name is trimmed`() {
        assertEquals(DiscardLoss.Changes("Юмореска"), DiscardLoss.of(isNew = false, savedName = "  Юмореска \n"))
    }

    @Test
    fun `an edit without a name to call it by speaks of what was typed`() {
        assertEquals(DiscardLoss.Typed, DiscardLoss.of(isNew = false, savedName = null))
        assertEquals(DiscardLoss.Typed, DiscardLoss.of(isNew = false, savedName = ""))
        assertEquals(DiscardLoss.Typed, DiscardLoss.of(isNew = false, savedName = "   "))
    }
}
