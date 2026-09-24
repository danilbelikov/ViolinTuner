package com.violinjourney.app.core.ui.components

import androidx.compose.runtime.Composable

/**
 * The system's picker of one photo: no permission is involved, the app gets one picture and no more. The returned
 * function opens it; [onPicked] gets where the picture is — a content Uri on Android, a file of the app on iOS — and is
 * not called when the picker is closed without a choice.
 */
@Composable
expect fun rememberImagePicker(onPicked: (String) -> Unit): () -> Unit
