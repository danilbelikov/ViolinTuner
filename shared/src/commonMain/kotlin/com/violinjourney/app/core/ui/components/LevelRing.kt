package com.violinjourney.app.core.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val PHOTO_CROSSFADE_MS = 200
private const val TOP_DEGREES = -90f
private const val FULL_DEGREES = 360f
private const val TABULAR_FIGURES = "tnum"

/**
 * The two sizes of the ring of the level (spec 5.29): [Row] of 48 with a rim of 4 in the path row of «Занятия», [Sheet] of 72
 * with a rim of 5 in «Мой путь». [inner] — the circle inside the rim (the number or the photo), [number] — the size of the number
 * in it; with a photo the number moves to a badge of [badge] with a figure of [badgeNumber], ringed by a gap of [badgeGap] in the
 * colour of the ground and standing [badgeOut] past the ring. Sizes of text in dp: the numbers follow the ring, not the system font.
 */
enum class LevelRingSize(
    val ring: Dp,
    val rim: Dp,
    val inner: Dp,
    val number: Dp,
    val badge: Dp,
    val badgeNumber: Dp,
    val badgeGap: Dp,
    val badgeOut: Dp,
) {
    Row(ring = 48.dp, rim = 4.dp, inner = 40.dp, number = 17.dp, badge = 20.dp, badgeNumber = 11.dp, badgeGap = 2.dp, badgeOut = 3.dp),
    Sheet(ring = 72.dp, rim = 5.dp, inner = 62.dp, number = 26.dp, badge = 26.dp, badgeNumber = 13.dp, badgeGap = 3.dp, badgeOut = 2.dp),
}

/**
 * The ring of the level (spec 3.36.1, 5.29): an arc in the accent over a track of surfaceContainerHigh, from twelve o'clock
 * clockwise, as far as [progress] of the level goes — read while drawing, so a caller animates it (ProgressMotion, R2) without
 * recomposing the ring. Inside, on a circle of the background: the number of the [level]; or, with a [photo], the photo (cropped
 * to the circle, faded in over 200 ms as the avatar is) and the number in a badge in the accent at the lower right, ringed in the
 * colour of the [ground] under the ring — surface in the path row, surfaceContainer in a sheet or a card.
 *
 * The photo comes from the caller (`rememberSmallFileImage(path)`). No semantics of its own: the path row it stands in speaks
 * for it (the level, its name and what is left).
 */
@Composable
fun LevelRing(
    level: Int,
    progress: () -> Float,
    modifier: Modifier = Modifier,
    size: LevelRingSize = LevelRingSize.Row,
    photo: ImageBitmap? = null,
    ground: Color = MaterialTheme.colorScheme.surface,
) {
    val colors = MaterialTheme.colorScheme
    val track = colors.surfaceContainerHigh
    val arc = colors.primary
    val rimWidth = size.rim
    Box(
        modifier = modifier
            .size(size.ring)
            .clearAndSetSemantics {}
            .drawBehind {
                val rim = rimWidth.toPx()
                val inset = rim / 2
                val circle = Size(this.size.width - rim, this.size.height - rim)
                drawArc(track, startAngle = 0f, sweepAngle = FULL_DEGREES, useCenter = false, topLeft = Offset(inset, inset), size = circle, style = Stroke(rim))
                val sweep = LevelRingMath.sweep(progress())
                if (sweep > 0f) {
                    drawArc(arc, startAngle = TOP_DEGREES, sweepAngle = sweep, useCenter = false, topLeft = Offset(inset, inset), size = circle, style = Stroke(rim, cap = StrokeCap.Butt))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(
            targetState = photo,
            animationSpec = tween(PHOTO_CROSSFADE_MS),
            modifier = Modifier.size(size.inner).clip(CircleShape).background(colors.surface),
            label = "levelRingPhoto",
        ) { bitmap ->
            if (bitmap != null) {
                Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size.inner))
            } else {
                Box(Modifier.size(size.inner), contentAlignment = Alignment.Center) {
                    Figure(level, size.number, colors.onSurface)
                }
            }
        }
        if (photo != null) {
            val out = size.badgeOut + size.badgeGap
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = out, y = out)
                    .size(size.badge + size.badgeGap * 2)
                    .background(ground, CircleShape)
                    .padding(size.badgeGap)
                    .background(colors.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Figure(level, size.badgeNumber, colors.onPrimary)
            }
        }
    }
}

/** A number sized by the ring in dp — the system font size does not move it, it has to fit, as the letter of the avatar. */
@Composable
private fun Figure(level: Int, size: Dp, color: Color) {
    val fontSize = with(LocalDensity.current) { size.toSp() }
    Text(
        text = level.toString(),
        color = color,
        maxLines = 1,
        softWrap = false,
        style = MaterialTheme.typography.titleLarge.copy(
            fontSize = fontSize,
            lineHeight = fontSize,
            fontWeight = FontWeight.ExtraBold,
            fontFeatureSettings = TABULAR_FIGURES,
        ),
    )
}

/** The arc of the ring, pure: 0…360 degrees for a progress of 0…1, clamped; nothing for NaN. */
internal object LevelRingMath {
    fun sweep(progress: Float): Float = if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f) * FULL_DEGREES
}
