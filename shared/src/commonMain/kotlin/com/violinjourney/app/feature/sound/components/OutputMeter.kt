package com.violinjourney.app.feature.sound.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.audio.fx.SoundMeters
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.sound.SoundFormats
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.sound_meter_limiter
import com.violinjourney.app.shared.resources.sound_meter_none
import com.violinjourney.app.shared.resources.sound_meter_output
import org.jetbrains.compose.resources.stringResource

// The output meter of «Звук» (spec 3.17, 5.11; 5.29 R5: a line of 12 sp, 700, a bar of 6 at a corner of 3 on surface-2).
private val MeterBar = 6.dp
private val LimiterMark = 8.dp
private val LimiterMarkCorner = 2.dp
private val MeterGap = 6.dp

/**
 * The bar is never narrower than this: where the word «выход» would leave it less, the word gives way — the bar and the number stay
 * (Russian «ограничитель» at the font 1.3 in the column of 277 of a phone of 640 lying behind a cutout leaves the bar 20).
 */
private val MinBar = 16.dp

/** Text laid out in whole pixels: a room measured to the pixel is not trusted with the last letter. */
private val NumberSlack = 1.dp

/** The widest level the number says: the floor of the bar is −60 dBFS, so «−59,5 дБ» — tabular figures, as wide as any other. */
private const val WIDEST_LEVEL_DB = -59.5
private const val TABULAR_FIGURES = "tnum"

/**
 * The level at the output (spec 3.17, 5.11): «выход», the bar, the mark of the limiter and the number — up at once, the whole bar down
 * in ~300 ms, thirty frames a second on the postcards' grid and only while there is something to show ([runMeter]). The mark lights
 * for a moment when the limiter has had to work, and the number gives way to the word — «ограничитель», not "overload": there will be
 * none in the file, but too much was asked for. The number keeps the room of the widest thing it says, the word or the lowest level,
 * so the bar does not jump when one takes the place of the other. The bar and the number are read where they are drawn and where
 * they change: the readings recompose nothing but the number. In a narrow room — the line of the time of the compact panel — the word
 * «выход» gives way before the bar grows too short.
 */
@Composable
internal fun OutputMeter(meters: State<SoundMeters?>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val sound = ViolinTheme.soundColors
    val level = remember { mutableFloatStateOf(0f) }
    var lit by remember { mutableStateOf(false) }
    var number by remember { mutableStateOf<Double?>(null) }

    LaunchedEffect(Unit) {
        val motion = OutputMeterMotion()
        // asleep until sound plays through the chain
        runMeter(motion, reading = { meters.value }) {
            level.floatValue = motion.level
            lit = motion.lit
            number = motion.number
        }
    }

    val style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR_FIGURES)
    val label = stringResource(Res.string.sound_meter_output)
    val limiter = stringResource(Res.string.sound_meter_limiter)
    val none = stringResource(Res.string.sound_meter_none)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val widest = remember(limiter, style, measurer, density) {
        val words = measurer.measure(limiter, style, softWrap = false, maxLines = 1).size.width
        val lowest = measurer.measure(SoundFormats.decibels(WIDEST_LEVEL_DB, signed = true), style, softWrap = false, maxLines = 1).size.width
        with(density) { maxOf(words, lowest).toDp() } + NumberSlack
    }
    Layout(
        content = {
            // said by the meter itself: where the word gives way it is not seen, and still heard
            Text(label, modifier = Modifier.clearAndSetSemantics { }, color = colors.onSurfaceVariant, maxLines = 1, softWrap = false, style = style)
            Box(
                Modifier
                    .height(MeterBar)
                    .clearAndSetSemantics { }
                    .drawBehind {
                        val corner = CornerRadius(size.height / 2)
                        drawRoundRect(colors.surfaceContainerHigh, size = size, cornerRadius = corner)
                        drawRoundRect(sound.meterLevel, size = Size(size.width * level.floatValue, size.height), cornerRadius = corner)
                    },
            )
            Box(
                Modifier
                    .size(LimiterMark)
                    .background(if (lit) sound.meterLimit else colors.surfaceContainerHigh, RoundedCornerShape(LimiterMarkCorner)),
            )
            Text(
                text = when {
                    lit -> limiter
                    else -> number?.takeIf { it > OutputMeterMotion.FLOOR_DB }?.let { SoundFormats.decibels(it, signed = true) } ?: none
                },
                modifier = Modifier.width(widest),
                color = if (lit) sound.meterLimit else colors.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 1,
                softWrap = false,
                style = style,
            )
        },
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = label },
        measurePolicy = MeterPolicy,
    )
}

/** The pieces of [OutputMeter], in the order they are composed and stand. */
private const val METER_LABEL = 0
private const val METER_BAR = 1
private const val METER_MARK = 2
private const val METER_NUMBER = 3

/**
 * The line of the meter: the mark and the number keep their width; the bar takes what is left, and the word «выход» stands before it
 * only while the bar keeps [MinBar] beside it — otherwise the word is not placed, and TalkBack does not hear it. Its least width, asked
 * by the line of the time of the compact panel, is that of the line without the word.
 */
private object MeterPolicy : MeasurePolicy {
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val gap = MeterGap.roundToPx()
        val least = MinBar.roundToPx()
        val label = measurables[METER_LABEL].measure(loose)
        val mark = measurables[METER_MARK].measure(loose)
        val number = measurables[METER_NUMBER].measure(loose)
        val fixed = mark.width + number.width + gap * 2
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else fixed + least + label.width + gap
        val withLabel = width >= fixed + least + label.width + gap
        val barWidth = (width - fixed - if (withLabel) label.width + gap else 0).coerceAtLeast(0)
        val bar = measurables[METER_BAR].measure(Constraints.fixed(barWidth, MeterBar.roundToPx()))
        val height = constraints.constrainHeight(maxOf(label.height, number.height, mark.height, bar.height))
        return layout(width, height) {
            var x = 0
            if (withLabel) {
                label.placeRelative(x, (height - label.height) / 2)
                x += label.width + gap
            }
            bar.placeRelative(x, (height - bar.height) / 2)
            x += bar.width + gap
            mark.placeRelative(x, (height - mark.height) / 2)
            x += mark.width + gap
            number.placeRelative(x, (height - number.height) / 2)
        }
    }

    /** Without the word: the least bar, the mark and the number. */
    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        MinBar.roundToPx() + MeterGap.roundToPx() * 2 + measurables[METER_MARK].maxIntrinsicWidth(height) + measurables[METER_NUMBER].maxIntrinsicWidth(height)

    /** With the word before a bar of its least width. */
    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        minIntrinsicWidth(measurables, height) + MeterGap.roundToPx() + measurables[METER_LABEL].maxIntrinsicWidth(height)

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        measurables.maxOf { it.minIntrinsicHeight(width) }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        measurables.maxOf { it.maxIntrinsicHeight(width) }
}
