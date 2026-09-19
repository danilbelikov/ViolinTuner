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

// Fill tones of the practice calendar (handoff `practice.1`–`.4`); tones 3 and 4 are
// primaryContainer and primary. Never zone colors: the calendar is not a grade.
internal val PracticeFill1 = Color(0xFF2A2352)
internal val PracticeFill2 = Color(0xFF3D2F80)

internal val ZoneInTune = Color(0xFF47C97E)
internal val ZoneNear = Color(0xFFE5B03C)
internal val ZoneOff = Color(0xFFE8565C)
internal val OnZoneOff = Color(0xFFFFFFFF)
internal val ZoneNone = Color(0xFF3A3846)

// Dot of the Live status line (handoff `Live2.dc.html`, `status.*`): "may play" and "may not".
// Muted on purpose and never the zone colors: green and red on Live already mean in tune and off.
internal val StatusReady = Color(0xFF7CB99A)
internal val StatusBlocked = Color(0xFFC4877F)

internal val GradientInTuneStart = Color(0xFF1A4A36)
internal val GradientInTuneMid = Color(0xFF153A2C)
internal val GradientNearStart = Color(0xFF4A3A12)
internal val GradientNearMid = Color(0xFF3A2E12)
internal val GradientOffStart = Color(0xFF4A1C22)
internal val GradientOffMid = Color(0xFF3A181D)

// Progress (handoff `Прогресс.dc.html`, `tokens`). The level bar stays in the violet family;
// trophies bring the palette of the instrument: wood, ebony, silver, gold.
internal val TrophyLocked = Color(0xFF6B6880)
internal val TrophyWood = Color(0xFF9A5530)
internal val TrophyWoodLight = Color(0xFFC9814A)
internal val TrophyEbony = Color(0xFF2C2430)
internal val TrophyEbonyLight = Color(0xFF5A4E64)
internal val TrophyEbonyEdge = Color(0xFF7A6E86)
internal val TrophySilver = Color(0xFFB9B7C4)
internal val TrophyGold = Color(0xFFD9B65C)

/** Not zone.near: another tone, and only ever inside the rosin trophy. */
internal val TrophyRosin = Color(0xFFB8672A)
internal val TrophyRosinLight = Color(0xFFE3975A)
internal val TrophyRosinGlint = Color(0xFFFFF3E0)
internal val TrophyHair = Color(0xFFEDE6D3)
internal val TrophyPaper = Color(0xFFF1EEE6)
internal val TrophyInk = Color(0xFF8D8A96)
internal val TrophyCase = Color(0xFF2A2430)
internal val GiftGlow = Color(0x38C4ADFF)

// Repertoire (handoff `Репертуар.dc.html`, `tokens`). Sheet music is the one bright object of
// the feature: paper under a photo that is still loading, a film that keeps thumbnails from
// glaring, and a stand darker than the surface so that the page is all there is to look at.
internal val Paper = Color(0xFFECE8DF)
internal val PaperFrame = Color(0x1FFFFFFF)
internal val StandBackground = Color(0xFF0E0E12)
internal val StandScrim = Color(0xEB0E0E12)
internal val StatusRepertoireContainer = Color(0xFF1F3A2C)
internal val OnStatusRepertoireContainer = Color(0xFF9FE4BE)
internal val TakeNew = Color(0xFF2A2540)

/** The hex of zone.off, but a token of its own: zone colors stay about intonation only. */
internal val FormError = Color(0xFFE8565C)

// Actions that destroy something: the bin and its caption (handoff polish, `icon.error`). The hex of
// zone.off, but a token of its own: zone colours stay about intonation.
internal val Destructive = Color(0xFFE8565C)
