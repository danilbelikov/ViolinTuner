package com.violinjourney.app.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private const val TABULAR_FIGURES = "tnum"

/** Live-screen text styles that have no Material counterpart (handoff `sizes` table). */
@Immutable
data class LiveTypography(
    val note: TextStyle,
    val octave: TextStyle,
    /** The status word, full size; the row gives it the size of its step (compact and smaller — `StatusFit` on Live). */
    val status: TextStyle,
    /** The cents beside the status word: for a direct look, smaller than the note and quieter than the word. */
    val cents: TextStyle,
    /** The status line above the ring, on its plate: "Играйте…", "Слишком шумно", the tuning hint — 15 sp, 600 (spec 5.29 R6). */
    val statusLine: TextStyle,
    /** The title of the card «нет разрешения»: 22 sp, 800 (spec 5.29 R6). */
    val promptTitle: TextStyle,
    /** Its text: 15 sp in lines of 21.75. */
    val promptBody: TextStyle,
)

/** The Live styles in [family]: Manrope — the app's own font on Android, from composeResources on iOS. */
fun liveTypography(family: FontFamily) = LiveTypography(
    note = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Bold,
        fontSize = 176.sp,
        lineHeight = 176.sp,
        letterSpacing = (-0.04).em,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    octave = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 52.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    status = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
    ),
    cents = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    statusLine = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
    ),
    // the card «нет разрешения» (spec 5.29 R6): the title 22 sp / 800 / −0.01 em, the text 15 sp in lines of 1.45
    promptTitle = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.01).em,
    ),
    promptBody = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.75.sp,
    ),
)

val LocalLiveTypography = staticCompositionLocalOf<LiveTypography> {
    error("LiveTypography not provided: wrap content in ViolinTheme")
}

private val Default = Typography()

/** The Material styles, all in [family]. */
fun violinTypography(family: FontFamily) = Typography(
    displayLarge = Default.displayLarge.copy(fontFamily = family),
    displayMedium = Default.displayMedium.copy(fontFamily = family),
    displaySmall = Default.displaySmall.copy(fontFamily = family),
    headlineLarge = Default.headlineLarge.copy(fontFamily = family),
    headlineMedium = Default.headlineMedium.copy(fontFamily = family),
    headlineSmall = Default.headlineSmall.copy(fontFamily = family),
    titleLarge = Default.titleLarge.copy(fontFamily = family, fontWeight = FontWeight.Bold),
    titleMedium = Default.titleMedium.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
    titleSmall = Default.titleSmall.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
    bodyLarge = Default.bodyLarge.copy(fontFamily = family),
    bodyMedium = Default.bodyMedium.copy(fontFamily = family),
    bodySmall = Default.bodySmall.copy(fontFamily = family),
    labelLarge = Default.labelLarge.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
    labelMedium = Default.labelMedium.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
    labelSmall = Default.labelSmall.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
)
