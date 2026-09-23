package com.violinjourney.app.feature.onboarding

/**
 * Where each move leads (spec 3.33): pure functions over the step. What the big button does on a
 * step that asks the system or finishes is not a move — the view model handles those.
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

    /** One screen back, from the setup into the introduction too; null on the first — back leaves the app. */
    fun back(step: OnboardingStep): OnboardingStep? = OnboardingStep.entries.getOrNull(step.ordinal - 1)

    /** A swipe moves only within the introduction and only while it is shown. */
    fun swipedTo(step: OnboardingStep, page: Int): OnboardingStep =
        if (step.part == OnboardingPart.INTRO) OnboardingStep.intro.getOrElse(page) { step } else step
}
