package com.violinjourney.app.core.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private val TitleSize = 28.sp
private val CompactTitleSize = 24.sp
private val TitleTracking = (-0.02).em

/**
 * The title of a tab — «Репертуар», «Записи» (spec 3.36.1, 5.29): 28 sp, 800, one line; [compact] — 24 sp, in landscape.
 * A heading for TalkBack and VoiceOver; not pressed. Stages R4 and R5 put headers of their own in its place.
 */
@Composable
fun TabTitle(text: String, modifier: Modifier = Modifier, compact: Boolean = false) {
    val size = if (compact) CompactTitleSize else TitleSize
    Text(
        text = text,
        modifier = modifier.semantics { heading() },
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.headlineMedium.copy(
            fontSize = size,
            lineHeight = size * LINE_HEIGHT,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = TitleTracking,
        ),
    )
}

/** The line of a title: 1.2 of its size — a tight line for one word; the spec names none. */
private const val LINE_HEIGHT = 1.2f
