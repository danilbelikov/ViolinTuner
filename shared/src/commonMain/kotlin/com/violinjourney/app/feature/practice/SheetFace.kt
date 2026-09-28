package com.violinjourney.app.feature.practice

/**
 * What the one frame of the sheets of «Занятия» shows (spec 3.36.3): a sheet opened from another one — «Время за день» from the sheet of
 * the day — stands in its place in the same frame, without sliding away, and «назад» on it goes back to its parent. Since stage 106 the
 * frame holds «Закончить занятие», the sheet of the day, «Время за день» and «Мой путь»; «Профиль», «Трофеи», the recap and the gift keep
 * their own windows until stage 107.
 */
sealed interface SheetFace {
    data class Summary(val sheet: PracticeSheet.Summary) : SheetFace

    data class Day(val day: SelectedDay) : SheetFace

    data class EditTime(val sheet: PracticeSheet.EditTime) : SheetFace

    data class Path(val header: ProfileHeader) : SheetFace
}

object SheetFaces {
    /** The face of [state]: none without a sheet in the frame, and none while a record opened from the sheet of the day is on screen. */
    fun of(state: PracticeState): SheetFace? {
        if (state.sheetsAway) return null
        return when (val sheet = state.sheet) {
            is PracticeSheet.Summary -> SheetFace.Summary(sheet)
            is PracticeSheet.Day -> state.selected?.let(SheetFace::Day)
            is PracticeSheet.EditTime -> SheetFace.EditTime(sheet)
            PracticeSheet.Path -> SheetFace.Path(state.header)
            else -> null
        }
    }

    /**
     * What a swipe, a tap beside the frame or «назад» without a parent means for [face] (spec 3.36.3): each only hides — the practice
     * runs on, the day is not changed — and a face over another one gives that one back.
     */
    fun hideIntent(face: SheetFace): PracticeIntent = when (face) {
        is SheetFace.Summary -> PracticeIntent.SummaryHidden
        is SheetFace.Day -> PracticeIntent.DayHidden
        is SheetFace.EditTime -> PracticeIntent.EditTimeCancelled
        is SheetFace.Path -> PracticeIntent.PathHidden
    }

    /** «назад» of a face that stands over another one: its parent in place; null — the face has no parent, «назад» hides it. */
    fun backIntent(face: SheetFace): PracticeIntent? = when (face) {
        is SheetFace.EditTime -> PracticeIntent.EditTimeCancelled
        is SheetFace.Summary, is SheetFace.Day, is SheetFace.Path -> null
    }
}
