package com.violinjourney.app.ios

import com.violinjourney.app.core.backup.BackupKeepAlive
import com.violinjourney.app.core.recording.RecordingWatch
import com.violinjourney.app.core.ui.components.ScreenOnHolds
import com.violinjourney.app.core.ui.components.appScreenOnHolds
import kotlin.concurrent.AtomicInt
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
    private val turns = KeepAliveTurns(UiKitBackgroundTime(BACKUP_NAME), appScreenOnHolds)
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

    private const val BACKUP_NAME = "backup"
}

/**
 * Keeps a take on its way to the database on iOS (spec 3.9, 3.32): from the start of its recording until it is saved —
 * [RecordingWatch] says both — the app holds a stint of background time. A take the app finishes as it leaves the screen
 * («Домой» mid-take: the sound closed, the video of the camera stopped and made, the row written) is not put to sleep
 * halfway, and lost with its files if iOS then ends the app. The screen is not held: the screens that record hold it.
 */
internal object IosTakeKeepAlive {
    /** Follows [watch] on the main thread until cancelled — the life of the graph that owns it; the stint goes with it. */
    suspend fun follow(watch: RecordingWatch, time: BackgroundTime = UiKitBackgroundTime(TAKE_NAME)) {
        val turns = KeepAliveTurns(time, screen = null)
        val returns = NSNotificationCenter.defaultCenter.addObserverForName(UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue) { _ ->
            turns.becameActive()
        }
        try {
            watch.recording.collect { recording -> if (recording) turns.start() else turns.stop() }
        } finally {
            NSNotificationCenter.defaultCenter.removeObserver(returns)
            turns.stop()
        }
    }

    private const val TAKE_NAME = "take"
}

/** The background time of iOS: a stint asked for, and given back. */
internal interface BackgroundTime {
    /** A stint; [onExpired] runs when the system's clock has run out, and the stint must then be given back at once. */
    fun begin(onExpired: () -> Unit): ULong

    fun end(task: ULong)

    val none: ULong
}

private class UiKitBackgroundTime(private val name: String) : BackgroundTime {
    override fun begin(onExpired: () -> Unit): UIBackgroundTaskIdentifier = UIApplication.sharedApplication.beginBackgroundTaskWithName(name, onExpired)

    override fun end(task: UIBackgroundTaskIdentifier) = UIApplication.sharedApplication.endBackgroundTask(task)

    override val none: UIBackgroundTaskIdentifier = UIBackgroundTaskInvalid
}

/**
 * What [IosKeepAlive] holds while a copy runs: one hold of the screen from [start] to [stop], however often a job starts,
 * and a stint of background time — asked for again when the app comes back ([becameActive]) after the last one ran out.
 * [IosTakeKeepAlive] holds the same without the screen ([screen] null). The main thread only, as [ScreenOnHolds].
 */
internal class KeepAliveTurns(private val time: BackgroundTime, private val screen: ScreenOnHolds?) {
    private var wanted = false
    private var task = time.none

    fun start() {
        if (!wanted) {
            wanted = true
            screen?.take()
        }
        ask()
    }

    fun stop() {
        if (!wanted) return
        wanted = false
        screen?.give()
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

/**
 * [block] in a stint of background time of its own: work the app finishes after it has left the screen — a take shot by
 * its camera, stopped and made into a video as the app goes (spec 3.32) — is not stopped halfway when iOS puts the app
 * to sleep. The stint is given back when [block] ends, or when the system's clock runs out.
 */
internal suspend fun <T> withBackgroundTime(name: String, block: suspend () -> T): T {
    val stint = BackgroundStint(name)
    try {
        return block()
    } finally {
        stint.end()
    }
}

private class BackgroundStint(name: String) {
    private val ended = AtomicInt(0)
    private val task: UIBackgroundTaskIdentifier = UIApplication.sharedApplication.beginBackgroundTaskWithName(name) { end() }

    /** Once, from whichever comes first: the work's end or the system's. */
    fun end() {
        if (ended.compareAndSet(0, 1) && task != UIBackgroundTaskInvalid) UIApplication.sharedApplication.endBackgroundTask(task)
    }
}
