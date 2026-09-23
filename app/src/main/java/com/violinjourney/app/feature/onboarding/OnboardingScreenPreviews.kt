package com.violinjourney.app.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview
import com.violinjourney.app.core.domain.TolerancePreset
import com.violinjourney.app.core.domain.UserSettings
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.ViolinTheme

// Handoff series 36: the four pages of the introduction and the three steps of the setup, plus the
// tight cases (36g). Previews are still frames: motion is removed, as «убрать анимации» shows them.

@Composable
private fun OnboardingPreview(
    step: OnboardingStep,
    a4Hz: Int = 440,
    tolerance: TolerancePreset = TolerancePreset.INTERMEDIATE,
) {
    ViolinTheme {
        CompositionLocalProvider(LocalReduceMotion provides true) {
            OnboardingScreen(
                state = OnboardingState(step, a4Hz, UserSettings.A4_OPTIONS_HZ, tolerance),
                onIntent = {},
                onHaveBackup = {},
            )
        }
    }
}

@Preview(name = "36a · welcome", widthDp = 412, heightDp = 868)
@Composable
private fun WelcomePreview() = OnboardingPreview(OnboardingStep.WELCOME)

@Preview(name = "36b · Live on the stand", widthDp = 412, heightDp = 868)
@Composable
private fun LivePreview() = OnboardingPreview(OnboardingStep.LIVE)

@Preview(name = "36c · practice, recordings, road", widthDp = 412, heightDp = 868)
@Composable
private fun JourneyPreview() = OnboardingPreview(OnboardingStep.JOURNEY)

@Preview(name = "36d · the data stay on the phone", widthDp = 412, heightDp = 868)
@Composable
private fun DataPreview() = OnboardingPreview(OnboardingStep.DATA)

@Preview(name = "36e2 · microphone", widthDp = 412, heightDp = 868)
@Composable
private fun MicrophonePreview() = OnboardingPreview(OnboardingStep.MICROPHONE)

@Preview(name = "36e3 · reference pitch, 442", widthDp = 412, heightDp = 868)
@Composable
private fun ReferencePitchPreview() = OnboardingPreview(OnboardingStep.REFERENCE_PITCH, a4Hz = 442)

@Preview(name = "36e4 · tolerance, beginner", widthDp = 412, heightDp = 868)
@Composable
private fun TolerancePreview() = OnboardingPreview(OnboardingStep.TOLERANCE, tolerance = TolerancePreset.BEGINNER)

@Preview(name = "36g1 · welcome, landscape", widthDp = 892, heightDp = 388)
@Composable
private fun WelcomeLandscapePreview() = OnboardingPreview(OnboardingStep.WELCOME)

@Preview(name = "36g2 · data, landscape", widthDp = 892, heightDp = 388)
@Composable
private fun DataLandscapePreview() = OnboardingPreview(OnboardingStep.DATA)

@Preview(name = "tolerance, landscape", widthDp = 892, heightDp = 388)
@Composable
private fun ToleranceLandscapePreview() = OnboardingPreview(OnboardingStep.TOLERANCE)

@Preview(name = "36g3 · road, 360 × 640", widthDp = 360, heightDp = 616)
@Composable
private fun JourneySmallPreview() = OnboardingPreview(OnboardingStep.JOURNEY)

@Preview(name = "36g4 · data in German", widthDp = 412, heightDp = 868, locale = "de")
@Composable
private fun DataGermanPreview() = OnboardingPreview(OnboardingStep.DATA)

@Preview(name = "tolerance, small phone, large font", widthDp = 320, heightDp = 544, fontScale = 1.5f)
@Composable
private fun ToleranceSmallLargeFontPreview() = OnboardingPreview(OnboardingStep.TOLERANCE, tolerance = TolerancePreset.PRO)
