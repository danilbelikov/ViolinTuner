package com.violinjourney.app.feature.live.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.util.lerp
import com.violinjourney.app.core.ui.theme.LiveTheme
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.record_start
import com.violinjourney.app.shared.resources.record_stop
import kotlin.math.hypot
import org.jetbrains.compose.resources.stringResource

/**
 * The record key of Live (spec 3.9, 3.36.6, 5.29 R6), the one circle of the bottom row: 76 dp of bone with a soft highlight from
 * above, in the dark rim of [com.violinjourney.app.core.ui.theme.VenueColors.keyRim] (3 dp, inside the 76 — the key, its touch and
 * its row are all 76), on a soft shadow that takes no room, with a velvet dot of 28 — «запись», and no zone. Recording, it is the
 * red of recording in its darker rim with a white stop of 24 at a corner of 6; the one turns into the other in 200 ms. Pressed, it
 * goes down by 3 onto its shadow (90 ms) — the answer to a touch. Not [enabled] (in «Настройка», without a microphone or the
 * permission) it does not answer; its dimming ([alpha], [RecordKeyLight]: 0.4 there and the light of the room at rest) is read while
 * drawing and goes into the colours, not into a layer.
 *
 * TalkBack hears a button «Начать запись» / «Остановить запись» — a description, not only the label of the click, so the key is no
 * unnamed node for uiautomator either.
 */
@Composable
fun LiveRecordKey(
    recording: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    alpha: () -> Float = CardWhole,
    reduceMotion: Boolean = false,
) {
    val colors = LiveTheme.venueColors
    val red = ViolinTheme.recording
    val stop = ViolinTheme.onRecording
    val morph = animateFloatAsState(
        targetValue = if (recording) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(LiveMotion.RECORD_MORPH_MS),
        label = "recordKeyMorph",
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val travel = animateFloatAsState(
        targetValue = if (pressed && enabled) 1f else 0f,
        animationSpec = tween(LiveMotion.RECORD_PRESS_MS),
        label = "recordKeyTravel",
    )
    val description = stringResource(if (recording) Res.string.record_stop else Res.string.record_start)
    Spacer(
        modifier = modifier
            .size(LiveDimens.RecordKeySize)
            .drawWithCache {
                val radius = size.minDimension / 2
                val shadow = SoftShadow.bake(size, radius, LiveDimens.RecordKeyShadowSigma.toPx())
                val drop = LiveDimens.RecordKeyShadowDrop.toPx()
                val down = LiveDimens.RecordKeyTravel.toPx()
                val rim = LiveDimens.RecordKeyRim.toPx()
                // the highlight of bone: white-ish at 50 % across and 38 % down, bone at 55 % of the way to the farthest corner
                val light = Offset(size.width * LiveDimens.KEY_HIGHLIGHT_X, size.height * LiveDimens.KEY_HIGHLIGHT_Y)
                val reach = hypot(maxOf(light.x, size.width - light.x), maxOf(light.y, size.height - light.y))
                val face = Brush.radialGradient(
                    0f to lerp(colors.bone, Color.White, LiveDimens.KEY_HIGHLIGHT),
                    LiveDimens.KEY_HIGHLIGHT_BONE_STOP to colors.bone,
                    1f to colors.boneShade,
                    center = light,
                    radius = reach,
                )
                val dot = LiveDimens.RecordKeyDot.toPx()
                val square = LiveDimens.RecordKeyStop.toPx()
                val squareCorner = LiveDimens.RecordKeyStopCorner.toPx()
                val hole = Path()
                onDrawBehind {
                    val a = alpha().coerceIn(0f, 1f)
                    val m = morph.value
                    val t = travel.value * down
                    // the key goes down onto its shadow: the shadow is set down less
                    shadow.draw(this, drop - t, LiveDimens.KEY_SHADOW_ALPHA * a)
                    translate(top = t) {
                        val centre = center
                        // the dot of 28 turns into the stop of 24 at a corner of 6
                        val side = lerp(dot, square, m)
                        val glyph = RoundRect(Rect(centre, side / 2), CornerRadius(lerp(dot / 2, squareCorner, m)))
                        if (a >= 1f) {
                            if (m < 1f) drawCircle(face, radius, centre)
                            if (m > 0f) drawCircle(red, radius, centre, alpha = m)
                        } else {
                            // Dimmed, each part lies over the picture and not over another, as a layer would dim the key as a whole: the
                            // face with holes where the rim and the dot stand — a dot at 0.38 over the bone would come out pale.
                            hole.reset()
                            hole.fillType = PathFillType.EvenOdd
                            hole.addOval(Rect(centre, radius - rim + SEAM))
                            hole.addRoundRect(glyph)
                            if (m < 1f) drawPath(hole, face, alpha = a)
                            if (m > 0f) drawPath(hole, red, alpha = a * m)
                        }
                        drawCircle(lerp(colors.keyRim, colors.recordingRim, m), radius - rim / 2, centre, alpha = a, style = Stroke(rim))
                        drawRoundRect(
                            color = lerp(colors.velvet, stop, m),
                            topLeft = Offset(glyph.left, glyph.top),
                            size = Size(glyph.width, glyph.height),
                            cornerRadius = CornerRadius(glyph.topLeftCornerRadius.x),
                            alpha = a,
                        )
                    }
                }
            }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    )
}

/** The face under a dimmed rim reaches a pixel under it: no seam of the picture between the two. */
private const val SEAM = 1f

/**
 * The light of the record key (spec 3.36.6 «Свет и приглушение», 5.29 R6 «Приглушение»): at rest it dims with the light of the room,
 * to 0.38 while it is out; where it may not record — «Настройка», no microphone, no permission — it is 0.4 times that light; with its
 * stop, while a take records, it stays whole in the dark: it is how the performance ends. Public for the previews of the app.
 */
object RecordKeyLight {
    /** The alpha of the key: [recording] — it shows its stop; [enabled] — it may record; [light] — the light of the room, 0.38…1. */
    fun alpha(recording: Boolean, enabled: Boolean, light: Float): Float =
        if (recording) 1f else (if (enabled) 1f else LiveDimens.DISABLED_ALPHA) * light
}
