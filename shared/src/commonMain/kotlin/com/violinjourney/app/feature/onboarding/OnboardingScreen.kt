package com.violinjourney.app.feature.onboarding

import org.jetbrains.compose.resources.StringResource
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.ui.components.A4Selector
import com.violinjourney.app.core.ui.components.ChoiceLook
import com.violinjourney.app.core.ui.components.FaceArrival
import com.violinjourney.app.core.ui.components.SettleAfterDoubleTap
import com.violinjourney.app.core.ui.components.TolerancePresetList
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.onboarding.art.ArtFit
import com.violinjourney.app.feature.onboarding.art.ArtScene
import com.violinjourney.app.feature.onboarding.art.OnboardingArtCanvas
import com.violinjourney.app.feature.onboarding.art.OnboardingArtData
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.onboarding_a4_hint_lead
import com.violinjourney.app.shared.resources.onboarding_a4_hint_text
import com.violinjourney.app.shared.resources.onboarding_a4_text
import com.violinjourney.app.shared.resources.onboarding_a4_title
import com.violinjourney.app.shared.resources.onboarding_mic_cta
import com.violinjourney.app.shared.resources.onboarding_mic_hint_lead
import com.violinjourney.app.shared.resources.onboarding_mic_hint_text
import com.violinjourney.app.shared.resources.onboarding_mic_text
import com.violinjourney.app.shared.resources.onboarding_mic_title
import com.violinjourney.app.shared.resources.onboarding_next
import com.violinjourney.app.shared.resources.onboarding_tolerance_cta
import com.violinjourney.app.shared.resources.onboarding_tolerance_text
import com.violinjourney.app.shared.resources.onboarding_tolerance_title
import org.jetbrains.compose.resources.stringResource

/** The picture of the setup changes by a crossfade: the same evening, the sun lower with every step; its height goes along. */
private const val PART_CROSSFADE_MS = 300

/** Words give way through the background — the old ones are gone before the new come — so they never lie over each other. */
private const val WORDS_OUT_MS = 120
private const val WORDS_IN_MS = 180

/** Lying, the picture takes the left half of the window and the words the right one, as in the introduction. */
private const val HALF = 0.5f

/** Keeps the value where it started until the very end, then jumps: the leaving picture stays whole under the new one. */
private val HoldToEnd = Easing { fraction -> if (fraction < 1f) 0f else 1f }

private fun <S> AnimatedContentTransitionScope<S>.fadeThrough(still: Boolean): ContentTransform =
    if (still) {
        EnterTransition.None togetherWith ExitTransition.None
    } else {
        fadeIn(tween(WORDS_IN_MS, delayMillis = WORDS_OUT_MS)) togetherWith fadeOut(tween(WORDS_OUT_MS))
    } using SizeTransform(clip = false)

/**
 * The onboarding (spec 3.7, 3.33, 3.36.8, handoff series 36): four pages of the introduction, then three steps
 * of the setup, under one strip of the way. Stateless. The layout follows the shape of the window: wider than tall — the
 * picture on the left and the words on the right. [micAllowed] — the microphone is allowed already: «Микрофон» has no hint.
 */
@Composable
fun OnboardingScreen(
    state: OnboardingState,
    onIntent: (OnboardingIntent) -> Unit,
    modifier: Modifier = Modifier,
    onHaveBackup: (() -> Unit)? = null,
    micAllowed: Boolean = false,
) {
    // A screen that came in the place of another does not take the second tap of the finger that pressed the one before (5.29 R8, as
    // a face of a sheet in R3 and a moment of the journey in R7): «Дальше» of «Эталон» would press «Начать играть» standing in its
    // place and skip «Допуск», «Понятно» — «Разрешить микрофон» before its hint is read, «Пропустить» — «Начать» of the page it
    // leaves. Two guards, one for each side of the frame that composes the new step. A tap that comes before it reaches the old
    // button, which still says its own step: every press carries the step its button showed, and the model moves only forward from
    // it ([OnboardingFlow.press]) — the second tap finds the way past that step already. A tap after it meets the new step: the main
    // button and «Пропустить» are held for the time of a double tap of the system, counted in frames from the first one of the new
    // step — with the animations removed too: there is no dissolve then, but the swap under the finger is there.
    val arrival = rememberStepArrival(state.step)
    val held: (OnboardingIntent) -> Unit = { intent -> if (!(arrival.holds && intent.isPress)) onIntent(intent) }
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        val layout = when {
            maxWidth > maxHeight -> OnboardingLayout.LANDSCAPE
            maxWidth < OnboardingDimens.CompactBelowWidth -> OnboardingLayout.COMPACT
            else -> OnboardingLayout.PORTRAIT
        }
        val still = LocalReduceMotion.current
        AnimatedContent(
            targetState = state.step.part,
            transitionSpec = { fadeThrough(still) },
            label = "part",
        ) { part ->
            when (part) {
                OnboardingPart.INTRO -> OnboardingIntro(state.step.takeIf { it.part == part } ?: OnboardingStep.DATA, layout, held, onHaveBackup)
                OnboardingPart.SETUP -> OnboardingSetup(state, layout, micAllowed, held)
            }
        }
    }
}

/** A press that moves the way on: the main button of a screen and «Пропустить». */
private val OnboardingIntent.isPress: Boolean get() = this is OnboardingIntent.PrimaryClicked || this is OnboardingIntent.SkipClicked

/**
 * How the step in view came: in the place of another — under the finger that pressed the button before it — or as the one the screen
 * opened on, which holds nothing. Let go once the time of a double tap has passed ([SettleAfterDoubleTap]).
 */
@Composable
private fun rememberStepArrival(step: OnboardingStep): FaceArrival {
    // the step the screen showed last; written after every change, never read for drawing — not a state
    val shown = remember { arrayOfNulls<OnboardingStep>(1) }
    val arrival = remember(step) { FaceArrival(inPlace = shown[0] != null) }
    SideEffect { shown[0] = step }
    SettleAfterDoubleTap(arrival)
    return arrival
}

/**
 * The three steps of the setup (36e): a short picture of the same road, the sun lower with every step, and the strip of the way
 * under it (spec 3.36.8) — lying in the top row of the column of words, as in the introduction. Each step has a scroll of its own,
 * made at one place whatever the layout: a step scrolled down never opens the next one scrolled, and a turn of the phone keeps
 * where each was.
 */
@Composable
private fun OnboardingSetup(state: OnboardingState, layout: OnboardingLayout, micAllowed: Boolean, onIntent: (OnboardingIntent) -> Unit) {
    val step = state.step.takeIf { it.part == OnboardingPart.SETUP } ?: OnboardingStep.MICROPHONE
    val scrolls = rememberSetupScrolls()
    // one button for the three steps, out of the words that dissolve: it says the step it was composed for, so a tap that reaches it
    // before the next frame carries the step the finger saw, not the one the model has moved to
    val onPrimary = { onIntent(OnboardingIntent.PrimaryClicked(step)) }
    val ctaIcon = if (step == OnboardingStep.MICROPHONE) AppIcons.Mic else null
    if (layout == OnboardingLayout.LANDSCAPE) {
        Box(Modifier.fillMaxSize()) {
            SetupArt(
                step = step,
                fit = ArtFit.CENTER,
                fadeEnd = OnboardingDimens.LandscapeFade,
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(HALF),
            )
            Column(
                Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(HALF),
            ) {
                LandscapeTopRow(step)
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = OnboardingDimens.SkipInset, end = OnboardingDimens.Padding, bottom = OnboardingDimens.PaddingCompact),
                ) {
                    SetupWords(state, step, layout, scrolls, micAllowed, onIntent)
                    Spacer(Modifier.height(OnboardingDimens.GapCompact))
                    OnboardingCta(ctaOf(step), wide = false, onClick = onPrimary, icon = ctaIcon)
                }
            }
        }
    } else {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = layout == OnboardingLayout.COMPACT
            val target = SetupArtHeight.of(step, maxHeight, compact)
            val still = LocalReduceMotion.current
            // the height goes from step to step with the crossfade of the picture; read where the picture is measured
            val art = remember { Animatable(target.value) }
            LaunchedEffect(target, still) {
                if (still) art.snapTo(target.value) else art.animateTo(target.value, tween(PART_CROSSFADE_MS))
            }
            val padding = if (compact) OnboardingDimens.PaddingCompact else OnboardingDimens.Padding
            val gap = if (compact) OnboardingDimens.GapCompact else OnboardingDimens.Gap
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (target > 0.dp) {
                    SetupArt(
                        step = step,
                        fit = ArtFit.BOTTOM,
                        fadeEnd = 0.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .layout { measurable, constraints ->
                                val height = constraints.constrainHeight(art.value.dp.roundToPx().coerceAtLeast(0))
                                val picture = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
                                layout(picture.width, height) { picture.place(0, 0) }
                            },
                    )
                } else {
                    Spacer(Modifier.height(padding))
                }
                Column(
                    Modifier
                        .widthIn(max = OnboardingDimens.MaxTextWidth)
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(start = padding, end = padding, bottom = padding),
                ) {
                    if (target > 0.dp) Spacer(Modifier.height(OnboardingDimens.ProgressTop))
                    OnboardingProgress(step)
                    Spacer(Modifier.height(OnboardingDimens.ProgressToTitle))
                    SetupWords(state, step, layout, scrolls, micAllowed, onIntent)
                    Spacer(Modifier.height(gap))
                    OnboardingCta(ctaOf(step), wide = true, onClick = onPrimary, icon = ctaIcon)
                }
            }
        }
    }
}

/** A scroll for each step of the setup, kept over a turn of the phone. */
@Composable
private fun rememberSetupScrolls(): Map<OnboardingStep, ScrollState> {
    val scrolls = rememberSaveable(
        saver = listSaver<List<ScrollState>, Int>(save = { list -> list.map { it.value } }, restore = { values -> values.map { ScrollState(it) } }),
    ) { OnboardingStep.setup.map { ScrollState(0) } }
    return remember(scrolls) { OnboardingStep.setup.zip(scrolls).toMap() }
}

@Composable
private fun SetupArt(step: OnboardingStep, fit: ArtFit, fadeEnd: Dp, modifier: Modifier = Modifier) {
    val still = LocalReduceMotion.current
    val fadePx = with(LocalDensity.current) { fadeEnd.toPx() }
    // The new picture comes in over the old one, which stays whole under it until the end: a plain
    // crossfade would let the dark background through at the half and show two suns on it.
    AnimatedContent(
        targetState = step,
        transitionSpec = {
            val duration = if (still) 0 else PART_CROSSFADE_MS
            fadeIn(tween(duration)) togetherWith fadeOut(tween(duration, easing = HoldToEnd))
        },
        modifier = modifier,
        label = "setup art",
    ) { shown ->
        OnboardingArtCanvas(
            scenes = listOf(sceneOf(shown)),
            position = { 0f },
            shownPage = 0,
            fit = fit,
            still = still,
            zoneColors = ViolinTheme.zoneColors,
            surface = MaterialTheme.colorScheme.surface,
            fadeEndPx = fadePx,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** The title and the words of a step in the sizes of its layout. */
private class SetupStyles(val title: TextStyle, val body: TextStyle)

/**
 * The words of the step in view, each step in a scroll of its own, fading into the ground at an edge past which it goes on; where the
 * room is low, the choice of «Эталон» and the hint of «Микрофон» are put where they are seen as the step opens ([SetupWordsMath]).
 */
@Composable
private fun ColumnScope.SetupWords(
    state: OnboardingState,
    step: OnboardingStep,
    layout: OnboardingLayout,
    scrolls: Map<OnboardingStep, ScrollState>,
    micAllowed: Boolean,
    onIntent: (OnboardingIntent) -> Unit,
) {
    val styles = remember(layout) {
        SetupStyles(
            title = when (layout) {
                OnboardingLayout.PORTRAIT -> OnboardingType.Title
                OnboardingLayout.COMPACT -> OnboardingType.TitleCompact
                OnboardingLayout.LANDSCAPE -> OnboardingType.TitleLandscape
            },
            body = when (layout) {
                OnboardingLayout.PORTRAIT -> OnboardingType.Body
                OnboardingLayout.COMPACT -> OnboardingType.BodyCompact
                OnboardingLayout.LANDSCAPE -> OnboardingType.BodyLandscape
            },
        )
    }
    val still = LocalReduceMotion.current
    val ground = MaterialTheme.colorScheme.surface
    AnimatedContent(
        targetState = step,
        transitionSpec = { fadeThrough(still) },
        modifier = Modifier.weight(1f),
        label = "setup words",
    ) { shown ->
        // A face that leaves keeps the face it had (the lesson of stage 119): the answer to the system's question moves the step on and
        // allows the microphone in one frame, and the hint of the leaving «Микрофон» fades with its words rather than going from
        // under them. The face in view follows the permission — it may come from the settings while the step waits.
        val allowed = remember { booleanArrayOf(micAllowed) }
        if (transition.targetState != EnterExitState.PostExit) allowed[0] = micAllowed
        val scroll = scrolls.getValue(shown)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val room = constraints
            when (shown) {
                OnboardingStep.MICROPHONE -> MicrophoneWords(room, scroll, ground, styles, hint = !allowed[0])
                OnboardingStep.REFERENCE_PITCH -> Scrolling(scroll, ground) { ReferenceWords(state, styles, room.maxHeight, onIntent) }
                OnboardingStep.TOLERANCE -> Scrolling(scroll, ground) {
                    OnboardingTitle(Res.string.onboarding_tolerance_title, styles.title)
                    TolerancePresetList(
                        selected = state.tolerance,
                        onSelect = { onIntent(OnboardingIntent.ToleranceSelected(it)) },
                        modifier = Modifier.padding(top = OnboardingDimens.CardsTop),
                        look = ChoiceLook.Onboarding,
                    )
                    OnboardingCaption(Res.string.onboarding_tolerance_text, Modifier.padding(top = OnboardingDimens.CaptionTop))
                }
                else -> Unit
            }
        }
    }
}

/** The words of a step in their own scroll, fading into the [ground] at an edge past which they go on. */
@Composable
private fun Scrolling(scroll: ScrollState, ground: Color, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxSize()
            .scrollEdges(scroll, ground)
            .verticalScroll(scroll),
        content = content,
    )
}

/**
 * «Микрофон» (spec 3.36.8): the title, the words and — while the microphone is not allowed ([hint]) — the card «Любой ответ ведёт
 * дальше…» under them; where the words with the card do not stand in the room of the step ([constraints] — 640 × 360 lying), the card
 * stands over the button, out of what scrolls, and is read before the system asks ([SetupWordsMath.hintPinned]).
 */
@Composable
private fun MicrophoneWords(constraints: Constraints, scroll: ScrollState, ground: Color, styles: SetupStyles, hint: Boolean) {
    val pinned = hint && rememberHintPinned(constraints, styles)
    val words: @Composable ColumnScope.() -> Unit = {
        OnboardingTitle(Res.string.onboarding_mic_title, styles.title)
        Spacer(Modifier.height(OnboardingDimens.TitleToBody))
        OnboardingBody(Res.string.onboarding_mic_text, styles.body)
    }
    // what comes next said before the system asks — and only while it may ask (spec 3.36.8)
    val card = @Composable {
        HintCard(AppIcons.Mic, Res.string.onboarding_mic_hint_lead, Res.string.onboarding_mic_hint_text, Modifier.padding(top = OnboardingDimens.HintTop))
    }
    if (pinned) {
        Column(Modifier.fillMaxSize()) {
            Scrolling(scroll, ground, Modifier.weight(1f), words)
            card()
        }
    } else {
        Scrolling(scroll, ground) {
            words()
            if (hint) card()
        }
    }
}

/** Measures the words of «Микрофон» and its hint as they are drawn, [constraints] wide, and asks [SetupWordsMath.hintPinned]. */
@Composable
private fun rememberHintPinned(constraints: Constraints, styles: SetupStyles): Boolean {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val title = stringResource(Res.string.onboarding_mic_title)
    val text = stringResource(Res.string.onboarding_mic_text)
    val lead = stringResource(Res.string.onboarding_mic_hint_lead)
    val hint = hintWords(lead, stringResource(Res.string.onboarding_mic_hint_text), MaterialTheme.colorScheme.onSurface)
    val titleStyle = onboardingTitleStyle(styles.title)
    val bodyStyle = onboardingBodyStyle(styles.body)
    val hintStyle = hintStyle()
    // the styles are keys: on iOS Manrope comes a frame after the first one
    return remember(constraints, title, text, hint, titleStyle, bodyStyle, hintStyle, measurer, density) {
        with(density) {
            val width = constraints.maxWidth
            val hintRoom = width - (OnboardingDimens.HintPaddingHorizontal * 2 + OnboardingDimens.HintIcon + OnboardingDimens.HintIconGap).roundToPx()
            val heightOf = { words: AnnotatedString, style: TextStyle, room: Int ->
                measurer.measure(words, style, constraints = Constraints(maxWidth = room.coerceAtLeast(0))).size.height.toFloat()
            }
            SetupWordsMath.hintPinned(
                viewport = constraints.maxHeight.toFloat(),
                title = heightOf(AnnotatedString(title), titleStyle, width),
                text = heightOf(AnnotatedString(text), bodyStyle, width),
                hint = heightOf(hint, hintStyle, hintRoom) + (OnboardingDimens.HintPaddingVertical * 2).toPx(),
                titleToText = OnboardingDimens.TitleToBody.toPx(),
                toHint = OnboardingDimens.HintTop.toPx(),
            )
        }
    }
}

/**
 * «Эталон» (spec 3.36.8): the title, «Частота ноты A4…», the four buttons and the hint «Не знаете — оставьте 440.» — or, where under
 * the title and the words the buttons would not stand whole above the edge of the words as the step opens (640 × 360 lying), the
 * buttons right under the title and the words under them ([SetupWordsMath.choiceFirst], by the heights the four take in [viewport]).
 */
@Composable
private fun ReferenceWords(state: OnboardingState, styles: SetupStyles, viewport: Int, onIntent: (OnboardingIntent) -> Unit) {
    Layout(
        content = {
            OnboardingTitle(Res.string.onboarding_a4_title, styles.title)
            OnboardingBody(Res.string.onboarding_a4_text, styles.body)
            A4Selector(
                optionsHz = state.a4OptionsHz,
                selectedHz = state.a4Hz,
                onSelect = { onIntent(OnboardingIntent.A4Selected(it)) },
                look = ChoiceLook.Onboarding,
            )
            HintCard(AppIcons.Info, Res.string.onboarding_a4_hint_lead, Res.string.onboarding_a4_hint_text)
        },
    ) { measurables, constraints ->
        val each = constraints.copy(minWidth = 0, minHeight = 0)
        val (title, text, choice, hint) = measurables.map { it.measure(each) }
        val choiceFirst = SetupWordsMath.choiceFirst(
            viewport = viewport.toFloat(),
            title = title.height.toFloat(),
            text = text.height.toFloat(),
            choice = choice.height.toFloat(),
            titleToText = OnboardingDimens.TitleToBody.toPx(),
            toChoice = OnboardingDimens.A4Top.toPx(),
            fade = OnboardingDimens.ScrollFade.toPx(),
        )
        // each with the gap over it
        val toChoice = OnboardingDimens.A4Top.roundToPx()
        val toHint = OnboardingDimens.HintTop.roundToPx()
        val order = if (choiceFirst) {
            listOf(title to 0, choice to toChoice, text to OnboardingDimens.ChoiceToText.roundToPx(), hint to toHint)
        } else {
            listOf(title to 0, text to OnboardingDimens.TitleToBody.roundToPx(), choice to toChoice, hint to toHint)
        }
        layout(constraints.maxWidth, order.sumOf { (placeable, gap) -> gap + placeable.height }) {
            var y = 0
            order.forEach { (placeable, gap) ->
                y += gap
                placeable.placeRelative(0, y)
                y += placeable.height
            }
        }
    }
}

private fun sceneOf(step: OnboardingStep): ArtScene = when (step) {
    OnboardingStep.REFERENCE_PITCH -> OnboardingArtData.setupReference
    OnboardingStep.TOLERANCE -> OnboardingArtData.setupTolerance
    else -> OnboardingArtData.setupMicrophone
}

private fun ctaOf(step: OnboardingStep): StringResource = when (step) {
    OnboardingStep.MICROPHONE -> Res.string.onboarding_mic_cta
    OnboardingStep.TOLERANCE -> Res.string.onboarding_tolerance_cta
    else -> Res.string.onboarding_next
}
