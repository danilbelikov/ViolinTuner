package com.violinjourney.app.feature.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.violinjourney.app.feature.backup.DataBlock

@Composable
fun SettingsRoute(
    onOpenOnboarding: () -> Unit,
    onOpenSound: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenRestore: (uri: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnOpenOnboarding by rememberUpdatedState(onOpenOnboarding)
    val currentOnOpenSound by rememberUpdatedState(onOpenSound)

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    SettingsEffect.OpenOnboarding -> currentOnOpenOnboarding()
                    SettingsEffect.OpenSound -> currentOnOpenSound()
                }
            }
        }
    }

    val context = LocalContext.current
    // Android 13 lets a person choose the language of one app; before it the app follows the device and there is nothing to open
    val openLanguage: (() -> Unit)? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        { context.startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null))) }
    } else {
        null
    }
    SettingsScreen(
        state = state,
        onIntent = viewModel::onIntent,
        modifier = modifier,
        dataBlock = { DataBlock(onOpenBackup = onOpenBackup, onOpenRestore = onOpenRestore) },
        onLanguageClick = openLanguage,
    )
}
