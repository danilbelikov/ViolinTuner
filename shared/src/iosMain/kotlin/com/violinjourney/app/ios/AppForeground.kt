package com.violinjourney.app.ios

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationState
import platform.UIKit.UIApplicationWillEnterForegroundNotification

/**
 * Whether the app is on the screen: false once it has gone to the background, true again when it comes back.
 * A pulled-down Control Center or a notification over it is not the background — Live goes on listening, as on
 * Android, where only a stopped screen lets the microphone go.
 */
internal fun appInForeground(): Flow<Boolean> = callbackFlow {
    trySend(UIApplication.sharedApplication.applicationState != UIApplicationState.UIApplicationStateBackground)
    val center = NSNotificationCenter.defaultCenter
    val observers = listOf(
        center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, NSOperationQueue.mainQueue) { _ -> trySend(false) },
        center.addObserverForName(UIApplicationWillEnterForegroundNotification, null, NSOperationQueue.mainQueue) { _ -> trySend(true) },
    )
    awaitClose { observers.forEach(center::removeObserver) }
}.distinctUntilChanged()
