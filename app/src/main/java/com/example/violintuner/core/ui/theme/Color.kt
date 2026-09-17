package com.example.violintuner.core.ui.theme

import androidx.compose.ui.graphics.Color

// Raw palette from the design handoff (docs/design, `tokens` and `gradients` tables).
// UI code reads these through MaterialTheme.colorScheme or ViolinTheme.zoneColors, never directly.

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

/** Center of the soft glow behind onboarding illustrations (handoff 5a-5c). */
internal val AccentGlow = Color(0xFF2A2352)

internal val ZoneInTune = Color(0xFF47C97E)
internal val ZoneNear = Color(0xFFE5B03C)
internal val ZoneOff = Color(0xFFE8565C)
internal val ZoneNone = Color(0xFF3A3846)
internal val RingTrack = Color(0x1AFFFFFF)

internal val GradientInTuneStart = Color(0xFF1A4A36)
internal val GradientInTuneMid = Color(0xFF153A2C)
internal val GradientNearStart = Color(0xFF4A3A12)
internal val GradientNearMid = Color(0xFF3A2E12)
internal val GradientOffStart = Color(0xFF4A1C22)
internal val GradientOffMid = Color(0xFF3A181D)
