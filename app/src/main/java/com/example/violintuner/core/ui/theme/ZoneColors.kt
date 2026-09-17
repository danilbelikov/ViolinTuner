package com.example.violintuner.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.violintuner.core.domain.Zone

/**
 * Stops of the radial background gradient for one zone: [start] at 0 %, [mid] at [MID_STOP],
 * [end] at 100 %. [end] equals the screen surface so the gradient fades into the background.
 */
@Immutable
data class ZoneGradient(
    val start: Color,
    val mid: Color,
    val end: Color,
) {
    val colorStops: Array<Pair<Float, Color>>
        get() = arrayOf(0f to start, MID_STOP to mid, 1f to end)

    companion object {
        const val MID_STOP = 0.45f
    }
}

/** Semantic intonation-zone tokens. `colorScheme.error` is deliberately not used for "off". */
@Immutable
data class ZoneColors(
    val inTune: Color,
    val near: Color,
    val off: Color,
    /** Ring outline in silence and error states. */
    val none: Color,
    val ringTrack: Color,
    val inTuneGradient: ZoneGradient,
    val nearGradient: ZoneGradient,
    val offGradient: ZoneGradient,
    /** All stops equal the surface: "no gradient", yet cross-fadable like the others. */
    val noneGradient: ZoneGradient,
) {
    /** [zone] is null when nothing is sounding. */
    fun colorFor(zone: Zone?): Color = when (zone) {
        Zone.IN_TUNE -> inTune
        Zone.NEAR -> near
        Zone.OFF -> off
        null -> none
    }

    fun gradientFor(zone: Zone?): ZoneGradient = when (zone) {
        Zone.IN_TUNE -> inTuneGradient
        Zone.NEAR -> nearGradient
        Zone.OFF -> offGradient
        null -> noneGradient
    }
}

internal val DarkZoneColors = ZoneColors(
    inTune = ZoneInTune,
    near = ZoneNear,
    off = ZoneOff,
    none = ZoneNone,
    ringTrack = RingTrack,
    inTuneGradient = ZoneGradient(GradientInTuneStart, GradientInTuneMid, Surface),
    nearGradient = ZoneGradient(GradientNearStart, GradientNearMid, Surface),
    offGradient = ZoneGradient(GradientOffStart, GradientOffMid, Surface),
    noneGradient = ZoneGradient(Surface, Surface, Surface),
)

internal val LocalZoneColors = staticCompositionLocalOf<ZoneColors> {
    error("ZoneColors not provided: wrap content in ViolinTheme")
}
