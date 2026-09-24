package com.violinjourney.app.feature.live.components

import androidx.compose.ui.unit.dp

/** Sizes of the Live screen from the handoff `sizes` table (base screen 412 × 892 dp). */
object LiveDimens {
    val ScreenPadding = 24.dp

    val SwitcherTopPadding = 12.dp
    val SwitcherHeight = 40.dp
    // The maple plank with its bone slider (handoff venue 29j)
    val SwitcherCorner = 21.dp
    val SwitcherInset = 3.dp
    val SwitcherSliderCorner = 18.dp
    val SwitcherSegmentPadding = 20.dp
    val SwitcherBorder = 1.dp
    val SwitcherIconSize = 14.dp
    val SwitcherIconStroke = 2.5.dp
    val SwitcherTuningIconWidth = 4.dp
    val SwitcherIconGap = 8.dp

    /** The gear in the row of the switcher, at its right edge (handoff nav_bar 35): a disc of smoked glass. */
    val GearSize = 36.dp
    val GearTouch = 48.dp
    val GearIcon = 20.dp
    val GearEnd = 16.dp
    val GearBorder = 1.dp

    /** The practice tag to the right of the record key (handoff nav_bar 35): the bookmark's size, turned round. */
    val PracticeTagMinWidth = 124.dp
    val PracticeTagTextStart = 25.dp
    val PracticeTagTextEnd = 10.dp
    val PracticeTagIcon = 18.dp
    val PracticeTagIconGap = 8.dp
    val PracticeTagDot = 7.dp
    val PracticeTagDotGap = 6.dp

    val StringRowTopPadding = 12.dp
    val StringButtonWidth = 76.dp
    val StringButtonHeight = 64.dp
    // A peg: its head stands out over the top edge (handoff venue 29j)
    val StringButtonCorner = 12.dp
    val StringButtonGap = 14.dp
    val StringButtonEdge = 1.5.dp
    val StringPegHeadWidth = 26.dp
    val StringPegHeadHeight = 9.dp
    val StringPegHeadRise = 5.dp
    val StringLockBadgeSize = 22.dp
    val StringLockBadgeOffset = 7.dp
    val StringLockIconSize = 12.dp
    val StringHintTopPadding = 10.dp

    val RingMargin = 16.dp
    val RingStroke = 6.dp
    val WaveStroke = 2.dp
    val OctaveStartPadding = 4.dp
    val IndicatorSpacing = 20.dp
    val IndicatorSpacingCompact = 10.dp

    // Status line above the ring (handoff 12a, 12b)
    val StatusLineHeight = 28.dp
    val StatusLineTopPadding = 12.dp
    val StatusLineDot = 8.dp
    val StatusLineDotStroke = 2.dp
    val StatusLineGap = 8.dp
    /** The plate of smoked glass under the status line over the picture (handoff venue `venue.plate`). */
    val StatusPlateHeight = 26.dp
    val StatusPlateCorner = 13.dp
    val StatusPlatePadding = 13.dp

    // Status word with the cents beside it (handoff 12c1)
    val StatusRowHeight = 48.dp
    val StatusArrowSize = 40.dp
    val StatusDotSize = 18.dp
    val StatusGap = 14.dp
    val StatusArrowSizeCompact = 32.dp
    val StatusDotSizeCompact = 16.dp
    val StatusGapCompact = 12.dp

    /** A portrait ring smaller than this means a small screen: word and cents go compact with it. */
    val CompactStatusBelowRing = 250.dp

    val ScaleHeight = 36.dp
    val ScaleBottomPadding = 8.dp
    // The wooden ruler (handoff venue 29j)
    val ScaleRulerHeight = 26.dp
    val ScaleRulerCorner = 8.dp
    val ScaleRulerEdge = 1.dp
    val ScalePillHeight = 12.dp
    val ScaleTickWidth = 2.dp
    val ScaleTickHeight = 18.dp
    val MarkerWidth = 8.dp
    val MarkerHeight = 24.dp
    val HaloWidth = 44.dp
    val HaloHeight = 24.dp
    val HaloFeather = 4.dp

    val RecordButtonSize = 72.dp
    // The key of a tape recorder: bone face, brass rim, a hard shadow it goes down onto (handoff venue 29j)
    val RecordRim = 3.dp
    val RecordShadow = 4.dp
    val RecordTravel = 3.dp
    val RecordDotSize = 20.dp
    val RecordPaddingVertical = 12.dp
    val RecordStopSize = 22.dp
    val RecordStopCorner = 3.dp

    // The bookmark of blocks by the record key (spec 3.28, 5.21; handoff 30a2, `sizes`)
    val BookmarkWidth = 150.dp
    val BookmarkWidthLandscape = 170.dp
    /** «Репертуар» is as wide as its word, never narrower than this. */
    val BookmarkEntryMinWidth = 124.dp
    val BookmarkHeight = 56.dp
    /** Room under the paper for its shadow: the outline set down by [BookmarkShadow], without blur. */
    val BookmarkShadowRoom = 4.dp
    val BookmarkShadow = 3.dp
    val BookmarkCorner = 6.dp
    val BookmarkNotch = 15.dp
    val BookmarkEdge = 1.6.dp
    val BookmarkRim = 1.8.dp
    val BookmarkLine = 3.dp
    val BookmarkLineStart = 13.dp
    val BookmarkLineEnd = 27.dp
    val BookmarkLineFromBottom = 9.dp
    val BookmarkTextStart = 13.dp
    val BookmarkTextEnd = 30.dp
    val BookmarkTextGap = 3.dp
    val BookmarkTick = 15.dp
    val BookmarkTickGap = 5.dp
    /** Air between the bookmark (and the practice tag) and the record key; [BookmarkMargin] at the far side of its column. */
    val BookmarkToKey = 16.dp
    val BookmarkMargin = 4.dp

    val RecordingStripTopPadding = 8.dp
    val RecordingStripGap = 12.dp
    val RecordingDotSize = 10.dp
    val RecordingBarHeight = 6.dp
    val RecordingBarGap = 2.dp

    // Landscape (handoff v1-land): ring panel on the left, controls on the right
    // 400, not the 440 of v1: the ring is 260 here and its halo (380) fits the panel.
    const val LANDSCAPE_RING_PANEL_FRACTION = 400f / 892f
    val LandscapePaddingStart = 8.dp
    val LandscapePaddingEnd = 24.dp
    val LandscapePaddingVertical = 16.dp
    // 8, not the handoff's 12: a tuning-mode landscape runs out of height for the status word.
    val LandscapeSpacing = 8.dp
    /** Below this height the landscape status word and cents go compact. */
    val LandscapeStatusCompactHeight = 48.dp
    val LandscapeRecordTopPadding = 4.dp
    val LandscapeStatusMinHeight = 40.dp

    /** Height kept free under the ring for the permission prompt in portrait. */
    val PromptReservedHeight = 230.dp

    val MicIconContainerSize = 72.dp
    val MicGlyphWidth = 18.dp
    val MicGlyphHeight = 32.dp
    val PromptMaxWidth = 300.dp
    val PromptSpacing = 16.dp
    val PromptButtonHeight = 44.dp
    /** The card of paper the permission prompt is written on (handoff venue 29c8). */
    val PromptCardCorner = 14.dp
    val PromptCardPaddingHorizontal = 18.dp
    val PromptCardPaddingVertical = 16.dp
    val PromptButtonPaddingHorizontal = 24.dp

    // Opacity of dimmed parts (handoff states 8d–8f)
    const val SCALE_ALPHA_IDLE = 0.45f
    const val SCALE_ALPHA_NO_MIC = 0.3f
    const val CHROME_ALPHA_NO_MIC = 0.4f

    /** Controls that do nothing right now: record outside play mode, the switcher while recording. */
    const val DISABLED_ALPHA = 0.4f
    const val HALO_ALPHA_CORE = 0.2f
    const val HALO_ALPHA_FEATHER = 0.1f
}
