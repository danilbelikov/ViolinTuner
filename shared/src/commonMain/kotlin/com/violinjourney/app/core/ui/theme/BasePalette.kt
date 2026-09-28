package com.violinjourney.app.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

// The base of the raw palette (docs/design, `tokens`); internal: the colors of every screen are built from it here, and
// UI code reads colors through MaterialTheme.colorScheme and ViolinTheme / LiveTheme, never these directly.

internal val Primary = Color(0xFFC4ADFF)
internal val OnPrimary = Color(0xFF2E1A6E)
internal val PrimaryContainer = Color(0xFF5B43B8)
internal val OnPrimaryContainer = Color(0xFFE9DDFF)
internal val Surface = Color(0xFF131318)
internal val SurfaceContainer = Color(0xFF1E1D25)
internal val SurfaceContainerHigh = Color(0xFF292833)
internal val OutlineVariant = Color(0xFF3A3846)
internal val OnSurface = Color(0xFFE6E4EE)
internal val OnSurfaceVariant = Color(0xFFA39FB5)

/**
 * The third level of text (spec 5.29): day labels, chevrons, the figures in chips, a placeholder. Only on [Surface] (5.3 : 1)
 * and [SurfaceContainer] (4.7 : 1) — on [SurfaceContainerHigh] it falls to 4.1 : 1, under the 4.5 of every text.
 */
internal val TextTertiary = Color(0xFF8A8699)

/**
 * The soft accent (spec 5.29): the ground of a chosen option, the plate of an icon. The hex of `PracticeFill1`, but not the
 * same thing — the calendar keeps its own token.
 */
internal val AccentSoft = Color(0xFF2A2352)

// v1 is dark-only (spec 3.6). Dynamic color is off: zone colors are tuned against this palette.
internal val ViolinColorScheme = darkColorScheme(
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
