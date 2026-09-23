package com.violinjourney.app.core.analytics

import com.violinjourney.app.core.audio.MicUnavailableReason
import com.violinjourney.app.core.ui.permission.MicPermissionAnswer

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

/** What the system answered about the microphone, once per request (spec 3.34). */
class MicPermission(answer: MicPermissionAnswer) : AnalyticsEvent("mic_permission", mapOf("result" to answer.key))

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

/** A practice that was timed and saved (spec 3.12): is the timer used, and for how long. */
class PracticeFinished(minutes: Int, blocks: Int, bars: Int) :
    AnalyticsEvent("practice_finished", mapOf("minutes" to minutes, "blocks" to blocks, "bars" to bars))

/** A take that was kept (spec 3.9, 3.19, 3.32) — its length and kind, never its piece. */
class TakeRecorded(seconds: Int, video: Boolean, backing: Boolean) :
    AnalyticsEvent("take_recorded", mapOf("seconds" to seconds, "kind" to if (video) "video" else "audio", "backing" to backing))

/** Takes thrown away, one or a handful at once (spec 3.18). */
class TakeDeleted(count: Int) : AnalyticsEvent("take_deleted", mapOf("count" to count))

/** Which parts of the repertoire get used (spec 3.22). The section, never the title. */
class PieceAdded(section: String, ownSection: Boolean, scale: Boolean) :
    AnalyticsEvent("piece_added", mapOf("section" to section, "own_section" to ownSection, "scale" to scale))

/** How far along the road people get (spec 3.23): the number of the city, not its name. */
class CityReached(index: Int) : AnalyticsEvent("city_reached", mapOf("index" to index))

/** What the shop sells (spec 3.24, 3.29); the id is a key of the catalogue, not a name on screen. */
class ItemBought(id: String, house: Boolean) :
    AnalyticsEvent("item_bought", mapOf("item_id" to id, "kind" to if (house) "house" else "item"))

/** A level gained from time at the violin (spec 3.13). */
class LevelUp(level: Int) : AnalyticsEvent("level_up", mapOf("level" to level))

/** A copy written (spec 3.20): how big, and how many parts went in. */
class BackupCreated(megabytes: Int, parts: Int) :
    AnalyticsEvent("backup_created", mapOf("size_mb" to megabytes, "parts" to parts))

/**
 * A copy restored. The only moment where a person can lose everything, and until now nobody but
 * them ever learned that it had failed.
 */
class BackupRestored(ok: Boolean) : AnalyticsEvent("backup_restored", mapOf("ok" to ok))

/**
 * A navigation route reduced to the screen it names: `piece/{pieceId}` becomes `piece`. Ids and
 * everything else an argument could carry are cut off here, so that no value can leave the phone by
 * riding along inside a route (spec 3.34, rule 1).
 */
fun screenKeyOf(route: String?): String? = route
    ?.substringBefore('/')
    ?.substringBefore('?')
    ?.takeIf { it.isNotBlank() }
