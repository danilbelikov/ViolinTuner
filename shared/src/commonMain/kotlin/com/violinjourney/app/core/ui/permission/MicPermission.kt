package com.violinjourney.app.core.ui.permission

import androidx.compose.runtime.Composable

/**
 * Returns a function that asks for the microphone and reports the answer to [onResult].
 *
 * [onAnswer] fires only for an answer to a dialog this function brought up, once for each, and with
 * BLOCKED for a tap the system answers without a dialog any more — never when the microphone is allowed
 * already (nothing is asked then) and never on the checks made on every return to the screen, which is
 * why it can be counted (spec 3.34). While a dialog is up, another tap asks nothing ([MicAsk]).
 *
 * With [openSettingsWhenBlocked] a request the system refuses to show ("denied for good": it
 * answers at once and wants no rationale before or after) opens the app settings instead,
 * because nothing else can help then (spec 3.4).
 */
@Composable
expect fun rememberMicPermissionRequester(
    openSettingsWhenBlocked: Boolean,
    onAnswer: (MicPermissionAnswer) -> Unit = {},
    onResult: (granted: Boolean) -> Unit,
): () -> Unit

/** Whether the microphone is allowed now: read on every return to a screen that listens. */
@Composable
expect fun rememberMicPermissionCheck(): () -> Boolean
