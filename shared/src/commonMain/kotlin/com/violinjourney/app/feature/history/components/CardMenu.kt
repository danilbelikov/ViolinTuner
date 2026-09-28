package com.violinjourney.app.feature.history.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.ui.components.AppMenu
import com.violinjourney.app.core.ui.components.AppMenuItem
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.feature.history.HistoryCard
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.best_clear
import com.violinjourney.app.shared.resources.best_set
import com.violinjourney.app.shared.resources.card_menu
import com.violinjourney.app.shared.resources.card_menu_share
import com.violinjourney.app.shared.resources.card_menu_sound
import org.jetbrains.compose.resources.stringResource

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
    val label = stringResource(Res.string.card_menu)
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button) { open = true }
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) { AppIcon(AppIcons.More, contentDescription = null, tint = colors.onSurfaceVariant) }
        AppMenu(expanded = open, onDismissRequest = { open = false }) {
            val onBest = actions.onBest
            if (card.take && onBest != null) {
                AppMenuItem(
                    text = stringResource(if (card.best) Res.string.best_clear else Res.string.best_set),
                    icon = if (card.best) AppIcons.Star else AppIcons.StarOutline,
                    onClick = { open = false; onBest(sessionId) },
                )
            }
            if (card.hasAudio) {
                AppMenuItem(
                    text = stringResource(Res.string.card_menu_share),
                    icon = AppIcons.Share,
                    onClick = { open = false; actions.onShare(sessionId) },
                )
                AppMenuItem(
                    text = stringResource(Res.string.card_menu_sound),
                    icon = AppIcons.Sound,
                    onClick = { open = false; actions.onSound(sessionId) },
                )
            }
        }
    }
}
