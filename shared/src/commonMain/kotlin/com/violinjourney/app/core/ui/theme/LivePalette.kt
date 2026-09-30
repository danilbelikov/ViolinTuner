package com.violinjourney.app.core.ui.theme

import androidx.compose.ui.graphics.Color

// The raw colors of Live (docs/design, `tokens` and `gradients`). UI code reads them through
// LiveTheme.zoneColors, statusColors and venueColors, never directly.

internal val ZoneInTune = Color(0xFF47C97E)
internal val ZoneNear = Color(0xFFE5B03C)
internal val ZoneOff = Color(0xFFE8565C)
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

// The controls of Live (spec 3.36.6, 5.29 R6): smoked glass (Color.kt) and bone — the maple, the ebony, the nickel and the
// wooden ruler of 3.27 are gone: warm wood at the top of the screen read as the amber «рядом» to the corner of the eye. None
// of these is the colour of a zone; the brass, the nearest to amber, is «сделано» and rims only, never by the ring.
internal val CtrlBone = Color(0xFFF1EEE6)
internal val CtrlBoneShade = Color(0xFFD8D0BE)
internal val CtrlBrass = Color(0xFFC9A24A)
internal val CtrlVelvet = Color(0xFF8E2F3F)
internal val CtrlInk = Color(0xFF2A2430)
internal val CtrlInkSoft = Color(0xFF6E635C)
internal val RecordingRim = Color(0xFFA8323F)

/** The dark rim of the record key (spec 5.29 R6): darker and duller than the brass of «сделано», so the two never argue. */
internal val KeyRim = Color(0xFF9C8452)
