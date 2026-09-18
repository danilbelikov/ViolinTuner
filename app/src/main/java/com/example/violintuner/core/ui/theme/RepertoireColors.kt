package com.example.violintuner.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Tokens of the repertoire (handoff `Репертуар.dc.html`, `tokens`). Not zone colors and not the Material scheme. */
@Immutable
data class RepertoireColors(
    /** Under a sheet photo while it loads. */
    val paper: Color,
    /** Hairline inside a thumbnail: parts the bright sheet from its card. */
    val paperFrame: Color,
    /** The film over thumbnails; its strength is the component's business. */
    val thumbDim: Color,
    val standBackground: Color,
    val statusRepertoireContainer: Color,
    val onStatusRepertoireContainer: Color,
    /** A take that has just been recorded, before it settles into the list. */
    val takeNew: Color,
    /** Frame and caption of a form field that cannot be saved; the destructive button of a dialog. */
    val formError: Color,
)

internal val DarkRepertoireColors = RepertoireColors(
    paper = Paper,
    paperFrame = PaperFrame,
    thumbDim = Surface,
    standBackground = StandBackground,
    statusRepertoireContainer = StatusRepertoireContainer,
    onStatusRepertoireContainer = OnStatusRepertoireContainer,
    takeNew = TakeNew,
    formError = FormError,
)

internal val LocalRepertoireColors = staticCompositionLocalOf<RepertoireColors> {
    error("RepertoireColors not provided: wrap content in ViolinTheme")
}
