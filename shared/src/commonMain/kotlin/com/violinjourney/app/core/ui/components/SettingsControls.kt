package com.violinjourney.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.domain.TolerancePreset
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.a4_option_description
import com.violinjourney.app.shared.resources.tolerance_beginner_name
import com.violinjourney.app.shared.resources.tolerance_beginner_text
import com.violinjourney.app.shared.resources.tolerance_cents
import com.violinjourney.app.shared.resources.tolerance_cents_spoken_few
import com.violinjourney.app.shared.resources.tolerance_cents_spoken_many
import com.violinjourney.app.shared.resources.tolerance_cents_spoken_one
import com.violinjourney.app.shared.resources.tolerance_intermediate_name
import com.violinjourney.app.shared.resources.tolerance_intermediate_text
import com.violinjourney.app.shared.resources.tolerance_pro_name
import com.violinjourney.app.shared.resources.tolerance_pro_text
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// Handoff prototype: onboarding steps 2 and 3, reused by the settings screen.
private val SelectorHeight = 48.dp
private val SelectorCorner = 24.dp
private val SelectorBorder = 1.dp
private val PresetCorner = 16.dp
private val PresetBorder = 2.dp
private val PresetPaddingHorizontal = 15.dp
private val PresetPaddingVertical = 13.dp
private val PresetSpacing = 8.dp
private const val TABULAR_FIGURES = "tnum"

// The setup of the onboarding (spec 3.36.8, 5.29 R8): the buttons of the reference…
private val A4Gap = 8.dp
private val A4MinHeight = 64.dp
private val A4Corner = 14.dp
private val ChoiceBorder = 1.5.dp
private const val A4_NUMBER_SP = 20f

/** Where the number does not stand in its button (a narrow phone at the largest font), it steps down — at most this far. */
private const val A4_NUMBER_LEAST_SP = 14f

// …and the cards of the tolerance.
private val CardMinHeight = 76.dp
private val CardCorner = 18.dp
private val CardPaddingVertical = 12.dp
private val CardPaddingHorizontal = 16.dp
private val CardGap = 14.dp
private val CardsGap = 10.dp
private val Radio = 22.dp
private val RadioRing = 2.dp
private val RadioDot = 11.dp
private val BarHeight = 10.dp
private val BarUnderCaption = 6.dp

/** A card is laid out in whole pixels: a name that fits only by a hair is not trusted. */
private val CardSlack = 1.dp

/**
 * What a test of the cards measures, though a reader never meets it: the name and «±8 ц» of a card are silent (the card says them),
 * so they carry these tags and nothing else.
 */
internal const val TOLERANCE_NAME_TAG = "tolerance name"
internal const val TOLERANCE_NUMBER_TAG = "tolerance number"

/** The words of a caption wrap at these, never inside a word; a no-break space keeps its two sides together. */
private val CaptionBreaks = charArrayOf(' ', '\n')

/**
 * The two looks of a choice of the reference and of the tolerance (spec 3.36.8): [Onboarding] — the four buttons of 64 with «Гц» and
 * the three cards with a radio and the bar of the green zone of the setup; [Settings] — the look of «Настройки» (until stage 121 the
 * one they had).
 */
enum class ChoiceLook { Settings, Onboarding }

/** The choice of the A4 reference pitch in hertz, in the [look] of its screen. */
@Composable
fun A4Selector(
    optionsHz: List<Int>,
    selectedHz: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    look: ChoiceLook = ChoiceLook.Settings,
) {
    when (look) {
        ChoiceLook.Settings -> A4Segments(optionsHz, selectedHz, onSelect, modifier)
        ChoiceLook.Onboarding -> A4Buttons(optionsHz, selectedHz, onSelect, modifier)
    }
}

/** Segmented choice of the A4 reference pitch in hertz. */
@Composable
private fun A4Segments(
    optionsHz: List<Int>,
    selectedHz: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(SelectorCorner)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SelectorHeight)
            .clip(shape)
            .border(SelectorBorder, colors.outlineVariant, shape)
            .selectableGroup(),
    ) {
        optionsHz.forEachIndexed { index, hz ->
            if (index > 0) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(SelectorBorder)
                        .background(colors.outlineVariant),
                )
            }
            val selected = hz == selectedHz
            val description = stringResource(Res.string.a4_option_description, hz)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (selected) colors.primaryContainer else Color.Transparent)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(hz) })
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = hz.toString(),
                    color = if (selected) colors.onPrimaryContainer else colors.onSurface,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.sp,
                        fontFeatureSettings = TABULAR_FIGURES,
                    ),
                )
            }
        }
    }
}

/**
 * The four buttons of the reference in the setup (spec 3.36.8, 5.29 R8): one row, 8 apart, at least 64 high — they grow together with
 * the font; the number large and «Гц» under it in the unit of the language ([Formats]); the chosen one on the soft accent, framed in the
 * accent, its number in the accent. A reader hears «440 герц» and «выбрано», as before.
 */
@Composable
private fun A4Buttons(optionsHz: List<Int>, selectedHz: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(A4Corner)
    val unit = Formats.language.sound.hz
    val number = MaterialTheme.typography.titleLarge.copy(
        fontSize = A4_NUMBER_SP.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.ExtraBold,
        fontFeatureSettings = TABULAR_FIGURES,
    )
    val hertz = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(A4Gap),
    ) {
        optionsHz.forEach { hz ->
            val selected = hz == selectedHz
            val description = stringResource(Res.string.a4_option_description, hz)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = A4MinHeight)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(if (selected) ViolinTheme.accentSoft else colors.surfaceContainer)
                    .border(ChoiceBorder, if (selected) colors.primary else colors.outlineVariant, shape)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(hz) })
                    .semantics { contentDescription = description },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                OneLineText(
                    text = hz.toString(),
                    style = number,
                    minSp = A4_NUMBER_LEAST_SP,
                    modifier = Modifier.clearAndSetSemantics {},
                    color = if (selected) colors.primary else colors.onSurface,
                )
                Text(
                    text = unit,
                    modifier = Modifier.clearAndSetSemantics {},
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    style = hertz,
                )
            }
        }
    }
}

/** The choice of «Новичок ±12 / Средний ±8 / Профи ±3» — how wide the green zone is — in the [look] of its screen. */
@Composable
fun TolerancePresetList(
    selected: TolerancePreset,
    onSelect: (TolerancePreset) -> Unit,
    modifier: Modifier = Modifier,
    look: ChoiceLook = ChoiceLook.Settings,
) {
    when (look) {
        ChoiceLook.Settings -> Column(
            modifier = modifier
                .fillMaxWidth()
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(PresetSpacing),
        ) {
            TolerancePreset.entries.forEach { preset ->
                PresetCard(preset, selected = preset == selected, onClick = { onSelect(preset) })
            }
        }
        ChoiceLook.Onboarding -> ToleranceCards(selected, onSelect, modifier)
    }
}

@Composable
private fun PresetCard(preset: TolerancePreset, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(PresetCorner)
    val (nameRes, textRes) = wordsOf(preset)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) colors.surfaceContainer else Color.Transparent)
            .border(PresetBorder, if (selected) colors.primary else colors.outlineVariant, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = PresetPaddingHorizontal, vertical = PresetPaddingVertical),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(nameRes),
                color = colors.onSurface,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
            )
            Text(
                text = stringResource(textRes),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            )
        }
        Text(
            text = stringResource(Res.string.tolerance_cents, preset.cents),
            color = colors.onSurface,
            style = TextStyle(
                fontFamily = MaterialTheme.typography.titleLarge.fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                fontFeatureSettings = TABULAR_FIGURES,
            ),
        )
    }
}

private fun wordsOf(preset: TolerancePreset): Pair<StringResource, StringResource> = when (preset) {
    TolerancePreset.BEGINNER -> Res.string.tolerance_beginner_name to Res.string.tolerance_beginner_text
    TolerancePreset.INTERMEDIATE -> Res.string.tolerance_intermediate_name to Res.string.tolerance_intermediate_text
    TolerancePreset.PRO -> Res.string.tolerance_pro_name to Res.string.tolerance_pro_text
}

/**
 * The three cards of the tolerance in the setup (spec 3.36.8, 5.29 R8): a radio, the name and its caption, the bar of the green zone
 * — as wide as the zone of the card ([ToleranceBarMath]) — and «±8 ц» on the right; the whole card is pressed. Where the widest of
 * the three names does not stand on one line beside the bar (a phone of 360, a large font, fr «Intermédiaire», es and it
 * «Principiante», the column of words lying), the bars of all three go under their captions, at the start, as wide, and the names
 * step down together; where not even that keeps every word whole, «±8 ц» goes under the caption too ([ToleranceBarMath.fit]). The bar
 * never goes: for one who has not seen Live it is the explanation. A reader hears «Средний, чувствуется вибрато, плюс-минус 8 центов»
 * and «выбрано»; the bar is silent.
 */
@Composable
private fun ToleranceCards(selected: TolerancePreset, onSelect: (TolerancePreset) -> Unit, modifier: Modifier) {
    val names = TolerancePreset.entries.map { stringResource(wordsOf(it).first) }
    val captions = TolerancePreset.entries.map { stringResource(wordsOf(it).second) }
    val numbers = TolerancePreset.entries.map { stringResource(Res.string.tolerance_cents, it.cents) }
    val nameStyle = MaterialTheme.typography.titleMedium.copy(fontSize = ToleranceBarMath.NAME_SP.sp, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold)
    val numberStyle = MaterialTheme.typography.titleMedium.copy(
        fontSize = ToleranceBarMath.NAME_SP.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.ExtraBold,
        fontFeatureSettings = TABULAR_FIGURES,
    )
    val captionStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val fit = rememberCardsFit(maxWidth - CardPaddingHorizontal * 2, names, captions, numbers, nameStyle, numberStyle, captionStyle)
        Column(
            Modifier
                .fillMaxWidth()
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(CardsGap),
        ) {
            TolerancePreset.entries.forEachIndexed { index, preset ->
                ToleranceCard(
                    preset = preset,
                    words = CardWords(names[index], captions[index], numbers[index]),
                    selected = preset == selected,
                    layout = fit.layout,
                    styles = CardStyles(nameStyle.copy(fontSize = fit.nameSp.sp), captionStyle, numberStyle),
                    onClick = { onSelect(preset) },
                )
            }
        }
    }
}

/** The words of a card: its name, its caption and «±8 ц». */
private class CardWords(val name: String, val caption: String, val number: String)

/** The styles of a card: the name at the size the three share, the caption, the number. */
private class CardStyles(val name: TextStyle, val caption: TextStyle, val number: TextStyle)

/**
 * Measures the [names], the words of the [captions] and the [numbers] of the cards in a row [rowWidth] wide (the card less its fields)
 * and asks [ToleranceBarMath.fit] where the bars and the numbers stand and how large the names are. The styles are keys: on iOS Manrope
 * comes a frame after the first one.
 */
@Composable
private fun rememberCardsFit(
    rowWidth: Dp,
    names: List<String>,
    captions: List<String>,
    numbers: List<String>,
    nameStyle: TextStyle,
    numberStyle: TextStyle,
    captionStyle: TextStyle,
): CardsFit {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(rowWidth, names, captions, numbers, nameStyle, numberStyle, captionStyle, measurer, density) {
        with(density) {
            val parts = ToleranceBarMath.Parts(
                lead = (Radio + CardGap).toPx(),
                gap = CardGap.toPx(),
                bar = ToleranceBarMath.TRACK_DP.dp.toPx(),
                slack = CardSlack.toPx(),
            )
            ToleranceBarMath.fit(
                row = rowWidth.toPx(),
                parts = parts,
                widestNumber = numbers.maxOf { measurer.widthOf(it, numberStyle) },
                widestCaptionWord = captions.flatMap { it.split(*CaptionBreaks) }.filter { it.isNotEmpty() }.maxOf { measurer.widthOf(it, captionStyle) },
                widestNameAt = { sizeSp -> names.maxOf { measurer.widthOf(it, nameStyle.copy(fontSize = sizeSp.sp)) } },
            )
        }
    }
}

private fun TextMeasurer.widthOf(text: String, style: TextStyle): Float = measure(text, style, softWrap = false, maxLines = 1).size.width.toFloat()

@Composable
private fun ToleranceCard(
    preset: TolerancePreset,
    words: CardWords,
    selected: Boolean,
    layout: CardsLayout,
    styles: CardStyles,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(CardCorner)
    val spoken = stringResource(
        Formats.plural(preset.cents, Res.string.tolerance_cents_spoken_one, Res.string.tolerance_cents_spoken_few, Res.string.tolerance_cents_spoken_many),
        preset.cents,
    )
    val description = "${words.name}, ${words.caption}, $spoken"
    val number: @Composable () -> Unit = {
        Text(
            text = words.number,
            modifier = Modifier.clearAndSetSemantics { testTag = TOLERANCE_NUMBER_TAG },
            color = if (selected) colors.primary else colors.onSurface,
            maxLines = 1,
            softWrap = false,
            style = styles.number,
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = CardMinHeight)
            .clip(shape)
            .background(if (selected) ViolinTheme.accentSoft else colors.surfaceContainer)
            .border(ChoiceBorder, if (selected) colors.primary else colors.outlineVariant, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = CardPaddingHorizontal, vertical = CardPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioMark(selected)
        Spacer(Modifier.width(CardGap))
        // the parts are silent — the card says them
        Column(Modifier.weight(1f)) {
            Text(text = words.name, modifier = Modifier.clearAndSetSemantics { testTag = TOLERANCE_NAME_TAG }, color = colors.onSurface, style = styles.name)
            Text(text = words.caption, modifier = Modifier.clearAndSetSemantics {}, color = colors.onSurfaceVariant, style = styles.caption)
            when (layout) {
                CardsLayout.BAR_BESIDE -> Unit
                CardsLayout.BAR_UNDER -> ZoneBar(preset.cents, Modifier.padding(top = BarUnderCaption))
                CardsLayout.NUMBER_BESIDE_BAR -> Row(Modifier.padding(top = BarUnderCaption), verticalAlignment = Alignment.CenterVertically) {
                    ZoneBar(preset.cents)
                    Spacer(Modifier.width(CardGap))
                    number()
                }
                CardsLayout.NUMBER_UNDER_BAR -> {
                    ZoneBar(preset.cents, Modifier.padding(top = BarUnderCaption))
                    number()
                }
            }
        }
        if (layout == CardsLayout.BAR_BESIDE) {
            Spacer(Modifier.width(CardGap))
            ZoneBar(preset.cents)
        }
        if (layout.numberAtEnd) {
            Spacer(Modifier.width(CardGap))
            number()
        }
    }
}

/** The radio of a card: a ring of 2, in the accent with a dot of 11 when chosen. Seen only: the card says it is chosen. */
@Composable
private fun RadioMark(selected: Boolean) {
    val colors = MaterialTheme.colorScheme
    val ring = if (selected) colors.primary else colors.outlineVariant
    val dot = colors.primary
    Box(
        Modifier
            .size(Radio)
            .drawBehind {
                val stroke = RadioRing.toPx()
                drawCircle(ring, radius = (size.minDimension - stroke) / 2, style = Stroke(stroke))
                if (selected) drawCircle(dot, radius = RadioDot.toPx() / 2)
            },
    )
}

/**
 * The bar of the green zone (spec 3.36.8, 5.29 R8): a track of 84 × 10 on surfaceContainerHigh and in its middle the zone of [cents] in
 * the colour of «в строе» of Live — it explains Live, it does not judge; the width and the number say it as well.
 */
@Composable
private fun ZoneBar(cents: Int, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val zone = ViolinTheme.zoneColors.inTune
    Box(
        modifier
            .size(ToleranceBarMath.TRACK_DP.dp, BarHeight)
            .drawBehind {
                val radius = CornerRadius(size.height / 2)
                drawRoundRect(track, cornerRadius = radius)
                val width = ToleranceBarMath.segmentDp(cents).dp.toPx()
                drawRoundRect(zone, topLeft = Offset((size.width - width) / 2, 0f), size = Size(width, size.height), cornerRadius = radius)
            },
    )
}
