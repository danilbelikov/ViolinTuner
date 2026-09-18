package com.example.violintuner.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// v1 is dark-only (spec 3.6). Dynamic color is off: zone colors are tuned against this palette.
private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    background = Surface,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,
    outlineVariant = OutlineVariant,
)

@Composable
fun ViolinTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalZoneColors provides DarkZoneColors,
        LocalLiveTypography provides DefaultLiveTypography,
        LocalPracticeColors provides DarkPracticeColors,
        LocalProgressColors provides DarkProgressColors,
    ) {
        MaterialTheme(
            colorScheme = DarkColorScheme,
            typography = ViolinTypography,
            content = content,
        )
    }
}

object ViolinTheme {
    val zoneColors: ZoneColors
        @Composable @ReadOnlyComposable get() = LocalZoneColors.current

    val liveTypography: LiveTypography
        @Composable @ReadOnlyComposable get() = LocalLiveTypography.current

    val practiceColors: PracticeColors
        @Composable @ReadOnlyComposable get() = LocalPracticeColors.current

    val progressColors: ProgressColors
        @Composable @ReadOnlyComposable get() = LocalProgressColors.current

    val accentGlow: Color
        get() = AccentGlow
}
