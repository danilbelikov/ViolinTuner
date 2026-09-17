package com.example.violintuner.feature.history

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.example.violintuner.R
import com.example.violintuner.core.ui.components.ComingSoonScreen
import com.example.violintuner.core.ui.theme.ViolinTheme

@Composable
fun HistoryScreen(modifier: Modifier = Modifier) {
    ComingSoonScreen(
        title = stringResource(R.string.nav_history),
        text = stringResource(R.string.history_coming_soon_text),
        iconRes = R.drawable.ic_nav_history,
        modifier = modifier,
    )
}

@Preview(widthDp = 412, heightDp = 812)
@Composable
private fun HistoryScreenPreview() {
    ViolinTheme { HistoryScreen() }
}
