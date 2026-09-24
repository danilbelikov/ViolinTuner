package com.violinjourney.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.violinjourney.app.core.ui.theme.ViolinTheme

@Preview
@Composable
private fun AppBottomBarPreview() {
    ViolinTheme {
        AppBottomBar(current = TopLevelDestination.LIVE, onSelect = {}, practiceRunning = true)
    }
}

@Preview(widthDp = 892)
@Composable
private fun AppBottomBarCompactPreview() {
    ViolinTheme {
        AppBottomBar(current = TopLevelDestination.PRACTICE, onSelect = {}, practiceRunning = true, compact = true)
    }
}
