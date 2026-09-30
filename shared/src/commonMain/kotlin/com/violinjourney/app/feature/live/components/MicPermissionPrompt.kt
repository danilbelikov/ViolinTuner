package com.violinjourney.app.feature.live.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.components.ButtonFit
import com.violinjourney.app.core.ui.components.ExactLines
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.LiveTheme
import com.violinjourney.app.feature.live.LiveLayoutMath
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.mic_permission_grant
import com.violinjourney.app.shared.resources.mic_permission_text
import com.violinjourney.app.shared.resources.mic_permission_title
import kotlin.math.ceil
import org.jetbrains.compose.resources.stringResource

/** «Разрешить доступ»: 16 sp / 800, stepping down to 12 where the capsule is narrow (as the words of a button do, spec 5.29 R4). */
private const val GRANT_SP = 16f
private const val GRANT_MIN_SP = 12f

/** Its lines, exactly: the capsule of 54 holds two of them at the least size and a large font. */
private const val GRANT_LINE_EM = 1.15f

/** A word laid out in whole pixels: one that fits only by a hair is not trusted. */
private val WordSlack = 1.dp

/**
 * The card «нет разрешения» (spec 3.4, 3.36.6, 5.29 R6) — the one state of Live that needs words and a touch, in the place of the
 * ring: paper on a deep soft shadow; the plate of the microphone, «Дайте мне услышать скрипку», the text of the system request of iOS
 * (it does not change, spec 3.36) and «Разрешить доступ» — a dark capsule across the card with the microphone before the word. The
 * caller gives it its width and the height of its place; where it does not stand whole, first the plate goes, then the text scrolls
 * inside the card in whole lines — the title and the button are always seen, the title stepping down below 22 sp only to keep a line
 * of the text or, where not even one stands, the title and the button themselves ([LiveLayoutMath.promptFit]). A text with no line
 * seen is heard with the title: TalkBack skips what has no room on the screen. The button keeps its 54 whatever the place: a place
 * lower than the least card lets the card go on past it. The texts start at the start of their lines, as in the mockup. TalkBack goes
 * through the card as one group: the title, the text, the button.
 */
@Composable
fun MicPermissionPrompt(onGrantClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LiveTheme.venueColors
    val typography = LiveTheme.liveTypography
    val title = stringResource(Res.string.mic_permission_title)
    val text = stringResource(Res.string.mic_permission_text)
    val grant = stringResource(Res.string.mic_permission_grant)
    BoxWithConstraints(modifier) {
        val plan = rememberPromptPlan(title, text, grant, constraints.maxWidth, if (constraints.hasBoundedHeight) constraints.maxHeight.toFloat() else Float.POSITIVE_INFINITY)
        val fit = plan.fit
        val density = LocalDensity.current
        val textSeen = fit.textHeight > 0f
        Column(
            modifier = Modifier
                .width(maxWidth)
                // the least card stands whole even in a place lower than it, rather than squeeze its button
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .semantics { isTraversalGroup = true }
                .drawWithCache {
                    val corner = LiveDimens.PromptCorner.toPx()
                    val shadow = SoftShadow.bake(size, corner, LiveDimens.PromptShadowSigma.toPx())
                    val drop = LiveDimens.PromptShadowDrop.toPx()
                    onDrawBehind {
                        shadow.draw(this, drop, LiveDimens.PROMPT_SHADOW_ALPHA)
                        drawRoundRect(colors.bone, cornerRadius = CornerRadius(corner))
                    }
                }
                .padding(
                    start = LiveDimens.PromptPaddingSide,
                    end = LiveDimens.PromptPaddingSide,
                    top = LiveDimens.PromptPaddingTop * fit.air,
                    bottom = LiveDimens.PromptPaddingBottom * fit.air,
                ),
        ) {
            if (fit.showIcon) {
                Box(
                    modifier = Modifier
                        .size(LiveDimens.PromptIconPlate)
                        .background(colors.boneShade, RoundedCornerShape(LiveDimens.PromptIconPlateCorner)),
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(AppIcons.Mic, contentDescription = null, size = LiveDimens.PromptIcon, tint = colors.ink)
                }
                Spacer(Modifier.height(LiveDimens.PromptIconGap))
            }
            Text(
                text = title,
                modifier = Modifier.semantics {
                    heading()
                    // not a line of the text has room: TalkBack does not reach what the screen does not show, so the title says it
                    if (!textSeen) contentDescription = "$title\n$text"
                },
                color = colors.ink,
                style = titleStyle(typography.promptTitle, fit.titleSp),
            )
            if (textSeen) {
                Spacer(Modifier.height(LiveDimens.PromptTitleGap))
                Box(
                    modifier = Modifier
                        .heightIn(max = with(density) { fit.textHeight.toDp() })
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(text = text, color = colors.inkSoft, style = typography.promptBody)
                }
            }
            Spacer(Modifier.height(LiveDimens.PromptButtonTop * fit.air))
            GrantButton(grant, plan.grant, onGrantClick)
        }
    }
}

/** «Разрешить доступ»: a capsule of ink across the card, the microphone and the word in bone; one button for TalkBack. */
@Composable
private fun GrantButton(word: String, fit: GrantFit, onClick: () -> Unit) {
    val colors = LiveTheme.venueColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(LiveDimens.PromptButtonHeight)
            .clip(RoundedCornerShape(LiveDimens.PromptButtonHeight / 2))
            .background(colors.ink)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = LiveDimens.PromptButtonPadding),
        horizontalArrangement = Arrangement.spacedBy(LiveDimens.PromptButtonGap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (fit.icon) AppIcon(AppIcons.Mic, contentDescription = null, size = LiveDimens.PromptButtonIcon, tint = colors.bone)
        Text(
            text = word,
            color = colors.bone,
            style = grantStyle(LiveTheme.liveTypography.promptBody, fit.sizeSp),
            maxLines = fit.lines,
            softWrap = fit.lines > 1,
            textAlign = TextAlign.Center,
        )
    }
}

/** The title at [sizeSp], its line kept in proportion. */
private fun titleStyle(base: TextStyle, sizeSp: Float): TextStyle {
    val scale = sizeSp / LiveLayoutMath.PROMPT_TITLE_SP
    return base.copy(fontSize = sizeSp.sp, lineHeight = (base.lineHeight.value * scale).sp)
}

/** The word of the button: 16 sp / 800 in lines of exactly 1.15 — Android would pad a line back to Manrope's own 1.37 em. */
private fun grantStyle(base: TextStyle, sizeSp: Float): TextStyle =
    base.copy(fontSize = sizeSp.sp, lineHeight = (sizeSp * GRANT_LINE_EM).sp, fontWeight = FontWeight.ExtraBold, lineHeightStyle = ExactLines)

/** How the card stands: what of it is seen and at what size its title is ([LiveLayoutMath.PromptFit]), how its button holds its word. */
private class PromptPlan(val fit: LiveLayoutMath.PromptFit, val grant: GrantFit)

/** How «Разрешить доступ» holds its word: its size, with the microphone or without, on one line or two. */
private class GrantFit(val sizeSp: Float, val icon: Boolean, val lines: Int)

/**
 * What the card measures of itself at one width, in the font and at the font scale of the screen, in the whole pixels its column
 * lays it out in: the parts [LiveLayoutMath.promptFit] weighs — the title at each size it asks for, measured once — and the word of
 * the button. The place changes its height with every frame the string row of «Настройка» unfolds in; none of this does.
 */
private class PromptMeasures(val parts: LiveLayoutMath.PromptParts, val grant: GrantFit)

/**
 * The card [width] pixels wide in a place [available] pixels high (infinite — no bound). The button keeps its word whole: 16 sp beside
 * the microphone, 0.5 sp smaller at a time down to 12; then the word without the microphone; then two lines at its space — never
 * broken inside.
 */
@Composable
private fun rememberPromptPlan(title: String, text: String, grant: String, width: Int, available: Float): PromptPlan {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val typography = LiveTheme.liveTypography
    val measures = remember(title, text, grant, width, density, typography, measurer) {
        with(density) {
            val inner = (width - LiveDimens.PromptPaddingSide.roundToPx() * 2).coerceAtLeast(0)
            val slack = WordSlack.toPx()
            val wrapIn = Constraints(maxWidth = inner)
            // the words of the title that must stand whole in a line — not those of scripts whose lines break between their letters
            val words = LiveCardWords.wholeWords(title)
            val heights = HashMap<Float, Float>()
            val whole = HashMap<Float, Boolean>()
            val body = measurer.measure(text, typography.promptBody, constraints = wrapIn)
            val parts = LiveLayoutMath.PromptParts(
                air = (LiveDimens.PromptPaddingTop.roundToPx() + LiveDimens.PromptButtonTop.roundToPx() + LiveDimens.PromptPaddingBottom.roundToPx()).toFloat(),
                button = LiveDimens.PromptButtonHeight.roundToPx().toFloat(),
                icon = (LiveDimens.PromptIconPlate.roundToPx() + LiveDimens.PromptIconGap.roundToPx()).toFloat(),
                textGap = LiveDimens.PromptTitleGap.roundToPx().toFloat(),
                // where each line ends, in whole pixels, as a text takes its own: the window of the text is cut there
                textLines = List(body.lineCount) { line -> ceil(body.getLineBottom(line)) },
                titleHeight = { sp ->
                    heights.getOrPut(sp) { measurer.measure(title, titleStyle(typography.promptTitle, sp), constraints = wrapIn).size.height.toFloat() }
                },
                titleWhole = { sp ->
                    whole.getOrPut(sp) {
                        val widest = words.maxOfOrNull { measurer.measure(it, titleStyle(typography.promptTitle, sp), softWrap = false, maxLines = 1).size.width } ?: 0
                        widest + slack <= inner
                    }
                },
            )
            val room = (inner - LiveDimens.PromptButtonPadding.roundToPx() * 2).toFloat()
            val withIcon = (LiveDimens.PromptButtonIcon.roundToPx() + LiveDimens.PromptButtonGap.roundToPx()).toFloat()
            fun grantWidth(sp: Float) = measurer.measure(grant, grantStyle(typography.promptBody, sp), softWrap = false, maxLines = 1).size.width + slack
            val besideIcon = ButtonFit.size(room - withIcon, GRANT_SP, GRANT_MIN_SP, ::grantWidth)
            val grantFit = when {
                grantWidth(besideIcon) <= room - withIcon -> GrantFit(besideIcon, icon = true, lines = 1)
                grantWidth(GRANT_MIN_SP) <= room -> GrantFit(ButtonFit.size(room, GRANT_SP, GRANT_MIN_SP, ::grantWidth), icon = false, lines = 1)
                else -> GrantFit(GRANT_MIN_SP, icon = false, lines = 2)
            }
            PromptMeasures(parts, grantFit)
        }
    }
    return remember(measures, available) { PromptPlan(LiveLayoutMath.promptFit(available, measures.parts), measures.grant) }
}
