package com.example.violintuner.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.violintuner.R
import com.example.violintuner.core.domain.TolerancePreset
import com.example.violintuner.core.ui.components.A4Selector
import com.example.violintuner.core.ui.components.TolerancePresetList
import com.example.violintuner.core.ui.theme.ViolinTheme

/** Sizes of the handoff onboarding frames 5a–5c (base screen 412 × 892 dp). */
private object OnboardingDimens {
    val ScreenPadding = 24.dp
    val MaxContentWidth = 480.dp
    val ProgressHeight = 4.dp
    val ProgressGap = 6.dp
    val ContentSpacing = 20.dp
    val Illustration = 200.dp
    val IllustrationCompact = 120.dp

    /** Below this height of the content area the illustration shrinks, below the next it goes. */
    val CompactBelow = 520.dp
    val NoIllustrationBelow = 360.dp
    val CtaHeight = 56.dp
    val CtaCorner = 28.dp
    val BottomSpacing = 12.dp
    val FootIconWidth = 18.dp
    val FootIconHeight = 12.dp
    val FootIconBorder = 2.dp
    val FootIconCorner = 2.dp
    val FootGap = 8.dp
    val MicGlyphWidth = 44.dp
    val MicGlyphHeight = 76.dp
    val ToleranceTrackWidth = 160.dp
    val ToleranceTrackHeight = 8.dp
    val TolerancePillHeight = 12.dp

    /** Prototype: the pill is 3.2 % of the track per cent of tolerance. */
    const val TOLERANCE_PILL_FRACTION_PER_CENT = 0.032f
    const val GLOW_EDGE = 0.7f
}

private val TitleStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 37.sp, letterSpacing = (-0.5).sp)
private val BodyStyle = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
private val CtaStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp)
private val FootStyle = TextStyle(fontSize = 13.sp)
private val ReferenceLetterStyle = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 88.sp, lineHeight = 88.sp, letterSpacing = (-0.045).em)
private val ReferenceOctaveStyle = TextStyle(fontWeight = FontWeight.Normal, fontSize = 32.sp)

private class StepTexts(val title: Int, val text: Int, val cta: Int, val foot: Int)

private fun textsOf(step: OnboardingStep) = when (step) {
    OnboardingStep.MICROPHONE -> StepTexts(
        R.string.onboarding_mic_title, R.string.onboarding_mic_text,
        R.string.onboarding_mic_cta, R.string.onboarding_mic_foot,
    )
    OnboardingStep.REFERENCE_PITCH -> StepTexts(
        R.string.onboarding_a4_title, R.string.onboarding_a4_text,
        R.string.onboarding_a4_cta, R.string.onboarding_a4_foot,
    )
    OnboardingStep.TOLERANCE -> StepTexts(
        R.string.onboarding_tolerance_title, R.string.onboarding_tolerance_text,
        R.string.onboarding_tolerance_cta, R.string.onboarding_tolerance_foot,
    )
}

/** Three-step onboarding (spec 3.7, handoff 5a–5c). Stateless. */
@Composable
fun OnboardingScreen(
    state: OnboardingState,
    onIntent: (OnboardingIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val texts = textsOf(state.step)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = OnboardingDimens.MaxContentWidth)
                .fillMaxSize()
                .padding(OnboardingDimens.ScreenPadding),
        ) {
            StepProgress(current = state.step)
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val illustration = when {
                    maxHeight < OnboardingDimens.NoIllustrationBelow -> null
                    maxHeight < OnboardingDimens.CompactBelow -> OnboardingDimens.IllustrationCompact
                    else -> OnboardingDimens.Illustration
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // scroll first: inside it the column is at least one viewport tall, so
                        // short content stays centered and long content scrolls
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = maxHeight)
                        .padding(vertical = OnboardingDimens.ContentSpacing),
                    verticalArrangement = Arrangement.spacedBy(OnboardingDimens.ContentSpacing, Alignment.CenterVertically),
                ) {
                    if (illustration != null) {
                        Illustration(state, illustration, Modifier.align(Alignment.CenterHorizontally))
                    }
                    Text(
                        text = stringResource(texts.title),
                        color = colors.onSurface,
                        style = MaterialTheme.typography.headlineLarge.merge(TitleStyle),
                    )
                    Text(
                        text = stringResource(texts.text),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge.merge(BodyStyle),
                    )
                    when (state.step) {
                        OnboardingStep.MICROPHONE -> Unit
                        OnboardingStep.REFERENCE_PITCH -> A4Selector(
                            optionsHz = state.a4OptionsHz,
                            selectedHz = state.a4Hz,
                            onSelect = { onIntent(OnboardingIntent.A4Selected(it)) },
                        )
                        OnboardingStep.TOLERANCE -> TolerancePresetList(
                            selected = state.tolerance,
                            onSelect = { onIntent(OnboardingIntent.ToleranceSelected(it)) },
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(OnboardingDimens.BottomSpacing)) {
                Button(
                    onClick = { onIntent(OnboardingIntent.PrimaryClicked) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(OnboardingDimens.CtaHeight),
                    shape = RoundedCornerShape(OnboardingDimens.CtaCorner),
                ) {
                    Text(text = stringResource(texts.cta), style = MaterialTheme.typography.labelLarge.merge(CtaStyle))
                }
                Foot(text = stringResource(texts.foot))
            }
        }
    }
}

@Composable
private fun StepProgress(current: OnboardingStep) {
    val colors = MaterialTheme.colorScheme
    val description = stringResource(
        R.string.onboarding_step_description, current.ordinal + 1, OnboardingStep.entries.size,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(OnboardingDimens.ProgressGap),
    ) {
        OnboardingStep.entries.forEach { step ->
            Box(
                Modifier
                    .weight(1f)
                    .height(OnboardingDimens.ProgressHeight)
                    .background(
                        if (step <= current) colors.primary else colors.surfaceContainerHigh,
                        CircleShape,
                    ),
            )
        }
    }
}

@Composable
private fun Illustration(state: OnboardingState, size: Dp, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(size)
            .background(
                Brush.radialGradient(
                    0f to ViolinTheme.accentGlow,
                    OnboardingDimens.GLOW_EDGE to colors.surface,
                ),
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        when (state.step) {
            OnboardingStep.MICROPHONE -> Box(
                Modifier
                    .size(OnboardingDimens.MicGlyphWidth, OnboardingDimens.MicGlyphHeight)
                    .background(colors.primary, CircleShape),
            )
            OnboardingStep.REFERENCE_PITCH -> Row {
                Text(
                    text = "A",
                    modifier = Modifier.alignByBaseline(),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.displayLarge.merge(ReferenceLetterStyle),
                )
                Text(
                    text = "4",
                    modifier = Modifier.alignByBaseline(),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.headlineLarge.merge(ReferenceOctaveStyle),
                )
            }
            OnboardingStep.TOLERANCE -> ToleranceIllustration(state.tolerance)
        }
    }
}

/** Miniature of the Live scale: the green zone is as wide as the chosen preset. */
@Composable
private fun ToleranceIllustration(preset: TolerancePreset) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.size(OnboardingDimens.ToleranceTrackWidth, OnboardingDimens.TolerancePillHeight),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(OnboardingDimens.ToleranceTrackHeight)
                .background(colors.surfaceContainerHigh, CircleShape),
        )
        Box(
            Modifier
                .fillMaxWidth(preset.cents * OnboardingDimens.TOLERANCE_PILL_FRACTION_PER_CENT)
                .height(OnboardingDimens.TolerancePillHeight)
                .background(ViolinTheme.zoneColors.inTune, CircleShape),
        )
    }
}

@Composable
private fun Foot(text: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(OnboardingDimens.FootGap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(OnboardingDimens.FootIconWidth, OnboardingDimens.FootIconHeight)
                .border(
                    OnboardingDimens.FootIconBorder,
                    colors.onSurfaceVariant,
                    RoundedCornerShape(OnboardingDimens.FootIconCorner),
                ),
        )
        Text(
            text = text,
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall.merge(FootStyle),
        )
    }
}
