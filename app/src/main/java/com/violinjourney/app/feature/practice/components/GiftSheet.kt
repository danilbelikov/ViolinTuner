package com.violinjourney.app.feature.practice.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.violinjourney.app.R
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.practice.Gift
import com.violinjourney.app.feature.practice.PracticeIntent
import kotlinx.coroutines.launch

private val Stage = 240.dp
private val Illustration = 160.dp

// A window too low for the full sheet (a phone on its side): the trophy gives up its size so
// that «Спасибо» is in sight without scrolling.
private val LowWindowHeight = 520.dp
private val StageLow = 104.dp
private val IllustrationLow = 80.dp
private const val NAME_SIZE = 28
private const val NAME_SIZE_LOW = 22
private val ButtonGap = 12.dp
private const val TABULAR_FIGURES = "tnum"

// The glow of the handoff: full strength in the middle, a trace at 45 %, nothing from 70 % on.
private const val GLOW_MID_STOP = 0.45f
private const val GLOW_END_STOP = 0.7f
private const val GLOW_MID_SHARE = 0.27f

/**
 * A new trophy (spec 3.13, handoff 11f). Quiet on purpose: the trophy comes forward, a soft
 * light follows, one button. Closing the sheet any way is the same «Спасибо».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GiftSheet(gift: Gift, onIntent: (PracticeIntent) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = { onIntent(PracticeIntent.GiftAccepted(gift.hours)) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        GiftSheetContent(gift, onAccept = { onIntent(PracticeIntent.GiftAccepted(gift.hours)) })
    }
}

@Composable
internal fun GiftSheetContent(gift: Gift, onAccept: () -> Unit, modifier: Modifier = Modifier, animated: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    val glowColor = ViolinTheme.progressColors.giftGlow
    val name = stringArrayResource(R.array.progress_trophy_names).getOrElse(gift.index) { "" }
    val low = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() } < LowWindowHeight

    // Keyed by the mark: the second gift of a row of them comes forward like the first.
    val arrival = remember(gift.hours) { Animatable(if (animated) 0f else 1f) }
    val glow = remember(gift.hours) { Animatable(if (animated) 0f else 1f) }
    LaunchedEffect(gift.hours) {
        launch { arrival.animateTo(1f, tween(ProgressMotion.GIFT_IN_MS, easing = ProgressMotion.GiftIn)) }
        launch { glow.animateTo(1f, tween(ProgressMotion.GIFT_GLOW_MS, delayMillis = ProgressMotion.GIFT_GLOW_DELAY_MS)) }
    }

    // Scrolls when it has to: a low landscape window is shorter than the sheet.
    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        SheetColumn {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.gift_title),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(
                    modifier = Modifier
                        .size(if (low) StageLow else Stage)
                        .drawBehind {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    0f to glowColor,
                                    GLOW_MID_STOP to glowColor.copy(alpha = glowColor.alpha * GLOW_MID_SHARE),
                                    GLOW_END_STOP to Color.Transparent,
                                ),
                                alpha = glow.value,
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    TrophyIcon(
                        hours = gift.hours,
                        locked = false,
                        size = if (low) IllustrationLow else Illustration,
                        modifier = Modifier.graphicsLayer {
                            val scale = ProgressMotion.GIFT_SCALE_FROM + (1f - ProgressMotion.GIFT_SCALE_FROM) * arrival.value
                            scaleX = scale
                            scaleY = scale
                            alpha = arrival.value
                        },
                    )
                }
                Text(
                    text = name,
                    color = colors.onSurface,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = (if (low) NAME_SIZE_LOW else NAME_SIZE).sp, lineHeight = 1.1.em, fontWeight = FontWeight.Bold,
                    ),
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = giftHours(gift.hours) + "\n" + Formats.dayAndMonth(gift.awardedDate),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp, fontFeatureSettings = TABULAR_FIGURES),
                    textAlign = TextAlign.Center,
                )
                if (!low) Spacer(Modifier.height(ButtonGap))
                PrimaryButton(text = stringResource(R.string.gift_thanks), onClick = onAccept)
            }
        }
    }
}

/** «1 час за скрипкой», «10 часов за скрипкой», «2500 часов за скрипкой». */
@Composable
private fun giftHours(hours: Int): String = stringResource(
    Formats.plural(hours, R.string.gift_hours_one, R.string.gift_hours_few, R.string.gift_hours_many),
    Formats.grouped(hours.toLong()),
)
