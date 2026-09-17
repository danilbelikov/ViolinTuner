package com.example.violintuner.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

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
}
