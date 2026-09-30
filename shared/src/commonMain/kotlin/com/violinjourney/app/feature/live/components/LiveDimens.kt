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

    /** The practice tag to the right of the record key (handoff nav_bar 35): the bookmark's size, turned round. */
    val PracticeTagMinWidth = 124.dp
    val PracticeTagTextStart = 25.dp
    val PracticeTagTextEnd = 10.dp
    val PracticeTagIcon = 18.dp
    val PracticeTagIconGap = 8.dp
    val PracticeTagDot = 7.dp
    val PracticeTagDotGap = 6.dp

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
    val ScaleTopGap = PortraitRows.SCALE_GAP.dp

    /** Without the permission the scale stays at the bottom of the place, over the keys, as before R6. */
    val ScaleBottomPadding = 8.dp
    val ScaleLine = 2.dp
    val ScalePillHeight = 10.dp
    val ScaleTickWidth = 2.dp
    val ScaleTickHeight = 14.dp
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
    val RecordPaddingVertical = PortraitRows.AIR.dp
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
    /** Below this height the landscape status word and cents go compact. */
    val LandscapeStatusCompactHeight = 48.dp
    val LandscapeRecordTopPadding = LiveLayoutMath.LANDSCAPE_KEYS_AIR.dp
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

    /** The note of the locked string, pale in the ring in silence (spec 5.29 R6, behind [LiveSwitches.STRING_SILHOUETTE]). */
    const val SILHOUETTE_ALPHA = 0.28f

    // The scale of «Настройка»: its line and zero tick in onSurface, its pill in the green of the tolerance (spec 5.29 R6)
    const val SCALE_LINE_ALPHA = 0.35f
    const val SCALE_PILL_ALPHA = 0.55f

    /** The ground of the nearest string: bone over the glass (spec 5.29 R6). */
    const val STRING_NEAR_GROUND_ALPHA = 0.16f
}
