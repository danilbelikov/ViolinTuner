package com.example.violintuner.feature.live.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.example.violintuner.R
import com.example.violintuner.core.ui.theme.ViolinTheme

/** Microphone glyph shown inside the small ring of the NoMicPermission state. */
@Composable
fun MicGlyph(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(LiveDimens.MicIconContainerSize)
            .background(colors.surfaceContainerHigh, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(LiveDimens.MicGlyphWidth, LiveDimens.MicGlyphHeight)
                .background(colors.onSurfaceVariant, CircleShape),
        )
    }
}

/** Explanation and the "Разрешить доступ" button (spec 3.4, NoMicPermission). */
@Composable
fun MicPermissionPrompt(onGrantClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val typography = ViolinTheme.liveTypography
    Column(
        modifier = modifier.widthIn(max = LiveDimens.PromptMaxWidth),
        verticalArrangement = Arrangement.spacedBy(LiveDimens.PromptSpacing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.mic_permission_title),
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            style = typography.promptTitle,
        )
        Text(
            text = stringResource(R.string.mic_permission_text),
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = typography.promptBody,
        )
        Button(
            onClick = onGrantClick,
            modifier = Modifier.height(LiveDimens.PromptButtonHeight),
            contentPadding = PaddingValues(horizontal = LiveDimens.PromptButtonPaddingHorizontal),
        ) {
            Text(
                text = stringResource(R.string.mic_permission_grant),
                style = typography.promptBody.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}
