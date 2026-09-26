package com.violinjourney.app.ios

import com.violinjourney.app.core.backup.BackupKeepAlive
import com.violinjourney.app.core.ui.components.ScreenOnHolds
import com.violinjourney.app.core.ui.components.appScreenOnHolds
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIBackgroundTaskIdentifier
import platform.UIKit.UIBackgroundTaskInvalid
import platform.darwin.NSObjectProtocol

/**
 * Keeps a copy on its way on iOS (spec 3.20). iOS has nothing like the foreground service of Android: an app that leaves
 * the screen gets some tens of seconds, then it sleeps, and the copy with it. So while a copy or a restore runs, the
 * screen does not go dark by itself (the progress asks not to leave the app), and every return to the app asks for a
 * new stint of background time — the one before ran out by the system's clock.
 */
internal object IosKeepAlive : BackupKeepAlive {
    private val turns = KeepAliveTurns(UiKitBackgroundTime, appScreenOnHolds)
    private var returns: NSObjectProtocol? = null

    override fun start() {
        if (returns == null) {
            returns = NSNotificationCenter.defaultCenter.addObserverForName(UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue) { _ ->
                turns.becameActive()
            }
        }
        turns.start()
    }

    /** The copy has ended, one way or the other: the time is given back at once, and the screen may go dark again. */
    fun stop() = turns.stop()
}

/** The background time of iOS: a stint asked for, and given back. */
internal interface BackgroundTime {
    /** A stint; [onExpired] runs when the system's clock has run out, and the stint must then be given back at once. */
    fun begin(onExpired: () -> Unit): ULong

    fun end(task: ULong)

    val none: ULong
}

private object UiKitBackgroundTime : BackgroundTime {
    override fun begin(onExpired: () -> Unit): UIBackgroundTaskIdentifier = UIApplication.sharedApplication.beginBackgroundTaskWithName(NAME, onExpired)

    override fun end(task: UIBackgroundTaskIdentifier) = UIApplication.sharedApplication.endBackgroundTask(task)

    override val none: UIBackgroundTaskIdentifier = UIBackgroundTaskInvalid

    private const val NAME = "backup"
}

/**
 * What [IosKeepAlive] holds while a copy runs: one hold of the screen from [start] to [stop], however often a job starts,
 * and a stint of background time — asked for again when the app comes back ([becameActive]) after the last one ran out.
 * The main thread only, as [ScreenOnHolds].
 */
internal class KeepAliveTurns(private val time: BackgroundTime, private val screen: ScreenOnHolds) {
    private var wanted = false
    private var task = time.none

    fun start() {
        if (!wanted) {
            wanted = true
            screen.take()
        }
        ask()
    }

    fun stop() {
        if (!wanted) return
        wanted = false
        screen.give()
        giveBack()
    }

    fun becameActive() {
        if (wanted) ask()
    }

    private fun ask() {
        if (task != time.none) return
        var begun = time.none
        begun = time.begin {
            // exactly the stint this was asked with: a later one is not the system's to take back here
            time.end(begun)
            if (task == begun) task = time.none
        }
        task = begun
    }

    private fun giveBack() {
        if (task == time.none) return
        time.end(task)
        task = time.none
    }
}
