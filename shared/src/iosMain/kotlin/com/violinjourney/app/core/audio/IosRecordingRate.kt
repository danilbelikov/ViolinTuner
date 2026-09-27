package com.violinjourney.app.core.audio

import com.violinjourney.app.core.audio.backing.AudioRoutes
import com.violinjourney.app.core.concurrent.PlatformLock
import com.violinjourney.app.core.concurrent.withLock
import com.violinjourney.app.core.domain.backing.AudioRoute

/**
 * The rate a take will be recorded at on iOS (spec 5.25): the one the microphone last got on the route the sound goes to
 * now. The microphone asks the session for the first of our rates (48 kHz) and gets it wherever the hardware can; a
 * route that cannot — a USB interface fixed at 44.1 kHz, some headphones — gives its own. A take under the backing is
 * always made in headphones, while Live mostly listens on the loudspeaker: one rate for the whole app would be
 * overwritten by every Live and miss again at the next take, so the rate is learned per route. A route not heard yet —
 * and a rate that is not one of ours — falls back to the first of ours, which is what the microphone asks for.
 */
internal class IosRecordingRate(private val supportedHz: List<Int>, private val routes: AudioRoutes) : RecordingRate {
    private val lock = PlatformLock()
    private val heardByRoute = HashMap<AudioRoute, Int>()

    /** The microphone opened at [rateHz], on the route of now; from the thread that opened it. */
    fun heard(rateHz: Int) {
        val route = routes.current()
        lock.withLock { heardByRoute[route] = rateHz }
    }

    override fun likelyHz(): Int {
        val route = routes.current()
        return SampleRates.candidates(lock.withLock { heardByRoute[route] }, supportedHz).first()
    }
}
