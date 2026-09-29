package com.violinjourney.app.feature.practice

import com.violinjourney.app.core.domain.practice.PracticeRecap

/**
 * What the one frame of the sheets of «Занятия» shows (spec 3.36.3): a sheet opened from another one — «Время за день» from the sheet of
 * the day, «Трофеи» and «Имя и фото» from «Мой путь» — stands in its place in the same frame, without sliding away, and «назад» on it
 * goes back to its parent; the recap takes the place of «Закончить занятие», the gift the place of the recap. Every sheet of «Занятия»
 * is a face of it since stage 107.
 */
sealed interface SheetFace {
    data class Summary(val sheet: PracticeSheet.Summary) : SheetFace

    data class Day(val day: SelectedDay) : SheetFace

    data class EditTime(val sheet: PracticeSheet.EditTime) : SheetFace

    data class Path(val header: ProfileHeader) : SheetFace

    /** «Имя и фото»: the draft of the name, and the header for the photo it changes. */
    data class Profile(val sheet: PracticeSheet.Profile, val header: ProfileHeader) : SheetFace

    /** «Трофеи»: every mark and the whole time at the violin. */
    data class Trophies(val lines: List<TrophyLine>, val totalMs: Long) : SheetFace

    /** «Занятие сохранено». */
    data class Recap(val recap: PracticeRecap) : SheetFace

    /** «Подарок»: not a sheet of the model — it comes when none is open. */
    data class Gift(val gift: com.violinjourney.app.feature.practice.Gift) : SheetFace
}

object SheetFaces {
    /**
     * The face of [state]: none without a sheet in the frame, and none while a record opened from the sheet of the day is on screen.
     * Without a sheet the gift is the face — but not while [appPromptShown]: «Занятие не закончено» is a sheet of the app over any
     * screen, and it lies over the gift, as the dialog did (spec 3.36.3); the gift comes once it has gone.
     */
    fun of(state: PracticeState, appPromptShown: Boolean = false): SheetFace? {
        if (state.sheetsAway) return null
        return when (val sheet = state.sheet) {
            is PracticeSheet.Summary -> SheetFace.Summary(sheet)
            is PracticeSheet.Day -> state.selected?.let(SheetFace::Day)
            is PracticeSheet.EditTime -> SheetFace.EditTime(sheet)
            PracticeSheet.Path -> SheetFace.Path(state.header)
            is PracticeSheet.Profile -> SheetFace.Profile(sheet, state.header)
            PracticeSheet.Trophies -> SheetFace.Trophies(state.trophies, state.header.totalMs)
            is PracticeSheet.Recap -> SheetFace.Recap(sheet.recap)
            null -> state.gift?.takeUnless { appPromptShown }?.let(SheetFace::Gift)
        }
    }

    /**
     * What a swipe, a tap beside the frame or «назад» without a parent means for [face] (spec 3.36.3): where a sheet has one safe
     * answer, that answer — «Готово» of the recap and of «Имя и фото» (the name is stored), «Спасибо» of the gift; otherwise it only
     * hides — the practice runs on, the day is not changed — and a face over another one gives that one back.
     */
    fun hideIntent(face: SheetFace): PracticeIntent = when (face) {
        is SheetFace.Summary -> PracticeIntent.SummaryHidden
        is SheetFace.Day -> PracticeIntent.DayHidden
        is SheetFace.EditTime -> PracticeIntent.EditTimeCancelled
        is SheetFace.Path -> PracticeIntent.PathHidden
        is SheetFace.Profile -> PracticeIntent.ProfileClosed
        is SheetFace.Trophies -> PracticeIntent.TrophiesClosed
        is SheetFace.Recap -> PracticeIntent.RecapClosed
        is SheetFace.Gift -> PracticeIntent.GiftAccepted(face.gift.hours)
    }

    /** «назад» of a face that stands over another one: its parent in place; null — the face has no parent, «назад» hides it. */
    fun backIntent(face: SheetFace): PracticeIntent? = when (face) {
        is SheetFace.EditTime -> PracticeIntent.EditTimeCancelled
        is SheetFace.Profile -> PracticeIntent.ProfileClosed
        is SheetFace.Trophies -> PracticeIntent.TrophiesClosed
        is SheetFace.Summary, is SheetFace.Day, is SheetFace.Path, is SheetFace.Recap, is SheetFace.Gift -> null
    }

    /**
     * Which face [face] is, for the frame: a new key is a new face — it starts at its top and, come in the place of another, holds its
     * main button for a double tap (5.29 R3). Its kind — a step of the stepper or a chip is the same face — but each gift of a row is
     * a face of its own: «Колок» after «Канифоль» is not «Канифоль» changed.
     */
    fun keyOf(face: SheetFace): Any = when (face) {
        is SheetFace.Gift -> face.gift.hours
        else -> face::class
    }

    /**
     * The buttons of [face] leave the bottom of the sheet for the end of what scrolls while the keyboard is up: «Имя и фото» in a
     * [lowWindow] (below 520 dp, as the recap and the gift, 5.29 R3) — pinned, they would leave its field less room over the keyboard
     * than the field itself; the keyboard's «Готово» answers as the button does.
     */
    fun buttonsInContentOverKeyboard(face: SheetFace, lowWindow: Boolean): Boolean = face is SheetFace.Profile && lowWindow
}
