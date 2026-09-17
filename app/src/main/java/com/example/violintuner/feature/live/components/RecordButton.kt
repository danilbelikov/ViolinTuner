package com.example.violintuner.feature.live.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.example.violintuner.R

/** Big accent circle with a dot. Visual only in v1: recording is spec section 7. */
@Composable
fun RecordButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(LiveDimens.RecordButtonSize)
            .clip(CircleShape)
            .background(colors.primary)
            .clickable(
                onClickLabel = stringResource(R.string.record_button),
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(LiveDimens.RecordDotSize)
                .background(colors.onPrimary, CircleShape),
        )
    }
}
