package com.violinjourney.app.feature.live.components

import androidx.compose.ui.unit.dp
import com.violinjourney.app.feature.live.LiveLayoutMath
import com.violinjourney.app.feature.live.LiveLayoutMath.PortraitRows
import com.violinjourney.app.feature.live.LiveSwitches

/**
 * Sizes of the Live screen from the handoff `sizes` table (base screen 412 × 892 dp) and spec 5.29 R6. The heights of the rows
 * around the ring are [PortraitRows]: the model of the ring and the screen share them.
 */
object LiveDimens {
    val ScreenPadding = 24.dp

    // The top row (spec 5.29 R6): the switcher in the middle, the gear at the right edge; the touch of both is the whole row
    val SwitcherTopPadding = PortraitRows.AIR.dp
    val TopRowHeight = PortraitRows.TOP_ROW.dp

    // The switcher «Игра | Настройка»: a capsule of glass with a bone slider under the chosen word (spec 3.36.6)
    val SwitcherHeight = 44.dp
    val SwitcherCorner = 22.dp
    val SwitcherInset = 4.dp
    val SwitcherSliderHeight = 36.dp
    val SwitcherSliderCorner = 18.dp

    /** A segment is not narrower than this where the row allows it (spec 5.29 R6)… */
    val SwitcherSegmentMin = 106.dp

    /** …and holds its word with this much on either side, the word stepping down to 12 sp before the padding does. */
    val SwitcherSegmentPadding = 16.dp
    val SwitcherSegmentPaddingMin = 8.dp

    /** The gear in the row of the switcher, at its right edge: a disc of the same glass. */
    val GearSize = 40.dp
    val GearTouch = 48.dp
    val GearIcon = 20.dp

    /** From the right edge of the screen to the disc (portrait); its touch reaches 4 closer. */
    val GearEnd = 16.dp

    // The strings of «Настройка»: four buttons of glass across the row (spec 3.36.6, 5.29 R6)
    val StringRowTopPadding = PortraitRows.AIR.dp
    val StringButtonHeight = PortraitRows.STRING_BUTTON.dp
    val StringButtonCorner = 16.dp
    val StringButtonGap = 10.dp

    /** From the sides of the screen to the row in portrait. */
    val StringRowSide = 22.dp

    /** The bone edge inside the nearest string. */
    val StringNearEdge = 2.dp

    /** The lock of the locked string, in its top right corner. */
    val StringLock = 14.dp
    val StringLockTop = 5.dp
    val StringLockEnd = 6.dp

    val RingMargin = 16.dp
    val RingStroke = 6.dp
    val WaveStroke = 2.dp
    val OctaveStartPadding = 4.dp
    val IndicatorSpacing = PortraitRows.WORD_GAP.dp
    val IndicatorSpacingCompact = 10.dp

    // The status line above the ring (spec 3.14, 5.29 R6): on a plate of glass over the picture
    val StatusLineHeight = PortraitRows.STATUS_PLATE.dp
    val StatusLineTopPadding = PortraitRows.AIR.dp

    /** «Можно» — a filled dot; «нельзя» — a ring with its stroke (spec 5.29 R6). */
    val StatusDotReady = 9.dp
    val StatusDotBlocked = 10.dp
    val StatusLineDotStroke = 2.dp
    val StatusLineGap = 9.dp

    /** The plate of smoked glass under the status line over the picture: a capsule without an edge. */
    val StatusPlateHeight = PortraitRows.STATUS_PLATE.dp
    val StatusPlateCorner = 18.dp
    val StatusPlatePadding = 15.dp

    // Status word with the cents beside it (handoff 12c1)
    val StatusRowHeight = PortraitRows.WORD_ROW.dp
    val StatusArrowSize = 40.dp
    val StatusDotSize = 18.dp
    val StatusGap = 14.dp
    val StatusArrowSizeCompact = 32.dp
    val StatusDotSizeCompact = 16.dp
    val StatusGapCompact = 12.dp

    /** A portrait ring smaller than this means a small screen: word and cents go compact with it. */
    val CompactStatusBelowRing = 250.dp

    // The scale of «Настройка» (spec 5.29 R6): a light line, the green pill of the tolerance, the zero tick; under the word
    val ScaleHeight = PortraitRows.SCALE_ROW.dp

    /** Under the word and the cents; without the permission — over the scale at the bottom of the place, under the card. */
    val ScaleTopGap = PortraitRows.SCALE_GAP.dp
    val ScaleLine = 2.dp
    val ScalePillHeight = 10.dp
    val ScaleTickWidth = 2.dp
    val ScaleTickHeight = 14.dp
    val MarkerWidth = 8.dp
    val MarkerHeight = 24.dp
    val HaloWidth = 44.dp
    val HaloHeight = 24.dp
    val HaloFeather = 4.dp

    // The bottom row (spec 3.36.6, 5.29 R6): «Что играю» · the record key · the practice tag, 76 high with 12 above and below
    val KeyRowPadding = PortraitRows.AIR.dp
    val KeyRowSide = LiveLayoutMath.KEY_ROW_SIDE.dp
    val CardToKey = LiveLayoutMath.CARD_TO_KEY.dp

    // The record key: bone with a soft highlight in the dark rim of KeyRim, a velvet dot; red with a white stop while it records
    val RecordKeySize = PortraitRows.KEY_ROW.dp
    val RecordKeyRim = 3.dp
    val RecordKeyDot = 28.dp
    val RecordKeyStop = 24.dp
    val RecordKeyStopCorner = 6.dp

    /** The key goes down by this under a finger. */
    val RecordKeyTravel = 3.dp

    /** Its soft shadow (black at [KEY_SHADOW_ALPHA]): set down by this, blurred as CSS blurs 14 — a sigma of 7; it takes no room. */
    val RecordKeyShadowDrop = 7.dp
    val RecordKeyShadowSigma = 7.dp

    // The two cards of the bottom row (spec 5.29 R6): glass — something to choose or start; paper — something runs
    val CardHeight = 60.dp
    val CardCorner = 18.dp
    val CardPadding = 10.dp
    val CardGap = 7.dp
    val CardIcon = 20.dp

    /** The velvet dot of a running practice, in the place of the icon. */
    val CardDot = 9.dp

    /** The paper's soft shadow: set down by 4, blurred as CSS blurs 10 — a sigma of 5. */
    val CardShadowDrop = 4.dp
    val CardShadowSigma = 5.dp

    /** The brass line of a running block: [CardBarSide] from the sides, [CardBarBottom] over the bottom edge. */
    val CardBar = 3.dp
    val CardBarCorner = 2.dp
    val CardBarSide = 12.dp
    val CardBarBottom = 7.dp

    /** The words keep this much over the brass line. */
    val CardBarGap = 2.dp

    /** «готово»: the brass rim outside the paper. */
    val CardRim = 2.5.dp

    // The strip of a take (spec 3.36.6, 5.29 R6): its own capsule of glass over the bottom row
    val RecordingStripTopPadding = PortraitRows.RECORDING_GAP.dp
    val RecordingStripHeight = PortraitRows.RECORDING_STRIP.dp
    val RecordingStripCorner = 25.dp

    /** From the sides of the screen (upright; in landscape the strip spans its column). */
    val RecordingStripSide = 22.dp
    val RecordingStripPadding = 16.dp
    val RecordingStripGap = 10.dp
    val RecordingDotSize = 11.dp

    /** The halo round the pulsing dot, [RECORDING_HALO_ALPHA] of the red of recording. */
    val RecordingDotHalo = 4.dp
    val RecordingBarHeight = 8.dp
    val RecordingBarCorner = 2.dp
    val RecordingBarGap = 2.dp

    // Landscape (handoff v1-land): ring panel on the left, controls on the right
    // 400, not the 440 of v1: the ring is 260 here and its halo (380) fits the panel.
    const val LANDSCAPE_RING_PANEL_FRACTION = 400f / 892f
    val LandscapePaddingStart = 8.dp
    val LandscapePaddingEnd = 24.dp
    /** Below this height the landscape status word and cents go compact. */
    val LandscapeStatusCompactHeight = 48.dp
    val LandscapeRecordTopPadding = LiveLayoutMath.LANDSCAPE_KEYS_AIR.dp
    val LandscapeStatusMinHeight = 40.dp

    // The card «нет разрешения» in the place of the ring (spec 3.36.6, 5.29 R6): paper on a deep soft shadow
    /** Upright: 26 from the sides of the screen, not wider than 360; lying down: in the middle of the ring's panel, not wider than 340. */
    val PromptSide = 26.dp
    val PromptMaxWidth = 360.dp
    val PromptMaxWidthLandscape = 340.dp

    /** Lying down the card keeps the ring's margin from the edges of its panel. */
    val PromptPanelMargin = RingMargin
    val PromptCorner = 24.dp
    val PromptPaddingTop = 22.dp
    val PromptPaddingSide = 20.dp
    val PromptPaddingBottom = 18.dp

    /** Its shadow (black at [PROMPT_SHADOW_ALPHA]): set down by 18, blurred as CSS blurs 40 — a sigma of 20. */
    val PromptShadowDrop = 18.dp
    val PromptShadowSigma = 20.dp
    val PromptIconPlate = 48.dp
    val PromptIconPlateCorner = 16.dp
    val PromptIcon = 24.dp
    val PromptIconGap = 12.dp
    val PromptTitleGap = 6.dp
    val PromptButtonHeight = 54.dp
    val PromptButtonTop = 18.dp
    val PromptButtonIcon = 18.dp
    val PromptButtonGap = 8.dp

    /** The word of «Разрешить доступ» keeps this much off the round ends of its capsule. */
    val PromptButtonPadding = 16.dp

    // Opacity of dimmed parts (handoff states 8d–8f)
    const val SCALE_ALPHA_IDLE = 0.45f
    const val SCALE_ALPHA_NO_MIC = 0.3f
    const val CHROME_ALPHA_NO_MIC = 0.4f

    /** Controls that do nothing right now: record outside play mode, the switcher while recording. */
    const val DISABLED_ALPHA = 0.4f

    // The soft shadows of the bottom row and of the card «нет разрешения» (spec 5.29 R6), baked once per size
    const val KEY_SHADOW_ALPHA = 0.55f
    const val CARD_SHADOW_ALPHA = 0.35f
    const val PROMPT_SHADOW_ALPHA = 0.5f

    /** The highlight of the bone key: at 50 % across and 38 % down, this much of the way from bone to white… */
    const val KEY_HIGHLIGHT = 0.6f
    const val KEY_HIGHLIGHT_X = 0.5f
    const val KEY_HIGHLIGHT_Y = 0.38f

    /** …bone at 55 % of its reach, the shade of bone at the far edge (live.html, `.key`). */
    const val KEY_HIGHLIGHT_BONE_STOP = 0.55f

    const val RECORDING_HALO_ALPHA = 0.25f
    const val HALO_ALPHA_CORE = 0.2f
    const val HALO_ALPHA_FEATHER = 0.1f

    /** The note of the locked string, pale in the ring in silence (spec 5.29 R6, behind [LiveSwitches.STRING_SILHOUETTE]). */
    const val SILHOUETTE_ALPHA = 0.28f

    // The scale of «Настройка»: its line and zero tick in onSurface, its pill in the green of the tolerance (spec 5.29 R6)
    const val SCALE_LINE_ALPHA = 0.35f
    const val SCALE_PILL_ALPHA = 0.55f

    /** The ground of the nearest string: bone over the glass (spec 5.29 R6). */
    const val STRING_NEAR_GROUND_ALPHA = 0.16f
}
