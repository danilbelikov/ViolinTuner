package com.violinjourney.app.feature.live.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.violinjourney.app.core.ui.theme.LiveTheme
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.mic_permission_grant
import com.violinjourney.app.shared.resources.mic_permission_text
import com.violinjourney.app.shared.resources.mic_permission_title
import org.jetbrains.compose.resources.stringResource

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

/**
 * Explanation and the "Разрешить доступ" button (spec 3.4, NoMicPermission), written on a card of
 * paper (spec 3.27, handoff 29c8): it has to be read over a bright room, and it is the one state of
 * Live that asks for a tap.
 */
@Composable
fun MicPermissionPrompt(onGrantClick: () -> Unit, modifier: Modifier = Modifier) {
    val paper = LiveTheme.venueColors
    val typography = LiveTheme.liveTypography
    val shape = RoundedCornerShape(LiveDimens.PromptCardCorner)
    Column(
        modifier = modifier
            .widthIn(max = LiveDimens.PromptMaxWidth)
            .background(paper.bone, shape)
            .padding(horizontal = LiveDimens.PromptCardPaddingHorizontal, vertical = LiveDimens.PromptCardPaddingVertical),
        verticalArrangement = Arrangement.spacedBy(LiveDimens.PromptSpacing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.mic_permission_title),
            color = paper.ink,
            textAlign = TextAlign.Center,
            style = typography.promptTitle,
        )
        Text(
            text = stringResource(Res.string.mic_permission_text),
            color = paper.ink,
            textAlign = TextAlign.Center,
            style = typography.promptBody,
        )
        Button(
            onClick = onGrantClick,
            modifier = Modifier.height(LiveDimens.PromptButtonHeight),
            shape = RoundedCornerShape(LiveDimens.PromptButtonHeight / 2),
            colors = ButtonDefaults.buttonColors(containerColor = paper.ink, contentColor = paper.bone),
            contentPadding = PaddingValues(horizontal = LiveDimens.PromptButtonPaddingHorizontal),
        ) {
            Text(
                text = stringResource(Res.string.mic_permission_grant),
                style = typography.promptBody.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}
