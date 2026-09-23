package com.example.violintuner.core.audio.playback

import com.example.violintuner.core.audio.fx.SoundMeters
import com.example.violintuner.core.domain.sound.SoundRules
import com.example.violintuner.core.domain.sound.SoundSettings
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** A player without sound: ready the moment a file is loaded, and remembers everything it was told. */
class FakeSessionPlayer : SessionPlayer {
    override val state = MutableStateFlow(PlayerState())
    override val meters = MutableStateFlow<SoundMeters?>(null)
    val loaded = mutableListOf<File>()
    val sounds = mutableListOf<SoundSettings>()
    var released = 0

    /** What the backing was loaded with, and every mix it was told since (spec 3.32). */
    var backing: PlayerBacking? = null
    val mixes = mutableListOf<Pair<Int, Float>>()

    override fun loadWithBacking(file: File, backing: PlayerBacking?) {
        load(file)
        this.backing = backing
        state.update { it.copy(hasBacking = backing != null) }
    }

    override fun setBackingMix(offsetMs: Int, gainDb: Float) {
        mixes += offsetMs to gainDb
    }

    override fun setBackingHeard(heard: Boolean) = state.update { it.copy(backingHeard = heard) }

    override fun load(file: File) {
        loaded += file
        state.update { PlayerState(ready = true, durationMs = 4_100, processed = it.processed, original = it.original) }
    }

    override fun play() = state.update { it.copy(playing = true) }

    override fun pause() = state.update { it.copy(playing = false) }

    override fun seekTo(positionMs: Long) = state.update { it.copy(positionMs = positionMs) }

    override fun setSound(settings: SoundSettings) {
        sounds += settings
        state.update { it.copy(processed = !SoundRules.isNeutral(settings)) }
    }

    override fun setOriginal(original: Boolean) = state.update { it.copy(original = original) }

    override fun release() {
        released++
    }
}
