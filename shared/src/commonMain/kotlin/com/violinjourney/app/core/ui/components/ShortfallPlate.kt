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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The plate of what is missing (spec 3.36.7, 5.29 R7, «Кнопки»).
private val Capsule = RoundedCornerShape(percent = 50)
private val PlateHeight = 56.dp
private val PlateHeightCompact = 48.dp
private val PlateSide = 20.dp
private val PlateSideCompact = 16.dp
private val PlatePadding = PaddingValues(horizontal = PlateSide, vertical = 6.dp)
private val PlatePaddingCompact = PaddingValues(horizontal = PlateSideCompact, vertical = 4.dp)

/** A plate is laid out in whole pixels: words that fit only by a hair are not trusted. */
private val OneLineSlack = 1.dp
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
                    style = plateWords(),
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

/** The words of the plate — the style they are drawn and measured in ([shortfallPlateFits]). */
@Composable
private fun plateWords(): TextStyle = MaterialTheme.typography.labelLarge.copy(
    fontSize = TEXT_SP.sp,
    lineHeight = (TEXT_SP * LINE_HEIGHT).sp,
    fontWeight = FontWeight.Bold,
    fontFeatureSettings = TABULAR_FIGURES,
)

/**
 * Whether the words of a [ShortfallPlate] — [text], after the sign of [leading] wide — stand whole in a plate [width] wide, [compact] or
 * not: at its least size of 14 sp, where the plate would otherwise cut them (spec 5.29 R7: the number is never cut, and the words around
 * it are not cut beside a neighbour either). The card of a thing and the try-on stand the plate beside an outline only where it does;
 * else the two stand one under the other, the plate on the whole width.
 */
@Composable
internal fun shortfallPlateFits(text: String, width: Dp, compact: Boolean, leading: Dp = 0.dp): Boolean {
    val words = plateWords()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(text, width, compact, leading, words, measurer, density) {
        with(density) {
            val sign = if (leading > 0.dp) leading + LeadingGap else 0.dp
            val room = (width - (if (compact) PlateSideCompact else PlateSide) * 2 - sign - OneLineSlack).toPx()
            val least = words.copy(fontSize = TEXT_LEAST_SP.sp, lineHeight = (TEXT_LEAST_SP * LINE_HEIGHT).sp)
            room > 0f && measurer.measure(text, least, softWrap = false, maxLines = 1).size.width <= room
        }
    }
}
