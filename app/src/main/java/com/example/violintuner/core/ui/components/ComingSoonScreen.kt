package com.example.violintuner.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.violintuner.R

// Handoff section `v1-stubs` (drawn at half scale there).
private val TitlePaddingHorizontal = 20.dp
private val TitlePaddingTop = 28.dp
private val BodyPaddingHorizontal = 40.dp
private val BodySpacing = 16.dp
private val IconContainerSize = 72.dp
private val IconSize = 24.dp
private val TitleFontSize = 32.sp
private val HeadlineFontSize = 30.sp
private val BodyFontSize = 22.sp
private const val BODY_LINE_HEIGHT_EM = 1.4f

/** Placeholder for tabs that are out of v1 scope (spec section 4). */
@Composable
fun ComingSoonScreen(
    title: String,
    text: String,
    @DrawableRes iconRes: Int,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface),
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(
                start = TitlePaddingHorizontal,
                end = TitlePaddingHorizontal,
                top = TitlePaddingTop,
            ),
            color = colors.onSurface,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = TitleFontSize,
                fontWeight = FontWeight.Bold,
            ),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = BodyPaddingHorizontal),
            verticalArrangement = Arrangement.spacedBy(BodySpacing, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(IconContainerSize)
                    .background(colors.surfaceContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(IconSize),
                    tint = colors.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(R.string.coming_soon_title),
                color = colors.onSurface,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = HeadlineFontSize,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Text(
                text = text,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = BodyFontSize,
                    lineHeight = BodyFontSize * BODY_LINE_HEIGHT_EM,
                ),
            )
        }
    }
}
