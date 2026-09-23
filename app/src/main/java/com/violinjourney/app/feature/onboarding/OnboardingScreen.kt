package com.violinjourney.app.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.violinjourney.app.R
import com.violinjourney.app.core.ui.components.A4Selector
import com.violinjourney.app.core.ui.components.TolerancePresetList
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.onboarding.art.ArtFit
import com.violinjourney.app.feature.onboarding.art.ArtScene
import com.violinjourney.app.feature.onboarding.art.OnboardingArtCanvas
import com.violinjourney.app.feature.onboarding.art.OnboardingArtData

/** The introduction gives way to the setup, and back, by a crossfade: the same evening in both. */
private const val PART_CROSSFADE_MS = 300

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
            transitionSpec = {
                val duration = if (still) 0 else PART_CROSSFADE_MS
                fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))
            },
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
                    SetupWords(state, step, layout, onIntent)
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
                        SetupWords(state, step, layout, onIntent)
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
    Crossfade(targetState = step, animationSpec = tween(if (still) 0 else PART_CROSSFADE_MS), modifier = modifier, label = "setup art") { shown ->
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
private fun SetupWords(state: OnboardingState, step: OnboardingStep, layout: OnboardingLayout, onIntent: (OnboardingIntent) -> Unit) {
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
    Column(verticalArrangement = Arrangement.spacedBy(OnboardingDimens.TitleToBody)) {
        when (step) {
            OnboardingStep.MICROPHONE -> {
                OnboardingTitle(R.string.onboarding_mic_title, titleStyle)
                OnboardingBody(R.string.onboarding_mic_text, bodyStyle)
            }
            OnboardingStep.REFERENCE_PITCH -> {
                OnboardingTitle(R.string.onboarding_a4_title, titleStyle)
                OnboardingBody(R.string.onboarding_a4_text, bodyStyle)
            }
            OnboardingStep.TOLERANCE -> {
                OnboardingTitle(R.string.onboarding_tolerance_title, titleStyle)
                OnboardingBody(R.string.onboarding_tolerance_text, bodyStyle)
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

private fun ctaOf(step: OnboardingStep): Int = when (step) {
    OnboardingStep.MICROPHONE -> R.string.onboarding_mic_cta
    OnboardingStep.TOLERANCE -> R.string.onboarding_tolerance_cta
    else -> R.string.onboarding_next
}
