package com.violinjourney.app.feature.onboarding

import org.jetbrains.compose.resources.StringResource
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.ui.components.A4Selector
import com.violinjourney.app.core.ui.components.TolerancePresetList
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.onboarding.art.ArtFit
import com.violinjourney.app.feature.onboarding.art.ArtScene
import com.violinjourney.app.feature.onboarding.art.OnboardingArtCanvas
import com.violinjourney.app.feature.onboarding.art.OnboardingArtData
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.onboarding_a4_text
import com.violinjourney.app.shared.resources.onboarding_a4_title
import com.violinjourney.app.shared.resources.onboarding_mic_cta
import com.violinjourney.app.shared.resources.onboarding_mic_text
import com.violinjourney.app.shared.resources.onboarding_mic_title
import com.violinjourney.app.shared.resources.onboarding_next
import com.violinjourney.app.shared.resources.onboarding_tolerance_cta
import com.violinjourney.app.shared.resources.onboarding_tolerance_text
import com.violinjourney.app.shared.resources.onboarding_tolerance_title

/** The picture of the setup changes by a crossfade: the same evening, the sun lower with every step. */
private const val PART_CROSSFADE_MS = 300

/** Words give way through the background — the old ones are gone before the new come — so they never lie over each other. */
private const val WORDS_OUT_MS = 120
private const val WORDS_IN_MS = 180

/** Keeps the value where it started until the very end, then jumps: the leaving picture stays whole under the new one. */
private val HoldToEnd = Easing { fraction -> if (fraction < 1f) 0f else 1f }

private fun <S> AnimatedContentTransitionScope<S>.fadeThrough(still: Boolean): ContentTransform =
    if (still) {
        EnterTransition.None togetherWith ExitTransition.None
    } else {
        fadeIn(tween(WORDS_IN_MS, delayMillis = WORDS_OUT_MS)) togetherWith fadeOut(tween(WORDS_OUT_MS))
    } using SizeTransform(clip = false)

/**
 * The onboarding (spec 3.7, 3.33, handoff series 36): four pages of the introduction, then three steps
 * of the setup. Stateless. The layout follows the shape of the window: wider than tall — the picture on
 * the left and the words on the right.
 */
@Composable
fun OnboardingScreen(
    state: OnboardingState,
    onIntent: (OnboardingIntent) -> Unit,
    modifier: Modifier = Modifier,
    onHaveBackup: (() -> Unit)? = null,
) {
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
                OnboardingPart.INTRO -> OnboardingIntro(state.step.takeIf { it.part == part } ?: OnboardingStep.DATA, layout, onIntent, onHaveBackup)
                OnboardingPart.SETUP -> OnboardingSetup(state, layout, onIntent)
            }
        }
    }
}

/** The three steps of the setup (36e): a short picture of the same road, the sun lower with every step. */
@Composable
private fun OnboardingSetup(state: OnboardingState, layout: OnboardingLayout, onIntent: (OnboardingIntent) -> Unit) {
    val step = state.step.takeIf { it.part == OnboardingPart.SETUP } ?: OnboardingStep.MICROPHONE
    if (layout == OnboardingLayout.LANDSCAPE) {
        Row(Modifier.fillMaxSize()) {
            SetupArt(
                step = step,
                fit = ArtFit.CENTER,
                fadeEnd = OnboardingDimens.LandscapeFade,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = OnboardingDimens.SkipInset, top = OnboardingDimens.PaddingCompact, end = OnboardingDimens.Padding, bottom = OnboardingDimens.PaddingCompact),
            ) {
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(OnboardingDimens.GapCompact),
                ) {
                    SetupWords(state, step, layout, OnboardingDimens.GapCompact, onIntent)
                }
                Spacer(Modifier.height(OnboardingDimens.GapCompact))
                OnboardingCta(ctaOf(step), wide = false, onClick = { onIntent(OnboardingIntent.PrimaryClicked) })
            }
        }
    } else {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = layout == OnboardingLayout.COMPACT
            val art: Dp = when {
                maxHeight < OnboardingDimens.ArtOffBelow -> 0.dp
                compact || maxHeight < OnboardingDimens.SetupArtCompactBelow -> OnboardingDimens.SetupArtCompact
                else -> OnboardingDimens.SetupArt
            }
            val padding = if (compact) OnboardingDimens.PaddingCompact else OnboardingDimens.Padding
            val gap = if (compact) OnboardingDimens.GapCompact else OnboardingDimens.Gap
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (art > 0.dp) {
                    SetupArt(step, ArtFit.BOTTOM, fadeEnd = 0.dp, modifier = Modifier.fillMaxWidth().height(art))
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
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        SetupWords(state, step, layout, gap, onIntent)
                    }
                    Spacer(Modifier.height(gap))
                    OnboardingCta(ctaOf(step), wide = true, onClick = { onIntent(OnboardingIntent.PrimaryClicked) })
                }
            }
        }
    }
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

@Composable
private fun SetupWords(state: OnboardingState, step: OnboardingStep, layout: OnboardingLayout, gap: Dp, onIntent: (OnboardingIntent) -> Unit) {
    val titleStyle = when (layout) {
        OnboardingLayout.PORTRAIT -> OnboardingType.Title
        OnboardingLayout.COMPACT -> OnboardingType.TitleCompact
        OnboardingLayout.LANDSCAPE -> OnboardingType.TitleLandscape
    }
    val bodyStyle = when (layout) {
        OnboardingLayout.PORTRAIT -> OnboardingType.Body
        OnboardingLayout.COMPACT -> OnboardingType.BodyCompact
        OnboardingLayout.LANDSCAPE -> OnboardingType.BodyLandscape
    }
    SetupProgress(step.indexInPart, OnboardingStep.setup.size)
    val still = LocalReduceMotion.current
    AnimatedContent(targetState = step, transitionSpec = { fadeThrough(still) }, label = "setup words") { shown ->
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            SetupStepWords(state, shown, titleStyle, bodyStyle, onIntent)
        }
    }
}

@Composable
private fun SetupStepWords(
    state: OnboardingState,
    step: OnboardingStep,
    titleStyle: TextStyle,
    bodyStyle: TextStyle,
    onIntent: (OnboardingIntent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(OnboardingDimens.TitleToBody)) {
        when (step) {
            OnboardingStep.MICROPHONE -> {
                OnboardingTitle(Res.string.onboarding_mic_title, titleStyle)
                OnboardingBody(Res.string.onboarding_mic_text, bodyStyle)
            }
            OnboardingStep.REFERENCE_PITCH -> {
                OnboardingTitle(Res.string.onboarding_a4_title, titleStyle)
                OnboardingBody(Res.string.onboarding_a4_text, bodyStyle)
            }
            OnboardingStep.TOLERANCE -> {
                OnboardingTitle(Res.string.onboarding_tolerance_title, titleStyle)
                OnboardingBody(Res.string.onboarding_tolerance_text, bodyStyle)
            }
            else -> Unit
        }
    }
    when (step) {
        OnboardingStep.REFERENCE_PITCH -> A4Selector(
            optionsHz = state.a4OptionsHz,
            selectedHz = state.a4Hz,
            onSelect = { onIntent(OnboardingIntent.A4Selected(it)) },
        )
        OnboardingStep.TOLERANCE -> TolerancePresetList(
            selected = state.tolerance,
            onSelect = { onIntent(OnboardingIntent.ToleranceSelected(it)) },
        )
        else -> Unit
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
