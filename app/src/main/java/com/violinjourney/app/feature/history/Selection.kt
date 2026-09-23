package com.violinjourney.app.feature.history

/**
 * Picking several recordings to delete them at once (spec 3.18) — the same in «Записи» and in the
 * takes of a piece. [active] is apart from [ids]: «Выбрать» opens the mode with nothing picked yet.
 */
data class Selection(
    val active: Boolean = false,
    val ids: Set<Long> = emptySet(),
    /** «Удалить N записей?» is on the screen. */
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

    data object DeleteDismissed : SelectionIntent

    /** The view model deletes [Selection.ids]; the rules only close the mode. */
    data object DeleteConfirmed : SelectionIntent
}

/** Pure rules of the selection mode; `visible` is what the list shows now — after the filter. */
object SelectionRules {
    fun reduce(selection: Selection, intent: SelectionIntent, visible: List<Long>): Selection = when (intent) {
        SelectionIntent.SelectClicked -> if (visible.isEmpty()) selection else selection.copy(active = true)
        is SelectionIntent.CardLongPressed -> when {
            intent.id !in visible -> selection
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
        SelectionIntent.DeleteDismissed -> selection.copy(confirming = false)
        SelectionIntent.Closed, SelectionIntent.DeleteConfirmed -> Selection()
    }

    fun allSelected(selection: Selection, visible: List<Long>): Boolean =
        visible.isNotEmpty() && selection.ids.containsAll(visible)

    /**
     * A picked recording that is gone — deleted elsewhere, cleaned up — quietly drops out; when
     * nothing is left to pick from, the mode has nothing to be open for.
     */
    fun prune(selection: Selection, visible: List<Long>): Selection {
        if (!selection.active) return selection
        if (visible.isEmpty()) return Selection()
        val ids = selection.ids.intersect(visible.toSet())
        return if (ids.size == selection.ids.size) selection else selection.copy(ids = ids, confirming = selection.confirming && ids.isNotEmpty())
    }
}
