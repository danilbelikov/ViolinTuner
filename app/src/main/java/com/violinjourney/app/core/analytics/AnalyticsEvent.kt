package com.violinjourney.app.core.analytics

import com.violinjourney.app.core.audio.MicUnavailableReason

/**
 * Every event the app can send, with its parameters — one list, the way `IntonationConfig` holds
 * the numbers of the domain (spec 3.34). A new event is a new line in the spec first and a class
 * here second: the list is part of the promise made on the fourth page of the onboarding.
 */
sealed class AnalyticsEvent(val name: String, val params: Map<String, Any> = emptyMap())

/** Which screen was opened; the key is a route stripped of its arguments — see [screenKeyOf]. */
class ScreenOpen(screen: String) : AnalyticsEvent(NAME, mapOf(PARAM to screen)) {
    companion object {
        const val NAME = "screen_open"
        const val PARAM = "screen"
    }
}

/** Why the microphone went away (spec 3.34): on the emulator it is the bridge, on a phone — unknown. */
class MicUnavailable(reason: MicUnavailableReason) : AnalyticsEvent("mic_unavailable", mapOf("reason" to reason.key))

/**
 * One visit to Live, folded by [FramePicture]: the picture the thresholds get tuned by. The
 * tolerance and the reference pitch ride along because both change what «active» means.
 */
class LiveFrames(
    seconds: Int,
    silencePct: Int,
    noisyPct: Int,
    activePct: Int,
    clarityMedian: Double,
    peakRmsDbfs: Int,
    toleranceCents: Int,
    a4Hz: Int,
) : AnalyticsEvent(
    "live_frames",
    mapOf(
        "seconds" to seconds,
        "silence_pct" to silencePct,
        "noisy_pct" to noisyPct,
        "active_pct" to activePct,
        "clarity_median" to clarityMedian,
        "rms_peak_dbfs" to peakRmsDbfs,
        "tolerance" to toleranceCents,
        "a4" to a4Hz,
    ),
)

/**
 * A navigation route reduced to the screen it names: `piece/{pieceId}` becomes `piece`. Ids and
 * everything else an argument could carry are cut off here, so that no value can leave the phone by
 * riding along inside a route (spec 3.34, rule 1).
 */
fun screenKeyOf(route: String?): String? = route
    ?.substringBefore('/')
    ?.substringBefore('?')
    ?.takeIf { it.isNotBlank() }
