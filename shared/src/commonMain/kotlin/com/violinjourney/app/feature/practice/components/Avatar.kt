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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.text.firstSymbol
import com.violinjourney.app.core.ui.theme.ViolinTheme

private const val AVATAR_CROSSFADE_MS = 200

/** The letter or «?» of «Имя и фото» (5.29 R3): 40, 800 — in dp, so the system font size does not push it out of the circle. */
private val LetterSize = 40.dp

/** What stands in the circle when there is no photo to show — «Имя и фото» only; the path row and «Мой путь» have the ring of the level. */
sealed interface AvatarFallback {
    /** First letter of the name, or «?» with neither a photo nor a name (spec 3.36.3): no silhouette. */
    data class Letter(val text: String) : AvatarFallback
}

/**
 * The photo of the profile in a circle, or [fallback] without one — the second level of text on surface-2 (5.29 R3). Decorative: the
 * sheet it stands in speaks for it. [photo] is the picture the screen has already decoded for the path row and «Мой путь»
 * (`rememberSmallFileImage`): the three places show the same one and change together (spec 3.36.3); the crossfade is for a photo that
 * loads or changes before the eyes.
 */
@Composable
fun Avatar(photo: ImageBitmap?, fallback: AvatarFallback, size: Dp, modifier: Modifier = Modifier) {
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
            FallbackCircle(fallback)
        }
    }
}

@Composable
private fun FallbackCircle(fallback: AvatarFallback) {
    val colors = ViolinTheme.progressColors
    val text = when (fallback) {
        is AvatarFallback.Letter -> fallback.text
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.avatarLetterBackground, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        // dp to sp without the scale of the system font: the letter has to fit the circle
        val fontSize = with(LocalDensity.current) { LetterSize.toSp() }
        Text(
            text = text,
            color = colors.avatarLetter,
            // The theme's style for the typeface; its fixed line height would not suit a size given in dp.
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = fontSize,
                lineHeight = fontSize,
                fontWeight = FontWeight.ExtraBold,
                fontFeatureSettings = "tnum",
            ),
            maxLines = 1,
        )
    }
}

/**
 * First character of the name as the avatar shows it: a symbol whole — an emoji with its skin tone, a flag, a family
 * joined by ZWJ, a letter with its combining marks.
 */
fun initialOf(name: String): String = name.firstSymbol().uppercase()
