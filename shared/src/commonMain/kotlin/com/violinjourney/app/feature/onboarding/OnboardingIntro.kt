package com.violinjourney.app.feature.onboarding

import org.jetbrains.compose.resources.StringResource
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.onboarding.art.ArtFit
import com.violinjourney.app.feature.onboarding.art.OnboardingArtCanvas
import com.violinjourney.app.feature.onboarding.art.OnboardingArtData
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.app_name
import com.violinjourney.app.shared.resources.onboarding_data_cta
import com.violinjourney.app.shared.resources.onboarding_data_text
import com.violinjourney.app.shared.resources.onboarding_data_title
import com.violinjourney.app.shared.resources.onboarding_journey_title
import com.violinjourney.app.shared.resources.onboarding_live_foot
import com.violinjourney.app.shared.resources.onboarding_live_text
import com.violinjourney.app.shared.resources.onboarding_live_title
import com.violinjourney.app.shared.resources.onboarding_next
import com.violinjourney.app.shared.resources.onboarding_welcome_cta
import com.violinjourney.app.shared.resources.onboarding_welcome_text
import kotlin.math.abs
import kotlin.math.floor

private val IntroScenes by lazy { listOf(OnboardingArtData.welcome, OnboardingArtData.live, OnboardingArtData.road, OnboardingArtData.data) }

/** «Пропустить» dissolves the land and the text into the page about the data; the sky stands (36h2). */
private const val SKIP_HALF_MS = 225
private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * The four pages of the introduction (spec 3.33, 36a–36d). The pager holds the words; the picture is one
 * canvas behind it that follows the swipe, so the sky has no seam between pages. The view model owns the
 * page: a swipe tells it, and a change it makes — a button, «Пропустить», back — moves the pager.
 */
@Composable
internal fun OnboardingIntro(
    step: OnboardingStep,
    layout: OnboardingLayout,
    onIntent: (OnboardingIntent) -> Unit,
    onHaveBackup: (() -> Unit)?,
) {
    val still = LocalReduceMotion.current
    val pager = rememberPagerState(initialPage = step.indexInPart) { OnboardingStep.intro.size }
    val fade = remember { Animatable(1f) }
    val currentOnIntent by rememberUpdatedState(onIntent)

    LaunchedEffect(step) {
        val target = step.indexInPart
        if (step.part != OnboardingPart.INTRO || pager.currentPage == target) return@LaunchedEffect
        when {
            still -> pager.scrollToPage(target)
            abs(target - pager.currentPage) > 1 -> {
                fade.animateTo(0f, tween(SKIP_HALF_MS, easing = Emphasized))
                pager.scrollToPage(target)
                fade.animateTo(1f, tween(SKIP_HALF_MS, easing = Emphasized))
            }
            else -> pager.animateScrollToPage(target)
        }
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { currentOnIntent(OnboardingIntent.PageShown(it)) }
    }

    val position = { pager.currentPage + pager.currentPageOffsetFraction }
    val showSkip = { (OnboardingStep.intro.lastIndex - position()).coerceIn(0f, 1f) }
    val skip: @Composable (Modifier) -> Unit = { modifier ->
        SkipButton(
            onClick = { if (showSkip() > 0.5f) onIntent(OnboardingIntent.SkipClicked) },
            modifier = modifier.graphicsLayer { alpha = showSkip() },
        )
    }

    if (layout == OnboardingLayout.LANDSCAPE) {
        Row(Modifier.fillMaxSize()) {
            OnboardingArtCanvas(
                scenes = IntroScenes,
                position = position,
                shownPage = pager.settledPage,
                fit = ArtFit.CENTER,
                still = still,
                zoneColors = ViolinTheme.zoneColors,
                surface = MaterialTheme.colorScheme.surface,
                foregroundAlpha = { fade.value },
                fadeEndPx = with(LocalDensity.current) { OnboardingDimens.LandscapeFade.toPx() },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                IntroPager(pager, layout, fade, onIntent, onHaveBackup, onArtHeight = null)
                skip(Modifier.align(Alignment.TopEnd).padding(OnboardingDimens.SkipInset))
            }
        }
    } else {
        val artHeights = remember { mutableStateMapOf<Int, Int>() }
        Box(Modifier.fillMaxSize()) {
            OnboardingArtCanvas(
                scenes = IntroScenes,
                position = position,
                shownPage = pager.settledPage,
                fit = ArtFit.BOTTOM,
                still = still,
                zoneColors = ViolinTheme.zoneColors,
                surface = MaterialTheme.colorScheme.surface,
                foregroundAlpha = { fade.value },
                height = { artHeightAt(position(), artHeights) },
                modifier = Modifier.fillMaxSize(),
            )
            IntroPager(pager, layout, fade, onIntent, onHaveBackup, onArtHeight = { page, px -> artHeights[page] = px })
            skip(Modifier.align(Alignment.TopEnd).padding(OnboardingDimens.SkipInset))
        }
    }
}

/** The picture is as tall as the page leaves it, and between two pages in between. */
private fun artHeightAt(position: Float, heights: Map<Int, Int>): Float {
    val left = floor(position).toInt()
    val fraction = position - left
    val a = heights[left] ?: heights[left + 1] ?: return 0f
    val b = heights[left + 1] ?: a
    return (a + (b - a) * fraction).let { if (it < 0f) 0f else it }
}

@Composable
private fun IntroPager(
    pager: PagerState,
    layout: OnboardingLayout,
    fade: Animatable<Float, *>,
    onIntent: (OnboardingIntent) -> Unit,
    onHaveBackup: (() -> Unit)?,
    onArtHeight: ((page: Int, px: Int) -> Unit)?,
) {
    HorizontalPager(
        state = pager,
        beyondViewportPageCount = 1,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade.value },
    ) { page ->
        val step = OnboardingStep.intro[page]
        if (layout == OnboardingLayout.LANDSCAPE) {
            LandscapePage(step, onIntent, onHaveBackup)
        } else {
            PortraitPage(step, layout, onIntent, onHaveBackup, onArtHeight = { onArtHeight?.invoke(page, it) })
        }
    }
}

@Composable
private fun PortraitPage(
    step: OnboardingStep,
    layout: OnboardingLayout,
    onIntent: (OnboardingIntent) -> Unit,
    onHaveBackup: (() -> Unit)?,
    onArtHeight: (Int) -> Unit,
) {
    val compact = layout == OnboardingLayout.COMPACT
    val padding = if (compact) OnboardingDimens.PaddingCompact else OnboardingDimens.Padding
    val gap = if (compact) OnboardingDimens.GapCompact else OnboardingDimens.Gap
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        val available = maxHeight
        val minArt = when {
            maxHeight < OnboardingDimens.ArtOffBelow -> 0.dp
            compact -> OnboardingDimens.ArtMinCompact
            else -> OnboardingDimens.ArtMin
        }
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onSizeChanged { size -> onArtHeight(size.height) },
            )
            Column(
                Modifier
                    .widthIn(max = OnboardingDimens.MaxTextWidth)
                    .fillMaxWidth()
                    .heightIn(max = available - minArt)
                    .padding(start = padding, end = padding, bottom = padding),
            ) {
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(gap),
                ) {
                    PageDots(step.indexInPart, OnboardingStep.intro.size)
                    PageWords(step, layout)
                }
                Spacer(Modifier.height(gap + OnboardingDimens.CtaTop))
                OnboardingCta(ctaOf(step), wide = true, onClick = { onIntent(OnboardingIntent.PrimaryClicked) })
                if (step == OnboardingStep.WELCOME && onHaveBackup != null) {
                    BackupLink(onHaveBackup, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun LandscapePage(step: OnboardingStep, onIntent: (OnboardingIntent) -> Unit, onHaveBackup: (() -> Unit)?) {
    val layout = OnboardingLayout.LANDSCAPE
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = OnboardingDimens.SkipInset, end = OnboardingDimens.Padding, bottom = OnboardingDimens.PaddingCompact),
    ) {
        Box(Modifier.height(OnboardingDimens.SkipHeight + OnboardingDimens.SkipInset), contentAlignment = Alignment.CenterStart) {
            PageDots(step.indexInPart, OnboardingStep.intro.size)
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(OnboardingDimens.TitleToBody),
        ) {
            PageWords(step, layout)
        }
        Spacer(Modifier.height(OnboardingDimens.GapCompact))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OnboardingCta(ctaOf(step), wide = false, onClick = { onIntent(OnboardingIntent.PrimaryClicked) })
            if (step == OnboardingStep.WELCOME && onHaveBackup != null) {
                BackupLink(onHaveBackup, Modifier.padding(start = OnboardingDimens.SkipInset))
            }
        }
    }
}

/** Title, words and rows of a page. */
@Composable
private fun PageWords(step: OnboardingStep, layout: OnboardingLayout) {
    val titleStyle = when {
        layout == OnboardingLayout.LANDSCAPE -> OnboardingType.TitleLandscape
        layout == OnboardingLayout.COMPACT -> OnboardingType.TitleCompact
        step == OnboardingStep.WELCOME -> OnboardingType.WelcomeTitle
        else -> OnboardingType.Title
    }
    val bodyStyle = when (layout) {
        OnboardingLayout.PORTRAIT -> OnboardingType.Body
        OnboardingLayout.COMPACT -> OnboardingType.BodyCompact
        OnboardingLayout.LANDSCAPE -> OnboardingType.BodyLandscape
    }
    val gap = if (layout == OnboardingLayout.PORTRAIT) OnboardingDimens.Gap else OnboardingDimens.GapCompact
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        Column(verticalArrangement = Arrangement.spacedBy(OnboardingDimens.TitleToBody)) {
            when (step) {
                OnboardingStep.WELCOME -> {
                    OnboardingTitle(Res.string.app_name, titleStyle)
                    OnboardingBody(Res.string.onboarding_welcome_text, bodyStyle)
                }
                OnboardingStep.LIVE -> {
                    OnboardingTitle(Res.string.onboarding_live_title, titleStyle)
                    OnboardingBody(Res.string.onboarding_live_text, bodyStyle)
                }
                OnboardingStep.JOURNEY -> OnboardingTitle(Res.string.onboarding_journey_title, titleStyle)
                OnboardingStep.DATA -> {
                    OnboardingTitle(Res.string.onboarding_data_title, titleStyle)
                    OnboardingBody(Res.string.onboarding_data_text, bodyStyle)
                }
                else -> Unit
            }
        }
        when (step) {
            OnboardingStep.LIVE -> OnboardingFoot(Res.string.onboarding_live_foot)
            OnboardingStep.JOURNEY -> IntroRows(JourneyRows, layout)
            OnboardingStep.DATA -> IntroRows(DataRows, layout)
            else -> Unit
        }
    }
}

private fun ctaOf(step: OnboardingStep): StringResource = when (step) {
    OnboardingStep.WELCOME -> Res.string.onboarding_welcome_cta
    OnboardingStep.DATA -> Res.string.onboarding_data_cta
    else -> Res.string.onboarding_next
}
