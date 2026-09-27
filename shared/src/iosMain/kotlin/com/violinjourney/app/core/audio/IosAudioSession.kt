package com.violinjourney.app.core.audio

import com.violinjourney.app.core.concurrent.PlatformLock
import com.violinjourney.app.core.concurrent.withLock
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionRouteChangeReasonOldDeviceUnavailable
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.setActive
import platform.Foundation.NSNumber

/**
 * Who is using a shared thing, counted: [enter] runs the opening and counts the user only if it returns, [leave] lets
 * the thing go ([letGo]) when the last user has left. Both run under one lock, so a letting-go never falls between
 * another user's opening and its counting — that user would find the thing let go under it.
 */
internal class SessionUsers(private val letGo: () -> Unit) {
    private val lock = PlatformLock()
    private var count = 0

    fun <T> enter(open: () -> T): T = lock.withLock { open().also { count++ } }

    /** How many are in now; for tests. */
    val users: Int get() = lock.withLock { count }

    /** A leave with nobody in is a mistake of the caller, not a reason to let go twice. */
    fun leave() = lock.withLock {
        if (count == 0) return@withLock
        count--
        if (count == 0) letGo()
    }
}

/**
 * The audio session of the app — one for the microphone of every screen and for the players of recordings. On
 * Android each of them opens its own stream; on iOS they share this one, so it is let go only by the last one using it:
 * a Live still listening for its two seconds after the screen went away no longer deactivates it under a player that
 * has just started, and a player let go no longer leaves the session active with nothing playing. Letting it go says
 * so to other apps, so music another app had on comes back.
 */
@OptIn(ExperimentalForeignApi::class)
internal object IosAudioSession {
    private val users = SessionUsers {
        AVAudioSession.sharedInstance().setActive(false, AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, null)
    }

    /** Opens the session for one more user — the category and the activation are [open]'s — and counts it, if [open] returns. */
    fun <T> enter(open: (AVAudioSession) -> T): T = users.enter { open(AVAudioSession.sharedInstance()) }

    /** One user fewer; the last one lets the session go. Only after an [enter] that returned. */
    fun leave() = users.leave()

    /** How many use the session now; for tests. */
    val count: Int get() = users.users
}

/**
 * Whether a change of the route ends what plays: the output it went to is gone — headphones taken off — and the sound
 * would go on out of the loudspeaker. Players stop there, as they do on Android when the output «becomes noisy»; a new
 * device and a category changed by another user in the app do not stop them.
 */
internal fun routeChangeStopsSound(reason: ULong?): Boolean = reason == AVAudioSessionRouteChangeReasonOldDeviceUnavailable

/** A number of a notification's userInfo, as the bridge hands it over: an NSNumber, or a Kotlin number. */
internal fun Any?.asULong(): ULong? = when (this) {
    is NSNumber -> unsignedIntegerValue
    is Number -> toLong().toULong()
    else -> null
}
