package com.violinjourney.app.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The shares of weight in a copy of the data (spec 3.20, handoff `Копия данных`, `tokens`): a
 * row of violets from light to dark, by weight. Not zone colors — a gigabyte is not a grade. A
 * share left out of the copy greys to surfaceContainerHigh (spec 3.36.8, 5.29 R8): no colour of its own.
 */
@Immutable
data class BackupColors(val data: Color, val sheets: Color, val audio: Color, val video: Color)

internal val DarkBackupColors = BackupColors(data = BackupData, sheets = BackupSheets, audio = BackupAudio, video = BackupVideo)

internal val LocalBackupColors = staticCompositionLocalOf<BackupColors> {
    error("BackupColors not provided: wrap content in ViolinTheme")
}
