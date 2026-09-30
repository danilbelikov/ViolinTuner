package com.violinjourney.app.feature.sound.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.audio.fx.SoundMeters
import com.violinjourney.app.core.domain.sound.CompressorAmount
import com.violinjourney.app.core.domain.sound.EqBand
import com.violinjourney.app.core.domain.sound.ReverbSpace
import com.violinjourney.app.core.domain.sound.SoundBlock
import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.domain.sound.SoundParam
import com.violinjourney.app.core.domain.sound.SoundParams
import com.violinjourney.app.core.domain.sound.SoundSettings
import com.violinjourney.app.core.ui.components.ButtonFit
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.AppShapes
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.sound.SliderValues
import com.violinjourney.app.feature.sound.SoundCard
import com.violinjourney.app.feature.sound.SoundFormats
import com.violinjourney.app.feature.sound.SoundIntent
import com.violinjourney.app.feature.sound.SoundReducer
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.sound_amount_little
import com.violinjourney.app.shared.resources.sound_amount_lot
import com.violinjourney.app.shared.resources.sound_amount_noticeable
import com.violinjourney.app.shared.resources.sound_band_low_cut_switch
import com.violinjourney.app.shared.resources.sound_band_names
import com.violinjourney.app.shared.resources.sound_block_collapse
import com.violinjourney.app.shared.resources.sound_block_compressor
import com.violinjourney.app.shared.resources.sound_block_compressor_sub
import com.violinjourney.app.shared.resources.sound_block_eq
import com.violinjourney.app.shared.resources.sound_block_expand
import com.violinjourney.app.shared.resources.sound_block_off
import com.violinjourney.app.shared.resources.sound_block_output
import com.violinjourney.app.shared.resources.sound_block_reverb
import com.violinjourney.app.shared.resources.sound_block_reverb_sub
import com.violinjourney.app.shared.resources.sound_block_switch
import com.violinjourney.app.shared.resources.sound_comp_details
import com.violinjourney.app.shared.resources.sound_comp_now
import com.violinjourney.app.shared.resources.sound_comp_text
import com.violinjourney.app.shared.resources.sound_eq_flat
import com.violinjourney.app.shared.resources.sound_limiter_hot
import com.violinjourney.app.shared.resources.sound_limiter_note
import com.violinjourney.app.shared.resources.sound_param_amount
import com.violinjourney.app.shared.resources.sound_param_amount_own
import com.violinjourney.app.shared.resources.sound_param_attack
import com.violinjourney.app.shared.resources.sound_param_attack_hint
import com.violinjourney.app.shared.resources.sound_param_brightness
import com.violinjourney.app.shared.resources.sound_param_brightness_hint
import com.violinjourney.app.shared.resources.sound_param_decay
import com.violinjourney.app.shared.resources.sound_param_decay_hint
import com.violinjourney.app.shared.resources.sound_param_frequency
import com.violinjourney.app.shared.resources.sound_param_gain
import com.violinjourney.app.shared.resources.sound_param_makeup
import com.violinjourney.app.shared.resources.sound_param_mix
import com.violinjourney.app.shared.resources.sound_param_mix_hint
import com.violinjourney.app.shared.resources.sound_param_output
import com.violinjourney.app.shared.resources.sound_param_pre_delay
import com.violinjourney.app.shared.resources.sound_param_pre_delay_hint
import com.violinjourney.app.shared.resources.sound_param_ratio
import com.violinjourney.app.shared.resources.sound_param_release
import com.violinjourney.app.shared.resources.sound_param_release_hint
import com.violinjourney.app.shared.resources.sound_param_threshold
import com.violinjourney.app.shared.resources.sound_param_threshold_hint
import com.violinjourney.app.shared.resources.sound_param_width
import com.violinjourney.app.shared.resources.sound_param_width_hint
import com.violinjourney.app.shared.resources.sound_space_names
import kotlin.math.exp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

// The cards of «Звук» (spec 3.36.5, 5.29 R5; records.html 4).
private val HeaderMinHeight = 64.dp
private val HeaderStart = 16.dp
private val HeaderEnd = 12.dp
private val HeaderVertical = 8.dp
private val HeaderGap = 12.dp
private val NumberSize = 22.dp
private val ContentSide = 16.dp
private val ContentBottom = 16.dp
private val ContentGap = 12.dp
private val CardsGap = 10.dp
private val ChipHeight = 36.dp
private const val DISABLED_ALPHA = 0.38f
private const val EXPAND_MS = 250
private const val TURNED = 180f
private const val REDUCTION_FULL_DB = 20.0

/** The name of a card: 16 sp, 800; where its widest word does not stand whole in its room, a step smaller at a time down to 13. */
private const val TITLE_SP = 16f
private const val TITLE_MIN_SP = 13f
private const val TITLE_LINES = 2

/** The name is measured in whole pixels: a word that fits only by a hair is not trusted. */
private val TitleSlack = 1.dp

/** Between the parts of the short values of a card, as they are seen and as TalkBack hears them. */
private const val SUMMARY_SEPARATOR = " · "
private const val SAID_SEPARATOR = ", "

/** The limiter's line under «Громкость» looks at the readings ten times a second while the sound plays… */
private const val LIMITER_NOTE_STEP_MS = 100L

/** …and its warning goes out after a second of calm. */
private const val LIMITER_NOTE_CALM_STEPS = 10
private const val TABULAR_FIGURES = "tnum"

/**
 * The four cards of the chain, in the order the signal passes them, numbered 1–4 (spec 3.36.5): all closed when the screen opens,
 * any number of them open at once ([expanded]).
 */
@Composable
fun SoundBlocks(
    settings: SoundSettings,
    expanded: Set<SoundCard>,
    band: EqBand,
    details: Boolean,
    meters: State<SoundMeters?>,
    config: SoundConfig,
    onIntent: (SoundIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(CardsGap)) {
        val bands = stringArrayResource(Res.array.sound_band_names)
        BlockCard(
            block = SoundBlock.EQ, number = 1, title = stringResource(Res.string.sound_block_eq), subtitle = null,
            summary = eqSummary(settings, bands), settings = settings, expanded = expanded, onIntent = onIntent,
        ) { on -> EqContent(settings, band, on, config, onIntent) }
        BlockCard(
            block = SoundBlock.COMPRESSOR, number = 2, title = stringResource(Res.string.sound_block_compressor),
            subtitle = stringResource(Res.string.sound_block_compressor_sub),
            summary = listOf(settings.compressor.amount?.let { amountWord(it) } ?: SoundFormats.ratio(settings.compressor.ratio)),
            settings = settings, expanded = expanded, onIntent = onIntent,
        ) { on -> CompressorContent(settings, details, on, meters, config, onIntent) }
        BlockCard(
            block = SoundBlock.REVERB, number = 3, title = stringResource(Res.string.sound_block_reverb),
            subtitle = stringResource(Res.string.sound_block_reverb_sub),
            summary = listOf(
                stringArrayResource(Res.array.sound_space_names)[settings.reverb.space.ordinal],
                SoundFormats.value(SoundParam.REVERB_DECAY.unit, settings.reverb.decaySec),
                SoundFormats.value(SoundParam.REVERB_MIX.unit, settings.reverb.mix),
            ),
            settings = settings, expanded = expanded, onIntent = onIntent,
        ) { on -> ReverbContent(settings, on, config, onIntent) }
        BlockCard(
            block = SoundBlock.OUTPUT, number = 4, title = stringResource(Res.string.sound_block_output), subtitle = null,
            summary = listOf(SoundFormats.decibels(settings.output.gainDb, signed = true)), settings = settings, expanded = expanded, onIntent = onIntent,
        ) { on -> OutputContent(settings, on, meters, config, onIntent) }
    }
}

@Composable
private fun amountWord(amount: Double): String = stringResource(
    when {
        amount < (CompressorAmount.A_LITTLE + CompressorAmount.NOTICEABLY) / 2 -> Res.string.sound_amount_little
        amount < (CompressorAmount.NOTICEABLY + CompressorAmount.A_LOT) / 2 -> Res.string.sound_amount_noticeable
        else -> Res.string.sound_amount_lot
    },
)

/** What the equalizer does: the bands that are off zero, by name — «Гул 80 Гц», «Тело +2 дБ» — or «ровно». */
@Composable
private fun eqSummary(settings: SoundSettings, bands: List<String>): List<String> {
    val eq = settings.eq
    val moved = buildList {
        if (eq.lowCut.enabled) add("${bands[EqBand.LOW_CUT.ordinal]} ${SoundFormats.hertz(eq.lowCut.hz)}")
        listOf(EqBand.LOW to eq.low.gainDb, EqBand.BODY to eq.body.gainDb, EqBand.PRESENCE to eq.presence.gainDb, EqBand.AIR to eq.air.gainDb)
            .filter { it.second != 0.0 }
            .forEach { (band, db) -> add("${bands[band.ordinal]} ${SoundFormats.decibels(db, signed = true)}") }
    }
    return moved.ifEmpty { listOf(stringResource(Res.string.sound_eq_flat)) }
}

/**
 * A block of the chain as a card: its number, its name and — what it is for before what it is set to — its short values
 * («выравнивает громкость · заметно»), its switch and the chevron; opened, its controls. A block switched off neither folds nor hides:
 * its number, name and values go quieter in colour — «выключено» in place of the values — and one touch of its switch brings it back.
 */
@Composable
private fun BlockCard(
    block: SoundBlock,
    number: Int,
    title: String,
    subtitle: String?,
    summary: List<String>,
    settings: SoundSettings,
    expanded: Set<SoundCard>,
    onIntent: (SoundIntent) -> Unit,
    content: @Composable (on: Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val on = SoundReducer.isOn(settings, block)
    val card = SoundCard.of(block)
    val switchLabel = stringResource(Res.string.sound_block_switch, title)
    SoundCardFrame(
        number = number,
        title = title,
        summary = listOfNotNull(subtitle) + if (on) summary else listOf(stringResource(Res.string.sound_block_off)),
        open = card in expanded,
        onToggle = { onIntent(SoundIntent.CardToggled(card)) },
        on = on,
        switch = {
            Switch(
                checked = on,
                onCheckedChange = { onIntent(SoundIntent.BlockSwitched(block, it)) },
                modifier = Modifier.semantics { contentDescription = switchLabel },
                colors = SwitchDefaults.colors(checkedTrackColor = colors.primary, checkedThumbColor = colors.onPrimary),
            )
        },
    ) { content(on) }
}

/**
 * The frame of a card of «Звук» (spec 3.36.5, 5.29 R5): on the colour of a card at a corner of 18; its header of 64 at the least —
 * the number in a circle of 22 on the ground of the screen, the name (16 sp, 800) and under it the short values in one line that
 * ends in an ellipsis ([summary], its parts apart by «·»), the [switch] of a block and the chevron of 24 that turns when it opens;
 * opened ([open]), its [content] under the header. Not [on] — the number, the name and the values in the third level of text,
 * without a transparency. [onToggle] null — a card that does not open and has no chevron («Минусовка» that could not be prepared),
 * [note] its sentence under the name in place of the values, in as many lines as it takes.
 *
 * TalkBack hears the header as one button — «1, Эквалайзер, Гул 80 Гц, Тело +2 дБ» and «Развернуть» / «Свернуть» — and the switch as
 * one of its own, as before. Opening a card brings it into sight over the player once it has unfolded — the whole card, or its top
 * where it is taller than the room; a card already open when the screen is made again (a turn of the phone) is left where the scroll
 * was.
 */
@Composable
internal fun SoundCardFrame(
    number: Int,
    title: String,
    summary: List<String>,
    open: Boolean,
    onToggle: (() -> Unit)?,
    modifier: Modifier = Modifier,
    on: Boolean = true,
    switch: (@Composable () -> Unit)? = null,
    note: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val quiet = ViolinTheme.textTertiary
    val words = if (on) colors.onSurface else quiet
    val values = if (on) colors.onSurfaceVariant else quiet
    val bring = remember { BringIntoViewRequester() }
    // brought into sight only as it opens, not when the screen is made again with it open
    val wasOpen = remember { OpenMemory(open) }
    val extent = remember { CardExtent() }
    val unfolding = remember { MutableTransitionState(open) }
    unfolding.targetState = open
    LaunchedEffect(open) {
        val opening = open && !wasOpen.open
        wasOpen.open = open
        if (!opening) return@LaunchedEffect
        // The card as it stands unfolded, asked once it has unfolded. While it unfolds the end of the scroll is not there yet: the
        // last card, opened at the very bottom, leaves the scroll nothing to move by, and a scroll that cannot move gives the request
        // up — the card stayed under the player. The folded card — its header alone — would ask for nothing while the header is in sight.
        snapshotFlow { unfolding.isIdle && unfolding.currentState }.first { it }
        bring.bringIntoView(Rect(0f, 0f, extent.width.toFloat(), (extent.header + extent.content).toFloat()))
    }
    val turn by animateFloatAsState(if (open) TURNED else 0f, tween(EXPAND_MS, easing = FastOutSlowInEasing), label = "cardChevron")
    val foldLabel = stringResource(if (open) Res.string.sound_block_collapse else Res.string.sound_block_expand)
    val said = (listOf(number.toString(), title) + listOfNotNull(note) + summary.takeIf { note == null }.orEmpty()).joinToString(SAID_SEPARATOR)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bring)
            .clip(AppShapes.M)
            .background(colors.surfaceContainer),
    ) {
        Row(
            modifier = Modifier
                .onSizeChanged {
                    extent.width = it.width
                    extent.header = it.height
                }
                .fillMaxWidth()
                .heightIn(min = HeaderMinHeight)
                .then(if (onToggle != null) Modifier.clickable(onClickLabel = foldLabel, role = Role.Button, onClick = onToggle) else Modifier)
                .padding(start = HeaderStart, end = HeaderEnd, top = HeaderVertical, bottom = HeaderVertical),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HeaderGap),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clearAndSetSemantics { contentDescription = said },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HeaderGap),
            ) {
                Box(Modifier.size(NumberSize).background(colors.surface, CircleShape), contentAlignment = Alignment.Center) {
                    Text(
                        text = number.toString(),
                        color = if (on) colors.onSurfaceVariant else quiet,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = TABULAR_FIGURES),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    CardTitle(title, words)
                    if (note != null) {
                        Text(note, color = values, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp))
                    } else {
                        Text(
                            text = summary.joinToString(SUMMARY_SEPARATOR),
                            color = values,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = TABULAR_FIGURES),
                        )
                    }
                }
            }
            switch?.invoke()
            if (onToggle != null) {
                AppIcon(AppIcons.ChevronDown, contentDescription = null, tint = quiet, modifier = Modifier.rotate(turn))
            }
        }
        AnimatedVisibility(
            visibleState = unfolding,
            enter = expandVertically(tween(EXPAND_MS, easing = FastOutSlowInEasing)) + fadeIn(tween(EXPAND_MS)),
            exit = shrinkVertically(tween(EXPAND_MS, easing = FastOutSlowInEasing)) + fadeOut(tween(EXPAND_MS)),
        ) {
            Column(
                // measured at its whole height from its first frame: the unfolding clips it, it does not squeeze it
                modifier = Modifier
                    .onSizeChanged { extent.content = it.height }
                    .padding(start = ContentSide, end = ContentSide, bottom = ContentBottom),
                verticalArrangement = Arrangement.spacedBy(ContentGap),
                content = content,
            )
        }
    }
}

/** Whether a card was open the last time it was looked at: brought into sight only as it opens. */
private class OpenMemory(var open: Boolean)

/** A card as it was last laid out, in pixels: its width, the height of its header and that of its content unfolded. */
private class CardExtent {
    var width = 0
    var header = 0
    var content = 0
}

/**
 * The name of a card, 16 sp / 800 in up to two lines — a name of two words breaks at its space; one whose widest word does not stand
 * whole in the room (the narrow column of a phone lying, a large font) steps down together to 13 sp ([ButtonFit]).
 */
@Composable
private fun CardTitle(title: String, color: Color) {
    val style = MaterialTheme.typography.titleSmall.copy(fontSize = TITLE_SP.sp, lineHeight = 20.sp, fontWeight = FontWeight.ExtraBold)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints {
        val room = constraints.maxWidth.toFloat() - with(density) { TitleSlack.toPx() }
        val sizeSp = remember(title, room, style, measurer) {
            val words = title.split(' ').filter { it.isNotEmpty() }
            ButtonFit.size(room, TITLE_SP, TITLE_MIN_SP) { sp ->
                words.maxOfOrNull { measurer.measure(it, style.copy(fontSize = sp.sp), softWrap = false, maxLines = 1).size.width.toFloat() } ?: 0f
            }
        }
        Text(
            text = title,
            color = color,
            maxLines = TITLE_LINES,
            overflow = TextOverflow.Ellipsis,
            style = if (sizeSp == TITLE_SP) style else style.copy(fontSize = sizeSp.sp),
        )
    }
}

@Composable
private fun EqContent(settings: SoundSettings, band: EqBand, on: Boolean, config: SoundConfig, onIntent: (SoundIntent) -> Unit) {
    val names = stringArrayResource(Res.array.sound_band_names)
    EqCurveView(
        eq = settings.eq,
        points = EqBand.entries.map { SoundReducer.pointOf(settings, it).let { (hz, db) -> Triple(it, hz, db) } },
        selected = band,
        enabled = on,
        config = config,
        onDrag = { dragged, hz, db -> onIntent(SoundIntent.BandDragged(dragged, hz, db)) },
        onSelect = { onIntent(SoundIntent.BandSelected(it)) },
    )
    Chips(labels = names.toList(), selected = band.ordinal) { onIntent(SoundIntent.BandSelected(EqBand.entries[it])) }
    if (band == EqBand.LOW_CUT) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.sound_band_low_cut_switch), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), modifier = Modifier.weight(1f))
            Switch(checked = settings.eq.lowCut.enabled, enabled = on, onCheckedChange = { onIntent(SoundIntent.LowCutSwitched(it)) })
        }
    }
    val bandOn = on && (band != EqBand.LOW_CUT || settings.eq.lowCut.enabled)
    SoundParam.ofBand(band).forEach { Slider(it, settings, bandOn, config, onIntent) }
}

@Composable
private fun CompressorContent(settings: SoundSettings, details: Boolean, on: Boolean, meters: State<SoundMeters?>, config: SoundConfig, onIntent: (SoundIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val sound = ViolinTheme.soundColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Six columns as played, six as they come out: the quiet ones taller, the loud ones lower.
        Canvas(Modifier.size(width = 72.dp, height = 40.dp).clearAndSetSemantics { }) {
            val before = floatArrayOf(0.25f, 0.9f, 0.4f, 1f, 0.3f, 0.7f)
            val after = floatArrayOf(0.5f, 0.8f, 0.6f, 0.85f, 0.55f, 0.75f)
            val bar = 3.dp.toPx()
            val gap = 2.dp.toPx()
            fun columns(levels: FloatArray, startX: Float, color: Color) = levels.forEachIndexed { index, level ->
                val height = size.height * level
                drawRoundRect(color, Offset(startX + index * (bar + gap), size.height - height), Size(bar, height), CornerRadius(bar / 2))
            }
            columns(before, 0f, colors.onSurfaceVariant)
            columns(after, size.width - 6 * (bar + gap) + gap, sound.meterReduce)
        }
        Text(stringResource(Res.string.sound_comp_text), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp))
    }
    Slider(SoundParam.COMP_AMOUNT, settings, on, config, onIntent)
    ReductionMeter(meters, on)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button) { onIntent(SoundIntent.DetailsClicked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(Res.string.sound_comp_details), color = colors.primary, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp), modifier = Modifier.weight(1f))
        AppIcon(AppIcons.ChevronDown, contentDescription = null, tint = colors.primary, modifier = Modifier.rotate(if (details) 180f else 0f))
    }
    AnimatedVisibility(visible = details) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(SoundParam.COMP_THRESHOLD, SoundParam.COMP_RATIO, SoundParam.COMP_ATTACK, SoundParam.COMP_RELEASE, SoundParam.COMP_MAKEUP)
                .forEach { Slider(it, settings, on, config, onIntent) }
        }
    }
}

/**
 * «Сейчас сжимает −4 дБ»: the bar grows from the right at once and falls in ~300 ms, thirty frames a second while the
 * sound plays and asleep otherwise (spec 5.11, [runMeter]); the number moves in halves of a decibel and not too often.
 */
@Composable
private fun ReductionMeter(meters: State<SoundMeters?>, on: Boolean) {
    val colors = MaterialTheme.colorScheme
    val tint = ViolinTheme.soundColors.meterReduce
    val level = remember { mutableFloatStateOf(0f) }
    var shown by remember { mutableDoubleStateOf(0.0) }
    LaunchedEffect(on) {
        val motion = ReductionMeterMotion(REDUCTION_FULL_DB)
        runMeter(motion, reading = { if (on) meters.value?.reductionDb else null }) {
            level.floatValue = motion.level
            shown = motion.number
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.alpha(if (on) 1f else DISABLED_ALPHA)) {
        Text(stringResource(Res.string.sound_comp_now), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp))
        Box(
            Modifier
                .weight(1f)
                .height(4.dp)
                .clearAndSetSemantics { }
                .drawBehind {
                    drawRoundRect(colors.surfaceContainerHigh, size = size, cornerRadius = CornerRadius(size.height / 2))
                    val share = level.floatValue
                    drawRoundRect(tint, Offset(size.width * (1 - share), 0f), Size(size.width * share, size.height), CornerRadius(size.height / 2))
                },
        )
        Text(
            text = SoundFormats.decibels(-shown, signed = true),
            color = tint,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR_FIGURES),
        )
    }
}

@Composable
private fun ReverbContent(settings: SoundSettings, on: Boolean, config: SoundConfig, onIntent: (SoundIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val tint = ViolinTheme.soundColors.meterReduce
    Chips(labels = stringArrayResource(Res.array.sound_space_names).toList(), selected = settings.reverb.space.ordinal) { onIntent(SoundIntent.SpaceSelected(ReverbSpace.entries[it])) }
    // How the tail falls away: the longer it is asked to be, the slower the columns sink.
    val reach = (settings.reverb.decaySec / config.cathedralDecaySec.max).toFloat()
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .alpha(if (on) 1f else DISABLED_ALPHA)
            .clearAndSetSemantics { },
    ) {
        val columns = 34
        val step = size.width / columns
        for (index in 0 until columns) {
            val level = exp(-index / (columns * (0.12f + 0.5f * reach)))
            val height = (size.height * level).coerceAtLeast(1.dp.toPx())
            drawRoundRect(tint.copy(alpha = 0.35f + 0.65f * level), Offset(index * step, size.height - height), Size(step * 0.6f, height), CornerRadius(step * 0.3f))
        }
    }
    listOf(SoundParam.REVERB_DECAY, SoundParam.REVERB_PRE_DELAY, SoundParam.REVERB_BRIGHTNESS, SoundParam.REVERB_MIX).forEach { Slider(it, settings, on, config, onIntent) }
}

@Composable
private fun OutputContent(settings: SoundSettings, on: Boolean, meters: State<SoundMeters?>, config: SoundConfig, onIntent: (SoundIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Slider(SoundParam.OUTPUT_GAIN, settings, on, config, onIntent)
    var hot by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        var quietFor = 0
        while (true) {
            // asleep while nothing plays and the warning is out: no polling for as long as the block is open
            if (!hot && meters.value == null) snapshotFlow { meters.value }.first { it != null }
            // The warning comes at once and leaves only after a second of calm: a line of text must not blink with the music.
            if (meters.value?.limiting == true) {
                hot = true
                quietFor = 0
            } else if (++quietFor > LIMITER_NOTE_CALM_STEPS) {
                hot = false
            }
            delay(LIMITER_NOTE_STEP_MS)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        AppIcon(AppIcons.Limiter, contentDescription = null, tint = if (hot) ViolinTheme.soundColors.meterLimit else colors.onSurfaceVariant, size = 18.dp)
        Text(
            text = stringResource(if (hot) Res.string.sound_limiter_hot else Res.string.sound_limiter_note),
            color = if (hot) ViolinTheme.soundColors.meterLimit else colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
        )
    }
}

/** One parameter as the one slider: its words come from here, its numbers from [SoundParams]. */
@Composable
private fun Slider(param: SoundParam, settings: SoundSettings, enabled: Boolean, config: SoundConfig, onIntent: (SoundIntent) -> Unit) {
    val range = SoundParams.range(param, settings, config)
    val value = SoundParams.get(param, settings)
    val (label, hint) = wordsOf(param)
    val marks = if (param == SoundParam.COMP_AMOUNT) {
        listOf(
            CompressorAmount.A_LITTLE.toFloat() to stringResource(Res.string.sound_amount_little),
            CompressorAmount.NOTICEABLY.toFloat() to stringResource(Res.string.sound_amount_noticeable),
            CompressorAmount.A_LOT.toFloat() to stringResource(Res.string.sound_amount_lot),
        )
    } else {
        emptyList()
    }
    val own = stringResource(Res.string.sound_param_amount_own)
    val text = when {
        value == null -> own
        // «Сколько» is read as what it does: the ratio it has led to
        param == SoundParam.COMP_AMOUNT -> SoundFormats.ratio(settings.compressor.ratio)
        else -> SoundFormats.value(param.unit, value)
    }
    // the room the value keeps whatever it says (5.29 R5): «Сколько» says a ratio or «своё»
    val reserve = if (param == SoundParam.COMP_AMOUNT) SliderValues.of(SoundParam.COMP_RATIO, config.ratio) + own else SliderValues.of(param, range)
    ParamSlider(
        model = SliderModel(
            label = label, hint = hint, valueText = text,
            fraction = SoundParams.fractionOf(param, value ?: range.default, range),
            defaultFraction = SoundParams.fractionOf(param, range.default, range),
            bipolar = range.min < 0 && range.max > 0,
            marks = marks,
            detached = value == null,
            valueReserve = reserve,
        ),
        enabled = enabled,
        onFraction = { onIntent(SoundIntent.ParamChanged(param, SoundParams.valueAt(param, it, range))) },
        onStep = { onIntent(SoundIntent.ParamStepped(param, it)) },
        onReset = { onIntent(SoundIntent.ParamReset(param)) },
    )
}

@Composable
private fun wordsOf(param: SoundParam): Pair<String, String?> = when (param) {
    SoundParam.LOW_CUT_HZ, SoundParam.LOW_HZ, SoundParam.BODY_HZ, SoundParam.PRESENCE_HZ, SoundParam.AIR_HZ -> stringResource(Res.string.sound_param_frequency) to null
    SoundParam.LOW_GAIN, SoundParam.BODY_GAIN, SoundParam.PRESENCE_GAIN, SoundParam.AIR_GAIN -> stringResource(Res.string.sound_param_gain) to null
    SoundParam.BODY_Q, SoundParam.PRESENCE_Q -> stringResource(Res.string.sound_param_width) to stringResource(Res.string.sound_param_width_hint)
    SoundParam.COMP_AMOUNT -> stringResource(Res.string.sound_param_amount) to null
    SoundParam.COMP_THRESHOLD -> stringResource(Res.string.sound_param_threshold) to stringResource(Res.string.sound_param_threshold_hint)
    SoundParam.COMP_RATIO -> stringResource(Res.string.sound_param_ratio) to null
    SoundParam.COMP_ATTACK -> stringResource(Res.string.sound_param_attack) to stringResource(Res.string.sound_param_attack_hint)
    SoundParam.COMP_RELEASE -> stringResource(Res.string.sound_param_release) to stringResource(Res.string.sound_param_release_hint)
    SoundParam.COMP_MAKEUP -> stringResource(Res.string.sound_param_makeup) to null
    SoundParam.REVERB_DECAY -> stringResource(Res.string.sound_param_decay) to stringResource(Res.string.sound_param_decay_hint)
    SoundParam.REVERB_PRE_DELAY -> stringResource(Res.string.sound_param_pre_delay) to stringResource(Res.string.sound_param_pre_delay_hint)
    SoundParam.REVERB_BRIGHTNESS -> stringResource(Res.string.sound_param_brightness) to stringResource(Res.string.sound_param_brightness_hint)
    SoundParam.REVERB_MIX -> stringResource(Res.string.sound_param_mix) to stringResource(Res.string.sound_param_mix_hint)
    SoundParam.OUTPUT_GAIN -> stringResource(Res.string.sound_param_output) to null
}

/** A row of chips where one is chosen: the bands of the equalizer, the spaces of the hall. */
@Composable
private fun Chips(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label ->
            val chosen = index == selected
            Box(
                modifier = Modifier
                    .height(ChipHeight)
                    .clip(RoundedCornerShape(ChipHeight / 2))
                    .background(if (chosen) colors.primaryContainer else Color.Transparent)
                    .border(1.dp, if (chosen) colors.primaryContainer else colors.outlineVariant, RoundedCornerShape(ChipHeight / 2))
                    .selectable(selected = chosen, role = Role.RadioButton) { onSelect(index) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (chosen) colors.onPrimaryContainer else colors.onSurface, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
            }
        }
    }
}
