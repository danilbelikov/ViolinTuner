package com.violinjourney.app.feature.history

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class SelectionRulesTest {
    private val visible = listOf(1L, 2L, 3L)

    private fun Selection.on(intent: SelectionIntent, shown: List<Long> = visible) = SelectionRules.reduce(this, intent, shown)

    @Test
    fun selectOpensTheModeWithNothingPicked() {
        assertEquals(Selection(active = true), Selection().on(SelectionIntent.SelectClicked))
    }

    @Test
    fun selectDoesNothingWhenThereIsNothingToPick() {
        assertEquals(Selection(), Selection().on(SelectionIntent.SelectClicked, shown = emptyList()))
    }

    @Test
    fun longPressOpensTheModeWithThatCardPicked() {
        assertEquals(Selection(active = true, ids = setOf(2L)), Selection().on(SelectionIntent.CardLongPressed(2)))
    }

    @Test
    fun longPressInsideTheModePicksToo() {
        val selection = Selection(active = true, ids = setOf(1L)).on(SelectionIntent.CardLongPressed(3))
        assertEquals(setOf(1L, 3L), selection.ids)
    }

    @Test
    fun tapOutsideTheModeIsNotASelection() {
        assertEquals(Selection(), Selection().on(SelectionIntent.CardToggled(1)))
    }

    @Test
    fun tapPicksAndUnpicks() {
        val two = Selection(active = true, ids = setOf(1L)).on(SelectionIntent.CardToggled(2))
        assertEquals(setOf(1L, 2L), two.ids)
        assertEquals(setOf(2L), two.on(SelectionIntent.CardToggled(1)).ids)
    }

    @Test
    fun unpickingTheLastOneClosesTheMode() {
        assertEquals(Selection(), Selection(active = true, ids = setOf(1L)).on(SelectionIntent.CardToggled(1)))
    }

    @Test
    fun aCardThatIsNotShownCannotBePicked() {
        val selection = Selection(active = true)
        assertEquals(selection, selection.on(SelectionIntent.CardToggled(9)))
        assertEquals(Selection(), Selection().on(SelectionIntent.CardLongPressed(9)))
    }

    @Test
    fun selectAllTakesWhatIsShownAndThenLetsItGo() {
        val all = Selection(active = true, ids = setOf(1L)).on(SelectionIntent.SelectAllClicked)
        assertEquals(setOf(1L, 2L, 3L), all.ids)
        assertTrue(SelectionRules.allSelected(all, visible))
        // «Снять все» keeps the mode open: it was asked for by name
        assertEquals(Selection(active = true), all.on(SelectionIntent.SelectAllClicked))
    }

    @Test
    fun nothingShownIsNeverAllSelected() {
        assertFalse(SelectionRules.allSelected(Selection(active = true), emptyList()))
    }

    @Test
    fun theBinAsksFirstAndOnlyWithSomethingPicked() {
        assertFalse(Selection(active = true).on(SelectionIntent.DeleteClicked).confirming)
        val asking = Selection(active = true, ids = setOf(1L)).on(SelectionIntent.DeleteClicked)
        assertTrue(asking.confirming)
        assertEquals(Selection(active = true, ids = setOf(1L)), asking.on(SelectionIntent.DeleteDismissed))
    }

    @Test
    fun closingAndConfirmingEndTheMode() {
        val asking = Selection(active = true, ids = setOf(1L, 2L), confirming = true)
        assertEquals(Selection(), asking.on(SelectionIntent.Closed))
        assertEquals(Selection(), asking.on(SelectionIntent.DeleteConfirmed))
    }

    @Test
    fun aRecordingThatIsGoneDropsOutQuietly() {
        val pruned = SelectionRules.prune(Selection(active = true, ids = setOf(1L, 2L), confirming = true), listOf(2L, 3L))
        assertEquals(Selection(active = true, ids = setOf(2L), confirming = true), pruned)
    }

    @Test
    fun theDialogDoesNotOutliveWhatItAskedAbout() {
        val pruned = SelectionRules.prune(Selection(active = true, ids = setOf(1L), confirming = true), listOf(2L))
        assertEquals(Selection(active = true), pruned)
    }

    @Test
    fun anEmptyListClosesTheMode() {
        assertEquals(Selection(), SelectionRules.prune(Selection(active = true, ids = setOf(1L)), emptyList()))
    }

    @Test
    fun pruningLeavesAClosedModeAlone() {
        assertEquals(Selection(), SelectionRules.prune(Selection(), emptyList()))
    }

    // «Удалить…» of the «⋯» of one card (spec 3.36.5): the question of the recording's screen, by the rules of picking.

    private val askingAboutTwo = Selection(ids = setOf(2L), confirming = true)

    @Test
    fun deleteOfOneCardAsksAboutItWithoutOpeningTheMode() {
        val asking = Selection().on(SelectionIntent.DeleteOneClicked(2))
        assertEquals(askingAboutTwo, asking)
        assertFalse(asking.active)
    }

    @Test
    fun dismissingTheQuestionAboutOneLeavesNothingPickedAndConfirmingCloses() {
        assertEquals(Selection(), askingAboutTwo.on(SelectionIntent.DeleteDismissed))
        assertEquals(Selection(), askingAboutTwo.on(SelectionIntent.DeleteConfirmed))
        assertEquals(Selection(), askingAboutTwo.on(SelectionIntent.Closed))
    }

    @Test
    fun deleteOfOneIsNotAskedForACardNotShownNorWhilePicking() {
        assertEquals(Selection(), Selection().on(SelectionIntent.DeleteOneClicked(9)))
        val picking = Selection(active = true, ids = setOf(1L))
        assertEquals(picking, picking.on(SelectionIntent.DeleteOneClicked(2)))
        // one question at a time
        assertEquals(askingAboutTwo, askingAboutTwo.on(SelectionIntent.DeleteOneClicked(3)))
    }

    @Test
    fun theModeDoesNotOpenUnderTheQuestionAboutOne() {
        assertEquals(askingAboutTwo, askingAboutTwo.on(SelectionIntent.SelectClicked))
        assertEquals(askingAboutTwo, askingAboutTwo.on(SelectionIntent.CardLongPressed(1)))
        assertEquals(askingAboutTwo, askingAboutTwo.on(SelectionIntent.CardToggled(1)))
        assertEquals(askingAboutTwo, askingAboutTwo.on(SelectionIntent.SelectAllClicked))
        assertEquals(askingAboutTwo, askingAboutTwo.on(SelectionIntent.DeleteClicked))
    }

    @Test
    fun theQuestionAboutOneGoesWithItsCard() {
        assertEquals(Selection(), SelectionRules.prune(askingAboutTwo, listOf(1L, 3L)))
        assertEquals(askingAboutTwo, SelectionRules.prune(askingAboutTwo, listOf(2L, 3L)))
    }

    @Test
    fun selectAllTakesOnlyWhatTheChipShows() {
        val all = Selection(active = true).on(SelectionIntent.SelectAllClicked, shown = listOf(2L))
        assertEquals(setOf(2L), all.ids)
    }
}
