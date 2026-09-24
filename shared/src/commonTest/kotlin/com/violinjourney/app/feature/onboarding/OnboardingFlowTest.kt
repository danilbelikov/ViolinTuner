package com.violinjourney.app.feature.onboarding

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

class OnboardingFlowTest {

    @Test
    fun `four pages of the introduction come before the three steps of the setup`() {
        assertEquals(listOf(OnboardingStep.WELCOME, OnboardingStep.LIVE, OnboardingStep.JOURNEY, OnboardingStep.DATA), OnboardingStep.intro)
        assertEquals(listOf(OnboardingStep.MICROPHONE, OnboardingStep.REFERENCE_PITCH, OnboardingStep.TOLERANCE), OnboardingStep.setup)
        assertEquals(3, OnboardingStep.DATA.indexInPart)
        assertEquals(0, OnboardingStep.MICROPHONE.indexInPart)
    }

    @Test
    fun `the button goes on everywhere but on the microphone and the last step`() {
        assertEquals(OnboardingStep.LIVE, OnboardingFlow.next(OnboardingStep.WELCOME))
        assertEquals(OnboardingStep.MICROPHONE, OnboardingFlow.next(OnboardingStep.DATA))
        assertEquals(OnboardingStep.TOLERANCE, OnboardingFlow.next(OnboardingStep.REFERENCE_PITCH))
        assertNull(OnboardingFlow.next(OnboardingStep.MICROPHONE))
        assertNull(OnboardingFlow.next(OnboardingStep.TOLERANCE))
    }

    @Test
    fun `skip leads to the page about the data from the first three pages only`() {
        listOf(OnboardingStep.WELCOME, OnboardingStep.LIVE, OnboardingStep.JOURNEY).forEach {
            assertTrue(OnboardingFlow.canSkip(it))
            assertEquals(OnboardingStep.DATA, OnboardingFlow.skip(it))
        }
        assertFalse(OnboardingFlow.canSkip(OnboardingStep.DATA))
        assertFalse(OnboardingFlow.canSkip(OnboardingStep.MICROPHONE))
        assertEquals(OnboardingStep.REFERENCE_PITCH, OnboardingFlow.skip(OnboardingStep.REFERENCE_PITCH))
    }

    @Test
    fun `back from the microphone returns to the page about the data — from the first page it leaves`() {
        assertEquals(OnboardingStep.DATA, OnboardingFlow.back(OnboardingStep.MICROPHONE))
        assertEquals(OnboardingStep.WELCOME, OnboardingFlow.back(OnboardingStep.LIVE))
        assertNull(OnboardingFlow.back(OnboardingStep.WELCOME))
    }

    @Test
    fun `a swipe is heard only within the introduction`() {
        assertEquals(OnboardingStep.JOURNEY, OnboardingFlow.swipedTo(OnboardingStep.WELCOME, 2))
        assertEquals(OnboardingStep.MICROPHONE, OnboardingFlow.swipedTo(OnboardingStep.MICROPHONE, 1))
        assertEquals(OnboardingStep.LIVE, OnboardingFlow.swipedTo(OnboardingStep.LIVE, 9))
    }
}
