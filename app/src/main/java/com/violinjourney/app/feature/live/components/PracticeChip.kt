package com.violinjourney.app.feature.live.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.violinjourney.app.R
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.theme.ViolinTheme

private const val TABULAR_FIGURES = "tnum"

/**
 * «● занятие · 12:34» (spec 3.12, handoff 10h): a practice is running. A paper tag with a velvet
 * dot (spec 3.27), no motion, no zone colour — it must not compete with the ring. Fades in and out; while fading out it
 * keeps the last time, because the state is already back to "no practice".
 */
@Composable
fun PracticeChipSlot(practiceMs: Long?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var lastShown by remember { mutableLongStateOf(0L) }
    if (practiceMs != null) SideEffect { lastShown = practiceMs }
    AnimatedVisibility(
        visible = practiceMs != null,
        modifier = modifier,
        enter = fadeIn(tween(LiveMotion.PRACTICE_CHIP_FADE_MS)),
        exit = fadeOut(tween(LiveMotion.PRACTICE_CHIP_FADE_MS)),
    ) {
        PracticeChip(elapsedMs = practiceMs ?: lastShown, onClick = onClick)
    }
}

@Composable
private fun PracticeChip(elapsedMs: Long, onClick: () -> Unit) {
    // a paper tag, as a thing of the room (spec 3.27, handoff 29j); the dot is velvet, never the colour of a zone
    val paper = ViolinTheme.venueColors
    val time = Formats.timer(elapsedMs)
    val description = stringResource(R.string.practice_timer_description, time)
    val shape = RoundedCornerShape(LiveDimens.PracticeChipCorner)
    Row(
        modifier = Modifier
            .height(LiveDimens.PracticeChipHeight)
            .clip(shape)
            .background(paper.bone, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = LiveDimens.PracticeChipPaddingHorizontal)
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LiveDimens.PracticeChipGap),
    ) {
        Box(
            Modifier
                .size(LiveDimens.PracticeChipDotSize)
                .background(paper.velvet, CircleShape),
        )
        Text(
            text = stringResource(R.string.practice_chip_label),
            color = paper.ink,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
        )
        Text(
            text = time,
            color = paper.ink,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR_FIGURES),
        )
    }
}
