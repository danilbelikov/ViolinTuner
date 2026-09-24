package com.violinjourney.app.feature.live.block

import com.violinjourney.app.core.domain.repertoire.SectionRef

/** The bookmark by the record key (spec 3.28, handoff 30b). */
sealed interface Bookmark {
    /** «Репертуар»: nothing is being played for a goal — no practice, no block, or the last one was stopped. */
    data object Entry : Bookmark

    /** A block runs: the element, whole minutes left and how far the brass line has run. */
    data class Running(val title: String, val minutesLeft: Int, val progress: Float) : Bookmark

    /** The block reached its goal: «готово» until the next one or the end of the practice. */
    data class Done(val title: String) : Bookmark
}

/** What «Что играем» says of an element for today (spec 3.28): by shape, not only by colour. */
sealed interface TodayMark {
    data object None : TodayMark

    /** Played, but no block of it reached its goal: just the minutes. */
    data class Played(val ms: Long) : TodayMark

    /** A block of it reached its goal: the tick and the minutes of the day. */
    data class Done(val ms: Long) : TodayMark

    /** Its block runs now: «идёт», and it cannot be picked. */
    data object Running : TodayMark
}

data class PickerPiece(
    val id: Long,
    val title: String,
    /** The composer where there is one; scales and strokes explain themselves by name. */
    val composer: String?,
    val today: TodayMark,
)

data class PickerSection(
    val ref: SectionRef,
    /** Null for a built-in section: its name is a word of the interface. */
    val name: String?,
    /** Elements of the section played for their goal today: «Гаммы · сегодня 1», shown when not zero. */
    val doneToday: Int,
    val pieces: List<PickerPiece>,
)

/** «Сейчас: Концерт ля минор · ещё 7 мин» above the list while a block runs. */
data class NowLine(val title: String, val minutesLeft: Int)

sealed interface BlockSheet {
    /** «Сначала — занятие»: no practice runs. */
    data object Offer : BlockSheet

    /** «Что играем». */
    data class Picker(
        val now: NowLine?,
        /** Empty: the repertoire is empty. */
        val sections: List<PickerSection>,
        /** The element picked: the goal panel comes with it. */
        val selectedId: Long?,
        val goalMinutes: Int,
        val quickGoals: List<Int>,
        val goalStep: Int,
        val canGoalDown: Boolean,
        val canGoalUp: Boolean,
    ) : BlockSheet {
        val selectedTitle: String?
            get() = sections.firstNotNullOfOrNull { section -> section.pieces.firstOrNull { it.id == selectedId }?.title }
    }
}

data class BlockState(
    val bookmark: Bookmark,
    /** Null while no sheet is open. */
    val sheet: BlockSheet?,
) {
    companion object {
        val NONE = BlockState(Bookmark.Entry, sheet = null)
    }
}

sealed interface BlockIntent {
    data object BookmarkClicked : BlockIntent

    /** A swipe down, a tap on the scrim, «назад». */
    data object SheetDismissed : BlockIntent

    /** «Начать занятие» of the offer: the practice starts here, Live stays. */
    data object StartPracticeClicked : BlockIntent

    data object NotNowClicked : BlockIntent

    data class PieceClicked(val id: Long) : BlockIntent

    data class GoalPicked(val minutes: Int) : BlockIntent

    /** −5 / +5: one step down or up. */
    data class GoalStepped(val steps: Int) : BlockIntent

    data object StartClicked : BlockIntent

    data object StopClicked : BlockIntent

    data object OpenRepertoireClicked : BlockIntent
}

sealed interface BlockEffect {
    /** The repertoire is empty: to «Записи» → «Репертуар», where things are added. */
    data object OpenRepertoire : BlockEffect
}
