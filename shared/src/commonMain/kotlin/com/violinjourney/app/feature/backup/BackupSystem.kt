package com.violinjourney.app.feature.backup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * What a copy of the data asks of the system (spec 3.20): the place to write it, the file to bring back, the sheet to
 * send it with, and a start of the app anew after a copy has been brought back. A picked place or file comes back as
 * the uri of the platform — a content uri on Android, a `file:` uri on iOS; null when nothing was picked.
 */
class BackupSystem(
    val pickPlace: (fileName: String) -> Unit,
    val pickCopy: () -> Unit,
    val shareFile: (path: String) -> Unit,
    val restart: () -> Unit,
    /** The policy the stores link to (spec 3.34), in the browser. */
    val openPrivacyPolicy: () -> Unit,
)

@Composable
expect fun rememberBackupSystem(onPlacePicked: (uri: String?) -> Unit = {}, onCopyPicked: (uri: String?) -> Unit = {}): BackupSystem

/** How the app starts anew after a copy has been put in place; the root of each platform provides it. */
val LocalAppRestart = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * The policy the stores link to (spec 3.34): Google Play wants it inside an app that hears the
 * microphone and sends statistics, not only on the store's page. One page in both languages.
 */
const val PRIVACY_POLICY_URL = "https://danilbelikov.github.io/violin-journey/privacy/"
