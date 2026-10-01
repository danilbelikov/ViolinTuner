package com.violinjourney.app.feature.onboarding

import com.violinjourney.app.core.domain.TolerancePreset

/**
 * Screens in the order they are shown (spec 3.33): four pages of the introduction, then the three
 * steps of the setup (spec 3.7).
 */
enum class OnboardingStep(val part: OnboardingPart) {
    WELCOME(OnboardingPart.INTRO),
    LIVE(OnboardingPart.INTRO),
    JOURNEY(OnboardingPart.INTRO),
    DATA(OnboardingPart.INTRO),
    MICROPHONE(OnboardingPart.SETUP),
    REFERENCE_PITCH(OnboardingPart.SETUP),
    TOLERANCE(OnboardingPart.SETUP),
    ;

    /** Place within its own part: the page of the introduction, the step of the setup. */
    val indexInPart: Int get() = entries.filter { it.part == part }.indexOf(this)

    /** Place on the whole way, from 1 to 7: «Знакомство · 1 из 7», «Настройка · 5 из 7» (spec 3.36.8). */
    val number: Int get() = ordinal + 1

    companion object {
        val intro: List<OnboardingStep> = entries.filter { it.part == OnboardingPart.INTRO }
        val setup: List<OnboardingStep> = entries.filter { it.part == OnboardingPart.SETUP }
    }
}

/** The introduction is read and swiped through; the setup is chosen step by step. */
enum class OnboardingPart { INTRO, SETUP }

data class OnboardingState(
    val step: OnboardingStep,
    val a4Hz: Int,
    val a4OptionsHz: List<Int>,
    val tolerance: TolerancePreset,
)

sealed interface OnboardingIntent {
    /**
     * The big button at the bottom, pressed [from] the step it showed; what it does depends on that step ([OnboardingFlow.press]). A
     * press is bound to its step: a second tap that reaches the same button before the next frame moves nothing again (5.29 R8).
     */
    data class PrimaryClicked(val from: OnboardingStep) : OnboardingIntent

    /** «Пропустить» on the first three pages, pressed [from] the page in view: to the page about the data, never past it. */
    data class SkipClicked(val from: OnboardingStep) : OnboardingIntent

    /** A page of the introduction was swiped to. */
    data class PageShown(val page: Int) : OnboardingIntent

    /** The system dialog was answered; either answer moves on (spec 3.7). */
    data object MicPermissionAnswered : OnboardingIntent

    data class A4Selected(val hz: Int) : OnboardingIntent

    data class ToleranceSelected(val preset: TolerancePreset) : OnboardingIntent

    /** System back on any screen but the first. */
    data object BackPressed : OnboardingIntent
}

sealed interface OnboardingEffect {
    data object RequestMicPermission : OnboardingEffect

    /** Settings are stored and the flag is set: open «Занятия» (spec 3.25). */
    data object Finished : OnboardingEffect
}
