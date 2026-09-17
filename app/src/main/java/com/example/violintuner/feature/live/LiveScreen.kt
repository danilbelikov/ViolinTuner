package com.example.violintuner.feature.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Navigation-skeleton placeholder: a bare dark surface. The real stateless screen
 * (State + `(Intent) -> Unit`) arrives with LiveContract in spec step 3.
 */
@Composable
fun LiveScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    )
}
