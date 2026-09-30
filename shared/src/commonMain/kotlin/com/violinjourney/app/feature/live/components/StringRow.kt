package com.violinjourney.app.feature.live.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.domain.ViolinString
import com.violinjourney.app.core.ui.components.ExactLines
import com.violinjourney.app.core.ui.theme.LiveTheme
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.live.TuningState
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.tuning_string_auto
import com.violinjourney.app.shared.resources.tuning_string_hz
import com.violinjourney.app.shared.resources.tuning_string_locked
import org.jetbrains.compose.resources.stringResource

// The letter and the hertz of a string (spec 5.29 R6): 20 sp / 800 over 11.5 sp / 600 in tabular figures; with their lines of
// 22 and 14 they take 54 of the 58 at the font 1.5.
private val LetterSize = 20.sp
private val LetterLine = 22.sp
private val HertzSize = 11.5.sp
private val HertzLine = 14.sp
private const val TABULAR_FIGURES = "tnum"

/** The inner edge of the glass of a string (spec 5.29 R6). */
private val GlassEdgeWidth = 1.dp

/** How a string button looks: the glass; the nearest string in auto; the locked one. */
internal enum class StringLook { PLAIN, NEAREST, LOCKED }

/** How [string] looks in [tuning]: locked; the target of auto — the nearest string while one sounds; or the plain glass. */
internal fun stringLookOf(string: ViolinString, tuning: TuningState): StringLook = when (string) {
    tuning.lockedString -> StringLook.LOCKED
    tuning.targetString -> StringLook.NEAREST
    else -> StringLook.PLAIN
}

/**
 * The light of a string button (spec 3.36.6, «Свет и приглушение»; 5.29 R6): each dims by itself. Its own alpha, [base] — 1, or 0.4
 * for all four without the permission — times the light of the room, down to 0.38 while it is out; but the target, locked or the
 * nearest in auto, keeps its whole [base] in the dark while that is 1: it is the answer «which string I pull», read like the scale,
 * not touched. A button becomes the target and stops being one in a fade ([wholeness] is where it goes, [alpha] reads how far it
 * has come): a rest of the bow in the dark must not blink the button between 1 and 0.38 (spec 3.14: nothing blinks).
 */
internal object StringLight {
    /** How much of the target a button in [look] is to be, 1 or 0: without the permission there is none. */
    fun wholeness(look: StringLook, base: Float): Float = if (look != StringLook.PLAIN && base == 1f) 1f else 0f

    /** The alpha of a button [whole] 0…1 the target, in the [light] 0.38…1 of the room. */
    fun alpha(base: Float, light: Float, whole: Float): Float = base * (light + (1f - light) * whole.coerceIn(0f, 1f))
}

/**
 * G · D · A · E of «Настройка» (spec 3.5, 3.36.6): four buttons of smoked glass across the row, all as wide, 10 apart; the letter
 * and under it the hertz of the string (they follow the reference pitch, spec 3.8). The nearest string in auto wears a bone edge on
 * a bone ground of 16 %; the locked one is bone, its letter ink and a lock in its corner — the two differ by form and by the lock,
 * not only by colour (principle 5). A tap locks a string; a tap on the locked one returns to auto.
 *
 * Each button dims by itself ([StringLight]): [base] times the [light] of the room, the target whole. The alpha goes into the colours
 * while drawing, not into a layer: on iOS a layer under 1 is a saveLayer on every frame of the window, and the three strings that dim
 * would be three of them.
 */
@Composable
fun StringRow(
    tuning: TuningState,
    onStringClick: (ViolinString) -> Unit,
    light: () -> Float,
    base: Float,
    modifier: Modifier = Modifier,
    topPadding: Dp = LiveDimens.StringRowTopPadding,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = topPadding),
        horizontalArrangement = Arrangement.spacedBy(LiveDimens.StringButtonGap),
    ) {
        ViolinString.entries.forEach { string ->
            val look = stringLookOf(string, tuning)
            StringButton(
                letter = string.note.letter.toString(),
                hz = tuning.stringHz.getValue(string),
                look = look,
                base = base,
                light = light,
                onClick = { onStringClick(string) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StringButton(
    letter: String,
    hz: Int,
    look: StringLook,
    base: Float,
    light: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LiveTheme.venueColors
    val onGlass = MaterialTheme.colorScheme.onSurface
    val ground = liveGlassGround() ?: ViolinTheme.glass
    val edge = ViolinTheme.glassEdge
    val shape = RoundedCornerShape(LiveDimens.StringButtonCorner)
    val letterColor = if (look == StringLook.LOCKED) colors.ink else onGlass
    val hertzColor = if (look == StringLook.LOCKED) colors.inkSoft else colors.glassCaption
    val family = MaterialTheme.typography.titleLarge.fontFamily
    // Lines of 22 and 14 exactly (ExactLines): Android would pad each back to Manrope's own 1.37 em, and at the font 1.5 the letter
    // and the hertz (41 + 23.6) would outgrow the button of 58 that their lines (33 + 21) stand in.
    val letterStyle = remember(family) {
        TextStyle(fontFamily = family, fontSize = LetterSize, lineHeight = LetterLine, fontWeight = FontWeight.ExtraBold, lineHeightStyle = ExactLines)
    }
    val hertzStyle = remember(family) {
        TextStyle(
            fontFamily = family, fontSize = HertzSize, lineHeight = HertzLine, fontWeight = FontWeight.SemiBold,
            fontFeatureSettings = TABULAR_FIGURES, lineHeightStyle = ExactLines,
        )
    }
    // How far the button is the target, and how far it wears the edge of the nearest: both come and go in a fade, read only while
    // drawing — a pause of the bow in auto takes the target away, the next stroke gives it back, and neither may blink the row.
    val whole = animateFloatAsState(StringLight.wholeness(look, base), tween(LiveMotion.CONTENT_FADE_MS), label = "stringWhole")
    val nearest = animateFloatAsState(if (look == StringLook.NEAREST) 1f else 0f, tween(LiveMotion.CONTENT_FADE_MS), label = "stringNearest")
    val alpha = remember(base, light, whole) { { StringLight.alpha(base, light(), whole.value) } }
    val lockedDescription = stringResource(Res.string.tuning_string_locked)
    val autoDescription = stringResource(Res.string.tuning_string_auto)
    Box(
        modifier = modifier
            .height(LiveDimens.StringButtonHeight)
            .clip(shape)
            .drawWithCache {
                val corner = LiveDimens.StringButtonCorner.toPx()
                val thin = GlassEdgeWidth.toPx()
                val nearEdge = LiveDimens.StringNearEdge.toPx()
                onDrawBehind {
                    val a = alpha()
                    if (look == StringLook.LOCKED) {
                        face(colors.bone.faded(a), corner)
                    } else {
                        // the glass with its thin edge; the nearest string lays its bone ground and edge over it as far as it is one
                        val n = nearest.value
                        face(ground.faded(a), corner)
                        if (n < 1f) innerEdge(edge.faded(a * (1f - n)), thin, corner)
                        if (n > 0f) {
                            face(colors.bone.copy(alpha = LiveDimens.STRING_NEAR_GROUND_ALPHA * a * n), corner)
                            innerEdge(colors.bone.faded(a * n), nearEdge, corner)
                        }
                    }
                }
            }
            .selectable(selected = look != StringLook.PLAIN, role = Role.Button, onClick = onClick)
            .semantics { stateDescription = if (look == StringLook.LOCKED) lockedDescription else autoDescription },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(text = letter, style = letterStyle, maxLines = 1, color = { letterColor.faded(alpha()) })
            BasicText(
                text = stringResource(Res.string.tuning_string_hz, hz),
                style = hertzStyle,
                maxLines = 1,
                softWrap = false,
                color = { hertzColor.faded(alpha()) },
            )
        }
        AnimatedVisibility(
            visible = look == StringLook.LOCKED,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = LiveDimens.StringLockTop, end = LiveDimens.StringLockEnd),
            enter = scaleIn(
                keyframes {
                    durationMillis = LiveMotion.LOCK_POP_MS
                    LiveMotion.LOCK_POP_OVERSHOOT at LiveMotion.LOCK_POP_PEAK_MS
                },
            ) + fadeIn(tween(LiveMotion.LOCK_POP_PEAK_MS)),
            exit = scaleOut(tween(LiveMotion.LOCK_POP_PEAK_MS)) + fadeOut(tween(LiveMotion.LOCK_POP_PEAK_MS)),
        ) {
            LockGlyph(color = colors.ink, alpha = alpha)
        }
    }
}

/** [this] with its alpha times [alpha]. */
private fun Color.faded(alpha: Float): Color = copy(alpha = this.alpha * alpha)

/** The face of the button: its rounded rectangle filled with [color]. */
private fun DrawScope.face(color: Color, corner: Float) {
    drawRoundRect(color = color, cornerRadius = CornerRadius(corner))
}

/** An edge [width] wide inside the rounded rectangle of the button, as `Modifier.border` draws it. */
private fun DrawScope.innerEdge(color: Color, width: Float, corner: Float) {
    val half = width / 2
    drawRoundRect(
        color = color,
        topLeft = Offset(half, half),
        size = Size(size.width - width, size.height - width),
        cornerRadius = CornerRadius((corner - half).coerceAtLeast(0f)),
        style = Stroke(width),
    )
}

// The lock of the handoff SVG, 12 × 12 viewport: a body and a shackle; filled, without its old disc of bone (spec 5.29 R6).
private const val LOCK_VIEWPORT = 12f

@Composable
private fun LockGlyph(color: Color, alpha: () -> Float) {
    Canvas(Modifier.size(LiveDimens.StringLock)) {
        val glyph = color.faded(alpha())
        scale(scale = size.width / LOCK_VIEWPORT, pivot = Offset.Zero) {
            drawRoundRect(
                color = glyph,
                topLeft = Offset(2f, 5.5f),
                size = Size(8f, 5.5f),
                cornerRadius = CornerRadius(1.2f, 1.2f),
            )
            // shackle: half circle of radius 2.2 around (6, 4) with legs down to the body
            drawArc(
                color = glyph,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(3.8f, 1.8f),
                size = Size(4.4f, 4.4f),
                style = Stroke(width = 1.8f, cap = StrokeCap.Butt),
            )
            drawLine(glyph, Offset(3.8f, 4f), Offset(3.8f, 5.5f), strokeWidth = 1.8f)
            drawLine(glyph, Offset(8.2f, 4f), Offset(8.2f, 5.5f), strokeWidth = 1.8f)
        }
    }
}
