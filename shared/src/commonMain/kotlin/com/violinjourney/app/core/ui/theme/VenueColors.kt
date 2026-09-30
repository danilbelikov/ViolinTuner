package com.violinjourney.app.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The controls of Live in glass and bone (spec 3.36.6, 5.29 R6; they were the maple plank, the ebony pegs and the wooden ruler of
 * 3.27): the switcher, the gear, the strings and the cards stand on smoked glass (`Modifier.glass`) with light words, the second
 * level of them [glassCaption]; what is chosen, locked or running is [bone] and [boneShade] with [ink] and [inkSoft] written on it; the
 * record key of R6 is bone in the dark [keyRim] with a [velvet] dot, red while it records with [recordingRim]; [brass] —
 * «сделано».
 */
@Immutable
data class VenueColors(
    val bone: Color,
    val boneShade: Color,
    val brass: Color,
    val velvet: Color,
    val ink: Color,
    val inkSoft: Color,
    val recordingRim: Color,
    /** The rim of the record key: darker and duller than [brass], the «сделано» beside it (spec 5.29 R6). */
    val keyRim: Color,
    /** The second level of text on the glass (spec 5.29 R6): «выбрать», «занятие», the hertz of a string, the word not chosen. */
    val glassCaption: Color,
)

val DarkVenueColors = VenueColors(
    bone = CtrlBone, boneShade = CtrlBoneShade, brass = CtrlBrass, velvet = CtrlVelvet, ink = CtrlInk, inkSoft = CtrlInkSoft,
    recordingRim = RecordingRim, keyRim = KeyRim, glassCaption = GlassCaption,
)

val LocalVenueColors = staticCompositionLocalOf<VenueColors> {
    error("VenueColors not provided: wrap content in ViolinTheme")
}
