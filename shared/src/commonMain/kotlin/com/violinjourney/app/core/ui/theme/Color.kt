package com.violinjourney.app.core.ui.theme

import androidx.compose.ui.graphics.Color

// Raw palette from the design handoff (docs/design, `tokens` and `gradients` tables).
// UI code reads these through MaterialTheme.colorScheme or ViolinTheme.zoneColors, never directly.
// The base of the palette and the colors of Live are shared with iOS: BasePalette.kt and
// LivePalette.kt in the shared module.

// Fill tones of the practice calendar (handoff `practice.1`–`.4`); tones 3 and 4 are
// primaryContainer and primary. Never zone colors: the calendar is not a grade.
internal val PracticeFill1 = Color(0xFF2A2352)
internal val PracticeFill2 = Color(0xFF3D2F80)

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
internal val TakeNew = Color(0xFF2A2540)

// The danger (spec 3.36.1, 5.29): what deletes or replaces data, a warning that data will be lost, the sign of a failure — a
// soft coral, 8.3 : 1 on a dialog or a menu. Never a zone colour and never colorScheme.error: the red of «мимо» it replaced
// meant «off» on Live. [OnDanger] is the text of the one filled dangerous button, «Восстановить» (9.2 : 1).
internal val DangerSoft = Color(0xFFFFB199)
internal val OnDanger = Color(0xFF3D1408)

// Smoked glass over pictures (spec 3.36.1, 5.29), without a blur on either platform: the plate at .72 (the .7 of 5.20 is gone),
// the strong one at .82 only over a busy picture — the preview of the own camera, the city tag on a tile of the shop.
internal val Glass = Color(0xB8131318)
internal val GlassStrong = Color(0xD1131318)

// The second level of text on the glass (spec 3.36.6, 5.29 R6): «выбрать», «занятие», the hertz of a string, the word of the
// switcher not chosen — 6.8 : 1 over a dark picture, 4.4 : 1 on the glass over pure white (the light hall is checked by a
// screenshot). Never the grey of the second level off the glass, which is 2.9 : 1 there. [GlassEdge] — the inner edge of 1 dp of
// the glass of Live's controls, white at 12 % (the mockup's 8–12 %, brought to one).
internal val GlassCaption = Color(0xFFC9C5D6)
internal val GlassEdge = Color(0x1FFFFFFF)

/** Black at .55 under a sheet (spec 5.29); it covers the status bar too. */
internal val SheetScrim = Color(0x8C000000)

// The sign of recording (spec 3.36.1, 5.29): the dot and the key of a take. The hex of zone.off, but a token of its own — a
// recording is no zone, and the zones stay about intonation. [OnRecording] is the stop square on the red key.
internal val Recording = Color(0xFFE8565C)
internal val OnRecording = Color(0xFFFFFFFF)

// «Видео с нотами» (spec 3.37, 5.30): the ink of a note's name on its capsule and the veil the summary lies on — near black, so
// the name reads on any zone colour (9 : 1 on green, 5.4 : 1 on red) and the last frame shows through the veil only faintly.
internal val OverlayInk = Color(0xFF0B0B10)

// The living practice screen (handoff polish, `tokens`).
// Brighter than the handoff (#E9DDFF at .35): over the light end of the fill that one could not be seen.
internal val LevelShine = Color(0xCCF7F2FF) // near-white lilac at .8
internal val TimerRing = Color(0x80C4ADFF) // primary at .5
internal val FlameOuter = Color(0xFFF28C3B)
internal val FlameCore = Color(0xFFFFE29A)
internal val FlameHot = Color(0xFFFFF6DC)

// The living «Начать занятие» (spec 3.16, 5.10; the prototype start-button.html): lights drifting over
// primary as light plays on mother-of-pearl, back to front, and the lilac glow under the button.
// None is a zone colour, and onPrimary reads over every one of them.
internal val StartLightPeriwinkle = Color(0xFFA6B5FF)
internal val StartLightRose = Color(0xFFEBB6F4)
internal val StartLightPearl = Color(0xFFF4EEFF)
internal val StartGlow = Color(0xFFB79BFF)

// The «Звук» screen (handoff sound, `tokens`). Not zone colors on purpose.
internal val MeterLevel = Color(0xFF6FA0C4)
internal val MeterReduce = Color(0xFF8FD0C9)
internal val MeterLimit = Color(0xFFF07A5A)
internal val EqFill = Color(0x1FC4ADFF) // primary at .12

internal val VideoField = Color(0xFF0E0E12) // = StandBackground
internal val VideoScrim = Color(0x73000000) // black at .45

internal val BackupData = Color(0xFFE9DDFF)
internal val BackupSheets = Color(0xFFB7ACD9)
internal val BackupAudio = Color(0xFF8A78C9)
internal val BackupVideo = Color(0xFF5B43B8)

// The kinds of events (spec 3.36.9, 5.29 R9; events-kinds.html, 1): eight colours in two tiers of lightness — the saturated four
// over the light four — none of them a zone, a violet of the time of practice, the orange of the flame, the brass of «сделано»
// or the coral of danger (the nearest to it is Пудра, ΔE2000 16.9). Each has the colour of its mark on the ground and of a sign
// and a caption on its plate of 18 %: the saturated four a tone lighter there, the light four the colour itself.
internal val KindBlue = Color(0xFF4D99E0)
internal val KindBlueOnPlate = Color(0xFF58A4EC)
internal val KindSea = Color(0xFF5E9D8E)
internal val KindSeaOnPlate = Color(0xFF6CAB9C)
internal val KindRose = Color(0xFFF266A5)
internal val KindRoseOnPlate = Color(0xFFFB6EAD)
internal val KindOrchid = Color(0xFFCF67E3)
internal val KindOrchidOnPlate = Color(0xFFDC74F1)
internal val KindIce = Color(0xFFA8DBFA)
internal val KindTurquoise = Color(0xFF5FCFDA)
internal val KindPowder = Color(0xFFFEB5CC)
internal val KindLime = Color(0xFFD7F092)

// Sections of the repertoire and the notation of scales (handoff `Упражнения`, `tokens`)
internal val LearnReading = Color(0xFF4A3F6E)
internal val LearnLearning = Color(0xFF8B74D9)
internal val InkOnDark = Color(0xFFDDD9E8)
internal val InkOnPaper = Color(0xFF2A2724)

