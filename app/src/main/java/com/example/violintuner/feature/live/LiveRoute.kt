package com.example.violintuner.feature.live

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Entry point of the Live destination; will own LiveViewModel once it exists (spec step 3). */
@Composable
fun LiveRoute(modifier: Modifier = Modifier) {
    LiveScreen(modifier = modifier)
}
