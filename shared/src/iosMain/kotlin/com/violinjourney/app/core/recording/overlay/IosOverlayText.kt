package com.violinjourney.app.core.recording.overlay

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.platform.Font
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.manrope_variable
import org.jetbrains.compose.resources.getFontResourceBytes
import org.jetbrains.compose.resources.getSystemResourceEnvironment

/**
 * Manrope for the notes drawn on a video on iOS (spec 3.37): the variable font the theme loads (`manrope()` of `IosApp`), read
 * from the shared resources as bytes — the overlay is drawn outside any composition, where `Font(resource)` cannot be asked;
 * and the icon of the signature beside it.
 */
internal object IosOverlayText {
    @OptIn(ExperimentalTextApi::class)
    suspend fun load(): OverlayText {
        val bytes = getFontResourceBytes(getSystemResourceEnvironment(), Res.font.manrope_variable)
        val fonts = WEIGHTS.map { weight ->
            Font(IDENTITY + weight.weight, bytes, weight, FontStyle.Normal, FontVariation.Settings(FontVariation.weight(weight.weight)))
        }
        return OverlayText(createFontFamilyResolver(), FontFamily(fonts), OverlayText.icon())
    }

    /** The weights the overlay draws in. */
    private val WEIGHTS = listOf(FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold)
    private const val IDENTITY = "manrope-overlay-"
}
