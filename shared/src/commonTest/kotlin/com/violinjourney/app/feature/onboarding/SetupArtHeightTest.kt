package com.violinjourney.app.feature.onboarding

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The picture of the setup (spec 3.36.8, 5.29 R8): 220 / 200 / 150 by the steps, no higher than 200 on a low or narrow screen, none below 520. */
class SetupArtHeightTest {

    @Test
    fun `the sun gives up room step by step on a tall phone`() {
        assertEquals(220.dp, SetupArtHeight.of(OnboardingStep.MICROPHONE, 892.dp, compact = false))
        assertEquals(200.dp, SetupArtHeight.of(OnboardingStep.REFERENCE_PITCH, 892.dp, compact = false))
        assertEquals(150.dp, SetupArtHeight.of(OnboardingStep.TOLERANCE, 892.dp, compact = false))
    }

    @Test
    fun `a window lower than 760 keeps it no higher than 200`() {
        assertEquals(200.dp, SetupArtHeight.of(OnboardingStep.MICROPHONE, 759.dp, compact = false))
        assertEquals(200.dp, SetupArtHeight.of(OnboardingStep.REFERENCE_PITCH, 616.dp, compact = false))
        assertEquals(150.dp, SetupArtHeight.of(OnboardingStep.TOLERANCE, 616.dp, compact = false), "lower than 200 stays as it is")
        assertEquals(220.dp, SetupArtHeight.of(OnboardingStep.MICROPHONE, 760.dp, compact = false), "760 itself is not lower")
    }

    @Test
    fun `a narrow phone keeps it no higher than 200 however tall`() {
        assertEquals(200.dp, SetupArtHeight.of(OnboardingStep.MICROPHONE, 892.dp, compact = true))
        assertEquals(150.dp, SetupArtHeight.of(OnboardingStep.TOLERANCE, 892.dp, compact = true))
    }

    @Test
    fun `below 520 there is no picture`() {
        OnboardingStep.setup.forEach { step ->
            assertEquals(0.dp, SetupArtHeight.of(step, 519.dp, compact = false), "$step")
            assertEquals(0.dp, SetupArtHeight.of(step, 308.dp, compact = true), "$step")
        }
        assertTrue(SetupArtHeight.of(OnboardingStep.MICROPHONE, 520.dp, compact = false) > 0.dp, "520 itself keeps it")
    }
}
