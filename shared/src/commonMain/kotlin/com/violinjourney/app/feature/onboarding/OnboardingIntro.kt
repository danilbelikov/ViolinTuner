package com.violinjourney.app.feature.onboarding

import org.jetbrains.compose.resources.StringResource
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
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
import kotlin.math.roundToInt
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.isActive

private val IntroScenes by lazy { listOf(OnboardingArtData.welcome, OnboardingArtData.live, OnboardingArtData.road, OnboardingArtData.data) }

/** «Пропустить» answers while more than half of it is seen. */
private const val SKIP_ANSWERS_FROM = 0.5f

/** Lying, the picture takes the left half of the window and the words the right one (spec 3.33). */
private const val HALF = 0.5f

private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * The four pages of the introduction (spec 3.33, 36a–36d). The pager holds the words; the picture is one
 * canvas behind it that follows the swipe, so the sky has no seam between pages. The view model owns the
 * page: a swipe tells it, and a change it makes — a button, «Пропустить», back — moves the pager, which takes no finger until the
 * page stands (5.29 R8).
 *
 * The strip of the way (spec 3.36.8) stands outside the pager too, so it does not leave with a page: upright on the bottom edge of the
 * picture, which changes its height between pages — the strip rides that edge, read in the placement phase; lying in the top row of
 * the column of words, before «Пропустить». A page keeps its room with an unseen twin. The strip shows the page that has stopped
 * (`settledPage`), and at once the page a jump of «Пропустить» goes to, so 2–4 fill under its dissolve.
 *
 * One layout tree for both shapes: the pager and the scroll of each page are made at one place whatever the layout, so a turn of the
 * phone keeps the page and where its words were scrolled.
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
    // the page a jump of «Пропустить» goes to, while it goes
    var jumpingTo by remember { mutableStateOf<Int?>(null) }
    // The model drives the pager to a step the pager did not stop on by itself — a button, «Пропустить», back — and from the frame that
    // brings the step until the page stands there the pager takes no finger (5.29 R8). A pager on its way takes a finger before what is
    // on it: the second tap of a double tap stopped the page half-way and sent it back while the model went on — the button then
    // carried the page left behind and moved nothing — and a finger in the dissolve of «Пропустить» refused its jump. Now the tap
    // reaches the button under it, which carries its own page. Decided in the composition that brings the step, so no frame of the
    // slide takes a finger; read without observing the pager, which moves every frame.
    val driven = remember(step) {
        mutableStateOf(step.part == OnboardingPart.INTRO && !Snapshot.withoutReadObservation { pager.restsOn(step.indexInPart) })
    }

    LaunchedEffect(step) {
        val target = step.indexInPart
        if (step.part != OnboardingPart.INTRO) return@LaunchedEffect
        try {
            val far = !still && abs(target - pager.currentPage) > 1
            // A dissolve cut short — a new step half-way, back in the middle of «Пропустить» — never leaves the words
            // faded: every step but the far jump starts from the words in full.
            if (!far) fade.snapTo(1f)
            // not only «on the page»: a slide cut short by this step — back while the page slides — stands between two pages
            if (pager.restsOn(target)) return@LaunchedEffect
            when {
                still -> pager.scrollToPage(target)
                far -> try {
                    jumpingTo = target
                    fade.animateTo(0f, tween(SKIP_HALF_MS, easing = Emphasized))
                    try {
                        pager.scrollToPage(target)
                    } finally {
                        jumpingTo = null
                        driven.value = false
                        // A finger dragging the pager since before the step came (another finger pressed «Пропустить») refuses the
                        // jump (the refusal ends this effect as it always did): the words come back all the same, and the page the
                        // finger leaves the pager on is told to the model. A new step that cancelled this one brings them back itself.
                        if (currentCoroutineContext().isActive) fade.animateTo(1f, tween(SKIP_HALF_MS, easing = Emphasized))
                    }
                } finally {
                    jumpingTo = null
                }
                else -> pager.animateScrollToPage(target)
            }
        } finally {
            driven.value = false
        }
    }
    // The page the pager stands on while nothing drives it is the page in view — where a swipe left it, or a finger that was on it
    // before the model moved — and the model follows it, even back to the page it started from. A stop while the pager is driven is not
    // told: a slide cut short by back would take the model on to where the slide went and undo the back.
    val drivenNow by rememberUpdatedState(driven)
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage.takeUnless { pager.isScrollInProgress || drivenNow.value } }
            .filterNotNull()
            .collect { currentOnIntent(OnboardingIntent.PageShown(it)) }
    }

    val position = { pager.currentPage + pager.currentPageOffsetFraction }
    val showSkip = { (OnboardingStep.intro.lastIndex - position()).coerceIn(0f, 1f) }
    // Faded out on the last page, «Пропустить» is not there (spec 3.33): it neither answers a tap nor stays in the tree of
    // TalkBack and VoiceOver. The fade itself is read in the draw phase; this recomposes only when the threshold is crossed.
    val skipAnswers by remember { derivedStateOf { showSkip() > SKIP_ANSWERS_FROM } }
    val skip: @Composable (Modifier) -> Unit = { modifier ->
        SkipButton(
            // pressed from the page under it (5.29 R8): a second tap goes nowhere past the page about the data
            onClick = { if (skipAnswers) onIntent(OnboardingIntent.SkipClicked(OnboardingStep.intro[pager.currentPage])) },
            modifier = modifier
                .graphicsLayer { alpha = showSkip() }
                .then(if (skipAnswers) Modifier else Modifier.clearAndSetSemantics {}),
        )
    }
    // the strip fills when a page has stopped, and at once to where «Пропустить» goes
    val shown = OnboardingStep.intro[jumpingTo ?: pager.settledPage]
    val landscape = layout == OnboardingLayout.LANDSCAPE
    val artHeights = remember { mutableStateMapOf<Int, Int>() }
    val fadeEndPx = with(LocalDensity.current) { OnboardingDimens.LandscapeFade.toPx() }

    Box(Modifier.fillMaxSize()) {
        OnboardingArtCanvas(
            scenes = IntroScenes,
            position = position,
            shownPage = pager.settledPage,
            fit = if (landscape) ArtFit.CENTER else ArtFit.BOTTOM,
            still = still,
            zoneColors = ViolinTheme.zoneColors,
            surface = MaterialTheme.colorScheme.surface,
            foregroundAlpha = { fade.value },
            height = if (landscape) null else { { artHeightAt(position(), artHeights) } },
            fadeEndPx = if (landscape) fadeEndPx else 0f,
            modifier = if (landscape) Modifier.fillMaxHeight().fillMaxWidth(HALF) else Modifier.fillMaxSize(),
        )
        Box(if (landscape) Modifier.align(Alignment.TopEnd).fillMaxHeight().fillMaxWidth(HALF) else Modifier.fillMaxSize()) {
            IntroPager(pager, layout, fade, !driven.value, onIntent, onHaveBackup, onArtHeight = { page, px -> artHeights[page] = px })
            if (landscape) {
                LandscapeTopRow(shown, Modifier.align(Alignment.TopStart), skip = skip)
            } else {
                val padding = if (layout == OnboardingLayout.COMPACT) OnboardingDimens.PaddingCompact else OnboardingDimens.Padding
                OnboardingProgress(
                    step = shown,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        // on the edge of the picture, which moves between pages: read where the strip is placed, nothing recomposes
                        .offset { IntOffset(0, (artHeightAt(position(), artHeights) + OnboardingDimens.ProgressTop.toPx()).roundToInt()) }
                        .widthIn(max = OnboardingDimens.MaxTextWidth)
                        .fillMaxWidth()
                        .padding(horizontal = padding),
                )
                skip(Modifier.align(Alignment.TopEnd).padding(OnboardingDimens.SkipInset))
            }
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

/** The pager stands still right on [page]: neither on its way nor between two pages. */
private fun PagerState.restsOn(page: Int): Boolean = !isScrollInProgress && currentPage == page && currentPageOffsetFraction == 0f

/** [fingers] — the pager takes a swipe; not while the model drives it. */
@Composable
private fun IntroPager(
    pager: PagerState,
    layout: OnboardingLayout,
    fade: Animatable<Float, *>,
    fingers: Boolean,
    onIntent: (OnboardingIntent) -> Unit,
    onHaveBackup: (() -> Unit)?,
    onArtHeight: (page: Int, px: Int) -> Unit,
) {
    HorizontalPager(
        state = pager,
        beyondViewportPageCount = 1,
        userScrollEnabled = fingers,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade.value },
    ) { page ->
        val step = OnboardingStep.intro[page]
        // one scroll for the page whatever the layout: a turn of the phone keeps where its words were
        val scroll = rememberScrollState()
        if (layout == OnboardingLayout.LANDSCAPE) {
            LandscapePage(step, scroll, onIntent, onHaveBackup)
        } else {
            PortraitPage(step, layout, scroll, onIntent, onHaveBackup, onArtHeight = { onArtHeight(page, it) })
        }
    }
}

@Composable
private fun PortraitPage(
    step: OnboardingStep,
    layout: OnboardingLayout,
    scroll: ScrollState,
    onIntent: (OnboardingIntent) -> Unit,
    onHaveBackup: (() -> Unit)?,
    onArtHeight: (Int) -> Unit,
) {
    val compact = layout == OnboardingLayout.COMPACT
    val padding = if (compact) OnboardingDimens.PaddingCompact else OnboardingDimens.Padding
    val gap = if (compact) OnboardingDimens.GapCompact else OnboardingDimens.Gap
    val ground = MaterialTheme.colorScheme.surface
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        val available = maxHeight
        // Lower than 520 the picture gives way to the words, but not all of it: «Пропустить» stands in its corner over the first three
        // pages, and the strip on its edge must stand under the button, not under its letters (the review of stage 120).
        val minArt = when {
            maxHeight < OnboardingDimens.ArtOffBelow -> OnboardingDimens.ArtUnderSkip
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
                // the room of the strip, which stands over the pager on the edge of the picture
                Spacer(Modifier.height(OnboardingDimens.ProgressTop))
                OnboardingProgress(step, placeholder = true)
                Spacer(Modifier.height(OnboardingDimens.ProgressToTitle))
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .scrollEdges(scroll, ground)
                        .verticalScroll(scroll),
                ) {
                    PageWords(step, layout)
                }
                if (step == OnboardingStep.LIVE) {
                    Spacer(Modifier.height(gap))
                    OnboardingFoot(Res.string.onboarding_live_foot)
                    Spacer(Modifier.height(OnboardingDimens.FootToCta))
                } else {
                    Spacer(Modifier.height(gap + OnboardingDimens.CtaTop))
                }
                OnboardingCta(ctaOf(step), wide = true, onClick = { onIntent(OnboardingIntent.PrimaryClicked(step)) })
                if (step == OnboardingStep.WELCOME && onHaveBackup != null) {
                    BackupLink(onHaveBackup, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun LandscapePage(step: OnboardingStep, scroll: ScrollState, onIntent: (OnboardingIntent) -> Unit, onHaveBackup: (() -> Unit)?) {
    val layout = OnboardingLayout.LANDSCAPE
    Column(Modifier.fillMaxSize()) {
        // the room of the row of the strip and «Пропустить», which stands over the pager
        LandscapeTopRow(step, placeholder = true)
        Column(
            Modifier
                .weight(1f)
                .padding(start = OnboardingDimens.SkipInset, end = OnboardingDimens.Padding, bottom = OnboardingDimens.PaddingCompact),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .scrollEdges(scroll, MaterialTheme.colorScheme.surface)
                    .verticalScroll(scroll),
            ) {
                PageWords(step, layout)
            }
            Spacer(Modifier.height(OnboardingDimens.GapCompact))
            // the promise of Live is read as the button is pressed, and lying it no longer leaves with the text (spec 3.36.8)
            if (step == OnboardingStep.LIVE) {
                OnboardingFoot(Res.string.onboarding_live_foot)
                Spacer(Modifier.height(OnboardingDimens.FootToCta))
            }
            LandscapeCta(
                text = ctaOf(step),
                onClick = { onIntent(OnboardingIntent.PrimaryClicked(step)) },
                onHaveBackup = onHaveBackup.takeIf { step == OnboardingStep.WELCOME },
            )
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
