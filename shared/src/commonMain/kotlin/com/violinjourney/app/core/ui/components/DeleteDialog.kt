package com.violinjourney.app.core.ui.components

import androidx.compose.runtime.Composable
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.session_delete_confirm
import org.jetbrains.compose.resources.stringResource

/**
 * «Удалить …?» of every screen (spec 3.36.1): the bin in its soft coral circle over the question, what goes with it, and the action
 * as a coral word — no filled button, the bin and the colour together (3.16). [confirm] is the word of the action when it is not
 * «Удалить»: «Убрать» for a backing track.
 */
@Composable
fun DeleteDialog(
    title: String,
    text: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirm: String = stringResource(Res.string.session_delete_confirm),
) {
    AppDialog(
        title = title, confirm = confirm, onConfirm = onConfirm, onDismiss = onDismiss, text = text,
        confirmTone = DialogTone.Danger, bin = true,
    )
}
