package com.violinjourney.app.core.ui.permission

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioApplication
import platform.AVFAudio.AVAudioApplicationRecordPermissionGranted
import platform.AVFAudio.AVAudioApplicationRecordPermissionUndetermined
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

/**
 * iOS shows its dialog once; after a refusal only the Settings can change it — that is the «blocked» of Android, and
 * the button opens them when [openSettingsWhenBlocked] (spec 3.4).
 */
@Composable
actual fun rememberMicPermissionRequester(
    openSettingsWhenBlocked: Boolean,
    onAnswer: (MicPermissionAnswer) -> Unit,
    onResult: (granted: Boolean) -> Unit,
): () -> Unit {
    val scope = rememberCoroutineScope()
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnAnswer by rememberUpdatedState(onAnswer)
    return remember(scope, openSettingsWhenBlocked) {
        {
            val permission = AVAudioApplication.sharedInstance.recordPermission
            when {
                permission == AVAudioApplicationRecordPermissionUndetermined ->
                    AVAudioApplication.requestRecordPermissionWithCompletionHandler { granted ->
                        // the answer comes on a thread of the system's own
                        scope.launch(Dispatchers.Main) {
                            currentOnResult(granted)
                            currentOnAnswer(if (granted) MicPermissionAnswer.GRANTED else MicPermissionAnswer.DENIED)
                        }
                    }
                permission == AVAudioApplicationRecordPermissionGranted -> currentOnResult(true)
                else -> {
                    currentOnResult(false)
                    currentOnAnswer(MicPermissionAnswer.BLOCKED)
                    if (openSettingsWhenBlocked) {
                        NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let { settings ->
                            UIApplication.sharedApplication.openURL(settings, emptyMap<Any?, Any?>(), null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
actual fun rememberMicPermissionCheck(): () -> Boolean =
    remember { { AVAudioApplication.sharedInstance.recordPermission == AVAudioApplicationRecordPermissionGranted } }
