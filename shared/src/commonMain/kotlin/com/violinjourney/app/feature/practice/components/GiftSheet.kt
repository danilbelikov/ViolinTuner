package com.violinjourney.app.feature.practice.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.components.AppSheetButtons
import com.violinjourney.app.core.ui.components.SectionLabel
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.AppShapes
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.practice.Gift
import com.violinjourney.app.feature.practice.NextTrophy
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.gift_description
import com.violinjourney.app.shared.resources.gift_hours_few
import com.violinjourney.app.shared.resources.gift_hours_many
import com.violinjourney.app.shared.resources.gift_hours_one
import com.violinjourney.app.shared.resources.gift_line
import com.violinjourney.app.shared.resources.gift_next
import com.violinjourney.app.shared.resources.gift_next_description
import com.violinjourney.app.shared.resources.gift_next_line
import com.violinjourney.app.shared.resources.gift_thanks
import com.violinjourney.app.shared.resources.gift_title
import com.violinjourney.app.shared.resources.progress_trophy_names
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

// «Подарок» (spec 3.36.3, 5.29 R3; practice-sheets.html 5): the stage 200 with its glow, the trophy 120; in a low window 104 / 80.
private val Stage = 200.dp
private val Illustration = 120.dp
private val StageLow = 104.dp
private val IllustrationLow = 80.dp
private val StageTop = 8.dp
private val StageBottom = 4.dp
/**
 * The name, 30 sp in a low window too (5.29 R3 names only the stage and the trophy smaller there): «Спасибо» stands pinned at the bottom
 * of the sheet whatever the name takes, and the widest name of any language, «Подбородник», is 202 dp at 30 sp and 262 at a font of
 * 1.3 — one line in the narrowest sheet (320 dp at 360), so nothing has to shrink it.
 */
private const val NAME_SIZE = 30
private const val NAME_TRACKING = -0.02
private const val LINE_SIZE = 15
private val LineTop = 4.dp

// The card «Дальше» (5.29 R3): on the ground of the screen, 18, fields 12 / 14; the outline 36 in the third level of text.
private val NextTop = 18.dp
private val NextPaddingVertical = 12.dp
private val NextPaddingHorizontal = 14.dp
private val NextIcon = 36.dp
private val NextGap = 12.dp
private const val NEXT_TITLE_SIZE = 15
private const val NEXT_LINE_SIZE = 13
private const val TABULAR_FIGURES = "tnum"

// The glow of the handoff: full strength in the middle, a trace at 45 %, nothing from 70 % on.
private const val GLOW_MID_STOP = 0.45f
private const val GLOW_END_STOP = 0.7f
private const val GLOW_MID_SHARE = 0.27f

/**
 * A new trophy (spec 3.13, 3.36.3; practice-sheets.html 5): «НОВЫЙ ТРОФЕЙ», the trophy large in a soft glow — it comes forward once,
 * by scale (3.16) — its name and «50 часов за скрипкой · 27 сентября» on one line; under it the quiet card of the trophy after it —
 * its outline, «Дальше — Смычок», «100 ч · ещё 50 ч»: a horizon, not a debt. In a [low] window the trophy is smaller and the card is
 * not shown: «Спасибо» ([GiftButtons], at the bottom of the sheet) is in sight without scrolling. TalkBack reads it as one paragraph.
 */
@Composable
fun GiftSheetContent(gift: Gift, modifier: Modifier = Modifier, low: Boolean = lowSheetWindow(), animated: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    val glowColor = ViolinTheme.progressColors.giftGlow
    val names = stringArrayResource(Res.array.progress_trophy_names)
    val name = names.getOrElse(gift.index) { "" }
    val title = stringResource(Res.string.gift_title)
    val still = LocalReduceMotion.current || !animated

    // Keyed by the mark: the second gift of a row of them comes forward like the first.
    val arrival = remember(gift.hours) { Animatable(if (still) 1f else 0f) }
    val glow = remember(gift.hours) { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(gift.hours) {
        if (still) return@LaunchedEffect
        launch { arrival.animateTo(1f, tween(ProgressMotion.GIFT_IN_MS, easing = ProgressMotion.GiftIn)) }
        launch { glow.animateTo(1f, tween(ProgressMotion.GIFT_GLOW_MS, delayMillis = ProgressMotion.GIFT_GLOW_DELAY_MS)) }
    }

    val hours = giftHours(gift.hours)
    val date = Formats.dayAndMonth(gift.awardedDate)
    val next = gift.next?.takeUnless { low }
    val said = stringResource(Res.string.gift_description, title, name, hours, date)
    val description = next?.let { "$said ${nextDescription(it, names)}" } ?: said
    Column(modifier.fillMaxWidth().semantics { paneTitle = title }) {
        Column(
            Modifier.fillMaxWidth().clearAndSetSemantics {
                heading()
                contentDescription = description
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SectionLabel(title)
            Box(
                modifier = Modifier
                    .padding(top = StageTop, bottom = StageBottom)
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
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = NAME_SIZE.sp,
                    lineHeight = 1.15.em,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = NAME_TRACKING.em,
                ),
            )
            Text(
                text = stringResource(Res.string.gift_line, hours, date),
                modifier = Modifier.padding(top = LineTop),
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = LINE_SIZE.sp, lineHeight = 22.sp, fontFeatureSettings = TABULAR_FIGURES),
            )
            // it comes with the sheet, without a movement of its own
            if (next != null) NextCard(next, names.getOrElse(next.index) { "" }, Modifier.padding(top = NextTop))
        }
    }
}

/** «Спасибо» — the one button of the gift (spec 3.13); a swipe is the same. */
@Composable
fun GiftButtons(onThanks: () -> Unit, modifier: Modifier = Modifier) {
    AppSheetButtons(main = stringResource(Res.string.gift_thanks), onMain = onThanks, modifier = modifier)
}

/** The quiet card of the trophy after the gift: its outline in the third level of text, «Дальше — Смычок», «100 ч · ещё 50 ч». */
@Composable
private fun NextCard(next: NextTrophy, name: String, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .background(colors.surface, AppShapes.M)
            .padding(horizontal = NextPaddingHorizontal, vertical = NextPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NextGap),
    ) {
        TrophyIcon(next.hours, locked = true, size = NextIcon, lockedColor = ViolinTheme.textTertiary)
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.gift_next, name),
                color = colors.onSurface,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = NEXT_TITLE_SIZE.sp, lineHeight = 20.sp, fontWeight = FontWeight.ExtraBold),
            )
            Text(
                text = stringResource(Res.string.gift_next_line, Formats.hoursMark(next.hours), Formats.remainingTime(next.remainingMs)),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = NEXT_LINE_SIZE.sp, lineHeight = 18.sp, fontFeatureSettings = TABULAR_FIGURES),
            )
        }
    }
}

/** «Дальше — Смычок, 100 ч, ещё 50 ч» for TalkBack. */
@Composable
private fun nextDescription(next: NextTrophy, names: List<String>): String = stringResource(
    Res.string.gift_next_description,
    names.getOrElse(next.index) { "" },
    Formats.hoursMark(next.hours),
    Formats.remainingTime(next.remainingMs),
)

/** «1 час за скрипкой», «10 часов за скрипкой», «2500 часов за скрипкой». */
@Composable
private fun giftHours(hours: Int): String = stringResource(
    Formats.plural(hours, Res.string.gift_hours_one, Res.string.gift_hours_few, Res.string.gift_hours_many),
    Formats.grouped(hours.toLong()),
)
