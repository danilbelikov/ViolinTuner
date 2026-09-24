package com.violinjourney.app.ios

import platform.AVFAudio.AVAudioApplication
import platform.AVFAudio.AVAudioApplicationRecordPermissionGranted
import platform.AVFAudio.AVAudioApplicationRecordPermissionUndetermined
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

/**
 * The microphone permission of iOS, read the way Live needs it (spec 3.4): allowed or not. Never asked counts as
 * not allowed — the prompt shows and its button brings the system dialog; once refused, the system will not ask
 * again, so the button opens the app's page in the Settings instead.
 */
internal object MicPermission {
    fun granted(): Boolean = AVAudioApplication.sharedInstance.recordPermission == AVAudioApplicationRecordPermissionGranted

    /** The button of the prompt; [onAnswer] gets the answer of the system dialog, on some thread of its own. */
    fun request(onAnswer: (Boolean) -> Unit) {
        if (AVAudioApplication.sharedInstance.recordPermission == AVAudioApplicationRecordPermissionUndetermined) {
            AVAudioApplication.requestRecordPermissionWithCompletionHandler { granted -> onAnswer(granted) }
        } else {
            val settings = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
            UIApplication.sharedApplication.openURL(settings, emptyMap<Any?, Any?>(), null)
        }
    }
}
