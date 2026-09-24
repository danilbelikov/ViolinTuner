package com.violinjourney.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.icons.IconLabel
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.dialog_cancel
import com.violinjourney.app.shared.resources.session_delete_confirm
import org.jetbrains.compose.resources.stringResource

private val IconCircle = 44.dp
private const val ICON_CIRCLE_ALPHA = 0.16f

/**
 * The one "this cannot be undone" dialog of recordings (spec 3.10, 3.18; handoff 19e4): a bin in a
 * soft circle, the question, and a filled destructive button — the bin and the colour together.
 */
@Composable
fun DeleteDialog(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val destructive = ViolinTheme.destructive
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(Modifier.size(IconCircle).background(destructive.copy(alpha = ICON_CIRCLE_ALPHA), CircleShape), contentAlignment = Alignment.Center) {
                AppIcon(AppIcons.Trash, contentDescription = null, tint = destructive)
            }
        },
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = destructive, contentColor = Color.White)) {
                IconLabel(AppIcons.Trash, stringResource(Res.string.session_delete_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.dialog_cancel)) } },
    )
}
