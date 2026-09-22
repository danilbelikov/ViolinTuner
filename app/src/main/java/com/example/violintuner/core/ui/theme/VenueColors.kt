package com.example.violintuner.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The controls of Live drawn as things of the room (spec 3.27, handoff venue 29j): a maple plank with
 * a bone slider, ebony pegs with nickel heads, a bone key with a brass rim, a paper tag, a wooden
 * ruler. [ink] is what is written on bone and paper, [caption] — small figures on ebony; the label of
 * the place is a smoked-glass pill over the picture.
 */
@Immutable
data class VenueColors(
    val ebony: Color,
    val ebonyEdge: Color,
    val maple: Color,
    val mapleLit: Color,
    val mapleDark: Color,
    val ruler: Color,
    val bone: Color,
    val boneShade: Color,
    val nickel: Color,
    val brass: Color,
    val velvet: Color,
    val ink: Color,
    val inkSoft: Color,
    val caption: Color,
    val muted: Color,
    val recordingRim: Color,
    val labelFill: Color,
    val labelEdge: Color,
)

internal val DarkVenueColors = VenueColors(
    ebony = CtrlEbony, ebonyEdge = CtrlEbonyEdge, maple = CtrlMaple, mapleLit = CtrlMapleLit, mapleDark = CtrlMapleDark, ruler = CtrlRuler,
    bone = CtrlBone, boneShade = CtrlBoneShade, nickel = CtrlNickel, brass = CtrlBrass, velvet = CtrlVelvet, ink = CtrlInk, inkSoft = CtrlInkSoft,
    caption = CtrlCaption, muted = CtrlMuted, recordingRim = RecordingRim, labelFill = VenueLabelFill, labelEdge = VenueLabelEdge,
)

internal val LocalVenueColors = staticCompositionLocalOf<VenueColors> {
    error("VenueColors not provided: wrap content in ViolinTheme")
}
