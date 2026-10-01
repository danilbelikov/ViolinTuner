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
    fun `a press goes on from the step its button showed`() {
        assertEquals(OnboardingPress.GoTo(OnboardingStep.LIVE), OnboardingFlow.press(OnboardingStep.WELCOME, OnboardingStep.WELCOME))
        assertEquals(OnboardingPress.GoTo(OnboardingStep.MICROPHONE), OnboardingFlow.press(OnboardingStep.DATA, OnboardingStep.DATA))
        assertEquals(OnboardingPress.AskMicrophone, OnboardingFlow.press(OnboardingStep.MICROPHONE, OnboardingStep.MICROPHONE))
        assertEquals(OnboardingPress.GoTo(OnboardingStep.TOLERANCE), OnboardingFlow.press(OnboardingStep.REFERENCE_PITCH, OnboardingStep.REFERENCE_PITCH))
        assertEquals(OnboardingPress.Finish, OnboardingFlow.press(OnboardingStep.TOLERANCE, OnboardingStep.TOLERANCE))
    }

    /** Two taps that both reach a button before the next frame (5.29 R8): the second one carries the step the first has moved past. */
    @Test
    fun `a second press from the step already left moves nothing`() {
        // «Понятно» twice: the second neither goes past «Микрофон» nor asks the system before its hint is read
        assertEquals(OnboardingPress.Stay, OnboardingFlow.press(OnboardingStep.MICROPHONE, OnboardingStep.DATA))
        // «Дальше» of «Эталон» twice: the second does not finish with «Допуск» unseen
        assertEquals(OnboardingPress.Stay, OnboardingFlow.press(OnboardingStep.TOLERANCE, OnboardingStep.REFERENCE_PITCH))
        // «Разрешить микрофон» once more after the answer: the system is not asked again
        assertEquals(OnboardingPress.Stay, OnboardingFlow.press(OnboardingStep.REFERENCE_PITCH, OnboardingStep.MICROPHONE))
        // «Начать играть» reaching the model after back: no finish from «Эталон»
        assertEquals(OnboardingPress.Stay, OnboardingFlow.press(OnboardingStep.REFERENCE_PITCH, OnboardingStep.TOLERANCE))
        // «Начать» of the first page under the dissolve of «Пропустить»: the page about the data stays
        assertEquals(OnboardingPress.Stay, OnboardingFlow.press(OnboardingStep.DATA, OnboardingStep.WELCOME))
    }

    @Test
    fun `a press from a step already left moves nothing and no press moves the way back`() {
        OnboardingStep.entries.forEach { current ->
            OnboardingStep.entries.forEach { from ->
                val press = OnboardingFlow.press(current, from)
                if (from < current) assertEquals(OnboardingPress.Stay, press, "a press from $from while the model is on $current")
                if (press is OnboardingPress.GoTo) assertTrue(press.step > current, "a press from $from while the model is on $current: back to ${press.step}")
            }
        }
    }

    @Test
    fun `a press on a page the pager stopped on before the model heard of it goes on from that page`() {
        // the third page stands in view, the model still says the second: the pager has not told it yet
        assertEquals(OnboardingPress.GoTo(OnboardingStep.DATA), OnboardingFlow.press(OnboardingStep.LIVE, OnboardingStep.JOURNEY))
    }

    @Test
    fun `skip leads to the page about the data once and only forward`() {
        assertEquals(OnboardingStep.DATA, OnboardingFlow.skipped(OnboardingStep.WELCOME, OnboardingStep.WELCOME))
        assertNull(OnboardingFlow.skipped(OnboardingStep.DATA, OnboardingStep.WELCOME), "the second tap of «Пропустить»")
        assertEquals(OnboardingStep.DATA, OnboardingFlow.skipped(OnboardingStep.LIVE, OnboardingStep.JOURNEY), "ahead of the model")
        assertNull(OnboardingFlow.skipped(OnboardingStep.MICROPHONE, OnboardingStep.LIVE), "never back")
        assertNull(OnboardingFlow.skipped(OnboardingStep.WELCOME, OnboardingStep.MICROPHONE), "not from the setup")
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
