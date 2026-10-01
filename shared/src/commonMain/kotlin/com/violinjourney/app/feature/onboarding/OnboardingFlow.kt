package com.violinjourney.app.feature.onboarding

/**
 * Where each move leads (spec 3.33): pure functions over the step. Asking the system and finishing are not moves: [press] only says
 * when they are due — the view model does them.
 */
object OnboardingFlow {

    /** The big button on a step that just goes on; null where the button does something else. */
    fun next(step: OnboardingStep): OnboardingStep? = when (step) {
        OnboardingStep.WELCOME, OnboardingStep.LIVE, OnboardingStep.JOURNEY, OnboardingStep.DATA,
        OnboardingStep.REFERENCE_PITCH,
        -> OnboardingStep.entries[step.ordinal + 1]
        OnboardingStep.MICROPHONE, OnboardingStep.TOLERANCE -> null
    }

    /** «Пропустить» leads to the page about the data: that one is read at least once. */
    fun skip(step: OnboardingStep): OnboardingStep = if (canSkip(step)) OnboardingStep.DATA else step

    fun canSkip(step: OnboardingStep): Boolean = step.part == OnboardingPart.INTRO && step < OnboardingStep.DATA

    /**
     * What a press of the big button made [from] the step it showed does while [current] is the step of the model (5.29 R8). The
     * press is bound to its own step, not to the model's: two taps that both reach a button before the next frame — the second on the
     * button the first one has already moved past — move the way once. So it goes only forward, to [next] of its own step: a press
     * from a page ahead of the model (the pager has stopped on it and not told the model yet; a pager on its way takes no tap at all)
     * goes on from that page, and one from a step already left — the same step pressed twice, or an earlier one — stays. «Разрешить
     * микрофон» asks the system and «Начать играть» finishes only while their own step is the model's.
     */
    fun press(current: OnboardingStep, from: OnboardingStep): OnboardingPress = when (from) {
        OnboardingStep.MICROPHONE -> if (current == from) OnboardingPress.AskMicrophone else OnboardingPress.Stay
        OnboardingStep.TOLERANCE -> if (current == from) OnboardingPress.Finish else OnboardingPress.Stay
        else -> next(from)?.takeIf { it > current }?.let(OnboardingPress::GoTo) ?: OnboardingPress.Stay
    }

    /** Where «Пропустить» pressed [from] a page leads while [current] is the step of the model: to the page about the data, only forward. */
    fun skipped(current: OnboardingStep, from: OnboardingStep): OnboardingStep? = skip(from).takeIf { canSkip(from) && it > current }

    /** One screen back, from the setup into the introduction too; null on the first — back leaves the app. */
    fun back(step: OnboardingStep): OnboardingStep? = OnboardingStep.entries.getOrNull(step.ordinal - 1)

    /** A swipe moves only within the introduction and only while it is shown. */
    fun swipedTo(step: OnboardingStep, page: Int): OnboardingStep =
        if (step.part == OnboardingPart.INTRO) OnboardingStep.intro.getOrElse(page) { step } else step
}

/** What a press of the big button does ([OnboardingFlow.press]). */
sealed interface OnboardingPress {
    /** The way goes on to [step]. */
    data class GoTo(val step: OnboardingStep) : OnboardingPress

    /** The system is asked for the microphone (spec 3.7); its answer moves on. */
    data object AskMicrophone : OnboardingPress

    /** The choices are kept and «Занятия» open (spec 3.25). */
    data object Finish : OnboardingPress

    /** The press finds the way already past its step: nothing happens. */
    data object Stay : OnboardingPress
}
