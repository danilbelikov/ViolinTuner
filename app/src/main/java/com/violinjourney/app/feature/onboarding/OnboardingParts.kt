package com.violinjourney.app.feature.onboarding

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.R
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.ViolinTheme

/** Sizes of «Знакомство» (handoff new_onboarding, `sizes`, base screen 412 × 892 dp). */
internal object OnboardingDimens {
    val Padding = 24.dp
    val Gap = 16.dp
    val PaddingCompact = 20.dp
    val GapCompact = 12.dp
    val TitleToBody = 10.dp

    /** The picture takes what the text leaves, but not less than this; on a narrow phone less. */
    val ArtMin = 280.dp
    val ArtMinCompact = 180.dp

    /** Below this height the picture gives way to the text entirely. */
    val ArtOffBelow = 520.dp
    val SetupArt = 300.dp
    val SetupArtCompact = 200.dp
    val SetupArtCompactBelow = 760.dp
    val LandscapeFade = 120.dp
    val CompactBelowWidth = 380.dp
    val MaxTextWidth = 560.dp

    val Dot = 6.dp
    val DotActive = 20.dp
    val DotGap = 6.dp
    val Bar = 4.dp
    val BarGap = 6.dp
    val IndicatorToLabel = 10.dp

    val RowCircle = 40.dp
    val RowCircleLandscape = 36.dp
    val RowIcon = 22.dp
    val RowGap = 14.dp
    val RowTextTop = 9.dp
    val RowTextTopLandscape = 7.dp
    val RowsGapLandscape = 8.dp

    val CtaHeight = 56.dp
    val CtaCorner = 28.dp
    val CtaPaddingLandscape = 40.dp
    val CtaTop = 6.dp
    val LinkHeight = 48.dp
    val SkipHeight = 48.dp
    val SkipInset = 8.dp
    val SkipPaddingHorizontal = 16.dp
}

internal object OnboardingType {
    val WelcomeTitle = title(36.sp, 40.sp)
    val Title = title(30.sp, 36.sp)
    val TitleCompact = title(24.sp, 30.sp)
    val TitleLandscape = title(24.sp, 28.sp)
    val Body = TextStyle(fontSize = 16.sp, lineHeight = 24.sp)
    val BodyCompact = TextStyle(fontSize = 14.sp, lineHeight = 20.sp)
    val BodyLandscape = TextStyle(fontSize = 15.sp, lineHeight = 22.sp)
    val Row = TextStyle(fontSize = 15.sp, lineHeight = 22.sp)
    val RowCompact = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
    val RowLandscape = TextStyle(fontSize = 13.5.sp, lineHeight = 19.sp)
    val Foot = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
    val Cta = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold)
    val Link = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    val Skip = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    val StepLabel = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp)

    private fun title(size: TextUnit, line: TextUnit) =
        TextStyle(fontSize = size, lineHeight = line, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
}

/** How a screen of the onboarding is laid out: by the shape of the window, not by the configuration. */
internal enum class OnboardingLayout { PORTRAIT, COMPACT, LANDSCAPE }

/** A row of the introduction: an icon in a circle, the start of the sentence in bold. */
internal class IntroRow(val icon: ImageVector, @StringRes val lead: Int, @StringRes val text: Int)

internal val JourneyRows = listOf(
    IntroRow(AppIcons.Timer, R.string.onboarding_journey_practice_lead, R.string.onboarding_journey_practice_text),
    IntroRow(AppIcons.Tape, R.string.onboarding_journey_records_lead, R.string.onboarding_journey_records_text),
    IntroRow(AppIcons.Takt, R.string.onboarding_journey_takts_lead, R.string.onboarding_journey_takts_text),
)

internal val DataRows = listOf(
    IntroRow(AppIcons.SaveCopy, R.string.onboarding_data_copy_lead, R.string.onboarding_data_copy_text),
    IntroRow(AppIcons.Trash, R.string.onboarding_data_takes_lead, R.string.onboarding_data_takes_text),
)

/** Four dots of the introduction; the page in view is a longer pill. */
@Composable
internal fun PageDots(current: Int, count: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.onboarding_page_description, current + 1, count)
    Row(
        modifier = modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(OnboardingDimens.DotGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { page ->
            val target = if (page == current) OnboardingDimens.DotActive else OnboardingDimens.Dot
            val width by animateDpAsState(target, label = "dot")
            Box(
                Modifier
                    .size(if (LocalReduceMotion.current) target else width, OnboardingDimens.Dot)
                    .background(if (page == current) MaterialTheme.colorScheme.primary else ViolinTheme.onboardingDotIdle, CircleShape),
            )
        }
    }
}

/** Three bars of the setup with «Настройка · 2 из 3» under them (36f4). */
@Composable
internal fun SetupProgress(current: Int, count: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(R.string.onboarding_setup_progress, current + 1, count)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(OnboardingDimens.IndicatorToLabel)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(OnboardingDimens.BarGap)) {
            repeat(count) { step ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(OnboardingDimens.Bar)
                        .background(if (step <= current) colors.primary else colors.surfaceContainerHigh, CircleShape),
                )
            }
        }
        Text(
            text = label.uppercase(),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall.merge(OnboardingType.StepLabel),
        )
    }
}

@Composable
internal fun IntroRows(rows: List<IntroRow>, layout: OnboardingLayout, modifier: Modifier = Modifier) {
    val landscape = layout == OnboardingLayout.LANDSCAPE
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(if (landscape) OnboardingDimens.RowsGapLandscape else OnboardingDimens.RowGap),
    ) {
        rows.forEach { row -> IntroRowView(row, layout) }
    }
}

@Composable
private fun IntroRowView(row: IntroRow, layout: OnboardingLayout) {
    val colors = MaterialTheme.colorScheme
    val landscape = layout == OnboardingLayout.LANDSCAPE
    val circle: Dp = if (landscape) OnboardingDimens.RowCircleLandscape else OnboardingDimens.RowCircle
    val style = when (layout) {
        OnboardingLayout.PORTRAIT -> OnboardingType.Row
        OnboardingLayout.COMPACT -> OnboardingType.RowCompact
        OnboardingLayout.LANDSCAPE -> OnboardingType.RowLandscape
    }
    val lead = stringResource(row.lead)
    val text = stringResource(row.text)
    Row(horizontalArrangement = Arrangement.spacedBy(OnboardingDimens.RowGap)) {
        Box(
            Modifier
                .size(circle)
                .background(colors.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(row.icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(OnboardingDimens.RowIcon))
        }
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = colors.onSurface)) { append(lead) }
                append(' ')
                append(text)
            },
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium.merge(style),
            modifier = Modifier.padding(top = if (landscape) OnboardingDimens.RowTextTopLandscape else OnboardingDimens.RowTextTop),
        )
    }
}

@Composable
internal fun OnboardingTitle(@StringRes text: Int, style: TextStyle, modifier: Modifier = Modifier) = OnboardingTitle(stringResource(text), style, modifier)

@Composable
internal fun OnboardingTitle(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    Text(text = text, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.headlineLarge.merge(style), modifier = modifier)
}

@Composable
internal fun OnboardingBody(@StringRes text: Int, style: TextStyle, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(text),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyLarge.merge(style),
        modifier = modifier,
    )
}

@Composable
internal fun OnboardingFoot(@StringRes text: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(text),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium.merge(OnboardingType.Foot),
        modifier = modifier.fillMaxWidth(),
    )
}

/** The big button: the whole width in portrait, as wide as its words in landscape. */
@Composable
internal fun OnboardingCta(@StringRes text: Int, wide: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = (if (wide) modifier.fillMaxWidth() else modifier).height(OnboardingDimens.CtaHeight),
        shape = RoundedCornerShape(OnboardingDimens.CtaCorner),
        contentPadding = if (wide) PaddingValues() else PaddingValues(horizontal = OnboardingDimens.CtaPaddingLandscape),
    ) {
        Text(text = stringResource(text), style = MaterialTheme.typography.labelLarge.merge(OnboardingType.Cta))
    }
}

/** «У меня есть копия данных» under the button of the first page (spec 3.20, 3.33). */
@Composable
internal fun BackupLink(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = OnboardingDimens.LinkHeight)) {
        Text(stringResource(R.string.backup_onboarding_link), style = MaterialTheme.typography.labelLarge.merge(OnboardingType.Link))
    }
}

/** «Пропустить» over the picture of the first three pages. */
@Composable
internal fun SkipButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onClick,
        modifier = modifier.height(OnboardingDimens.SkipHeight),
        contentPadding = PaddingValues(horizontal = OnboardingDimens.SkipPaddingHorizontal),
    ) {
        Text(
            stringResource(R.string.onboarding_skip),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge.merge(OnboardingType.Skip),
        )
    }
}
