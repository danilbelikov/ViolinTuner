package com.violinjourney.app.feature.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.components.ButtonFit
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.onboarding_part_intro
import com.violinjourney.app.shared.resources.onboarding_part_setup
import com.violinjourney.app.shared.resources.onboarding_progress_count
import com.violinjourney.app.shared.resources.onboarding_progress_description
import kotlin.math.abs
import kotlin.math.max
import org.jetbrains.compose.resources.stringResource

/** A segment of the strip fills in this long once its screen has stopped (spec 3.36.8, 5.29 R8, `BAR_FILL_MS`). */
internal const val BAR_FILL_MS = 200

/** «Пропустить» dissolves the land and the text into the page about the data in two such halves; the sky stands (36h2). */
internal const val SKIP_HALF_MS = 225

/**
 * Where the part word of the label does not stand whole in the strip (lying beside «Пропустить» at a large font), the label steps down
 * 0.5 sp at a time — at most to this; below it the strip goes under «Пропустить» (spec 5.29 R8, the review of stage 120).
 */
internal const val PROGRESS_LABEL_LEAST_SP = 10f

/** Where a reader meets the strip among the nodes around it: before the words of the page under it. */
private const val HEARD_FIRST = -1f

/** The accent of a screen passed, laid over the empty segment: ≈ #6F648F (spec 5.29 R8). */
private const val DONE_ALPHA = 0.45f
private const val TABULAR_FIGURES = "tnum"

/**
 * The strip of the way (spec 3.36.8, 5.29 R8): one for the seven screens of the onboarding — four of the introduction, a break, three
 * of the setup. What each segment shows and how fast it fills; pure, with a test.
 */
internal object OnboardingProgressMath {
    /** A segment: a screen passed, the screen in view, a screen ahead. */
    enum class Segment { DONE, CURRENT, AHEAD }

    /** The screens of the way: all seven of the onboarding. */
    val count: Int get() = OnboardingStep.entries.size

    /** The segment [index] (from 0) while [current] is in view. */
    fun segmentState(index: Int, current: OnboardingStep): Segment = when {
        index < current.ordinal -> Segment.DONE
        index == current.ordinal -> Segment.CURRENT
        else -> Segment.AHEAD
    }

    /** The break of the two parts stands after the last page of the introduction, and only there. */
    fun partGapAfter(index: Int): Boolean = index == OnboardingStep.intro.lastIndex

    /**
     * How long the segments take from the screen [from] to the screen [to] (numbers 1–7): a jump of more than one is «Пропустить» —
     * 2–4 fill at once under its dissolve of 450 ms; one screen — 200 ms; with the animations removed — at once.
     */
    fun fillMs(from: Int, to: Int, still: Boolean): Int = when {
        still -> 0
        abs(to - from) > 1 -> 2 * SKIP_HALF_MS
        else -> BAR_FILL_MS
    }

    /**
     * The label of the strip in [width] (spec 3.36.8, 5.29 R8): the size of the label ([maxSp]) where the wider part word of the two
     * parts — «ЗНАКОМСТВО», «НАСТРОЙКА» — stands whole, else 0.5 sp smaller at a time down to [PROGRESS_LABEL_LEAST_SP]; and the count
     * beside the part where the wider of the two labels holds both on one line at that size, else under it. One answer for the seven
     * screens, so the strip is as high on all of them and does not jump into the setup. Null where the part word does not stand whole
     * even at the least size: the caller gives the strip more room. [partAt] — the wider part word at a size; [lineAt] — the wider
     * label, its part, the least gap and the count, at a size. The units are the caller's.
     */
    fun labelFit(width: Float, maxSp: Float, partAt: (sizeSp: Float) -> Float, lineAt: (sizeSp: Float) -> Float): ProgressLabelFit? {
        val size = ButtonFit.largest(maxSp, PROGRESS_LABEL_LEAST_SP) { partAt(it) <= width }
        if (partAt(size) > width) return null
        return ProgressLabelFit(size, oneLine = lineAt(size) <= width)
    }

    /**
     * The top row of the column of words lying (spec 3.36.8, 5.29 R8), px from its top: «Пропустить» — or its unseen twin — [inset]
     * under the top; the strip [beside] it, at the bottom of the row so that the title under the row stands [toTitle] under the
     * label, the row no lower than the button with its inset — or, where its label does not stand whole beside the button, under it.
     */
    fun topRow(beside: Boolean, stripHeight: Int, skipHeight: Int, inset: Int, toTitle: Int): TopRowPlaces =
        if (beside) {
            val height = max(inset + skipHeight, inset + stripHeight + toTitle)
            TopRowPlaces(stripTop = height - toTitle - stripHeight, skipTop = inset, height = height)
        } else {
            TopRowPlaces(stripTop = inset + skipHeight, skipTop = inset, height = inset + skipHeight + stripHeight + toTitle)
        }
}

/** How the label of the strip stands: its size and whether the count stands beside the part (spec 3.36.8). */
internal data class ProgressLabelFit(val sizeSp: Float, val oneLine: Boolean)

/** Where the strip and «Пропустить» stand in the top row lying, and how high the row is — the words start under it. */
internal data class TopRowPlaces(val stripTop: Int, val skipTop: Int, val height: Int)

/**
 * The height of the picture of the setup (spec 3.36.8, 5.29 R8): 220, 200 and 150 dp on «Микрофон», «Эталон» and «Допуск» — the
 * hints and the bars of the later steps take what the sun gives up; in a window lower than 760 dp or on a narrow phone ([compact]) no
 * higher than 200; lower than 520 dp — none. Pure, with a test; [windowHeight] is the room of the screen, as the onboarding measures it.
 */
internal object SetupArtHeight {
    fun of(step: OnboardingStep, windowHeight: Dp, compact: Boolean): Dp {
        val full = when (step) {
            OnboardingStep.REFERENCE_PITCH -> OnboardingDimens.SetupArtSteps[1]
            OnboardingStep.TOLERANCE -> OnboardingDimens.SetupArtSteps[2]
            else -> OnboardingDimens.SetupArtSteps[0]
        }
        return when {
            windowHeight < OnboardingDimens.ArtOffBelow -> 0.dp
            compact || windowHeight < OnboardingDimens.SetupArtCompactBelow -> minOf(full, OnboardingDimens.SetupArtCompactMax)
            else -> full
        }
    }
}

/**
 * The strip of the way (spec 3.36.8): seven segments with the break of the parts after the fourth, and under it «ЗНАКОМСТВО · 1 ИЗ
 * 7» — the part on the left, the count on the right, under the part where the line does not hold both; the size and the lines of the
 * label are decided by the wider label of the two parts in the width the strip takes ([ProgressLabelWords]), so they are the same on
 * all seven screens. One description for a reader — «Знакомство, экран 1 из 7». A segment fills in 200 ms when [step] changes, a jump
 * of more than one under the dissolve of «Пропустить»; with the animations removed — at once. The colours are read in the draw phase:
 * a filling segment recomposes nothing.
 *
 * [placeholder] — the twin that keeps the room of the living strip on a page of the introduction (the strip itself stands outside the
 * pager): the same layout, the same height whatever the language and the font, but neither drawn nor heard.
 */
@Composable
internal fun OnboardingProgress(step: OnboardingStep, modifier: Modifier = Modifier, placeholder: Boolean = false) {
    val total = OnboardingProgressMath.count
    val part = stringResource(if (step.part == OnboardingPart.INTRO) Res.string.onboarding_part_intro else Res.string.onboarding_part_setup)
    val count = stringResource(Res.string.onboarding_progress_count, step.number, total)
    val description = stringResource(Res.string.onboarding_progress_description, part, step.number, total)
    val words = rememberProgressLabelWords()
    BoxWithConstraints(
        modifier
            .then(if (placeholder) Modifier.drawWithContent {} else Modifier)
            .clearAndSetSemantics {
                if (!placeholder) {
                    contentDescription = description
                    // it stands over the pager, after it in the tree: heard first, before the words under it
                    traversalIndex = HEARD_FIRST
                }
            },
    ) {
        val width = constraints.maxWidth
        // the part word that does not stand whole at the least size breaks — the limit; the caller gives the strip the room first
        val fit = remember(words, width) { words.fit(width.toFloat()) ?: ProgressLabelFit(PROGRESS_LABEL_LEAST_SP, oneLine = false) }
        Column {
            if (placeholder) {
                Spacer(Modifier.fillMaxWidth().height(OnboardingDimens.ProgressSegment))
            } else {
                ProgressSegments(step, still = LocalReduceMotion.current)
            }
            Spacer(Modifier.height(OnboardingDimens.ProgressToLabel))
            ProgressLabel(part.uppercase(), count.uppercase(), fit)
        }
    }
}

/**
 * The words of the label of both parts, measured: the label of any screen is decided by the wider of the two
 * ([OnboardingProgressMath.labelFit]), so the strip of the introduction and of the setup is as high and its label as large. The count is
 * measured with its number: the figures are tabular, every screen's count is as wide.
 */
@Stable
internal class ProgressLabelWords(
    private val measurer: TextMeasurer,
    private val style: TextStyle,
    private val parts: List<String>,
    private val count: String,
    private val gap: Float,
) {
    fun fit(width: Float): ProgressLabelFit? = OnboardingProgressMath.labelFit(width, style.fontSize.value, ::partAt, ::lineAt)

    private fun partAt(sizeSp: Float): Float = parts.maxOf { widthOf(it, progressLabelStyle(style, sizeSp)) }

    private fun lineAt(sizeSp: Float): Float =
        partAt(sizeSp) + gap + widthOf(count, progressLabelStyle(style, sizeSp).copy(fontFeatureSettings = TABULAR_FIGURES))

    private fun widthOf(text: String, style: TextStyle): Float = measurer.measure(text, style, softWrap = false, maxLines = 1).size.width.toFloat()
}

/** The style of the label at [sizeSp]: the line keeps its share of the size (16 to 12), the spacing is in em already. */
private fun progressLabelStyle(style: TextStyle, sizeSp: Float): TextStyle =
    if (sizeSp == style.fontSize.value) style else style.copy(fontSize = sizeSp.sp, lineHeight = style.lineHeight * (sizeSp / style.fontSize.value))

@Composable
private fun progressLabelBase(): TextStyle = MaterialTheme.typography.labelSmall.merge(OnboardingType.ProgressLabel)

@Composable
internal fun rememberProgressLabelWords(): ProgressLabelWords {
    val measurer = rememberTextMeasurer()
    val style = progressLabelBase()
    val intro = stringResource(Res.string.onboarding_part_intro).uppercase()
    val setup = stringResource(Res.string.onboarding_part_setup).uppercase()
    val total = OnboardingProgressMath.count
    val count = stringResource(Res.string.onboarding_progress_count, total, total).uppercase()
    val gap = with(LocalDensity.current) { OnboardingDimens.LabelGap.toPx() }
    return remember(measurer, style, intro, setup, count, gap) { ProgressLabelWords(measurer, style, listOf(intro, setup), count, gap) }
}

@Composable
private fun ProgressSegments(step: OnboardingStep, still: Boolean) {
    val colors = MaterialTheme.colorScheme
    val done = colors.primary.copy(alpha = DONE_ALPHA).compositeOver(colors.surfaceContainerHigh)
    val number = step.number
    // the screen the strip showed before: a jump of more than one is «Пропустить». Not a state — it is written after every change
    // and must not recompose; the spec of a change is fixed in the composition that brought it.
    val shown = remember { intArrayOf(number) }
    val spec = remember(number, still) { tween<Color>(OnboardingProgressMath.fillMs(shown[0], number, still)) }
    SideEffect { shown[0] = number }
    val fills = List(OnboardingProgressMath.count) { index ->
        val target = when (OnboardingProgressMath.segmentState(index, step)) {
            OnboardingProgressMath.Segment.DONE -> done
            OnboardingProgressMath.Segment.CURRENT -> colors.primary
            OnboardingProgressMath.Segment.AHEAD -> colors.surfaceContainerHigh
        }
        animateColorAsState(target, spec, label = "segment")
    }
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(OnboardingDimens.ProgressSegment)
            .drawBehind {
                val gap = OnboardingDimens.ProgressGap.toPx()
                val partGap = OnboardingDimens.ProgressPartGap.toPx()
                // the gaps of the row: one break of the parts, the rest the usual ones
                val gaps = gap * (fills.size - 2) + partGap
                val each = (size.width - gaps) / fills.size
                val radius = CornerRadius(size.height / 2)
                var x = 0f
                fills.forEachIndexed { index, fill ->
                    drawRoundRect(fill.value, Offset(x, 0f), Size(each, size.height), radius)
                    x += each + if (OnboardingProgressMath.partGapAfter(index)) partGap else gap
                }
            },
    )
}

/**
 * «ЗНАКОМСТВО» on the left and «1 ИЗ 7» on the right, at the size of [fit]; the count under the part where [fit] says the wider label
 * does not stand on one line (de, a large font, lying beside «Пропустить»).
 */
@Composable
private fun ProgressLabel(part: String, count: String, fit: ProgressLabelFit) {
    val colors = MaterialTheme.colorScheme
    val style = progressLabelStyle(progressLabelBase(), fit.sizeSp)
    Layout(
        content = {
            Text(part, color = colors.onSurfaceVariant, style = style)
            Text(count, color = ViolinTheme.textTertiary, style = style.copy(fontFeatureSettings = TABULAR_FIGURES))
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val partLine = measurables[0].measure(loose)
        val countLine = measurables[1].measure(loose)
        val gap = OnboardingDimens.LabelGap.roundToPx()
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else partLine.width + gap + countLine.width
        // the words measured here are the ones [fit] measured: the guard only keeps the count off the part
        if (fit.oneLine && partLine.width + gap + countLine.width <= width) {
            layout(width, max(partLine.height, countLine.height)) {
                partLine.placeRelative(0, 0)
                countLine.placeRelative(width - countLine.width, 0)
            }
        } else {
            layout(width, partLine.height + countLine.height) {
                partLine.placeRelative(0, 0)
                countLine.placeRelative(0, partLine.height)
            }
        }
    }
}

/**
 * The top row of the column of words lying (spec 3.36.8, 5.29 R8): the strip, and at its end «Пропустить» — the same on all seven
 * screens: in the setup ([skip] null) an unseen twin of it keeps its place, as the faded «Пропустить» of the fourth page does, so the
 * strip is as long everywhere and does not jump into the setup. The strip stands at the bottom of the row, its label 12 over the title
 * under the row, the row no lower than «Пропустить» with its inset — 56 at the font 1. Where the label does not stand whole beside the
 * button even at its least size (a large font in a column of 640 × 360), the strip goes under the button, as wide as the row
 * ([OnboardingProgressMath.topRow]). Measured: the button first, then the strip in what it leaves. [placeholder] — the twin of the row
 * on a page of the introduction, under the living row that stands over the pager: it keeps the room and is neither seen nor heard nor
 * pressed.
 */
@Composable
internal fun LandscapeTopRow(
    step: OnboardingStep,
    modifier: Modifier = Modifier,
    placeholder: Boolean = false,
    skip: (@Composable (Modifier) -> Unit)? = null,
) {
    val words = rememberProgressLabelWords()
    Layout(
        content = {
            OnboardingProgress(step, placeholder = placeholder)
            if (skip != null && !placeholder) {
                skip(Modifier)
            } else {
                SkipButton(onClick = {}, enabled = false, modifier = Modifier.drawWithContent {}.clearAndSetSemantics {})
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = OnboardingDimens.SkipInset),
    ) { measurables, constraints ->
        val row = constraints.maxWidth
        val button = measurables[1].measure(Constraints(maxWidth = row))
        val beside = words.fit((row - button.width).toFloat()) != null
        val strip = measurables[0].measure(Constraints.fixedWidth(if (beside) row - button.width else row))
        val places = OnboardingProgressMath.topRow(
            beside = beside,
            stripHeight = strip.height,
            skipHeight = button.height,
            inset = OnboardingDimens.SkipInset.roundToPx(),
            toTitle = OnboardingDimens.ProgressToTitle.roundToPx(),
        )
        layout(row, places.height) {
            strip.placeRelative(0, places.stripTop)
            button.placeRelative(row - button.width, places.skipTop)
        }
    }
}
