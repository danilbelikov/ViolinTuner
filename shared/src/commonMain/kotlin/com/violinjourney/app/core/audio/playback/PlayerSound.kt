package com.violinjourney.app.core.audio.playback

import com.violinjourney.app.core.audio.backing.BackingMixer
import com.violinjourney.app.core.audio.backing.BackingSource
import com.violinjourney.app.core.audio.fx.SoundChain
import com.violinjourney.app.core.audio.fx.SoundMeters
import com.violinjourney.app.core.domain.backing.BackingConfig
import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.domain.sound.SoundRules
import com.violinjourney.app.core.domain.sound.SoundSettings

/**
 * What a player does to the sound of a take between reading a chunk and handing it to the output, the same on both
 * platforms (spec 3.17, 3.32): the chain with its settings, «A ↔ B» ([AbMixer]), the backing mixed in against the violin
 * that is heard ([BackingMixer]), the silence after the last sample while the hall and the delays ring out, and when the
 * meters are due. The platform keeps the rest — the decoder, the output (`AudioTrack`, `AVAudioPlayerNode`), its thread
 * and the wishes of the screen — and calls [next] for every chunk. One per loaded file; not thread-safe: the player's
 * worker alone calls it.
 */
class PlayerSound(
    private val rate: Int,
    private val config: SoundConfig,
    backingConfig: BackingConfig,
    /** The backing's sound at [rate]; null — the violin alone, mono. */
    backing: BackingSource?,
    offsetMs: Int,
    gainDb: Float,
    heard: Boolean,
    settings: SoundSettings,
    original: Boolean,
) {
    private val chain = SoundChain(rate, config)
    private val mixer = AbMixer(chain.latencySamples, fadeSamples = (config.abFadeMs * rate / MS_PER_SECOND).toInt())
    private val backingMix = backing?.let {
        BackingMixer(rate, it, offsetMs, gainDb, heard, fadeSamples = (backingConfig.shiftFadeMs * rate / MS_PER_SECOND).toInt(), soundConfig = config)
    }

    /** Under a backing the sound is stereo: [output] holds its frames interleaved, left and right. */
    val stereo: Boolean = backingMix != null

    private val dry = FloatArray(CHUNK)
    private val wet = FloatArray(CHUNK)
    private val mixed = FloatArray(if (stereo) CHUNK * 2 else 0)

    /** The chunk [next] made: its frames, mono — or interleaved stereo, see [stereo]. */
    var output: FloatArray = dry
        private set

    private var current = settings
    private var processing = !SoundRules.isNeutral(settings)

    /** The violin's sample the next chunk starts at: where the backing is read from. */
    private var violinPosition = 0L

    /** Samples of silence still to be pushed through after the end: the hall rings on, the delays empty out. */
    private var tailLeft = NO_TAIL
    private var sinceMeters = 0

    init {
        chain.set(settings, immediate = true)
        mixer.jumpTo(if (processing && !original) 1f else 0f)
    }

    /** The take and all that rings after it are through: nothing more comes until [restartAt]. */
    val ended: Boolean get() = tailLeft == 0

    fun changeSettings(fresh: SoundSettings) {
        current = fresh
        processing = !SoundRules.isNeutral(fresh)
        chain.set(fresh)
    }

    fun changeBacking(offsetMs: Int, gainDb: Float, heard: Boolean) {
        backingMix?.set(offsetMs, gainDb, heard)
    }

    /**
     * The take goes on from its sample [frame] — a seek, the end, or the output lost and taken again: nothing of what
     * rang before is carried over, and the backing is read from the new place. The platform moves its decoder there.
     */
    fun restartAt(frame: Long) {
        chain.reset()
        mixer.reset()
        backingMix?.reset()
        violinPosition = frame
        tailLeft = NO_TAIL
    }

    /**
     * The next chunk into [output], [original] — «A» asked for. [read] fills the array it is given from its start with the
     * take's next samples, −1…1, at most [CHUNK], and says how many; none — the take has ended, and silence follows for as
     * long as the chain and the mix still ring. Returns the frames made; 0 once [ended].
     */
    fun next(original: Boolean, read: (FloatArray) -> Int): Int {
        var count = if (tailLeft == NO_TAIL) read(dry) else 0
        if (count <= 0) {
            if (tailLeft == NO_TAIL) tailLeft = chain.latencySamples + (backingMix?.latencySamples ?: 0) + if (mixer.originalOnly) 0 else chain.tailSamples(current)
            count = minOf(tailLeft, CHUNK)
            tailLeft -= count
            dry.fill(0f, 0, count)
        }
        // A chain that has been resting — «A», or settings that did nothing — knows nothing of the last seconds: it starts
        // clean, and the fade covers its first moment.
        if (mixer.aim(if (processing && !original) 1f else 0f)) chain.reset()
        val out = if (mixer.originalOnly) {
            mixer.passOriginal(dry, count)
            dry
        } else {
            dry.copyInto(wet, 0, 0, count)
            chain.process(wet, count)
            mixer.mix(dry, wet, count)
            wet
        }
        output = if (backingMix != null) {
            // the A/B line hands the violin over chain.latencySamples late, processed or not: the backing is read as far
            // behind — as the render does by dropping those samples (spec 3.17: what is heard is what is sent)
            backingMix.mix(out, count, violinPosition - chain.latencySamples, mixed)
            mixed
        } else {
            out
        }
        violinPosition += count
        return count
    }

    /** [frames] more have been played: true when the meters are due again — about [SoundConfig.metersPerSecond] times a second. */
    fun metersDue(frames: Int): Boolean {
        sinceMeters += frames
        if (sinceMeters < rate / config.metersPerSecond) return false
        sinceMeters = 0
        return true
    }

    /** What the meters show now: nothing while the chain rests and the original alone is heard (spec 5.11). */
    fun meters(): SoundMeters? = if (mixer.originalOnly) null else chain.takeMeters()

    companion object {
        /** ~43 ms at 48 kHz: how often the players look at the wishes and tell the position. */
        const val CHUNK = 2_048
        private const val NO_TAIL = -1
        private const val MS_PER_SECOND = 1_000L
    }
}
