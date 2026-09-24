package com.violinjourney.app.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

// The base of the raw palette (docs/design, `tokens`). Public only because the theme files of the app
// build their colors from it too; UI code reads colors through MaterialTheme.colorScheme and
// ViolinTheme / LiveTheme, never these directly.

val Primary = Color(0xFFC4ADFF)
val OnPrimary = Color(0xFF2E1A6E)
val PrimaryContainer = Color(0xFF5B43B8)
val OnPrimaryContainer = Color(0xFFE9DDFF)
val Surface = Color(0xFF131318)
val SurfaceContainer = Color(0xFF1E1D25)
val SurfaceContainerHigh = Color(0xFF292833)
val OutlineVariant = Color(0xFF3A3846)
val OnSurface = Color(0xFFE6E4EE)
val OnSurfaceVariant = Color(0xFFA39FB5)

// v1 is dark-only (spec 3.6). Dynamic color is off: zone colors are tuned against this palette.
val ViolinColorScheme = darkColorScheme(
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
