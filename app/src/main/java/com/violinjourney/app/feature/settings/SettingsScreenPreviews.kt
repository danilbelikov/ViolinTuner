package com.violinjourney.app.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.violinjourney.app.core.domain.TolerancePreset
import com.violinjourney.app.core.domain.UserSettings
import com.violinjourney.app.core.domain.sound.BuiltInPreset
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.sound.SoundCaption

@Preview(widthDp = 412, heightDp = 812)
@Composable
private fun SettingsScreenPreview() {
    ViolinTheme {
        SettingsScreen(
            state = SettingsState(442, UserSettings.A4_OPTIONS_HZ, TolerancePreset.BEGINNER, SoundCaption.BuiltIn(BuiltInPreset.CHAMBER_HALL), analyticsEnabled = true),
            onIntent = {},
            onBack = {},
        )
    }
}
