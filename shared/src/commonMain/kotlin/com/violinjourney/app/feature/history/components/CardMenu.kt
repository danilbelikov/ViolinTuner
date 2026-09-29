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
import com.violinjourney.app.core.ui.components.MenuDanger
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.feature.history.HistoryCard
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.best_clear
import com.violinjourney.app.shared.resources.best_set
import com.violinjourney.app.shared.resources.card_menu
import com.violinjourney.app.shared.resources.card_menu_delete
import com.violinjourney.app.shared.resources.card_menu_share
import com.violinjourney.app.shared.resources.card_menu_sound
import org.jetbrains.compose.resources.stringResource

/** «⋯» of a card: an icon of 24 in a target of 48 (spec 5.29 R5). */
private val MenuTarget = 48.dp

/**
 * What the «⋯» of a recording's card offers (spec 3.36.5): sharing and the sound — for recordings with sound (spec 3.17) — the
 * «лучший» mark for a take, where [onBest] is given (spec 3.21), and [onDelete] for every one of them: «Удалить…» asks the question
 * of the recording's own screen and deletes it the way picked ones go (spec 3.18).
 */
class CardActions(
    val onShare: (sessionId: Long) -> Unit,
    val onSound: (sessionId: Long) -> Unit,
    val onDelete: (sessionId: Long) -> Unit,
    val onBest: ((sessionId: Long) -> Unit)? = null,
)

/**
 * «⋯» on the card of a recording (spec 3.36.5, components.html «Меню ⋯»), in this order: «Отметить лучшим» / «Снять отметку
 * „лучший“» of a take; «Поделиться» and «Звук…» of a recording with sound; a line and «Удалить…» in the colour of danger with the
 * bin — a recording without sound that is not a take has «Удалить…» alone, without the line. Renaming stays on the recording's
 * own screen.
 */
@Composable
fun CardMenuButton(card: HistoryCard, actions: CardActions, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val label = stringResource(Res.string.card_menu)
    val close = { open = false }
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(MenuTarget)
                .clip(CircleShape)
                .clickable(role = Role.Button) { open = true }
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) { AppIcon(AppIcons.More, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        AppMenu(expanded = open, onDismissRequest = close, danger = cardMenuDanger(card, actions, close)) {
            CardMenuItems(card, actions, close)
        }
    }
}

/** «Удалить…» of the card's «⋯»: after a line, unless it is the only item — a recording without sound that is not a take. */
@Composable
fun cardMenuDanger(card: HistoryCard, actions: CardActions, close: () -> Unit): MenuDanger = MenuDanger(
    text = stringResource(Res.string.card_menu_delete),
    onClick = { close(); actions.onDelete(card.id) },
    divider = marksBest(card, actions) || card.hasAudio,
)

/** The items of the card's «⋯» before «Удалить…» — for [AppMenu], and for the card of a menu in a preview. */
@Composable
fun CardMenuItems(card: HistoryCard, actions: CardActions, close: () -> Unit) {
    val sessionId = card.id
    val onBest = actions.onBest
    if (onBest != null && marksBest(card, actions)) {
        AppMenuItem(
            text = stringResource(if (card.best) Res.string.best_clear else Res.string.best_set),
            icon = if (card.best) AppIcons.Star else AppIcons.StarOutline,
            onClick = { close(); onBest(sessionId) },
        )
    }
    if (card.hasAudio) {
        AppMenuItem(
            text = stringResource(Res.string.card_menu_share),
            icon = AppIcons.Share,
            onClick = { close(); actions.onShare(sessionId) },
        )
        AppMenuItem(
            text = stringResource(Res.string.card_menu_sound),
            icon = AppIcons.Sound,
            onClick = { close(); actions.onSound(sessionId) },
        )
    }
}

private fun marksBest(card: HistoryCard, actions: CardActions): Boolean = card.take && actions.onBest != null
