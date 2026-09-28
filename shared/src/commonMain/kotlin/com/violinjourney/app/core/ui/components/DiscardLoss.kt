package com.violinjourney.app.core.ui.components

/**
 * What «Не сохранять?» of a form would lose (spec 3.36.1): the changes to something that has a name — said by the name it had before
 * the edit, «Изменения в «Менуэт соль мажор» пропадут.» — or just what was typed into a new one.
 */
sealed interface DiscardLoss {
    /** A new element, or one whose name there is nothing to call by. */
    data object Typed : DiscardLoss

    /** The edits of an element saved as [name]. */
    data class Changes(val name: String) : DiscardLoss

    companion object {
        /** [savedName] is the name as saved, before the edit; a new element has none that counts. */
        fun of(isNew: Boolean, savedName: String?): DiscardLoss {
            val name = savedName?.trim()
            return if (isNew || name.isNullOrEmpty()) Typed else Changes(name)
        }
    }
}
