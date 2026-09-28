package com.violinjourney.app.feature.practice

import com.violinjourney.app.core.domain.practice.RunningPractice

/** What the app asks about a running practice when it is opened (spec 3.12, «Забытое занятие»; 3.36.3). */
sealed interface PracticePrompt {
    /**
     * The violin has been quiet for too long: the sheet «Занятие не закончено» (spec 3.36.3). [practice] — the running practice as
     * it was when the question was asked: its start gives the numbers of the buttons, and «Закончить в 18:42» ends at the time
     * written on the button, whatever Live hears under the sheet meanwhile.
     */
    data class Forgotten(val practice: RunningPractice) : PracticePrompt {
        /** Which of the three lines the sheet says, and which answer comes first. */
        val kind: ForgottenKind
            get() = when {
                practice.lastSoundEpochMs == null -> ForgottenKind.Silent
                practice.lastMarkByAnswer -> ForgottenKind.Answered
                else -> ForgottenKind.Sounded
            }
    }

    /**
     * «Закончить занятие» of the app, over any screen: the practice ran past the limit, or «Указать, сколько играли» was answered.
     * It stands in the place of «Занятие не закончено» and does not go back to it.
     */
    data class Summary(val sheet: PracticeSheet.Summary) : PracticePrompt
}

/** The three lines of «Занятие не закончено» (spec 3.36.3). */
enum class ForgottenKind {
    /** «Скрипка звучала последний раз в 18:42…»: «Закончить в 18:42 · …» first. */
    Sounded,

    /** «Скрипка не звучала с начала занятия…»: nothing to end at — «Указать, сколько играли» first. */
    Silent,

    /** «В 19:05 вы ответили „Продолжаю заниматься“…»: the last mark is the answer's; its buttons are those of [Sounded]. */
    Answered,
}

sealed interface PracticePromptIntent {
    /** «Закончить в 18:42»: the practice ends at the last mark — the time written on the button. */
    data object EndAtLastSound : PracticePromptIntent

    data object EndNow : PracticePromptIntent

    /**
     * «Продолжаю заниматься» — and a swipe, «назад», a tap beside the sheet: a sign of life, the question comes back after another
     * quiet hour (spec 3.12, 3.36.3).
     */
    data object Continue : PracticePromptIntent

    /** «Указать, сколько играли» (spec 3.36.3; was «Изменить время»): «Закончить занятие» with the stepper takes the sheet's place. */
    data object SetLength : PracticePromptIntent

    data class SummaryStepped(val steps: Int) : PracticePromptIntent

    data object SummarySaved : PracticePromptIntent

    data object SummaryDiscarded : PracticePromptIntent

    /** The sheet swiped away: only hidden, the practice stays — the question comes back the next time the app opens. */
    data object SummaryHidden : PracticePromptIntent
}

/** What the prompt says without asking (spec 3.12). */
sealed interface PracticePromptEffect {
    /** «Слишком коротко»: a practice under a minute — ended at its last sound, or by itself past the limit — is not kept. */
    data object ShowTooShort : PracticePromptEffect
}
