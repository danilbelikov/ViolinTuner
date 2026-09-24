package com.violinjourney.app.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.violinjourney.app.core.domain.Zone

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
    /** Content on top of [off]: the stop square of the record button. */
    val onOff: Color,
    /** Ring outline in silence and error states. */
    val none: Color,
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

/** The dot of the Live status line (spec 3.14). Two shapes go with the two colors; see the component. */
@Immutable
data class StatusColors(val ready: Color, val blocked: Color)

val DarkStatusColors = StatusColors(ready = StatusReady, blocked = StatusBlocked)

val LocalStatusColors = staticCompositionLocalOf<StatusColors> {
    error("StatusColors not provided: wrap content in ViolinTheme")
}

val DarkZoneColors = ZoneColors(
    inTune = ZoneInTune,
    near = ZoneNear,
    off = ZoneOff,
    onOff = OnZoneOff,
    none = ZoneNone,
    inTuneGradient = ZoneGradient(GradientInTuneStart, GradientInTuneMid, Surface),
    nearGradient = ZoneGradient(GradientNearStart, GradientNearMid, Surface),
    offGradient = ZoneGradient(GradientOffStart, GradientOffMid, Surface),
    noneGradient = ZoneGradient(Surface, Surface, Surface),
)

val LocalZoneColors = staticCompositionLocalOf<ZoneColors> {
    error("ZoneColors not provided: wrap content in ViolinTheme")
}
