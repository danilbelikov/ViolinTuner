package com.violinjourney.app.feature.history

/**
 * Picking several recordings to delete them at once (spec 3.18) — the same in «Записи» and in the
 * takes of a piece. [active] is apart from [ids]: «Выбрать» opens the mode with nothing picked yet.
 *
 * «Удалить…» of the «⋯» of one card (spec 3.36.5) goes the same way without opening the mode: [confirming] with [active] false
 * and that one card in [ids] — the question of the recording's own screen over the list, and on «Удалить» the same deletion of
 * the picked ones in one transaction.
 */
data class Selection(
    val active: Boolean = false,
    val ids: Set<Long> = emptySet(),
    /** «Удалить N записей?» — or «Удалить запись?» of one card's «⋯» — is on the screen. */
    val confirming: Boolean = false,
) {
    val count: Int get() = ids.size
}

sealed interface SelectionIntent {
    /** «Выбрать» above the list. */
    data object SelectClicked : SelectionIntent

    /** A long press: the mode opens with this card picked. */
    data class CardLongPressed(val id: Long) : SelectionIntent

    /** A tap on a card while the mode is open. */
    data class CardToggled(val id: Long) : SelectionIntent

    /** «Выбрать все», or «Снять все» when all of them are. */
    data object SelectAllClicked : SelectionIntent

    /** The cross, the system back, leaving the screen. */
    data object Closed : SelectionIntent

    data object DeleteClicked : SelectionIntent

    /** «Удалить…» of the «⋯» of one card: its question, the mode stays shut. */
    data class DeleteOneClicked(val id: Long) : SelectionIntent

    data object DeleteDismissed : SelectionIntent

    /** The view model deletes [Selection.ids]; the rules only close the mode. */
    data object DeleteConfirmed : SelectionIntent
}

/** Pure rules of the selection mode; `visible` is what the list shows now — after the filter. */
object SelectionRules {
    fun reduce(selection: Selection, intent: SelectionIntent, visible: List<Long>): Selection = when (intent) {
        SelectionIntent.SelectClicked -> if (visible.isEmpty() || asksAboutOne(selection)) selection else selection.copy(active = true)
        is SelectionIntent.CardLongPressed -> when {
            intent.id !in visible || asksAboutOne(selection) -> selection
            // a long press inside the mode is just another way to pick
            else -> selection.copy(active = true, ids = selection.ids + intent.id)
        }
        is SelectionIntent.CardToggled -> when {
            !selection.active || intent.id !in visible -> selection
            intent.id !in selection.ids -> selection.copy(ids = selection.ids + intent.id)
            // taking the last one off closes the mode
            selection.ids.size == 1 -> Selection()
            else -> selection.copy(ids = selection.ids - intent.id)
        }
        SelectionIntent.SelectAllClicked -> when {
            !selection.active -> selection
            allSelected(selection, visible) -> selection.copy(ids = emptySet())
            else -> selection.copy(ids = visible.toSet())
        }
        SelectionIntent.DeleteClicked -> if (selection.active && selection.ids.isNotEmpty()) selection.copy(confirming = true) else selection
        // the «⋯» of a card is not there while picking, and one question at a time
        is SelectionIntent.DeleteOneClicked -> when {
            selection.active || selection.confirming || intent.id !in visible -> selection
            else -> Selection(ids = setOf(intent.id), confirming = true)
        }
        // «Отмена» of the question about one card leaves nothing picked behind; of the mode — the mode with its picks
        SelectionIntent.DeleteDismissed -> if (selection.active) selection.copy(confirming = false) else Selection()
        SelectionIntent.Closed, SelectionIntent.DeleteConfirmed -> Selection()
    }

    /** The question of «Удалить…» about one card is on the screen: the mode does not open under it. */
    private fun asksAboutOne(selection: Selection): Boolean = !selection.active && selection.confirming

    fun allSelected(selection: Selection, visible: List<Long>): Boolean =
        visible.isNotEmpty() && selection.ids.containsAll(visible)

    /**
     * A picked recording that is gone — deleted elsewhere, cleaned up — quietly drops out; when
     * nothing is left to pick from, the mode has nothing to be open for. The question about one
     * card goes with the card.
     */
    fun prune(selection: Selection, visible: List<Long>): Selection {
        if (!selection.active) return if (asksAboutOne(selection) && selection.ids.any { it !in visible }) Selection() else selection
        if (visible.isEmpty()) return Selection()
        val ids = selection.ids.intersect(visible.toSet())
        return if (ids.size == selection.ids.size) selection else selection.copy(ids = ids, confirming = selection.confirming && ids.isNotEmpty())
    }
}
