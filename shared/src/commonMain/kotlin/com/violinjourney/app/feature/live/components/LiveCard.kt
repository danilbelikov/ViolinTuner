package com.violinjourney.app.feature.live.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.components.ExactLines
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.icons.IconPaths
import com.violinjourney.app.core.ui.theme.LiveTheme
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.live.LiveLayoutMath
import com.violinjourney.app.feature.live.components.LiveCardWords.First
import kotlin.math.roundToInt

/** The lead of a card: the icon of its kind, or the velvet dot of a running practice. */
internal sealed interface CardLead {
    data class Icon(val vector: ImageVector) : CardLead

    data object Dot : CardLead
}

/**
 * One face of a card of the bottom row of Live (spec 3.36.6): [paper] — something runs, else glass — something to choose or start;
 * its lead and its two lines, and how the first gives way where it does not fit ([LiveCardWords]). [tabular] — the first line is a
 * time. [doneSecond] — «готово», which takes the place of [second] (and the check the place of the lead) as a block reaches its goal;
 * the line holds the wider of the two, so nothing moves when it does.
 */
@Immutable
internal data class CardFace(
    val paper: Boolean,
    val lead: CardLead,
    val first: String,
    val second: String,
    val firstGives: First = First.WRAPS,
    val tabular: Boolean = false,
    val doneSecond: String? = null,
)

/**
 * A card of the bottom row of Live — «Что играю» and the practice tag are one form (spec 3.36.6, 5.29 R6): 60 high with a corner
 * of 18, [width] wide (the row gives both the same). Glass with an inner edge where there is something to choose or start, bone
 * paper on a soft shadow where something runs; the one turns into the other in a cross-fade of [swapMs], the face that leaves
 * keeping the words it showed — what it draws beside them, [done] and [progress], its caller keeps for it (the paper of a block
 * that drains into the glass keeps its line and its «готово»). [done] — how far a block has come to «готово»: the brass rim outside
 * the paper, the check over the lead, «готово» over the second line; [progress] — the brass line of a running block along the bottom,
 * which gives way only where the words need the whole card.
 *
 * Everything that follows the light is read while drawing: [light] (the light of the room) goes into the colours, not into a layer —
 * on iOS a layer under 1 is a saveLayer on every frame of the window. The paper comes up to its whole as «готово» comes in, the glass
 * never ([CardLight]). One phrase for TalkBack: [description]; not [enabled] — the same look, «недоступно».
 */
@Composable
internal fun LiveCard(
    face: CardFace,
    width: Dp,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    swapMs: Int = LiveMotion.BOOKMARK_SWAP_MS,
    reduceMotion: Boolean = false,
    light: () -> Float = CardWhole,
    done: () -> Float = CardNotDone,
    progress: (() -> Float)? = null,
) {
    val colors = LiveTheme.venueColors
    val glass = liveGlassGround() ?: ViolinTheme.glass
    val edge = ViolinTheme.glassEdge
    // the face of either ground, kept while the other comes in: the paper of a practice drains with its last time on it
    var keptGlass by remember { mutableStateOf<CardFace?>(null) }
    var keptPaper by remember { mutableStateOf<CardFace?>(null) }
    SideEffect { if (face.paper) keptPaper = face else keptGlass = face }
    val glassFace = if (face.paper) keptGlass else face
    val paperFace = if (face.paper) face else keptPaper
    val paper = animateFloatAsState(
        targetValue = if (face.paper) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(swapMs),
        label = "cardPaper",
    )
    val glassShown by remember(paper) { derivedStateOf { paper.value < 1f } }
    val paperShown by remember(paper) { derivedStateOf { paper.value > 0f } }
    val glassAlpha = remember(light, paper) { { light() * (1f - paper.value) } }
    val paperAlpha = remember(light, done, paper) { { CardLight.alpha(light(), done()) * paper.value } }
    Box(
        modifier = modifier
            .size(width, LiveDimens.CardHeight)
            // the shadow and the rim lie outside the card: drawn before its clip
            .drawWithCache {
                val corner = LiveDimens.CardCorner.toPx()
                val shadow = SoftShadow.bake(size, corner, LiveDimens.CardShadowSigma.toPx())
                val drop = LiveDimens.CardShadowDrop.toPx()
                val thin = GlassEdgeWidth.toPx()
                val rim = LiveDimens.CardRim.toPx()
                onDrawBehind {
                    val l = light()
                    val p = paper.value
                    if (p < 1f) {
                        drawRoundRect(glass, cornerRadius = CornerRadius(corner), alpha = l * (1f - p))
                        innerEdge(edge, thin, corner, l * (1f - p))
                    }
                    if (p > 0f) {
                        val d = done()
                        val a = CardLight.alpha(l, d) * p
                        shadow.draw(this, drop, LiveDimens.CARD_SHADOW_ALPHA * a)
                        drawRoundRect(colors.bone, cornerRadius = CornerRadius(corner), alpha = a)
                        if (d > 0f) outerRim(colors.brass, rim, corner, a * d)
                    }
                }
            }
            .clip(RoundedCornerShape(LiveDimens.CardCorner))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        if (glassShown) glassFace?.let { Face(it, width, glassAlpha, CardNotDone, progress = null) }
        if (paperShown) paperFace?.let { Face(it, width, paperAlpha, done, progress) }
    }
}

/** The light of a card that does not dim; a card that is not «готово». */
internal val CardWhole: () -> Float = { 1f }
internal val CardNotDone: () -> Float = { 0f }

/**
 * The light of a card of the bottom row (spec 3.36.6 «Свет и приглушение», 5.29 R6 «Приглушение»): glass and paper go down with the
 * light of the room, to 0.38 while it is out; the paper of a block comes up to its whole as «готово» comes in and stays so — the end
 * of a block comes while the violin sounds, and it is for the corner of the eye. The glass is never «готово».
 */
internal object CardLight {
    /** The alpha of a card's paper in the [light] of the room (0.38…1), [done] 0…1 of the way to «готово»; of its glass — [done] 0. */
    fun alpha(light: Float, done: Float): Float = light + (1f - light) * done.coerceIn(0f, 1f)
}

/** The inner edge of the glass of Live's controls (spec 5.29 R6). */
private val GlassEdgeWidth = 1.dp

/** A word laid out in whole pixels: one that fits only by a hair is not trusted. */
private val WordSlack = 1.dp

private const val TABULAR_FIGURES = "tnum"

/** The check of «готово», drawn on the grid of the icon set with a stroke of 3 (live.html, `.ok`). */
private const val CHECK_STROKE = 3f

/** The words, the lead and the brass line of one face. */
@Composable
private fun Face(face: CardFace, width: Dp, alpha: () -> Float, done: () -> Float, progress: (() -> Float)?) {
    val colors = LiveTheme.venueColors
    val scheme = MaterialTheme.colorScheme
    val family = MaterialTheme.typography.bodyMedium.fontFamily
    val fit = rememberCardFit(face, width, family)
    val density = LocalDensity.current
    // the brass line of a running block, and how far the words stand up over it — or it gives way to them
    val lift = if (progress == null) {
        null
    } else {
        with(density) {
            val card = LiveDimens.CardHeight.toPx()
            val clear = card - (LiveDimens.CardBarBottom + LiveDimens.CardBar + LiveDimens.CardBarGap).toPx()
            LiveCardWords.liftOverBar(fit.height, card, clear)?.toDp()
        }
    }
    val ink = if (face.paper) colors.ink else scheme.onSurface
    val soft = if (face.paper) colors.inkSoft else colors.glassCaption
    val doneOf = if (face.doneSecond != null) done else CardNotDone
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (progress != null && lift != null) Modifier.drawBehind { bar(progress(), colors.boneShade, colors.brass, alpha() * (1f - doneOf())) } else Modifier),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = LiveDimens.CardPadding, end = LiveDimens.CardPadding, bottom = lift ?: 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LiveDimens.CardGap),
        ) {
            if (fit.lead) Lead(face.lead, ink, alpha, doneOf, withCheck = face.doneSecond != null)
            Lines(face, fit, family, ink, soft, alpha, doneOf, Modifier.weight(1f))
        }
    }
}

/** The icon or the dot before the words; [withCheck] — a block, whose check of «готово» comes in over its icon as [done] grows. */
@Composable
private fun Lead(lead: CardLead, color: Color, alpha: () -> Float, done: () -> Float, withCheck: Boolean) {
    when (lead) {
        is CardLead.Icon -> Box(Modifier.size(LiveDimens.CardIcon)) {
            val icon = remember(alpha, done) { { alpha() * (1f - done()) } }
            CardIcon(lead.vector, color, icon)
            // «готово»: the check in the place of the icon, in ink (the green of the mockup would read as «в строе» by the ring)
            if (withCheck) {
                val check = remember(alpha, done) { { alpha() * done() } }
                CardCheck(LiveTheme.venueColors.ink, check)
            }
        }
        CardLead.Dot -> {
            val velvet = LiveTheme.venueColors.velvet
            Spacer(Modifier.size(LiveDimens.CardDot).drawBehind { drawCircle(velvet, alpha = alpha().coerceIn(0f, 1f)) })
        }
    }
}

/** An icon of the set whose alpha is read while drawing: a vector painter takes it without a layer. */
@Composable
private fun CardIcon(vector: ImageVector, color: Color, alpha: () -> Float) {
    val painter = rememberVectorPainter(vector)
    val tint = remember(color) { ColorFilter.tint(color) }
    Spacer(
        Modifier
            .size(LiveDimens.CardIcon)
            .drawBehind {
                val a = alpha().coerceIn(0f, 1f)
                if (a > 0f) with(painter) { draw(size, alpha = a, colorFilter = tint) }
            },
    )
}

/** The check of «готово» (spec 5.29 R6): the check of the icon set, 20 dp, with a stroke of 3 on its grid, in ink. */
@Composable
private fun CardCheck(color: Color, alpha: () -> Float) {
    Spacer(
        Modifier
            .size(LiveDimens.CardIcon)
            .drawWithCache {
                val path = PathParser().parsePathString(IconPaths.CHECK.single()).toPath()
                val stroke = Stroke(width = CHECK_STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round)
                onDrawBehind {
                    val a = alpha().coerceIn(0f, 1f)
                    if (a > 0f) scale(size.width / AppIcons.GRID, pivot = Offset.Zero) { drawPath(path, color, alpha = a, style = stroke) }
                }
            },
    )
}

/** The two lines as [fit] stands them; «готово» over the second as the block reaches its goal. Their alpha is read while drawing. */
@Composable
private fun Lines(face: CardFace, fit: LiveCardWords.Fit, family: FontFamily?, ink: Color, soft: Color, alpha: () -> Float, done: () -> Float, modifier: Modifier) {
    val firstStyle = remember(fit, family, face.tabular) { cardStyle(family, first = true, tabular = face.tabular, sizeSp = fit.firstSp, em = fit.lineEm) }
    val secondStyle = remember(fit, family) { cardStyle(family, first = false, tabular = false, sizeSp = fit.secondSp, em = fit.lineEm) }
    val title = face.firstGives == First.ELLIPSIS
    Column(modifier = modifier) {
        BasicText(
            text = face.first,
            style = firstStyle,
            maxLines = fit.firstLines,
            softWrap = title || fit.firstLines > 1,
            overflow = if (title) TextOverflow.Ellipsis else TextOverflow.Clip,
            color = { ink.copy(alpha = ink.alpha * alpha().coerceIn(0f, 1f)) },
        )
        Box {
            BasicText(
                text = face.second,
                style = secondStyle,
                maxLines = fit.secondLines,
                softWrap = fit.secondLines > 1,
                color = { soft.copy(alpha = soft.alpha * (alpha() * (1f - done())).coerceIn(0f, 1f)) },
            )
            face.doneSecond?.let { word ->
                BasicText(
                    text = word,
                    style = secondStyle,
                    maxLines = 1,
                    softWrap = false,
                    color = { soft.copy(alpha = soft.alpha * (alpha() * done()).coerceIn(0f, 1f)) },
                )
            }
        }
    }
}

/**
 * The lines of a card (spec 5.29 R6): the first 13.5 sp / 700, the second 12.5 sp / 600, or as [LiveCardWords] steps them; their
 * lines exactly [em] of their size — Android would pad a line back to Manrope's own 1.37 em ([ExactLines]).
 */
private fun cardStyle(family: FontFamily?, first: Boolean, tabular: Boolean, sizeSp: Float, em: Float): TextStyle = TextStyle(
    fontFamily = family,
    fontWeight = if (first) FontWeight.Bold else FontWeight.SemiBold,
    fontSize = sizeSp.sp,
    lineHeight = (sizeSp * em).sp,
    letterSpacing = 0.sp,
    fontFeatureSettings = if (tabular) TABULAR_FIGURES else null,
    lineHeightStyle = ExactLines,
)

/**
 * How the words of [face] stand in a card [width] wide ([LiveCardWords]), measured in the font and at the font scale of the screen.
 * A time measures as its shape — its figures are tabular — so a card that ticks every second is not measured every second.
 */
@Composable
private fun rememberCardFit(face: CardFace, width: Dp, family: FontFamily?): LiveCardWords.Fit {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val first = if (face.tabular) face.first.map { if (it.isDigit()) '0' else it }.joinToString("") else face.first
    val dot = face.lead is CardLead.Dot
    return remember(first, face.second, face.doneSecond, face.firstGives, face.tabular, dot, width, density, family, measurer) {
        with(density) {
            val slack = WordSlack.toPx()
            val lead = if (dot) LiveDimens.CardDot else LiveDimens.CardIcon
            // the room of the words in the whole pixels the row of the face lays out: the card less its padding (and the lead with its gap)
            val bareRoom = (width.roundToPx() - LiveDimens.CardPadding.roundToPx() * 2).toFloat()
            // the dot of a running practice is no icon: it stays where an icon would hide (spec 5.29 R6: below 120)
            val leadRoom = if (dot || LiveLayoutMath.keyCardShowsIcon(width.value)) {
                bareRoom - lead.roundToPx() - LiveDimens.CardGap.roundToPx()
            } else {
                null
            }
            val seconds = listOfNotNull(face.second, face.doneSecond)
            fun style(isFirst: Boolean, sp: Float) = cardStyle(family, isFirst, face.tabular && isFirst, sp, LiveCardWords.LINE_EM)
            fun widthOf(text: String, style: TextStyle) = measurer.measure(text, style, softWrap = false, maxLines = 1).size.width + slack
            fun lineOf(text: String, style: TextStyle) =
                LiveCardWords.Line(widthOf(text, style), LiveCardWords.wholeWords(text).maxOfOrNull { widthOf(it, style) } ?: 0f)
            fun linesOf(text: String, style: TextStyle, room: Float) =
                measurer.measure(text, style, constraints = Constraints(maxWidth = room.roundToInt().coerceAtLeast(0))).lineCount
            LiveCardWords.fit(
                leadRoom = leadRoom,
                bareRoom = bareRoom,
                height = LiveDimens.CardHeight.roundToPx().toFloat(),
                first = face.firstGives,
                measure = LiveCardWords.Measure(
                    first = { sp -> lineOf(first, style(true, sp)) },
                    second = { sp ->
                        val lines = seconds.map { lineOf(it, style(false, sp)) }
                        LiveCardWords.Line(lines.maxOf { it.width }, lines.maxOf { it.word })
                    },
                    wrapped = { which, sp, room ->
                        if (which == 0) linesOf(first, style(true, sp), room) else seconds.maxOf { linesOf(it, style(false, sp), room) }
                    },
                    lineHeight = { sp, em -> (sp * em).sp.toPx() },
                ),
            )
        }
    }
}

/** The brass line of a running block (spec 5.29 R6): 3 high with a corner of 2, 12 from the sides and 7 over the bottom, on its track. */
private fun DrawScope.bar(progress: Float, track: Color, brass: Color, alpha: Float) {
    if (alpha <= 0f) return
    val side = LiveDimens.CardBarSide.toPx()
    val height = LiveDimens.CardBar.toPx()
    val top = size.height - LiveDimens.CardBarBottom.toPx() - height
    val length = size.width - side * 2
    val corner = CornerRadius(LiveDimens.CardBarCorner.toPx())
    drawRoundRect(track, topLeft = Offset(side, top), size = Size(length, height), cornerRadius = corner, alpha = alpha.coerceAtMost(1f))
    val filled = length * progress.coerceIn(0f, 1f)
    if (filled > 0f) drawRoundRect(brass, topLeft = Offset(side, top), size = Size(filled, height), cornerRadius = corner, alpha = alpha.coerceAtMost(1f))
}

/** An edge [width] wide inside the rounded rectangle of the card, as `Modifier.border` draws it. */
private fun DrawScope.innerEdge(color: Color, width: Float, corner: Float, alpha: Float) {
    val half = width / 2
    drawRoundRect(
        color = color,
        topLeft = Offset(half, half),
        size = Size(size.width - width, size.height - width),
        cornerRadius = CornerRadius((corner - half).coerceAtLeast(0f)),
        style = Stroke(width),
        alpha = alpha.coerceIn(0f, 1f),
    )
}

/** A rim [width] wide round the outside of the card, as a CSS `box-shadow: 0 0 0 <width>` draws it. */
private fun DrawScope.outerRim(color: Color, width: Float, corner: Float, alpha: Float) {
    val half = width / 2
    drawRoundRect(
        color = color,
        topLeft = Offset(-half, -half),
        size = Size(size.width + width, size.height + width),
        cornerRadius = CornerRadius(corner + half),
        style = Stroke(width),
        alpha = alpha.coerceIn(0f, 1f),
    )
}
