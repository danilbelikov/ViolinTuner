package com.violinjourney.app.ios

import com.violinjourney.app.core.backup.BackupKeepAlive
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskInvalid

/**
 * Keeps a copy on its way when the app leaves the screen: iOS gives an app that asks some time in the background, as
 * the foreground service does on Android. The time runs out by the system's clock; the task is then given back.
 */
internal object IosKeepAlive : BackupKeepAlive {
    private var task = UIBackgroundTaskInvalid

    override fun start() {
        if (task != UIBackgroundTaskInvalid) return
        val application = UIApplication.sharedApplication
        task = application.beginBackgroundTaskWithName(NAME) {
            application.endBackgroundTask(task)
            task = UIBackgroundTaskInvalid
        }
    }

    /** The copy has ended, one way or the other: the time is given back at once. */
    fun stop() {
        if (task == UIBackgroundTaskInvalid) return
        UIApplication.sharedApplication.endBackgroundTask(task)
        task = UIBackgroundTaskInvalid
    }

    private const val NAME = "backup"
}
