package com.example.violintuner.feature.live.components

import androidx.compose.ui.unit.dp

/** Sizes of the Live screen from the handoff `sizes` table (base screen 412 × 892 dp). */
internal object LiveDimens {
    val ScreenPadding = 24.dp

    val SwitcherTopPadding = 12.dp
    val SwitcherHeight = 40.dp
    val SwitcherCorner = 20.dp
    val SwitcherBorder = 1.dp
    val SwitcherIconSize = 14.dp
    val SwitcherIconStroke = 2.5.dp
    val SwitcherTuningIconWidth = 4.dp
    val SwitcherIconGap = 8.dp

    val RingSize = 300.dp
    val RingSizeNoMic = 200.dp
    val RingStroke = 6.dp
    val OctaveStartPadding = 4.dp
    val IndicatorSpacing = 24.dp

    val StatusRowHeight = 64.dp
    val StatusArrowSize = 64.dp
    val StatusDotSize = 24.dp
    val StatusGap = 12.dp

    val ScaleHeight = 36.dp
    val ScaleBottomPadding = 8.dp
    val ScaleTrackHeight = 8.dp
    val ScalePillHeight = 12.dp
    val ScaleTickWidth = 2.dp
    val ScaleTickHeight = 20.dp
    val MarkerWidth = 6.dp
    val MarkerHeight = 28.dp
    val MarkerOutline = 2.dp
    val HaloWidth = 44.dp
    val HaloHeight = 24.dp
    val HaloFeather = 4.dp

    val RecordButtonSize = 72.dp
    val RecordDotSize = 26.dp
    val RecordPaddingVertical = 12.dp

    val MicIconContainerSize = 72.dp
    val MicGlyphWidth = 18.dp
    val MicGlyphHeight = 32.dp
    val PromptMaxWidth = 300.dp
    val PromptSpacing = 16.dp
    val PromptButtonHeight = 48.dp
    val PromptButtonPaddingHorizontal = 24.dp

    // Opacity of dimmed parts (handoff states 8d–8f)
    const val SCALE_ALPHA_IDLE = 0.45f
    const val SCALE_ALPHA_NO_MIC = 0.3f
    const val CHROME_ALPHA_NO_MIC = 0.4f
    const val HALO_ALPHA_CORE = 0.2f
    const val HALO_ALPHA_FEATHER = 0.1f
}
