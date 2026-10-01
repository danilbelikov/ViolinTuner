package com.violinjourney.app.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview
import com.violinjourney.app.core.domain.TolerancePreset
import com.violinjourney.app.core.domain.UserSettings
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.ViolinTheme

// Handoff series 36 and the start of the redesign (spec 3.36.8, start.html 1–2): the four pages of the introduction and the three
// steps of the setup under one strip of the way, plus the tight cases (36g). Previews are still frames: motion is removed, as
// «убрать анимации» shows them. 640 × 360 lying is the 603 × 308 the emulator leaves the app behind its cutout and bars.

@Composable
private fun OnboardingPreview(
    step: OnboardingStep,
    a4Hz: Int = 440,
    tolerance: TolerancePreset = TolerancePreset.INTERMEDIATE,
    micAllowed: Boolean = false,
) {
    ViolinTheme {
        CompositionLocalProvider(LocalReduceMotion provides true) {
            OnboardingScreen(
                state = OnboardingState(step, a4Hz, UserSettings.A4_OPTIONS_HZ, tolerance),
                onIntent = {},
                onHaveBackup = {},
                micAllowed = micAllowed,
            )
        }
    }
}

@Preview(name = "36a · welcome", widthDp = 412, heightDp = 868)
@Composable
private fun WelcomePreview() = OnboardingPreview(OnboardingStep.WELCOME)

@Preview(name = "36a · welcome, de: «EINFÜHRUNG · 1 VON 7»", widthDp = 412, heightDp = 868, locale = "de")
@Composable
private fun WelcomeGermanPreview() = OnboardingPreview(OnboardingStep.WELCOME)

@Preview(name = "36b · Live on the stand: «Пока играете…» over the button", widthDp = 412, heightDp = 868)
@Composable
private fun LivePreview() = OnboardingPreview(OnboardingStep.LIVE)

@Preview(name = "36c · practice, recordings, road: the icons on plates", widthDp = 412, heightDp = 868)
@Composable
private fun JourneyPreview() = OnboardingPreview(OnboardingStep.JOURNEY)

@Preview(name = "36d · the data stay on the phone: the copy on the accent, the chart", widthDp = 412, heightDp = 868)
@Composable
private fun DataPreview() = OnboardingPreview(OnboardingStep.DATA)

@Preview(name = "36e2 · microphone: the hint, the icon on the button", widthDp = 412, heightDp = 868)
@Composable
private fun MicrophonePreview() = OnboardingPreview(OnboardingStep.MICROPHONE)

@Preview(name = "36e2 · microphone, allowed: no hint", widthDp = 412, heightDp = 868)
@Composable
private fun MicrophoneAllowedPreview() = OnboardingPreview(OnboardingStep.MICROPHONE, micAllowed = true)

@Preview(name = "36e3 · reference pitch, 442: «Гц» under the numbers, the hint", widthDp = 412, heightDp = 868)
@Composable
private fun ReferencePitchPreview() = OnboardingPreview(OnboardingStep.REFERENCE_PITCH, a4Hz = 442)

@Preview(name = "36e4 · tolerance, beginner: the bars beside the words", widthDp = 412, heightDp = 868)
@Composable
private fun TolerancePreview() = OnboardingPreview(OnboardingStep.TOLERANCE, tolerance = TolerancePreset.BEGINNER)

@Preview(name = "36g1 · welcome, landscape", widthDp = 892, heightDp = 388)
@Composable
private fun WelcomeLandscapePreview() = OnboardingPreview(OnboardingStep.WELCOME)

@Preview(name = "36g2 · data, landscape", widthDp = 892, heightDp = 388)
@Composable
private fun DataLandscapePreview() = OnboardingPreview(OnboardingStep.DATA)

@Preview(name = "live, landscape: «Пока играете…» at the button, not under the words", widthDp = 892, heightDp = 388)
@Composable
private fun LiveLandscapePreview() = OnboardingPreview(OnboardingStep.LIVE)

@Preview(name = "tolerance, landscape", widthDp = 892, heightDp = 388)
@Composable
private fun ToleranceLandscapePreview() = OnboardingPreview(OnboardingStep.TOLERANCE)

@Preview(name = "welcome, landscape 640 × 360: «У меня есть копия данных» on two lines beside «Начать»", widthDp = 603, heightDp = 308)
@Composable
private fun WelcomeLowLandscapePreview() = OnboardingPreview(OnboardingStep.WELCOME)

@Preview(name = "reference pitch, landscape 640 × 360: the buttons right under the title, its words under them", widthDp = 603, heightDp = 308)
@Composable
private fun ReferenceLowLandscapePreview() = OnboardingPreview(OnboardingStep.REFERENCE_PITCH)

@Preview(name = "microphone, landscape 640 × 360: the hint over the button, the words scroll over it", widthDp = 603, heightDp = 308)
@Composable
private fun MicrophoneLowLandscapePreview() = OnboardingPreview(OnboardingStep.MICROPHONE)

@Preview(name = "welcome, landscape 640 × 360, font 1.5: «ЗНАКОМСТВО» smaller, whole, the title 12 under it", widthDp = 603, heightDp = 308, fontScale = 1.5f)
@Composable
private fun WelcomeLowLandscapeLargeFontPreview() = OnboardingPreview(OnboardingStep.WELCOME)

@Preview(name = "welcome, landscape 640 × 360, de, font 2: the strip under «Überspringen»", widthDp = 603, heightDp = 308, fontScale = 2f, locale = "de")
@Composable
private fun WelcomeLowLandscapeLargestFontPreview() = OnboardingPreview(OnboardingStep.WELCOME)

@Preview(name = "tolerance, landscape 640 × 360: the bars under the captions", widthDp = 603, heightDp = 308)
@Composable
private fun ToleranceLowLandscapePreview() = OnboardingPreview(OnboardingStep.TOLERANCE)

@Preview(name = "36g3 · road, 360 × 640", widthDp = 360, heightDp = 616)
@Composable
private fun JourneySmallPreview() = OnboardingPreview(OnboardingStep.JOURNEY)

@Preview(name = "36g4 · data in German", widthDp = 412, heightDp = 868, locale = "de")
@Composable
private fun DataGermanPreview() = OnboardingPreview(OnboardingStep.DATA)

@Preview(name = "tolerance, fr 360 × 640: «Intermédiaire» puts the bars under the captions", widthDp = 360, heightDp = 616, locale = "fr")
@Composable
private fun ToleranceFrenchPreview() = OnboardingPreview(OnboardingStep.TOLERANCE)

@Preview(name = "reference pitch, 360 × 640, font 1.3: «Гц» under the number, the buttons taller", widthDp = 360, heightDp = 616, fontScale = 1.3f)
@Composable
private fun ReferenceLargeFontPreview() = OnboardingPreview(OnboardingStep.REFERENCE_PITCH)

@Preview(name = "tolerance, small phone, large font", widthDp = 320, heightDp = 544, fontScale = 1.5f)
@Composable
private fun ToleranceSmallLargeFontPreview() = OnboardingPreview(OnboardingStep.TOLERANCE, tolerance = TolerancePreset.PRO)

@Preview(name = "tolerance, fr 320 × 492, font 1.5: «±12 cts» beside the bars, «Intermédiaire» whole at 17 sp", widthDp = 320, heightDp = 492, fontScale = 1.5f, locale = "fr")
@Composable
private fun ToleranceSmallFrenchPreview() = OnboardingPreview(OnboardingStep.TOLERANCE)

@Preview(name = "road, 320 × 492: lower than 520 the picture keeps the corner of «Пропустить», the strip under it", widthDp = 320, heightDp = 492)
@Composable
private fun JourneyLowPreview() = OnboardingPreview(OnboardingStep.JOURNEY)
