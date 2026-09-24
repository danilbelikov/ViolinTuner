package com.violinjourney.app.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily

/**
 * The part of the theme Android and iOS share: the Material colors and styles, and what Live reads
 * besides them. The app's ViolinTheme wraps it with the colors of its other screens; [fontFamily] is
 * Manrope, loaded the way each platform loads fonts.
 */
@Composable
fun ViolinBaseTheme(fontFamily: FontFamily, content: @Composable () -> Unit) {
    val live = remember(fontFamily) { liveTypography(fontFamily) }
    val material = remember(fontFamily) { violinTypography(fontFamily) }
    CompositionLocalProvider(
        LocalZoneColors provides DarkZoneColors,
        LocalStatusColors provides DarkStatusColors,
        LocalLiveTypography provides live,
        LocalVenueColors provides DarkVenueColors,
    ) {
        MaterialTheme(colorScheme = ViolinColorScheme, typography = material, content = content)
    }
}

/** The tokens of Live that Material has no place for; the app's ViolinTheme gives the same values. */
object LiveTheme {
    val zoneColors: ZoneColors
        @Composable @ReadOnlyComposable get() = LocalZoneColors.current

    val statusColors: StatusColors
        @Composable @ReadOnlyComposable get() = LocalStatusColors.current

    val liveTypography: LiveTypography
        @Composable @ReadOnlyComposable get() = LocalLiveTypography.current

    /** The controls of Live in the style of the room (spec 3.27). */
    val venueColors: VenueColors
        @Composable @ReadOnlyComposable get() = LocalVenueColors.current
}
