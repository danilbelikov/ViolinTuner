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

/** What the «⋯» of a recording's card offers (spec 3.17). Only recordings with sound get one. */
class CardActions(val onShare: (sessionId: Long) -> Unit, val onSound: (sessionId: Long) -> Unit)

/**
 * «⋯» on the card of a recording with sound: the quick way to «Поделиться» and «Звук…» without
 * opening the recording. Renaming and deleting stay on the recording's own screen — their
 * dialogs live there.
 */
@Composable
fun CardMenuButton(sessionId: Long, actions: CardActions, modifier: Modifier = Modifier) {
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
