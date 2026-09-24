package com.violinjourney.app.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

@Composable
fun ViolinTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalPracticeColors provides DarkPracticeColors,
        LocalProgressColors provides DarkProgressColors,
        LocalRepertoireColors provides DarkRepertoireColors,
        LocalSoundColors provides DarkSoundColors,
        LocalVideoColors provides DarkVideoColors,
        LocalBackupColors provides DarkBackupColors,
        LocalExerciseColors provides DarkExerciseColors,
    ) {
        ViolinBaseTheme(fontFamily = Manrope, content = content)
    }
}

object ViolinTheme {
    val zoneColors: ZoneColors
        @Composable @ReadOnlyComposable get() = LocalZoneColors.current

    val statusColors: StatusColors
        @Composable @ReadOnlyComposable get() = LocalStatusColors.current

    val liveTypography: LiveTypography
        @Composable @ReadOnlyComposable get() = LocalLiveTypography.current

    val practiceColors: PracticeColors
        @Composable @ReadOnlyComposable get() = LocalPracticeColors.current

    val progressColors: ProgressColors
        @Composable @ReadOnlyComposable get() = LocalProgressColors.current

    val repertoireColors: RepertoireColors
        @Composable @ReadOnlyComposable get() = LocalRepertoireColors.current

    val soundColors: SoundColors
        @Composable @ReadOnlyComposable get() = LocalSoundColors.current

    val videoColors: VideoColors
        @Composable @ReadOnlyComposable get() = LocalVideoColors.current

    val backupColors: BackupColors
        @Composable @ReadOnlyComposable get() = LocalBackupColors.current

    val exerciseColors: ExerciseColors
        @Composable @ReadOnlyComposable get() = LocalExerciseColors.current

    /** The controls of Live in the style of the room (spec 3.27). */
    val venueColors: VenueColors
        @Composable @ReadOnlyComposable get() = LocalVenueColors.current

    val accentGlow: Color
        get() = AccentGlow

    /** The dots of «Знакомство»: a page not in view (spec 3.33). */
    val onboardingDotIdle: Color
        get() = OnboardingDotIdle

    /** Bin icons and the captions of actions that delete (spec 3.16). */
    val destructive: Color
        get() = Destructive
}
