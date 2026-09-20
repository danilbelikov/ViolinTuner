package com.example.violintuner.feature.history.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.violintuner.R
import com.example.violintuner.core.ui.icons.AppIcon
import com.example.violintuner.core.ui.icons.AppIcons
import com.example.violintuner.feature.history.HistoryCard

/**
 * What the «⋯» of a recording's card offers: sharing and the sound — for recordings with sound
 * (spec 3.17) — and, for a take, the «лучший» mark (spec 3.21), where [onBest] is given.
 */
class CardActions(
    val onShare: (sessionId: Long) -> Unit,
    val onSound: (sessionId: Long) -> Unit,
    val onBest: ((sessionId: Long) -> Unit)? = null,
)

/**
 * «⋯» on the card of a recording: the quick way to «Поделиться» and «Звук…» without opening it,
 * and the mark of the best take first (handoff 22e4). Renaming and deleting stay on the
 * recording's own screen — their dialogs live there.
 */
@Composable
fun CardMenuButton(card: HistoryCard, actions: CardActions, modifier: Modifier = Modifier) {
    val sessionId = card.id
    val colors = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.card_menu)
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button) { open = true }
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) { AppIcon(AppIcons.More, contentDescription = null, tint = colors.onSurfaceVariant) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colors.surfaceContainerHigh) {
            val onBest = actions.onBest
            if (card.take && onBest != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(if (card.best) R.string.best_clear else R.string.best_set)) },
                    leadingIcon = { AppIcon(if (card.best) AppIcons.Star else AppIcons.StarOutline, contentDescription = null) },
                    onClick = { open = false; onBest(sessionId) },
                )
            }
            if (card.hasAudio) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.card_menu_share)) },
                leadingIcon = { AppIcon(AppIcons.Share, contentDescription = null) },
                onClick = { open = false; actions.onShare(sessionId) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.card_menu_sound)) },
                leadingIcon = { AppIcon(AppIcons.Sound, contentDescription = null) },
                onClick = { open = false; actions.onSound(sessionId) },
            )
            }
        }
    }
}
