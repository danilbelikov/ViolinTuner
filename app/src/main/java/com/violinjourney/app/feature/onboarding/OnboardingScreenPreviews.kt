package com.violinjourney.app.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.violinjourney.app.core.domain.TolerancePreset
import com.violinjourney.app.core.domain.UserSettings
import com.violinjourney.app.core.ui.theme.ViolinTheme

// Handoff frames 5a–5c plus the tight cases.

@Composable
private fun OnboardingPreview(
    step: OnboardingStep,
    a4Hz: Int = 440,
    tolerance: TolerancePreset = TolerancePreset.INTERMEDIATE,
) {
    ViolinTheme {
        OnboardingScreen(
            state = OnboardingState(step, a4Hz, UserSettings.A4_OPTIONS_HZ, tolerance),
            onIntent = {},
        )
    }
}

@Preview(name = "1 · microphone", widthDp = 412, heightDp = 868)
@Composable
private fun MicrophonePreview() = OnboardingPreview(OnboardingStep.MICROPHONE)

@Preview(name = "2 · reference pitch, 442", widthDp = 412, heightDp = 868)
@Composable
private fun ReferencePitchPreview() = OnboardingPreview(OnboardingStep.REFERENCE_PITCH, a4Hz = 442)

@Preview(name = "3 · tolerance, beginner", widthDp = 412, heightDp = 868)
@Composable
private fun TolerancePreview() = OnboardingPreview(OnboardingStep.TOLERANCE, tolerance = TolerancePreset.BEGINNER)

@Preview(name = "3 · landscape", widthDp = 892, heightDp = 388)
@Composable
private fun ToleranceLandscapePreview() = OnboardingPreview(OnboardingStep.TOLERANCE)

@Preview(name = "3 · small phone, large font", widthDp = 320, heightDp = 544, fontScale = 1.5f)
@Composable
private fun ToleranceSmallLargeFontPreview() = OnboardingPreview(OnboardingStep.TOLERANCE, tolerance = TolerancePreset.PRO)
