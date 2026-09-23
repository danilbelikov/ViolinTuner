package com.violinjourney.app.feature.sound.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.R
import com.violinjourney.app.core.audio.fx.SoundMeters
import com.violinjourney.app.core.domain.sound.CompressorAmount
import com.violinjourney.app.core.domain.sound.EqBand
import com.violinjourney.app.core.domain.sound.ReverbSpace
import com.violinjourney.app.core.domain.sound.SoundBlock
import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.domain.sound.SoundParam
import com.violinjourney.app.core.domain.sound.SoundParams
import com.violinjourney.app.core.domain.sound.SoundSettings
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.sound.SoundFormats
import com.violinjourney.app.feature.sound.SoundIntent
import com.violinjourney.app.feature.sound.SoundReducer
import kotlin.math.exp

private val CardCorner = 20.dp
private val HeaderHeight = 60.dp
private val ChipHeight = 36.dp
private const val DISABLED_ALPHA = 0.38f
private const val EXPAND_MS = 250
private const val REDUCTION_FULL_DB = 20.0
private const val TABULAR_FIGURES = "tnum"

/** The four cards, in the order the signal passes them. */
@Composable
fun SoundBlocks(
    settings: SoundSettings,
    expanded: SoundBlock?,
    band: EqBand,
    details: Boolean,
    meters: State<SoundMeters?>,
    config: SoundConfig,
    onIntent: (SoundIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val bands = stringArrayResource(R.array.sound_band_names)
        BlockCard(
            block = SoundBlock.EQ, icon = AppIcons.Eq, title = stringResource(R.string.sound_block_eq), subtitle = null,
            summary = eqSummary(settings, bands), settings = settings, expanded = expanded, onIntent = onIntent,
        ) { on -> EqContent(settings, band, on, config, onIntent) }
        BlockCard(
            block = SoundBlock.COMPRESSOR, icon = AppIcons.Compressor, title = stringResource(R.string.sound_block_compressor),
            subtitle = stringResource(R.string.sound_block_compressor_sub),
            summary = settings.compressor.amount?.let { amountWord(it) } ?: SoundFormats.ratio(settings.compressor.ratio),
            settings = settings, expanded = expanded, onIntent = onIntent,
        ) { on -> CompressorContent(settings, details, on, meters, config, onIntent) }
        BlockCard(
            block = SoundBlock.REVERB, icon = AppIcons.Hall, title = stringResource(R.string.sound_block_reverb),
            subtitle = stringResource(R.string.sound_block_reverb_sub),
            summary = "${stringArrayResource(R.array.sound_space_names)[settings.reverb.space.ordinal]} · ${SoundFormats.value(SoundParam.REVERB_DECAY.unit, settings.reverb.decaySec)} · " +
                SoundFormats.value(SoundParam.REVERB_MIX.unit, settings.reverb.mix),
            settings = settings, expanded = expanded, onIntent = onIntent,
        ) { on -> ReverbContent(settings, on, config, onIntent) }
        BlockCard(
            block = SoundBlock.OUTPUT, icon = AppIcons.Volume, title = stringResource(R.string.sound_block_output), subtitle = null,
            summary = SoundFormats.decibels(settings.output.gainDb, signed = true), settings = settings, expanded = expanded, onIntent = onIntent,
        ) { on -> OutputContent(settings, on, meters, config, onIntent) }
    }
}

@Composable
private fun amountWord(amount: Double): String = stringResource(
    when {
        amount < (CompressorAmount.A_LITTLE + CompressorAmount.NOTICEABLY) / 2 -> R.string.sound_amount_little
        amount < (CompressorAmount.NOTICEABLY + CompressorAmount.A_LOT) / 2 -> R.string.sound_amount_noticeable
        else -> R.string.sound_amount_lot
    },
)

/** What the equalizer does, in a line: the bands that are off zero, by name. */
@Composable
private fun eqSummary(settings: SoundSettings, bands: Array<String>): String {
    val eq = settings.eq
    val moved = buildList {
        if (eq.lowCut.enabled) add("${bands[EqBand.LOW_CUT.ordinal]} ${SoundFormats.hertz(eq.lowCut.hz)}")
        listOf(EqBand.LOW to eq.low.gainDb, EqBand.BODY to eq.body.gainDb, EqBand.PRESENCE to eq.presence.gainDb, EqBand.AIR to eq.air.gainDb)
            .filter { it.second != 0.0 }
            .forEach { (band, db) -> add("${bands[band.ordinal]} ${SoundFormats.decibels(db, signed = true)}") }
    }
    return if (moved.isEmpty()) stringResource(R.string.sound_eq_flat) else moved.joinToString(" · ")
}

/**
 * A block: its switch, its name, what it is set to in a line, and — opened — its controls. A
 * block switched off does not fold; it dims: to bring it back is one touch. Opening one scrolls
 * it up to the mini player.
 */
@Composable
private fun BlockCard(
    block: SoundBlock,
    icon: ImageVector,
    title: String,
    subtitle: String?,
    summary: String,
    settings: SoundSettings,
    expanded: SoundBlock?,
    onIntent: (SoundIntent) -> Unit,
    content: @Composable (on: Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val on = SoundReducer.isOn(settings, block)
    val open = expanded == block
    val bring = remember { BringIntoViewRequester() }
    LaunchedEffect(open) { if (open) bring.bringIntoView() }
    val turn by animateFloatAsState(if (open) 180f else 0f, tween(EXPAND_MS, easing = FastOutSlowInEasing), label = "blockChevron")
    val switchLabel = stringResource(R.string.sound_block_switch, title)
    val foldLabel = stringResource(if (open) R.string.sound_block_collapse else R.string.sound_block_expand)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bring)
            .clip(RoundedCornerShape(CardCorner))
            .background(colors.surfaceContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(HeaderHeight)
                .clickable(onClickLabel = foldLabel, role = Role.Button) { onIntent(SoundIntent.BlockHeaderClicked(block)) }
                .padding(start = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Switch(
                checked = on,
                onCheckedChange = { onIntent(SoundIntent.BlockSwitched(block, it)) },
                modifier = Modifier.semantics { contentDescription = switchLabel },
                colors = SwitchDefaults.colors(checkedTrackColor = colors.primary, checkedThumbColor = colors.onPrimary),
            )
            AppIcon(icon, contentDescription = null, tint = if (on) colors.primary else colors.onSurfaceVariant, size = 18.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = colors.onSurface, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                // What the block is for goes before what it is set to — beside the title it did not fit («выравнивает громкость»).
                val state = if (on) summary else stringResource(R.string.sound_block_off)
                Text(
                    text = if (subtitle != null) "$subtitle · $state" else state,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontFeatureSettings = TABULAR_FIGURES),
                )
            }
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                AppIcon(AppIcons.ChevronDown, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.rotate(turn))
            }
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(tween(EXPAND_MS, easing = FastOutSlowInEasing)) + fadeIn(tween(EXPAND_MS)),
            exit = shrinkVertically(tween(EXPAND_MS, easing = FastOutSlowInEasing)) + fadeOut(tween(EXPAND_MS)),
        ) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) { content(on) }
        }
    }
}

@Composable
private fun EqContent(settings: SoundSettings, band: EqBand, on: Boolean, config: SoundConfig, onIntent: (SoundIntent) -> Unit) {
    val names = stringArrayResource(R.array.sound_band_names)
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
            Text(stringResource(R.string.sound_band_low_cut_switch), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), modifier = Modifier.weight(1f))
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
        Text(stringResource(R.string.sound_comp_text), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp))
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
        Text(stringResource(R.string.sound_comp_details), color = colors.primary, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp), modifier = Modifier.weight(1f))
        AppIcon(AppIcons.ChevronDown, contentDescription = null, tint = colors.primary, modifier = Modifier.rotate(if (details) 180f else 0f))
    }
    AnimatedVisibility(visible = details) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(SoundParam.COMP_THRESHOLD, SoundParam.COMP_RATIO, SoundParam.COMP_ATTACK, SoundParam.COMP_RELEASE, SoundParam.COMP_MAKEUP)
                .forEach { Slider(it, settings, on, config, onIntent) }
        }
    }
}

/** «Сейчас сжимает −4 дБ»: the bar grows from the right, the number moves in halves of a decibel and not too often. */
@Composable
private fun ReductionMeter(meters: State<SoundMeters?>, on: Boolean) {
    val colors = MaterialTheme.colorScheme
    val tint = ViolinTheme.soundColors.meterReduce
    var shown by remember { mutableDoubleStateOf(0.0) }
    LaunchedEffect(on) {
        while (true) {
            shown = if (on) meters.value?.reductionDb ?: 0.0 else 0.0
            kotlinx.coroutines.delay(100) // ten readings a second: a number, not a flicker
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.alpha(if (on) 1f else DISABLED_ALPHA)) {
        Text(stringResource(R.string.sound_comp_now), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp))
        Box(
            Modifier
                .weight(1f)
                .height(4.dp)
                .clearAndSetSemantics { }
                .drawBehind {
                    drawRoundRect(colors.surfaceContainerHigh, size = size, cornerRadius = CornerRadius(size.height / 2))
                    val share = ((if (on) meters.value?.reductionDb ?: 0.0 else 0.0) / REDUCTION_FULL_DB).toFloat().coerceIn(0f, 1f)
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
    Chips(labels = stringArrayResource(R.array.sound_space_names).toList(), selected = settings.reverb.space.ordinal) { onIntent(SoundIntent.SpaceSelected(ReverbSpace.entries[it])) }
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
            // The warning comes at once and leaves only after a second of calm: a line of text must not blink with the music.
            if (meters.value?.limiting == true) { hot = true; quietFor = 0 } else if (++quietFor > 10) hot = false
            kotlinx.coroutines.delay(100)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        AppIcon(AppIcons.Limiter, contentDescription = null, tint = if (hot) ViolinTheme.soundColors.meterLimit else colors.onSurfaceVariant, size = 18.dp)
        Text(
            text = stringResource(if (hot) R.string.sound_limiter_hot else R.string.sound_limiter_note),
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
            CompressorAmount.A_LITTLE.toFloat() to stringResource(R.string.sound_amount_little),
            CompressorAmount.NOTICEABLY.toFloat() to stringResource(R.string.sound_amount_noticeable),
            CompressorAmount.A_LOT.toFloat() to stringResource(R.string.sound_amount_lot),
        )
    } else {
        emptyList()
    }
    val text = when {
        value == null -> stringResource(R.string.sound_param_amount_own)
        // «Сколько» is read as what it does: the ratio it has led to
        param == SoundParam.COMP_AMOUNT -> SoundFormats.ratio(settings.compressor.ratio)
        else -> SoundFormats.value(param.unit, value)
    }
    ParamSlider(
        model = SliderModel(
            label = label, hint = hint, valueText = text,
            fraction = SoundParams.fractionOf(param, value ?: range.default, range),
            defaultFraction = SoundParams.fractionOf(param, range.default, range),
            bipolar = range.min < 0 && range.max > 0,
            marks = marks,
            detached = value == null,
        ),
        enabled = enabled,
        onFraction = { onIntent(SoundIntent.ParamChanged(param, SoundParams.valueAt(param, it, range))) },
        onStep = { onIntent(SoundIntent.ParamStepped(param, it)) },
        onReset = { onIntent(SoundIntent.ParamReset(param)) },
    )
}

@Composable
private fun wordsOf(param: SoundParam): Pair<String, String?> = when (param) {
    SoundParam.LOW_CUT_HZ, SoundParam.LOW_HZ, SoundParam.BODY_HZ, SoundParam.PRESENCE_HZ, SoundParam.AIR_HZ -> stringResource(R.string.sound_param_frequency) to null
    SoundParam.LOW_GAIN, SoundParam.BODY_GAIN, SoundParam.PRESENCE_GAIN, SoundParam.AIR_GAIN -> stringResource(R.string.sound_param_gain) to null
    SoundParam.BODY_Q, SoundParam.PRESENCE_Q -> stringResource(R.string.sound_param_width) to stringResource(R.string.sound_param_width_hint)
    SoundParam.COMP_AMOUNT -> stringResource(R.string.sound_param_amount) to null
    SoundParam.COMP_THRESHOLD -> stringResource(R.string.sound_param_threshold) to stringResource(R.string.sound_param_threshold_hint)
    SoundParam.COMP_RATIO -> stringResource(R.string.sound_param_ratio) to null
    SoundParam.COMP_ATTACK -> stringResource(R.string.sound_param_attack) to stringResource(R.string.sound_param_attack_hint)
    SoundParam.COMP_RELEASE -> stringResource(R.string.sound_param_release) to stringResource(R.string.sound_param_release_hint)
    SoundParam.COMP_MAKEUP -> stringResource(R.string.sound_param_makeup) to null
    SoundParam.REVERB_DECAY -> stringResource(R.string.sound_param_decay) to stringResource(R.string.sound_param_decay_hint)
    SoundParam.REVERB_PRE_DELAY -> stringResource(R.string.sound_param_pre_delay) to stringResource(R.string.sound_param_pre_delay_hint)
    SoundParam.REVERB_BRIGHTNESS -> stringResource(R.string.sound_param_brightness) to stringResource(R.string.sound_param_brightness_hint)
    SoundParam.REVERB_MIX -> stringResource(R.string.sound_param_mix) to stringResource(R.string.sound_param_mix_hint)
    SoundParam.OUTPUT_GAIN -> stringResource(R.string.sound_param_output) to null
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
