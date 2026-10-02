package com.violinjourney.app.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.theme.AppShapes

private const val SWITCH_MS = 200
private const val LINE_HEIGHT = 1.15f
private const val DISABLED_ALPHA = 0.38f
private const val TABULAR_FIGURES = "tnum"

/** The regular switch (spec 5.29): a container of 52, the pills of 44 inside it — 4 from its edges, 4 between them. */
private val RegularHeight = 52.dp
private val RegularEdge = 4.dp
private val RegularBetween = 2.dp

/** The compact one, for the A/B of a player in a low window: 28 seen, 48 pressed (at least); pills of 24, 2 from the edges. */
private val CompactTouch = 48.dp
private val CompactHeight = 28.dp
private val CompactEdge = 2.dp
private val CompactBetween = 1.dp
private val CompactCorner = 14.dp

/** A label that does not stand in one line of its size goes on two lines of this size in the same segment (spec 5.29 R5). */
private const val SHRUNK_SP = 12f

/** «A» / «B» before the words of a segment stand a step larger and heavier than them — 15 sp, 800 by words of 14 (spec 5.29 R5). */
private const val PREFIX_STEP_SP = 1

/** Between a prefix and its words: an en space — about the 6 dp of the mockup, and a place to go on to a second line at. */
private const val PREFIX_GAP = "\u2002"

/**
 * The one segmented switch of the app (spec 3.36.1, 5.29; components.html `.seg`): the chosen segment is a pill in the accent, no
 * frame and no dividers; the whole height of the container is pressed, not only the pill, and the ripple starts under the finger.
 * A label that does not fit goes on a second line; a large font makes the container and its pills taller rather than cut that line
 * (52 and 44 are the least), and only a third line — a very large font breaking a word — ends in an ellipsis. Two switches are not
 * this one: «Игра | Настройка» of Live and the switches over pictures (the house, a stop of the journey). [compact] is for a player
 * in a window lower than 700 dp — its A/B and «С минусовкой | Только скрипка» (spec 3.36.5): 28 dp to see, at least 48 to press.
 * [fontSize] is larger for segments that are a single sign, like ♭ ♮ ♯.
 *
 * [wholeWords] — no label breaks inside a word (the status of an element, spec 3.36.4): equal shares while each holds its widest
 * word, otherwise the segments share the row by their words, and where even that is too narrow the labels step down together
 * ([SegmentFit]). [byWords] — by the words always, not equally: the status in the narrow left column lying, where «В репертуаре»
 * would not stand in a third (spec 3.36.4). Both measure the labels and need a bounded width. [description] — the name of the group
 * for TalkBack («Статус»), on the group itself.
 *
 * [containerColor] — the ground of the container where surfaceContainer would not stand out: the ground of the screen in a sheet
 * («Тональность», 5.29 R4) and on the panel of a player (5.29 R5). [enabled] false — the whole switch sleeps: 0.38 with its ground,
 * no segment answers, TalkBack says «недоступно» (the sign and the mode without a tonic, the key of a scale in an edit, the A/B of a
 * player while its backing is made); the reason is its caller's line near it. [segmentEnabled] — one segment sleeps alone at 0.38
 * while the others answer (the octave that would leave the violin).
 *
 * The player of a recording and of «Звук» (spec 3.36.5, 5.29 R5): [strong] — the words at 800, not 700; [prefixes] — «A» / «B» a
 * step larger and heavier before the words («A оригинал | B обработка»); [segmentDescriptions] — what TalkBack says of each segment
 * instead of its label («A, оригинал»); [onHold] — a finger held on a segment: `(index, true)` once the press has lasted as long as a
 * long press, `(index, false)` when that finger lets go or its gesture is cut short («пока держишь» of A, spec 3.17) — a tap still
 * selects, and TalkBack's activation selects too, the hold being a gesture of the finger only; [shrinkToTwoLines] — where a label
 * does not stand in one line of [fontSize] in its equal share, all the labels go on up to two lines of 12 sp in the same height,
 * rather than one of them growing the switch; where a word would not stand whole even so (Spanish «acompañamiento» at a large
 * font), the segments share the row by their words and only then step smaller ([SegmentFit]) — a word is never broken by the
 * letter. The [compact] switch holds one line in its pills of 24 — a line of its line height, not of the font's taller ascent and
 * descent ([ExactLines]: «A» of 14 sp at the font 1.3 stands in 21.6 dp, not 26.3); two lines of 12 would grow it past 28: its labels
 * go down to 12 sp in one line, by their lines where equal halves do not hold them, and a step smaller where even that does not
 * ([SegmentFit.oneLine]: «С минусовкой | Только скрипка» in the narrow column lying at the font 1.3); only where not even 11.5 sp
 * holds them in one line do they go on two, the switch growing, rather than break a word — as it grows where its line itself is
 * taller than 24 (14 sp at Android's next step of font, 1.5: 25.5 dp). [byWords] and [wholeWords] have their own way of fitting and
 * take no [prefixes] and no [shrinkToTwoLines]. [strong] words take tabular figures as well: the numbers of the reference of
 * «Настройки» (spec 5.29 R8) — a label without figures looks as before.
 *
 * [sublabels] — a second line under the words of each segment, 12 sp, 700, in tabular figures and the colour of the words: «Новичок»
 * over «±12 ц», the tolerance of «Настройки» (spec 3.36.8, 5.29 R8). The words of all the segments then take one size, 14 sp or down
 * to 12 together, and where even that does not hold one in its equal share the segments share the row by their words — a word is not
 * broken ([SegmentLabelSize]); where a large font takes the words under 12, their second line goes down with them — a number is never
 * larger than its word. The two
 * lines keep to their line height ([ExactLines]), so that they stand in the pill of 44 at the font 1.3 as well. A regular switch
 * only: it takes no [compact], [byWords], [wholeWords], [prefixes] and [shrinkToTwoLines].
 */
@Composable
fun SegmentedSwitch(
    labels: List<String>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    fontSize: Int = 14,
    byWords: Boolean = false,
    wholeWords: Boolean = false,
    description: String? = null,
    containerColor: Color = Color.Unspecified,
    enabled: Boolean = true,
    segmentEnabled: (Int) -> Boolean = { true },
    strong: Boolean = false,
    prefixes: List<String>? = null,
    segmentDescriptions: List<String>? = null,
    onHold: ((index: Int, held: Boolean) -> Unit)? = null,
    shrinkToTwoLines: Boolean = false,
    sublabels: List<String>? = null,
) {
    val looks = SwitchLooks(containerColor, enabled, segmentEnabled, segmentDescriptions, onHold)
    if (sublabels != null) {
        TwoLineSwitch(labels, sublabels, selectedIndex, onSelect, modifier, fontSize.toFloat(), strong, description, looks)
        return
    }
    val labelStyle = labelStyleOf(fontSize.toFloat(), strong, compact)
    val fits = byWords || wholeWords
    if (!fits && !shrinkToTwoLines) {
        SwitchRow(textsOf(labels, prefixes, fontSize.toFloat()), selectedIndex, onSelect, modifier, compact, labelStyle, weights = null, description, looks)
        return
    }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val room = constraints.maxWidth
        if (!fits) {
            val plan = remember(labels, prefixes, labelStyle, measurer, density, room, compact) {
                val around = labels.indices.map { index -> with(density) { labelRoom(index, labels.lastIndex, compact).toPx() } }
                val slack = with(density) { SegmentSlack.toPx() }
                val lines = textsOf(labels, prefixes, fontSize.toFloat()).map { measurer.lineWidth(it, labelStyle) }
                val styleAt = { sizeSp: Float -> labelStyle.copy(fontSize = sizeSp.sp, lineHeight = (sizeSp * LINE_HEIGHT).sp) }
                if (!SegmentFit.shrinks(room.toFloat(), around, slack, lines)) {
                    SegmentFit.Plan(fontSize.toFloat(), List(labels.size) { room.toFloat() / labels.size })
                } else {
                    // the compact pills of 24 hold one line: 12 sp or a step smaller in one line, by the lines where equal halves do
                    // not hold them — a second line would grow the switch past its 28 (spec 5.29 R5)
                    val oneLine = if (compact) {
                        SegmentFit.oneLine(room.toFloat(), around, slack, maxSp = SHRUNK_SP) { sizeSp ->
                            textsOf(labels, prefixes, sizeSp).map { measurer.lineWidth(it, styleAt(sizeSp)) }
                        }
                    } else {
                        null
                    }
                    // two lines of 12 in equal halves while each word stands whole; else by the words, and smaller only where even
                    // that breaks one — a word is never broken by the letter (SegmentFit)
                    oneLine ?: SegmentFit.plan(room.toFloat(), around, slack, maxSp = SHRUNK_SP, share = SegmentFit.Share.Equal) { sizeSp ->
                        textsOf(labels, prefixes, sizeSp).map { text ->
                            SegmentFit.Label(
                                line = measurer.lineWidth(text, styleAt(sizeSp)),
                                word = text.text.split(' ', '\n', '\t', PREFIX_GAP.single()).filter { it.isNotEmpty() }
                                    .maxOfOrNull { measurer.lineWidth(AnnotatedString(it), styleAt(sizeSp)) } ?: 0f,
                            )
                        }
                    }
                }
            }
            val style = if (plan.sizeSp == fontSize.toFloat()) labelStyle else labelStyle.copy(fontSize = plan.sizeSp.sp, lineHeight = (plan.sizeSp * LINE_HEIGHT).sp)
            SwitchRow(
                textsOf(labels, prefixes, plan.sizeSp), selectedIndex, onSelect, Modifier, compact, style,
                plan.widths.takeIf { widths -> widths.all { it > 0f } }, description, looks,
            )
            return@BoxWithConstraints
        }
        val plan = remember(labels, labelStyle, measurer, density, room, compact, byWords) {
            val around = labels.indices.map { index -> with(density) { labelRoom(index, labels.lastIndex, compact).toPx() } }
            SegmentFit.plan(
                room = room.toFloat(),
                around = around,
                slack = with(density) { SegmentSlack.toPx() },
                maxSp = fontSize.toFloat(),
                share = if (byWords) SegmentFit.Share.ByWords else SegmentFit.Share.Equal,
            ) { sizeSp ->
                val style = labelStyle.copy(fontSize = sizeSp.sp, lineHeight = (sizeSp * LINE_HEIGHT).sp)
                labels.map { label ->
                    SegmentFit.Label(
                        line = measurer.lineWidth(AnnotatedString(label), style),
                        word = label.split(' ', '\n', '\t').filter { it.isNotEmpty() }.maxOfOrNull { measurer.lineWidth(AnnotatedString(it), style) } ?: 0f,
                    )
                }
            }
        }
        val style = if (plan.sizeSp == fontSize.toFloat()) labelStyle else labelStyle.copy(fontSize = plan.sizeSp.sp, lineHeight = (plan.sizeSp * LINE_HEIGHT).sp)
        // a row of no width yet (a transition) has nothing to share
        SwitchRow(labels.map(::AnnotatedString), selectedIndex, onSelect, Modifier, compact, style, plan.widths.takeIf { widths -> widths.all { it > 0f } }, description, looks)
    }
}

/** The words of the segments at [sizeSp], each after its prefix, if it has one, a step larger and at 800. */
private fun textsOf(labels: List<String>, prefixes: List<String>?, sizeSp: Float): List<AnnotatedString> = labels.mapIndexed { index, label ->
    val prefix = prefixes?.getOrNull(index)
    if (prefix == null) {
        AnnotatedString(label)
    } else {
        buildAnnotatedString {
            withStyle(SpanStyle(fontSize = (sizeSp + PREFIX_STEP_SP).sp, fontWeight = FontWeight.ExtraBold)) { append(prefix) }
            append(PREFIX_GAP)
            append(label)
        }
    }
}

/**
 * The words of a segment at [sizeSp], 700 or [strong] 800 in tabular figures, lines of 1.15. The [compact] pill of 24 is made for one
 * such line: its box is the line and no more ([ExactLines]) — on Android the box of material3's style is Manrope's own 1.37 em, which
 * at the font 1.3 (14 sp are 18.8 dp there) is 26.3 dp, and the pill grew past 24 to take it, the switch past its 48 (50.67).
 */
@Composable
private fun labelStyleOf(sizeSp: Float, strong: Boolean, compact: Boolean): TextStyle {
    val style = MaterialTheme.typography.labelLarge.copy(
        fontSize = sizeSp.sp,
        lineHeight = (sizeSp * LINE_HEIGHT).sp,
        fontWeight = if (strong) FontWeight.ExtraBold else FontWeight.Bold,
        fontFeatureSettings = if (strong) TABULAR_FIGURES else null,
    )
    return if (compact) style.copy(lineHeightStyle = ExactLines) else style
}

/**
 * A switch whose segments carry a second line ([SegmentedSwitch]'s `sublabels`): the words of every segment at the one size
 * [SegmentLabelSize] finds for them (from [maxSp] down), shares equal or by the words, and under the words their second line at 12 sp
 * — or, under words smaller than that (a large font), at their size ([SegmentLabelSize.secondSp]). The lines keep to their line height
 * ([ExactLines]): two of them stand in the pill of 44 at the font 1.3 too.
 */
@Composable
private fun TwoLineSwitch(
    labels: List<String>,
    sublabels: List<String>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
    maxSp: Float,
    strong: Boolean,
    description: String?,
    looks: SwitchLooks,
) {
    val labelStyle = labelStyleOf(maxSp, strong, compact = false).copy(lineHeightStyle = ExactLines)
    val secondStyle = labelStyleOf(SegmentLabelSize.MIN_SP, strong = false, compact = false)
        .copy(fontFeatureSettings = TABULAR_FIGURES, lineHeightStyle = ExactLines)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val room = constraints.maxWidth
        val plan = remember(labels, sublabels, labelStyle, secondStyle, measurer, density, room) {
            val around = labels.indices.map { index -> with(density) { labelRoom(index, labels.lastIndex, compact = false).toPx() } }
            // the second lines at 12 sp are asked for at every size from 14 down to 12: measured once a size
            val seconds = HashMap<Float, List<SegmentFit.Label>>()
            SegmentLabelSize.plan(
                room = room.toFloat(),
                around = around,
                slack = with(density) { SegmentSlack.toPx() },
                leastSp = with(density) { SegmentLabelSize.LEAST_DP.dp.toSp().value },
                secondsAt = { sizeSp ->
                    seconds.getOrPut(sizeSp) {
                        val style = secondStyle.at(sizeSp)
                        sublabels.map { line ->
                            SegmentFit.Label(
                                line = measurer.lineWidth(AnnotatedString(line), style),
                                word = line.split(' ', '\n', '\t').filter { it.isNotEmpty() }.maxOfOrNull { measurer.lineWidth(AnnotatedString(it), style) } ?: 0f,
                            )
                        }
                    }
                },
                maxSp = maxSp,
            ) { sizeSp ->
                val style = labelStyle.at(sizeSp)
                labels.map { measurer.lineWidth(AnnotatedString(it), style) }
            }
        }
        val style = if (plan.sizeSp == maxSp) labelStyle else labelStyle.at(plan.sizeSp)
        val secondSp = SegmentLabelSize.secondSp(plan.sizeSp)
        SwitchRow(
            labels.map(::AnnotatedString), selectedIndex, onSelect, Modifier, compact = false, labelStyle = style,
            // a row of no width yet (a transition) has nothing to share
            weights = plan.widths.takeIf { widths -> widths.all { it > 0f } }, description = description, looks = looks,
            seconds = SecondLines(sublabels, if (secondSp == SegmentLabelSize.MIN_SP) secondStyle else secondStyle.at(secondSp)),
        )
    }
}

/** The style at [sizeSp], its lines of 1.15. */
private fun TextStyle.at(sizeSp: Float): TextStyle = copy(fontSize = sizeSp.sp, lineHeight = (sizeSp * LINE_HEIGHT).sp)

/** The second lines of the segments, one to each, and their style. */
private class SecondLines(val texts: List<String>, val style: TextStyle)

/** What of a switch is not about its words: its ground, what of it answers, what TalkBack says of each segment and who hears a hold. */
private class SwitchLooks(
    val containerColor: Color,
    val enabled: Boolean,
    val segmentEnabled: (Int) -> Boolean,
    val descriptions: List<String>?,
    val onHold: ((index: Int, held: Boolean) -> Unit)?,
)

@Composable
private fun SwitchRow(
    texts: List<AnnotatedString>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
    compact: Boolean,
    labelStyle: TextStyle,
    weights: List<Float>?,
    description: String?,
    looks: SwitchLooks,
    seconds: SecondLines? = null,
) {
    val colors = MaterialTheme.colorScheme
    val container = if (looks.containerColor.isSpecified) looks.containerColor else colors.surfaceContainer
    val edge = if (compact) CompactEdge else RegularEdge
    val between = if (compact) CompactBetween else RegularBetween
    // the compact container is drawn in the middle of its touch target, 10 dp of air above and below it
    val inset = if (compact) (CompactTouch - CompactHeight) / 2 else 0.dp
    Row(
        modifier = modifier
            .fillMaxWidth()
            // 52 (48 to press the compact one) at the usual font; the tallest label decides above it, and the segments and their
            // pills stretch to the height — the whole of it stays pressed
            .heightIn(min = if (compact) CompactTouch else RegularHeight)
            .height(IntrinsicSize.Min)
            // dimmed as a whole, its ground too, in its own colours — as a disabled button is
            .then(if (looks.enabled) Modifier else Modifier.alpha(DISABLED_ALPHA))
            .then(
                if (compact) {
                    Modifier.drawBehind {
                        val corner = CompactCorner.toPx()
                        val air = inset.toPx()
                        drawRoundRect(container, topLeft = Offset(0f, air), size = Size(size.width, size.height - 2 * air), cornerRadius = CornerRadius(corner))
                    }
                } else {
                    Modifier.clip(AppShapes.Control).background(container)
                },
            )
            .selectableGroup()
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
    ) {
        texts.forEachIndexed { index, text ->
            val selected = index == selectedIndex
            val answers = looks.enabled && looks.segmentEnabled(index)
            // a segment of its own is dimmed alone; a switch dimmed as a whole is not dimmed twice
            val segmentDim = if (looks.enabled && !answers) Modifier.alpha(DISABLED_ALPHA) else Modifier
            val pill by animateColorAsState(if (selected) colors.primaryContainer else Color.Transparent, tween(SWITCH_MS), label = "segment")
            val interaction = remember { MutableInteractionSource() }
            val pillPresses = remember(interaction) { ShiftedInteractionSource(interaction) }
            val spoken = looks.descriptions?.getOrNull(index)
            val hold = looks.onHold
            Box(
                modifier = Modifier
                    .weight(weights?.getOrNull(index) ?: 1f)
                    .fillMaxHeight()
                    .then(segmentDim)
                    .then(
                        if (hold == null) {
                            Modifier.selectable(
                                selected = selected,
                                interactionSource = interaction,
                                indication = null,
                                enabled = answers,
                                role = Role.RadioButton,
                                onClick = { onSelect(index) },
                            )
                        } else {
                            holdableSegment(index, selected, answers, interaction, onSelect, hold)
                        },
                    )
                    .then(if (spoken != null) Modifier.semantics { contentDescription = spoken } else Modifier),
            ) {
                Box(
                    modifier = Modifier
                        .padding(
                            start = if (index == 0) edge else between,
                            end = if (index == texts.lastIndex) edge else between,
                            top = inset + edge,
                            bottom = inset + edge,
                        )
                        // the press comes in the coordinates of the whole segment, the ripple is drawn in the pill
                        .onPlaced { pillPresses.shift = it.positionInParent() }
                        .fillMaxSize()
                        .clip(if (compact) AppShapes.S else AppShapes.Segment)
                        .background(pill)
                        .indication(pillPresses, ripple())
                        .padding(horizontal = LabelPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    // the segment says its description; its label, said as well, would be heard twice
                    val silent = if (spoken != null) Modifier.clearAndSetSemantics { } else Modifier
                    val words = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant
                    if (seconds == null) {
                        Text(
                            text = text,
                            modifier = silent,
                            color = words,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            style = labelStyle,
                        )
                    } else {
                        // the words and under them their second line, of their colour
                        Column(silent, horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = text, color = words, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, style = labelStyle)
                            Text(
                                text = seconds.texts[index],
                                color = words,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                style = seconds.style,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A segment that hears a hold as well as a tap (spec 3.17, «пока держишь»): a tap selects it; a press that lasts as long as a long
 * press tells `(index, true)`, and its end — the finger let go, or the gesture cut short (the switch went to sleep under it) — tells
 * `(index, false)`. The press lights the ripple of the pill as a click would. To TalkBack and VoiceOver it is a radio button that
 * their activation selects: a hold is a gesture of the finger only.
 */
@Composable
private fun holdableSegment(
    index: Int,
    selected: Boolean,
    answers: Boolean,
    interaction: MutableInteractionSource,
    onSelect: (Int) -> Unit,
    onHold: (index: Int, held: Boolean) -> Unit,
): Modifier {
    val select by rememberUpdatedState(onSelect)
    val hold by rememberUpdatedState(onHold)
    return Modifier
        .semantics(mergeDescendants = true) {
            role = Role.RadioButton
            this.selected = selected
            if (answers) {
                onClick {
                    select(index)
                    true
                }
            } else {
                disabled()
            }
        }
        .pointerInput(answers, index, interaction) {
            if (!answers) return@pointerInput
            var holding = false
            detectTapGestures(
                onPress = { offset ->
                    val press = PressInteraction.Press(offset)
                    interaction.tryEmit(press)
                    var released = false
                    try {
                        released = tryAwaitRelease()
                    } finally {
                        interaction.tryEmit(if (released) PressInteraction.Release(press) else PressInteraction.Cancel(press))
                        // a hold ends however its gesture does
                        if (holding) {
                            holding = false
                            hold(index, false)
                        }
                    }
                },
                onLongPress = {
                    holding = true
                    hold(index, true)
                },
                onTap = { select(index) },
            )
        }
}

/** The label's own padding inside its pill, at each side. */
private val LabelPadding = 4.dp

/** The row is laid out in whole pixels: a word that fits only by a hair is not trusted. */
private val SegmentSlack = 1.dp

/**
 * Around a label within its segment, for [SegmentFit]: the edge or the gap at each side of its pill and its own padding — 4 + 2 + 4 + 4
 * at the ends of the row, 2 + 2 + 4 + 4 in the middle (the compact one: 2 or 1 instead of 4 and 2).
 */
private fun labelRoom(index: Int, last: Int, compact: Boolean): Dp {
    val edge = if (compact) CompactEdge else RegularEdge
    val between = if (compact) CompactBetween else RegularBetween
    return (if (index == 0) edge else between) + (if (index == last) edge else between) + LabelPadding * 2
}

private fun TextMeasurer.lineWidth(text: AnnotatedString, style: TextStyle): Float =
    measure(text, style, softWrap = false, maxLines = 1).size.width.toFloat()
