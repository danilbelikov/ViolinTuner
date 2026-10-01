package com.violinjourney.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The plate of what is missing (spec 3.36.7, 5.29 R7, «Кнопки»).
private val Capsule = RoundedCornerShape(percent = 50)
private val PlateHeight = 56.dp
private val PlateHeightCompact = 48.dp
private val PlatePadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)
private val PlatePaddingCompact = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
private val LeadingGap = 8.dp
private const val TEXT_SP = 16f
private const val TEXT_LEAST_SP = 14f
private const val CAPTION_SP = 13f
private const val CAPTION_LEAST_SP = 12f
private const val LINE_HEIGHT = 1.25f
private const val TABULAR_FIGURES = "tnum"

/**
 * What is missing, said — not a sleeping button (spec 3.36.7, 5.29 R7): «[takt] не хватает 1 128» and under it, when given,
 * «примерно 4 занятия». It tells, it does not forbid (3.23): no role and no touch, not dimmed to 0.38, no line of a reason over it,
 * and for a reader one text — [description], or the words and the [caption] one after the other.
 *
 * A capsule of 56 — 48 where the zone is [compact] (a window no higher than 360 dp) — on surfaceContainerHigh, as high as the main
 * button it stands in the place of; the words 16 sp / 700 and the caption 13 sp / 600 in the second colour, tabular, each on one
 * line: smaller where it does not fit — 16 → 14 and 13 → 12 sp — then cut ([OneLineText]); [keep] — the part of the words never cut
 * (the number). [leading] stands before the words: the sign of a takt, which is the journey's own and comes from there.
 */
@Composable
fun ShortfallPlate(
    text: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    description: String? = null,
    compact: Boolean = false,
    keep: String? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val said = description ?: listOfNotNull(text, caption).joinToString(", ")
    Row(
        modifier = modifier
            .heightIn(min = if (compact) PlateHeightCompact else PlateHeight)
            .background(colors.surfaceContainerHigh, Capsule)
            .clearAndSetSemantics { contentDescription = said }
            .padding(if (compact) PlatePaddingCompact else PlatePadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(LeadingGap))
            }
            val words = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = TABULAR_FIGURES)
            Column(Modifier.weight(1f, fill = false), horizontalAlignment = if (caption != null) Alignment.Start else Alignment.CenterHorizontally) {
                OneLineText(
                    text = text,
                    style = words.copy(fontSize = TEXT_SP.sp, lineHeight = (TEXT_SP * LINE_HEIGHT).sp, fontWeight = FontWeight.Bold),
                    minSp = TEXT_LEAST_SP,
                    color = colors.onSurface,
                    keep = keep,
                )
                if (caption != null) {
                    OneLineText(
                        text = caption,
                        style = words.copy(fontSize = CAPTION_SP.sp, lineHeight = (CAPTION_SP * LINE_HEIGHT).sp, fontWeight = FontWeight.SemiBold),
                        minSp = CAPTION_LEAST_SP,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
