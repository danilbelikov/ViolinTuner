package com.violinjourney.app.feature.practice.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.violinjourney.app.core.text.firstSymbol
import com.violinjourney.app.core.ui.components.rememberSmallFileImage
import com.violinjourney.app.core.ui.theme.ViolinTheme

private const val AVATAR_CROSSFADE_MS = 200
private const val LETTER_SHARE = 0.43f

/**
 * What stands in the circle when there is no photo to show — on the profile sheet only; the path row and «Мой путь» have the
 * ring of the level instead (spec 3.36.2).
 */
sealed interface AvatarFallback {
    /** First letter of the name, or «?»: on the accent color. */
    data class Letter(val text: String) : AvatarFallback
}

/**
 * The profile photo in a circle, or [fallback] without one, while it loads and when the file
 * cannot be decoded. Decorative: the header it stands in speaks for it. The letter scales with
 * the circle, not with the system font size — it has to fit.
 *
 * The photo comes from the cache of small pictures (a new photo is a new file name, so the path is the key): seen
 * once, it is there from the first frame of the next visit — the header shows what is already true as it is. The
 * crossfade is for a photo that loads or changes before the eyes.
 */
@Composable
fun Avatar(path: String?, fallback: AvatarFallback, size: Dp, modifier: Modifier = Modifier) {
    val photo = rememberSmallFileImage(path)
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
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background, CircleShape),
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

/**
 * First character of the name as the avatar shows it: a symbol whole — an emoji with its skin tone, a flag, a family
 * joined by ZWJ, a letter with its combining marks.
 */
fun initialOf(name: String): String = name.firstSymbol().uppercase()
