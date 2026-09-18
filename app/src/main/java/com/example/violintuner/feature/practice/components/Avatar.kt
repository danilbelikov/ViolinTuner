package com.example.violintuner.feature.practice.components

import android.graphics.BitmapFactory
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.violintuner.core.ui.theme.ViolinTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val AVATAR_CROSSFADE_MS = 200
private const val LETTER_SHARE = 0.43f
private const val LEVEL_SHARE = 0.39f

/** What stands in the circle when there is no photo to show. */
sealed interface AvatarFallback {
    /** First letter of the name, or «?» on the profile sheet: on the accent color. */
    data class Letter(val text: String) : AvatarFallback

    /** Neither name nor photo: the number of the level on a quiet circle (handoff 11b2). */
    data class Level(val level: Int) : AvatarFallback
}

/**
 * The profile photo in a circle, or [fallback] without one, while it loads and when the file
 * cannot be decoded. Decorative: the header it stands in speaks for it. The letter scales with
 * the circle, not with the system font size — it has to fit.
 */
@Composable
fun Avatar(path: String?, fallback: AvatarFallback, size: Dp, modifier: Modifier = Modifier) {
    val photo by produceState<ImageBitmap?>(initialValue = null, key1 = path) {
        // A new photo is a new file name, so the path is the whole cache key.
        value = path?.let { withContext(Dispatchers.IO) { BitmapFactory.decodeFile(it)?.asImageBitmap() } }
    }
    Crossfade(
        targetState = photo,
        animationSpec = tween(AVATAR_CROSSFADE_MS),
        modifier = modifier
            .size(size)
            .clip(CircleShape),
        label = "avatar",
    ) { bitmap ->
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            FallbackCircle(fallback, size)
        }
    }
}

@Composable
private fun FallbackCircle(fallback: AvatarFallback, size: Dp) {
    val colors = ViolinTheme.progressColors
    val density = LocalDensity.current
    val (background, content, text, share) = when (fallback) {
        is AvatarFallback.Letter -> Look(colors.avatarLetterBackground, colors.avatarLetter, fallback.text, LETTER_SHARE)
        is AvatarFallback.Level -> Look(colors.avatarLevelBackground, colors.avatarLevel, fallback.level.toString(), LEVEL_SHARE)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background, CircleShape)
            .then(if (fallback is AvatarFallback.Level) Modifier.border(1.dp, colors.avatarLevelBorder, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val fontSize = with(density) { (size * share).toSp() }
        Text(
            text = text,
            color = content,
            // The theme's style for the typeface; its fixed line height would not suit a size
            // that follows the circle.
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = fontSize,
                lineHeight = fontSize,
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = "tnum",
            ),
            maxLines = 1,
        )
    }
}

private data class Look(val background: Color, val content: Color, val text: String, val share: Float)
