package com.violinjourney.app.feature.practice

/** What the app asks about a running practice when it is opened (spec 3.12, «Забытое занятие»). */
sealed interface PracticePrompt {
    /** The violin has been quiet for too long: the «Занятие не закончено» dialog (handoff 10f1, 10f2). */
    data class Forgotten(
        val elapsedMs: Long,
        /** Null when the violin never sounded during this practice. */
        val lastSoundEpochMs: Long?,
    ) : PracticePrompt

    /** The summary sheet: the practice ran past the limit, or the user asked to set its length. */
    data class Summary(val sheet: PracticeSheet.Summary) : PracticePrompt
}

sealed interface PracticePromptIntent {
    /** «Закончить в 18:42»: the practice ends at the last sound. */
    data object EndAtLastSound : PracticePromptIntent

    data object EndNow : PracticePromptIntent

    /** «Продолжаю заниматься»: counts as a sign of life, the question comes back after another quiet hour. */
    data object Continue : PracticePromptIntent

    /** «Изменить время» of the no-sound dialog: opens the summary sheet with the stepper. */
    data object EditTime : PracticePromptIntent

    data class SummaryStepped(val steps: Int) : PracticePromptIntent

    data object SummarySaved : PracticePromptIntent

    data object SummaryDiscarded : PracticePromptIntent

    /** The sheet swiped away: only hidden, the practice stays — the question comes back the next time the app opens. */
    data object SummaryHidden : PracticePromptIntent
}
