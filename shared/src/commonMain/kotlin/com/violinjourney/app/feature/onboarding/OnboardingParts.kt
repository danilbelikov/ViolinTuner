package com.violinjourney.app.feature.onboarding

import org.jetbrains.compose.resources.StringResource
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.components.AppButton
import com.violinjourney.app.core.ui.components.AppButtonStyle
import com.violinjourney.app.core.ui.components.ExactLines
import com.violinjourney.app.core.ui.components.appButtonFitsLines
import com.violinjourney.app.core.ui.components.appButtonWidth
import com.violinjourney.app.core.ui.components.currentDockMetrics
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.backup_onboarding_link
import com.violinjourney.app.shared.resources.onboarding_data_analytics_lead
import com.violinjourney.app.shared.resources.onboarding_data_analytics_text
import com.violinjourney.app.shared.resources.onboarding_data_copy_lead
import com.violinjourney.app.shared.resources.onboarding_data_copy_text
import com.violinjourney.app.shared.resources.onboarding_data_takes_lead
import com.violinjourney.app.shared.resources.onboarding_data_takes_text
import com.violinjourney.app.shared.resources.onboarding_journey_practice_lead
import com.violinjourney.app.shared.resources.onboarding_journey_practice_text
import com.violinjourney.app.shared.resources.onboarding_journey_records_lead
import com.violinjourney.app.shared.resources.onboarding_journey_records_text
import com.violinjourney.app.shared.resources.onboarding_journey_takts_lead
import com.violinjourney.app.shared.resources.onboarding_journey_takts_text
import com.violinjourney.app.shared.resources.onboarding_skip
import org.jetbrains.compose.resources.stringResource

/** Sizes of «Знакомство» (handoff new_onboarding, `sizes`, base screen 412 × 892 dp) and of the onboarding of the redesign (spec 5.29 R8). */
internal object OnboardingDimens {
    val Padding = 24.dp
    val Gap = 16.dp
    val PaddingCompact = 20.dp
    val GapCompact = 12.dp
    val TitleToBody = 10.dp

    /** The picture takes what the text leaves, but not less than this; on a narrow phone less. */
    val ArtMin = 280.dp
    val ArtMinCompact = 180.dp

    /** Below this height the picture of the setup gives way to the text entirely; the one of the introduction keeps [ArtUnderSkip]. */
    val ArtOffBelow = 520.dp

    /** The picture of the setup on «Микрофон», «Эталон» and «Допуск» ([SetupArtHeight]). */
    val SetupArtSteps = listOf(220.dp, 200.dp, 150.dp)

    /** …and no higher than this in a window lower than [SetupArtCompactBelow] or on a narrow phone. */
    val SetupArtCompactMax = 200.dp
    val SetupArtCompactBelow = 760.dp
    val LandscapeFade = 120.dp
    val CompactBelowWidth = 380.dp
    val MaxTextWidth = 560.dp

    /** The strip of the way: segments 6 high, a capsule each, 6 apart, 18 at the break of the parts. */
    val ProgressSegment = 6.dp
    val ProgressGap = 6.dp
    val ProgressPartGap = 18.dp

    /** The strip 16 under the picture, its label 8 under the strip, the title 12 under the label. */
    val ProgressTop = 16.dp
    val ProgressToLabel = 8.dp
    val ProgressToTitle = 12.dp

    /** The least room between the part and the count of the label on one line. */
    val LabelGap = 12.dp

    /** The plate of the icon of a row: 40, 36 lying, rounded 12; the icon on it 22. */
    val RowPlate = 40.dp
    val RowPlateLandscape = 36.dp
    val RowPlateCorner = 12.dp
    val RowIcon = 22.dp
    val RowGap = 14.dp
    val RowTextTop = 9.dp
    val RowTextTopLandscape = 7.dp
    val RowsGapLandscape = 8.dp

    /** The card of a hint of the setup: rounded 14, fields 12 / 14, the icon 18 and 10 to the words, 14 under what it explains. */
    val HintCorner = 14.dp
    val HintPaddingVertical = 12.dp
    val HintPaddingHorizontal = 14.dp
    val HintIcon = 18.dp
    val HintIconGap = 10.dp
    val HintTop = 14.dp

    /** The buttons of the reference 18 under the words, the cards of the tolerance 16 under the title, their caption 10 under them. */
    val A4Top = 18.dp
    val CardsTop = 16.dp
    val CaptionTop = 10.dp

    /** Where the buttons of the reference stand right under the title ([SetupWordsMath.choiceFirst]), its words come 14 under them. */
    val ChoiceToText = 14.dp

    /** «Пока играете…» stands 6 over the button. */
    val FootToCta = 6.dp
    val CtaTop = 6.dp

    /** Between the button and «У меня есть копия данных» beside it lying. */
    val LinkGap = 8.dp
    val SkipHeight = 48.dp
    val SkipInset = 8.dp
    val SkipPaddingHorizontal = 16.dp

    /**
     * The least picture of the introduction upright in a window lower than [ArtOffBelow]: the strip, 16 under it, stands under
     * «Пропустить» in its corner (8 + 48), never under the button (the review of stage 120). Declared after the sizes it is made of.
     */
    val ArtUnderSkip = SkipInset + SkipHeight - ProgressTop

    /** Words that go on past the edge of their scroll fade into the ground over this, rather than being cut mid-line. */
    val ScrollFade = 24.dp
}

/** «У меня есть копия данных» goes on two lines beside the button lying, not three (spec 3.36.8). */
private const val LINK_LINES_BESIDE = 2

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
    val Skip = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)

    /**
     * The label of the strip: 12 sp / 700, capitals, 0.06 em apart (spec 5.29 R8), in lines of 16 exactly ([ExactLines]): Android would
     * pad each line back to Manrope's own 1.37 em — at the font 1.3 a label of two lines stood 1.1 dp taller than its lines, and the strip
     * and the row lying with it (the lead's check of stage 120). On iOS the lines are 16 already.
     */
    val ProgressLabel = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.06.em,
        lineHeightStyle = ExactLines,
    )

    /** The words of a hint card: 14 sp, a line of 1.45 (spec 5.29 R8). */
    val Hint = TextStyle(fontSize = 14.sp, lineHeight = 20.3.sp)

    /** The caption under the cards of the tolerance: 13 sp (spec 5.29 R8). */
    val Caption = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)

    private fun title(size: TextUnit, line: TextUnit) =
        TextStyle(fontSize = size, lineHeight = line, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
}

/** How a screen of the onboarding is laid out: by the shape of the window, not by the configuration. */
internal enum class OnboardingLayout { PORTRAIT, COMPACT, LANDSCAPE }

/**
 * A row of the introduction: an icon on a plate, the start of the sentence in bold. [highlighted] — the one row of a page to remember,
 * the copy of the fourth (spec 3.36.8): its plate in the accent, the icon in the colour of the words of the main button.
 */
internal class IntroRow(val icon: ImageVector, val lead: StringResource, val text: StringResource, val highlighted: Boolean = false)

internal val JourneyRows = listOf(
    IntroRow(AppIcons.Timer, Res.string.onboarding_journey_practice_lead, Res.string.onboarding_journey_practice_text),
    IntroRow(AppIcons.Tape, Res.string.onboarding_journey_records_lead, Res.string.onboarding_journey_records_text),
    IntroRow(AppIcons.Takt, Res.string.onboarding_journey_takts_lead, Res.string.onboarding_journey_takts_text),
)

internal val DataRows = listOf(
    IntroRow(AppIcons.SaveCopy, Res.string.onboarding_data_copy_lead, Res.string.onboarding_data_copy_text, highlighted = true),
    IntroRow(AppIcons.Trash, Res.string.onboarding_data_takes_lead, Res.string.onboarding_data_takes_text),
    // The row the page waited for (spec 3.33): it may only promise what the app actually does,
    // and the switch it points at exists since spec 3.34; its icon is the chart of the row of «Настройки» (3.36.8).
    IntroRow(AppIcons.Chart, Res.string.onboarding_data_analytics_lead, Res.string.onboarding_data_analytics_text),
)

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
    val plate: Dp = if (landscape) OnboardingDimens.RowPlateLandscape else OnboardingDimens.RowPlate
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
                .size(plate)
                .background(if (row.highlighted) colors.primary else ViolinTheme.accentSoft, RoundedCornerShape(OnboardingDimens.RowPlateCorner)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                row.icon,
                contentDescription = null,
                tint = if (row.highlighted) colors.onPrimary else colors.primary,
                modifier = Modifier.size(OnboardingDimens.RowIcon),
            )
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
internal fun OnboardingTitle(text: StringResource, style: TextStyle, modifier: Modifier = Modifier) = OnboardingTitle(stringResource(text), style, modifier)

@Composable
internal fun OnboardingTitle(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    Text(text = text, color = MaterialTheme.colorScheme.onSurface, style = onboardingTitleStyle(style), modifier = modifier)
}

@Composable
internal fun OnboardingBody(text: StringResource, style: TextStyle, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(text),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = onboardingBodyStyle(style),
        modifier = modifier,
    )
}

/** A title of the onboarding as it is drawn, in [style] — the size of its layout; what measures it asks the same. */
@Composable
internal fun onboardingTitleStyle(style: TextStyle): TextStyle = MaterialTheme.typography.headlineLarge.merge(style)

/** The words of a page or a step as they are drawn, in [style]. */
@Composable
internal fun onboardingBodyStyle(style: TextStyle): TextStyle = MaterialTheme.typography.bodyLarge.merge(style)

/** The words of a hint card as they are drawn ([HintCard]). */
@Composable
internal fun hintStyle(): TextStyle = MaterialTheme.typography.bodyMedium.merge(OnboardingType.Hint)

/** One sentence of a hint: its [lead] in bold [leadColor], then the [text], joined with a space (D8). */
internal fun hintWords(lead: String, text: String, leadColor: Color): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = leadColor)) { append(lead) }
    append(' ')
    append(text)
}

/** «Пока играете, ничего нажимать не нужно» — out of the scroll, right over the button (spec 3.36.8): read as it is pressed. */
@Composable
internal fun OnboardingFoot(text: StringResource, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(text),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium.merge(OnboardingType.Foot),
        modifier = modifier.fillMaxWidth(),
    )
}

/** The caption under the cards of «Допуск»: 13 sp, onSurfaceVariant (spec 3.36.8). */
@Composable
internal fun OnboardingCaption(text: StringResource, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(text),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall.merge(OnboardingType.Caption),
        modifier = modifier,
    )
}

/**
 * A hint of the setup (spec 3.36.8, 5.29 R8): a card on surfaceContainer, an icon and one sentence — its [lead] in bold, then the
 * [text], joined with a space as the rows of the introduction are. Read by a reader as its words; the icon is not said.
 */
@Composable
internal fun HintCard(icon: ImageVector, lead: StringResource, text: StringResource, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val style = hintStyle()
    // the icon stands on the middle of the first line, whatever the font
    val firstLine = with(LocalDensity.current) { style.lineHeight.toDp() }
    val words = hintWords(stringResource(lead), stringResource(text), colors.onSurface)
    Row(
        modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer, RoundedCornerShape(OnboardingDimens.HintCorner))
            .padding(horizontal = OnboardingDimens.HintPaddingHorizontal, vertical = OnboardingDimens.HintPaddingVertical),
    ) {
        AppIcon(
            icon,
            contentDescription = null,
            modifier = Modifier.padding(top = ((firstLine - OnboardingDimens.HintIcon) / 2).coerceAtLeast(0.dp)),
            size = OnboardingDimens.HintIcon,
            tint = colors.onSurfaceVariant,
        )
        Spacer(Modifier.width(OnboardingDimens.HintIconGap))
        Text(text = words, color = colors.onSurfaceVariant, style = style)
    }
}

/**
 * The words of a page that scroll, fading into the [ground] at an edge past which they go on (spec 3.36.8: no line is cut mid-way
 * at the edge of the scroll, lying on 640 × 360): the fade is drawn over the window of the scroll, only while there is more beyond
 * that edge, and read in the draw phase. Put before the scroll itself.
 */
internal fun Modifier.scrollEdges(scroll: ScrollState, ground: Color, fade: Dp = OnboardingDimens.ScrollFade): Modifier = drawWithContent {
    drawContent()
    val height = fade.toPx().coerceAtMost(size.height / 2)
    if (height <= 0f) return@drawWithContent
    if (scroll.canScrollBackward) {
        drawRect(Brush.verticalGradient(listOf(ground, ground.copy(alpha = 0f)), startY = 0f, endY = height), size = Size(size.width, height))
    }
    if (scroll.canScrollForward) {
        val top = size.height - height
        drawRect(
            Brush.verticalGradient(listOf(ground.copy(alpha = 0f), ground), startY = top, endY = size.height),
            topLeft = Offset(0f, top),
            size = Size(size.width, height),
        )
    }
}

/**
 * The main button of a screen of the onboarding — the [AppButton] of the redesign (spec 3.36.8, 5.29 R8): the whole width upright, as
 * wide as its words lying; 48 in a window no higher than 360 dp. [icon] — the microphone of «Разрешить микрофон».
 */
@Composable
internal fun OnboardingCta(text: StringResource, wide: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    AppButton(
        text = stringResource(text),
        onClick = onClick,
        modifier = if (wide) modifier.fillMaxWidth() else modifier,
        icon = icon,
        compact = currentDockMetrics().compact,
    )
}

/** «У меня есть копия данных» under the button of the first page (spec 3.20, 3.33): a word button of 48 in the accent. */
@Composable
internal fun BackupLink(onClick: () -> Unit, modifier: Modifier = Modifier) {
    AppButton(stringResource(Res.string.backup_onboarding_link), onClick, modifier, style = AppButtonStyle.Text)
}

/**
 * The button of a page lying (spec 3.33, 3.36.8): as wide as its words, and on the first page «У меня есть копия данных» beside it —
 * on up to two lines with every word whole; where it would take three or break a word (de, a large font, a narrow column) it goes
 * under the button. Measured, so the first frame is already right.
 */
@Composable
internal fun LandscapeCta(text: StringResource, onClick: () -> Unit, onHaveBackup: (() -> Unit)?) {
    if (onHaveBackup == null) {
        OnboardingCta(text, wide = false, onClick = onClick)
        return
    }
    val words = stringResource(text)
    val link = stringResource(Res.string.backup_onboarding_link)
    val compact = currentDockMetrics().compact
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val room = maxWidth - appButtonWidth(words, AppButtonStyle.Main, compact = compact) - OnboardingDimens.LinkGap
        val beside = room > 0.dp && appButtonFitsLines(link, room, AppButtonStyle.Text, lines = LINK_LINES_BESIDE)
        if (beside) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OnboardingCta(text, wide = false, onClick = onClick)
                Spacer(Modifier.width(OnboardingDimens.LinkGap))
                BackupLink(onHaveBackup, Modifier.weight(1f, fill = false))
            }
        } else {
            Column {
                OnboardingCta(text, wide = false, onClick = onClick)
                BackupLink(onHaveBackup)
            }
        }
    }
}

/**
 * «Пропустить» over the picture of the first three pages; lying — at the end of the row of the strip. [enabled] false — its twin,
 * which only keeps its place in that row where it is not there ([LandscapeTopRow]).
 */
@Composable
internal fun SkipButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TextButton(
        onClick = onClick,
        modifier = modifier.height(OnboardingDimens.SkipHeight),
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = OnboardingDimens.SkipPaddingHorizontal),
    ) {
        Text(
            stringResource(Res.string.onboarding_skip),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge.merge(OnboardingType.Skip),
        )
    }
}
