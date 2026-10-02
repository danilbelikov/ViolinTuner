package com.violinjourney.app.core.ui.components

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.piece_edit
import com.violinjourney.app.shared.resources.session_back
import org.jetbrains.compose.resources.stringResource

/** The buttons of the bar: «назад» and «⋯» — 48 (5.29 R4, R9 «Общее»). */
private val BarButton = 48.dp
private val BarEdge = 4.dp

/**
 * The bar of a screen of one thing — an element of the repertoire (spec 3.36.4) and an event (spec 3.36.9; plan D23): «назад», the name
 * — on the bar only once the large one has scrolled away ([titleVisible]): upright on both, lying on the screen of an event, whose left
 * column holds the name large, and always lying on an element's, which has no large name there — then «Изменить» with its pencil,
 * a text button of 48, where the thing has a form ([onEdit]); asleep while a take runs ([editable] false): the form would end it (spec
 * 3.15). [more] — what stands after it, the «⋯» of an event with «Удалить…». Until the thing is read ([loaded] false) — «назад» alone.
 * 56 upright, 48 lying.
 */
@Composable
fun ElementTopBar(
    title: String,
    titleVisible: Boolean,
    height: Dp,
    onBack: () -> Unit,
    onEdit: (() -> Unit)?,
    editable: Boolean = true,
    loaded: Boolean = true,
    more: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = BarEdge),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val back = stringResource(Res.string.session_back)
        Box(
            modifier = Modifier
                .size(BarButton)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onBack)
                .semantics { contentDescription = back },
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(AppIcons.Back, contentDescription = null, tint = colors.onSurface)
        }
        // The large title below says it first upright; this one takes over once that has scrolled away.
        Box(modifier = Modifier.weight(1f)) {
            // not the one of RowScope: it would grow the row sideways
            androidx.compose.animation.AnimatedVisibility(visible = titleVisible, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    text = title,
                    modifier = Modifier.semantics { heading() },
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold),
                )
            }
        }
        if (loaded) {
            if (onEdit != null) {
                AppButton(
                    text = stringResource(Res.string.piece_edit),
                    onClick = onEdit,
                    modifier = Modifier.dimmedWhen(!editable),
                    style = AppButtonStyle.Text,
                    icon = AppIcons.Pencil,
                )
            }
            more?.invoke()
        }
    }
}

/**
 * «⋯» of a bar (spec 3.36.9): an icon of 24 in a target of 48, [label] for TalkBack; asleep with the rest of the bar while a take runs
 * ([enabled] false). What it opens — the menu — the caller lays beside it.
 */
@Composable
fun BarMoreButton(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Box(
        modifier = Modifier
            .dimmedWhen(!enabled)
            .size(BarButton)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(AppIcons.More, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
