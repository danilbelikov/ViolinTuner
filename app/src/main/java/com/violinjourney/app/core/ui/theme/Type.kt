package com.violinjourney.app.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.violinjourney.app.R

// Variable-font axes are still an experimental Compose API; one TTF covers all weights.
@OptIn(ExperimentalTextApi::class)
private fun manrope(weight: FontWeight) = Font(
    resId = R.font.manrope_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

internal val Manrope = FontFamily(
    manrope(FontWeight.Normal),
    manrope(FontWeight.Medium),
    manrope(FontWeight.SemiBold),
    manrope(FontWeight.Bold),
    manrope(FontWeight.ExtraBold),
)

private const val TABULAR_FIGURES = "tnum"

/** Live-screen text styles that have no Material counterpart (handoff `sizes` table). */
@Immutable
data class LiveTypography(
    val note: TextStyle,
    val octave: TextStyle,
    val status: TextStyle,
    /** Status word where the height is short: low landscape, small screens. */
    val statusCompact: TextStyle,
    /** The cents beside the status word: for a direct look, smaller than the note and quieter than the word. */
    val cents: TextStyle,
    val centsCompact: TextStyle,
    /** The small status line above the ring: "Играйте…", "Слишком шумно", the tuning hint. */
    val statusLine: TextStyle,
    val promptTitle: TextStyle,
    val promptBody: TextStyle,
)

internal val DefaultLiveTypography = LiveTypography(
    note = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Bold,
        fontSize = 176.sp,
        lineHeight = 176.sp,
        letterSpacing = (-0.04).em,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    octave = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.SemiBold,
        fontSize = 52.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    status = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
    ),
    statusCompact = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
    ),
    cents = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    centsCompact = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    statusLine = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
    ),
    promptTitle = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 29.sp,
    ),
    promptBody = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
)

internal val LocalLiveTypography = staticCompositionLocalOf<LiveTypography> {
    error("LiveTypography not provided: wrap content in ViolinTheme")
}

private val Default = Typography()

internal val ViolinTypography = Typography(
    displayLarge = Default.displayLarge.copy(fontFamily = Manrope),
    displayMedium = Default.displayMedium.copy(fontFamily = Manrope),
    displaySmall = Default.displaySmall.copy(fontFamily = Manrope),
    headlineLarge = Default.headlineLarge.copy(fontFamily = Manrope),
    headlineMedium = Default.headlineMedium.copy(fontFamily = Manrope),
    headlineSmall = Default.headlineSmall.copy(fontFamily = Manrope),
    titleLarge = Default.titleLarge.copy(fontFamily = Manrope, fontWeight = FontWeight.Bold),
    titleMedium = Default.titleMedium.copy(fontFamily = Manrope, fontWeight = FontWeight.SemiBold),
    titleSmall = Default.titleSmall.copy(fontFamily = Manrope, fontWeight = FontWeight.SemiBold),
    bodyLarge = Default.bodyLarge.copy(fontFamily = Manrope),
    bodyMedium = Default.bodyMedium.copy(fontFamily = Manrope),
    bodySmall = Default.bodySmall.copy(fontFamily = Manrope),
    labelLarge = Default.labelLarge.copy(fontFamily = Manrope, fontWeight = FontWeight.SemiBold),
    labelMedium = Default.labelMedium.copy(fontFamily = Manrope, fontWeight = FontWeight.SemiBold),
    labelSmall = Default.labelSmall.copy(fontFamily = Manrope, fontWeight = FontWeight.SemiBold),
)
