package com.violinjourney.app.core.audio

import com.violinjourney.app.core.concurrent.PlatformLock
import com.violinjourney.app.core.concurrent.withLock
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionModeDefault
import platform.AVFAudio.AVAudioSessionRouteChangeReasonOldDeviceUnavailable
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.preferredIOBufferDuration
import platform.AVFAudio.setActive
import platform.AVFAudio.setPreferredIOBufferDuration
import platform.Foundation.NSNumber

/**
 * Who is using a shared thing, counted: [enter] runs the opening and counts the user only if it returns, [leave] lets
 * the thing go ([letGo]) when the last user has left. Both run under one lock, so a letting-go never falls between
 * another user's opening and its counting — that user would find the thing let go under it.
 */
internal class SessionUsers(private val letGo: () -> Unit) {
    private val lock = PlatformLock()
    private var count = 0

    /** [open] hears how many others are in already. */
    fun <T> enter(open: (others: Int) -> T): T = lock.withLock { open(count).also { count++ } }

    /** For one already in that takes the thing again: [open] runs under the same lock, hearing how many others are in. */
    fun <T> again(open: (others: Int) -> T): T = lock.withLock { open((count - 1).coerceAtLeast(0)) }

    /** How many are in now; for tests. */
    val users: Int get() = lock.withLock { count }

    /**
     * A leave with nobody in is a mistake of the caller, not a reason to let go twice. [left] runs under the same lock
     * with the number still in, before the last one lets go.
     */
    fun leave(left: (remaining: Int) -> Unit = {}) = lock.withLock {
        if (count == 0) return@withLock
        count--
        left(count)
        if (count == 0) letGo()
    }
}

/**
 * The I/O buffer the session prefers (spec 3.4, 5.25): a microphone asks for one hop, so that each I/O cycle brings a hop
 * and Live follows the violin hop by hop; what was preferred before the first microphone is given back once no microphone
 * is left — at once when nobody else uses the session, else when the last user lets it go or a player takes the session
 * for its output with nobody else in (its own engine is stopped then), so that a running output is never reconfigured
 * under it. A player that came in while a microphone listened plays its first sound with the hop, and what was before
 * from its next start on. [read] and [write] are the session's preference; one lock outside (the users') guards it all.
 */
internal class IoBufferPreference(private val read: () -> Double, private val write: (Double) -> Unit) {
    private var saved: Double? = null
    private var microphones = 0

    /** Before a microphone opens the session: remembers what was preferred, the first time, and asks for [seconds]. */
    fun microphoneOpening(seconds: Double) {
        if (saved == null) saved = read()
        write(seconds)
    }

    fun microphoneOpened() {
        microphones++
    }

    /** The session would not open for it: nothing of it stays. */
    fun microphoneFailed() {
        if (microphones == 0) restore()
    }

    fun microphoneLeft(remaining: Int) {
        if (microphones > 0) microphones--
        if (microphones == 0 && remaining == 0) restore()
    }

    /**
     * A player takes the session for its output — coming in, or starting again after a pause — with [others] users in
     * besides it: with no microphone and nobody else whose output may be running, it plays with what was preferred
     * before the microphones.
     */
    fun playerOpening(others: Int) {
        if (microphones == 0 && others == 0) restore()
    }

    fun lettingGo() {
        if (microphones == 0) restore()
    }

    private fun restore() {
        saved?.let(write)
        saved = null
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
    private val ioBuffer = IoBufferPreference(
        read = { AVAudioSession.sharedInstance().preferredIOBufferDuration },
        // only a preference: the system gives what it can, and a refusal changes nothing that works
        write = { AVAudioSession.sharedInstance().setPreferredIOBufferDuration(it, null) },
    )

    private val users = SessionUsers {
        ioBuffer.lettingGo()
        AVAudioSession.sharedInstance().setActive(false, AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, null)
    }

    /** Opens the session for one more user — the category and the activation are [open]'s — and counts it, if [open] returns. */
    fun <T> enter(open: (AVAudioSession) -> T): T = users.enter { others ->
        ioBuffer.playerOpening(others)
        open(AVAudioSession.sharedInstance())
    }

    /**
     * For a player already in whose output starts again after a pause: [open] sets the category and activates the session
     * as on its first play, and the I/O buffer a microphone asked for goes back once the player is alone in it
     * ([IoBufferPreference]) — its engine is stopped now, so nothing running is reconfigured.
     */
    fun <T> retake(open: (AVAudioSession) -> T): T = users.again { others ->
        ioBuffer.playerOpening(others)
        open(AVAudioSession.sharedInstance())
    }

    /**
     * [enter] for a microphone: the session is asked for an I/O buffer of [ioBufferSeconds] before [open] activates it,
     * and what was preferred before comes back when the last microphone has left ([IoBufferPreference]).
     */
    fun <T> enterMicrophone(ioBufferSeconds: Double, open: (AVAudioSession) -> T): T = users.enter {
        ioBuffer.microphoneOpening(ioBufferSeconds)
        try {
            open(AVAudioSession.sharedInstance()).also { ioBuffer.microphoneOpened() }
        } catch (failure: Throwable) {
            ioBuffer.microphoneFailed()
            throw failure
        }
    }

    /** One user fewer; the last one lets the session go. Only after an [enter] that returned. */
    fun leave() = users.leave()

    /** [leave] for a microphone that came in by [enterMicrophone]. */
    fun leaveMicrophone() = users.leave(ioBuffer::microphoneLeft)

    /** How many use the session now; for tests. */
    val count: Int get() = users.users
}

/**
 * Takes the session for playing a recording or a backing aloud. The mode is set too, not only the category: a microphone
 * leaves the session in the measurement mode, which keeps across a change of the category alone and plays the
 * loudspeaker much quieter — a take listened to after Live was barely heard.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun AVAudioSession.takeForPlayback() {
    setCategory(AVAudioSessionCategoryPlayback, AVAudioSessionModeDefault, 0u, null)
    setActive(true, null)
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
