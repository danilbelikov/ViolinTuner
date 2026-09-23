package com.violinjourney.app.feature.practice.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.domain.progress.ProgressConfig
import com.violinjourney.app.core.ui.theme.ViolinTheme

private const val GALLERY_COLUMNS = 5

/** Handoff 11t: every trophy large, small and not yet given. */
@Preview(name = "11t trophies", widthDp = 1000, heightDp = 520)
@Composable
private fun TrophyGalleryPreview() {
    ViolinTheme {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProgressConfig().trophyHours.chunked(GALLERY_COLUMNS).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { hours ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TrophyIcon(hours, locked = false, size = 160.dp)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                TrophyIcon(hours, locked = false, size = 40.dp)
                                TrophyIcon(hours, locked = true, size = 40.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}
