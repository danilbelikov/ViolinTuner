package com.violinjourney.app.core.ui.theme

import androidx.compose.ui.graphics.Color

// The raw colors of Live (docs/design, `tokens` and `gradients`). UI code reads them through
// LiveTheme.zoneColors, statusColors and venueColors, never directly.

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

// The controls of Live in the style of the room (handoff `Комната и залы.dc.html`, 29j, `ctrl.*`):
// the materials of the violin and of the room. None of them is the colour of a zone; the brass,
// the nearest to amber, only rims and pins, never by the ring.
internal val CtrlEbony = Color(0xFF1C1A20)
internal val CtrlEbonyEdge = Color(0xFF2E2A32)
internal val CtrlMaple = Color(0xFF8B5E3C)
internal val CtrlMapleLit = Color(0xFFA9763F)
internal val CtrlMapleDark = Color(0xFF4E3424)
internal val CtrlRuler = Color(0xFF6E4A34)
internal val CtrlBone = Color(0xFFF1EEE6)
internal val CtrlBoneShade = Color(0xFFD8D0BE)
internal val CtrlNickel = Color(0xFF6B6C78)
internal val CtrlBrass = Color(0xFFC9A24A)
internal val CtrlVelvet = Color(0xFF8E2F3F)
internal val CtrlInk = Color(0xFF2A2430)
internal val CtrlInkSoft = Color(0xFF6E635C)
internal val CtrlCaption = Color(0xFF8D8796)
internal val CtrlMuted = Color(0xFF6E687A)
internal val RecordingRim = Color(0xFFA8323F)
internal val VenuePlate = Color(0xB3131318)
