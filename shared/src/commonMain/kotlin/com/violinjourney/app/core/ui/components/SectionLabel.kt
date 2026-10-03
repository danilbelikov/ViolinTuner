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

/**
 * The label of a section (spec 5.29): 13 sp / 700, in capitals, letters 0.06 em apart, onSurfaceVariant, one line — «СЕГОДНЯ»,
 * the groups of «Настройки» (R8). The title of a sheet has the same look («ЗАКОНЧИТЬ ЗАНЯТИЕ», `.sheet-title`). A heading for
 * TalkBack; the words are given as they are written and put in capitals here. [maxLines] above one — a label whose translation
 * can outgrow a narrow sheet at a large font («DER UNTERRICHT WIEDERHOLT SICH»): it wraps at a space rather than lose its words.
 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, maxLines: Int = 1) {
    Text(
        text = text.uppercase(),
        modifier = modifier.semantics { heading() },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.06.em),
    )
}
