package com.example.violintuner.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.example.violintuner.R
import com.example.violintuner.core.ui.components.ComingSoonScreen
import com.example.violintuner.core.ui.theme.ViolinTheme

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    ComingSoonScreen(
        title = stringResource(R.string.nav_settings),
        text = stringResource(R.string.settings_coming_soon_text),
        iconRes = R.drawable.ic_nav_settings,
        modifier = modifier,
    )
}

@Preview(widthDp = 412, heightDp = 812)
@Composable
private fun SettingsScreenPreview() {
    ViolinTheme { SettingsScreen() }
}
