package com.violinjourney.app.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/**
 * The theme of every screen: the colors of each part of the app over [ViolinBaseTheme]. [fontFamily] is Manrope,
 * loaded the way each platform loads fonts — the app's ViolinTheme gives the one from res/font.
 */
@Composable
fun ViolinAppTheme(fontFamily: FontFamily, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalPracticeColors provides DarkPracticeColors,
        LocalProgressColors provides DarkProgressColors,
        LocalRepertoireColors provides DarkRepertoireColors,
        LocalSoundColors provides DarkSoundColors,
        LocalVideoColors provides DarkVideoColors,
        LocalBackupColors provides DarkBackupColors,
        LocalExerciseColors provides DarkExerciseColors,
    ) {
        ViolinBaseTheme(fontFamily = fontFamily, content = content)
    }
}

object ViolinTheme {
    val zoneColors: ZoneColors
        @Composable @ReadOnlyComposable get() = LocalZoneColors.current

    val statusColors: StatusColors
        @Composable @ReadOnlyComposable get() = LocalStatusColors.current

    val liveTypography: LiveTypography
        @Composable @ReadOnlyComposable get() = LocalLiveTypography.current

    val practiceColors: PracticeColors
        @Composable @ReadOnlyComposable get() = LocalPracticeColors.current

    val progressColors: ProgressColors
        @Composable @ReadOnlyComposable get() = LocalProgressColors.current

    val repertoireColors: RepertoireColors
        @Composable @ReadOnlyComposable get() = LocalRepertoireColors.current

    val soundColors: SoundColors
        @Composable @ReadOnlyComposable get() = LocalSoundColors.current

    val videoColors: VideoColors
        @Composable @ReadOnlyComposable get() = LocalVideoColors.current

    val backupColors: BackupColors
        @Composable @ReadOnlyComposable get() = LocalBackupColors.current

    val exerciseColors: ExerciseColors
        @Composable @ReadOnlyComposable get() = LocalExerciseColors.current

    /** The controls of Live in the style of the room (spec 3.27). */
    val venueColors: VenueColors
        @Composable @ReadOnlyComposable get() = LocalVenueColors.current

    /**
     * The third level of text (spec 5.29): day labels, chevrons, the figures in chips, a placeholder. Only on the background
     * and on surfaceContainer — not on surfaceContainerHigh (dialogs, menus), where it falls under 4.5 : 1.
     */
    val textTertiary: Color
        get() = TextTertiary

    /**
     * «Сделано» outside Live (spec 3.36, 3.36.3, 5.29): the brass of a block played to its goal in «Что играли» — the hex of the brass
     * of Live's controls, so the tick says the same thing on both. One token for «сделано»: R6 moves the tick of «сегодня» in «Что
     * играем» on Live to it as well.
     */
    val done: Color
        get() = CtrlBrass

    /** The ground of a chosen option and the plate of an icon (spec 5.29); not the calendar, which keeps its fills. */
    val accentSoft: Color
        get() = AccentSoft

    /**
     * What is dangerous (spec 3.36.1, 5.29): deleting, removing, replacing data, stopping a copy; a warning that data will be
     * lost; the sign of a failure. With the bin, as spec 3.16 wants. Never colorScheme.error, never a zone colour; the error
     * of a field is not dangerous either — it is onSurfaceVariant with an icon.
     */
    val dangerSoft: Color
        get() = DangerSoft

    /** The text of the one filled dangerous button, «Восстановить» (spec 3.20, 5.29). */
    val onDanger: Color
        get() = OnDanger

    /** Smoked glass at .72 over a picture (spec 5.29), no blur; text on it is onSurface, never onSurfaceVariant. */
    val glass: Color
        get() = Glass

    /** Glass at .82: only over a busy picture — the preview of the own camera, the city tag of the shop (spec 5.29). */
    val glassStrong: Color
        get() = GlassStrong

    /**
     * The second level of text on the glass (spec 5.29 R6): the line under the first one of a card on glass, the hertz of a string, the
     * word of the switcher not chosen. 4.4 : 1 on the glass over pure white — the lightest hall is checked by a screenshot.
     */
    val glassCaption: Color
        get() = GlassCaption

    /** The inner edge of 1 dp of the glass of Live's controls (spec 5.29 R6): white at 12 %; the plate of the status line has none. */
    val glassEdge: Color
        get() = GlassEdge

    /** Black at .55 under a sheet (spec 5.29). */
    val sheetScrim: Color
        get() = SheetScrim

    /** The sign of recording, its dot and its key (spec 3.9, 5.29): the hex of «мимо», but no zone. */
    val recording: Color
        get() = Recording

    /** The stop square on the red key of recording. */
    val onRecording: Color
        get() = OnRecording
}
