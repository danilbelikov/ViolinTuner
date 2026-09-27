package com.violinjourney.app.core.audio.playback

/**
 * Where the sound of a player is as it is heard (spec 5.13), not as it is taken from the track: between the two lies the
 * output — tens of milliseconds through the speaker, more through Bluetooth. The picture of a video take, the slider and
 * the cursor of the note roll follow what is heard, so they must not run ahead of the ear by the output's delay.
 *
 * The player's thread drives it: [startAt] where the sound starts anew, [stamp] with what the output says it presents,
 * [positionMs] after each chunk. Pure; the thread of one player only.
 */
class HeardClock(
    private val rate: Int,
    /** A delay longer than this is no delay but a stamp that lies; it is not taken. */
    private val maxLagMs: Long,
    /**
     * Until the output has said what it presents, the position waits where the sound started — at most this long of sound
     * played: an output that never says is taken to have no delay, as before.
     */
    private val waitMs: Long,
) {
    /** How far behind the head of the track the ear is, in frames, as the output last said; kept across seeks and plays — it is the output's. */
    var lagFrames = 0L
        private set

    /** The output has said it once: from then on each start waits exactly its delay. */
    var lagKnown = false
        private set

    /**
     * The delay the position is counted with: [lagFrames] from every start, but one that grows while the sound plays on —
     * headphones came, the first word of the output came late — is taken half a chunk at a time ([positionMs]).
     */
    private var countedLag = 0L

    /** The position has left the place the sound last started at: from then on a longer delay slows it, never stops it. */
    private var moving = false

    private var waitedFrames = 0L
    private var waitHead = 0L
    private var baseMs = 0L
    private var headBase = 0L
    private var lastMs = 0L

    /** The sound starts anew at [ms] with the head of the track at [head]: the start, a seek, the return after the end. */
    fun startAt(ms: Long, head: Long) {
        baseMs = ms
        headBase = head
        waitHead = head
        lastMs = ms
        countedLag = lagFrames
        moving = false
    }

    /**
     * The output presented [stampFrames] at [stampNanos]; the head of the track is at [head] at [nowNanos]. A stamp that
     * puts the ear ahead of the head or too far behind it is not believed.
     */
    fun stamp(head: Long, stampFrames: Long, stampNanos: Long, nowNanos: Long) {
        lagOf(head, stampFrames, stampNanos, nowNanos, rate, maxLagMs)?.let {
            lagFrames = it
            lagKnown = true
            // still where the sound started: the whole delay is waited out there; a shorter one is taken at once
            if (!moving || it < countedLag) countedLag = it
        }
    }

    /**
     * Where the ear is, now that [count] more frames went to the track and its head is at [head], within 0…[durationMs].
     * After a start it waits there until the sound has come through the output. Once it moves it never steps back and
     * never stands still for a longer delay: it slows to half speed until the delay is taken. A position that stood
     * still, or went back, while the picture carried the clock on from its last word would make the picture seek back.
     */
    fun positionMs(head: Long, count: Int, durationMs: Long): Long {
        if (!lagKnown && waitedFrames < rate * waitMs / MS_PER_SECOND) {
            // of the sound the output has taken, not of the frames written: the first ones only fill the track
            waitedFrames += (head - waitHead).coerceAtLeast(0)
            waitHead = head
            return lastMs.coerceIn(0, durationMs)
        }
        if (countedLag < lagFrames) countedLag = minOf(lagFrames, countedLag + count / 2)
        val heard = (head - headBase - countedLag).coerceAtLeast(0)
        if (heard > 0) moving = true
        val ms = maxOf(lastMs, baseMs + heard * MS_PER_SECOND / rate).coerceIn(0, durationMs)
        lastMs = ms
        return ms
    }

    companion object {
        private const val MS_PER_SECOND = 1_000L
        private const val NANOS_PER_SECOND = 1_000_000_000L

        /**
         * The frames between the head of the track and the ear: the stamp's frame carried on to [nowNanos] at [rate], then
         * taken from [head]. Null when that is below zero or above [maxLagMs].
         */
        fun lagOf(head: Long, stampFrames: Long, stampNanos: Long, nowNanos: Long, rate: Int, maxLagMs: Long): Long? {
            val presented = stampFrames + (nowNanos - stampNanos) * rate / NANOS_PER_SECOND
            val lag = head - presented
            return lag.takeIf { it >= 0 && it <= rate * maxLagMs / MS_PER_SECOND }
        }
    }
}

/**
 * Whether a picture may carry the clock of the sound on from the player's latest word (spec 5.13). Only while the sound
 * is heard moving: the word before said it played too, and this one is further on by about the time between the two.
 * After a play, a seek or the start after the end the heard position waits while the sound is on its way to the ear —
 * the player says nothing meanwhile — and a picture carried on would run ahead and then have to seek back.
 */
object PictureCarry {
    /** How much further than the time between two words a word may be and still be the sound playing on, not a jump. */
    const val JUMP_SLACK_MS = 100L

    fun carries(previousMs: Long, previousAtMs: Long, previousPlaying: Boolean, positionMs: Long, atMs: Long, playing: Boolean): Boolean {
        if (!playing || !previousPlaying) return false
        val moved = positionMs - previousMs
        return moved > 0 && moved <= atMs - previousAtMs + JUMP_SLACK_MS
    }
}
